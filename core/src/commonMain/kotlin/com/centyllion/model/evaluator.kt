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
                val index = simulator.simulation.toIndex(args[0].int, args[1].int)
                val result = if (index >= 0 && index < simulator.agents.size) simulator.agents[index] else -1
                result.v
            } else Value.Zero
        }
        "field" -> {
            if (simulator != null) {
                val id = args[0].int
                val index = simulator.simulation.toIndex(args[1].int, args[2].int)
                simulator.fields[id]?.let { values ->
                    val result = if (index >= 0 && index < values.size) values[index] else 0f
                    result.v
                } ?: Value.Zero
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
 * Returns an error message, or null when valid.
 */
fun validatePredicateFormula(formula: String): String? {
    val error = validateFormula(formula)
    if (error != null) return error
    return try {
        val parsed = formula.parseExpression()
        if (parsed.containsFunctionCall("field") || parsed.containsFunctionCall("agent")) {
            "field(...) and agent(...) are not available in behaviour formulas (use the field thresholds)"
        } else if (parsed.inferType() != ExprType.Boolean) {
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