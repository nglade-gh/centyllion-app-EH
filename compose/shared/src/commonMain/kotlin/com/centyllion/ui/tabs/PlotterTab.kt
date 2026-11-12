package com.centyllion.ui.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.centyllion.model.Simulator
import com.centyllion.model.colorNames
import com.centyllion.ui.AppContext
import com.centyllion.ui.Combo
import com.centyllion.ui.color
import com.centyllion.ui.plotter.Plotter
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ChartLine

object PlotterTab : Tab {

    override val nameKey = "Plotters"
    override val icon = FontAwesomeIcons.Solid.ChartLine

    private val windowSizes = (10..500 step 10).toList()

    @Composable
    override fun content(app: AppContext) {
        // shared display mode for both plots below:
        // unchecked = step 0 -> current step, checked = last windowSize steps
        val windowMode = remember { mutableStateOf(false) }
        val windowSize = remember { mutableStateOf(100) }
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(checked = windowMode.value, onCheckedChange = { windowMode.value = it })
                Text(app.locale.i18n("Sliding window"))
                Text(app.locale.i18n("Window"))
                Combo(
                    windowSize.value, windowSizes, Modifier,
                    valueContent = { Text(it.toString()) }
                ) { windowSize.value = it }
            }
            grainPlot(app, Modifier.weight(1f).fillMaxWidth(), app.simulator, windowMode.value, windowSize.value)
            fieldPlot(app, Modifier.weight(1f).fillMaxWidth(), app.simulator, windowMode.value, windowSize.value)
        }
    }

    @Composable
    private fun grainPlot(appContext: AppContext, modifier: Modifier, simulator: Simulator, windowMode: Boolean, windowSize: Int) {
        val grainPlotter = remember { Plotter() }

        val visibleGrains = remember {
            mutableStateOf(
                appContext.modelAndSimulation.model.grains
                    .filter { appContext.modelAndSimulation.model.doesGrainCountCanChange(it) }
                    .map { it.id }
                    .toSet()
            )
        }

        Canvas(modifier) {
            val maxGrainCount = simulator.maxGrainCount
                .filterKeys { visibleGrains.value.contains(it) }
                .values.maxOrNull() ?: 0

            val historySize = simulator.grainCountHistory.values.firstOrNull()?.size ?: 0
            val start = if (windowMode) (historySize - windowSize).coerceAtLeast(0) else 0

            val lines = simulator.grainCountHistory
                .filter { visibleGrains.value.contains(it.key.id) }
                .map { (grain, values) ->
                    val color = colorNames[grain.color]?.color ?: Color.Red
                    val size = (values.size - start).coerceAtLeast(0)
                    Plotter.PlotLine(color, size) { values[start + it].toFloat() }
                }
            grainPlotter.plot(this, (historySize - start - 1).coerceAtLeast(1), maxGrainCount, lines)
        }
    }

    @Composable
    private fun fieldPlot(appContext: AppContext, modifier: Modifier, simulator: Simulator, windowMode: Boolean, windowSize: Int) {
        val fieldPlotter = remember { Plotter() }

        val visibleFields = remember {
            mutableStateOf(
                appContext.modelAndSimulation.model.fields.map { it.id }.toSet()
            )
        }

        Canvas(modifier) {
            val maxFieldAmount = simulator.maxFieldAmount
                .filterKeys { visibleFields.value.contains(it) }
                .values.maxOrNull() ?: 0f

            val historySize = simulator.fieldAmountHistory.values.firstOrNull()?.size ?: 0
            val start = if (windowMode) (historySize - windowSize).coerceAtLeast(0) else 0

            val lines = simulator.fieldAmountHistory
                .filter { visibleFields.value.contains(it.key.id) }
                .map { (field, values) ->
                    val color = colorNames[field.color]?.color ?: Color.Red
                    val size = (values.size - start).coerceAtLeast(0)
                    Plotter.PlotLine(color, size) { values[start + it] }
                }
            fieldPlotter.plot(this, (historySize - start - 1).coerceAtLeast(1), maxFieldAmount, lines)
        }
    }
}
