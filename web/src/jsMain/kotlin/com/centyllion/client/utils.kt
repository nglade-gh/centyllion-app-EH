package com.centyllion.client

import bulma.BulmaElement
import com.centyllion.model.ModelAndSimulation
import com.centyllion.model.minFieldLevel
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.dom.create
import kotlinx.html.js.a
import kotlinx.serialization.json.Json
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.js.Promise

val netbiodynJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

inline fun <reified T> Any?.takeInstance(): T? = if (this is T) this else null

external fun encodeURI(parameter: String): String
external fun encodeURIComponent(parameter: String): String

private val fieldFloatRegex = Regex("(0\\.(0*)([0-9]+))|(([1-9])\\.([0-9]+)e-([0-9]+))")

fun Float.toFieldString(): String = when {
    this < minFieldLevel -> ""
    this < 1e-3 -> {
        val output = this.toString()
        val match = fieldFloatRegex.matchEntire(output)
        when {
            match == null -> output
            output.startsWith("0") -> {
                val exponent = "-${match.groupValues[2].length+1}"
                val mantissa = match.groupValues[3].substring(0,1)
                "${mantissa}e${exponent}"
            }
            else -> {
                val exponent = "-${match.groupValues[7]}"
                val mantissa = match.groupValues[5]
                "${mantissa}e${exponent}"
            }
        }
    }
    else -> this.toFixed(3)
}

fun Number.toFixed(size: Int = 3): String = toDouble().toFixed(size)
fun Double.toFixed(size: Int = 3): String = asDynamic().toFixed(size) as String

fun stringHref(content: String) = "data:text/plain;charset=utf-8,${encodeURIComponent(content)}"

fun blobHref(blob: Blob) = URL.createObjectURL(blob)

/** Download the given [content] with [filename] using the `<a>` trick. */
fun download(filename: String, href: String) {
    val a = document.create.a(href)
    a.setAttribute("download", filename)
    a.style.display = "none"

    document.body?.appendChild(a)
    a.click()
    document.body?.removeChild(a)
}

/**
 * Saves [content] through the native save dialog (File System Access API),
 * letting the user choose the location and the file name.
 *
 * @param suggestedName file name proposed in the dialog (e.g. "model.netbiodyn").
 * @param onSaved called with the file name chosen by the user and its file handle.
 * @param onCancel called when the user dismisses the dialog without saving.
 * @param onFallback called when the API is unavailable or writing fails;
 * the caller should fall back to [download].
 */
fun saveFilePicker(
    suggestedName: String,
    content: String,
    onSaved: (String, dynamic) -> Unit,
    onCancel: () -> Unit = {},
    onFallback: () -> Unit,
) {
    val w = window.asDynamic()
    if (w.showSaveFilePicker == js("undefined")) {
        onFallback()
        return
    }
    val options = js("{}")
    options.suggestedName = suggestedName
    val type = js("{}")
    type.description = "NetBioDyn model"
    val accept = js("{}")
    accept["application/json"] = js("['.netbiodyn']")
    type.accept = accept
    options.types = js("[type]")
    w.showSaveFilePicker(options)
        .then { handle: dynamic ->
            writeThroughHandle(handle, content).then { onSaved(handle.name as String, handle) }
        }
        .catch { error: dynamic ->
            if (error != null && error.name == "AbortError") onCancel() else onFallback()
        }
}

/** Writes [content] into the file of [handle] (creates a new version). */
private fun writeThroughHandle(handle: dynamic, content: String): Promise<Unit> =
    (handle.createWritable() as Promise<dynamic>)
        .then { writable: dynamic ->
            (writable.write(content) as Promise<dynamic>)
                .then { _: dynamic ->
                    (writable.close() as Promise<dynamic>).then { _: dynamic -> }
                }
        }

/**
 * Overwrites [content] into the file represented by [handle] — a quick save
 * without dialog. The browser asks for a read/write permission when it has
 * not been granted yet (this must therefore be called from a user gesture).
 *
 * @param onSaved called with the file name when the write succeeded.
 * @param onDenied called when there is no permission (the caller should
 * offer a save-as dialog instead).
 */
fun quickSaveFile(handle: dynamic, content: String, onSaved: (String) -> Unit, onDenied: () -> Unit) {
    // A restored handle may be missing requestPermission (property access does not
    // throw, unlike the call): guard before calling, otherwise offer save-as.
    if (handle == null || handle.requestPermission == null || handle.requestPermission == js("undefined")) {
        onDenied()
        return
    }
    try {
        handle.requestPermission(js("({mode: 'readwrite'})"))
            .then { state: String ->
                if (state == "granted") {
                    writeThroughHandle(handle, content)
                        .then { _: Unit -> onSaved(handle.name as String) }
                        .catch { _: dynamic -> onDenied() }
                } else {
                    onDenied()
                }
            }
            .catch { _: dynamic -> onDenied() }
    } catch (e: Exception) {
        // invalid or unsupported handle: let the caller offer a save-as dialog
        onDenied()
    }
}

/** Remembers a file handle in IndexedDB so it survives browser restarts. */
fun rememberFileHandle(key: String, handle: dynamic) {
    val w = window.asDynamic()
    w.__centyllionHandle = handle
    w.__centyllionKey = key
    js("(function () { try { var req = indexedDB.open('centyllion-files', 1); req.onupgradeneeded = function () { var db = req.result; if (!db.objectStoreNames.contains('handles')) db.createObjectStore('handles'); }; req.onsuccess = function () { var db = req.result; try { db.transaction('handles', 'readwrite').objectStore('handles').put(window.__centyllionHandle, window.__centyllionKey); } catch (e) {} db.close(); }; } catch (e) {} })()")
}

/** Restores a previously remembered file handle (resolves null when absent). */
fun restoreFileHandle(key: String): Promise<dynamic> {
    val w = window.asDynamic()
    w.__centyllionKey = key
    return (js("new Promise(function (resolve) { try { var req = indexedDB.open('centyllion-files', 1); req.onupgradeneeded = function () { var db = req.result; if (!db.objectStoreNames.contains('handles')) db.createObjectStore('handles'); }; req.onsuccess = function () { var db = req.result; var get = db.transaction('handles').objectStore('handles').get(window.__centyllionKey); get.onsuccess = function () { db.close(); resolve(get.result || null); }; get.onerror = function () { db.close(); resolve(null); }; }; req.onerror = function () { resolve(null); }; } catch (e) { resolve(null); } })") as Promise<dynamic>)
}

/** Toggles a element into fullscreen */
fun toggleElementToFullScreen(htmlElement: HTMLElement): Boolean {
    val d = document.asDynamic()
    if (d.webkitFullscreenElement != null || d.fullscreenElement != null) {
        if (d.webkitExitFullscreen != null) {
            d.webkitExitFullscreen()
        } else if (d.mozExitFullscreen != null) {
            d.mozExitFullscreen()
        } else if (d.exitFullscreen != null) {
            d.exitFullscreen()
        }
        return false
    } else {
        val view = htmlElement.asDynamic()
        if (view.webkitRequestFullscreen != null) {
            view.webkitRequestFullscreen()
        } else if (view.mozRequestFullscreen != null) {
            view.mozRequestFullscreen()
        } else if (view.requestFullscreen != null) {
            view.requestFullscreen()
        }
        return true
    }
}

fun BulmaElement.setTooltip(text: String) {
    root.classList.add("has-tooltip")
    root.classList.add("has-tooltip-primary")
    root.classList.add("has-tooltip-bottom")
    root.setAttribute("data-tooltip", text)
}

private val markdownFlavour = CommonMarkFlavourDescriptor()
private val markdownParser = MarkdownParser(markdownFlavour)

fun renderMarkdown(source: String): String {
    val parsedTree = markdownParser.buildMarkdownTreeFromString(source)
    return HtmlGenerator(source, parsedTree, markdownFlavour).generateHtml()
}

fun loadFromStorage(): ModelAndSimulation? =
    try {
        window.localStorage.getItem("model")?.let {
            netbiodynJson.decodeFromString(ModelAndSimulation.serializer(), it)
        }
    } catch (_: Throwable) {
        // A model stored by an older version may no longer deserialize: ignore it.
        console.warn("Could not load the model from local storage, ignoring it.")
        null
    }

fun saveToStorage(model: ModelAndSimulation) {
    window.setTimeout({
        val value = netbiodynJson.encodeToString(ModelAndSimulation.serializer(), model)
        window.localStorage.setItem("model", value)
    }, 25)
}
