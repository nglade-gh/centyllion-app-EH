package com.centyllion.expression

import kotlin.math.pow

/** Type of the value an expression evaluates to. */
enum class ExprType {
    /** The expression evaluates to a number (integer or float). */
    Number,
    /** The expression evaluates to a boolean. */
    Boolean
}

/**
 * Statically infers the type of the value produced by [this] expression.
 *
 * All variables and functions are numeric, so an expression is [ExprType.Boolean]
 * only when its result comes from a boolean literal, a comparison, a logical
 * operator (`&&`, `||`, `!`) or a ternary expression with boolean branches.
 *
 * A ternary expression must have both branches of the same type; a mismatch
 * throws [IllegalStateException] (reported by the formula validators). The
 * condition itself is not constrained: at runtime a condition is true only
 * when it evaluates to `true`, so numeric conditions are tolerated.
 *
 * Used to check that a behaviour condition formula is a logical predicate
 * (returns true/false) and that a field formula returns a number.
 */
/** Functions of the model language that return a truth value instead of a number. */
private val booleanFunctions = setOf(
    // state tests (condition of a simulation modifier only, see model.stateTestFunctions)
    "IsBehaviourActive", "IsModifierActive",
    "IsToricSelectionOn", "IsOverwritingOn", "IsVisible"
)

fun Expression.inferType(): ExprType = when (this) {
    is BooleanLiteral -> ExprType.Boolean
    is NumberLiteral -> ExprType.Number
    is ParenthesisExpression -> child.inferType()
    is UnaryExpression -> when (operator) {
        UnaryOperator.Not -> ExprType.Boolean
        UnaryOperator.Plus, UnaryOperator.Minus -> ExprType.Number
    }
    is BinaryExpression -> when (operator) {
        BinaryOperator.And, BinaryOperator.Or,
        BinaryOperator.GreaterOrEqualThan, BinaryOperator.GreaterThan,
        BinaryOperator.LesserOrEqualThan, BinaryOperator.LesserThan,
        BinaryOperator.Equal, BinaryOperator.NotEqual -> ExprType.Boolean
        BinaryOperator.Plus, BinaryOperator.Minus, BinaryOperator.Times,
        BinaryOperator.Div, BinaryOperator.Rem -> ExprType.Number
    }
    is ExponentExpression -> ExprType.Number
    // the activation tests return a truth value: they can be used directly as predicates
    is FunctionCall -> if (name.content in booleanFunctions) ExprType.Boolean else ExprType.Number
    is VariableCall -> ExprType.Number
    is TernaryExpression -> {
        val thenType = ifThen.inferType()
        val elseType = orElse.inferType()
        if (thenType != elseType) {
            throw IllegalStateException("Ternary branches must have the same type (number or boolean)")
        }
        thenType
    }
}

/**
 * Value of [this] expression when it is a constant, or null when it depends on the state of the
 * simulation (variables, functions, state tests).
 *
 * Only literals combined with arithmetic operators are folded. The parser uses it to refuse an
 * argument once and for all when it can: the duration of `Pause(t)` must be greater than 0, and
 * a duration written `2.5` or `5 / 2` is checked at parse time, while `step / 10` can only be
 * checked when the action runs.
 */
fun Expression.constantNumberOrNull(): Double? = when (this) {
    is NumberLiteral -> value.float.toDouble()
    is ParenthesisExpression -> child.constantNumberOrNull()
    is UnaryExpression -> when (operator) {
        UnaryOperator.Plus -> child.constantNumberOrNull()
        UnaryOperator.Minus -> child.constantNumberOrNull()?.let { -it }
        UnaryOperator.Not -> null
    }
    is ExponentExpression -> {
        val base = decimal.constantNumberOrNull()
        val power = exponent.constantNumberOrNull()
        if (base == null || power == null) null else base.pow(power)
    }
    is BinaryExpression -> {
        val leftValue = left.constantNumberOrNull()
        val rightValue = right.constantNumberOrNull()
        if (leftValue == null || rightValue == null) null else when (operator) {
            BinaryOperator.Plus -> leftValue + rightValue
            BinaryOperator.Minus -> leftValue - rightValue
            BinaryOperator.Times -> leftValue * rightValue
            // a division by zero is left to the run time, which reports it in its own way
            BinaryOperator.Div -> if (rightValue == 0.0) null else leftValue / rightValue
            BinaryOperator.Rem -> if (rightValue == 0.0) null else leftValue % rightValue
            // comparisons and logical operators do not produce a number
            else -> null
        }
    }
    is TernaryExpression -> {
        val condition = condition.constantNumberOrNull()
        when {
            condition == null -> null
            condition != 0.0 -> ifThen.constantNumberOrNull()
            else -> orElse.constantNumberOrNull()
        }
    }
    is BooleanLiteral, is VariableCall, is FunctionCall -> null
}

/** True when the expression tree contains a call to the function [name]. */
fun Expression.containsFunctionCall(name: String): Boolean = when (this) {
    is FunctionCall -> this.name.content == name || arguments.any { it.containsFunctionCall(name) }
    is ParenthesisExpression -> child.containsFunctionCall(name)
    is UnaryExpression -> child.containsFunctionCall(name)
    is BinaryExpression -> left.containsFunctionCall(name) || right.containsFunctionCall(name)
    is ExponentExpression -> decimal.containsFunctionCall(name) || exponent.containsFunctionCall(name)
    is TernaryExpression ->
        condition.containsFunctionCall(name) ||
                ifThen.containsFunctionCall(name) ||
                orElse.containsFunctionCall(name)
    is VariableCall -> false
    is BooleanLiteral -> false
    is NumberLiteral -> false
}
