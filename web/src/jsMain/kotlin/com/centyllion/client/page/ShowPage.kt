package com.centyllion.client.page

import bulma.*
import bulma.extension.Switch
import com.centyllion.client.*
import com.centyllion.client.controller.model.ModelController
import com.centyllion.client.controller.utils.EditableStringController
import com.centyllion.client.controller.utils.UndoRedoSupport
import com.centyllion.client.tutorial.ActionsTutorial
import com.centyllion.client.tutorial.BacteriasTutorial
import com.centyllion.client.tutorial.DisplayTutorial
import com.centyllion.client.tutorial.MetaActionsTutorial
import com.centyllion.client.tutorial.FieldFormulaTutorial
import com.centyllion.client.tutorial.FieldTutorial
import com.centyllion.client.tutorial.GameOfLifeTutorial
import com.centyllion.client.tutorial.Tutorial
import com.centyllion.client.tutorial.TutorialLayer
import com.centyllion.model.ModelAndSimulation
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
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

    /** Build date and time of the running bundle (`version.json`), shown in the About dialog. */
    private var versionDate: String = ""

    /** Public repository of the project, shown in the About dialog. */
    private val repositoryUrl = "https://github.com/nglade-gh/centyllion-app-EH"

    /** Code name of this distribution, shown in the About dialog. */
    private val distributionName = "NetBioDyn Forever"

    private val authors = "Pascal Ballet, Jean-Charles Roger, Nicolas Glade"

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
            modelDescription.data = new.model.description

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

    init {
        // the model may refuse to leave the normal mode (graphical predicates): then the
        // switch must come back to its previous position, and the page owns its state
        modelController.onExpertModeRefused = {
            if (expertMode) expertMode = false
        }
    }

    val simulationController get() = modelController.simulationController

    val modelName = EditableStringController(
        initialData = model.model.name,
        placeHolder = i18n("Model Name"),
    ) { old: String, new: String, controller: EditableStringController ->
        model = model.updateModel(model.model.copy(name = new ) )
    }

    /** Description of the model: saved in the file, empty by default. */
    val modelDescription = EditableStringController(
        initialData = model.model.description,
        placeHolder = i18n("Model Description"),
    ) { old: String, new: String, controller: EditableStringController ->
        model = model.updateModel(model.model.copy(description = new))
    }


    val newControl = Control(Button(
        i18n("New Model"), Icon("plus"), color = ElementColor.Primary
    ) { new() })

    val fileInput = FileInput(label = i18n("Import"), color = ElementColor.Primary, onChange = ::import)

    // no quick-save button: "Save As" covers it, and the file is now nbd2.

    val saveAsControl = Control(Button(
        i18n("Save As"), Icon("download"), color = ElementColor.Primary
    ) { saveAs() })

    val tutorialControl = Control(Button(
        i18n("Tutorial"), Icon("question-circle"), color = ElementColor.Primary
    ) { startTutorial() })

    val helpControl = Control(Button(
        i18n("Help"), Icon("info-circle"), color = ElementColor.Primary
    ) { window.open("help.html", "_blank") })

    val aboutControl = Control(Button(
        i18n("About"), Icon("info"), color = ElementColor.Primary
    ) { showAbout() })

    val expertModeSwitch = Switch(
        text = i18n("Expert Mode"),
        color =  ElementColor.Primary,
        rounded = true,
        checked = expertMode
    ) { _, value -> expertMode = value }

    val tools = BField(
        modelName.container,
        modelDescription.container,
        newControl,
        Control(fileInput),
        saveAsControl,
        tutorialControl,
        helpControl,
        aboutControl,
        Control(undoRedo.undoButton), Control(undoRedo.redoButton),
        // TODO hide tutorial for now tutorialControl,
        grouped = true, groupedMultiline = true
    )

    val container: BulmaElement = Div(
        Columns(
            Column(
                Level(
                    left = listOf(
                        Label("NetBioDyn", size = Size.Large)
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

    /** Shows the About dialog: distribution name, build date and time, authors and repository. */
    fun showAbout() {
        fun row(name: String, value: BulmaElement) = TableRow(
            TableCell(i18n(name)).apply { root.classList.add("has-text-weight-bold") },
            TableCell(body = arrayOf(value))
        )

        val body = Section(
            p(i18n("Multi-agent modelling and simulation environment.")),
            Table(
                body = listOf(
                    row("Name", span(distributionName)),
                    row("Version", span(versionDate.ifBlank { i18n("unknown") })),
                    row("Authors", span(authors)),
                    TableRow(
                        TableCell(i18n("Git repository")).apply { root.classList.add("has-text-weight-bold") },
                        TableCell(body = arrayOf(
                            span(repositoryUrl),
                            Button(i18n("Open"), size = Size.Small, color = ElementColor.Info) {
                                window.open(repositoryUrl, "_blank")
                            }
                        ))
                    )
                ),
                fullWidth = true, striped = true
            )
        )

        modalDialog(
            i18n("About NetBioDyn"),
            listOf(body),
            Button(i18n("Close"), color = ElementColor.Primary) {}
        )
    }

    private fun startTutorial(tutorial: Tutorial<ShowPage>) {
        // stops the tutorial in progress (if any) before starting a new one,
        // otherwise the previous layer stays in the page
        tutorialLayer?.stop()
        tutorialLayer = TutorialLayer(tutorial) { tutorialLayer = null }
        tutorialLayer?.start()
    }

    /**
     * Tutorial menu: the two beginner tutorials (bacterias, fields) and the
     * expert ones (Game of Life, actions, field formula). Each tutorial can also
     * be reached from the conclusion of the previous one.
     */
    fun startTutorial() {
        val beginner = i18n("Beginner")
        val expert = i18n("Expert")
        val entries: List<Pair<String, Tutorial<ShowPage>>> = listOf(
            beginner to BacteriasTutorial(this),
            beginner to FieldTutorial(this),
            expert to GameOfLifeTutorial(this),
            expert to ActionsTutorial(this),
            expert to FieldFormulaTutorial(this),
            expert to MetaActionsTutorial(this),
            beginner to DisplayTutorial(this)
        )

        // The tutorial names are long: a native <select> keeps the dialog readable
        // (the list is drawn by the browser, so it can never be clipped by the
        // modal) and the closed control stays compact.
        val labels = listOf(i18n("Choose a tutorial")) + entries.map { (level, tutorial) ->
            "$level — ${i18n(tutorial.name)}"
        }
        var dialog: ModalCard? = null
        val choice = Select(
            options = labels.map { Option(it) },
            rounded = true,
            selectedIndex = 0
        ) { _: Event, value: List<Option> ->
            val index = labels.indexOfFirst { it == value.firstOrNull()?.value }
            if (index in 1..entries.size) {
                val tutorial = entries[index - 1].second
                dialog?.active = false
                startTutorial(tutorial)
            }
        }

        dialog = modalDialog(
            i18n("Tutorials"),
            listOf(
                p(i18n("Choose a tutorial. The expert tutorials assume you already know how to create agents, behaviours and fields.")),
                p(i18n("They are also chained: the conclusion of a tutorial proposes the next one.")),
                choice
            ),
            Button(i18n("Close"), color = ElementColor.Primary) {}
        )

        // comfortable height for the dialog (the browser draws the option list
        // outside the card, so the entries are never clipped)
        dialog?.root?.querySelector(".modal-card-body")?.let { body ->
            (body as? HTMLElement)?.style?.setProperty("min-height", "200px")
        }
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
                // an old project is migrated on import, like in Endless Bloom
                setModel(netbiodynJson.decodeFromString<ModelAndSimulation>(text).migrated())
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
        // the new format is nbd2: an old name is renamed, a new one keeps its extension
        val stem = base.removeSuffix(".netbiodyn").removeSuffix(".nbd2")
        val fileName = "$stem.nbd2"
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
                // write error in the native picker: plain download
                download(fileName, stringHref(content))
                after()
            },
            onUnavailable = {
                // no native dialog (Firefox, or a non-secure page): try the
                // local save bridge, which opens the OS dialog in every browser
                tryBridgeSave(fileName, content) { savedPath ->
                    if (savedPath != null) {
                        currentFileName = savedPath.substringAfterLast('/').substringAfterLast('\\')
                        after()
                        notification(i18n("Save As: saved") + " " + savedPath, ElementColor.Success, 4000)
                    } else {
                        // bridge not running (or dialog cancelled): plain download
                        download(fileName, stringHref(content))
                        after()
                        notification(i18n("Save As: bridge unavailable"), ElementColor.Warning, 8000)
                    }
                }
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
            versionDate = it.date
        }

        // restore the file handle of the last saved file (quick save across restarts)
        restoreFileHandle("save").then { handle: dynamic -> saveHandle = handle }
    }
}
