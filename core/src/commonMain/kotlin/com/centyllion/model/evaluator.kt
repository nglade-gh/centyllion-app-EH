package com.centyllion.model

import com.centyllion.expression.*

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
        "agent", "field", "count", "countEmpty", "age"
    )

    /**
     * True while evaluating the condition of a simulation modifier: the only place where the
     * state tests ([stateTestFunctions]) are allowed. It is a flag and not a different evaluator
     * because the same instance serves the behaviours, the fields and the modifiers of a step
     * (like [absoluteCoordinates]).
     */
    var stateTestsAllowed: Boolean = false

    /** True when x, y, z are not the cell of a behaviour (simulation modifier). */
    var absoluteCoordinates: Boolean = false

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
 * Validates a formula: it must parse, reference only known functions and variables, and
 * evaluate without error. Returns an error message, or null when valid.
 *
 * [allowStateTests] opens the grammar to the state tests (`IsBehaviourActive`, `IsVisible`...). They are
 * refused by default because the only place they belong to is the condition of a simulation
 * modifier: see [validateModifierCondition], [validatePredicateFormula] and
 * [validateActionFormula].
 */
fun validateFormula(formula: String, allowStateTests: Boolean = false): String? {
    // the state tests are evaluable during the check: the context rule is applied below, with a
    // message that names the function instead of a generic "function doesn't exist"
    val evaluator = createEvaluator().also { it.stateTestsAllowed = true }
    return try {
        val parsed = formula.parseExpression()
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
 */
fun validateModifierCondition(formula: String): String? = validateFormula(formula, allowStateTests = true)


/**
 * Validates a behaviour condition formula: it must parse, reference only
 * known functions and variables, and evaluate to a boolean (logical predicate).
 * `field(id)`, `field(id, dx, dy)` and `agent(x, y)` are allowed (the simulator
 * state is available when the formula is evaluated). The state tests (`Is...`) are refused:
 * they read the simulation as a whole and belong to the condition of a modifier.
 * Returns an error message, or null when valid.
 */
fun validatePredicateFormula(formula: String): String? {
    // explicit message: a state test here is a mistake, not an unknown function
    formula.stateTestUsedName()?.let { return misplacedStateTest(it, "the condition of a behaviour") }
    val error = validateFormula(formula)
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
fun validateNumericFormula(formula: String): String? {
    val error = validateFormula(formula)
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

    /** True when the formula uses the count() function (needs neighbour agent counts). */
    val usesCount: Boolean = expression.containsFunctionCall("count") || expression.containsFunctionCall("countEmpty")

    override fun evaluateField(/*step: Int, position: Position, */current: Float): Float {
        evaluator.current = current.v
        return evaluator.evaluate(expression).float
    }

}