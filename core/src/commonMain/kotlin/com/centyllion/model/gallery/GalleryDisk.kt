package com.centyllion.model.gallery

import com.centyllion.model.sanitizeCaptureName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Format of the personal gallery when it is written on the disk, inside the working folder.
 *
 * The gallery has two possible homes and only one is active at a time: the folder `galerie/` of
 * the working folder when a folder is defined and its read/write permission granted, the IndexedDB
 * of the browser otherwise (see `GalleryDiskStorage` in the web module). The two formats are the
 * same one (`version`, `exportedAt`, `models`), so a `galerie/` folder is a gallery export that can
 * be imported as is — and an export can be dropped into a folder.
 *
 * On the disk the picture is never written as base64 inside the JSON: `image` holds the name of a
 * separate JPEG file of the same folder (`image-<id>.jpg`), while in the browser and in an export
 * the same field holds the data URL. [toDisk] and [fromDisk] do that conversion; both are pure, so
 * they are tested in `:core:jvmTest`, without a browser.
 */

/** Folder, directly inside the working folder, holding the personal gallery. */
const val GALLERY_DISK_FOLDER = "galerie"

/** Name of the metadata file of the gallery inside [GALLERY_DISK_FOLDER]. */
const val GALLERY_DISK_INDEX = "galerie.json"

/** Prefix of the image files of the gallery: they are recognisable, and never named [GALLERY_DISK_INDEX]. */
const val GALLERY_IMAGE_PREFIX = "image-"

/** Extension of the image files of [GALLERY_DISK_FOLDER]: the pictures are JPEG. */
const val GALLERY_IMAGE_EXTENSION = "jpg"

/** Media type written in the data URL rebuilt from an image file. */
const val GALLERY_IMAGE_MEDIA_TYPE = "image/jpeg"

/** Stem used when an entry has no id usable as a file name (empty or blank). */
const val GALLERY_IMAGE_FALLBACK_STEM = "entry"

/** One card of the gallery, in the browser, in an export and on the disk. */
@Serializable
data class GalleryEntry(
    val id: String,
    val title: String = "",
    val description: String = "",
    /**
     * Picture of the card: a data URL in the browser and in an export, the name of a JPEG file of
     * `galerie/` on the disk (see [galleryImageFileName]).
     */
    val image: String = "",
    /** Path of the model file, relative to the served root (static entries only). */
    val file: String? = null,
    /** JSON text of the model, kept for the models imported by the user. */
    val model: String? = null,
    /** Masks a static entry without touching `gallery.json`. */
    val hidden: Boolean = false,
    /**
     * ISO-8601 date of the entry, written on the disk only: it is the date the entry was added to
     * the gallery of the folder. Empty for an entry that never left the browser.
     */
    val date: String = ""
) {
    /** True for an entry carrying its own model text (a model imported by the user). */
    val imported: Boolean get() = !model.isNullOrBlank()
}

/** Content of `Gallery/gallery.json` (the distributed index) and of `galerie/galerie.json`. */
@Serializable
data class GalleryIndex(
    val version: Int = 1,
    val models: List<GalleryEntry> = emptyList()
)

/** Content of a personal gallery export, as downloaded by the visitor — and of `galerie.json`. */
@Serializable
data class GalleryExport(
    val version: Int = 1,
    /** ISO-8601 timestamp of the export (or of the last write on the disk). */
    val exportedAt: String = "",
    val models: List<GalleryEntry> = emptyList()
)

/** An image taken out of a data URL: exactly what a separate file of `galerie/` holds. */
class GalleryImageFile(
    /** Name of the file inside `galerie/`, derived from the id of the entry. */
    val fileName: String,
    val bytes: ByteArray,
    val mediaType: String = GALLERY_IMAGE_MEDIA_TYPE
)

/** Result of the conversion of a gallery to the on-disk form: one JSON text and one file per picture. */
class GalleryDiskContent(
    /** Text of `galerie.json`, same format as an export, with file names instead of data URLs. */
    val json: String,
    /** JPEG files to write beside it. */
    val images: List<GalleryImageFile>
)

/** What reading `galerie.json` gave. */
sealed class GalleryDiskRead {
    /** The file was read and decoded: its entries are usable. */
    class Loaded(val entries: List<GalleryEntry>, val exportedAt: String) : GalleryDiskRead()

    /** There is no `galerie.json` (no `galerie/` folder, or an empty one). */
    object Missing : GalleryDiskRead()

    /**
     * The file exists but cannot be trusted (not JSON, no `models` array, a newer version, an entry
     * without id, a decoding failure). Nothing must be overwritten on its basis: the caller keeps it
     * under a dated name and writes a new file.
     */
    class Unreadable(val reason: String) : GalleryDiskRead()
}

private val galleryJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

private val galleryDiskJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
    // the format is a contract: version and exportedAt are always written
    encodeDefaults = true
}

private val galleryEntriesSerializer = ListSerializer(GalleryEntry.serializer())

/**
 * Name of the image file of the entry [id]: `image-<id>.jpg`.
 *
 * The id is cleaned with the rules of the captures ([sanitizeCaptureName]): `/`, `\`, `:`, `*`,
 * `?`, `"`, `<`, `>`, `|` and the control characters become `_`, the surrounding spaces and dots
 * are dropped, the length is bounded and a Windows device name gets a leading underscore. An empty
 * id gives [GALLERY_IMAGE_FALLBACK_STEM], so a file always has a name. Two ids that clean to the
 * same text get distinct names (see [toDisk]): a picture is never overwritten by another entry.
 */
fun galleryImageFileName(id: String): String {
    val stem = if (id.isBlank()) GALLERY_IMAGE_FALLBACK_STEM else sanitizeCaptureName(id)
    return "$GALLERY_IMAGE_PREFIX$stem.$GALLERY_IMAGE_EXTENSION"
}

/** True for a value of `image` that names a file of `galerie/` (and not a data URL or a served path). */
fun isGalleryImageFileName(image: String): Boolean =
    image.isNotBlank() &&
        !image.startsWith("data:", ignoreCase = true) &&
        !image.contains('/') && !image.contains('\\') &&
        image.startsWith(GALLERY_IMAGE_PREFIX) &&
        image.endsWith(".$GALLERY_IMAGE_EXTENSION", ignoreCase = true)

/**
 * Splits a data URL (`data:image/jpeg;base64,...`) into its media type and its bytes; null when the
 * text is not a base64 data URL — an image that is already a file name, a served path or nothing.
 */
@OptIn(ExperimentalEncodingApi::class)
fun decodeImageDataUrl(dataUrl: String): Pair<String, ByteArray>? {
    val trimmed = dataUrl.trim()
    if (!trimmed.startsWith("data:", ignoreCase = true)) return null
    val comma = trimmed.indexOf(',')
    if (comma < 0) return null
    val header = trimmed.substring(5, comma)
    if (!header.contains("base64", ignoreCase = true)) return null
    val mediaType = header.substringBefore(';').ifBlank { GALLERY_IMAGE_MEDIA_TYPE }
    // a data URL read from a file may be wrapped: the base64 payload ignores the white space
    val payload = trimmed.substring(comma + 1).filterNot { it.isWhitespace() }
    if (payload.isEmpty()) return null
    val bytes = try {
        Base64.decode(payload)
    } catch (t: Throwable) {
        return null
    }
    return mediaType to bytes
}

/** Rebuilds a data URL from the bytes of an image file: the form the browser shows in a card. */
@OptIn(ExperimentalEncodingApi::class)
fun encodeImageDataUrl(bytes: ByteArray, mediaType: String = GALLERY_IMAGE_MEDIA_TYPE): String =
    "data:$mediaType;base64,${Base64.encode(bytes)}"

/**
 * Converts a gallery to the on-disk format: `image` becomes a file name and the picture itself is
 * returned as a JPEG file of [GalleryDiskContent.images]. An entry whose image is not a data URL
 * (no picture, a served path, a file name already) is kept as is.
 *
 * [exportedAt] is the ISO-8601 date of the write: it also fills the [GalleryEntry.date] of the
 * entries that do not have one yet, so the JSON always carries a date per entry.
 */
fun toDisk(entries: List<GalleryEntry>, exportedAt: String): GalleryDiskContent {
    val images = ArrayList<GalleryImageFile>(entries.size)
    val usedNames = HashSet<String>(entries.size)
    // an id repeated in the list is written once: the last value wins, like a dictionary
    val unique = LinkedHashMap<String, GalleryEntry>(entries.size)
    entries.forEach { unique[it.id] = it }
    val models = unique.values.map { entry ->
        val date = entry.date.ifBlank { exportedAt }
        val payload = decodeImageDataUrl(entry.image)
        if (payload == null) {
            entry.copy(date = date)
        } else {
            var fileName = galleryImageFileName(entry.id)
            if (!usedNames.add(fileName)) {
                // two ids cleaned to the same text: the second one gets a numbered file of its own
                var suffix = 2
                while (!usedNames.add("${fileName.substringBeforeLast('.')}-$suffix.$GALLERY_IMAGE_EXTENSION")) suffix++
                fileName = "${fileName.substringBeforeLast('.')}-$suffix.$GALLERY_IMAGE_EXTENSION"
            }
            images += GalleryImageFile(fileName, payload.second, payload.first)
            entry.copy(image = fileName, date = date)
        }
    }
    return GalleryDiskContent(encodeDisk(models, exportedAt), images)
}

/** JSON text of `galerie.json`: the export format, version 1, key `models`. */
fun encodeDisk(entries: List<GalleryEntry>, exportedAt: String): String =
    galleryDiskJson.encodeToString(
        GalleryExport.serializer(),
        GalleryExport(version = 1, exportedAt = exportedAt, models = entries)
    )

/**
 * Reads the text of `galerie.json`. [jsonText] is null when the file does not exist; anything that
 * cannot be used gives [GalleryDiskRead.Unreadable], with the reason logged by the caller, so the
 * file is never overwritten blindly.
 */
fun decodeDisk(jsonText: String?): GalleryDiskRead {
    if (jsonText == null) return GalleryDiskRead.Missing
    if (jsonText.isBlank()) return GalleryDiskRead.Unreadable("empty file")
    val root = try {
        galleryJson.parseToJsonElement(jsonText)
    } catch (t: Throwable) {
        return GalleryDiskRead.Unreadable("not JSON")
    }
    val rootObject = root as? JsonObject ?: return GalleryDiskRead.Unreadable("not a JSON object")
    val models = rootObject["models"] as? JsonArray ?: return GalleryDiskRead.Unreadable("no models array")
    val version = (rootObject["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
    if (version > 1) return GalleryDiskRead.Unreadable("version $version is newer than 1")
    val entries = try {
        galleryJson.decodeFromJsonElement(galleryEntriesSerializer, models)
    } catch (t: Throwable) {
        return GalleryDiskRead.Unreadable("entries cannot be decoded")
    }
    if (entries.any { it.id.isBlank() }) return GalleryDiskRead.Unreadable("an entry has no id")
    val exportedAt = (rootObject["exportedAt"] as? JsonPrimitive)?.content ?: ""
    return GalleryDiskRead.Loaded(entries, exportedAt)
}

/**
 * Rebuilds the entries loaded from the disk: an `image` naming a file present in [images] becomes a
 * data URL again. An entry whose file is missing keeps its metadata (title, description, model) and
 * the name of its file, so a lost or unreadable picture never costs the model.
 */
fun fromDisk(entries: List<GalleryEntry>, images: Map<String, ByteArray>): List<GalleryEntry> =
    withDataUrls(entries, images.mapValues { (_, bytes) -> encodeImageDataUrl(bytes) })

/**
 * Replaces the file name of `image` by its data URL, [urls] giving the data URL of each file name.
 * A name that is not in [urls] (a data URL already, a served path, a picture that was deleted by
 * hand) is left untouched.
 */
fun withDataUrls(entries: List<GalleryEntry>, urls: Map<String, String>): List<GalleryEntry> =
    entries.map { entry ->
        val url = urls[entry.image]
        // a name that is not in [urls] (as served path, a picture that could not be read) is kept as
        // is: the entry never loses the reference of its picture, even when the picture is unreadable
        if (url == null) entry else entry.copy(image = url)
    }

/**
 * Merges the gallery of the caller [mine] into the one just read from the disk [disk], by id:
 *
 * - an id present in both keeps the value of [mine] — the last writer wins for that entry;
 * - an id present only on the disk is kept, so an entry written by another tab is never lost;
 * - an id present only in [mine] is added;
 * - the ids of [removed] are dropped from both sides: a deletion is an explicit user action, so it
 *   is the one change that must not come back from the file.
 *
 * The order of the disk is kept, then the new entries are appended.
 */
fun mergeById(
    disk: List<GalleryEntry>,
    mine: List<GalleryEntry>,
    removed: Set<String> = emptySet()
): List<GalleryEntry> {
    val mineById = mine.filterNot { it.id in removed }.associateBy { it.id }
    val merged = ArrayList<GalleryEntry>(disk.size + mine.size)
    val seen = HashSet<String>(disk.size + mine.size)
    disk.forEach { entry ->
        if (entry.id in removed || !seen.add(entry.id)) return@forEach
        merged += mineById[entry.id] ?: entry
    }
    mine.forEach { entry ->
        if (entry.id in removed || !seen.add(entry.id)) return@forEach
        merged += entry
    }
    return merged
}

/**
 * Name under which an unreadable `galerie.json` is kept before a new one is written:
 * `galerie.bak-<date>.json`, [stamp] being compact (`20261125-101233`). The old file is copied, never
 * deleted, so a visitor who repairs it by hand still finds it.
 */
fun galleryBackupFileName(stamp: String): String {
    val cleaned = stamp.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "backup" }
    return "galerie.bak-$cleaned.json"
}
