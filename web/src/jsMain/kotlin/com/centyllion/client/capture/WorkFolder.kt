package com.centyllion.client.capture

import com.centyllion.client.blobHref
import com.centyllion.client.download
import com.centyllion.model.captureFileName
import com.centyllion.model.sanitizeCaptureName
import kotlinx.browser.window
import kotlin.js.Promise
import org.w3c.files.Blob

/**
 * The working folder of the captures: a directory chosen by the visitor, remembered in the
 * browser, in which every `Capture()` meta-action writes its picture.
 *
 * The directory handle (`FileSystemDirectoryHandle`) cannot go into localStorage: it is a live
 * object, so it lives in IndexedDB, in the `handles` store of the `centyllion-files` database —
 * the database the save dialog already uses for its own handle — under the key `workFolder`, with
 * the same style of code as `GalleryStore`. localStorage only keeps two strings: the name to
 * display and the fallback mode.
 *
 * File System Access is not available everywhere (Firefox, Safari, and any non-secure page):
 * the directory part of this object is then unsupported and every capture is downloaded through a
 * Blob and a `download` attribute, like the gallery export. A capture never fails silently: the
 * result says which of the two paths was taken and why ([CaptureResult.fallback]).
 */

/** IndexedDB database shared with the file handles of the save dialog. */
const val FILES_DB_NAME = "centyllion-files"

/** Schema version of [FILES_DB_NAME]. */
const val FILES_DB_VERSION = 1

/** Object store of [FILES_DB_NAME]: one handle per key. */
const val FILES_DB_STORE = "handles"

/** Key of the working folder handle in [FILES_DB_STORE]. */
const val WORK_FOLDER_HANDLE_KEY = "workFolder"

/** localStorage key: name of the working folder, displayed only (a plain string). */
const val WORK_FOLDER_NAME_KEY = "centyllion-work-folder-name"

/** localStorage key: where the captures go — [WORK_FOLDER_MODE_FOLDER] or [WORK_FOLDER_MODE_DOWNLOAD]. */
const val WORK_FOLDER_MODE_KEY = "centyllion-work-folder-mode"

/** Mode: a working folder was chosen, the captures are written in it. */
const val WORK_FOLDER_MODE_FOLDER = "folder"

/** Mode: no working folder, the captures are downloaded. */
const val WORK_FOLDER_MODE_DOWNLOAD = "download"

/** State of the working folder, as the interface shows it. */
enum class WorkFolderState {
    /** The browser cannot open a directory (Firefox, Safari, a non-secure page). */
    UNSUPPORTED,

    /** No directory was chosen (or it was forgotten). */
    NOT_DEFINED,

    /** A directory is remembered and the read/write permission is still granted. */
    DEFINED,

    /** A directory is remembered but the browser asks for the permission again. */
    PERMISSION_TO_RENEW
}

/** What the directory picker did. */
enum class FolderChoice {
    /** A folder was chosen and remembered: the captures are written in it. */
    CHOSEN,

    /** A folder was chosen but IndexedDB refused to keep the handle (private mode, quota). */
    UNSAVED,

    /** The visitor closed the picker: nothing changes. */
    CANCELLED,

    /** The browser refused to open the picker. */
    FAILED
}

/** Where a capture went. */
enum class CaptureDestination { FOLDER, DOWNLOAD }

/** Why a capture was downloaded instead of written in the working folder. */
enum class CaptureFallback {
    NONE,

    /** The browser has no directory access at all (Firefox, Safari, a non-secure page). */
    UNSUPPORTED,

    /** No working folder: nothing was ever chosen, or it was forgotten. */
    NO_FOLDER,

    /** The browser no longer grants the read/write permission on the remembered folder. */
    PERMISSION,

    /** The write failed (folder deleted, moved, disk full, handle restored without permission). */
    WRITE_ERROR
}

/** What a capture did: written in the folder, or downloaded, and the file name in both cases. */
data class CaptureResult(
    val destination: CaptureDestination,
    val fileName: String,
    /** Folder and sub-directory of a written capture (`MyFolder/model`), null for a download. */
    val path: String? = null,
    val fallback: CaptureFallback = CaptureFallback.NONE
)

/** Permission state words of the File System Access API. */
private const val PERMISSION_GRANTED = "granted"
private const val PERMISSION_PROMPT = "prompt"
private const val PERMISSION_DENIED = "denied"
private const val PERMISSION_UNKNOWN = "unknown"

object WorkFolder {

    /** Remembered directory handle, null when no folder is defined. */
    private var handle: dynamic = null

    /** Last known read/write permission of [handle]. */
    private var permission: String = PERMISSION_UNKNOWN

    /** True when the browser exposes the directory picker of the File System Access API. */
    fun isSupported(): Boolean = try {
        directoryAccess.supported() as Boolean
    } catch (t: Throwable) {
        false
    }

    /** Name of the working folder, for display only (a string kept in localStorage). */
    val folderName: String? get() = readLocal(WORK_FOLDER_NAME_KEY)?.takeIf { it.isNotBlank() }

    /** Where the captures go, as recorded in localStorage ([WORK_FOLDER_MODE_FOLDER] or download). */
    val mode: String get() = readLocal(WORK_FOLDER_MODE_KEY) ?: WORK_FOLDER_MODE_DOWNLOAD

    /** State of the working folder, from what is known without asking the browser again. */
    val state: WorkFolderState
        get() = when {
            !isSupported() -> WorkFolderState.UNSUPPORTED
            handle == null -> WorkFolderState.NOT_DEFINED
            permission == PERMISSION_GRANTED -> WorkFolderState.DEFINED
            else -> WorkFolderState.PERMISSION_TO_RENEW
        }

    /**
     * Restores the remembered handle from IndexedDB and asks the browser whether its permission is
     * still granted; [onReady] is called in every case (no folder, no database, no API).
     */
    fun restore(onReady: () -> Unit = {}) {
        if (!isSupported()) {
            onReady()
            return
        }
        try {
            val promise = workFolderDatabase.read() as Promise<dynamic>
            promise.then { stored: dynamic ->
                if (stored != null) {
                    handle = stored
                    queryPermission { state ->
                        permission = state
                        onReady()
                    }
                } else {
                    // the handle is the source of truth: a name left by a previous session whose
                    // database was cleared would describe a folder that no longer exists
                    clearLocal()
                    onReady()
                }
            }.catch { error: dynamic ->
                console.warn("Working folder: cannot read the remembered folder from IndexedDB.", error)
                onReady()
            }
        } catch (t: Throwable) {
            console.warn("Working folder: cannot read the remembered folder from IndexedDB.", t)
            onReady()
        }
    }

    /**
     * Opens the directory picker (`showDirectoryPicker({mode: 'readwrite'})`) and remembers the
     * chosen folder. Must be called from a user gesture. [onDone] tells what happened: a cancelled
     * picker leaves everything as it was.
     */
    fun chooseFolder(onDone: (FolderChoice) -> Unit) {
        if (!isSupported()) {
            onDone(FolderChoice.FAILED)
            return
        }
        try {
            val promise = directoryAccess.pick() as Promise<dynamic>
            promise.then { picked: dynamic ->
                handle = picked
                // the picker itself grants the read/write permission
                permission = PERMISSION_GRANTED
                writeLocal(WORK_FOLDER_NAME_KEY, picked.name as? String ?: "")
                writeLocal(WORK_FOLDER_MODE_KEY, WORK_FOLDER_MODE_FOLDER)
                persist { stored -> onDone(if (stored) FolderChoice.CHOSEN else FolderChoice.UNSAVED) }
            }.catch { error: dynamic ->
                // AbortError: the visitor closed the dialog, nothing changes
                if (error != null && error.name == "AbortError") {
                    onDone(FolderChoice.CANCELLED)
                } else {
                    console.warn("Working folder: the directory picker failed.", error)
                    onDone(FolderChoice.FAILED)
                }
            }
        } catch (t: Throwable) {
            console.warn("Working folder: the directory picker is not usable.", t)
            onDone(FolderChoice.FAILED)
        }
    }

    /**
     * Asks the permission back on the remembered folder: the current state is read first
     * (`queryPermission`), then the permission is requested (`requestPermission`) when it is not
     * granted yet — the dialog button provides the user gesture the browser requires. [onDone]
     * receives false when the permission stays refused.
     */
    fun reconnect(onDone: (Boolean) -> Unit) {
        val current = handle
        if (current == null || !isSupported()) {
            onDone(false)
            return
        }
        queryPermission { state ->
            permission = state
            if (state == PERMISSION_GRANTED) {
                onDone(true)
            } else {
                requestPermission(current, onDone)
            }
        }
    }

    /** Requests the read/write permission of [current] (user gesture required). */
    private fun requestPermission(current: dynamic, onDone: (Boolean) -> Unit) {
        try {
            val promise = directoryAccess.request(current) as Promise<dynamic>
            promise.then { result: dynamic ->
                permission = result as? String ?: PERMISSION_UNKNOWN
                val granted = permission == PERMISSION_GRANTED
                if (granted) writeLocal(WORK_FOLDER_MODE_KEY, WORK_FOLDER_MODE_FOLDER)
                onDone(granted)
            }.catch { error: dynamic ->
                console.warn("Working folder: the permission was not granted.", error)
                permission = PERMISSION_UNKNOWN
                onDone(false)
            }
        } catch (t: Throwable) {
            console.warn("Working folder: cannot ask the permission back.", t)
            onDone(false)
        }
    }

    /** Forgets the working folder: the captures are downloaded again. */
    fun forget() {
        handle = null
        permission = PERMISSION_UNKNOWN
        clearLocal()
        try {
            (workFolderDatabase.remove() as Promise<dynamic>).catch { error: dynamic ->
                console.warn("Working folder: cannot forget the folder handle.", error)
            }
        } catch (t: Throwable) {
            console.warn("Working folder: cannot forget the folder handle.", t)
        }
    }

    /** Drops the two strings of localStorage that describe the working folder. */
    private fun clearLocal() {
        writeLocal(WORK_FOLDER_MODE_KEY, WORK_FOLDER_MODE_DOWNLOAD)
        try {
            window.localStorage.removeItem(WORK_FOLDER_NAME_KEY)
        } catch (t: Throwable) {
            console.warn("Working folder: cannot clear the folder name.", t)
        }
    }

    /**
     * Saves the picture of [step] of the model named [modelName]: written in the sub-directory of
     * the working folder that carries the model name (created when needed), or downloaded when
     * there is no folder, when the permission is gone or when the write fails.
     *
     * [onDone] always says what happened, so the interface can tell the visitor which of the two
     * paths was taken and why — a capture never switches to a download silently.
     */
    fun saveCapture(modelName: String, step: Int, payload: Blob, onDone: (CaptureResult) -> Unit) {
        val fileName = captureFileName(modelName, step)
        val subFolder = sanitizeCaptureName(modelName)
        val current = handle
        if (!isSupported()) {
            downloadCapture(fileName, payload)
            onDone(CaptureResult(CaptureDestination.DOWNLOAD, fileName, fallback = CaptureFallback.UNSUPPORTED))
            return
        }
        if (current == null) {
            downloadCapture(fileName, payload)
            onDone(CaptureResult(CaptureDestination.DOWNLOAD, fileName, fallback = CaptureFallback.NO_FOLDER))
            return
        }
        queryPermission { state ->
            permission = state
            if (state != PERMISSION_GRANTED) {
                downloadCapture(fileName, payload)
                onDone(CaptureResult(CaptureDestination.DOWNLOAD, fileName, fallback = CaptureFallback.PERMISSION))
                return@queryPermission
            }
            writeInFolder(current, subFolder, fileName, payload, onDone)
        }
    }

    // ---------------------------------------------------------------- internals

    private fun writeInFolder(
        folder: dynamic, subFolder: String, fileName: String, payload: Blob, onDone: (CaptureResult) -> Unit
    ) {
        try {
            val promise = directoryAccess.writeIn(folder, subFolder, fileName, payload) as Promise<dynamic>
            promise.then { written: dynamic ->
                val name = written as? String ?: subFolder
                val parent = folderName ?: ""
                val path = if (parent.isEmpty()) "$name/$fileName" else "$parent/$name/$fileName"
                onDone(CaptureResult(CaptureDestination.FOLDER, fileName, path))
            }.catch { error: dynamic ->
                console.warn("Working folder: cannot write $subFolder/$fileName, downloading it.", error)
                downloadCapture(fileName, payload)
                onDone(CaptureResult(CaptureDestination.DOWNLOAD, fileName, fallback = CaptureFallback.WRITE_ERROR))
            }
        } catch (t: Throwable) {
            console.warn("Working folder: cannot write $subFolder/$fileName, downloading it.", t)
            downloadCapture(fileName, payload)
            onDone(CaptureResult(CaptureDestination.DOWNLOAD, fileName, fallback = CaptureFallback.WRITE_ERROR))
        }
    }

    /** Plain download through a Blob and an anchor: the path that works in every browser. */
    private fun downloadCapture(fileName: String, payload: Blob) {
        download(fileName, blobHref(payload))
    }

    /** Writes the handle into IndexedDB; [onDone] receives false when the write failed. */
    private fun persist(onDone: (Boolean) -> Unit) {
        try {
            val promise = workFolderDatabase.write(handle) as Promise<dynamic>
            promise.then { saved: dynamic ->
                onDone(saved == true)
            }.catch { error: dynamic ->
                console.warn("Working folder: cannot remember the folder in IndexedDB.", error)
                onDone(false)
            }
        } catch (t: Throwable) {
            console.warn("Working folder: cannot remember the folder in IndexedDB.", t)
            onDone(false)
        }
    }

    /** Asks the browser the read/write permission of the remembered folder. */
    private fun queryPermission(onState: (String) -> Unit) {
        val current = handle
        if (current == null) {
            onState(PERMISSION_UNKNOWN)
            return
        }
        try {
            val promise = directoryAccess.query(current) as Promise<dynamic>
            promise.then { state: dynamic ->
                onState(state as? String ?: PERMISSION_UNKNOWN)
            }.catch { _: dynamic ->
                // a handle restored without the API (another browser) cannot answer
                onState(PERMISSION_UNKNOWN)
            }
        } catch (t: Throwable) {
            onState(PERMISSION_UNKNOWN)
        }
    }

    private fun readLocal(key: String): String? = try {
        window.localStorage.getItem(key)
    } catch (t: Throwable) {
        console.warn("Working folder: local storage is not available.", t)
        null
    }

    private fun writeLocal(key: String, value: String) {
        try {
            window.localStorage.setItem(key, value)
        } catch (t: Throwable) {
            console.warn("Working folder: cannot write into local storage.", t)
        }
    }
}

/**
 * IndexedDB access to the remembered directory handle, written in JavaScript (ES5) because the
 * Kotlin/JS standard library has no IndexedDB binding. Every method returns a Promise; the handle
 * travels as the stored value itself, since a structured clone keeps a `FileSystemDirectoryHandle`
 * usable across sessions in the browsers that support it.
 *
 * The database and the store are the ones of the save dialog ([FILES_DB_NAME],
 * [FILES_DB_STORE]): the store is created without a key path, the key is passed explicitly.
 */
private val workFolderDatabase: dynamic = js("""
(function () {
    var DB_NAME = 'centyllion-files';
    var DB_VERSION = 1;
    var STORE = 'handles';
    var KEY = 'workFolder';

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
                if (!db.objectStoreNames.contains(STORE)) {
                    db.createObjectStore(STORE);
                }
            };
            request.onsuccess = function () { resolve(request.result); };
            request.onerror = function () { reject(request.error); };
            request.onblocked = function () { reject(new Error('indexedDB upgrade blocked')); };
        });
    }

    function read() {
        return open().then(function (db) {
            return new Promise(function (resolve, reject) {
                try {
                    var tx = db.transaction(STORE, 'readonly');
                    var request = tx.objectStore(STORE).get(KEY);
                    tx.oncomplete = function () { db.close(); resolve(request.result || null); };
                    tx.onerror = function () { db.close(); reject(tx.error); };
                    tx.onabort = function () { db.close(); reject(tx.error); };
                } catch (e) {
                    db.close();
                    reject(e);
                }
            });
        });
    }

    function write(handle) {
        return open().then(function (db) {
            return new Promise(function (resolve, reject) {
                try {
                    var tx = db.transaction(STORE, 'readwrite');
                    tx.objectStore(STORE).put(handle, KEY);
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

    function remove() {
        return open().then(function (db) {
            return new Promise(function (resolve, reject) {
                try {
                    var tx = db.transaction(STORE, 'readwrite');
                    tx.objectStore(STORE).delete(KEY);
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

    return { read: read, write: write, remove: remove };
})()
""")

/**
 * Directory access of the File System Access API, in ES5 JavaScript: the picker, the permission
 * calls and the write of one picture in the sub-directory of the model.
 *
 * `writeIn` creates the sub-directory when needed (`getDirectoryHandle` with `create: true`), then
 * the file (`getFileHandle` with `create: true`) and its writable stream, so an existing picture
 * of the same step is replaced instead of duplicated.
 */
private val directoryAccess: dynamic = js("""
(function () {
    function supported() {
        return typeof window.showDirectoryPicker === 'function';
    }

    function pick() {
        return window.showDirectoryPicker({ mode: 'readwrite' });
    }

    function query(handle) {
        return handle.queryPermission({ mode: 'readwrite' });
    }

    function request(handle) {
        return handle.requestPermission({ mode: 'readwrite' });
    }

    function writeIn(folder, subFolder, fileName, payload) {
        return folder.getDirectoryHandle(subFolder, { create: true }).then(function (directory) {
            return directory.getFileHandle(fileName, { create: true });
        }).then(function (file) {
            return file.createWritable();
        }).then(function (writable) {
            return writable.write(payload).then(function () {
                return writable.close();
            });
        }).then(function () {
            return subFolder;
        });
    }

    return { supported: supported, pick: pick, query: query, request: request, writeIn: writeIn };
})()
""")
