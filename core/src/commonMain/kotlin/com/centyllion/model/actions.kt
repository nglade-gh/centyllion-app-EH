package com.centyllion.model

import com.centyllion.expression.ExprType
import com.centyllion.expression.Expression
import com.centyllion.expression.inferType
import com.centyllion.expression.parseExpression

/**
 * Imperative action executed by a behaviour when it is applied.
 *
 * Arguments are numeric expressions evaluated at execution time; the variables
 * `step`, `x`, `y` (position of the behaviour cell) are available, as well as
 * the mathematical functions and the `field(id, dx, dy)` model function.
 */
sealed class Action {
    /** Adds an agent of type `grain` at offset (dx, dy) from the behaviour cell (empty target only). */
    data class Add(val grain: Expression, val dx: Expression, val dy: Expression) : Action()

    /** Removes the agent of type `grain` at offset (dx, dy) from the behaviour cell. */
    data class Remove(val grain: Expression, val dx: Expression, val dy: Expression) : Action()

    /** Moves the agent of type `grain` from offset (dx1, dy1) to offset (dx2, dy2) of the behaviour cell. */
    data class Move(
        val grain: Expression,
        val dx1: Expression, val dy1: Expression,
        val dx2: Expression, val dy2: Expression
    ) : Action()

    /** Sets the value of field `field` at offset (dx, dy) to `value` (clamped to [0, 1]). */
    data class SetField(val field: Expression, val dx: Expression, val dy: Expression, val value: Expression) : Action()

    /** Adds `delta` to the value of field `field` at offset (dx, dy) (result clamped to [0, 1]). */
    data class AddField(val field: Expression, val dx: Expression, val dy: Expression, val delta: Expression) : Action()

    /** Sets the value of field `field` at offset (dx, dy) to 0. */
    data class ClearField(val field: Expression, val dx: Expression, val dy: Expression) : Action()
}

private val actionArity = mapOf(
    "Add" to 3,
    "Remove" to 3,
    "Move" to 5,
    "SetField" to 4,
    "AddField" to 4,
    "ClearField" to 3
)

/** Splits [text] on top-level commas (commas inside parentheses are kept). */
private fun splitTopLevel(text: String): List<String> {
    val parts = mutableListOf<String>()
    var depth = 0
    var start = 0
    for (i in text.indices) {
        when (text[i]) {
            '(' -> depth += 1
            ')' -> depth -= 1
            ',' -> if (depth == 0) {
                parts.add(text.substring(start, i))
                start = i + 1
            }
        }
    }
    parts.add(text.substring(start))
    return parts.map { it.trim() }
}

private val actionCallPattern = Regex("""^(\w+)\s*\((.*)\)$""")

/**
 * Parses an actions formula: a comma separated list of action calls, e.g.
 * `Add(0, 1, 0), Remove(1, 0, 0), Move(0, 0, 0, 1, 0)`.
 *
 * Throws [IllegalArgumentException] with a descriptive message when the formula is invalid.
 */
fun String.parseActionFormula(): List<Action> {
    val trimmed = trim()
    if (trimmed.isEmpty()) return emptyList()

    return splitTopLevel(trimmed).map { part ->
        val match = actionCallPattern.find(part)
            ?: throw IllegalArgumentException(
                "Expected a list of actions like Add(0, 1, 0), Remove(1, 0, 0) — got '$part'"
            )
        val name = match.groupValues[1]
        val arity = actionArity[name]
            ?: throw IllegalArgumentException(
                "Unknown action '$name' (available: Add, Remove, Move, SetField, AddField, ClearField)"
            )
        val args = splitTopLevel(match.groupValues[2]).map { arg ->
            val expr = try {
                arg.parseExpression()
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid argument '$arg' in $name: ${e.message}")
            }
            if (expr.inferType() != ExprType.Number) {
                throw IllegalArgumentException("Argument '$arg' of $name must be a number")
            }
            expr
        }
        if (args.size != arity) {
            throw IllegalArgumentException("$name expects $arity arguments, got ${args.size}")
        }
        when (name) {
            "Add" -> Action.Add(args[0], args[1], args[2])
            "Remove" -> Action.Remove(args[0], args[1], args[2])
            "Move" -> Action.Move(args[0], args[1], args[2], args[3], args[4])
            "SetField" -> Action.SetField(args[0], args[1], args[2], args[3])
            "AddField" -> Action.AddField(args[0], args[1], args[2], args[3])
            else -> Action.ClearField(args[0], args[1], args[2])
        }
    }
}

/**
 * Validates an actions formula. Returns an error message, or null when valid.
 */
fun validateActionFormula(formula: String): String? =
    try {
        formula.parseActionFormula()
        null
    } catch (e: Exception) {
        e.message
    }
