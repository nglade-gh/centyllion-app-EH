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
        "agent", "field", "count", "countEmpty", "age",
        // activation tests, available in every predicate and every action
        "IsActive", "IsInactive", "IsInUse", "IsNotInUse"
    )

    /** True when x, y, z are not the cell of a behaviour (simulation modifier). */
    var absoluteCoordinates: Boolean = false

    /** Names of variables provided by this evaluator (in addition to [variables]) */
    private val dynamicVariables = setOf("step", "x", "y", "z", "current")

    override fun hasFunction(name: String): Boolean =
        dynamicFunctions.contains(name) || super.hasFunction(name)

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
        // activation tests: 1 when the condition holds, 0 otherwise
        "IsActive" -> {
            val sim = simulator
            (sim != null && sim.isRuntimeEnabled(args[0].int)).v
        }
        "IsInactive" -> {
            val sim = simulator
            (sim != null && !sim.isRuntimeEnabled(args[0].int)).v
        }
        "IsInUse" -> {
            val sim = simulator
            (sim != null && sim.isModifierUsed(args[0].int)).v
        }
        "IsNotInUse" -> {
            val sim = simulator
            (sim != null && !sim.isModifierUsed(args[0].int)).v
        }
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
}

fun createEvaluator(simulator: Simulator) = NetbioDynEvaluator(simulator)

fun createEvaluator() = NetbioDynEvaluator(null)

/** Positive modulo: wraps [v] into [0, n) — toroidal coordinates (see [com.centyllion.model.Simulation.moveIndex]). */
private fun wrapCoordinate(v: Int, n: Int): Int = ((v % n) + n) % n

fun validateFormula(formula: String): String? {
    val evaluator = createEvaluator()
    return try {
        val parsed = formula.parseExpression()
        val errors = evaluator.check(parsed)
        if (errors.isNotEmpty()) return errors.first().message
        evaluator.evaluate(parsed)
        null
    } catch (e: Exception) {
        e.message
    }
}

/**
 * Validates a behaviour condition formula: it must parse, reference only
 * known functions and variables, and evaluate to a boolean (logical predicate).
 * `field(id)`, `field(id, dx, dy)` and `agent(x, y)` are allowed (the simulator
 * state is available when the formula is evaluated).
 * Returns an error message, or null when valid.
 */
fun validatePredicateFormula(formula: String): String? {
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