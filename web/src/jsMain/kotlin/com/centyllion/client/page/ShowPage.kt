package com.centyllion.client.page

import bulma.*
import bulma.extension.Switch
import com.centyllion.client.*
import com.centyllion.client.controller.model.ModelController
import com.centyllion.client.controller.utils.EditableStringController
import com.centyllion.client.controller.utils.UndoRedoSupport
import com.centyllion.client.tutorial.BacteriasTutorial
import com.centyllion.client.tutorial.TutorialLayer
import com.centyllion.model.ModelAndSimulation
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.url.URLSearchParams
import org.w3c.files.FileList
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.js.Promise
import kotlin.properties.Delegates.observable
import bulma.Field as BField

/** Local storage key holding the file name used as the default for saves. */
private const val FILE_NAME_KEY = "saveFileName"

/** ShowPage is used to present and edit (if not read-only) a model and a simulation. */
class ShowPage(override val appContext: AppContext) : BulmaPage {

    private var tutorialLayer: TutorialLayer<ShowPage>? = null

    /** File name (with extension) proposed as the default for the next file save.
     *  Set by the latest file import or file save, and persisted so the same
     *  project keeps its file name across page reloads. */
    var currentFileName: String?
        get() = window.localStorage.getItem(FILE_NAME_KEY)
        set(value) {
            if (value == null) window.localStorage.removeItem(FILE_NAME_KEY)
            else window.localStorage.setItem(FILE_NAME_KEY, value)
        }

    /** File handle of the last saved file, used by the quick save. Restored
     *  from IndexedDB on start (same browser profile, even after a restart). */
    private var saveHandle: dynamic = null

    var model: ModelAndSimulation by observable(ModelAndSimulation.empty) { _, old, new ->
        if (new != old) {
            undoRedo.update(old, new)
            modelController.data = new
            modelName.data = new.model.name

            expertMode = expertMode || model.expert

            refreshButtons()
            saveToStorage(model)
        }
    }

    var expertMode: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            expertModeSwitch.checked = new
            modelController.expertMode = new
        }
        expertModeSwitch.disabled = model.expert
    }

    private val undoRedo = UndoRedoSupport(model) { model = it }

    val modelController = ModelController(this, model) { _, new, _ -> model = new }

    val simulationController get() = modelController.simulationController

    val modelName = EditableStringController(
        initialData = model.model.name,
        placeHolder = i18n("Model Name"),
    ) { old: String, new: String, controller: EditableStringController ->
        model = model.updateModel(model.model.copy(name = new ) )
    }

    val newControl = Control(Button(
        i18n("New Model"), Icon("plus"), color = ElementColor.Primary
    ) { new() })

    val fileInput = FileInput(label = i18n("Import"), color = ElementColor.Primary, onChange = ::import)

    val saveControl = Control(Button(
        i18n("Export"), Icon("save"), color = ElementColor.Primary
    ) { saveQuick() })

    val saveAsControl = Control(Button(
        i18n("Save As"), Icon("download"), color = ElementColor.Primary
    ) { saveAs() })

    val tutorialControl = Control(Button(
        i18n("Tutorial"), Icon("question-circle"), color = ElementColor.Primary
    ) { startTutorial() })

    val helpControl = Control(Button(
        i18n("Help"), Icon("info-circle"), color = ElementColor.Primary
    ) { window.open("help.html", "_blank") })

    val expertModeSwitch = Switch(
        text = i18n("Expert Mode"),
        color =  ElementColor.Primary,
        rounded = true,
        checked = expertMode
    ) { _, value -> expertMode = value }

    val tools = BField(
        modelName.container,
        newControl,
        Control(fileInput),
        saveControl,
        saveAsControl,
        tutorialControl,
        helpControl,
        Control(undoRedo.undoButton), Control(undoRedo.redoButton),
        // TODO hide tutorial for now tutorialControl,
        grouped = true, groupedMultiline = true
    )

    private val currentVersion = p("")

    val container: BulmaElement = Div(
        Columns(
            Column(
                Level(
                    left = listOf(
                        Label("NetBioDyn", size = Size.Large),
                        currentVersion
                    ),
                    center = listOf(tools),
                    right = listOf(expertModeSwitch)
                ),
                size = ColumnSize.Full
            ),
            Column(modelController, size = ColumnSize.Full),
            multiline = true, centered = true
        )
    )

    override val root: HTMLElement = container.root

    fun startTutorial() {
        // Activates tutorial
        tutorialLayer = TutorialLayer(BacteriasTutorial(this)) { tutorialLayer = null }
        tutorialLayer?.start()
    }

    fun setModel(model: ModelAndSimulation) {
        undoRedo.reset(model)
        this.model = model
        refreshButtons()
    }

    fun new() = changeModelOrSimulation {accepted ->
        if (accepted) {
            setModel(ModelAndSimulation.empty)
            currentFileName = null
            saveHandle = null
            message("New model.")
        }
    }

    fun import(input: FileInput, files: FileList?) {
        val selectedFile = files?.get(0)
        if (selectedFile != null) {
            val reader = FileReader()
            reader.onload = {
                val text: String = reader.result as String
                // the imported file name becomes the default save name for this project
                currentFileName = selectedFile.name
                // a loaded project must not be quick-saved into the previous file
                saveHandle = null
                setModel(netbiodynJson.decodeFromString(text))
            }
            reader.readAsText(selectedFile)
        }
    }

    val needsSaving get() = undoRedo.changed(model)

    /**
     * Quick save: rewrites the file of the current project without dialog
     * when the write permission is already granted (the browser may show a
     * short permission prompt otherwise). Falls back to [saveAs] when the
     * project has no file yet or the permission is denied.
     */
    fun saveQuick(after: () -> Unit = {}) {
        val content = netbiodynJson.encodeToString(ModelAndSimulation.serializer(), model)
        val handle = saveHandle
        if (handle == null) {
            saveAs(after)
            return
        }
        quickSaveFile(
            handle, content,
            onSaved = { name ->
                currentFileName = name
                after()
            },
            onDenied = { saveAs(after) }
        )
    }

    /**
     * Save as: opens the native save dialog, with the current file name of
     * the project as the suggested name.
     */
    fun saveAs(after: () -> Unit = {}) {
        val content = netbiodynJson.encodeToString(ModelAndSimulation.serializer(), model)
        val base = currentFileName ?: if (model.model.name.isNotBlank()) model.model.name else "model"
        val fileName = if (base.endsWith(".netbiodyn")) base else "$base.netbiodyn"
        saveFilePicker(
            suggestedName = fileName,
            content = content,
            onSaved = { name, handle ->
                currentFileName = name
                saveHandle = handle
                rememberFileHandle("save", handle)
                after()
            },
            onFallback = {
                // browsers without the File System Access API: plain download
                download(fileName, stringHref(content))
                after()
            }
        )
    }

    fun refreshButtons() {
        undoRedo.refresh()
    }

    fun changeModelOrSimulation(dispose: Boolean = false, after: (Boolean) -> Unit = {}) {
        fun conclude(exit: Boolean) {
            if (exit && dispose) modelController.dispose()
            after(exit)
        }

        if (needsSaving) {
            modalDialog(i18n("Modifications not saved. Do you want to save ?"),
                listOf(p(i18n("You're about to quit the page and some modifications haven't been saved."))),
                textButton(i18n("Save"), ElementColor.Success) { saveQuick { conclude(true) } },
                textButton(i18n("Don't save"), ElementColor.Danger) { conclude(true) },
                textButton(i18n("Stay here")) { conclude(false) }
            )
        } else {
            conclude(true)
        }
    }

    override fun onExit() = Promise { resolve, _ ->
        changeModelOrSimulation(true) {
            if (it) tutorialLayer?.stop()
            resolve(it)
        }
    }


    init {
        // retrieves parameters
        val params = URLSearchParams(window.location.search)
        params.get("expert")?.let { expertMode = it.toBoolean()}


        val modelUrl = params.get("model")
        if (modelUrl != null) {
            appContext.api.fetch("GET", modelUrl)
                .then(
                    onFulfilled = { setModel(netbiodynJson.decodeFromString(it)) },
                    onRejected = { notification("Can't load model at $modelUrl", ElementColor.Danger) },
                )
        } else {
            val storage = loadFromStorage()
            if (storage != null) model = storage
        }

        appContext.api.fetchVersion().then {
            currentVersion.text = i18n("version of %0", it.date)
        }

        // restore the file handle of the last saved file (quick save across restarts)
        restoreFileHandle("save").then { handle: dynamic -> saveHandle = handle }
    }
}
