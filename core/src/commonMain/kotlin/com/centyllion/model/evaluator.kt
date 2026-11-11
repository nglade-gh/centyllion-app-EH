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

    /** Names of functions provided by this evaluator (in addition to [functions]) */
    private val dynamicFunctions = setOf("agent", "field", "count")

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
                val px = wrapCoordinate(if (args.size >= 2) x.int + args[1].int else x.int, sim.width)
                val py = wrapCoordinate(if (args.size >= 3) y.int + args[2].int else y.int, sim.height)
                val values = simulator.fields[id]
                val result = values?.get(sim.toIndex(px, py)) ?: 0f
                result.v
            } else Value.Zero
        }
        "count" -> {
            val grainId = args[0].int
            (agentCounts[grainId] ?: 0).v
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
    val usesCount: Boolean = expression.containsFunctionCall("count")

    override fun evaluateField(/*step: Int, position: Position, */current: Float): Float {
        evaluator.current = current.v
        return evaluator.evaluate(expression).float
    }

}