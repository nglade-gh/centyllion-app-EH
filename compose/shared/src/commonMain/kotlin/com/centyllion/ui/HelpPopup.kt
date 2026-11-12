package com.centyllion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small "Help" button that opens a popup with local help.
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
            modifier = Modifier.size(38.dp)
        ) {
            Text("Help", fontSize = 11.sp)
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

/** Complete help content for a behaviour actions formula. */
@Composable
fun ActionsHelp() {
    HelpText(
        "Exécutées quand le comportement est appliqué, après les réactions. " +
                "Liste d'actions séparées par des virgules."
    )
    HelpSection("Actions")
    HelpLine("Add(id, dx, dy)", "Ajoute un agent de type id à l'offset (dx, dy) (uniquement si la case cible est vide)")
    HelpLine("Remove(id, dx, dy)", "Supprime l'agent de type id à l'offset (dx, dy)")
    HelpLine("Move(id, dx1, dy1, dx2, dy2)", "Déplace l'agent de type id de l'offset (dx1, dy1) à l'offset (dx2, dy2) (case cible vide ou même type)")
    HelpLine("SetField(id, dx, dy, valeur)", "Fixe la valeur du champ id à l'offset (dx, dy) (bornée entre 0 et 1)")
    HelpLine("AddField(id, dx, dy, delta)", "Ajoute delta à la valeur du champ id à l'offset (dx, dy) (résultat borné entre 0 et 1)")
    HelpLine("ClearField(id, dx, dy)", "Remet la valeur du champ id à l'offset (dx, dy) à 0")
    HelpSection("Règles")
    HelpText(
        "Les offsets sont relatifs à la case du comportement et enroulent sur la grille (torique). " +
                "Les variables (step, x, y), count(grainId) et field(id, dx, dy) peuvent être utilisées dans les arguments."
    )
    HelpSection("Exemples")
    HelpCode("Add(0, 1, 0), Remove(1, 0, 0)")
    HelpCode("Move(0, 0, 0, 1, 0)")
    HelpCode("SetField(1, 0, 0, 1), ClearField(1, 1, 0)")
    HelpCode("Add(0, x % 2, 0), AddField(1, 0, 0, 0.1)")
    HelpSection("Ordre des réactions et des actions")
    HelpText(
        "Au sein d'un même comportement, les réactions sont appliquées d'abord, puis les actions. " +
                "Au sein des actions, l'ordre compte : les actions successives sont appliquées l'une après l'autre, " +
                "chacune voyant l'effet des précédentes. Notez que count(id) est évalué sur l'état du début du pas, " +
                "avant l'application des réactions et des actions."
    )
    HelpText("Exemple : comportement avec une case centrale vide et un voisin B à gauche en (-1,0) :")
    HelpCode("Réaction principale : None -> A ; Réaction : B -> B (B uniquement à gauche) ; Actions : Remove(0,0,0), Remove(1,-1,0), Add(0,0,0)")
    HelpText("Application (si le comportement est tiré au sort) :")
    HelpCode("1. Réaction principale : centre -> A")
    HelpCode("2. Réaction : B à (-1,0) -> B (identité)")
    HelpCode("3. Remove(0,0,0) : retire l'A du centre (celui posé par la réaction principale) -> case vide")
    HelpCode("4. Remove(1,-1,0) : retire le B de (-1,0) -> vide")
    HelpCode("5. Add(0,0,0) : pose A au centre (vide) -> A")
    HelpText(
        "Bilan net : centre = A, B à gauche supprimé. Remove(0,0,0) n'a d'effet que parce qu'il s'exécute " +
                "après la réaction principale (qui pose l'A) et avant l'Add : si l'Add avait précédé le Remove, " +
                "le centre finirait vide. Si l'ordre des réactions/actions était interverti, le résultat changerait."
    )
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
