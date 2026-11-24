package com.centyllion.model

import com.centyllion.expression.ExprType
import com.centyllion.expression.Expression
import com.centyllion.expression.inferType
import com.centyllion.expression.parseExpression
import kotlin.math.roundToInt

/**
 * A command on the edition grid: selection, clipboard and agent visibility.
 *
 * The core knows nothing about the view (selection, clipboard, hidden agents): the
 * meta-actions of a simulation modifier only queue these commands in the [Simulator]
 * ([Simulator.pendingGridCommands]) and the view applies them at the end of the step,
 * in order, with the code the user already uses by hand.
 *
 * Coordinates and identifiers are resolved when the command is executed (see
 * [GridCommandCall.resolve]): a queued command only holds plain integers.
 */
sealed class GridCommand {
    /** Selects the rectangle spanned by (x1, y1) and (x2, y2), in any order. */
    data class Select(val x1: Int, val y1: Int, val x2: Int, val y2: Int) : GridCommand()

    /** Copies the visible agents of the selection into the clipboard, then clears them. */
    data object Cut : GridCommand()

    /** Copies the visible agents of the selection into the clipboard. */
    data object Copy : GridCommand()

    /** Erases the visible agents of the selection from the grid, then drops the selection. */
    data object EraseSelection : GridCommand()

    /** Pastes the clipboard with (x, y) as its top-left corner. */
    data class Paste(val x: Int, val y: Int, val overwrite: Boolean) : GridCommand()

    /** Drops the selection without touching the clipboard. */
    data object FlushSelection : GridCommand()

    /** Pastes the clipboard, then drops the selection. */
    data class PasteAndFlush(val x: Int, val y: Int, val overwrite: Boolean) : GridCommand()

    /** Rotates the clipboard [quarterTurns] times 90° clockwise (0 to 3). */
    data class Rotate(val quarterTurns: Int) : GridCommand()

    /** Mirrors the clipboard left/right. */
    data object FlipX : GridCommand()

    /** Mirrors the clipboard top/bottom. */
    data object FlipY : GridCommand()

    /** Makes the agents of type [grainId] visible again. */
    data class Show(val grainId: Int) : GridCommand()

    /** Hides the agents of type [grainId]. */
    data class Hide(val grainId: Int) : GridCommand()
}

/** Names accepted in the actions formula, in the order they appear in the help. */
val gridCommandNames: List<String> = listOf(
    "Show", "Hide", "Select", "Cut", "Copy", "EraseSelection", "Paste", "FlushSelection", "Unselect",
    "PasteAndFlush", "Rotate", "FlipX", "FlipY"
)

/**
 * A grid command as written in the actions formula of a simulation modifier.
 *
 * The numeric arguments are kept as [Expression]s: they are evaluated once per step, when
 * the command runs, with the same evaluator as the other meta-actions (so `step`, the
 * mathematical functions and the model functions are available). The `overwrite` flag of
 * Paste/PasteAndFlush is a boolean literal, checked by the parser.
 */
class GridCommandCall(
    val name: String,
    private val numbers: List<Expression> = emptyList(),
    private val overwrite: Boolean? = null
) {
    override fun toString(): String = buildString {
        append(name)
        append('(')
        append(numbers.joinToString(", ") { "$it" })
        if (overwrite != null) {
            if (numbers.isNotEmpty()) append(", ")
            append(overwrite)
        }
        append(')')
    }

    /** Evaluates the arguments and returns the command the view has to apply. */
    fun resolve(evaluator: NetbioDynEvaluator): GridCommand {
        fun number(index: Int): Int = evaluator.evaluate(numbers[index]).float.roundToInt()
        return when (name) {
            "Select" -> GridCommand.Select(number(0), number(1), number(2), number(3))
            "Cut" -> GridCommand.Cut
            "Copy" -> GridCommand.Copy
            "EraseSelection" -> GridCommand.EraseSelection
            "Paste" -> GridCommand.Paste(number(0), number(1), overwrite == true)
            "FlushSelection", "Unselect" -> GridCommand.FlushSelection
            "PasteAndFlush" -> GridCommand.PasteAndFlush(number(0), number(1), overwrite == true)
            // negative turns go counter-clockwise: -1 is the same as three clockwise turns
            "Rotate" -> GridCommand.Rotate(((number(0) % 4) + 4) % 4)
            "FlipX" -> GridCommand.FlipX
            "FlipY" -> GridCommand.FlipY
            "Show" -> GridCommand.Show(number(0))
            "Hide" -> GridCommand.Hide(number(0))
            else -> throw IllegalArgumentException("Unknown grid command '$name'")
        }
    }
}

/** Splits [text] on top-level commas (commas inside parentheses are kept). */
internal fun splitTopLevelArguments(text: String): List<String> {
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

/**
 * Parses a grid command call, for instance `Paste(3, 4, true)`.
 *
 * Returns null when [name] is not a grid command (it may then be a cell or a meta-action).
 * Throws [IllegalArgumentException] with a descriptive message when the arity or the type
 * of an argument is wrong.
 */
fun parseGridCommandCall(name: String, argsText: String): GridCommandCall? {
    if (name !in gridCommandNames) return null
    val args = if (argsText.isEmpty()) emptyList() else splitTopLevelArguments(argsText)

    fun expect(count: Int) {
        if (args.size != count) {
            val expected = if (count == 1) "1 argument" else "$count arguments"
            throw IllegalArgumentException("$name expects $expected, got ${args.size}")
        }
    }

    fun number(index: Int): Expression {
        val text = args[index]
        val expression = try {
            text.parseExpression()
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid argument '$text' in $name: ${e.message}")
        }
        if (expression.inferType() != ExprType.Number) {
            throw IllegalArgumentException("Argument '$text' of $name must be a number")
        }
        return expression
    }

    fun numbersOf(count: Int): List<Expression> = List(count) { number(it) }

    fun overwrite(): Boolean {
        val text = args[2]
        return when (text.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException(
                "Argument '$text' of $name must be a boolean: write true or false"
            )
        }
    }

    return when (name) {
        "Select" -> {
            expect(4)
            GridCommandCall(name, numbersOf(4))
        }
        "Cut", "Copy", "EraseSelection", "FlushSelection", "FlipX", "FlipY" -> {
            expect(0)
            GridCommandCall(name)
        }
        "Paste" -> {
            expect(3)
            GridCommandCall(name, numbersOf(2), overwrite())
        }
        "PasteAndFlush" -> {
            expect(3)
            GridCommandCall(name, numbersOf(2), overwrite())
        }
        "Rotate", "Show", "Hide" -> {
            expect(1)
            GridCommandCall(name, numbersOf(1))
        }
        else -> throw IllegalArgumentException("Unknown grid command '$name'")
    }
}
