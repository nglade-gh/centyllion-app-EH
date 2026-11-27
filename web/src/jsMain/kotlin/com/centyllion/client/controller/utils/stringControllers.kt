package com.centyllion.client.controller.utils

import bulma.*
import com.centyllion.client.renderMarkdown
import com.centyllion.i18n.Locale
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import kotlin.properties.Delegates.observable

/**
 * Editable string controller.
 */
class EditableStringController(
    initialData: String = "", placeHolder: String = "", readOnly: Boolean = false, columns: Int? = null,
    val input: TextView = Input(
        value = initialData,
        placeholder = placeHolder,
        readonly = readOnly,
        static = true,
        columns = columns
    ),
    validateOnEnter: Boolean = true, var isValid: (value: String) -> String? = { null },
    var onUpdate: (old: String, new: String, controller: EditableStringController) -> Unit =
        { _, _, _ -> }
) : NoContextController<String, Field>() {

    override var data by observable(initialData) { _, old, new ->
        if (old != new) {
            onUpdate(old, new, this@EditableStringController)
            refresh()
        }
    }

    override var readOnly: Boolean by observable(readOnly) { _, old, new ->
        if (old != new) edit(false)
    }

    val okButton: Button = iconButton(Icon("check"), ElementColor.Success) { validate() }
    val cancelButton: Button = iconButton(Icon("times"), ElementColor.Danger, rounded = true) { cancel() }

    val okControl = Control(okButton)
    val cancelControl = Control(cancelButton)

    private var message: String? by observable(isValid(initialData)) { _, old, new ->
        if (old != new) refreshMessage()
    }

    private var autoGrowMin: Int = 0
    private var autoGrowMax: Int = 0

    val penIcon = Icon("pen")

    val inputControl = Control(this.input, expanded = true, rightIcon = if (readOnly) null else penIcon)

    fun edit(start: Boolean) {
        // don't edit if readonly
        if (start && readOnly) return

        input.static = !start
        input.readonly = !start

        container.addons = start
        if (start) {
            container.body = listOfNotNull(inputControl, okControl, cancelControl)
            inputControl.rightIcon = null
            input.root.focus()
            if (autoGrowMax > 0) growTextArea()
        } else {
            container.body = listOfNotNull(inputControl)
            inputControl.rightIcon = if (readOnly) null else penIcon
        }
    }

    override val container: Field = Field(inputControl)

    init {
        input.apply {
            root.onclick = { edit(true) }
            root.onkeyup = {
                if (!readonly) {
                    when (it.key) {
                        "Enter" -> if (validateOnEnter) validate()
                        "Esc", "Escape" -> cancel()
                    }
                }
            }
            onChange = { _, value -> message = isValid(value) }
            onFocus = { if (it) edit(true) else if (message == null) validate() else cancel() }
        }


        refreshMessage()
    }

    override fun refresh() {
        input.value = data
        refreshMessage()
        if (autoGrowMax > 0) growTextArea()
    }

    fun refreshMessage() {
        if (message != null) {
            // there is a error
            input.color = ElementColor.Danger
            input.root.classList.add(TextColor.Danger.className)
            penIcon.color = TextColor.Danger
            okButton.disabled = true
            inputControl.root.classList.add("tooltip")
            inputControl.root.classList.add("is-tooltip-active")
            inputControl.root.classList.add("is-tooltip-bottom")
            inputControl.root.classList.add("is-tooltip-danger")
            inputControl.root.setAttribute("data-tooltip", message?: "")
        } else {
            // input is valid
            input.color = ElementColor.None
            input.root.classList.remove(TextColor.Danger.className)
            penIcon.color = TextColor.None
            okButton.disabled = false
            inputControl.root.classList.remove("tooltip")
            inputControl.root.classList.remove("is-tooltip-active")
            inputControl.root.classList.remove("is-tooltip-bottom")
            inputControl.root.classList.remove("is-tooltip-danger")
            inputControl.root.removeAttribute("data-tooltip")
        }
    }

    fun validate() {
        this.data = input.value
        edit(false)
    }

    fun cancel() {
        input.value = this.data
        edit(false)
    }

    /**
     * Turns the input into a growing multi-line area: Enter inserts a line break (the default in
     * a text area), Shift+Enter validates, and the height follows the content up to [maxRows]
     * lines before a vertical scrollbar appears.
     *
     * Installed after construction rather than through a constructor parameter: a trailing lambda
     * is bound to the last parameter of the constructor, and adding a non-function parameter at
     * the end would break every existing call.
     */
    fun enableAutoGrow(minRows: Int = 3, maxRows: Int = 6) {
        autoGrowMin = minRows
        autoGrowMax = maxRows
        // a textarea defaults to 20 columns and one line: inside a horizontal field it ends up
        // as a postage stamp where a formula cannot be read
        input.root.style.width = "100%"
        input.root.style.boxSizing = "border-box"
        input.root.setAttribute("rows", "$minRows")
        input.root.addEventListener("keydown", { event ->
            val key = event.asDynamic()
            if (key.key == "Enter" && key.shiftKey == true) {
                key.preventDefault()
                validate()
            }
        })
        input.root.addEventListener("input", { growTextArea() })
        growTextArea()
    }

    /**
     * Height follows the content up to [maxRows] lines, then a vertical scrollbar appears.
     * Without a cap, a long formula would push the buttons out of the dialog.
     */
    private fun growTextArea() {
        if (autoGrowMax <= 0) return
        val element = input.root
        val style = window.getComputedStyle(element)
        val lineHeight = style.lineHeight.toDoubleOrNull() ?: 20.0
        val padding = (style.paddingTop.toDoubleOrNull() ?: 0.0) +
                (style.paddingBottom.toDoubleOrNull() ?: 0.0)
        val min = lineHeight * autoGrowMin + padding
        val max = lineHeight * autoGrowMax + padding

        element.style.height = "auto"
        val needed = element.scrollHeight.toDouble().coerceAtLeast(min)
        if (needed > max) {
            element.style.height = "${max}px"
            element.style.overflowY = "auto"
        } else {
            element.style.height = "${needed}px"
            element.style.overflowY = "hidden"
        }
    }
}

/**
 * Controller for the big texts of a model: behaviour predicate, actions, field formula,
 * modifier condition and actions. Multi-line (Enter breaks the line), Shift+Enter validates,
 * and the box grows up to [maxRows] lines before scrolling.
 */
fun formulaStringController(
    initialData: String, placeHolder: String, minRows: Int = 3, maxRows: Int = 6,
    isValid: (value: String) -> String? = { null },
    onUpdate: (old: String, new: String, controller: EditableStringController) -> Unit = { _, _, _ -> }
) = EditableStringController(
    initialData = initialData,
    placeHolder = placeHolder,
    input = TextArea(value = initialData, placeholder = placeHolder, readonly = true, static = true, rows = "1"),
    validateOnEnter = false,
    isValid = isValid,
    onUpdate = onUpdate
).apply { enableAutoGrow(minRows, maxRows) }

fun <T : Comparable<T>> isNumberIn(locale: Locale, placeholder: String, number: T?, min: T, max: T) = when {
    number == null -> locale.i18n("%0 must be a number", placeholder)
    number < min || number > max -> locale.i18n("%0 must be between %1 and %2", placeholder, min, max)
    else -> null
}

fun editableFloatController(
    locale: Locale, initialData: Float = 0f, placeHolder: String = "",
    minValue: Float = Float.NEGATIVE_INFINITY, maxValue: Float = Float.POSITIVE_INFINITY,
    onUpdate: (old: Float, new: Float, controller: EditableStringController) -> Unit = { _, _, _ -> }
) = EditableStringController(
    initialData.toString(), placeHolder, false,
    isValid = { isNumberIn(locale, placeHolder, it.toFloatOrNull(), minValue, maxValue) },
    onUpdate = { old, new, controller -> onUpdate(old.toFloat(), new.toFloat(), controller) }
)

fun editableDoubleController(
    locale: Locale, initialData: Double = 0.0, placeHolder: String = "",
    minValue: Double = Double.NEGATIVE_INFINITY, maxValue: Double = Double.POSITIVE_INFINITY,
    onUpdate: (old: Double, new: Double, controller: EditableStringController) -> Unit = { _, _, _ -> }
) = EditableStringController(
    initialData.toString(), placeHolder, false,
    isValid = { isNumberIn(locale, placeHolder, it.toDoubleOrNull(), minValue, maxValue) },
    onUpdate = { old, new, controller -> onUpdate(old.toDouble(), new.toDouble(), controller) }
)

fun editableIntController(
    locale: Locale, initialData: Int = 0, placeHolder: String = "",
    minValue: Int = Int.MIN_VALUE, maxValue: Int = Int.MAX_VALUE,
    onUpdate: (old: Int, new: Int, controller: EditableStringController) -> Unit = { _, _, _ -> }
) = EditableStringController(
    initialData.toString(), placeHolder, false,
    isValid = { isNumberIn(locale, placeHolder, it.toIntOrNull(), minValue, maxValue) },
    onUpdate = { old, new, controller -> onUpdate(old.toInt(), new.toInt(), controller) }
)

fun multiLineStringController(
    initialData: String = "", placeHolder: String = "", readOnly: Boolean = false,
    rows: Int = 4, columns: Int? = null,
    isValid: (value: String) -> String? = { null },
    onUpdate: (old: String, new: String, controller: EditableStringController) -> Unit = { _, _, _ -> }
) = EditableStringController(
    initialData, placeHolder, readOnly, columns,
    TextArea(value = initialData, placeholder = placeHolder, readonly = true, static = true, rows = "$rows"),
    validateOnEnter = false, isValid = isValid, onUpdate = onUpdate
)

class EditableMarkdownController(
    initialData: String = "", val placeHolder: String = "", readOnly: Boolean = false, rows: Int = 4,
    var onUpdate: (old: String, new: String, controller: EditableMarkdownController) -> Unit =
        { _, _, _ -> }
) : NoContextController<String, Field>() {

    override var data by observable(initialData) { _, old, new ->
        if (old != new) {
            onUpdate(old, new, this@EditableMarkdownController)
            refresh()
        }
    }

    override var readOnly: Boolean by observable(readOnly) { _, old, new ->
        if (old != new) {
            htmlControl.rightIcon = if (new) null else penIcon
            edit(false)
        }
    }

    val okButton: Button = iconButton(Icon("check"), ElementColor.Success) { validate() }
    val cancelButton: Button = iconButton(Icon("times"), ElementColor.Danger, rounded = true) { cancel() }

    val okControl = Control(okButton)
    val cancelControl = Control(cancelButton)


    val input = TextArea(value = initialData, placeholder = placeHolder, readonly = true, static = true, rows = "$rows")

    val inputControl = Control(this.input, expanded = true)

    val html = object : ControlElement {
        override val root = document.createElement("div") as HTMLDivElement

        init {
            root.innerHTML = transform(initialData)
        }
    }

    private var autoGrowMin: Int = 0
    private var autoGrowMax: Int = 0

    val penIcon = Icon("pen")
    val htmlControl = Control(html, rightIcon = if (readOnly) null else penIcon)

    override val container: Field = Field(htmlControl)

    init {
        html.root.onclick = { edit(true) }
        input.apply {
            root.onkeyup = {
                if (!readonly) {
                    when (it.key) {
                        "Esc", "Escape" -> cancel()
                    }
                }
            }
            //onChange = { _, value ->  }
            onFocus = { if (it) edit(true) else validate() }
        }

    }

    fun edit(start: Boolean) {
        // don't edit if readonly
        if (start && readOnly) return

        input.static = !start
        input.readonly = !start
        container.addons = start
        if (start) {
            input.value = this.data
            container.body = listOf(inputControl, okControl, cancelControl)
            inputControl.rightIcon = null
            input.root.focus()
        } else {
            html.root.innerHTML = transform(data)
            container.body = listOf(htmlControl)
        }
    }

    override fun refresh() {
        input.value = data
        html.root.innerHTML = transform(data)
    }

    fun validate() {
        this.data = input.value
        edit(false)
    }

    fun cancel() {
        edit(false)
    }

    fun transform(source: String) =
        if (source.isNotBlank()) renderMarkdown(source)
        else "<div class='has-text-grey-lighter'>$placeHolder</div>"
}
