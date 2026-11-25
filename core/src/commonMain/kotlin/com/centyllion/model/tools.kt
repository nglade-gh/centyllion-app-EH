package com.centyllion.model


/**
 * Tools of the drawing bar.
 *
 * [Square] and [Circle] draw shapes anchored on the press cell: they are applied when the
 * button is released, and the "Fill ?" switch of the bar decides whether they are solid or
 * only outlined (the other tools are brushes).
 */
enum class EditTool(val icon: String) {
    Move("arrows-alt"), Pen("pen"),
    Square("square"), Circle("circle"),
    Line("pencil-ruler"), Spray("spray-can"),
    Eraser("eraser");

    fun actualSize(size: ToolSize): Int = when (this) {
        Spray -> when (size) {
            ToolSize.Fine -> 10
            else -> size.size * 4
        }
        Line -> ToolSize.Fine.size
        Square -> when (size) {
            ToolSize.Fine -> size.size
            else -> 20
        }
        else -> size.size
    }
}

@Suppress("unused")
enum class ToolSize(val size: Int) {
    Fine(1), Small(5), Medium(10), Large(20)
}
