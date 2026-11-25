package com.centyllion.client.gallery

import bulma.*
import com.centyllion.client.blobHref
import com.centyllion.client.capture.WorkFolder
import com.centyllion.client.capture.WorkFolderState
import com.centyllion.client.decodeModelAndSimulation
import com.centyllion.client.download
import com.centyllion.client.page.ShowPage
import com.centyllion.model.gallery.GALLERY_DISK_FOLDER
import com.centyllion.model.gallery.GALLERY_DISK_INDEX
import com.centyllion.model.gallery.GALLERY_IMAGE_EXTENSION
import com.centyllion.model.gallery.galleryImageFileName
import com.centyllion.model.gallery.isGalleryImageFileName
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLStyleElement
import org.w3c.dom.events.Event
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import org.w3c.files.FileList
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.js.Date
import kotlin.math.max
import kotlin.math.min

/** Largest image data URL kept as is: bigger ones are resized (a card does not need more). */
private const val MAX_INLINE_IMAGE_CHARS = 700_000

/** Longest side of a resized image, in pixels. */
private const val IMAGE_MAX_SIZE = 640.0

/**
 * Model gallery window: the models shipped with the software
 * (`Gallery/gallery.json`) merged with the models the visitor added or edited
 * locally. The personal gallery lives in the working folder (`galerie/`, see
 * [GalleryDiskStorage]) when that folder is defined and its permission granted,
 * and in the IndexedDB of the browser otherwise (see [GalleryStore]). The
 * window always says which of the two is active: there is no silent switch.
 * Opened from the "Gallery" button of the page.
 */
class GalleryController(private val page: ShowPage) {

    /** Entries of the static index, empty until it is fetched. */
    private var indexEntries: List<GalleryEntry> = emptyList()

    /** Personal gallery: entries added by the visitor, and edits or masks of static entries. */
    private var overlay: List<GalleryEntry> = emptyList()

    /** Root of the gallery window, null when it is closed. */
    private var modal: ModalCard? = null

    private val grid = Div(classes = "centy-gallery-grid")

    private val status = p(classes = "centy-gallery-status")

    /** Line telling where the personal gallery lives (disk with its path, or browser). */
    private val locationLine = p(classes = "centy-gallery-location")

    /** What to do when the folder is remembered but its permission was not granted again. */
    private val locationHint = p(classes = "centy-gallery-location-hint")

    /** Where the personal gallery lives; browser until the state of the folder is known. */
    private var location: GalleryLocation = GalleryLocation.Browser

    /** True when the static index could not be fetched: only the local models are shown. */
    private var indexFailed = false

    /** Opens (or reopens) the gallery window. */
    fun open() {
        ensureStyles()
        indexEntries = emptyList()
        grid.body = emptyList()
        indexFailed = false
        status.hidden = false
        status.text = page.i18n("Loading gallery...")
        locationLine.text = page.i18n("Loading gallery...")
        locationHint.hidden = true

        var card: ModalCard? = null
        val addButton = Button(page.i18n("Add Model"), Icon("plus"), color = ElementColor.Primary) { showForm(null) }
        // export and import only concern the personal gallery: gallery.json is read only
        val exportButton = Button(
            page.i18n("Export my gallery"), Icon("download"), color = ElementColor.Primary
        ) { exportPersonal() }
        val importButton = importControl()
        val helpButton = Button(
            page.i18n("Where is my gallery?"), Icon("question-circle"), color = ElementColor.Light
        ) { showLocationHelp() }
        val closeButton = Button(page.i18n("Close"), color = ElementColor.Primary) { card?.active = false }
        val toolbar = Field(
            Control(addButton), Control(exportButton), Control(importButton), Control(helpButton),
            grouped = true, groupedMultiline = true
        )

        card = showModal(
            page.i18n("Model gallery"),
            listOf(locationLine, locationHint, toolbar, status, grid),
            listOf(closeButton)
        )
        card.root.classList.add("centy-gallery-modal")
        modal = card

        loadIndex()
        // the working folder is restored once at start-up: wait for its state instead of guessing it
        WorkFolder.whenReady { loadPersonal() }
    }

    // ------------------------------------------------------------------ window

    /** Appends a modal card to the page; it removes itself from the page when closed. */
    private fun showModal(title: String, body: List<BulmaElement>, buttons: List<Button>): ModalCard {
        val card = ModalCard(title, body, buttons) { closed ->
            closed.root.parentNode?.removeChild(closed.root)
            if (modal === closed) modal = null
        }
        page.root.appendChild(card.root)
        card.active = true
        return card
    }

    // ------------------------------------------------------------------- index

    private fun loadIndex() {
        val w = window.asDynamic()
        w.fetch(GALLERY_INDEX_URL).then { response: dynamic ->
            if (response.ok != true) throw IllegalStateException("HTTP ${response.status} for $GALLERY_INDEX_URL")
            response.text().then { content: dynamic ->
                indexEntries = GalleryStore.decodeIndex(content as String).models
                render()
            }
        }.catch { error: dynamic ->
            console.warn("Gallery: cannot load $GALLERY_INDEX_URL", error)
            indexEntries = emptyList()
            indexFailed = true
            // the models added locally stay available
            render()
        }
    }

    // ------------------------------------------------------------------- local

    /**
     * Reads the personal gallery from its active home: `galerie/` of the working folder when the
     * folder is available, the IndexedDB of the browser otherwise. The cards are drawn again once it
     * is known (the distributed index may arrive first).
     */
    private fun loadPersonal() {
        location = GalleryDiskStorage.location()
        renderLocation()
        when (location) {
            is GalleryLocation.Disk -> loadFromDisk()
            GalleryLocation.Browser -> GalleryStore.load { entries ->
                overlay = entries
                render()
            }
        }
    }

    /** Reads `galerie/galerie.json`; an empty or absent folder is filled from the browser, once. */
    private fun loadFromDisk() {
        GalleryDiskStorage.load { disk ->
            when (disk.state) {
                DiskState.LOADED -> {
                    overlay = disk.entries
                    render()
                    if (disk.entries.isEmpty()) seedFromBrowser()
                }
                DiskState.MISSING -> {
                    overlay = emptyList()
                    render()
                    seedFromBrowser()
                }
                DiskState.UNREADABLE -> {
                    overlay = emptyList()
                    render()
                    if (disk.backupName != null) {
                        page.warning(
                            "%0 could not be read: it was kept as %1 and a new file was created.",
                            GALLERY_DISK_INDEX, disk.backupName
                        )
                    } else {
                        page.warning("%0 could not be read: nothing was changed on the disk.", GALLERY_DISK_INDEX)
                    }
                    seedFromBrowser()
                }
            }
        }
    }

    /**
     * First use of the folder: the entries of the browser are copied into it, once, and the visitor
     * is told so. Nothing is removed from IndexedDB — the browser copy is kept as the fallback. When
     * the folder refuses the copy, the gallery of the browser is used again and the switch is
     * announced, so no entry lives only in a write that failed.
     */
    private fun seedFromBrowser() {
        val disk = location as? GalleryLocation.Disk ?: return
        GalleryStore.load { entries ->
            if (entries.isEmpty()) {
                render()
                return@load
            }
            GalleryDiskStorage.seedIfEmpty(entries) { written ->
                if (written != null) {
                    // what the folder holds: dates filled at the write, pictures as data URLs again
                    overlay = written.entries
                    page.message(
                        "Gallery copied to %0: %1 entries of the browser are now on the disk; the browser copy is kept.",
                        disk.path, written.entries.size
                    )
                } else {
                    overlay = entries
                    location = GalleryLocation.Browser
                    renderLocation()
                    page.warning(
                        "The gallery of this browser could not be copied to %0: it stays in the browser.",
                        disk.path
                    )
                }
                render()
            }
        }
    }

    /** Says where the personal gallery lives, and what to do when its folder is out of reach. */
    private fun renderLocation() {
        val disk = location as? GalleryLocation.Disk
        locationLine.text = if (disk != null) {
            page.i18n(
                "Gallery stored on the disk: %0 (%1 for the metadata, one %2 file per picture).",
                disk.path, GALLERY_DISK_INDEX, GALLERY_IMAGE_EXTENSION
            )
        } else {
            page.i18n("Gallery stored in this browser; export it to keep it or to transfer it.")
        }
        val hint = when (WorkFolder.state) {
            WorkFolderState.PERMISSION_TO_RENEW -> page.i18n(
                "The working folder %0 is remembered but its permission must be renewed: renew it with the home button to store the gallery on the disk.",
                WorkFolder.folderName ?: ""
            )
            WorkFolderState.UNSUPPORTED -> page.i18n(
                "This browser has no access to a folder: the gallery stays in its storage."
            )
            else -> ""
        }
        locationHint.text = hint
        locationHint.hidden = hint.isEmpty()
    }

    /** Explains the two homes of the personal gallery, the disk format and the sharing by copy. */
    private fun showLocationHelp() {
        val disk = location as? GalleryLocation.Disk
        val body = listOf(
            p(
                page.i18n(
                    "The personal gallery has one home at a time, and this line always says which one: the folder galerie of the working folder, or the storage of this browser."
                )
            ),
            p(
                page.i18n(
                    "On the disk: %0 holds the metadata of each entry (id, title, description, name of its picture, date) and every picture is a separate JPEG file of the same folder — never base64 inside the JSON. The file has the format of the export (version 1, key models).",
                    disk?.path ?: GALLERY_DISK_FOLDER
                )
            ),
            p(
                page.i18n(
                    "Sharing: copy the folder %0 to another computer (or into the working folder of another browser) and its models and pictures are there; the same folder can also be imported with Import my gallery.",
                    GALLERY_DISK_FOLDER
                )
            ),
            p(
                page.i18n(
                    "In the browser: without a working folder, or without its permission, the gallery stays in the IndexedDB of this browser, as before. The first time a folder is available, the gallery of the browser is copied into it once; the browser copy is never emptied."
                )
            ),
            p(
                page.i18n(
                    "Two tabs: the file is read again before every write and merged entry by entry, so the models added in the other tab are kept; the last write wins for the same model. Deleting an entry deletes only its own picture file, after confirmation."
                )
            )
        )
        page.modalDialog(
            page.i18n("Where is my gallery?"),
            body,
            Button(page.i18n("Close"), color = ElementColor.Primary) {}
        )
    }

    // ------------------------------------------------------------------- cards

    private fun render() {
        val entries = GalleryStore.merge(indexEntries, overlay)
        if (entries.isEmpty()) {
            status.hidden = false
            status.text = if (indexFailed) page.i18n("The gallery index could not be loaded.")
            else page.i18n("No model in the gallery yet.")
            grid.body = emptyList()
            return
        }
        status.hidden = true
        grid.body = listOf(
            Columns(
                *entries.map { card(it) }.toTypedArray(),
                multiline = true
            )
        )
    }

    /** A card: image with the title on a translucent band, description, edit and delete buttons. */
    private fun card(entry: GalleryEntry): Column {
        // a bare file name means a picture of the folder that could not be read: showing it as a URL
        // would only paint a broken image, and the model is still openable
        val picture = entry.image.isNotBlank() && !isGalleryImageFileName(entry.image)
        val media: BulmaElement = if (picture) {
            Image(entry.image).apply { root.classList.add("centy-gallery-image") }
        } else {
            Div(classes = "centy-gallery-image centy-gallery-noimage")
        }
        val caption = Div(p(entry.title, classes = "centy-gallery-title"), classes = "centy-gallery-caption")
        val head = Div(media, caption, classes = "centy-gallery-media")
        val text = Div(p(entry.description, classes = "centy-gallery-description"), classes = "centy-gallery-text")
        val edit = iconButton(Icon("pen"), ElementColor.Info, rounded = true) { editEntry(entry) }
        val remove = iconButton(Icon("trash"), ElementColor.Danger, rounded = true) { deleteEntry(entry) }
        edit.root.setAttribute("title", page.i18n("Edit"))
        remove.root.setAttribute("title", page.i18n("Delete"))
        val actions = Div(edit, remove, classes = "centy-gallery-actions")

        val cardRoot = Div(head, text, actions, classes = "card centy-gallery-card")
        cardRoot.root.addEventListener("click", { confirmOpen(entry) })
        // the buttons must not open the model
        edit.root.addEventListener("click", { event: Event -> event.stopPropagation() })
        remove.root.addEventListener("click", { event: Event -> event.stopPropagation() })

        return Column(
            cardRoot,
            desktopSize = ColumnSize.S3,
            tabletSize = ColumnSize.S4,
            mobileSize = ColumnSize.Full
        )
    }

    // -------------------------------------------------------------------- open

    /** Asks for a confirmation before replacing the current project. */
    private fun confirmOpen(entry: GalleryEntry) {
        if (entry.model.isNullOrBlank() && entry.file.isNullOrBlank()) {
            page.error("This model cannot be opened: it has neither a file nor a model text.")
            return
        }
        page.modalDialog(
            page.i18n("Open this model in NetBioDyn?"),
            listOf(p(page.i18n("The current project will be replaced. Unsaved modifications will be lost."))),
            Button(page.i18n("OK"), color = ElementColor.Primary) { open(entry) },
            Button(page.i18n("Cancel")) {}
        )
    }

    private fun open(entry: GalleryEntry) {
        val text = entry.model
        if (!text.isNullOrBlank()) {
            loadModel(text)
            return
        }
        val file = entry.file ?: return
        val w = window.asDynamic()
        w.fetch(file).then { response: dynamic ->
            if (response.ok != true) throw IllegalStateException("HTTP ${response.status} for $file")
            response.text().then { content: dynamic -> loadModel(content as String) }
        }.catch { error: dynamic ->
            console.error("Gallery: cannot load $file", error)
            page.error("The model file could not be loaded.")
        }
    }

    private fun loadModel(text: String) {
        val project = try {
            decodeModelAndSimulation(text).migrated()
        } catch (t: Throwable) {
            console.error("Gallery: cannot decode the model", t)
            page.error("The model could not be decoded.")
            return
        }
        page.setModel(project)
        // a gallery model is not the file of the previous project: do not
        // propose to overwrite it
        page.currentFileName = null
        modal?.active = false
        page.message("Model loaded from the gallery.")
    }

    // ------------------------------------------------------------------ delete

    /** Reopens the form on an existing entry (static or local). */
    private fun editEntry(entry: GalleryEntry) {
        showForm(entry)
    }

    private fun deleteEntry(entry: GalleryEntry) {
        val isStatic = indexEntries.any { it.id == entry.id }
        val disk = location as? GalleryLocation.Disk
        val explanation = when {
            isStatic -> page.i18n("The entry is only hidden: gallery.json is never modified.")
            disk != null -> page.i18n(
                "The model and its picture (%0) are removed from %1. Only the file of this entry is deleted: the other models, their pictures and the folder are untouched.",
                galleryImageFileName(entry.id), disk.path
            )
            else -> page.i18n("The model is removed from the local gallery of this browser.")
        }
        page.modalDialog(
            page.i18n("Delete this model from the gallery?"),
            listOf(p(explanation)),
            Button(page.i18n("Delete"), color = ElementColor.Danger) {
                val entries = if (isStatic) GalleryStore.hide(overlay, entry) else GalleryStore.remove(overlay, entry.id)
                // only a personal entry has a picture of its own on the disk
                val removed = if (isStatic) emptyList() else listOf(entry)
                store(entries, removed) { saved -> if (saved) page.message("Model removed from the gallery.") }
            },
            Button(page.i18n("Cancel")) {}
        )
    }

    /**
     * Writes the personal gallery and refreshes the cards; [onStored] receives false when the write
     * was refused. On the disk the file is read again and merged by id before the write (see
     * [GalleryDiskStorage.save]); the browser keeps a copy, so losing the folder never loses the
     * gallery. [removed] carries the entries the visitor just deleted: their picture files go away
     * with them, and only them.
     */
    private fun store(
        entries: List<GalleryEntry>,
        removed: List<GalleryEntry> = emptyList(),
        onStored: (Boolean) -> Unit = {}
    ) {
        val disk = location as? GalleryLocation.Disk
        if (disk != null) {
            GalleryDiskStorage.save(entries, removed) { result ->
                when (result.outcome) {
                    DiskSaveOutcome.SAVED -> {
                        overlay = result.entries
                        render()
                        // fallback copy: best effort, never announced when it fails (the disk has the data)
                        GalleryStore.save(result.entries) { }
                        onStored(true)
                    }
                    DiskSaveOutcome.FAILED -> {
                        page.error(
                            "The gallery could not be written in %0: nothing was changed. Check the permission of the folder, or forget it to use the gallery of this browser.",
                            disk.path
                        )
                        onStored(false)
                    }
                }
            }
            return
        }
        GalleryStore.save(entries) { saved ->
            if (saved) {
                overlay = entries
                render()
                onStored(true)
            } else {
                page.error("The gallery could not be saved in the browser storage.")
                onStored(false)
            }
        }
    }

    // ------------------------------------------------------------------- export

    /** Downloads the personal gallery as a standalone JSON file, model texts included. */
    private fun exportPersonal() {
        val text = GalleryStore.exportPersonal(overlay)
        val size = text.encodeToByteArray().size
        if (size > GALLERY_EXPORT_WARNING_BYTES) {
            page.warning(
                "The export is large (%0 MB): the download may take a while.",
                size / (1024 * 1024)
            )
        }
        val blob = Blob(arrayOf<dynamic>(text), BlobPropertyBag(type = "application/json"))
        download(GALLERY_PERSONAL_FILE_NAME, blobHref(blob))
    }

    // ------------------------------------------------------------------- import

    /** The "import my gallery" control: a plain file input, restricted to JSON files. */
    private fun importControl(): FileInput {
        val input = FileInput(
            label = page.i18n("Import my gallery"), icon = "upload", color = ElementColor.Primary
        )
        val element = input.root.querySelector("input[type=file]") as? HTMLInputElement
        // the accept attribute is only a hint: the content is validated anyway
        element?.accept = ".json,application/json"
        input.onChange = { _, files: FileList? ->
            val file = files?.get(0)
            if (file != null) importPersonal(file)
            // lets the visitor choose the same file again
            element?.value = ""
        }
        return input
    }

    /**
     * Merges an exported personal gallery: an entry whose id is already known
     * is replaced, the other ones are added.
     */
    private fun importPersonal(file: File) {
        readText(file) { text ->
            val imported = GalleryStore.decodeExport(text)
            if (imported == null) {
                page.error("This file is not a gallery export: nothing was imported.")
                return@readText
            }
            // an id appearing twice in the file is imported once
            val incoming = imported.associateBy { it.id }.values.toList()
            var entries = overlay
            incoming.forEach { entry -> entries = GalleryStore.upsert(entries, entry) }
            val added = incoming.count { entry -> overlay.none { it.id == entry.id } }
            val replaced = incoming.size - added
            store(entries) { saved ->
                if (saved) page.message("Import done: %0 added, %1 replaced.", added, replaced)
            }
        }
    }

    // --------------------------------------------------------------------- form

    /**
     * Add or edit form. [existing] is the edited entry, null to add a model.
     * The model text is kept in the overlay for a new model; editing a static
     * entry only changes its title, description and image.
     */
    private fun showForm(existing: GalleryEntry?) {
        ensureStyles()
        var modelText: String? = existing?.model
        var imageText: String? = existing?.image?.takeIf { it.isNotBlank() }
        var dialog: ModalCard? = null

        val titleInput = Input(existing?.title ?: "", placeholder = page.i18n("Title"), rounded = true)
        val descriptionInput = TextArea(
            existing?.description ?: "", placeholder = page.i18n("Description (optional)"), rows = "3"
        )
        val modelInput = FileInput(label = page.i18n("Choose a model file"), icon = "upload", color = ElementColor.Primary)
        val imageInput = FileInput(label = page.i18n("Choose an image"), icon = "image", color = ElementColor.Primary)
        // the accept attribute is only a hint: the selected file is decoded anyway
        (modelInput.root.querySelector("input[type=file]") as? HTMLInputElement)?.accept = ".nbd2,.netbiodyn"
        (imageInput.root.querySelector("input[type=file]") as? HTMLInputElement)?.accept = "image/*"

        val preview = Image("").apply { root.classList.add("centy-gallery-preview-image") }
        val previewBox = Div(preview, classes = "centy-gallery-preview")
        val hint = p(classes = "help")
        val errorLine = p(classes = "help has-text-danger")
        previewBox.hidden = true
        hint.hidden = true

        fun showImage(dataUrl: String?) {
            if (dataUrl.isNullOrBlank()) {
                previewBox.hidden = true
            } else {
                preview.src = dataUrl
                previewBox.hidden = false
            }
        }
        showImage(imageText)

        modelInput.onChange = { _, files: FileList? ->
            val file = files?.get(0)
            if (file != null) readText(file) { text ->
                when {
                    text.encodeToByteArray().size > MAX_GALLERY_MODEL_BYTES -> {
                        errorLine.text = page.i18n("The model file is too large (over 2 MB).")
                    }
                    else -> {
                        val project = try {
                            decodeModelAndSimulation(text).migrated()
                        } catch (t: Throwable) {
                            console.warn("Gallery: cannot decode the selected file", t)
                            null
                        }
                        if (project == null) {
                            errorLine.text = page.i18n("The model could not be decoded.")
                        } else {
                            errorLine.text = ""
                            modelText = text
                            // the model name is the natural title of the card
                            if (project.model.name.isNotBlank()) titleInput.value = project.model.name
                            // the model description is imported too, and stays editable
                            if (descriptionInput.value.isBlank() && project.model.description.isNotBlank()) {
                                descriptionInput.value = project.model.description
                            }
                            if (imageText.isNullOrBlank()) {
                                val thumbnail = GalleryThumbnails.fromInitialState(project)
                                if (thumbnail != null) {
                                    imageText = thumbnail
                                    showImage(thumbnail)
                                    hint.text = page.i18n("Thumbnail generated from the initial state.")
                                    hint.hidden = false
                                }
                            }
                        }
                    }
                }
            }
        }

        imageInput.onChange = { _, files: FileList? ->
            val file = files?.get(0)
            if (file != null) readDataUrl(file) { dataUrl ->
                if (dataUrl.isNotBlank()) {
                    imageText = dataUrl
                    showImage(dataUrl)
                }
            }
        }

        fun submit() {
            val title = titleInput.value.trim()
            when {
                title.isBlank() -> {
                    errorLine.text = page.i18n("A title is required.")
                }
                modelText.isNullOrBlank() && existing == null -> {
                    errorLine.text = page.i18n("Choose a model file.")
                }
                else -> {
                    val entry = GalleryEntry(
                        id = existing?.id ?: "local-${Date.now()}",
                        title = title,
                        description = descriptionInput.value.trim(),
                        image = imageText ?: "",
                        file = existing?.file,
                        model = modelText,
                        // the date of the entry is written on the disk; an edited entry keeps its own
                        date = existing?.date?.takeIf { it.isNotBlank() } ?: Date(Date.now()).toISOString(),
                    )
                    store(GalleryStore.upsert(overlay, entry)) { saved ->
                        if (saved) {
                            dialog?.active = false
                            page.message(
                                if (existing == null) "Model added to the gallery."
                                else "Model updated in the gallery."
                            )
                        } else {
                            errorLine.text = page.i18n("The gallery could not be saved in the browser storage.")
                        }
                    }
                }
            }
        }

        val body = listOf<BulmaElement>(
            Field(Label(page.i18n("Model file (.nbd2 or .netbiodyn)")), Control(modelInput)),
            Field(Label(page.i18n("Title")), Control(titleInput)),
            Field(Label(page.i18n("Description (optional)")), Control(descriptionInput)),
            Field(Label(page.i18n("Image (optional)")), Control(imageInput)),
            previewBox,
            hint,
            errorLine
        )
        val cancel = Button(page.i18n("Cancel")) { dialog?.active = false }
        val save = Button(page.i18n("Save"), color = ElementColor.Primary) { submit() }

        dialog = showModal(
            if (existing == null) page.i18n("Add a Model") else page.i18n("Edit the Model"),
            body,
            listOf(cancel, save)
        )
        dialog.root.classList.add("centy-gallery-form")
    }

    // ------------------------------------------------------------------ files

    private fun readText(file: File, onText: (String) -> Unit) {
        val reader = FileReader()
        reader.onload = {
            when (val result = reader.result) {
                is String -> onText(result)
                else -> page.error("The selected file could not be read.")
            }
        }
        reader.onerror = {
            page.error("The selected file could not be read.")
        }
        reader.readAsText(file)
    }

    private fun readDataUrl(file: File, onData: (String) -> Unit) {
        val reader = FileReader()
        reader.onload = {
            when (val result = reader.result) {
                is String -> shrinkImage(result, onData)
                else -> page.error("The selected file could not be read.")
            }
        }
        reader.onerror = {
            page.error("The selected file could not be read.")
        }
        reader.readAsDataURL(file)
    }

    /**
     * Keeps small images as they are and resizes the big ones: a card does not
     * need a full resolution photo.
     */
    private fun shrinkImage(dataUrl: String, onData: (String) -> Unit) {
        if (dataUrl.length <= MAX_INLINE_IMAGE_CHARS) {
            onData(dataUrl)
            return
        }
        val image = document.createElement("img") as HTMLImageElement
        image.onload = {
            val width = image.naturalWidth
            val height = image.naturalHeight
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            val scale = if (width > 0 && height > 0) min(1.0, IMAGE_MAX_SIZE / max(width, height).toDouble()) else 1.0
            canvas.width = max(1, (width * scale).toInt())
            canvas.height = max(1, (height * scale).toInt())
            val context = canvas.getContext("2d") as? org.w3c.dom.CanvasRenderingContext2D
            if (context == null) {
                onData(dataUrl)
            } else {
                context.drawImage(image, 0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
                onData(canvas.toDataURL("image/jpeg", 0.82))
            }
        }
        // onerror of an image takes the (event, source, line, column, error) signature:
        // the addEventListener form keeps the handler simple
        image.addEventListener("error", { onData(dataUrl) })
        image.src = dataUrl
    }

    // ------------------------------------------------------------------- style

    /**
     * Styles of the gallery: a full screen modal card, a grid of cards and the
     * translucent band over the images. Injected once, so the gallery does not
     * depend on an extra CSS file of the web root.
     */
    private fun ensureStyles() {
        if (document.getElementById("centy-gallery-styles") != null) return
        val style = document.createElement("style") as HTMLStyleElement
        style.id = "centy-gallery-styles"
        style.textContent = """
            .centy-gallery-modal .modal-card {
                width: 96vw;
                max-width: 1600px;
                height: 92vh;
                margin: auto;
            }
            .centy-gallery-modal .modal-card-body {
                overflow-y: auto;
                background-color: #fafafa;
            }
            .centy-gallery-card {
                height: 100%;
                display: flex;
                flex-direction: column;
                cursor: pointer;
                transition: box-shadow 0.15s ease, transform 0.15s ease;
            }
            .centy-gallery-card:hover {
                box-shadow: 0 0.8em 1.4em -0.4em rgba(10, 10, 10, 0.35);
                transform: translateY(-2px);
            }
            .centy-gallery-media {
                position: relative;
                overflow: hidden;
                background-color: #e8e8e8;
            }
            .centy-gallery-image img,
            .centy-gallery-noimage {
                display: block;
                width: 100%;
                aspect-ratio: 4 / 3;
                object-fit: cover;
                background-color: #e8e8e8;
            }
            .centy-gallery-caption {
                position: absolute;
                left: 0;
                right: 0;
                bottom: 0;
                padding: 0.6rem 0.75rem 0.5rem 0.75rem;
                background-image: linear-gradient(to top, rgba(0, 0, 0, 0.78), rgba(0, 0, 0, 0));
            }
            .centy-gallery-title {
                color: #fff;
                font-weight: 600;
                margin: 0;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }
            .centy-gallery-text {
                flex: 1;
                padding: 0.7rem 0.85rem;
            }
            .centy-gallery-description {
                font-size: 0.85rem;
                color: #4a4a4a;
            }
            .centy-gallery-description:empty {
                display: none;
            }
            .centy-gallery-actions {
                display: flex;
                border-top: 1px solid #ededed;
            }
            .centy-gallery-actions .button {
                flex: 1;
                border: 0;
                border-radius: 0;
            }
            .centy-gallery-actions .button:hover {
                filter: brightness(0.95);
            }
            .centy-gallery-status {
                margin-bottom: 1rem;
            }
            .centy-gallery-location {
                margin-bottom: 0.9rem;
                font-size: 0.8rem;
                font-weight: 600;
                color: #4a4a4a;
            }
            .centy-gallery-location-hint {
                margin-top: -0.5rem;
                margin-bottom: 0.9rem;
                font-size: 0.8rem;
                color: #b8860b;
            }
            .centy-gallery-preview {
                margin-bottom: 1rem;
            }
            .centy-gallery-preview-image img {
                max-height: 140px;
                width: auto;
            }
        """.trimIndent()
        document.head?.appendChild(style)
    }
}
