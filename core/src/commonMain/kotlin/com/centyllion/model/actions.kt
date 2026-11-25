package com.centyllion.model

import com.centyllion.expression.ExprType
import com.centyllion.expression.Expression
import com.centyllion.expression.constantNumberOrNull
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

    /**
     * Pauses the simulation at the end of the current step, for good (the run loop stops).
     *
     * A modifier that pauses the simulation makes the view show a warning: the run can be
     * resumed only by the reader, with the Run button or step by step.
     */
    object Pause : Action()

    /**
     * Pauses the simulation for `seconds` of **real** time, then resumes it by itself.
     *
     * The loop stays alive but executes no step during the pause, so the sequence of states does
     * not depend on the clock: this is a comfort pause for a demonstration, not a model clock.
     * The duration is a number strictly greater than 0, written as a literal or an expression
     * (`2.5`, `step / 10`…); a value <= 0 is ignored at run time, like `RunNStep(N)` ignores
     * `N <= 0`. `Pause()` without argument remains the indefinite pause, lifted only by the
     * reader.
     */
    data class TimedPause(val seconds: Expression) : Action()

    /** Runs exactly one more step, then pauses the simulation. */
    object RunOneStep : Action()

    /**
     * Runs `count` more steps, then pauses the simulation. A count below 1 is ignored.
     * The argument is a number: it is rounded to the nearest integer when the action runs.
     */
    data class RunNStep(val count: Expression) : Action()

    /** Rewinds the simulation to its initial state, without restarting the run. */
    object Rewind : Action()

    /** Rewinds the simulation to its initial state, then restarts the run. */
    object Restart : Action()

    /**
     * Takes a JPEG capture of the simulation view at the current step.
     *
     * The core knows nothing of the canvas, so the action only counts the request
     * ([Simulator.captureRequestCount]); the view reads the counter after the step, encodes the
     * simulation canvas and writes the picture in the working folder (or downloads it) under the
     * name `model__step.jpg`. Like the other meta-actions it runs once per step, after the
     * behaviours, so the image is the complete frame of the step that asked for it.
     */
    object Capture : Action()

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

    /** Draws a line of agents of type `grain` from (x1, y1) to (x2, y2). */
    data class DrawLine(
        val grain: Expression,
        val x1: Expression, val y1: Expression, val x2: Expression, val y2: Expression
    ) : Action()

    /** Activates a simulation modifier by its identifier (no effect when already active). */
    data class Use(val modifierId: Expression) : Action()

    /** Inactivates a simulation modifier by its identifier (no effect when already inactive). */
    data class Unuse(val modifierId: Expression) : Action()

    /**
     * A grid command (selection, clipboard, visibility): it is queued by the simulation and
     * executed by the view at the end of the step, which is the only place that knows the
     * selection and the clipboard.
     */
    data class Grid(val call: GridCommandCall) : Action()
}

/** True for the actions that act on the simulation as a whole rather than on one cell. */
fun isMetaAction(action: Action): Boolean = when (action) {
    Action.Pause, is Action.TimedPause, Action.RunOneStep, is Action.RunNStep, Action.Rewind, Action.Restart,
    Action.Capture,
    is Action.AddMany, is Action.AddRect, is Action.AddDisc,
    is Action.EraseRectAll, is Action.EraseDiscAll, is Action.EraseRect, is Action.EraseDisc,
    is Action.EraseAll, is Action.Activate, is Action.Inactivate, is Action.Use, is Action.Unuse,
    is Action.DrawLine, is Action.Grid -> true
    else -> false
}

/**
 * Names kept for the models already recorded, mapped to the current spelling.
 *
 * `pause()` was the only meta-function written in lower case: the meta-functions and the
 * meta-tests start with a capital letter, and so does every other one. The alias is accepted
 * (an old `.nbd2` must keep loading and running) but it is obsolete: `Pause()` is the name to
 * write, and the help says so.
 */
val deprecatedActionNames: Map<String, String> = mapOf("pause" to "Pause")

/** Accepted arities of each action: some names are overloaded (relative or global). */
private val actionArities = mapOf(
    "Add" to intArrayOf(2, 3),
    "Remove" to intArrayOf(3),
    "Move" to intArrayOf(5),
    "SetField" to intArrayOf(4),
    "AddField" to intArrayOf(4),
    "ClearField" to intArrayOf(3),
    "Pause" to intArrayOf(0, 1),
    "RunOneStep" to intArrayOf(0),
    "RunNStep" to intArrayOf(1),
    "Rewind" to intArrayOf(0),
    "Restart" to intArrayOf(0),
    "Capture" to intArrayOf(0),
    "AddRect" to intArrayOf(6),
    "AddDisc" to intArrayOf(5),
    "EraseRect" to intArrayOf(4, 5),
    "EraseDisc" to intArrayOf(3, 4),
    "Erase" to intArrayOf(1),
    "Activate" to intArrayOf(1),
    "Inactivate" to intArrayOf(1),
    "DrawLine" to intArrayOf(5),
    "Use" to intArrayOf(1),
    "Unuse" to intArrayOf(1),
)

private const val availableActions =
    "Add, Remove, Move, SetField, AddField, ClearField, Pause, RunOneStep, RunNStep, Rewind, Restart, Capture, " +
        "AddRect, AddDisc, EraseRect, EraseDisc, Erase, Activate, Inactivate, Use, Unuse, DrawLine"

/** Grid commands of the meta-actions: they are parsed by [parseGridCommandCall]. */
private const val availableGridCommands =
    "Show, Hide, Select, Cut, Copy, EraseSelection, Paste, FlushSelection, PasteAndFlush, Rotate, FlipX, FlipY, AllowToricSelection, AllowOverwriting"

private val actionCallPattern = Regex("""^(\w+)\s*\((.*)\)$""")

/**
 * Parses an actions formula: a comma separated list of action calls, e.g.
 * `Add(0, 1, 0), Remove(1, 0, 0), Move(0, 0, 0, 1, 0)`.
 *
 * The grid commands of the meta-actions (`Select(0, 0, 2, 2), Copy()`) are accepted too and
 * returned as [Action.Grid], at their position in the list: the order of the formula is the
 * order the view applies them.
 *
 * Throws [IllegalArgumentException] with a descriptive message when the formula is invalid.
 */
fun String.parseActionFormula(allowMeta: Boolean = true): List<Action> {
    val trimmed = trim()
    if (trimmed.isEmpty()) return emptyList()

    return splitTopLevelArguments(trimmed).map { part ->
        val match = actionCallPattern.find(part)
            ?: throw IllegalArgumentException(
                "Expected a list of actions like Add(0, 1, 0), Remove(1, 0, 0) — got '$part'"
            )
        val writtenName = match.groupValues[1]
        // obsolete spellings (pause) are mapped to the current name before any lookup, so that
        // an old formula parses exactly like the new one
        val name = deprecatedActionNames[writtenName] ?: writtenName
        val argsText = match.groupValues[2].trim()
        // a state test is a condition, not an action: say it instead of "unknown action"
        if (name in stateTestFunctions) {
            throw IllegalArgumentException(misplacedStateTest(name, "an action formula"))
        }
        val gridCall = parseGridCommandCall(name, argsText)
        if (gridCall != null) {
            if (!allowMeta) {
                throw IllegalArgumentException(
                    "$name is a meta-action: it belongs to a simulation modifier, not to a behaviour"
                )
            }
            return@map Action.Grid(gridCall)
        }
        val arities = actionArities[name]
            ?: throw IllegalArgumentException(
                "Unknown action '$name' (available: $availableActions; grid commands: $availableGridCommands)"
            )
        val args = if (argsText.isEmpty()) emptyList() else splitTopLevelArguments(argsText).map { arg ->
            val expr = try {
                arg.parseExpression()
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid argument '$arg' in $name: ${e.message}")
            }
            // a state test read the simulation state: refused inside an action argument too
            expr.stateTestName()?.let {
                throw IllegalArgumentException(misplacedStateTest(it, "an action formula"))
            }
            if (name == "Pause") {
                // Pause(t): t is a duration in seconds, strictly positive; Pause() stays the
                // indefinite pause. A duration that depends on the state is checked at run time.
                if (expr.inferType() != ExprType.Number) {
                    throw IllegalArgumentException(
                        "Pause expects a duration in seconds greater than 0 (a number, for instance " +
                            "Pause(2.5)), got '$arg'"
                    )
                }
                val constant = expr.constantNumberOrNull()
                if (constant != null && constant <= 0.0) {
                    throw IllegalArgumentException(
                        "Pause expects a duration in seconds greater than 0, got '$arg'"
                    )
                }
            } else if (expr.inferType() != ExprType.Number) {
                throw IllegalArgumentException("Argument '$arg' of $name must be a number")
            }
            expr
        }
        if (args.size !in arities) {
            throw IllegalArgumentException(
                "$name expects ${arities.joinToString(" or ")} arguments, got ${args.size}"
            )
        }
        val parsed = when (name) {
            "Add" -> if (args.size == 2) Action.AddMany(args[0], args[1])
            else Action.Add(args[0], args[1], args[2])
            "Remove" -> Action.Remove(args[0], args[1], args[2])
            "Move" -> Action.Move(args[0], args[1], args[2], args[3], args[4])
            "SetField" -> Action.SetField(args[0], args[1], args[2], args[3])
            "AddField" -> Action.AddField(args[0], args[1], args[2], args[3])
            "ClearField" -> Action.ClearField(args[0], args[1], args[2])
            "Pause" -> if (args.isEmpty()) Action.Pause else Action.TimedPause(args[0])
            "RunOneStep" -> Action.RunOneStep
            "RunNStep" -> Action.RunNStep(args[0])
            "Rewind" -> Action.Rewind
            "Restart" -> Action.Restart
            "Capture" -> Action.Capture
            "AddRect" -> Action.AddRect(args[0], args[1], args[2], args[3], args[4], args[5])
            "AddDisc" -> Action.AddDisc(args[0], args[1], args[2], args[3], args[4])
            "EraseRect" -> if (args.size == 4) Action.EraseRectAll(args[0], args[1], args[2], args[3])
            else Action.EraseRect(args[0], args[1], args[2], args[3], args[4])
            "EraseDisc" -> if (args.size == 3) Action.EraseDiscAll(args[0], args[1], args[2])
            else Action.EraseDisc(args[0], args[1], args[2], args[3])
            "Erase" -> Action.EraseAll(args[0])
            "Activate" -> Action.Activate(args[0])
            "Inactivate" -> Action.Inactivate(args[0])
            "DrawLine" -> Action.DrawLine(args[0], args[1], args[2], args[3], args[4])
            "Use" -> Action.Use(args[0])
            else -> Action.Unuse(args[0])
        }
        if (!allowMeta && isMetaAction(parsed)) {
            throw IllegalArgumentException(
                "$name is a meta-action: it belongs to a simulation modifier, not to a behaviour"
            )
        }
        parsed
    }
}

/**
 * Validates an actions formula. Returns an error message, or null when valid.
 */
fun validateActionFormula(formula: String): String? =
    try {
        // meta-actions are refused in the behaviours: they go to the simulation modifiers
        formula.parseActionFormula(allowMeta = false)
        null
    } catch (e: Exception) {
        e.message
    }

/** Parses the actions of a simulation modifier: only meta-actions are accepted. */
fun String.parseModifierActions(): List<Action> =
    parseActionFormula(allowMeta = true).onEach { action ->
        if (!isMetaAction(action)) {
            throw IllegalArgumentException(
                "this action acts on a cell: only meta-actions are allowed in a simulation modifier"
            )
        }
    }

/** Validates the actions of a simulation modifier. Returns an error message, or null. */
fun validateModifierActions(formula: String): String? =
    try {
        formula.parseModifierActions()
        null
    } catch (e: Exception) {
        e.message
    }
