package com.centyllion.model

import com.centyllion.expression.*
import kotlin.math.floor

interface FieldEvaluator {
    fun evaluateField(current: Float): Float
}

class NetbioDynEvaluator(
    val simulator: Simulator?,
    variables: Map<String, Value> = defaultConstants(),
    functions: Map<String, (values: List<Value>) -> Value> = defaultFunctions()
): Evaluator(variables, functions) {

    var step: Value = 0.v
    var x: Value = 0.v
    var y: Value = 0.v
    var z: Value = 0.v
    var current: Value = 0f.v

    /** Agent counts for the current position (grainId -> count) */
    var agentCounts: Map<Int, Int> = emptyMap()

    /** Number of empty neighbour cells, for `countEmpty()`. */
    var emptyCount: Int = 0

    /** Ages for the `age(n)` function, 0-based: index 0 is the main agent
     *  (age 0 for generation behaviours), index k (k >= 1) is the neighbour
     *  of reaction k. Formulas use the 1-based "reactive" numbering of the
     *  age-inheritance column: age(1) = main agent, age(2) = first reaction. */
    var ages: List<Int> = emptyList()

    /** Names of functions provided by this evaluator (in addition to [functions]) */
    private val dynamicFunctions = setOf(
        "agent", "field", "count", "countEmpty", "age", "onAgent"
    ) + neighbourSetFunctions

    /**
     * True while evaluating the condition of a simulation modifier: the only place where the
     * state tests ([stateTestFunctions]) are allowed. It is a flag and not a different evaluator
     * because the same instance serves the behaviours, the fields and the modifiers of a step
     * (like [absoluteCoordinates]).
     */
    var stateTestsAllowed: Boolean = false

    /** True when x, y, z are not the cell of a behaviour (simulation modifier). */
    var absoluteCoordinates: Boolean = false

    /**
     * Mask of the agent types present among the eight neighbours of the current cell: bit `id`
     * is set when at least one neighbour holds the grain `id`.
     *
     * It comes from [agentCounts], the same table as `count(grainId)`: the cell itself is not
     * counted, the boundary conditions of the behaviour are already applied, and the edges wrap
     * around the grid. Ids 0 to [NEIGHBOUR_SET_MAX_TYPES] - 1 are represented; the sentinels
     * `-1` (all agents) and `-2` (empty cells) are not types and are skipped.
     */
    val neighbourTypesMask: Int
        get() {
            var mask = 0
            for ((id, count) in agentCounts) {
                if (count > 0 && id in 0 until NEIGHBOUR_SET_MAX_TYPES) mask = mask or (1 shl id)
            }
            return mask
        }

    /** Names of variables provided by this evaluator (in addition to [variables]) */
    private val dynamicVariables = setOf("step", "x", "y", "z", "current")

    override fun hasFunction(name: String): Boolean =
        stateTestFunctions.contains(name) || dynamicFunctions.contains(name) || super.hasFunction(name)

    override fun hasVariable(name: String): Boolean =
        dynamicVariables.contains(name) || super.hasVariable(name)

    override fun variable(name: String): Value = when (name) {
        "step" -> step
        "x" -> x
        "y" -> y
        "z" -> z
        "current" -> current
        else -> super.variable(name)
    }

    /**
     * The four set predicates over the neighbourhood take a list as their only argument, and a
     * list has no value of its own: they are answered from the expression tree, before the
     * arguments are evaluated. Every other node is handled by [Evaluator.evaluate], which also
     * refuses a list used as a value.
     */
    override fun evaluate(node: Expression): Value = when {
        node is FunctionCall && node.name.content in neighbourSetFunctions ->
            neighbourSet(node.name.content, node)
        // count({0,1}): the list has no value of its own, it names types to sum
        node is FunctionCall && node.name.content == "count" && node.arguments.singleOrNull() is ListLiteral ->
            countOfSet(node)
        else -> super.evaluate(node)
    }

    override fun function(name: String, args: List<Value>): Value = when (name) {
        "agent" ->  {
            if (simulator != null) {
                val sim = simulator.simulation
                // toroidal coordinates (consistent with Simulation.moveIndex / count())
                val ax = wrapCoordinate(args[0].int, sim.width)
                val ay = wrapCoordinate(args[1].int, sim.height)
                simulator.agents[sim.toIndex(ax, ay)].v
            } else Value.Zero
        }
        "field" -> {
            if (simulator != null) {
                val id = args[0].int
                // field(id): value at the current cell (x, y)
                // field(id, dx, dy): value at the offset (dx, dy) from the current cell
                // toroidal coordinates (consistent with Simulation.moveIndex / count())
                val sim = simulator.simulation
                // in a modifier there is no current cell: field(id, x, y) are absolute coordinates
                val px = wrapCoordinate(if (args.size >= 2) if (absoluteCoordinates) args[1].int else x.int + args[1].int else x.int, sim.width)
                val py = wrapCoordinate(if (args.size >= 3) if (absoluteCoordinates) args[2].int else y.int + args[2].int else y.int, sim.height)
                val values = simulator.fields[id]
                val result = values?.get(sim.toIndex(px, py)) ?: 0f
                result.v
            } else Value.Zero
        }
        "count" -> {
            val grainId = args[0].int
            val sim = simulator?.simulation
            if (args.size >= 3 && sim != null && simulator != null) {
                // count(id, x, y): number of neighbours of that grain around the cell (x, y)
                val origin = sim.toIndex(wrapCoordinate(args[1].int, sim.width), wrapCoordinate(args[2].int, sim.height))
                var total = 0
                for (direction in Direction.entries) {
                    if (simulator.agents[sim.moveIndex(origin, direction)] == grainId) total += 1
                }
                total.v
            } else {
                (agentCounts[grainId] ?: 0).v
            }
        }
        "countEmpty" -> emptyCount.v
        "onAgent" -> onAgent(args)
        // state tests: 1 when the test holds, 0 otherwise. Only the condition of a simulation
        // modifier may use them, hence the flag checked by stateTest().
        "IsBehaviourActive", "IsModifierActive",
        "IsToricSelectionOn", "IsOverwritingOn", "IsVisible" -> stateTest(name, args)
        "age" -> {
            val sim = simulator?.simulation
            if (args.size >= 2 && sim != null && simulator != null) {
                // age(x, y): age of the agent at that cell, 0 when the cell is empty
                val index = sim.toIndex(wrapCoordinate(args[0].int, sim.width), wrapCoordinate(args[1].int, sim.height))
                (if (simulator.agents[index] >= 0) simulator.ages[index] else 0).v
            } else {
                // 1-based: age(1) = main agent, age(2..N+1) = neighbours of the reactions
                (ages.getOrNull(args[0].int - 1) ?: 0).v
            }
        }
        else -> super.function(name, args)
    }

    /**
     * Evaluates a state test (`IsBehaviourActive`, `IsModifierActive`, `IsVisible`, `IsToricSelectionOn`...).
     *
     * Those tests read the state of the simulation or of its view, which does not belong to the
     * cell of a behaviour: they are accepted in the condition of a simulation modifier only. An
     * out-of-context call throws instead of silently answering 0, so the validators report it.
     * A missing simulator (formula validation) answers with the safe default of each test.
     */
    private fun stateTest(name: String, args: List<Value>): Value {
        if (!stateTestsAllowed) throw EvaluationException(
            "$name is a state test: it may only be used in the condition of a simulation modifier"
        )
        val sim = simulator
        return when (name) {
            "IsToricSelectionOn" -> {
                expectArity(name, args, 0)
                (sim != null && sim.toricSelectionOn).v
            }
            "IsOverwritingOn" -> {
                expectArity(name, args, 0)
                (sim != null && sim.overwritingOn).v
            }
            // nothing is hidden by default: without a simulator every grain is visible
            "IsVisible" -> {
                // the argument is checked even without a simulator: a formula is validated first
                val id = grainIdArgument(name, args)
                (sim == null || sim.isGrainVisible(id)).v
            }
            "IsBehaviourActive" -> {
                val id = grainIdArgument(name, args)
                (sim != null && sim.isRuntimeEnabled(id)).v
            }
            "IsModifierActive" -> {
                val id = grainIdArgument(name, args)
                (sim != null && sim.isModifierUsed(id)).v
            }
            else -> throw EvaluationException("unknown state test: $name")
        }
    }

    private fun expectArity(name: String, args: List<Value>, count: Int) {
        if (args.size != count) throw EvaluationException(
            "$name expects $count argument${if (count == 1) "" else "s"}, got ${args.size}"
        )
    }

    /** The single integer argument of a state test, with a clear message when it is missing or boolean. */
    private fun grainIdArgument(name: String, args: List<Value>): Int {
        expectArity(name, args, 1)
        val value = args[0]
        if (value is BooleanValue) throw EvaluationException("$name expects an integer id, not a boolean")
        return value.int
    }

    /**
     * `onAgent(id)`: true when at least one of the eight neighbours of the current cell holds an
     * agent of that grain. The cell itself is not counted, exactly like `count(grainId)`: it
     * reads the same neighbour counts, which already exclude it and already respect the boundary
     * conditions of the behaviour.
     *
     * `onAgent(id, dx, dy)`: the same test on a single cell, at the offset (dx, dy) from the
     * current cell, the cell itself included for (0, 0). The offsets are limited to -1, 0 and 1
     * (an out-of-range offset is a formula error, rejected by [onAgentError]) and they wrap
     * around the grid like every other coordinate of this evaluator.
     *
     * A formula without a simulator (validation) answers false: the arguments are still checked.
     */
    private fun onAgent(args: List<Value>): Value {
        if (args.size != 1 && args.size != 3) throw EvaluationException(
            "onAgent expects 1 argument (onAgent(id), the eight neighbours) or 3 (onAgent(id, dx, dy), a single cell), got ${args.size}"
        )
        val grainId = args[0].int
        if (args.size == 1) return ((agentCounts[grainId] ?: 0) > 0).v
        val dx = onAgentOffset("dx", args[1])
        val dy = onAgentOffset("dy", args[2])
        val sim = simulator ?: return false.v
        val simulation = sim.simulation
        val px = wrapCoordinate(x.int + dx, simulation.width)
        val py = wrapCoordinate(y.int + dy, simulation.height)
        return (sim.agents[simulation.toIndex(px, py)] == grainId).v
    }

    /**
     * `hasAnyOf({0,1})`, `hasAllOf({0,1})`, `hasOnly({0,1})`, `hasExactly({0,1})`: set predicates
     * over the agent **types** present in the eight neighbours of the current cell.
     *
     * The list is turned into a bit mask (bit `id` for the type `id`), and so is the
     * neighbourhood ([neighbourTypesMask]); the four answers are then plain bit tests over two
     * ordinary numbers. The cell itself is not counted, like `count()`, and the edges wrap
     * around the grid.
     *
     * The empty list is refused by the validator: `hasAnyOf({})` would always be false and
     * `hasAllOf({})` always true, two conventions for the same notation. A list used anywhere
     * else, a computed id or an id outside 0..[NEIGHBOUR_SET_MAX_TYPES] - 1 is refused too, at
     * run time as well as in the validation.
     */
    private fun neighbourSet(name: String, call: FunctionCall): Value {
        neighbourSetCallError(call)?.let { throw EvaluationException(it) }
        val requested = (call.arguments[0] as ListLiteral).grainMask()
        val present = neighbourTypesMask
        return when (name) {
            "hasAnyOf" -> ((present and requested) != 0).v
            "hasAllOf" -> ((present and requested) == requested).v
            // no type outside the list: the listed ones may all be absent
            "hasOnly" -> ((present and requested) == present).v
            "hasExactly" -> (present == requested).v
            else -> throw EvaluationException("unknown set predicate: $name")
        }
    }

    /**
     * `count({0,1,3})`: number of neighbours whose grain is one of the listed types.
     *
     * The four `has…` predicates answer a truth value over the *set* of types present; this one
     * sums the per-type counts, because multiplicity matters — three neighbours of type 0 and
     * one of type 1 make four, not two. The list is deduplicated: `count({0,0})` is `count(0)`.
     *
     * Unlike the mask predicates there is no limit on the number of ids: no mask is built.
     */
    private fun countOfSet(call: FunctionCall): Value {
        grainListError(call, limitTypes = false)?.let { throw EvaluationException(it) }
        val list = call.arguments[0] as ListLiteral
        val seen = mutableSetOf<Int>()
        var total = 0
        for (element in list.elements) {
            val id = element.constantNumberOrNull()?.toInt() ?: continue
            if (seen.add(id)) total += agentCounts[id] ?: 0
        }
        return total.v
    }

    /**
     * Offset argument of `onAgent(id, dx, dy)`: -1, 0 or 1.
     *
     * A dynamic function is never checked for arity by [Evaluator.check]: a wrong call reaches
     * the run time, where the simulator silently ignores the formula. An offset outside [-1, 1]
     * is refused here rather than reading an arbitrary cell: two cells away, the offset wraps
     * around a small grid and answers about the wrong neighbour without any sign of error.
     */
    private fun onAgentOffset(label: String, value: Value): Int {
        if (value is BooleanValue) throw EvaluationException(
            "onAgent: $label must be -1, 0 or 1, not a boolean"
        )
        val amount = value.float
        if (amount != floor(amount)) throw EvaluationException(
            "onAgent: $label must be a whole number (-1, 0 or 1), got $amount"
        )
        val offset = amount.toInt()
        if (offset < -1 || offset > 1) throw EvaluationException(
            "onAgent: $label must be -1, 0 or 1, got $offset"
        )
        return offset
    }
}

/**
 * Names of the state tests: boolean functions that read the state of the simulation or of its
 * view (activation of a behaviour or of a modifier, visibility of a grain, paste options).
 *
 * They are accepted **only** in the condition of a simulation modifier: an action formula and
 * the condition of a behaviour refuse them (see [validateActionFormula] and
 * [validatePredicateFormula]).
 */
val stateTestFunctions: Set<String> = setOf(
    "IsBehaviourActive", "IsModifierActive",
    "IsToricSelectionOn", "IsOverwritingOn", "IsVisible"
)

/** Name of the first state test called by this expression, `null` when it calls none. */
fun Expression.stateTestName(): String? = stateTestFunctions.firstOrNull { containsFunctionCall(it) }

/** Name of the first state test called by this formula, `null` when none (or when it does not parse). */
fun String.stateTestUsedName(): String? =
    try { parseExpression().stateTestName() } catch (e: Exception) { null }

/** Message returned when a state test is written outside the condition of a simulation modifier. */
fun misplacedStateTest(name: String, context: String): String =
    "$name is a state test: it belongs to the condition of a simulation modifier, not to $context"

fun createEvaluator(simulator: Simulator) = NetbioDynEvaluator(simulator)

fun createEvaluator() = NetbioDynEvaluator(null)

/**
 * Positive modulo: wraps [v] into `[0, n)` — toroidal coordinates (see
 * [com.centyllion.model.Simulation.moveIndex]).
 *
 * A negative value comes back from the opposite edge (-1 becomes n - 1), a value past the edge
 * restarts at 0. Used by the model functions that take coordinates and by the toric paste of
 * the view (a paste that overflows the grid is folded instead of being clipped).
 */
fun wrapCoordinate(v: Int, n: Int): Int = if (n <= 0) 0 else ((v % n) + n) % n

/**
 * Error message of the first malformed `onAgent(...)` call of this expression, `null` when all
 * of them are well formed.
 *
 * The dynamic functions (`count`, `field`, `agent`...) have no arity check of their own: only
 * the name of a function is checked by [Evaluator.check], and a wrong call fails at run time —
 * silently for the simulator, which makes the behaviour simply never apply. `onAgent` also
 * restricts its two offsets to [-1, 1], so the check is done here, in the formula validation:
 * without it `onAgent(0, 5, 5)` would be accepted and would then read an arbitrary cell of the
 * grid (and, on a grid of 5 cells wide, wrap back to a plausible-looking neighbour).
 *
 * A constant offset is checked; a computed one (`onAgent(0, step, 0)`) is only checked when the
 * formula runs, where [NetbioDynEvaluator] throws the same kind of message.
 */
fun Expression.onAgentError(): String? {
    if (this is FunctionCall && name.content == "onAgent") onAgentCallError(arguments)?.let { return it }
    for (child in children) {
        if (child is Expression) child.onAgentError()?.let { return it }
    }
    return null
}

private fun onAgentCallError(arguments: List<Expression>): String? {
    if (arguments.size == 1) return null
    if (arguments.size != 3) return "onAgent expects 1 argument (onAgent(id), the eight neighbours) or 3 (onAgent(id, dx, dy), a single cell), got ${arguments.size}"
    for ((label, argument) in listOf("dx" to arguments[1], "dy" to arguments[2])) {
        val value = argument.constantNumberOrNull() ?: continue
        if (value != floor(value) || value < -1.0 || value > 1.0) {
            val shown = if (value == floor(value)) value.toInt().toString() else value.toString()
            return "onAgent: the offset $label must be -1, 0 or 1, got $shown"
        }
    }
    return null
}

/**
 * Number of agent types the set predicates can represent: one bit per type in a 32-bit integer,
 * bit 31 being the sign bit. Ids 0 to 29 are therefore usable, and a model that declares more
 * than [NEIGHBOUR_SET_MAX_TYPES] grains cannot be described by `hasAnyOf`, `hasAllOf`, `hasOnly`
 * or `hasExactly` — the model diagnostic says so ([com.centyllion.model.GrainModel.diagnose]),
 * because a mask that silently drops a type would answer a wrong result without any sign of it.
 */
const val NEIGHBOUR_SET_MAX_TYPES: Int = 30

/**
 * Names of the four set predicates over the agent types present in the eight neighbours of the
 * current cell. They are lower case, like every other formula function (the upper case is
 * reserved for the meta-commands and the state tests).
 *
 * The names say the direction of the comparison instead of `isSubsetOf`/`isSupersetOf`, whose
 * subject is ambiguous: `hasAnyOf` = at least one listed type is there, `hasAllOf` = every listed
 * type is there, `hasOnly` = no type outside the list is there, `hasExactly` = both.
 */
val neighbourSetFunctions: Set<String> = setOf("hasAnyOf", "hasAllOf", "hasOnly", "hasExactly")

/** Name of the first set predicate called by this expression, `null` when it calls none. */
fun Expression.neighbourSetName(): String? = neighbourSetFunctions.firstOrNull { containsFunctionCall(it) }

/** Name of the first set predicate called by this formula, `null` when none (or when it does not parse). */
fun String.neighbourSetUsedName(): String? =
    try { parseExpression().neighbourSetName() } catch (e: Exception) { null }

/** True when this expression calls one of the set predicates (see [neighbourSetFunctions]). */
fun Expression.neighbourSetUsed(): Boolean = neighbourSetName() != null

/**
 * Mask of the agent ids of a grain list already accepted by [neighbourSetCallError]: bit `id`
 * is set for every listed id, and two listings of the same id are one bit.
 *
 * The ids are limited to 0..[NEIGHBOUR_SET_MAX_TYPES] - 1: `1 shl 31` is the sign bit of the
 * 32-bit integer used at run time, so an id past the limit would wrap into a type that is not
 * the one written — a wrong answer with no sign of it. Refused here, and by the validation.
 */
private fun ListLiteral.grainMask(): Int {
    var mask = 0
    for (element in elements) {
        val value = element.constantNumberOrNull()
            ?: throw EvaluationException("A grain list holds constant agent ids only, e.g. {0,1}")
        if (value != floor(value)) throw EvaluationException("A grain list holds whole agent ids only, got $value")
        val id = value.toInt()
        if (id < 0 || id >= NEIGHBOUR_SET_MAX_TYPES) throw EvaluationException(
            "Agent id $id cannot be represented in a grain list (ids 0 to ${NEIGHBOUR_SET_MAX_TYPES - 1})"
        )
        mask = mask or (1 shl id)
    }
    return mask
}

/**
 * Error message of the first malformed set predicate call of this expression, `null` when all of
 * them are well formed.
 *
 * Two rules are enforced here, both impossible to recover from at run time:
 * - a list `{0,1,3}` is accepted **only** as the single argument of [neighbourSetFunctions]; used
 *   as a value it would be read as its mask (`count(0) + {1}` would add 2), and the type checker
 *   has nothing to say about it since a mask is an ordinary number;
 * - the list is neither empty nor made of non-existent ids. `{}` is refused because the two
 *   conventions collide (`hasAnyOf({})` always false, `hasAllOf({})` always true), and a computed
 *   or out-of-range id would silently answer about another type.
 */
fun Expression.neighbourSetError(): String? {
    if (this is FunctionCall && name.content in neighbourSetFunctions) {
        // the single argument is a list, checked as a whole: it is not walked into again
        return neighbourSetCallError(this)
    }
    // count({0,1}) takes a grain list too, but sums counts instead of testing a mask:
    // no width limit applies there
    if (this is FunctionCall && name.content == "count" && arguments.singleOrNull() is ListLiteral) {
        return grainListError(this, limitTypes = false)
    }
    if (this is ListLiteral) {
        return "A list {…} may only be the argument of count, hasAnyOf, hasAllOf, hasOnly or hasExactly"
    }
    for (child in children) {
        if (child is Expression) child.neighbourSetError()?.let { return it }
    }
    return null
}

private fun neighbourSetCallError(call: FunctionCall): String? = grainListError(call, limitTypes = true)

/**
 * Validates a grain list argument. [limitTypes] is false for `count({…})`, which sums counts
 * instead of building the 30-bit mask of the `has…` predicates: there is no width limit there.
 */
private fun grainListError(call: FunctionCall, limitTypes: Boolean): String? {
    val name = call.name.content
    if (call.arguments.size != 1) return "$name expects one grain list, e.g. $name({0,1}), got ${call.arguments.size} arguments"
    val list = call.arguments[0] as? ListLiteral
        ?: return "$name expects a grain list literal, e.g. $name({0,1}), not a computed value"
    if (list.elements.isEmpty()) return "$name: the empty list {} is not allowed (write at least one id, e.g. $name({0}))"
    for (element in list.elements) {
        if (element is ListLiteral) return "$name: a grain list cannot contain another list"
        val value = element.constantNumberOrNull()
            ?: return "$name: a grain list holds constant agent ids only, e.g. {0,1}"
        if (value != floor(value)) return "$name: a grain list holds whole agent ids only, got $value"
        val id = value.toInt()
        if (id < 0) return "$name: a grain list names agent types, not the sentinels -1 (all agents) and -2 (empty cells); use count(-1) for all agents, got $id"
        if (limitTypes && id >= NEIGHBOUR_SET_MAX_TYPES) return "$name: agent id $id cannot be represented in a grain list (ids 0 to ${NEIGHBOUR_SET_MAX_TYPES - 1})"
    }
    return null
}

/** Ids named by the grain lists of this expression, in the order they appear. */
fun Expression.neighbourSetGrainIds(): List<Int> {
    val ids = mutableListOf<Int>()
    fun collect(expression: Expression) {
        if (expression is FunctionCall && expression.name.content in neighbourSetFunctions) {
            val list = expression.arguments.singleOrNull() as? ListLiteral
            if (list != null) {
                for (element in list.elements) {
                    element.constantNumberOrNull()?.let { ids.add(it.toInt()) }
                }
            }
            return
        }
        for (child in expression.children) if (child is Expression) collect(child)
    }
    collect(this)
    return ids
}

/**
 * Validates a formula: it must parse, reference only known functions and variables, and
 * evaluate without error. Returns an error message, or null when valid.
 *
 * [allowStateTests] opens the grammar to the state tests (`IsBehaviourActive`, `IsVisible`...). They are
 * refused by default because the only place they belong to is the condition of a simulation
 * modifier: see [validateModifierCondition], [validatePredicateFormula] and
 * [validateActionFormula].
 *
 * [grainIds] lets the caller check the ids of the grain lists (`hasAnyOf({0,1})`) against the
 * model: without it (a formula edited before the model is known) the syntax is checked but an
 * id that no grain carries is only caught by the model diagnostic, which knows the grains.
 */
fun validateFormula(formula: String, allowStateTests: Boolean = false, grainIds: Set<Int>? = null): String? {
    // the state tests are evaluable during the check: the context rule is applied below, with a
    // message that names the function instead of a generic "function doesn't exist"
    val evaluator = createEvaluator().also { it.stateTestsAllowed = true }
    return try {
        val parsed = formula.parseExpression()
        // onAgent takes one argument or three, and its offsets are limited to [-1, 1]: the
        // dynamic functions have no arity check of their own, so the call is verified here
        parsed.onAgentError()?.let { return it }
        // a grain list is an argument of the four set predicates and nothing else
        parsed.neighbourSetError()?.let { return it }
        if (grainIds != null) {
            parsed.neighbourSetGrainIds().firstOrNull { it !in grainIds }?.let {
                return "Grain with id $it doesn't exist for this grain list"
            }
        }
        val errors = evaluator.check(parsed)
        if (errors.isNotEmpty()) return errors.first().message
        if (!allowStateTests) parsed.stateTestName()?.let { return misplacedStateTest(it, "a formula") }
        evaluator.evaluate(parsed)
        null
    } catch (e: Exception) {
        e.message
    }
}

/**
 * Validates the condition of a simulation modifier: the state tests are accepted there, and
 * only there. Returns an error message, or null when valid.
 *
 * The set predicates over the neighbourhood are refused: they read the eight neighbours of a
 * cell, and a modifier runs once per step, outside any cell — the counts are not filled, so the
 * test would answer false without any sign of it.
 */
fun validateModifierCondition(formula: String): String? {
    formula.neighbourSetUsedName()?.let {
        return "$it reads the eight neighbours of a cell: it belongs to the formula of a behaviour or of a field, not to the condition of a simulation modifier"
    }
    return validateFormula(formula, allowStateTests = true)
}


/**
 * Validates a behaviour condition formula: it must parse, reference only
 * known functions and variables, and evaluate to a boolean (logical predicate).
 * `field(id)`, `field(id, dx, dy)` and `agent(x, y)` are allowed (the simulator
 * state is available when the formula is evaluated). The state tests (`Is...`) are refused:
 * they read the simulation as a whole and belong to the condition of a modifier.
 * Returns an error message, or null when valid.
 */
fun validatePredicateFormula(formula: String, grainIds: Set<Int>? = null): String? {
    // explicit message: a state test here is a mistake, not an unknown function
    formula.stateTestUsedName()?.let { return misplacedStateTest(it, "the condition of a behaviour") }
    val error = validateFormula(formula, grainIds = grainIds)
    if (error != null) return error
    return try {
        if (formula.parseExpression().inferType() != ExprType.Boolean) {
            "Formula must be a logical predicate returning true or false (e.g. count(0) >= 3 && step > 10)"
        } else null
    } catch (e: Exception) {
        e.message
    }
}

/**
 * Validates a field formula: it must parse, reference only known functions
 * and variables, and evaluate to a number.
 * Returns an error message, or null when valid.
 */
fun validateNumericFormula(formula: String, grainIds: Set<Int>? = null): String? {
    val error = validateFormula(formula, grainIds = grainIds)
    if (error != null) return error
    return try {
        if (formula.parseExpression().inferType() != ExprType.Number) {
            "Formula must return a number (e.g. current * 0.9)"
        } else null
    } catch (e: Exception) {
        e.message
    }
}

object SimpleFieldEvaluator : FieldEvaluator {
    override fun evaluateField(/*step: Int, position: Position, */current: Float): Float = current

}

class FormulaFieldEvaluator(
    val field: Field,
    val formula: String,
    val evaluator: NetbioDynEvaluator
) : FieldEvaluator {

    private val expression = formula.parseExpression()

    /**
     * True when the formula reads the neighbour agents of the cell (the counts published by the
     * simulator): `count()`, `countEmpty()`, `onAgent`, whose one-argument form answers about
     * the eight neighbours, and the four set predicates over the agent types present there.
     */
    val usesCount: Boolean = expression.containsFunctionCall("count") ||
            expression.containsFunctionCall("countEmpty") ||
            expression.containsFunctionCall("onAgent") ||
            expression.neighbourSetUsed()

    override fun evaluateField(/*step: Int, position: Position, */current: Float): Float {
        evaluator.current = current.v
        return evaluator.evaluate(expression).float
    }

}