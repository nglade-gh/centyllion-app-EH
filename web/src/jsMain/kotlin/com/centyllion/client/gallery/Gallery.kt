package com.centyllion.client.gallery

import com.centyllion.model.Grain
import com.centyllion.model.ModelAndSimulation
import com.centyllion.model.colorNames
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.CanvasRenderingContext2D
import kotlin.random.Random

/** Index of the models shipped with the software (copied from `<project>/Gallery` by the build). */
const val GALLERY_INDEX_URL = "Gallery/gallery.json"

/** localStorage key of the local overlay: entries added by the user, or edits of static entries. */
const val GALLERY_OVERLAY_KEY = "centyllion-gallery-overlay"

/** Name proposed when the user downloads the merged gallery. */
const val GALLERY_EXPORT_FILE_NAME = "gallery.json"

/** Model texts above this size are refused: the overlay lives in localStorage (about 5 MB). */
const val MAX_GALLERY_MODEL_BYTES = 2 * 1024 * 1024

/** One card of the gallery. */
@Serializable
data class GalleryEntry(
    val id: String,
    val title: String = "",
    val description: String = "",
    val image: String = "",
    /** Path of the model file, relative to the served root (static entries only). */
    val file: String? = null,
    /** JSON text of the model, kept for the models imported by the user (overlay only). */
    val model: String? = null,
    /** Masks a static entry without touching `gallery.json`. */
    val hidden: Boolean = false
) {
    /** True for an entry carrying its own model text (a model imported by the user). */
    val imported: Boolean get() = !model.isNullOrBlank()
}

/** Content of `Gallery/gallery.json`. */
@Serializable
data class GalleryIndex(
    val version: Int = 1,
    val models: List<GalleryEntry> = emptyList()
)

/**
 * Persistence and merging of the gallery.
 *
 * The static index (`Gallery/gallery.json`) is read only; everything the user
 * adds or changes is written to a localStorage overlay: a JSON array of
 * entries with the same fields plus [GalleryEntry.model] (the JSON text of an
 * imported model) and [GalleryEntry.hidden] (to mask a static entry).
 */
object GalleryStore {

    private val indexJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val overlayJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    /** Decodes the content of the static index. */
    fun decodeIndex(text: String): GalleryIndex = indexJson.decodeFromString(GalleryIndex.serializer(), text)

    /** Reads the overlay, ignoring a corrupted value (an older version, a manual edit). */
    fun readOverlay(): List<GalleryEntry> {
        val raw = try {
            window.localStorage.getItem(GALLERY_OVERLAY_KEY)
        } catch (t: Throwable) {
            console.warn("Gallery: local storage is not available.", t)
            null
        } ?: return emptyList()
        return try {
            overlayJson.decodeFromString<List<GalleryEntry>>(raw)
        } catch (t: Throwable) {
            console.warn("Gallery: cannot decode the local overlay, ignoring it.", t)
            emptyList()
        }
    }

    /** Writes the overlay; returns false when the browser storage is full or unavailable. */
    fun writeOverlay(entries: List<GalleryEntry>): Boolean = try {
        window.localStorage.setItem(GALLERY_OVERLAY_KEY, overlayJson.encodeToString(ListSerializer(GalleryEntry.serializer()), entries))
        true
    } catch (t: Throwable) {
        console.error("Gallery: cannot write the local overlay.", t)
        false
    }

    /** Adds [entry] or replaces the entry of the same id. */
    fun upsert(entries: List<GalleryEntry>, entry: GalleryEntry): List<GalleryEntry> =
        entries.filterNot { it.id == entry.id } + entry

    /** Removes the entry [id] from the overlay (a model added by the user). */
    fun remove(entries: List<GalleryEntry>, id: String): List<GalleryEntry> =
        entries.filterNot { it.id == id }

    /** Masks the static entry [entry]; its fields are kept so it can be edited again. */
    fun hide(entries: List<GalleryEntry>, entry: GalleryEntry): List<GalleryEntry> =
        upsert(entries, entry.copy(model = null, hidden = true))

    /**
     * Static entries (not masked) overridden by the overlay, followed by the
     * entries that only exist in the overlay. Order of the static index is kept.
     */
    fun merge(static: List<GalleryEntry>, overlay: List<GalleryEntry>): List<GalleryEntry> {
        val patches = overlay.associateBy { it.id }
        val merged = ArrayList<GalleryEntry>(static.size + overlay.size)
        static.filterNot { it.hidden }.forEach { base ->
            val patch = patches[base.id]
            when {
                patch == null -> merged += base
                patch.hidden -> Unit // masked by the user
                else -> merged += base.copy(
                    title = patch.title.ifBlank { base.title },
                    description = patch.description,
                    image = patch.image.ifBlank { base.image },
                    file = patch.file ?: base.file,
                    model = patch.model,
                )
            }
        }
        overlay.forEach { entry ->
            if (static.none { it.id == entry.id } && !entry.hidden) merged += entry
        }
        return merged
    }

    /**
     * JSON of the merged gallery, ready to replace `Gallery/gallery.json`.
     * The text of the imported models is not exported (it would bloat the file
     * and is not a distributed model): it is replaced by [note].
     */
    fun export(static: List<GalleryEntry>, overlay: List<GalleryEntry>, note: String): String {
        val root = buildJsonObject {
            put("version", 1)
            put("models", buildJsonArray {
                merge(static, overlay).forEach { entry ->
                    add(buildJsonObject {
                        put("id", entry.id)
                        put("title", entry.title)
                        put("description", entry.description)
                        put("image", entry.image)
                        entry.file?.let { put("file", it) }
                        if (entry.imported) put("note", note)
                    })
                }
            })
        }
        return overlayJson.encodeToString(JsonObject.serializer(), root)
    }
}

/**
 * Thumbnails generated from a model (used when the user adds a model without
 * an image): one pixel per cell, painted with the colour of the grain.
 */
object GalleryThumbnails {

    /** JPEG quality of the generated thumbnails. */
    private const val QUALITY = 0.8

    /** Share of the cells painted when the model carries no initial grid. */
    private const val PREVIEW_DENSITY = 0.18

    /**
     * Draws the initial state of [project] on a canvas of the size of the grid
     * and returns it as a JPEG data URL, or null when the model has no grid.
     */
    fun fromInitialState(project: ModelAndSimulation): String? {
        val settings = project.simulation.settings
        val width = settings.effectiveWidth
        val height = settings.effectiveHeight
        if (width <= 0 || height <= 0) return null

        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = width
        canvas.height = height
        val context = canvas.getContext("2d") as? CanvasRenderingContext2D ?: return null

        context.fillStyle = cssColor(settings.backgroundColor ?: "White")
        context.fillRect(0.0, 0.0, width.toDouble(), height.toDouble())

        val agents = project.simulation.agents
        var painted = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val id = agents.getOrNull(y * width + x) ?: -1
                if (id < 0) continue
                val grain = project.model.grainForId(id) ?: continue
                context.fillStyle = cssColor(grain.color)
                context.fillRect(x.toDouble(), y.toDouble(), 1.0, 1.0)
                painted++
            }
        }

        val grains = project.model.grains.filter { !it.invisible }
        if (painted == 0 && grains.isNotEmpty()) {
            // A project saved before any cell was painted carries no initial
            // grid: a blank card would say nothing about the model, so show a
            // deterministic preview of its grains instead.
            paintPreview(context, grains, width, height, project.simulation.seed)
        }

        return canvas.toDataURL("image/jpeg", QUALITY)
    }

    private fun paintPreview(
        context: CanvasRenderingContext2D,
        grains: List<Grain>,
        width: Int, height: Int, seed: Long
    ) {
        // seeded: the same model always gets the same preview
        val random = Random(seed.toInt())
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (random.nextDouble() >= PREVIEW_DENSITY) continue
                val grain = grains[random.nextInt(grains.size)]
                context.fillStyle = cssColor(grain.color)
                context.fillRect(x.toDouble(), y.toDouble(), 1.0, 1.0)
            }
        }
    }

    /** CSS colour of a grain: the named colours of the model, or the value as is. */
    fun cssColor(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "#000000"
        val rgb = colorNames[trimmed] ?: colorNames.entries.firstOrNull { it.key.equals(trimmed, true) }?.value
        return rgb?.let { "rgb(${it.first}, ${it.second}, ${it.third})" } ?: trimmed
    }
}
