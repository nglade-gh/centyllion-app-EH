package com.centyllion.client.tutorial

import com.centyllion.expression.Expression
import com.centyllion.expression.parseExpression
import com.centyllion.expression.v
import com.centyllion.model.Action
import com.centyllion.model.createEvaluator
import com.centyllion.model.parseActionFormula
import kotlin.math.roundToInt

/**
 * Helpers used by the expert tutorials to check that the formula the user typed
 * really has the expected semantics, instead of comparing its exact text.
 *
 * Everything is evaluated with a standalone `NetbioDynEvaluator` (no simulator):
 * `count(id)` comes from the neighbour counts passed in, `age(n)` from the ages
 * list, `step`, `x` and `y` from the arguments.
 */

/** Value of a predicate formula, or null when it can't be parsed/evaluated. */
fun predicateHolds(
    formula: String,
    counts: Map<Int, Int> = emptyMap(),
    ages: List<Int> = emptyList(),
    step: Int = 0
): Boolean? = runCatching {
    val evaluator = createEvaluator()
    evaluator.agentCounts = counts
    evaluator.ages = ages
    evaluator.step = step.v
    evaluator.evaluate(formula.parseExpression()).boolean
}.getOrNull()

/** Value of a field (numeric) formula at a given position and step. */
fun fieldFormulaValue(
    formula: String,
    x: Int = 0,
    y: Int = 0,
    step: Int = 0,
    current: Float = 0f
): Float? = runCatching {
    val evaluator = createEvaluator()
    evaluator.x = x.v
    evaluator.y = y.v
    evaluator.step = step.v
    evaluator.current = current.v
    evaluator.evaluate(formula.parseExpression()).float
}.getOrNull()

/**
 * Checks a rule expressed with `count(0)`: the formula must be true for all the
 * [trueFor] neighbour counts and false for all the [falseFor] ones. Any
 * equivalent way of writing it is accepted.
 */
fun matchesCountRule(formula: String, trueFor: Set<Int>, falseFor: Set<Int>): Boolean {
    if (formula.isBlank()) return false
    return trueFor.all { predicateHolds(formula, mapOf(0 to it)) == true } &&
            falseFor.all { predicateHolds(formula, mapOf(0 to it)) == false }
}

/** Checks a rule expressed with `age(1)`: true at [aliveAge], false at [deadAge]. */
fun matchesAgeRule(formula: String, aliveAge: Int, deadAge: Int): Boolean {
    if (formula.isBlank()) return false
    return predicateHolds(formula, ages = listOf(aliveAge)) == true &&
            predicateHolds(formula, ages = listOf(deadAge)) == false
}

/** True when the actions formula adds an agent of [grainId] (offset is not checked). */
fun actionAddsGrain(formula: String, grainId: Int): Boolean = runCatching {
    formula.parseActionFormula().any { action ->
        action is Action.Add && action.grain.constantInt() == grainId
    }
}.getOrDefault(false)

/** True when the actions formula removes an agent of [grainId]. */
fun actionRemovesGrain(formula: String, grainId: Int): Boolean = runCatching {
    formula.parseActionFormula().any { action ->
        action is Action.Remove && action.grain.constantInt() == grainId
    }
}.getOrDefault(false)

/** Evaluates an action argument as a constant (null when it depends on the simulation state). */
private fun Expression.constantInt(): Int? = runCatching {
    createEvaluator().evaluate(this).float.roundToInt()
}.getOrNull()

/**
 * True when a field formula varies both in space (along x) and in time (with step):
 * the expected behaviour of the "moving gradient" tutorial.
 */
fun isMovingGradient(formula: String): Boolean {
    if (formula.isBlank()) return false
    val left = fieldFormulaValue(formula, x = 0, y = 0, step = 0) ?: return false
    val right = fieldFormulaValue(formula, x = 20, y = 0, step = 0) ?: return false
    val later = fieldFormulaValue(formula, x = 10, y = 10, step = 20) ?: return false
    val now = fieldFormulaValue(formula, x = 10, y = 10, step = 0) ?: return false
    return kotlin.math.abs(left - right) > 0.2f && kotlin.math.abs(now - later) > 0.2f
}
