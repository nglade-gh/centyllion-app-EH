# Documentation de projet : Centyllion (extension CentyllionExtend)

> **Localisation :** `Prog/Simulateurs/NetBioDyn_Simulators/CentyllionExtend`  
> **Projet original :** [github.com/jeancharles-roger/centyllion-app](https://github.com/jeancharles-roger/centyllion-app)  
> **Site de référence :** [virtulab.univ-brest.fr/netbiodyn.html](https://virtulab.univ-brest.fr/netbiodyn.html)  
> **Simulateur en ligne :** [www.centyllion.com](https://www.centyllion.com/)  
> **Version de code :** `0.0.1` (root) · Kotlin `2.1.0` · Compose `1.7.1` · Gradle `8.11`

---

## 0. Nouveautés de l'extension CentyllionExtend

| Fonctionnalité | Description |
|---|---|
| **Génération spontanée** | `mainReactiveId = -1` : un comportement peut créer un agent sur une case vide (`None → Agent`) |
| **Compteurs de voisins** | `agentCountPredicates` : conditions sur le nombre de voisins d'un type (`Agent_B > 3`) |
| **Expressions de condition** | `formula` : expressions logiques avec parenthèses et opérateurs (`count(1) > 2 && count(2) == 1`) |
| **Mise à jour parallèle** | `Simulation.parallelUpdate` (défaut) : calculs depuis l'état initial du pas, adapté aux automates cellulaires |
| **Validation corrigée** | `validateFormula` vérifie maintenant les références et signale les erreurs |
| **Build standalone** | Application desktop autonome + site web statique, sans serveur |

Voir le [README.md](README.md) pour l'installation et l'exécution.

---

## 1. Aperçu

**Centyllion** est un environnement de modélisation et de simulation en **multi-agents** (celle du projet **NetBioDyn**), conçu à l'usage de l'enseignement. Il permet de créer des modèles de "grains" (agents), de "comportements" et de "champs" (milieux continus), puis de simuler leur évolution dans un plateau quadrillage défini.

L'application est accessible en ligne et s'ouvre sous deux formes :

- **Web** : navigateur (compilation Kotlin/JS, interface Bulma + data2viz + Babylon.js pour la 3D).
- **Bureau (Compose Multiplatform)** : application JavaFX/Swing (package `com.centyllion.ui.NetbiodynKt`).

Le code source est partagé entre un module `core` (logique métier, multi-platforme `jvm`, `js`, `wasmJs`) et deux modules d'interface :
- `compose:shared` → interfaces utilisateur en Compose Multiplatform (cible `jvm` pour le bureau, `wasmJs` pour le navigateur WASM, `js` pour le web classique).
- `web` → module Kotlin/JS spécifique au navigateur web.
- `db` → serveur Ktor + SQLDelight (cible JVM) qui fournit l'API REST derrière laquelle s'articule le site en ligne.

---

## 2. Architecture générale

```
┌──────────────────────────────────────────────────────────────┐
│  centyllion-app                                              │
│  ├─ core/           Kotlin Multiplatform : modèle, simulation │
│  │                 (logique métier, expression, i18n, tests)  │
│  ├─ compose:shared/ │ Compose Multiplatform : UI ui (jvm,    │
│  │                 │ wasmJs, js)                               │
│  ├─ web/            Kotlin/JS : interface navigateur bulma    │
│  │                 │ + 3D (Babylon.js, data2viz)              │
│  ├─ db/             JVM : serveur Ktor REST + SQLDelight      │
│  └─ settings.gradle.kts (4 sous-projets)                       │
└──────────────────────────────────────────────────────────────┘
```

### Modules

| Module | Type | Cibles | Principal |
|--------|------|--------|-----------|
| `:core` | kotlin-multiplatform | jvm, js, (wasm via composeShared) | Modèle, simulateur, expression, i18n |
| `:web` | kotlin-multiplatform | js | Interface web (Bulma, Babylon.js, data2viz) |
| `:compose:shared` | kotlin-multiplatform | jvm, wasmJs | Interface Compose (bureau + WASM) |
| `:db` | kotlin-multiplatform | jvm | Serveur Ktor REST + DB SQLDelight |

La racine `build.gradle.kts` configure les projets, le group `com.centyllion`, la version `0.0.1`, et la résolution des versions dans `versions.toml`.

---

## 3. Structure des fichiers

```
centyllion-app/
├── settings.gradle.kts      # 4 sous-projets : core, web, compose:shared, db
├── build.gradle.kts         # plugins, group, version
├── gradle.properties        # options Kotlin/gradle
├── versions.toml            # versions et dépendances (versionCatalogs)
├── gradlew / gradlew.bat    # wrapper Gradle (gradle-8.11)
├── README.md                # documentation du projet (très brief)
├── .gitignore
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── core/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/centyllion/
│       │   ├─ model/          # model.kt, simulator.kt, evaluator.kt, technic.kt,
│       │   │                # tools.kt, utils.kt, validation.kt, colors.kt, icons.kt
│       │   ├─ i18n/           # english.kt, french.kt, locales.kt
│       │   └─ expression/     # Lexer.kt, Parser.kt, Builder.kt, Check.kt,
│       │                       # Evaluation.kt, Ast.kt, Input.kt, Stdlib.kt
│       └── commonTest/...     # tests (CombinationTest, FormulaTest, ...)
├── web/
│   ├── build.gradle.kts
│   └── src/jsMain/kotlin/com/centyllion/client/
│       ├─ index.kt
│       ├─ Api.kt            # client HTTP ↔ API du serveur db/
│       ├─ meta.kt           # Version
│       ├─ utils.kt          # helpers DOM, markdown, téléchargement
│       ├─ context.kt        # ClientEvent, AppContext
│       ├─ PlotterController.kt
│       ├─ plotter/          # PlotterController.kt, plotter.kt
│       ├─ tutorial/         # BacteriasTutorial, FieldTutorial, TutorialLayer, ...
│       ├─ page/             # BulmaPage, ShowPage
│       └─ controller/
│           ├─ model/*.kt    # 20 contrôleurs de l'éditeur
│           └─ utils/*.kt    # SearchController, UndoRedoSupport, stringControllers
├── compose/
│   └── shared/
│       ├── build.gradle.kts
│       └── src/
│           ├── wasmJsMain/  # index.html, styles.css, ui/main.kt (WASM)
│           ├── jvmMain/     # MenuBar.kt, main.kt (bureau)
│           └── commonMain/
│               └── kotlin/com/centyllion/ui/
│                   ├── Netbiodyn.kt, AppState.kt, AppContext.kt, Toolbar.kt
│                   ├── ComponentTree.kt, rows.kt, utils.kt
│                   ├─ tabs/   # Tab.kt + 8 onglets (Model, Simulation, Environment,
│                   │         # FieldEdit, GrainEdit, BehaviourEdit, Plotter, Logs)
│                   └─ plotter/ # Plotter
├── db/
│   ├── build.gradle.kts
│   ├── docker/
│   │   ├── Dockerfile
│   │   └── README.md
│   └── src/
│       ├── main/sqldelight/com/centyllion/db/Database.sq   # schéma SQL
│       └── main/kotlin/com/centyllion/db/
│           ├── Server.kt      # routes Ktor REST
│           ├── main.kt        # entrée : centelliaion-db
│           └── Export.kt      # export simulateur en format Netbiodyn
└── web/
    └── webroot/
        ├── index.html
        ├── css/centyllion.css   # bulma + styles appliqués
        └── font/fa-solid-900.*  # Font Awesome 5 Free Solid
```

---

## 4. Concept du modèle (NetBioDyn)

Le cœur du simulateur repose sur **trois abstractions** :

### 4.1 Grain (agent)

`Grain` représente une **entité discrète** (bactérie, cellule, ...). Chaque grain possède :

- `id`, `name`, `color`, `icon` (symbole Font Awesome)
- `invisible`, `size`
- `halfLife` → demi-vie ; la probabilité de mort par pas est `1 - 2^(-1/halfLife)`
- `movementProbability` (0..1) → probabilité de se déplacer (disponible si `allowedDirection` non vide)
- `allowedDirection` → ensemble de `Direction` (Left, Right, Up, Down, diagonales)
- `fieldProductions` : `Map<Int,Float>` → pour chaque champ, quantité produite (positif ou négatif)
- `fieldInfluences` : `Map<Int,Float>` → influence (de -1 à 1) d'un champ voisin sur le comportement
- `fieldPermeable` : `Map<Int,Float>` → perméabilité du grain pour chaque champ (0..1)

> `Grain.moveBehaviour()` génère un `Behaviour` "déplacement" si `movementProbability > 0 && allowedDirection non vide`.

### 4.2 Field (champ)

`Field` représente un **milieu continu** (nutriment, signal, etc.) :

- `id`, `name`, `color`, `invisible`
- `speed` (0..1) → vitesse de diffusion
- `halfLife` (0 = pas de décroissance) → décroissance naturelle du champ
- `allowedDirection` → directions de diffusion
- `formula` → chaîne d'expression permettant de **calculer la valeur du champ** à partir de `step`, `x`, `y`, `z`, `current` et des fonctions `field(id, x, y)`, `agent(x, y)`, `count(grainId)`. La formule doit renvoyer un nombre (vérifié par `validateNumericFormula`). Exemple : `count(0) > 2 ? current * 0.95 : current`.

### 4.3 Behaviour (comportement)

`Behaviour` est une règle décrite par :

- `name`, `description`
- `probability` (0..1) → fréquence
- `agePredicate` : `Predicate<Int>` → condition sur l'âge du grain actif
- `fieldPredicates` : `List<Pair<Int, Predicate<Float>>>` → `Pair(id du champ, prédicat)` sur la valeur du champ
- `mainReactiveId` / `mainProductId` → grain principal (réactif → produit)
- `sourceReactive` (0 = l'âge du grain lui-même ; >0 = âge du voisin de réactif `sourceReactive`) → pour "éclosion" avec âge différent
- `fieldInfluences` : `Map<Int, Float>` → influence pondérée des champs voisins
- `reaction` : `List<Reaction>` → réactions secondaires : `reactiveId`, `productId`, `sourceReactive`, `allowedDirection`

Une **réaction** s'exécute lorsque un grain voisin (dans `allowedDirection`) possède le `reactiveId` spécifié.

### 4.4 Modèle et simulation

`GrainModel` contient la liste des grains, comportements et champs, avec des fonctions de création/modification/suppression (`newGrain`, `updateGrain`, `dropGrain`, `newField`, `newBehaviour`, etc.) et des fonctions de réinitialisation des IDs/noms uniques.

`Simulation` contient :
- `name`, `description`
- `settings` : `SimulationSettings` (`size` du plateau, `showGrid`, `gridTextureUrl`, `backgroundColor`)
- `agents` : `List<Int>` → tableau d'index de grains par position (ou -1 si vide), de taille `size*size`
- `assets` : `List<Asset3d>` → position/scale/rotation/opacity d'objets 3D (GLTF)
- `width`, `height`, `depth` (à préserver ; TODO supprimer de la sérialisation)

`ModelAndSimulation` agit comme un **stateholder cohérent** : il enveloppe `model` + `simulation` et expose des méthodes d'update qui maintiennent la cohérence (ex. `dropGrain` nettoie la simulation des grains supprimés : `simulation.cleaned(model)`).

### 4.5 Notation mathématique du modèle (invariants de simulation)

Là où le simulateur est pertinent, les notions s'expriment ainsi :

- **Désintégration/sporadique** : si `halfLife > 0`, la probabilité de mort pour un grain à chaque pas est  
  `p = 1 - 2^(-1/halfLife)` (demi-vie = nombre de pas pour que la population s'approche de 1/2).
- **Diffusion d'un champ** : pour un champ `f` avec vitesse `s` et perméabilités `p_i` selon les directions opposées `d`:  
  `next[i] = current[i] * (1 - s * Σ p_i / n) + production(i) + Σ current[opposé] * s * p / n`, coupé à `[minField, 1]` puis passé éventuellement par `evaluateField`.
- **Étape de simulation** : la méthode `Simulator.oneStep()` applique les règles de mort, de déplacement, de comportement (avec sélection probabiliste + influence des champs via `log10` des valeurs voisines), puis actualise les champs. `Agent` dispose d'un `age` qui augmente à chaque pas.

---

## 5. Moteur de simulation (core/src/commonMain/kotlin/com/centyllion/model/simulator.kt)

### 5.1 Classes principales

| Classe | Rôle |
|--------|------|
| `Agent(index, direction, reactiveId, age, fields)` | Représente un agent actif à une position : grain, direction, âge, valeurs des champs. |
| `ApplicableBehavior(index, age, behaviour, usedNeighbours)` | Comportement applicable à une position, avec ses voisins utilisés (pour `sourceReactive`). |
| `Simulator(model, simulation)` | Moteur : gère l'état (`agents`, `ages`, `fields1/fields2` swap, `step`), exécute `oneStep()`. |

### 5.2 `oneStep()` — déroulé

1. **Mort** : si `grain.halfLife > 0` et `random.nextDouble() <= deathProbability`, le grain meurt (`transform` → -1, -1).
2. **Âge** : chaque grain vivant a son âge incrémenté (`ageGrain`).
3. **Délibération** : pour chaque grain réactif, recherche des comportements `applicable` et filtres par `speed` (probabilité).
   - Pour les comportements non influencés par les champs : choix uniforme parmi les combinaisons de réactifs voisins.
   - Pour les comportements influencés : calcul de poids pour chaque combinaison via `log10(agent.fields[champ]) - log10(sourceField)`, translation + puissance 6, et sélection par probabilité cumulative. Cela favorise localement les régions à forte concentration du champ (biais attractif).
4. **Conservation de la matière** : les comportements concurrents pour une même case sont exclus aléatoirement (`toExclude`), puis les `ApplicableBehavior.apply` s'exécutent dans un ordre non déterministe (les `transform` utilisent des cibles distinctes).
5. **Mise à jour des champs** : diffusion, production, perméabilité, formule, normalisation, historique sauvegardé (`grainCountHistory`, `fieldAmountHistory`).
6. **Swap des tampons de champs** et incrémentation `step`.

### 5.3 Mode de mise à jour : parallèle ou séquentiel

`Simulation.parallelUpdate` (défaut : `true`) contrôle la façon dont les comportements
sont appliqués pendant un pas.

**Mode parallèle** (automates cellulaires) :
- Au début de `oneStep()`, l'état courant est copié dans des tampons `nextAgents`/`nextAges`.
- Les âges de tous les grains vivants sont pré-incrémentés dans `nextAges`.
- Toutes les lectures (`currentAgents`, `ageAtIndex`, `neighbours`) utilisent l'état initial.
- Toutes les écritures (`transform`) se font dans `nextAgents`/`nextAges`.
- À la fin du pas, les tampons sont swappés : `agents` et `ages` deviennent le nouvel état.

**Mode séquentiel** (ancien comportement) :
- Les comportements sont appliqués immédiatement dans `agents`/`ages`.
- Un comportement peut voir les changements des comportements précédents.

Le drapeau `inStep` distingue les lectures pendant un pas (âges incrémentés) des lectures
hors pas (âges courants). Le basculement se fait par un bouton dans la barre d'outils
(desktop) ou dans la barre de contrôle (web).

### 5.3bis Parallélisation CPU du pas

Quand `parallelUpdate` est actif et que `simulation.dataSize >= 10_000` (grille ≥ 100×100),
`oneStepInternal()` répartit le travail sur plusieurs threads :

- `recommendedThreads()` = 80 % des processeurs disponibles (`availableProcessors()`).
- `splitIntoBlocks()` découpe la grille en blocs contigus.
- Chaque bloc construit ses propres accumulateurs (`all`, `currentCount`,
  `currentFieldAmounts`, `dead`) et son propre `Random`.
- Les résultats sont fusionnés à la fin du pas.

L'évaluateur est étatique (`x`, `y`, `z`, `step`, `current`), donc chaque thread possède
son propre `ThreadContext` (évaluateur + evaluateurs de champs).

Abstraction multiplateforme (`expect/actual`) :
- **JVM** : `ParallelExecutor` utilise un `ExecutorService` à pool fixe (threads daemon).
- **JS** : exécution séquentielle (Kotlin/JS est mono-thread).

`Simulator.close()` libère le pool de threads quand le simulateur est remplacé.

**Champ `updatePlan`** : le plan est stocké sur le modèle (`GrainModel.updatePlan`),
pas sur la simulation. Sérialisé dans `model`, après `description` et avant `grains`,
uniquement lorsque sa valeur est `"Sequential"` (le défaut `"Parallel"` n'est pas écrits).
`Simulation.parallelUpdate` est `@Transient` et dérivé du plan (`Simulator` lit
`model.isSequentialUpdate`). Au chargement, `ModelAndSimulation.applyUpdatePlan()` synchronise.

**Réglage dans l'UI** : le plan se choisit dans l'onglet **Environment**
(`Update Plan`, liste `Parallel` / `Sequential`) ou via la bascule de la barre d'outils.

**IDs des agents et des champs** : affichés uniquement dans les zones de définition,
jamais sur la grille. Dans la liste : `NOM (ID)`. Dans l'onglet Model, à droite du
nom : `ID = ID`.

### 5.4 État et performances

- `agents: IntArray`, `ages: IntArray` : stockage dense en mémoire, `oneStep()` itère sur `simulation.dataSize = width*height*depth`.
- `grainIdArray` : cache pour résoudre un grain à partir de son id en O(1).
- Les champs sont deux tampons (`fields1`, `fields2`) swappés à chaque pas.
- Les historiques (`grainCountHistory`, `fieldAmountHistory`) sont des listes par grain/champ, utilisées pour les graphiques.
- `grainsCounts()` et `fieldAmounts()` sont des utilitaires d'agrégation.

### 5.5 Erreurs et validation

La méthode `diagnose(locale)` sur `GrainModel`, `Grain`, `Behaviour`, `Field` retourne une liste de `Problem` (source, propriété, message) pour valider la cohérence du modèle (valeurs hors bornes, IDs inexistants, directions vides, etc.). Les clients s'en servent pour afficher les messages en bas de l'éditeur (`problemsTable`).

---

## 6. Système d'expressions (formula sur les champs et conditions de comportement)

### 6.1 Proposition de syntaxe

La sous-classe `NetbioDynEvaluator` étend `Evaluator` avec des variables et fonctions spécifiques au contexte de simulation.

| Vocabulaire | Signification |
|-------------|---------------|
| `step` | numéro de pas courant |
| `x`, `y`, `z` | coordonnées de la position courante |
| `current` | valeur courante du champ (avant évaluation) |
| `agent(ix, iy)` | id du grain à la position (int, int) → int |
| `field(id, ix, iy)` | valeur du champ `id` à la position (int, int, int) → float |
| `count(grainId)` | nombre de voisins du type `grainId` (conditions de comportement) |

| Fonction | Signification |
|----------|---------------|
| `abs(v)` | valeur absolue |
| `cos(v)`, `sin(v)`, `tan(v)`, `acos`, `asin`, `atan` | trigonométrie |
| `floor(v)`, `ceil(v)`, `round(v)` | arrondi |
| `min(v...)`, `max(v...)` | min/max |
| `ln(x)`, `log(x, base)` | logarithme (ln, log base) |

### 6.2 Grammaire de l'expression (précédence de gauche à droite)

```
expression   → logic_or ("?" expression ":")?
logic_or     → logic_and ("||" logic_and)*
logic_and    → equality ("&&" equality)*
equality     → comparison (("!=" | "==") comparison)*
comparison   → sum (">" | ">=" | "<" | "<=" sum)*
sum          → factor ("-" | "+" factor)*
factor       → unary ("/" | "*" | "%") unary*
unary        → ("!" | "-" | "+") unary | exponent
exponent     → terminal ("^" unary)*
terminal     → function | variable | number | boolean | parenthesis
function     → name "(" (expr ("," expr)*) ")"
```

### 6.3 Validation et évaluation

- `validateFormula(formula)` : parse et vérifie les références (variables/fonctions) **et** évalue sans contexte ; retourne `null` si OK, sinon le message d'erreur.
- `validatePredicateFormula(formula)` : comme `validateFormula`, mais exige en plus que la formule soit un **prédicat logique** (type booléen, via `inferType()`) et interdit `field(...)`/`agent(...)`. Utilisé pour les formules de comportement.
- `validateNumericFormula(formula)` : comme `validateFormula`, mais exige que la formule renvoie un **nombre**. Utilisé pour les formules de champ.
- `inferType()` (`TypeChecker.kt`) : infère statiquement le type du résultat d'une expression (`Number`/`Boolean`) ; `containsFunctionCall(name)` : détecte un appel à une fonction dans l'arbre.
- `createEvaluator(simulator)` : crée un `NetbioDynEvaluator` attaché au simulateur (variables `step`, `x`, `y`, `z`, `current`, fonctions `agent`, `field` et `count`).
- `SimpleFieldEvaluator` : champ sans formule = identité (`evaluateField(c) = c`).
- `FormulaFieldEvaluator` : champ avec formule → évalue la formule pour chaque position, avec `current` mis à jour. Si la formule utilise `count(...)`, le simulateur fournit le nombre d'agents voisins de la case (`usesCount`).

### 6.4 Formules de comportement (`formula`)

Un `Behaviour` possède un champ `formula` qui permet d'exprimer des conditions logiques complexes :

```
count(1) > 2 && (count(2) == 1 || count(3) == 0)
```

- Évalué dans `Behaviour.applicable()` à chaque pas pour chaque case
- `count(grainId)` compte les voisins du type donné (les 8 cases adjacentes)
- Les variables `step` (âge de l'agent, pas courant pour la génération spontanée) et `x`, `y`, `z` (coordonnées de la case) sont disponibles
- Le résultat doit être un **prédicat logique** (`true`/`false`) : `validatePredicateFormula` rejette toute formule non booléenne (par ex. `sin(step)`), ainsi que l'usage de `field(...)`/`agent(...)`
- Validation automatique dans l'interface (champ "Formula" + aide "?") et dans `validation.kt`

> **Remarque** : les formules de comportement sont évaluées avec `NetbioDynEvaluator(null)` (sans simulateur). Les fonctions `agent`/`field` ne sont donc pas disponibles dans les formules de comportement — seules `count`, les variables `step`/`x`/`y`/`z` et les fonctions mathématiques le sont. Pour conditionner sur la valeur d'un champ, utiliser les seuils de champ (`fieldPredicates`).

---

## 6bis. Génération spontanée et comptage de voisins

### Génération spontanée (None → Agent)

Un comportement peut créer un agent sur une case vide en utilisant `mainReactiveId = -1` :

```
C0 : None -> Agent_A
Quand : count(Agent_B) > 1
```

- `mainReactiveId = -1` signifie "la case centrale doit être vide"
- `Behaviour.applicable()` vérifie que la case est vide (`grain == null`)
- Le simulateur traite les cases vides pour les comportements de génération
- La validation interdit `None → None` (comportement sans effet)

### Comptage de voisins structuré

`Behaviour.agentCountPredicates` permet des conditions sans expression :

```
Agent_B > 3 AND Agent_C == 5
```

- `AgentCountPredicate(grainId, op, count)` avec opérateurs `=`, `!=`, `<`, `<=`, `>`, `>=`
- Évalué dans `Behaviour.applicable()` via `neighbours.count { it.second.reactiveId == p.grainId }`

---

## 7. Interface utilisateur (Compose Multiplatform)

### 7.1 Couche commune (`compose:shared` / `ui/`)

- `AppState` : état global du document (modèle, simulation, simulateur, sélection, outil, onglets, historique annulation/renoncement, logs, problèmes).
- `AppContext` : interface des contrats (locale, modèle, simulation, simulateur, tabs, logs, problèmes, ...).
- `Netbiodyn.kt` : affichage `MaterialTheme` avec `Column` (ToolBar + MainView) et `Row` (ComponentTree + Tabs Est/Sud-Ouest).
- `Toolbar.kt` : barre de menu : nouveau, exporter, ouvrir, annuler/retourner, irrégulier, nettoyer, jouer/stop, pas, vitesse, et message.
- `ComponentTree.kt` : arborescence des grains, comportements, champs avec boutons d'ajout et suppression.
- `rows.kt` / `utils.kt` : éléments UI de base (GrainSquare, ColoredSquare, NbdButton, ...).

### 7.2 Onglets centraux (centre)

| Onglet | Fichier | Contenu |
|--------|---------|---------|
| **Model** | `ModelTab.kt` | Sélection d'éléments ; édition des grains/comportements/champs |
| **Simulation** | `SimulationTab.kt` | Affichage de la simulation (canvas Skia : grains, champs, pas) |
| **Environment** | `EnvironmentTab.kt` | Nom/description du modèle et paramètres de simulation |

### 7.3 Onglets Est (plots)

| Onglet | Fichier | Contenu |
|--------|---------|---------|
| **Plotters** | `PlotterTab.kt` | Graphiques de nombre de grains et quantités de champs (Plotter) |
| **Logs** | `LogsTab.kt` | Journal des logs |

### 7.4 Onglets Sud-Ouest (éditeurs par type)

- `GrainEdit.kt` : nom, description, demi-vie, probabilité de mouvement, direction, icône, couleur, invisible, interactions (production/influence/perméabilité de champs).
- `FieldEdit.kt` : nom, description, demi-vie, vitesse, direction, couleur, invisible, formule (champ calculé).
- `BehaviourEdit.kt` : nom, description, vitesse, prédicat d'âge, réactions (réactif principal → produit principal, puis réactions secondaires avec direction et réactif source), prédicat de champ.
- `Plotter.kt` (plotter) : graphique des histoires de grain/champ.
- `EnvironmentTab.kt` : paramètres de simulation (taille, grille, fond, URL grille).

### 7.5 `AppState` — chronologie d'undo/redo

L'état est versionné dans une liste `past`/`future`. Chaque `setter` de `modelAndSimulation` ajoute l'état précédent. `undo()` et `redo()` manipulent ces listes sans recréer le simulateur (sauf `modelState`).

---

## 8. Interface web (module `web/` — Kotlin/JS)

### 8.1 Écriture du code

Le code Kotlin/JS est compilé en **AMD modules** (`moduleKind = "amd"`), chargeable par le fichier `centyllion.js` référencé depuis `webroot/index.html`. Les dépendances JS sont gérées par Kotlin/JS.

### 8.2 Composants clés

| Fichier | Rôle |
|---------|------|
| `index.kt` | Point d'entrée ; instancie `Api`, `BrowserContext`, `ShowPage`, injecte le DOM. |
| `Api.kt` | Client HTTP via `XMLHttpRequest` ; methods : `searchQuery`, `simulationGet`, `fetchVersion`. |
| `ShowPage.kt` | Page principale : en-tête (nom, boutons nouvelle/export/import/tutoriel, mode expert), contrôle du modèle, sauvegarde en localStorage, gestion des tutoriels. |
| `ModelController.kt` | Contrôleur de la page modèle : liste grains/comportements/champs, recherche, édition, graphiques. |
| `SimulationRunController.kt` | Contrôleur de la simulation : lecture complète, lecture, pas, settings, répercussion sur les charts. |
| `PlotterController.kt` | Graphique via **data2viz** (LinePlotter) + Legende; `push(x, ys)`, `reset()`. |
| `BulmaPage.kt` | Interface commune pour les écrans Bulma (modal, notification, erreur). |
| `TutorialLayer.kt` | Tour du tutorial (étapes validation, navigation, conclusion). |
| `BacteriasTutorial.kt` / `FieldTutorial.kt` | Tutoriels pas-à-pas de création de simulation. |

### 8.3 Contrôleurs de l'éditeur (`controller/model/`)

Chaque contrôleur (`*.kt`) est une classe descendante d'un contrôleur de base, et gère un élément UI bulma (input, button, slider, etc.) généré à partir d'un élément du modèle. Ils utilisent le pattern : "observer vers `data` (valeur du modèle)", "modifier `data` via `onUpdate`".

- `ModelController.kt` : racine de la page modèle ; orchestre les contrôleurs enfants ; gère la recherche (`SearchController`), l'export CSV, les graphiques.
- `SimulationRunController.kt` : contrôle la lecture complète (play/stop, pas, slider de vitesse, settings), appelle `simulator.oneStep()` et met à jour les graphs.
- `GrainEditController.kt`, `FieldEditController.kt`, `BehaviourEditController.kt` : édition détaillée des grains, champs et comportements.
- `FieldSelectController.kt`, `FieldChangeController.kt`, `FieldDisplayController.kt` : sélection/affichage/suppression de champs.
- `GrainSelectController.kt`, `GrainDisplayController.kt` : sélection/affichage de grains.
- `BehaviourDisplayController.kt`, `ReactionEditController.kt`, `FieldPredicateController.kt`, `FloatPredicateController.kt`, `IntPredicateController.kt`, `SourceReactiveSelectController.kt`, `DirectionController.kt`, `ColorSelectController.kt`, `IconSelectController.kt`, `SimulationSettingsController.kt`, `Simulator3dViewController.kt` : sous-composants détaillés.
- `ModelSearchController.kt` : recherche de modèles sur le serveur.
- `SearchController.kt`, `UndoRedoSupport.kt`, `stringControllers.kt` (utils).

### 8.4 Graphiques (Plotter) et 3D

- **data2viz** (`plotter/`) : tracé des courbes (nombre de grains, quantité de champs) dans `Plotter` (Canvas Skia) avec axes, ticks et courbes coloriées.
- **Babylon.js** (`controller/model/Simulator3dViewController.kt` ≥ 3000 L) : visualisation 3D du plateau de simulation virtuel, avec : maillage du sol, support de champs, maillages des grains (boîtes/cercles selon icône), outils de dessin (stylo, ligne, carré, pistil, gomme), camera ArcRotate, rendu en boucle, screenshots, textures GLTF pour les assets.

---

## 9. Serveur / API (module `db/` — Ktor + SQLDelight)

> **Module optionnel** : le module `db` est **commenté par défaut** dans `settings.gradle.kts`. Il nécessite un **JDK complet** (avec `javac`), pas seulement un JRE. Pour l'inclure :
> ```bash
> sudo apt-get install -y openjdk-21-jdk-headless
> # puis décommenter include("db") dans settings.gradle.kts
> ```

Le serveur Ktor sert l'API REST utilisée par la page web :

| Route | Méthode | Description |
|-------|---------|-------------|
| `/search/{query}` | GET | Recherche par nom de simulation/modèle (LIKE) |
| `/simulation/{id}` | GET | Retourne `ModelAndSimulation` (model, simulation, thumbnail) |
| `/asset/{id}` | GET | Retourne le contenu d'un asset (fichier binaire) |
| `/asset/{id}/{name}` / `/asset/{id}/{name}/{path...}` | GET | Cache des assets téléchargeables (ZIP) |

La base de données **SQLDelight** (PostgreSQL) contient :
- `users` (UUID, name, keycloak, email, username, tutorialDone, createdOn)
- `assets` (UUID, name, content BYTEA, usedId, entries)
- `infodescriptions` (UUID, usedId, createdOn, lastModifiedOn, readAccess)
- `modeldescriptions` (UUID, info, model TEXT, type, version, tags)
- `simulationdescriptions` (UUID, info, modelid, thumbnailId, simulation TEXT, type, version)

Les contrôleurs Ktor sont : `Server.kt` (routing), `main.kt` (clikt : `centyllion-db` avec sous-commandes `Server` et `Export`), `Export.kt` (export d'une simulation au format "Netbiodyn" = JSON `{"model":..., "simulation":...}`).

---

## 10. Configuration et build

- **Gradle wrapper** : Gradle 8.11 (`gradle-8.11-bin.zip`).
- **Kotlin** : `2.1.0`, multi-platform (jvm, js, wasm via composeShared).
- **Compose** : `1.7.1` (Compose Multiplatform) + compilateur `compose-compiler` (kotlin 2.1).
- **SQLDelight** : `2.0.1` (dialect postgresql).
- **Ktor** : `2.3.6` (server core, cio, content-negotiation, serialization, cors, calllog).
- **Bulma-kotlin** : `0.5` (UI).
- **Babylon-kotlin** : `0.5.2` (3D).
- **data2viz** : `0.10.1` (plotting).
- **Font Awesome** : `1.1.1`.
- **FileKit** : `0.8.8` (choix de fichiers).
- **Clikt** : `4.2.1` (CLI db).
- **Evaluation d'expressions** : `multiplatform-expressions-evaluator` 1.1.1.
- **UUID** : `benasher44/uuid`.
- **kotlinx-serialization** : `1.6.3`.
- **coroutines** : `1.8.0`.
- **PostgreSQL** : `42.6.0`.
- **logback** : `1.4.14`.
- **junit** : `5.6.0`.

### Commandes de build

```bash
# Tout compiler
./gradlew build

# Compile les modules
./gradlew :core:compileKotlinJvm
./gradlew :web:compileKotlinJs
./gradlew :compose:shared:compileKotlinJvm
./gradlew :compose:shared:compileKotlinWasmJs
./gradlew :db:build

# Exécuter le serveur (port 4545 par défaut)
./gradlew :db:run --args="--server-port=4545"

# Docker (db/)
./gradlew :db:copyForDocker
```

### Build standalone (sans serveur)

```bash
# ── Application desktop autonome (JRE incluse, ~180 Mo) ──
./gradlew :compose:shared:createDistributable
# Sortie : compose/shared/build/compose/binaries/main/app/Centyllion/
# Lancement (headless, rendu logiciel) :
JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE" \
  xvfb-run -a ./compose/shared/build/compose/binaries/main/app/Centyllion/bin/Centyllion

# ── Site web statique ──
./gradlew :web:jsBrowserProductionWebpack :web:syncJs
# Bundle : web/webroot/js/centyllion/centyllion.js
# Service :
python3 -m http.server 8081 --directory web/webroot
# → http://localhost:8081
```

### Environnement headless (conteneur)

Pour exécuter l'application desktop dans un conteneur sans écran :

```bash
# Prérequis
apt-get install -y xvfb libxtst6

# Lancement avec rendu logiciel (Skiko ne peut pas créer de contexte GL sans GPU)
JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE" xvfb-run -a ./gradlew :compose:shared:run
```

| Variable / flag | Rôle |
|---|---|
| `xvfb-run -a` | Crée un serveur X virtuel éphémère |
| `-Dskiko.renderApi=SOFTWARE` | Force le rendu logiciel Skia (pas de GPU nécessaire) |

> La compilation complète nécessite les référentiels GitHub (`maven.pkg.github.com/centyllion/...`) pour bulma-kotlin et babylon-kotlin, avec les credentials `grpUser`/`grpToken` (définis dans les variables de projet local, ou dans les fichiers de configuration métier).

---

## 11. Déploiement

### 11.1 Web (site en ligne)

Le site `www.centyllion.com` sert :
1. `webroot/index.html` (charge le CSS, Font Awesome, et `centyllion.js`).
2. `webroot/js/centyllion/centyllion.js` (généré par Gradle : `syncJs` + `generateVersion`).
3. Le serveur `db/` (Ktor) qui expose l'API REST.
4. La base PostgreSQL hébergée (cf. `db/docker/` Dockerfile + `db-*.tar` pour l'image).

### 11.2 Bureau (Compose)

`compose:shared` cible `jvm`, avec `mainClass = com.centyllion.ui.NetbiodynKt` ; le build multiplateforme produit des packages `.dmg`, `.msi`, `.deb` (nativeDistributions).

### 11.3 Web Assembly (WASM)

`compose:shared` cible `wasmJs` avec `src/wasmJsMain` : `index.html`, `styles.css`, `com.centyllion.ui.main` (UI Compose, exécuté dans le navigateur WASM).

---

## 12. Tutoriels (en ligne)

Deux tutoriels interactifs sont intégrés :

- **BacteriasTutorial** : crée une bactérie, la déplace, ajoute une division (comportement) pour faire croître une colonie. (17 étapes)
- **FieldTutorial** : ajoute un champ sucre, un grain source, un impact d'attraction et un seuil. (15 étapes)

Chaque étape est une classe `TutorialStep` avec `title`, `content`, `selector` (éléments DOM à attendre), `validated` (condition booléenne) et `nextCallback` (action). `TutorialLayer` orchestre le succès et la navigation.

---

## 13. Tests

Le module `core` possède des tests communs (`commonTest`) : `CombinationTest`, `FormulaTest`, `PerformanceTest`, `PositionTest`, `PredicateTest`, `SimulationTest`, `models.kt`. Ils utilisent le bundle de test Kotlin `test` (common + jvm + js).

```bash
# Tests JVM (modèle, simulation, expressions)
./gradlew :core:jvmTest

# Tests JS — désactivés par défaut (nécessitent Chrome/Chromium)
./gradlew :core:jsBrowserTest -PenableJsTests=true
```

Les tests couvrent notamment les nouvelles fonctionnalités de l'extension :
- `testGenerationBehaviour` : génération spontanée (`None → Agent`)
- `testAgentCountPredicate` : comptage de voisins structuré
- `testConditionExpression`, `testConditionExpressionFalse` : expressions de condition
- `testParallelUpdate` : mise à jour parallèle vs séquentielle

---

## 14. Points d'entrée et architecture pour l'ajout de fonctionnalités

Étant donné que l'utilisateur souhaite **ajouter des fonctionnalités**, voici les principaux endroits où intervenir, dont le projet est conçu pour être étendu :

### 14.1 Modèle métier (`core/src/commonMain/.../model/`)

- **`model.kt`** : ajouter des champs de grain/behaviour/field, nouvelles données de comportement.
- **`simulator.kt`** : ajouter de nouvelles règles de comportement (modification de `oneStep()`, de `ApplicableBehavior`, de `Simulator`).
- **`expression/`** : ajouter des variables/fonctions dans `NetbioDynEvaluator` ou `Stdlib`.
- **`validation.kt`** : ajouter des règles de validation.
- **`technic.kt`** : ajouter de nouvelles structures de données de données (ex. `CollectionInfo`, `Info`).

### 14.2 Interface (Compose)

- **Ajout d'onglet** : créer une classe implémentant `Tab`, l'ajouter à `AppState.centerTabs` ou `eastTabs`.
- **Ajout d'éditeur** : créer un contrôleur qui observe un élément du modèle et met à jour `modelAndSimulation`.
- **Ajout de contrôleur web** : correspondant au contrôleur Kotlin/JS de la page web.

### 14.3 Serveur/API

- **Ajout de route** : modifier `db/src/main/kotlin/com/centyllion/db/Server.kt`.
- **Ajout de schéma DB** : modifier `Database.sq` et régénérer le code SQLDelight.

### 14.4 Web (Kotlin/JS)

- **Ajout de nouveau contrôleur JS** : créer une classe dans `controller/model/` correspondant à un contrôleur de l'interface web.
- **Ajout de voie de rendu** : graphiques data2viz, 3D Babylon.js, etc.

### 14.5 Tests

- Ajouter des tests dans `core/src/commonTest/kotlin/com/centyllion/model/` pour valider le nouveau comportement.

---

## 15. Fiches de lecture rapides

- **Lancer le site en local** : `./gradlew :db:run` + ouvrir `webroot/index.html` (avec le serveur cours ou un proxy).
- **Lancer l'interface bureau** : `./gradlew :compose:shared:jvmExec` (si configuré) ou build jar.
- **Structure du modèle** : `Grain` + `Behaviour` + `Field` → `GrainModel` → `Simulation` → `Simulator`.
- **Simuler** : `simulator.oneStep()` → `step++` → historiques mis à jour.
- **Exporter** : `db/Export.kt` → JSON `{"model":..., "simulation":...}`.
- **API** : `/simulation/{id}` (JSON), `/search/{query}`, `/asset/{id}...`.

---

## 16. Historique Git (extrait)

Derniers commits : `unwanted tests removed`, `Adds model name edition in showpage...`, `Allows simulation of 200`, `Support wasm browser`, `Prepare compose multiplatform`, `Add formula performance test`, `Center direction`, `Replace kotlinx-uuid by benasher44`, `Correct pointer color change`, `Age predicate and source reactive are for expert mode`, `Adds Compose version`, `Updates to kotlin 2`, `Add Compose version`, `Adds grid for behaviour`, `Adds square for directions`, `Reworked properties title and combo`, `Removes split-panes`, `Important re-layout`, `Adds compose UI`, `Update field tutorial`, `Support zip assets`, `Adds examples tab...`, `Adds support for asset in server and use of thumbnail`, `Adds validation for fields`, `Adds search query`, `Add validation for fields`, `Test docker`, `Adds native image`, `Update translation`, `Add formula performance test`, `Update kotlin`, `Prevent chart resize if result is < 0`, `Correct still drawing even getting outside plane`, `Working on Compose version`, `Allows simulation of 200`, `Use value as key for remember in text field`, `Cleaning`, `Corrects missing value error`, `Support wasm browser`, `Adds model name edition...`, `Allows simulation of 200`.

Branches distantes : `main`, `compose`, `master`, `purehtml`, `share`. Dépôt upstream : `github.com/jeancharles-roger/centyllion-app`.

---

*Document généré à partir de l'inspection du code source du projet Centyllion (`Prog/Simulateurs/NetBioDyn_Simulators/centyllion-app`). Aucune modification n'a été apportée au dépôt.*
