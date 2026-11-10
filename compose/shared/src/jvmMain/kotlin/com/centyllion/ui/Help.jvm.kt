package com.centyllion.ui

import com.centyllion.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.awt.Desktop
import java.nio.file.Files

@OptIn(ExperimentalResourceApi::class)
actual suspend fun openHelpPage() {
    val html = Res.readBytes("files/help.html").decodeToString()
    val temp = Files.createTempFile("centyllion-help", ".html").toFile()
    temp.writeText(html)
    temp.deleteOnExit()

    val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
        desktop.browse(temp.toURI())
    } else {
        // Headless fallback: try to open with xdg-open, otherwise print the path.
        try {
            ProcessBuilder("xdg-open", temp.absolutePath).start()
        } catch (_: Exception) {
            println("Help file: ${temp.absolutePath}")
        }
    }
}
