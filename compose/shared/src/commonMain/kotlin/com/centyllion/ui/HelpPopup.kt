package com.centyllion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
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
                    .widthIn(max = 460.dp)
                    .width(IntrinsicSize.Min)
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

/** Complete help content for a field formula. */
@Composable
fun FieldFormulaHelp() {
    HelpText("Évaluée à chaque pas, pour chaque case. La formule doit renvoyer un nombre.")
    HelpSection("Variables")
    HelpLine("current", "Valeur actuelle du champ (avant application de la formule)")
    HelpLine("step", "Pas de simulation courant")
    HelpLine("x, y, z", "Coordonnées de la case")
    HelpSection("Fonctions du modèle")
    HelpLine("count(grainId)", "Nombre de voisins de l'agent d'ID grainId (8 cases adjacentes)")
    HelpLine("field(id)", "Valeur du champ d'ID id sur la case courante")
    HelpLine("field(id, dx, dy)", "Valeur du champ d'ID id à l'offset (dx, dy) depuis la case courante (ex. 1, 0 = case à l'est)")
    HelpLine("agent(x, y)", "ID de l'agent à la position (x, y), -1 si la case est vide")
    HelpOperators()
    HelpSection("Exemples")
    HelpCode("current * 0.9")
    HelpCode("current + 0.01 * (field(1) - current)")
    HelpCode("count(0) > 2 ? current * 0.95 : current")
}

/** Complete help content for a behaviour formula. */
@Composable
fun BehaviourFormulaHelp() {
    HelpText(
        "Évaluée pour chaque agent susceptible d'appliquer le comportement. " +
                "La formule doit être un prédicat logique renvoyant true ou false ; " +
                "toute autre valeur (par ex. sin(step)) rend le comportement inapplicable."
    )
    HelpSection("Variables")
    HelpLine("step", "Âge de l'agent (pas courant pour la génération spontanée)")
    HelpLine("x, y, z", "Coordonnées de la case")
    HelpSection("Fonctions du modèle")
    HelpLine("count(grainId)", "Nombre de voisins de l'agent d'ID grainId (8 cases adjacentes)")
    HelpLine("field(id)", "Valeur du champ d'ID id sur la case courante")
    HelpLine("field(id, dx, dy)", "Valeur du champ d'ID id à l'offset (dx, dy) depuis la case courante (ex. 1, 0 = case à l'est)")
    HelpLine("agent(x, y)", "ID de l'agent à la position (x, y), -1 si la case est vide")
    HelpOperators()
    HelpSection("Exemples")
    HelpCode("count(0) >= 3")
    HelpCode("count(0) >= 3 && count(0) < 5")
    HelpCode("step >= 10 && count(1) == 0")
    HelpCode("x % 2 == 0")
    HelpCode("count(0) > 3 && field(1) < 0.5")
}

/** Shared operators and math functions block. */
@Composable
private fun HelpOperators() {
    HelpSection("Opérateurs")
    HelpLine("+, -, *, /", "Arithmétique")
    HelpLine("%", "Modulo (reste d'une division entière)")
    HelpLine("^", "Puissance (a^b)")
    HelpLine("==, !=", "Égalité / différence")
    HelpLine("<, >, <=, >=", "Comparaisons")
    HelpLine("&&, ||, !", "Et, ou, non logiques")
    HelpLine("a ? b : c", "Ternaire : b si a est true, sinon c")
    HelpSection("Fonctions mathématiques")
    HelpLine("pi, e", "Constantes")
    HelpLine("abs(x)", "Valeur absolue")
    HelpLine("sin, cos, tan", "Trigonométrie (asin, acos, atan)")
    HelpLine("floor, ceil, round", "Arrondis")
    HelpLine("min, max", "Min / max de n valeurs")
    HelpLine("ln(x), log(x, base)", "Logarithmes")
}

@Composable
private fun HelpSection(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun HelpLine(name: String, description: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            name,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(130.dp)
        )
        Text(description, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
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
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
    )
}
