package com.centyllion.expression

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
    is FunctionCall -> ExprType.Number
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
