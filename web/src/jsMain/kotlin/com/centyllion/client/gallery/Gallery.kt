package com.centyllion.client.gallery

import com.centyllion.model.Grain
import com.centyllion.model.ModelAndSimulation
import com.centyllion.model.colorNames
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.CanvasRenderingContext2D
import kotlin.js.Date
import kotlin.random.Random

/** Index of the models shipped with the software (copied from `<project>/Gallery` by the build). */
const val GALLERY_INDEX_URL = "Gallery/gallery.json"

/** IndexedDB database holding the personal gallery of the browser. */
const val GALLERY_DB_NAME = "centyllion-gallery"

/** Schema version of [GALLERY_DB_NAME]. */
const val GALLERY_DB_VERSION = 1

/** Object store of [GALLERY_DB_NAME]: one value per entry, plus an index on `id`. */
const val GALLERY_DB_STORE = "entries"

/**
 * localStorage key of the previous overlay. It is read once, to copy its
 * entries into IndexedDB, and never read again afterwards (the key itself is
 * left in place, it is not deleted).
 */
const val GALLERY_OVERLAY_KEY = "centyllion-gallery-overlay"

/** localStorage flag: set once the previous overlay has been copied into IndexedDB. */
private const val GALLERY_MIGRATED_FLAG = "centyllion-gallery-migrated"

/** Name proposed when the visitor downloads their personal gallery. */
const val GALLERY_PERSONAL_FILE_NAME = "ma-galerie.json"

/** Above this size the export warns the visitor before starting the download. */
const val GALLERY_EXPORT_WARNING_BYTES = 20 * 1024 * 1024

/** Sanity limit of a single model text, in bytes. */
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

/** Content of a personal gallery export, as downloaded by the visitor. */
@Serializable
data class GalleryExport(
    val version: Int = 1,
    /** ISO-8601 timestamp of the export. */
    val exportedAt: String = "",
    val models: List<GalleryEntry> = emptyList()
)

/**
 * Persistence and merging of the gallery.
 *
 * The distributed index (`Gallery/gallery.json`) is read only and identical
 * for every visitor; everything a visitor adds or changes is personal and kept
 * in the browser, in an IndexedDB store (see [galleryDatabase]): one value per
 * entry, holding the whole [GalleryEntry] including the model text.
 */
object GalleryStore {

    private val indexJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Compact form used by the database: the model text dominates the size. */
    private val storeJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val exportJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        // the format is a contract: version and exportedAt are always written
        encodeDefaults = true
    }

    private val entriesSerializer = ListSerializer(GalleryEntry.serializer())

    /** True once the migration of the previous localStorage overlay has been attempted. */
    private var migrationChecked = false

    /** Decodes the content of the distributed index. */
    fun decodeIndex(text: String): GalleryIndex = indexJson.decodeFromString(GalleryIndex.serializer(), text)

    /**
     * Reads the personal gallery of this browser, the previous localStorage
     * overlay being copied into IndexedDB first (see [migrate]).
     *
     * [onLoaded] is called asynchronously, with an empty list when the
     * database cannot be read.
     */
    fun load(onLoaded: (List<GalleryEntry>) -> Unit) {
        migrate {
            try {
                galleryDatabase.all().then { text: dynamic ->
                    onLoaded(decodeEntries(text as? String))
                }.catch { error: dynamic ->
                    console.error("Gallery: cannot read the personal gallery from IndexedDB.", error)
                    onLoaded(emptyList())
                }
            } catch (t: Throwable) {
                console.error("Gallery: cannot read the personal gallery from IndexedDB.", t)
                onLoaded(emptyList())
            }
        }
    }

    /**
     * Replaces the personal gallery with [entries]; [onDone] receives false
     * when the browser refused the write (quota, private mode, no IndexedDB).
     */
    fun save(entries: List<GalleryEntry>, onDone: (Boolean) -> Unit) {
        try {
            val text = storeJson.encodeToString(entriesSerializer, entries)
            galleryDatabase.write(text).then { saved: dynamic ->
                onDone(saved == true)
            }.catch { error: dynamic ->
                console.error("Gallery: cannot write the personal gallery into IndexedDB.", error)
                onDone(false)
            }
        } catch (t: Throwable) {
            console.error("Gallery: cannot write the personal gallery into IndexedDB.", t)
            onDone(false)
        }
    }

    // -------------------------------------------------------------- migration

    /**
     * Copies the entries of the previous localStorage overlay into IndexedDB,
     * once: a flag is written afterwards, so the old key is never read again
     * (it is left in place, it is only an archive from now on). An entry whose
     * id already exists in the database is not overwritten, so a migration
     * interrupted halfway loses nothing.
     */
    private fun migrate(onReady: () -> Unit) {
        if (migrationChecked) {
            onReady()
            return
        }
        migrationChecked = true
        if (readStorageFlag()) {
            onReady()
            return
        }
        val legacy = readLegacyOverlay()
        if (legacy.isEmpty()) {
            writeStorageFlag()
            onReady()
            return
        }
        try {
            galleryDatabase.merge(storeJson.encodeToString(entriesSerializer, legacy)).then { added: dynamic ->
                // the marker goes last: a failure above means the migration is retried
                writeStorageFlag()
                console.log("Gallery: ${added} entries migrated from localStorage to IndexedDB.")
                onReady()
            }.catch { error: dynamic ->
                console.error("Gallery: cannot migrate the localStorage overlay.", error)
                onReady()
            }
        } catch (t: Throwable) {
            console.error("Gallery: cannot migrate the localStorage overlay.", t)
            onReady()
        }
    }

    /** Reads the previous overlay; the only place where the old key is ever read. */
    private fun readLegacyOverlay(): List<GalleryEntry> {
        val raw = try {
            window.localStorage.getItem(GALLERY_OVERLAY_KEY)
        } catch (t: Throwable) {
            console.warn("Gallery: local storage is not available.", t)
            null
        } ?: return emptyList()
        return try {
            storeJson.decodeFromString(entriesSerializer, raw)
        } catch (t: Throwable) {
            console.warn("Gallery: cannot decode the previous localStorage overlay, ignoring it.", t)
            emptyList()
        }
    }

    private fun readStorageFlag(): Boolean = try {
        window.localStorage.getItem(GALLERY_MIGRATED_FLAG) == "true"
    } catch (t: Throwable) {
        false
    }

    private fun writeStorageFlag() {
        try {
            window.localStorage.setItem(GALLERY_MIGRATED_FLAG, "true")
        } catch (t: Throwable) {
            console.warn("Gallery: cannot write the migration flag.", t)
        }
    }

    private fun decodeEntries(text: String?): List<GalleryEntry> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            storeJson.decodeFromString(entriesSerializer, text)
        } catch (t: Throwable) {
            console.warn("Gallery: cannot decode the personal gallery, ignoring it.", t)
            emptyList()
        }
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
     * JSON of the personal gallery: every entry of the overlay, model text
     * included, so that an import restores the gallery exactly as it was.
     */
    fun exportPersonal(entries: List<GalleryEntry>): String =
        exportJson.encodeToString(
            GalleryExport.serializer(),
            GalleryExport(
                version = 1,
                exportedAt = Date(Date.now()).toISOString(),
                models = entries
            )
        )

    /**
     * Reads a personal gallery export; null when the text is not one (a
     * missing `models` array, a decoding failure, a future version, an entry
     * without id). The caller must then refuse the file explicitly.
     */
    fun decodeExport(text: String): List<GalleryEntry>? {
        val root = try {
            indexJson.parseToJsonElement(text)
        } catch (t: Throwable) {
            console.warn("Gallery: the imported file is not JSON.", t)
            return null
        }
        val rootObject = root as? JsonObject ?: return null
        val models = rootObject["models"] as? JsonArray ?: return null
        val version = (rootObject["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
        if (version > 1) {
            console.warn("Gallery: the imported file comes from a newer version ($version).")
            return null
        }
        val entries = try {
            indexJson.decodeFromJsonElement(entriesSerializer, models)
        } catch (t: Throwable) {
            console.warn("Gallery: cannot decode the imported gallery.", t)
            return null
        }
        if (entries.any { it.id.isBlank() }) return null
        return entries
    }
}

/**
 * IndexedDB access to the personal gallery, written in JavaScript because the
 * Kotlin/JS standard library has no IndexedDB binding. Every method returns a
 * Promise, and the entries travel as JSON text: the code therefore does not
 * depend on the name mangling of the Kotlin properties in a production bundle.
 *
 * The store is created without a key path (the key is passed explicitly), and
 * carries an index on `id` as well.
 */
private val galleryDatabase: dynamic = js("""
(function () {
    var DB_NAME = 'centyllion-gallery';
    var DB_VERSION = 1;
    var STORE = 'entries';
    var INDEX = 'id';

    function open() {
        return new Promise(function (resolve, reject) {
            var request;
            try {
                request = indexedDB.open(DB_NAME, DB_VERSION);
            } catch (e) {
                reject(e);
                return;
            }
            request.onupgradeneeded = function () {
                var db = request.result;
                var store = db.objectStoreNames.contains(STORE)
                    ? request.transaction.objectStore(STORE)
                    : db.createObjectStore(STORE);
                if (!store.indexNames.contains(INDEX)) {
                    store.createIndex(INDEX, INDEX, { unique: true });
                }
            };
            request.onsuccess = function () { resolve(request.result); };
            request.onerror = function () { reject(request.error); };
            request.onblocked = function () { reject(new Error('indexedDB upgrade blocked')); };
        });
    }

    // resolves the JSON text of every stored entry
    function readAll() {
        return open().then(function (db) {
            return new Promise(function (resolve, reject) {
                try {
                    var tx = db.transaction(STORE, 'readonly');
                    var request = tx.objectStore(STORE).getAll();
                    tx.oncomplete = function () {
                        db.close();
                        resolve(JSON.stringify(request.result || []));
                    };
                    tx.onerror = function () { db.close(); reject(tx.error); };
                    tx.onabort = function () { db.close(); reject(tx.error); };
                } catch (e) {
                    db.close();
                    reject(e);
                }
            });
        });
    }

    // replaces the whole store with the entries of the JSON text, atomically
    function write(json) {
        var values = JSON.parse(json);
        return open().then(function (db) {
            return new Promise(function (resolve, reject) {
                try {
                    var tx = db.transaction(STORE, 'readwrite');
                    var store = tx.objectStore(STORE);
                    store.clear();
                    values.forEach(function (value) { store.put(value, value.id); });
                    tx.oncomplete = function () { db.close(); resolve(true); };
                    tx.onerror = function () { db.close(); reject(tx.error); };
                    tx.onabort = function () { db.close(); reject(tx.error); };
                } catch (e) {
                    db.close();
                    reject(e);
                }
            });
        });
    }

    // adds the entries of the JSON text whose id is absent; resolves how many
    function merge(json) {
        var values = JSON.parse(json);
        return open().then(function (db) {
            return new Promise(function (resolve, reject) {
                var added = 0;
                try {
                    var tx = db.transaction(STORE, 'readwrite');
                    var store = tx.objectStore(STORE);
                    var request = store.getAllKeys();
                    request.onsuccess = function () {
                        var existing = request.result || [];
                        values.forEach(function (value) {
                            if (existing.indexOf(value.id) < 0) {
                                store.put(value, value.id);
                                added++;
                            }
                        });
                    };
                    tx.oncomplete = function () { db.close(); resolve(added); };
                    tx.onerror = function () { db.close(); reject(tx.error); };
                    tx.onabort = function () { db.close(); reject(tx.error); };
                } catch (e) {
                    db.close();
                    reject(e);
                }
            });
        });
    }

    return { all: readAll, write: write, merge: merge };
})()
""")

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
