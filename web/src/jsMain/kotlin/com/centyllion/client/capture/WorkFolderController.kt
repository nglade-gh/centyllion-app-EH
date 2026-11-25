package com.centyllion.client.capture

import bulma.*
import com.centyllion.client.page.BulmaPage
import com.centyllion.client.page.notification

/**
 * The "Working folder…" control of the top bar and its dialog.
 *
 * The button only exists where the browser offers the File System Access API: elsewhere it is
 * replaced by a short note that says the captures are downloaded, so that no visitor is offered a
 * folder they cannot get. The dialog shows the state of the folder — not defined, defined,
 * permission to renew — and offers the two gestures the browser requires: choosing a folder
 * (`showDirectoryPicker`) and giving the permission back (`requestPermission`, which only works
 * from a click).
 *
 * The state itself lives in [WorkFolder], so the run loop can save a capture without knowing this
 * controller.
 */
class WorkFolderController(private val page: BulmaPage) {

    /** "Dossier de travail…": opens the dialog. Hidden where the API is not available. */
    private val openButton = Button(
        page.i18n("Working folder…"), Icon("folder-open"), color = ElementColor.Primary
    ) { showDialog() }

    /** Replaces the button on a browser without directory access. */
    private val statusLine = p(classes = "has-text-grey is-size-7")

    /** The element to put in the toolbar of the page: the button, or the note when unsupported. */
    val control: Control = Control(openButton)

    /** The note shown instead of the button (`hidden` while the button is shown). */
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

    /** Shows the button or the note, and keeps the tooltip of the button up to date. */
    fun refresh() {
        val supported = WorkFolder.isSupported()
        openButton.hidden = !supported
        statusLine.hidden = supported
        if (supported) {
            statusLine.text = ""
            openButton.root.setAttribute("title", stateText())
        } else {
            // no button at all: the note says what happens instead
            statusLine.text = stateText()
            statusLine.root.setAttribute("title", page.i18n("Working folder"))
        }
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
