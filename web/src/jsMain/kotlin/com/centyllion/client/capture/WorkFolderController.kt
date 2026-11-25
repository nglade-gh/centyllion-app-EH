package com.centyllion.client.capture

import bulma.*
import com.centyllion.client.page.BulmaPage
import com.centyllion.client.page.notification
import com.centyllion.model.CAPTURE_FOLDER

/**
 * The home button of the model bar and the working folder dialog.
 *
 * The button sits in the same row as the « Model information » button, under the title bar: an
 * icon-only button carrying the FontAwesome `fa-home` icon ([IconStyle.Solid], a full house, never
 * an outline). It is always filled, and its colour tells the state of the folder:
 *
 * - **not defined** (or no directory access at all): the magenta of the danger colour of the theme,
 *   `#d24193`, which `ElementColor.Danger` already paints on a filled button;
 * - **defined and granted**: the blue of the project, `#9595f8`, the primary colour of the `+`
 *   buttons of the model (`ElementColor.Primary`);
 * - **permission to renew**: the same blue, dimmed by a reduced opacity, so a click that reconnects
 *   the folder is expected.
 *
 * The colours come from the classes of the theme (no inline colour, so the hover and focus rules
 * keep working); only the opacity of the third state is set inline. The information that the
 * removed status note used to carry is in the tooltip of the button — both `title` and
 * `aria-label`, so it is read by every visitor: « Working folder — Set », « Working folder — Not
 * set » or « Working folder — Permission to renew ». The dialog keeps the detailed explanations.
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

    /** Home button: opens the dialog, beside the model information button. */
    private val homeButton = iconButton(
        Icon("home", style = IconStyle.Solid), ElementColor.Danger, outlined = false
    ) { showDialog() }.apply {
        // the icon-only button needs its own label: the tooltip and the accessible name are the
        // label of the control
        root.setAttribute("aria-label", page.i18n("Working folder — Not set"))
        root.style.marginLeft = "0.5rem"
    }

    /** The element to put beside the model information button: the home button alone. */
    val control: Control = Control(homeButton)

    init {
        refresh()
        WorkFolder.restore { refresh() }
    }

    // ------------------------------------------------------------------ state

    /** Sentence describing where the captures go, in the current language (dialog only). */
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
     * Tooltip of the button: the state of the folder, then where the captures go. It replaces the
     * grey note that used to sit beside the button, so it is the only text shown by the bar.
     */
    fun tooltipText(): String = when (WorkFolder.state) {
        WorkFolderState.DEFINED -> page.i18n("Working folder — Set")
        WorkFolderState.PERMISSION_TO_RENEW -> page.i18n("Working folder — Permission to renew")
        else -> page.i18n("Working folder — Not set")
    }

    /** Keeps the look and the tooltip of the button up to date with the state of the folder. */
    fun refresh() {
        val state = WorkFolder.state
        val set = state == WorkFolderState.DEFINED
        val renew = state == WorkFolderState.PERMISSION_TO_RENEW
        // a full house in both states: the colour alone tells them apart
        homeButton.outlined = false
        homeButton.color = if (set || renew) {
            // the blue of the `+` buttons of the model (#9595f8)
            ElementColor.Primary
        } else {
            // the magenta of the danger colour of the theme (#d24193)
            ElementColor.Danger
        }
        // a folder whose permission must be renewed keeps the blue, but dimmed
        homeButton.root.style.opacity = if (renew) "0.55" else "1"
        val label = tooltipText()
        homeButton.root.setAttribute("title", label)
        homeButton.root.setAttribute("aria-label", label)
    }

    /**
     * Destination announced to the visitor: the working folder, then its `captures` folder, where
     * the model folders and the pictures are written.
     */
    private fun capturesDestination(): String {
        val folder = WorkFolder.folderName ?: ""
        return if (folder.isEmpty()) CAPTURE_FOLDER else "$folder/$CAPTURE_FOLDER"
    }

    // ----------------------------------------------------------------- dialog

    /** Opens the working folder dialog. */
    fun showDialog() {
        val body = mutableListOf<BulmaElement>()
        body += p(stateText()).apply { root.classList.add("has-text-weight-bold") }
        body += p(
            page.i18n(
                "A capture is a JPEG picture of the simulation view, taken at the end of the step that asks for it. " +
                    "It is written in the folder captures of the working folder, then in a sub-folder named after " +
                    "the model, as %0; both folders are created when needed.",
                "captures/GoL_Modifiers/GoL_Modifiers__12.jpg"
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
                    page.i18n("The folder is now the working folder: the captures are written in %0.", capturesDestination()),
                    ElementColor.Success, 7000
                )
                FolderChoice.UNSAVED -> notification(
                    page.i18n(
                        "The folder is used until the page is reloaded: this browser could not remember it, " +
                            "so the captures are written in %0 now and downloaded after a restart.",
                        capturesDestination()
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
                    page.i18n("The permission is granted again: the captures are written in %0.", capturesDestination()),
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
