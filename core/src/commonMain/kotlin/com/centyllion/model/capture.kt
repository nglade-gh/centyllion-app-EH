package com.centyllion.model

/**
 * Naming of the pictures taken by the `Capture()` meta-action.
 *
 * The name of a capture is `model__step.jpg` (two underscores, for example `GoL_Modifiers__12.jpg`).
 * The model part becomes a directory and a file name, so it is cleaned first: the forbidden
 * characters become underscores, the surrounding spaces and dots are dropped and the length is
 * bounded. Both functions are pure — no canvas, no browser — so the rules the user relies on are
 * testable in `:core:jvmTest`.
 *
 * A capture is written in the working folder under `captures/model/`: [CAPTURE_FOLDER], then the
 * cleaned model name, then the file (`captures/GoL_Modifiers/GoL_Modifiers__12.jpg`). Those two
 * directory levels are built here too, by [captureFolder] and [captureRelativePath], so the path
 * the interface announces is the one the writer creates.
 */

/** Folder, directly inside the working folder, that holds every capture. */
const val CAPTURE_FOLDER = "captures"

/** Longest model part of a capture name, in characters: a file name has to stay usable. */
const val CAPTURE_NAME_MAX_LENGTH = 64

/** Part used when the model has no name usable as a file name (empty, blank, `.` or `..`). */
const val CAPTURE_NAME_FALLBACK = "model"

/** Characters refused by a file system (Windows refuses all of them) and awkward in a URL. */
private val forbiddenNameChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

/**
 * Device names of Windows: a file or a directory carrying one of those names cannot be created,
 * whatever its extension, so a leading underscore is added.
 */
private val deviceNames = buildSet {
    listOf("CON", "PRN", "AUX", "NUL").forEach { add(it) }
    for (number in 1..9) {
        add("COM$number")
        add("LPT$number")
    }
}

/**
 * Turns [raw] into a part usable both as a directory name (the sub-directory of the working
 * folder) and as the beginning of a file name:
 *
 * - `/`, `\`, `:`, `*`, `?`, `"`, `<`, `>` and `|` become `_`, as do the control characters;
 * - the leading and trailing spaces and dots are removed (`" a "` gives `a`, `"v1."` gives `v1`);
 * - the result is bounded to [CAPTURE_NAME_MAX_LENGTH] characters;
 * - an empty result (blank name, `"."`, `".."`, only forbidden characters…) gives
 *   [CAPTURE_NAME_FALLBACK];
 * - a Windows device name gets a leading underscore (`CON` gives `_CON`).
 *
 * Accents and the other letters are kept as they are: the name stays the one the author wrote.
 */
fun sanitizeCaptureName(raw: String): String {
    val replaced = StringBuilder(raw.length)
    for (character in raw) {
        replaced.append(
            when {
                character in forbiddenNameChars -> '_'
                // tab, newline, NUL… cannot appear in a name either
                character.code < 0x20 || character.code == 0x7f -> '_'
                else -> character
            }
        )
    }
    // a file system ignores the surrounding spaces; Windows drops a trailing dot, and "." / ".."
    // reference a directory instead of naming one
    var name = replaced.toString().trim()
    name = name.trim('.')
    name = name.trim()
    if (name.length > CAPTURE_NAME_MAX_LENGTH) {
        // bounding the length may leave a trailing space or dot: clean again
        name = name.take(CAPTURE_NAME_MAX_LENGTH).trim().trim('.')
    }
    if (name.isEmpty()) name = CAPTURE_NAME_FALLBACK
    if (name.uppercase() in deviceNames) name = "_$name"
    return name
}

/**
 * Name of the capture of [step]: the cleaned model name, two underscores and the step number,
 * for example `GoL_Modifiers__12.jpg`.
 *
 * [step] is the step counter displayed for the captured frame: the `Capture()` action runs at the
 * end of the step that emits it, so the image is the state that the interface then shows as step
 * `n + 1` when the condition was `step == n`.
 */
fun captureFileName(modelName: String, step: Int): String = "${sanitizeCaptureName(modelName)}__$step.jpg"

/**
 * Folder of the captures of [modelName], relative to the working folder: [CAPTURE_FOLDER] then the
 * cleaned model name, for example `captures/GoL_Modifiers`.
 *
 * The two levels are created when the capture is written (`getDirectoryHandle(create: true)`); the
 * model part is cleaned exactly like the beginning of the file name, so the directory and the
 * picture always agree.
 */
fun captureFolder(modelName: String): String = "$CAPTURE_FOLDER/${sanitizeCaptureName(modelName)}"

/**
 * Path of the capture of [step] relative to the working folder:
 * `captures/GoL_Modifiers/GoL_Modifiers__12.jpg` for a model named `GoL_Modifiers`.
 *
 * It is the path the confirmation message announces; the writer creates the two directory levels
 * before writing the file.
 */
fun captureRelativePath(modelName: String, step: Int): String =
    "${captureFolder(modelName)}/${captureFileName(modelName, step)}"
