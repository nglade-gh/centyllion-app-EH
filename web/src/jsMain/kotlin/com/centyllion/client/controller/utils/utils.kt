package com.centyllion.client.controller.utils

import bulma.*
import com.centyllion.model.ModelElement
import kotlinx.browser.document
import kotlinx.html.dom.create
import kotlinx.html.js.hr
import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <T> Array<T>.push(e: T): Int = asDynamic().push(e) as Int
fun <T> Array<T>.pop(): T = asDynamic().pop() as T

class DeleteCallbackProperty<T>(
    var callback: ((T) -> Unit)? = null,
    val controller: Controller<T, *, *>,
    val deleteSetter: (old: Delete?, new: Delete?) -> Unit
): ReadWriteProperty<Any, ((T) -> Unit)?> {

    private var delete: Delete? = null

    var readOnly by Delegates.observable(false) { _, old, new ->
        if (old != new) delete?.hidden = new
    }

    override fun getValue(thisRef: Any, property: KProperty<*>) = callback

    override fun setValue(thisRef: Any, property: KProperty<*>, value: ((T) -> Unit)?) {
        if (callback != value) {
            val oldDelete = delete
            if (value != null) {
                delete = Delete { value(controller.data) }
                delete?.hidden = controller.readOnly
            } else {
                delete = null
            }
            callback = value
            if (oldDelete != delete) deleteSetter(oldDelete, delete)
        }
    }
}

fun editorBox(title: String, icon: String?, vararg body: BulmaElement) = Div(
    Tabs(TabItem(title, icon).apply { active = true }, boxed = true).apply { root.classList.add("editor")},
    Box(*body).apply { root.classList.add("editor") }
).apply { root.style.display = "contents"}

/**
 * A collapsible titled block: clicking the header (with a chevron icon)
 * toggles the visibility of its content.
 */
fun collapsibleBlock(title: String, vararg content: BulmaElement): Div {
    val body = Div()
    content.forEach { body.root.appendChild(it.root) }
    val iconDown = Icon("chevron-down", Size.Small)
    val iconUp = Icon("chevron-up", Size.Small).apply { hidden = true }
    val header = Level(
        left = listOf(Label(title).apply { root.classList.add("has-text-weight-bold") }),
        right = listOf(iconDown, iconUp)
    )
    header.root.style.cursor = "pointer"
    header.root.onclick = {
        body.hidden = !body.hidden
        iconDown.hidden = body.hidden
        iconUp.hidden = !body.hidden
    }
    return Div(header, body)
}


fun <T: ModelElement> List<T>.filtered(filter: String) =
    if (filter.isBlank()) this
    else this.filter { it.name.contains(filter, true) || it.description.contains(filter, true) }

fun createHr() = document.create.hr()
