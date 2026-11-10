package com.centyllion.ui

import com.centyllion.shared.generated.resources.Res
import kotlinx.browser.window
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.w3c.dom.Blob
import org.w3c.dom.BlobPropertyBag
import org.w3c.dom.url.URL

@OptIn(ExperimentalResourceApi::class)
actual suspend fun openHelpPage() {
    val html = Res.readBytes("files/help.html").decodeToString()
    val blob = Blob(arrayOf(html), BlobPropertyBag(type = "text/html"))
    val url = URL.createObjectURL(blob)
    window.open(url, "_blank")
}
