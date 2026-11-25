package com.centyllion.client.gallery

import com.centyllion.client.capture.WorkFolder
import com.centyllion.model.gallery.GALLERY_DISK_FOLDER
import com.centyllion.model.gallery.GALLERY_DISK_INDEX
import com.centyllion.model.gallery.GALLERY_IMAGE_MEDIA_TYPE
import com.centyllion.model.gallery.GalleryDiskContent
import com.centyllion.model.gallery.GalleryDiskRead
import com.centyllion.model.gallery.GalleryEntry
import com.centyllion.model.gallery.decodeDisk
import com.centyllion.model.gallery.encodeDisk
import com.centyllion.model.gallery.galleryBackupFileName
import com.centyllion.model.gallery.galleryImageFileName
import com.centyllion.model.gallery.isGalleryImageFileName
import com.centyllion.model.gallery.mergeById
import com.centyllion.model.gallery.toDisk
import com.centyllion.model.gallery.withDataUrls
import kotlin.js.Date
import kotlin.js.Promise

/**
 * Where the personal gallery lives, as the gallery window announces it.
 *
 * The two homes never overlap: the folder is used only when the working folder is defined **and**
 * its read/write permission is granted (the `DEFINED` state of [WorkFolder]); in every other case
 * the gallery stays in the IndexedDB of the browser, exactly as before this feature. The interface
 * says which one is active — there is no silent switch.
 */
sealed class GalleryLocation {
    /** `galerie/` at the root of the working folder named [folder]; [path] is what is displayed. */
    class Disk(val folder: String, val path: String) : GalleryLocation()

    /** The IndexedDB of the browser (`centyllion-gallery`). */
    object Browser : GalleryLocation()
}

/** What the disk held when the gallery was loaded. */
enum class DiskState {
    /** `galerie.json` was read and decoded. */
    LOADED,

    /** There is no `galerie.json` (no folder, or an empty one): nothing was written. */
    MISSING,

    /**
     * `galerie.json` exists but could not be used: it was copied under a dated name and a new,
     * empty file was written. Nothing of the old file is lost.
     */
    UNREADABLE
}

/** Result of a read of the disk gallery. */
class DiskGallery(
    val state: DiskState,
    /** Entries with their pictures as data URLs, ready for the cards. */
    val entries: List<GalleryEntry> = emptyList(),
    /** Dated name under which an unreadable file was kept, null when nothing was kept. */
    val backupName: String? = null
)

/** What a write of the disk gallery did. */
enum class DiskSaveOutcome { SAVED, FAILED }

/** Result of a write of the disk gallery. */
class DiskSaveResult(
    val outcome: DiskSaveOutcome,
    /** Entries as they now are on the disk (pictures back to data URLs). */
    val entries: List<GalleryEntry> = emptyList(),
    /** Dated name under which an unreadable file was kept during this write, null if none. */
    val backupName: String? = null
)

/**
 * The personal gallery on the disk: `galerie/galerie.json` for the metadata, one JPEG file per
 * picture in the same folder. The format is the one of the export (version 1, key `models`), so the
 * folder can be copied to another computer and imported as is.
 *
 * Three rules make the folder safe to share and to open in two tabs:
 *
 * - the JSON is **read again just before every write** and merged by id with what is in memory
 *   ([mergeById]): the last writer wins for an entry, the entries of the other tab are kept;
 * - a deletion drops its id from both sides, then removes **only the picture file referenced by
 *   that entry**, once the confirmation has been given;
 * - a `galerie.json` that cannot be read is never overwritten blindly: it is copied under a dated
 *   name ([galleryBackupFileName]), then a new file is written.
 *
 * No byte of an image enters IndexedDB through this object: pictures are written as files of the
 * folder. The index of the browser is kept as a fallback copy by the controller, never by this
 * object.
 */
object GalleryDiskStorage {

    /** Where the gallery lives right now. Cheap: it reads the state of [WorkFolder]. */
    fun location(): GalleryLocation {
        val folder = WorkFolder.folderName ?: ""
        return if (WorkFolder.isFolderReady) {
            GalleryLocation.Disk(
                folder,
                if (folder.isEmpty()) GALLERY_DISK_FOLDER else "$folder/$GALLERY_DISK_FOLDER"
            )
        } else {
            GalleryLocation.Browser
        }
    }

    /** True when `galerie/` can be read and written without asking the visitor anything. */
    fun isAvailable(): Boolean = WorkFolder.isFolderReady

    /**
     * Reads `galerie/galerie.json` and its pictures. [onLoaded] is always called: an absent file is
     * [DiskState.MISSING], an unusable one is [DiskState.UNREADABLE] (after being kept under a dated
     * name and replaced by a new empty file).
     */
    fun load(onLoaded: (DiskGallery) -> Unit) {
        val handle = WorkFolder.directoryHandle
        if (handle == null) {
            onLoaded(DiskGallery(DiskState.MISSING))
            return
        }
        try {
            val promise = galleryFiles.readText(handle, GALLERY_DISK_FOLDER, GALLERY_DISK_INDEX) as Promise<dynamic>
            promise.then { text: dynamic ->
                when (val read = decodeDisk(text as? String)) {
                    is GalleryDiskRead.Loaded -> readPictures(handle, read.entries) { entries ->
                        onLoaded(DiskGallery(DiskState.LOADED, entries))
                    }
                    GalleryDiskRead.Missing -> onLoaded(DiskGallery(DiskState.MISSING))
                    is GalleryDiskRead.Unreadable -> {
                        console.warn("Gallery: $GALLERY_DISK_INDEX cannot be used (${read.reason}), keeping it aside.")
                        keepAside(handle) { backup ->
                            onLoaded(DiskGallery(DiskState.UNREADABLE, emptyList(), backup))
                        }
                    }
                }
            }.catch { error: dynamic ->
                console.error("Gallery: cannot read $GALLERY_DISK_INDEX.", error)
                onLoaded(DiskGallery(DiskState.UNREADABLE))
            }
        } catch (t: Throwable) {
            console.error("Gallery: cannot read $GALLERY_DISK_INDEX.", t)
            onLoaded(DiskGallery(DiskState.UNREADABLE))
        }
    }

    /**
     * Writes [entries] in the folder, after reading the file again and merging by id: the entries
     * of another tab are kept, and the ids of [removed] are dropped from both sides (a deletion is
     * an explicit action, it must not come back from the file).
     *
     * The pictures are written as JPEG files, the metadata as `galerie.json` without any base64.
     * [onDone] is always called; on failure nothing is announced as saved.
     */
    fun save(
        entries: List<GalleryEntry>,
        removed: List<GalleryEntry> = emptyList(),
        onDone: (DiskSaveResult) -> Unit
    ) {
        val handle = WorkFolder.directoryHandle
        if (handle == null || !WorkFolder.isFolderReady) {
            onDone(DiskSaveResult(DiskSaveOutcome.FAILED))
            return
        }
        write(handle, entries, removed, onDone)
    }

    /**
     * First use of the folder: copies [entries] (the gallery of the browser) into it, once, and only
     * when the disk gallery is missing or empty. Nothing is removed from IndexedDB: the browser copy
     * stays as it is. [onDone] receives what was written, or null when nothing was copied — the
     * folder already has a gallery, or the copy failed.
     */
    fun seedIfEmpty(entries: List<GalleryEntry>, onDone: (DiskSaveResult?) -> Unit) {
        if (entries.isEmpty()) {
            onDone(null)
            return
        }
        val handle = WorkFolder.directoryHandle
        if (handle == null || !WorkFolder.isFolderReady) {
            onDone(null)
            return
        }
        readIndex(handle) { text ->
            val read = decodeDisk(text)
            when {
                // the folder already has a gallery: nothing is copied over it
                read is GalleryDiskRead.Loaded && read.entries.isNotEmpty() -> onDone(null)
                read is GalleryDiskRead.Unreadable -> keepAside(handle) { backup ->
                    if (backup == null) {
                        onDone(null)
                    } else {
                        write(handle, entries, emptyList()) { result ->
                            onDone(result.takeIf { it.outcome == DiskSaveOutcome.SAVED })
                        }
                    }
                }
                else -> write(handle, entries, emptyList()) { result ->
                    onDone(result.takeIf { it.outcome == DiskSaveOutcome.SAVED })
                }
            }
        }
    }

    // ---------------------------------------------------------------- internals

    /**
     * Writes the merged gallery: pictures first, then the JSON, so an interrupted write leaves a
     * readable file pointing at pictures that exist. Pictures of the entries of [removed] are
     * deleted afterwards, and only when they are the file named by that entry and no remaining
     * entry refers to it.
     */
    private fun write(
        handle: dynamic,
        entries: List<GalleryEntry>,
        removed: List<GalleryEntry>,
        onDone: (DiskSaveResult) -> Unit
    ) {
        val exportedAt = Date(Date.now()).toISOString()
        readIndex(handle) { text ->
            val read = decodeDisk(text)
            val diskEntries = if (read is GalleryDiskRead.Loaded) read.entries else emptyList()
            val proceed: (String?) -> Unit = { backup ->
                val merged = mergeById(diskEntries, entries, removed.map { it.id }.toSet())
                val content = toDisk(merged, exportedAt)
                writeContent(handle, content) { written ->
                    if (written) {
                        removePictures(handle, removed, merged)
                        // pictures read back as data URLs: the cards do not depend on the disk afterwards
                        readPictures(handle, merged) { resolved ->
                            onDone(DiskSaveResult(DiskSaveOutcome.SAVED, resolved, backup))
                        }
                    } else {
                        onDone(DiskSaveResult(DiskSaveOutcome.FAILED))
                    }
                }
            }
            if (read is GalleryDiskRead.Unreadable) {
                console.warn("Gallery: $GALLERY_DISK_INDEX cannot be used (${read.reason}), keeping it aside.")
                keepAside(handle) { backup -> proceed(backup) }
            } else {
                proceed(null)
            }
        }
    }

    /** Reads `galerie.json` without creating anything; null when it is absent. */
    private fun readIndex(handle: dynamic, onText: (String?) -> Unit) {
        try {
            val promise = galleryFiles.readText(handle, GALLERY_DISK_FOLDER, GALLERY_DISK_INDEX) as Promise<dynamic>
            promise.then { text: dynamic -> onText(text as? String) }
                .catch { error: dynamic ->
                    console.warn("Gallery: cannot read $GALLERY_DISK_INDEX.", error)
                    onText(null)
                }
        } catch (t: Throwable) {
            console.warn("Gallery: cannot read $GALLERY_DISK_INDEX.", t)
            onText(null)
        }
    }

    /** Writes the pictures then the JSON; [onDone] receives false when the folder refused a write. */
    private fun writeContent(handle: dynamic, content: GalleryDiskContent, onDone: (Boolean) -> Unit) {
        var index = 0
        fun next() {
            if (index >= content.images.size) {
                writeIndex(handle, content.json, onDone)
                return
            }
            val image = content.images[index++]
            try {
                val promise = galleryFiles.writeBytes(
                    handle, GALLERY_DISK_FOLDER, image.fileName, image.bytes, image.mediaType
                ) as Promise<dynamic>
                promise.then { _: dynamic -> next() }.catch { error: dynamic ->
                    console.error("Gallery: cannot write ${image.fileName}.", error)
                    onDone(false)
                }
            } catch (t: Throwable) {
                console.error("Gallery: cannot write ${image.fileName}.", t)
                onDone(false)
            }
        }
        next()
    }

    private fun writeIndex(handle: dynamic, json: String, onDone: (Boolean) -> Unit) {
        try {
            val promise = galleryFiles.writeText(handle, GALLERY_DISK_FOLDER, GALLERY_DISK_INDEX, json) as Promise<dynamic>
            promise.then { saved: dynamic -> onDone(saved == true) }.catch { error: dynamic ->
                console.error("Gallery: cannot write $GALLERY_DISK_INDEX.", error)
                onDone(false)
            }
        } catch (t: Throwable) {
            console.error("Gallery: cannot write $GALLERY_DISK_INDEX.", t)
            onDone(false)
        }
    }

    /**
     * Copies an unreadable `galerie.json` under a dated name, then writes a new empty file in its
     * place: the old content is kept, the next write starts from a valid file. [onDone] receives the
     * dated name, or null when nothing could be kept (the original file is then left untouched).
     */
    private fun keepAside(handle: dynamic, onDone: (String?) -> Unit) {
        val backup = galleryBackupFileName(stamp())
        try {
            val copy = galleryFiles.copyText(handle, GALLERY_DISK_FOLDER, GALLERY_DISK_INDEX, backup) as Promise<dynamic>
            copy.then { copied: dynamic ->
                if (copied != true) {
                    onDone(null)
                } else {
                    val fresh = encodeDisk(emptyList(), Date(Date.now()).toISOString())
                    writeIndex(handle, fresh) { written -> onDone(if (written) backup else null) }
                }
            }.catch { error: dynamic ->
                console.error("Gallery: cannot keep $GALLERY_DISK_INDEX aside.", error)
                onDone(null)
            }
        } catch (t: Throwable) {
            console.error("Gallery: cannot keep $GALLERY_DISK_INDEX aside.", t)
            onDone(null)
        }
    }

    /**
     * Deletes the picture file of every entry of [removed], and only that one: a file still
     * referenced by a remaining entry, or a name that is not the one derived from the id of the
     * entry (a served path, a data URL), is left alone. Best effort: a failed deletion is logged,
     * never reported as a failed save.
     */
    private fun removePictures(handle: dynamic, removed: List<GalleryEntry>, kept: List<GalleryEntry>) {
        val referenced = kept.map { it.image }.toSet()
        removed.forEach { entry ->
            // the entry in memory may carry the picture as a data URL (loaded from the folder): the
            // name of its file is the one derived from its id, never another one
            val name = if (isGalleryImageFileName(entry.image)) entry.image else galleryImageFileName(entry.id)
            if (!isGalleryImageFileName(name) || name in referenced) return@forEach
            if (name != galleryImageFileName(entry.id)) return@forEach
            try {
                val promise = galleryFiles.remove(handle, GALLERY_DISK_FOLDER, name) as Promise<dynamic>
                promise.then { removedFile: dynamic ->
                    if (removedFile == true) console.log("Gallery: $name removed from the folder.")
                }.catch { error: dynamic -> console.warn("Gallery: cannot remove $name.", error) }
            } catch (t: Throwable) {
                console.warn("Gallery: cannot remove $name.", t)
            }
        }
    }

    /**
     * Turns the file names of [entries] back into data URLs, so the cards show the pictures of the
     * folder. An entry without a picture file keeps its metadata and gets an empty image.
     */
    private fun readPictures(handle: dynamic, entries: List<GalleryEntry>, onDone: (List<GalleryEntry>) -> Unit) {
        val names = entries.map { it.image }.filter { isGalleryImageFileName(it) }.distinct()
        if (names.isEmpty()) {
            onDone(entries)
            return
        }
        try {
            val promise = galleryFiles.readDataUrls(
                handle, GALLERY_DISK_FOLDER, names.toTypedArray(), GALLERY_IMAGE_MEDIA_TYPE
            ) as Promise<dynamic>
            promise.then { urls: dynamic ->
                @Suppress("UNCHECKED_CAST")
                val list = urls as Array<String?>
                val byName = HashMap<String, String>(names.size)
                names.forEachIndexed { index, name ->
                    val url = list.getOrNull(index)
                    if (url != null) byName[name] = url
                }
                onDone(withDataUrls(entries, byName))
            }.catch { error: dynamic ->
                console.warn("Gallery: cannot read the pictures of the folder.", error)
                onDone(entries)
            }
        } catch (t: Throwable) {
            console.warn("Gallery: cannot read the pictures of the folder.", t)
            onDone(entries)
        }
    }

    /** Compact stamp of the current date, used to name a file kept aside: `20261125-101233`. */
    private fun stamp(): String = Date(Date.now()).toISOString()
        .take(19)
        .replace("-", "")
        .replace(":", "")
        .replace("T", "-")
}

/**
 * Files of the gallery folder, in ES5 JavaScript: the File System Access API is not bound by the
 * Kotlin/JS standard library, and `galerie.json` is text while the pictures are bytes.
 *
 * Every function returns a Promise. `readText` resolves null when the file (or the folder) does not
 * exist instead of rejecting, so an absent gallery is a normal answer; the others reject only when
 * the folder itself refuses the operation. `copyText` keeps an unreadable file: it copies it under
 * another name and leaves the original in place.
 */
private val galleryFiles: dynamic = js("""
(function () {
    function isMissing(error) {
        return !!error && (error.name === 'NotFoundError' || error.name === 'TypeMismatchError');
    }

    function folderOf(dir, name, create) {
        return dir.getDirectoryHandle(name, { create: !!create });
    }

    function readText(dir, folderName, name) {
        return folderOf(dir, folderName, false).then(function (gallery) {
            return gallery.getFileHandle(name).then(function (handle) { return handle.getFile(); });
        }).then(function (file) {
            return file.text();
        }).catch(function (error) {
            if (isMissing(error)) return null;
            throw error;
        });
    }

    function writeBlob(dir, folderName, name, blob) {
        return folderOf(dir, folderName, true).then(function (gallery) {
            return gallery.getFileHandle(name, { create: true });
        }).then(function (file) {
            return file.createWritable();
        }).then(function (writable) {
            return writable.write(blob).then(function () { return writable.close(); });
        }).then(function () {
            return true;
        });
    }

    function writeText(dir, folderName, name, text) {
        return writeBlob(dir, folderName, name, new Blob([text], { type: 'application/json' }));
    }

    function writeBytes(dir, folderName, name, payload, mediaType) {
        // a Kotlin ByteArray arrives as an Int8Array: the unsigned view keeps the same bits
        var bytes = (payload instanceof Uint8Array) ? payload : new Uint8Array(payload);
        return writeBlob(dir, folderName, name, new Blob([bytes], { type: mediaType || 'image/jpeg' }));
    }

    function copyText(dir, folderName, from, to) {
        return readText(dir, folderName, from).then(function (text) {
            if (text === null) return false;
            return writeText(dir, folderName, to, text);
        });
    }

    function remove(dir, folderName, name) {
        return folderOf(dir, folderName, false).then(function (gallery) {
            return gallery.removeEntry(name);
        }).then(function () {
            return true;
        }).catch(function (error) {
            if (isMissing(error)) return false;
            throw error;
        });
    }

    function toDataUrl(buffer, mediaType) {
        var bytes = new Uint8Array(buffer);
        var binary = '';
        var chunk = 0x8000;
        for (var i = 0; i < bytes.length; i += chunk) {
            binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunk));
        }
        return 'data:' + (mediaType || 'image/jpeg') + ';base64,' + window.btoa(binary);
    }

    // one data URL per name, in the same order; null for a file that cannot be read
    function readDataUrls(dir, folderName, names, mediaType) {
        if (!names || names.length === 0) return Promise.resolve([]);
        return folderOf(dir, folderName, false).then(function (gallery) {
            return Promise.all(names.map(function (name) {
                return gallery.getFileHandle(name)
                    .then(function (handle) { return handle.getFile(); })
                    .then(function (file) { return file.arrayBuffer(); })
                    .then(function (buffer) { return toDataUrl(buffer, mediaType); })
                    .catch(function () { return null; });
            }));
        }).catch(function () {
            return names.map(function () { return null; });
        });
    }

    return {
        readText: readText,
        writeText: writeText,
        writeBytes: writeBytes,
        copyText: copyText,
        remove: remove,
        readDataUrls: readDataUrls
    };
})()
""")
