package com.centyllion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.QuestionCircle

/**
 * A small "?" button that opens a popup with local help.
 * The popup is dismissed by clicking outside or on the close button.
 */
@Composable
fun HelpPopup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(22.dp)
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.QuestionCircle,
                contentDescription = "Help",
                modifier = Modifier.size(16.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 420.dp)
                    .width(IntrinsicSize.Min)
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

/** Short help content for a field formula. */
@Composable
fun FieldFormulaHelp() {
    HelpText("Évaluée à chaque pas, pour chaque case, à partir de la valeur actuelle.")
    HelpCode("current * 0.9")
    HelpText("Variables : current, step, x, y, z.")
}

/** Short help content for a behaviour formula. */
@Composable
fun BehaviourFormulaHelp() {
    HelpText("Évaluée pour chaque agent susceptible d'appliquer le comportement.")
    HelpCode("step >= 10 && count(0) >= 3")
    HelpText("Variables : step (âge), x, y, z. Fonction : count(grainId).")
}

@Composable
private fun HelpText(text: String) {
    Text(text, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable
private fun HelpCode(code: String) {
    Text(
        code,
        fontSize = 12.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
    )
}
