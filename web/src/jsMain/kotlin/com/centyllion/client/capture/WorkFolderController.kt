package com.centyllion.client.capture

import bulma.*
import com.centyllion.client.page.BulmaPage
import com.centyllion.client.page.notification

/**
 * Red of the home button: the red the project already uses for its warnings
 * (`ModifierEditController`), that is Bulma's danger red. The theme of the application paints
 * [ElementColor.Danger] with a magenta (`#d24193`), too far from red for this control, so the red
 * is applied on top of the danger class — the class keeps the hover, focus and disabled rules.
 */
private const val WORK_FOLDER_RED = "#f14668"

/**
 * The home button of the top bar and the working folder dialog.
 *
 * The button sits on the left of the bar, right beside the « NetBioDyn » title: an icon-only
 * button carrying the FontAwesome `fa-home` icon, painted red ([WORK_FOLDER_RED], the danger red
 * of the project palette). It is filled when the folder is defined and its permission granted,
 * outlined and dimmed otherwise, so the state of the folder is readable at a glance. Its tooltip
 * is the label "Working folder"; a grey note next to it says in which state the folder is and
 * where the captures go.
 *
 * The button opens the dialog in every state, including a browser without the File System Access
 * API: the dialog then says that the captures are downloaded. It offers the two gestures the
 * browser requires: choosing a folder (`showDirectoryPicker`) and giving the permission back
 * (`requestPermission`, which only works from a click).
 *
 * The state itself lives in [WorkFolder], so the run loop can save a capture without knowing this
 * controller.
 */
class WorkFolderController(private val page: BulmaPage) {

    /** Home button: opens the dialog from the left of the top bar. */
    private val homeButton = iconButton(
        Icon("home"), ElementColor.Danger, outlined = true
    ) { showDialog() }.apply {
        // the icon-only button needs its own label: the tooltip and the accessible name are the
        // label of the control
        root.setAttribute("aria-label", page.i18n("Working folder"))
        root.style.marginLeft = "0.6rem"
    }

    /** Grey note beside the button: the state of the folder and where the captures go. */
    private val statusLine = p(classes = "has-text-grey is-size-7").apply {
        root.style.marginLeft = "0.75rem"
    }

    /** The element to put in the top bar, beside the title: the home button. */
    val control: Control = Control(homeButton)

    /** The note shown beside the button, in every state. */
    val note: BulmaElement get() = statusLine

    init {
        refresh()
        WorkFolder.restore { refresh() }
    }

    // ------------------------------------------------------------------ state

    /** Sentence describing where the captures go, in the current language. */
    fun stateText(): String = when (WorkFolder.state) {
        WorkFolderState.UNSUPPORTED -> page.i18n("Captures are downloaded: this browser has no access to a folder.")
        WorkFolderState.NOT_DEFINED -> page.i18n("Working folder: %0", page.i18n("not defined"))
        WorkFolderState.DEFINED -> page.i18n(
            "Working folder: %0 (%1)", WorkFolder.folderName ?: "", page.i18n("defined")
        )
        WorkFolderState.PERMISSION_TO_RENEW -> page.i18n(
            "Working folder: %0 (%1)", WorkFolder.folderName ?: "", page.i18n("permission to renew")
        )
    }

    /**
     * Sentence of the note: the state of the folder, then where the captures go — a sub-folder of
     * the folder when it is usable, a download otherwise.
     */
    fun noteText(): String = when (WorkFolder.state) {
        // already says that the captures are downloaded, and why
        WorkFolderState.UNSUPPORTED -> stateText()
        WorkFolderState.DEFINED -> stateText() + " — " + page.i18n("captures written in the folder")
        else -> stateText() + " — " + page.i18n("captures downloaded")
    }

    /** Keeps the look of the button and the note up to date with the state of the folder. */
    fun refresh() {
        val usable = WorkFolder.state == WorkFolderState.DEFINED
        // filled red when the folder is usable, red outline dimmed when it is not
        homeButton.outlined = !usable
        if (usable) {
            homeButton.root.style.backgroundColor = WORK_FOLDER_RED
            homeButton.root.style.borderColor = WORK_FOLDER_RED
            homeButton.root.style.color = "white"
            homeButton.root.style.opacity = "1"
        } else {
            homeButton.root.style.backgroundColor = "transparent"
            homeButton.root.style.borderColor = WORK_FOLDER_RED
            homeButton.root.style.color = WORK_FOLDER_RED
            homeButton.root.style.opacity = "0.6"
        }
        homeButton.root.setAttribute("title", page.i18n("Working folder"))
        statusLine.text = noteText()
    }

    // ----------------------------------------------------------------- dialog

    /** Opens the working folder dialog. */
    fun showDialog() {
        val body = mutableListOf<BulmaElement>()
        body += p(stateText()).apply { root.classList.add("has-text-weight-bold") }
        body += p(
            page.i18n(
                "A capture is a JPEG picture of the simulation view, taken at the end of the step that asks for it. " +
                    "It is written in a sub-folder of the working folder, named after the model, as %0.",
                "model__step.jpg"
            )
        )
        body += p(
            page.i18n(
                "The browser remembers only the folder itself, in its own database: no picture is stored in the " +
                    "browser, a capture goes to the folder or to a download, nowhere else."
            )
        )
        if (!WorkFolder.isSupported()) {
            body += p(
                page.i18n(
                    "This browser has no access to a folder: every capture is downloaded, one file per step. " +
                        "Chrome and Edge offer the working folder on HTTPS or on localhost; Firefox and Safari do not."
                )
            )
            page.modalDialog(
                page.i18n("Working folder"), body, Button(page.i18n("Close"), color = ElementColor.Primary) {}
            )
            return
        }
        body += p(
            page.i18n(
                "The browser may ask for the permission again after a restart: the button Reconnect gives it back " +
                    "without choosing the folder a second time. As long as it is not granted, the captures are downloaded."
            )
        )

        val buttons = mutableListOf<Button>()
        buttons += Button(page.i18n("Choose a folder…"), Icon("folder-open"), color = ElementColor.Primary) {
            chooseFolder()
        }
        if (WorkFolder.state == WorkFolderState.PERMISSION_TO_RENEW) {
            buttons += Button(page.i18n("Reconnect"), Icon("sync"), color = ElementColor.Warning) {
                reconnect()
            }
        }
        if (WorkFolder.state != WorkFolderState.NOT_DEFINED) {
            buttons += Button(page.i18n("Forget the folder"), color = ElementColor.Danger) { forgetFolder() }
        }
        buttons += Button(page.i18n("Close"), color = ElementColor.Primary) {}

        page.modalDialog(page.i18n("Working folder"), body, *buttons.toTypedArray())
    }

    // ---------------------------------------------------------------- actions

    /** Asks a folder (user gesture: the picker needs one) and reports what happened. */
    private fun chooseFolder() {
        WorkFolder.chooseFolder { choice ->
            refresh()
            when (choice) {
                FolderChoice.CHOSEN -> notification(
                    page.i18n("The folder is now the working folder: the captures are written in %0.", WorkFolder.folderName ?: ""),
                    ElementColor.Success, 7000
                )
                FolderChoice.UNSAVED -> notification(
                    page.i18n(
                        "The folder is used until the page is reloaded: this browser could not remember it, " +
                            "so the captures are written in %0 now and downloaded after a restart.",
                        WorkFolder.folderName ?: ""
                    ),
                    ElementColor.Warning, 8000
                )
                // the visitor closed the picker: nothing changed, nothing to say
                FolderChoice.CANCELLED -> {}
                FolderChoice.FAILED -> notification(
                    page.i18n("The folder could not be opened: the captures are downloaded."),
                    ElementColor.Warning, 7000
                )
            }
        }
    }

    /** Gives the read/write permission back (user gesture: requestPermission needs one). */
    private fun reconnect() {
        WorkFolder.reconnect { granted ->
            refresh()
            if (granted) {
                notification(
                    page.i18n("The permission is granted again: the captures are written in %0.", WorkFolder.folderName ?: ""),
                    ElementColor.Success, 7000
                )
            } else {
                notification(
                    page.i18n("The permission was not granted: the captures are downloaded."),
                    ElementColor.Warning, 7000
                )
            }
        }
    }

    /** Forgets the folder: the captures go back to the downloads. */
    private fun forgetFolder() {
        WorkFolder.forget()
        refresh()
        notification(
            page.i18n("The folder is forgotten: the captures are downloaded."),
            ElementColor.Info, 5000
        )
    }
}
