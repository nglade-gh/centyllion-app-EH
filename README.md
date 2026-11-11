# Centyllion

Environnement de modélisation et de simulation multi-agents (projet NetBioDyn) — grains, comportements, champs, avec génération spontanée, comptage de voisins et expressions de condition.

Ce dépôt est une **extension** du projet `centyllion-app` (ajout de la génération spontanée, des compteurs de voisins et des expressions de condition).

---

## 1. Prérequis

| Composant | Version | Usage |
|---|---|---|
| JDK | 21+ | compilation JVM, application desktop |
| Gradle | 8.11 (wrapper inclus) | build |
| Node.js | 18+ (26 testé) | build web (Kotlin/JS) |
| Kotlin | 2.1.0 | langage |

> **Note** : le module `db` (serveur REST Ktor) est **optionnel** et commenté par défaut dans `settings.gradle.kts`. Pour l'utiliser, il faut un **JDK complet** (avec `javac`) :
> ```bash
> sudo apt-get install -y openjdk-21-jdk-headless
> # puis décommenter include("db") dans settings.gradle.kts
> ```

Pour l'exécution **desktop dans un conteneur headless** :
- `xvfb` (serveur X virtuel)
- `libxtst6` (librairie X11 pour Java AWT)

Pour les **tests JS** (désactivés par défaut, nécessitent un navigateur) :
```bash
./gradlew :core:jsBrowserTest -PenableJsTests=true   # nécessite Chrome/Chromium
```

---

## 2. Installation

```bash
# Cloner / copier le projet
cd CentyllionExtend

# Compiler tout
./gradlew build
```

> **Note** : le build nécessite les dépôts GitHub (`maven.pkg.github.com/centyllion/...`) pour `bulma-kotlin` et `babylon-kotlin`. Les credentials `grpUser`/`grpToken` doivent être définis (fichier `local.properties` ou variables de projet).

---

## 3. Exécution

### 3.1 Application desktop standalone (recommandée)

```bash
# Construire la distribution autonome (JRE incluse, ~180 Mo)
./gradlew :compose:shared:createDistributable

# Lancer (avec rendu logiciel si pas de GPU)
JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE" \
  xvfb-run -a ./compose/shared/build/compose/binaries/main/app/Centyllion/bin/Centyllion
```

- Sortie : `compose/shared/build/compose/binaries/main/app/Centyllion/`
- Aucune dépendance externe sur la machine cible (JRE embarqué)

### 3.2 Site web statique

```bash
# Compiler le bundle de production
./gradlew :web:jsBrowserProductionWebpack :web:syncJs

# Servir les fichiers statiques
python3 -m http.server 8081 --directory web/webroot
# → http://localhost:8081
```

- Bundle : `web/webroot/js/centyllion/centyllion.js`
- Servable par n'importe quel serveur statique (nginx, Apache, etc.)

### 3.3 Serveur de développement web

```bash
./gradlew :web:jsBrowserDevelopmentRun
# → http://localhost:8080
```

### 3.4 Application desktop en mode développement

```bash
xvfb-run -a ./gradlew :compose:shared:run
```

---

## 4. Nouvelles fonctionnalités

### 4.1 Génération spontanée (None → Agent)

Un comportement peut maintenant créer un agent sur une case vide :

```
C0 : None -> Agent_A
Quand : #Agent_B > 3
```

- `mainReactiveId = -1` signifie "case vide"
- La case doit être vide pour que le comportement soit applicable
- La validation interdit les comportements sans effet (`None → None`)

### 4.2 Comptage de voisins (prédicats structurés)

```
C0 : Agent_A -> None
Quand : Agent_B > 3 AND Agent_C == 5
```

- `agentCountPredicates` : liste de `AgentCountPredicate(grainId, op, count)`
- Opérateurs : `=`, `!=`, `<`, `<=`, `>`, `>=`

### 4.3 Expressions de condition

Expressions logiques avec parenthèses et opérateurs :

```
count(1) > 2 && (count(2) == 1 || count(3) == 0)
```

- `count(grainId)` : nombre de voisins du type donné (les 8 cases adjacentes)
- Variables : `step` (âge de l'agent), `x`, `y`, `z` (position)
- Opérateurs : `&&`, `||`, `!`, comparaisons, parenthèses
- La formule doit être un **prédicat logique** (renvoyer `true`/`false`) : `sin(step)` est refusé par la validation
- `field(id)` / `field(id, dx, dy)` (case courante ou offset) et `agent(x, y)` sont disponibles dans les formules de comportement
- Validation automatique dans l'interface et dans le diagnostic du modèle
- Dans l'interface, la ligne de formule est libellée **Predicate**

### 4.4 Mise à jour parallèle ou séquentielle

Chaque simulation peut choisir son mode de mise à jour :

- **Parallèle** (défaut) : tous les comportements sont calculés à partir de l'état au
  début du pas, puis appliqués simultanément. C'est le mode adapté aux automates
  cellulaires : le comptage des voisins ne dépend pas de l'ordre de mise à jour.
- **Séquentiel** : les comportements sont appliqués immédiatement, dans un ordre
  aléatoire. Un comportement peut donc voir les changements des comportements
  précédents.

Le basculement se fait par un bouton dans la barre d'outils (icône « couches » pour
le mode parallèle, « liste » pour le mode séquentiel). Le changement réinitialise
la simulation.

L'implémentation utilise un double tampon : les lectures se font sur l'état courant,
les écritures sur une copie, qui devient l'état courant à la fin du pas.

**Parallélisation CPU** : quand le mode parallèle est actif et que la grille fait au
moins 100×100, le calcul du pas est réparti sur plusieurs threads (80 % des
processeurs disponibles). Chaque bloc de cellules possède ses propres accumulateurs,
qui sont fusionnés à la fin du pas. L'exécution est séquentielle sur Kotlin/JS
(mono-thread) et parallèle sur JVM.

**Champ `updatePlan`** : le plan de mise à jour est stocké dans le modèle, sous forme
d'un champ optionnel `updatePlan` (après `description`, avant `grains`) :

```json
{
  "name": "Fourmis",
  "description": "Agent bleus et rouges",
  "updatePlan": "Sequential",
  "grains": [ ... ]
}
```

- Valeurs : `"Parallel"` (défaut) ou `"Sequential"`.
- Le champ n'est écrits que lorsque le plan est `"Sequential"`.
- Au chargement, `ModelAndSimulation.applyUpdatePlan()` applique le plan à la simulation.

### 4.5 Actions de comportement (`actions`)

Un comportement peut définir des actions exécutées **après** les réactions, dans la
ligne **Actions** (après « Reactions », avant les effets de champs) :

```
Add(0, 1, 0), Remove(1, 0, 0), Move(0, 0, 0, 1, 0), SetField(2, 0, 0, 0.5)
```

- `Add(id, dx, dy)` : crée un agent de type `id` à l'offset (dx, dy) (case cible vide uniquement)
- `Remove(id, dx, dy)` : détruit l'agent de type `id` à l'offset (dx, dy)
- `Move(id, dx1, dy1, dx2, dy2)` : déplace l'agent de type `id` de (dx1, dy1) à (dx2, dy2)
  (cible vide, ou même type : échange de cases)
- `SetField(id, dx, dy, valeur)` : fixe la valeur du champ `id` (bornée [0, 1])
- `AddField(id, dx, dy, delta)` : ajoute `delta` à la valeur du champ `id` (résultat borné [0, 1])
- `ClearField(id, dx, dy)` : remet la valeur du champ `id` à 0
- Les offsets sont relatifs à la case du comportement et **toriques** (modulo, comme `moveIndex`)
- Les arguments sont des expressions numériques (`step`, `x`, `y`, `field(id, dx, dy)`, fonctions mathématiques)
- Validation automatique dans l'interface (champ **Actions** + aide) et dans le diagnostic du modèle

### 4.6 Visibilité des agents et des champs

Dans le panneau latéral (liste des agents, comportements et champs), chaque grain et
chaque champ possède une **case à cocher** (cochée par défaut) à gauche de sa couleur :
- décocher masque l'affichage de ce type d'agent ou de ce champ dans la simulation
  (desktop et web) ;
- la visibilité est session-only (non persistée dans le modèle).

### 4.7 Exemples complets

**Prédation** (Agent_A mangé par Agent_B voisin) :
```
C0 : Agent_A -> None
Cij : Agent_B -> Agent_B
```

**Génération catalysée** (Agent_A apparaît si 2 Agent_B voisins) :
```
C0 : None -> Agent_A
Quand : count(Agent_B) > 1
```

**Échange de position** :
```
C0 : Agent_A -> Agent_B
Cij : Agent_B -> Agent_A
```

---

## 5. Structure du projet

```
CentyllionExtend/
├── core/           # Modèle, simulation, expressions (Kotlin Multiplatform)
├── compose/
│   └── shared/     # Interface Compose Multiplatform (desktop)
├── web/            # Interface navigateur (Kotlin/JS + Babylon.js)
├── db/             # Serveur Ktor REST + SQLDelight (optionnel)
├── gradle/         # Configuration Gradle
├── gradlew         # Wrapper Gradle 8.11
└── web/webroot/    # Ressources web statiques (index.html, css)
```

### Modules

| Module | Cible | Usage |
|---|---|---|
| `core` | jvm, js, wasmJs | Logique métier, simulation, expressions |
| `compose:shared` | jvm, wasmJs | Interface bureau (Compose) |
| `web` | js | Interface navigateur (Bulma + Babylon.js) |
| `db` | jvm | Serveur REST (optionnel) |

---

## 6. Tests

```bash
# Tests JVM (modèle, simulation, expressions)
./gradlew :core:jvmTest

# Tests JS (nécessite Chrome/Chromium — désactivés par défaut)
./gradlew :core:jsBrowserTest -PenableJsTests=true

# Build complet sans tests JS
./gradlew build
```

Tests couvrant :
- Génération spontanée (`testGenerationBehaviour`)
- Comptage de voisins (`testAgentCountPredicate`)
- Expressions de condition (`testConditionExpression`, `testConditionExpressionFalse`)
- Mise à jour parallèle vs séquentielle (`testParallelUpdate`)
- Validation des formules (`FormulaTest`)

---

## 7. Documentation détaillée

- [`PROJECT_DOCUMENTATION.md`](PROJECT_DOCUMENTATION.md) — documentation complète du projet (architecture, modèle, simulateur, UI)
- [`README_Centyllion_Structure.md`](README_Centyllion_Structure.md) — structure des classes et diagramme UML

---

## 8. Références

- Projet original : [github.com/jeancharles-roger/centyllion-app](https://github.com/jeancharles-roger/centyllion-app)
- Simulateur en ligne : [www.centyllion.com](https://www.centyllion.com/)
- Site de référence : [virtulab.univ-brest.fr/netbiodyn.html](https://virtulab.univ-brest.fr/netbiodyn.html)
