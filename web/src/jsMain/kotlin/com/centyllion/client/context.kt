package com.centyllion.client

import bulma.ElementColor
import com.centyllion.i18n.Locale

data class ClientEvent(
    val date: String,
    val context: String,
    val color: ElementColor
)

interface AppContext {
    val locale: Locale
    val api: Api

    /** Ids of grain types hidden in the simulation view (visibility toggle in the side panel). */
    val hiddenGrainIds: MutableSet<Int>

    /** Ids of fields hidden in the simulation view (visibility toggle in the side panel). */
    val hiddenFieldIds: MutableSet<Int>

    fun i18n(key: String, vararg parameters: String) = locale.i18n(key, *parameters)
}
