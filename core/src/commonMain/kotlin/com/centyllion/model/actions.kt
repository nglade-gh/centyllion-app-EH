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

    // --- meta-actions: they act on the simulation itself, not on the current cell ---

    /** Pauses the simulation at the end of the current step. */
    object Pause : Action()

    /** Adds `count` agents of type `grain` anywhere in the environment (empty cells only). */
    data class AddMany(val grain: Expression, val count: Expression) : Action()

    /** Adds `count` agents of type `grain` in the rectangle (x1, y1)-(x2, y2). */
    data class AddRect(
        val grain: Expression, val count: Expression,
        val x1: Expression, val y1: Expression, val x2: Expression, val y2: Expression
    ) : Action()

    /** Adds `count` agents of type `grain` in the disc centred on (x, y). */
    data class AddDisc(
        val grain: Expression, val count: Expression,
        val x: Expression, val y: Expression, val radius: Expression
    ) : Action()

    /** Erases every agent (any type) in the rectangle (x1, y1)-(x2, y2). */
    data class EraseRectAll(
        val x1: Expression, val y1: Expression, val x2: Expression, val y2: Expression
    ) : Action()

    /** Erases every agent (any type) in the disc centred on (x, y). */
    data class EraseDiscAll(val x: Expression, val y: Expression, val radius: Expression) : Action()

    /** Erases the agents of type `grain` in the rectangle (x1, y1)-(x2, y2). */
    data class EraseRect(
        val grain: Expression, val x1: Expression, val y1: Expression,
        val x2: Expression, val y2: Expression
    ) : Action()

    /** Erases the agents of type `grain` in the disc centred on (x, y). */
    data class EraseDisc(
        val grain: Expression, val x: Expression, val y: Expression, val radius: Expression
    ) : Action()

    /** Erases every agent of type `grain` in the whole environment. */
    data class EraseAll(val grain: Expression) : Action()

    /** Activates a behaviour by its identifier (no effect when already active). */
    data class Activate(val behaviourId: Expression) : Action()

    /** Inactivates a behaviour by its identifier (no effect when already inactive). */
    data class Inactivate(val behaviourId: Expression) : Action()
}

/** Accepted arities of each action: some names are overloaded (relative or global). */
private val actionArities = mapOf(
    "Add" to intArrayOf(2, 3),
    "Remove" to intArrayOf(3),
    "Move" to intArrayOf(5),
    "SetField" to intArrayOf(4),
    "AddField" to intArrayOf(4),
    "ClearField" to intArrayOf(3),
    "pause" to intArrayOf(0),
    "AddRect" to intArrayOf(6),
    "AddDisc" to intArrayOf(5),
    "EraseRect" to intArrayOf(4, 5),
    "EraseDisc" to intArrayOf(3, 4),
    "Erase" to intArrayOf(1),
    "Activate" to intArrayOf(1),
    "Inactivate" to intArrayOf(1),
)

private const val availableActions =
    "Add, Remove, Move, SetField, AddField, ClearField, pause, AddRect, AddDisc, EraseRect, EraseDisc, Erase, Activate, Inactivate"

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
        val arities = actionArities[name]
            ?: throw IllegalArgumentException("Unknown action '$name' (available: $availableActions)")
        val argsText = match.groupValues[2].trim()
        val args = if (argsText.isEmpty()) emptyList() else splitTopLevel(argsText).map { arg ->
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
        if (args.size !in arities) {
            throw IllegalArgumentException(
                "$name expects ${arities.joinToString(" or ")} arguments, got ${args.size}"
            )
        }
        when (name) {
            "Add" -> if (args.size == 2) Action.AddMany(args[0], args[1])
            else Action.Add(args[0], args[1], args[2])
            "Remove" -> Action.Remove(args[0], args[1], args[2])
            "Move" -> Action.Move(args[0], args[1], args[2], args[3], args[4])
            "SetField" -> Action.SetField(args[0], args[1], args[2], args[3])
            "AddField" -> Action.AddField(args[0], args[1], args[2], args[3])
            "ClearField" -> Action.ClearField(args[0], args[1], args[2])
            "pause" -> Action.Pause
            "AddRect" -> Action.AddRect(args[0], args[1], args[2], args[3], args[4], args[5])
            "AddDisc" -> Action.AddDisc(args[0], args[1], args[2], args[3], args[4])
            "EraseRect" -> if (args.size == 4) Action.EraseRectAll(args[0], args[1], args[2], args[3])
            else Action.EraseRect(args[0], args[1], args[2], args[3], args[4])
            "EraseDisc" -> if (args.size == 3) Action.EraseDiscAll(args[0], args[1], args[2])
            else Action.EraseDisc(args[0], args[1], args[2], args[3])
            "Erase" -> Action.EraseAll(args[0])
            "Activate" -> Action.Activate(args[0])
            else -> Action.Inactivate(args[0])
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
