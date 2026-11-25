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
| **Prédicats graphiques** | En mode normal : lignes « Âge de la simulation » (`agePredicates`), « Seuils des champs » (`fieldPredicates`) et « Nb voisins » (`agentCountPredicates`, avec choix du type), toutes combinées en **ET** ; chaque groupe a son bouton `Aide` (voir §8.3bis) |
| **Actions de comportement** | `actions` : `Add`, `Remove`, `Move`, `SetField`, `AddField`, `ClearField`, exécutées après les réactions (voir §6.5) |
| **Captures** | `Capture()` : image JPEG de la vue de simulation (`NOM_MODELE__PAS.jpg`), écrite dans `captures/NOM_MODELE/` du **dossier de travail** ou téléchargée (voir §6.5bis) |
| **Formules de comportement** | `formula` avec `step` (étape courante), `age(n)` (âge du réactif n), `count`, `field`, `agent` (voir §6.4) |
| **Expressions de condition** | `formula` : expressions logiques avec parenthèses et opérateurs (`count(1) > 2 && count(2) == 1`) |
| **Mise à jour parallèle** | `Simulation.parallelUpdate` (défaut) : calculs depuis l'état initial du pas, adapté aux automates cellulaires |
| **Conditions aux limites** | `boundaryConditions` par grain (4 parois toriques/imperméables) et `toric` par champ ; défaut torique partout, asymétrie autorisée (voir §4, §10) |
| **Validation corrigée** | `validateFormula` vérifie maintenant les références et signale les erreurs |
| **Build standalone** | Application desktop autonome + site web statique, sans serveur |

Voir le [README.md](README.md) pour l'installation et l'exécution.

---

## 1. Aperçu

**Centyllion** est un environnement de modélisation et de simulation en **multi-agents** (celle du projet **NetBioDyn**), conçu à l'usage de l'enseignement. Il permet de créer des modèles de "grains" (agents), de "comportements" et de "champs" (milieux continus), puis de simuler leur évolution dans un plateau quadrillage défini.

L'application est accessible en ligne et s'ouvre sous deux formes :

- **Web** : navigateur (compilation Kotlin/JS, interface Bulma + data2viz ; vue de simulation en canvas 2D).
- **Bureau (Compose Multiplatform)** : application JavaFX/Swing (package `com.centyllion.ui.NetbiodynKt`).

Le code source est partagé entre un module `core` (logique métier, multi-platforme `jvm`, `js`, `wasmJs`) et deux modules d'interface :
- `compose:shared` → interfaces utilisateur en Compose Multiplatform (cible `jvm` pour le bureau, `wasmJs` pour le navigateur WASM, `js` pour le web classique).  <!-- module compose supprimé : version bureau retirée -->
- `web` → module Kotlin/JS spécifique au navigateur web.
- `db` → serveur Ktor + SQLDelight (cible JVM) qui fournit l'API REST derrière laquelle s'articule le site en ligne.

---

## 2. Architecture générale

```
┌──────────────────────────────────────────────────────────────┐
│  centyllion-app                                              │
│  ├─ core/           Kotlin Multiplatform : modèle, simulation │
│  │                 (logique métier, expression, i18n, tests)  │
│  ├─ compose:shared/ │ Compose Multiplatform : UI ui (jvm,    │  <!-- module compose supprimé : version bureau retirée -->
│  │                 │ wasmJs, js)                               │
│  ├─ web/            Kotlin/JS : interface navigateur bulma    │
│  │                 │ + canvas 2D, data2viz                     │
│  ├─ db/             JVM : serveur Ktor REST + SQLDelight      │
│  └─ settings.gradle.kts (4 sous-projets)                       │
└──────────────────────────────────────────────────────────────┘
```

### Modules

| Module | Type | Cibles | Principal |
|--------|------|--------|-----------|
| `:core` | kotlin-multiplatform | jvm, js, (wasm via composeShared) | Modèle, simulateur, expression, i18n |  <!-- module compose supprimé : version bureau retirée -->
| `:web` | kotlin-multiplatform | js | Interface web (Bulma, data2viz, canvas 2D) |
| `:compose:shared` | kotlin-multiplatform | jvm, wasmJs | Interface Compose (bureau + WASM) |  <!-- module compose supprimé : version bureau retirée -->
| `:db` | kotlin-multiplatform | jvm | Serveur Ktor REST + DB SQLDelight |

La racine `build.gradle.kts` configure les projets, le group `com.centyllion`, la version `0.0.1`, et la résolution des versions dans `versions.toml`.

---

## 3. Structure des fichiers

```
centyllion-app/
├── settings.gradle.kts      # 4 sous-projets : core, web, compose:shared, db  <!-- module compose supprimé : version bureau retirée -->
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
│       │   │                # actions.kt, shapes.kt, tools.kt, utils.kt, validation.kt,
│       │   │                # gridcommands.kt, colors.kt, icons.kt
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
│       ├─ tutorial/         # 5 tutoriels (débutant + expert), TutorialLayer, formulas.kt
│       ├─ page/             # BulmaPage, ShowPage
│       └─ controller/
│           ├─ model/*.kt    # 20 contrôleurs de l'éditeur
│           └─ utils/*.kt    # SearchController, UndoRedoSupport, stringControllers
├── compose/  <!-- module compose supprimé : version bureau retirée -->
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
- `boundaryConditions` : `BoundaryConditions(left, right, top, bottom)` → autorisation par paroi de traverser la bordure du plateau (retour torique). Défaut : `true` partout (torique dans les 4 directions). L'asymétrie est autorisée : `left=false, right=true` signifie qu'un agent du bord gauche ne traverse pas la paroi gauche en allant à gauche, tandis qu'un agent du bord droit traverse la paroi droite en allant à droite (vérification `BoundaryConditions.crossed(position, direction, width, height)`). Éditable dans **les deux modes** (4 interrupteurs entre *Movement* et *Fields*).

> `Grain.moveBehaviour()` génère un `Behaviour` "déplacement" si `movementProbability > 0 && allowedDirection non vide`.

### 4.2 Field (champ)

`Field` représente un **milieu continu** (nutriment, signal, etc.) :

- `id`, `name`, `color`, `invisible`
- `speed` (0..1) → vitesse de diffusion
- `halfLife` (0 = pas de décroissance) → décroissance naturelle du champ
- `allowedDirection` → directions de diffusion
- `toric` : `Boolean` (défaut `true`) → bordure torique du champ ; `false` = bordure **imperméable** : la diffusion ne traverse pas les bords de la grille (« best effort », sans solveur numérique dédié). Éditable dans les deux modes (interrupteur *Toric*).
- `formula` → chaîne d'expression permettant de **calculer la valeur du champ** à partir de `step`, `x`, `y`, `z`, `current` et des fonctions `field(id)`, `field(id, dx, dy)`, `agent(x, y)`, `count(grainId)`. La formule doit renvoyer un nombre (vérifié par `validateNumericFormula`). Exemple : `count(0) > 2 ? current * 0.95 : current`.

### 4.3 Behaviour (comportement)

`Behaviour` est une règle décrite par :

- `name`, `description`
- `probability` (0..1) → fréquence
- `agePredicate` : `Predicate<Int>` → condition sur l'âge du grain actif (prédicat « historique », conservé pour la compatibilité des fichiers)
- `agePredicates` : `List<Predicate<Int>>` → lignes supplémentaires du prédicat graphique « Âge de la simulation » ; **toutes** les lignes (et `agePredicate`) doivent être vraies simultanément (ET)
- `fieldPredicates` : `List<Pair<Int, Predicate<Float>>>` → `Pair(id du champ, prédicat)` sur la valeur du champ
- `mainReactiveId` / `mainProductId` → grain principal (réactif → produit)
- `sourceReactive` (0 = l'âge du grain lui-même ; >0 = âge du voisin de réactif `sourceReactive`) → pour "éclosion" avec âge différent
- `fieldInfluences` : `Map<Int, Float>` → influence pondérée des champs voisins
- `reaction` : `List<Reaction>` → réactions secondaires : `reactiveId`, `productId`, `sourceReactive`, `allowedDirection`
- `formula` : chaîne (libellée **Predicate** dans l'interface) → condition logique d'application (voir 6.4)
- `actions` : chaîne → liste d'actions exécutées après les réactions (voir 6.5)
- `agentCountPredicates` : `List<AgentCountPredicate(grainId, op, count)>` → conditions sur le nombre de voisins ; `grainId < 0` = tout agent voisin occupé (8 directions, valeur 0-8) (voir 6bis)
- `boundaryConditions` : `BoundaryConditions(left, right, top, bottom)` → parois du **voisinage visible** du comportement (même classe et même forme JSON que pour un grain) ; une paroi `false` rend invisibles les voisins situés au-delà (aucun repliement, aucune lecture), les 4 parois `true` (défaut) étant le cas torique (voir 4.5)

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
- **Conditions aux limites** : un déplacement (ou une production sur case voisine vide) traverse la bordure dans la direction `d` si et seulement si la paroi correspondante est autorisée : `crossesBorder(position, d, w, h)` (test géométrique) ∧ `boundaryConditions.crossed(...)` (drapeau du grain). Par défaut (torique), le retour est `position + w` / `position - w` (ou `± h`) modulo la taille. Pour un champ avec `toric = false`, les échanges de diffusion à travers les bords sont simplement omis (aucun terme de voisin opposé au-delà de la grille).
- **Voisinage visible d'un comportement** : `Behaviour.boundaryConditions` filtre la vue des 8 voisins. Si `crossed(position, d, w, h)` est vrai pour une paroi fermée, la direction `d` est masquée : ce voisin n'existe ni comme réactif, ni comme case vide cible d'un produit, il n'est pas compté (`count(...)`, `agentCountPredicates`, y compris `Nb voisins`/tous agents/cases vides) et ses champs ne sont pas lus (`fieldInfluences`). Toutes les parois toriques (défaut) ⇒ `hasClosedBoundary == false` ⇒ table de voisins précalculée utilisée telle quelle, sans filtrage ni coût ajouté.
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
- Les variables `step` (étape courante de la simulation) et `x`, `y`, `z` (coordonnées de la case) sont disponibles
- `age(n)` renvoie l'âge du réactif `n` : `1` = agent principal, `2` à `N+1` = voisins des réactions, même numérotation que la colonne "Sources" (héritage d'âge) ; `0` si le réactif n'existe pas
- Les fonctions `field(id)` / `field(id, dx, dy)` (valeur du champ sur la case courante ou à un offset) et `agent(x, y)` (ID de l'agent en une position) sont disponibles : le simulateur passe l'état de la grille à `applicable()`
- Le résultat doit être un **prédicat logique** (`true`/`false`) : `validatePredicateFormula` rejette toute formule non booléenne (par ex. `sin(step)`), ainsi que les ternaires aux branches de types différents
- Validation automatique dans l'interface (champ "Predicate" + aide "Help") et dans `validation.kt` (propriété du problème : `Predicate`)

> **Remarque** : les formules de comportement sont évaluées avec l'état de la grille au début du pas (double buffering en mode parallèle). `field(id, dx, dy)` est relatif à la case de l'agent ; `field(...)` et `agent(...)` utilisent des **coordonnées toriques** (modulo de la taille de la grille), comme `count()`/`moveIndex` : un offset qui sort de la grille ressort de l'autre côté.

### 6.5 Actions de comportement (`actions`)

Un `Behaviour` possède un champ `actions` : une liste d'actions séparées par des virgules, exécutées **après** les réactions lorsque le comportement s'applique :

```
Add(0, 1, 0), Remove(1, 0, 0), Move(0, 0, 0, 1, 0), SetField(2, 0, 0, 0.5)
```

| Action | Effet |
|---|---|
| `Add(id, dx, dy)` | Crée un agent de type `id` à l'offset (dx, dy) (case cible vide uniquement) |
| `Remove(id, dx, dy)` | Détruit l'agent de type `id` à l'offset (dx, dy) |
| `Move(id, dx1, dy1, dx2, dy2)` | Déplace l'agent de type `id` de l'offset (dx1, dy1) à (dx2, dy2) (cible vide ou même type : échange) |
| `SetField(id, dx, dy, valeur)` | Fixe la valeur du champ `id` à l'offset (dx, dy) (bornée [0, 1]) |
| `AddField(id, dx, dy, delta)` | Ajoute `delta` à la valeur du champ `id` à l'offset (dx, dy) (résultat borné [0, 1]) |
| `ClearField(id, dx, dy)` | Remet la valeur du champ `id` à l'offset (dx, dy) à 0 |

- Les offsets sont **relatifs à la case du comportement** et **toriques** (modulo, comme `moveIndex`)
- Les arguments sont des expressions numériques évaluées dans le contexte de la case (`step`, `age(n)`, `x`, `y`, `z`, `count(grainId)`, `field(id, dx, dy)`, fonctions mathématiques) ; `count` est compté sur l'état du début du pas, comme dans le prédicat ; `age(n)` suit la même numérotation que le prédicat
- Parsing : `parseActionFormula()` ; validation : `validateActionFormula()` (propriété du problème : `Actions`)
- Exécution : `ApplicableBehavior.apply()` → `applyActions()` dans `simulator.kt`

---

### 6.5bis Méta-actions d'un modificateur (`modifiers[].actions`)

Un `SimulationModifier` (`name`, `description`, `formula`, `actions`, `probability`, `id`,
`enabled`) est le **seul** endroit où les méta-actions sont admises : `validateActionFormula()`
les refuse dans un comportement, `validateModifierActions()` n'accepte qu'elles. Elles tournent
une fois par pas, hors de toute case, après les comportements.

**Règle de casse** : méta-fonctions et méta-tests en **majuscule** (`Pause`, `AddRect`,
`IsVisible`), fonctions de formule et prédicats en **minuscules** (`abs`, `min`, `max`, `sin`,
`cos`, `count`, `field`, `age`, `agent`, variables `step`, `x`, `y`, `z`, `current`).

| Catégorie | Noms |
|---|---|
| Boucle de course | `Pause()`, `Pause(t)`, `RunOneStep()`, `RunNStep(N)`, `Rewind()`, `Restart()` |
| Captures | `Capture()` |
| Population / activation | `Add(id, N)`, `AddRect(id, N, x1, y1, x2, y2[, fill])`, `AddCircle(id, N, x, y, radius[, fill])`, `DrawLine(id, x1, y1, x2, y2)`, `EraseRect(x1, y1, x2, y2[, fill])`, `EraseRect(id, x1, y1, x2, y2[, fill])`, `EraseCircle(x, y, radius[, fill])`, `EraseCircle(id, x, y, radius[, fill])`, `Erase(id)`, `Activate(id)`, `Inactivate(id)`, `Use(id)`, `Unuse(id)` |
| Commandes de grille | `Show(id)`, `Hide(id)`, `Select(x1, y1, x2, y2)`, `Cut()`, `Copy()`, `EraseSelection()`, `Paste(x, y, overwrite)`, `FlushSelection()`, `Unselect()`, `PasteAndFlush(x, y, overwrite)`, `Rotate(N)`, `FlipX()`, `FlipY()`, `AllowToricSelection(enabled)`, `AllowOverwriting(enabled)` |
| Tests d'état (condition seulement) | `IsBehaviourActive(id)`, `IsModifierActive(id)`, `IsToricSelectionOn()`, `IsOverwritingOn()`, `IsVisible(id)` |

**Fin d'opération et rectangle de sélection** : `GridCommand.dropsSelection` (vrai pour
`EraseSelection`, `FlushSelection`, `PasteAndFlush` et `Unselect`) dit quelles commandes *terminent*
une opération et **lâchent donc le rectangle de sélection**, comme `Unselect()` : la vue appelle son
`clearSelection()` après le travail de la commande (`applyGridCommands`). Ce lâcher ne concerne que
l'affichage — `FlushSelection()` vide la mémoire (le presse-papiers) et ne modifie pas la grille,
`PasteAndFlush()` colle d'abord (le collage a besoin du presse-papiers) puis vide la mémoire — et
`Unselect()` ne fait que cela : ni la grille ni la mémoire ne changent.

**Alias obsolètes** : `pause()` (minuscules), `AddDisc` et `EraseDisc` restent acceptés — table
`deprecatedActionNames` (`actions.kt`), normalisée avant toute recherche de nom — pour que les
`.nbd2` déjà enregistrés continuent de se charger et de se comporter à l'identique. `Pause()`,
`AddCircle` et `EraseCircle` sont les noms courants ; les anciens ne sont plus documentés que comme
alias.

**Le drapeau `fill`** des quatre formes est un **littéral** `true` / `false`, écrit après les
autres arguments (`AddRect(1, 10, 0, 0, 9, 9, false)`), comme le booléen de
`Paste(x, y, overwrite)` : il n'est pas évalué comme une expression, et un argument non booléen à
cette place est refusé avec un message qui nomme `fill` et demande `true` ou `false`. Omis, il vaut
`true` (forme pleine), le comportement d'avant le drapeau : aucune formule existante ne change de
sens. `EraseRect(x1, y1, x2, y2, false)` reste distinguishable de la forme typée
`EraseRect(id, x1, y1, x2, y2)` : c'est le **littéral** en dernière position qui marque le drapeau.

**Deux canaux distincts, à ne pas confondre** :

- les **commandes de grille** sont mises en file dans `Simulator.pendingGridCommands` et drainées
  par la vue en fin de pas (`drainGridCommands()`) : le noyau ne connaît ni la sélection ni le
  presse-papiers ;
- les **actions de boucle** passent par des **drapeaux** du `Simulator` —
  `pauseRequested`, `pauseDurationSeconds`, `stepsRequested`, `rewindRequested`,
  `restartRequested` — regroupés par `consumeControlRequests()` en un
  `SimulationControlRequests`, et transformés en état de boucle par
  `SimulationLoop.afterStep()`. Elles ne touchent jamais la grille.

**Troisième canal — les captures** : `Capture()` n'agit ni sur la grille ni sur la boucle. Le noyau
incrémente `Simulator.captureRequestCount` (même forme que `modifierPauseCount`, remis à zéro par
`reset()`), et la vue consomme le compteur juste après le pas (`consumeCaptureRequest()`, appelé par
`SimulationRunController.takeCaptures()`, après le rendu du pas et avant le pas suivant). L'encodage
est **synchrone** (`SimulatorViewController.captureJpeg()`, JPEG qualité 0,92 par `toDataURL` :
`toBlob` rendrait la main après que le pas suivant a repeint le canevas). Le nom,
`NOM_MODELE__PAS.jpg`, vient des fonctions pures `captureFileName()` / `sanitizeCaptureName()`
(`core/.../model/capture.kt`) : caractères interdits (`/ \ : * ? " < > |`) remplacés par `_`,
espaces et points de bord retirés, longueur bornée à `CAPTURE_NAME_MAX_LENGTH` (64), repli explicite
`model`, préfixe `_` pour un nom de périphérique Windows. Le chemin relatif, `captures/NOM_MODELE/`,
vient de `captureFolder()` / `captureRelativePath()` du même fichier. Le numéro est le compteur
affiché pour l'image : une condition `step == 5` produit `modele__6.jpg`, puisque l'image montre
l'état obtenu par ce pas. `WorkFolder` (`web/.../client/capture/`) écrit dans `captures/NOM_MODELE/`
du dossier de travail (`getDirectoryHandle('captures', {create: true})`, puis celui du modèle,
`getFileHandle(create: true)`, `createWritable()`), après
`queryPermission({mode: 'readwrite'})` ; sans dossier, sans permission ou si l'écriture échoue,
l'image est **téléchargée** (`Blob` + ancre `download`) et un message dit toujours laquelle des deux
voies a été suivie. Le `handle` du dossier vit dans **sa propre base IndexedDB** (`centyllion-home`
v1, magasin `handles`, clé `home`) : elle ne partage rien avec `centyllion-gallery` ni avec
`centyllion-files`, et son writer refuse tout ce qui n'est pas un handle de dossier, donc aucune
image ne peut y entrer ; `localStorage` ne contient que deux chaînes (nom affiché, mode de repli).
L'ancienne clé `workFolder` de `centyllion-files` n'est ni lue ni migrée : elle est supprimée une
fois si elle existe. **Aucun octet d'image ne passe par le stockage du navigateur** : la capture est
un `Blob` en mémoire, écrit dans le dossier ou téléchargé, jamais dans IndexedDB, `localStorage`,
`sessionStorage` ni Cache Storage. Le contrôle est le **bouton maison** (`fa-home` plein) placé dans
la rangée du bouton **Informations sur le modèle**, sous la barre du haut : **magenta plein**
(`ElementColor.Danger`, `#d24193`) quand aucun dossier n'est défini, **bleu plein**
(`ElementColor.Primary`, `#9595f8`, le bleu des boutons `+`) quand le dossier est défini et sa
permission accordée, **même bleu atténué** (opacité réduite) quand la permission doit être
renouvelée ; son infobulle (`title` et `aria-label`) porte l'état, aucune note adjacente ne
subsiste. Le sélecteur s'ouvre dans le dossier courant (`showDirectoryPicker(..., startIn: handle)`),
et l'option est ignorée proprement quand le handle manque ou que le navigateur la refuse. Tests :
`core/src/commonTest/.../CaptureTest.kt` — nom et nettoyage du nom de modèle, chemin relatif
`captures/NOM_MODELE/`, refus dans une action de comportement, incrément/consommation du compteur,
remise à zéro par un rembobinage ; aucun test ne dépend d'un navigateur.

`SimulationLoop` (commonMain, `simulationloop.kt`) porte la sémantique une seule fois, pour la vue
web et pour les tests JVM : `canStep`, `start()`, `stop()`, `afterStep()` → `LoopUpdate(running,
remainingSteps, rewound, pauseSeconds)`. `RunOneStep()` est `RunNStep(1)` : la boucle exécute les
pas demandés puis se met en pause d'elle-même ; `N <= 0` n'ajoute aucun pas et n'arrête pas la
course. Dans un même pas, l'ordre d'application est `Restart`, `Rewind`, `Pause`/`Pause(t)`,
`RunOneStep`/`RunNStep` (le dernier l'emporte) ; les pas de deux demandes s'additionnent et un
`Pause()` annule ceux qui restent. `Rewind()` et `Restart()` appellent `Simulator.reset()`, qui
restaure l'état d'activation du début de course et efface les demandes en attente.

**Pause minutée (`Pause(t)`)** — `Action.TimedPause(seconds)` : `t` est un nombre de **secondes
réelles**, littéral ou expression, strictement positif. Le parseur refuse une valeur constante
`<= 0` (`constantNumberOrNull()` d'`expression/TypeChecker.kt` replie les littéraux et les
opérations arithmétiques) et un argument non numérique ; une expression qui dépend de l'état
(`Pause(step / 10)`) n'est vérifiable qu'à l'exécution, où une valeur `<= 0` est ignorée, comme
`RunNStep(N <= 0)`. Une pause minutée est une **méta-action** (refusée dans un comportement) et
elle ne change pas la trajectoire : aucun pas n'est exécuté pendant la pause.

C'est une **pause de confort**, pas une horloge de modèle. `SimulationLoop` ne lit jamais l'horloge
système : il reçoit une `Clock` (`fun interface Clock { fun nowMillis(): Long }`, `clock.kt`),
implémentée par `systemClock()` (`System.currentTimeMillis()` en JVM, `Date.now()` en JS) et
injectable dans les tests. À la réception de `Pause(t)`, `afterStep()` passe `running = true` (la
boucle **reste vivante**), pose `pausedUntilMillis = clock.nowMillis() + ceil(t * 1000)` et
`pauseSeconds = t`, ce qui met `canStep` à `false` sans arrêter la vue. `resumeIfDue()` — appelé à
chaque image d'animation, y compris celles où aucun pas ne tourne — lève la pause dès que
l'échéance est atteinte (`>=`, donc la pause dure **au moins** `t`), remet `running = true` et
rapporte `true` : la reprise appartient à la boucle, pas à la vue, et se teste sans navigateur ni
attente réelle. `start()` et `stop()` effacent l'échéance (l'utilisateur reprend la main). La durée
est donc liée à l'horloge de la machine — au moins `t` secondes, davantage en onglet
d'arrière-plan, où les images d'animation se raréfient — mais la **trajectoire des états** reste
reproductible dès que le déclencheur l'est (`step == 10`).

`Continue()` n'est pas une action du langage : elle est refusée comme n'importe quel nom inconnu
(« Unknown action 'Continue' », avec la liste des actions disponibles) et la pause ne se lève que
depuis l'interface — ou seule, pour un `Pause(t)`.

Un `Pause()` ou un `Pause(t)` émis par un modificateur est **signalé à l'utilisateur** :
`Simulator.modifierPauseCount`
compte les pauses demandées par un modificateur (remis à zéro par `consumeModifierPauseRequest()`,
appelé par la vue, et par `reset()`), et `SimulationRunController` affiche alors une notification
Bulma `notification is-warning` non bloquante et refermable, insérée en haut de la zone de
simulation. Le texte dépend de la pause : « Le modèle a mis la simulation en pause pendant 2.5 s
(modificateur). La course reprend seule à la fin de la pause. » pour un `Pause(t)`
(`formatPauseSeconds()` écrit la durée : `2`, `2.5`, `0.25`), le message « … en pause
(modificateur). Reprenez avec le bouton Run ou avancez pas à pas. » pour un `Pause()`. Le bandeau
est retiré au démarrage suivant de la course, à l'arrêt, et à la **reprise automatique** d'une
pause minutée.

Côté web, `SimulationRunController.applyControlRequests()` applique le `LoopUpdate` : il resynchronise
les radios d'activation et rappelle `onReset` après un rembobinage, et il suit `running`. Les pas
demandés s'enchaînent sans attendre la limite de fps (`loop.remainingSteps > 0` lève la condition
de cadence dans `animationCallback`). Une pause minutée ne passe pas par `running` : la boucle reste
vivante, donc la vue continue de demander des images ; `animationCallback` interroge
`loop.isTimedPaused` / `loop.resumeIfDue()` avant la cadence et n'exécute un pas que si
`loop.canStep`. L'observable `running` n'appelle `loop.start()` que si la boucle ne tourne pas déjà
(`if (!loop.running)`), sinon le simple miroir de l'état de la boucle lèverait la pause qu'elle vient
de poser.

Tests : `core/src/commonTest/.../SimulationLoopTest.kt` (un pas puis pause, N pas puis arrêt,
`N <= 0` ignoré, pause signalée par `modifierPauseCount` puis consommée, rewind sans relance,
restart avec relance, aucune commande de grille en file ; `Pause(2)` avec une horloge simulée :
aucun pas avant 1999 ms, reprise seule à 2000 ms, grille inchangée pendant la pause, `Pause()`
indéfinie qui ne reprend jamais, durée `<= 0` ignorée à l'exécution) et
`ActionTest.testTheLoopControlActionsParse` / `testATimedPauseTakesADurationInSeconds` /
`testContinueIsAnUnknownAction` / `testPauseIsCapitalisedAndTheLowercaseFormIsAnAlias` pour le
parseur.

---

### 6.5ter Géométrie des formes : rectangle et cercle (`shapes.kt`)

Les formes ne sont plus énumérées cellule par cellule dans la boîte englobante : deux fonctions
**pures** de `core/.../model/shapes.kt` les décrivent **par lignes** (portées de colonnes) :

| Fonction | Rôle |
|---|---|
| `CellRow(row, from, to)` | une portée : les cellules `from..to` de la ligne `row`, bornes incluses, dans l'ordre |
| `rectangleRows(x1, y1, x2, y2, fill = true)` | une portée par ligne (plein), ou les deux portées de bord de chaque ligne (contour) |
| `circleRows(cx, cy, radius, fill = true)` | une portée par ligne du disque, ou `portée(r) − portée(r − 1)` sur chaque ligne (contour) |
| `List<CellRow>.forEachCell(width, height, block)` | parcourt les cellules des portées **dans la grille** (celles du dehors sont sautées) |

Le **contour est défini à partir du plein**, sans seconde formule de la forme : le rectangle est le
plein des mêmes coins moins le plein de `(left + 1, top + 1)-(right - 1, bottom - 1)` ; le cercle est
le plein du rayon `r` moins le plein du rayon `r − 1`. Une ligne intermédiaire du rectangle émet deux
portées (une par côté), une ligne du haut ou du bas une seule, entière ; une ligne du disque émet la
portée extérieure et la portée intérieure quand elle existe, sinon la portée extérieure entière (les
calottes et le rayon 0). La racine carrée entière (`integerSquareRoot`) évite qu'un arrondi flottant
ouvre un trou : `dx² + dy² <= r²` est décidé en arithmétique entière.

Le noyau (`Simulator.forEachShapeCell`) et la vue (`SimulatorViewController.forEachShapeCell`) ne
font que convertir ces portées — en index de case pour l'un, en coordonnées de dessin pour l'autre
— de sorte qu'il n'existe **qu'une seule** définition du disque dans l'application. La vue replie une
cellule qui déborde de la grille sur le côté opposé quand l'interrupteur « Torique ? » est actif et
la perd sinon, comme un collage. Tests : `ShapeRowsTest.kt` (plein/contour, petits et grands,
bords de grille, contour = plein − intérieur) et `ShapeActionsTest.kt` (parseur, exécution, alias).

---

### 6.6 Persistance : contrat de sauvegarde et compatibilité

La sauvegarde (`.netbiodyn`) sérialise le **modèle**, indépendamment du mode d'édition : tout ce qui est saisi en mode normal (prédicat d'âge, seuils de champs, nb voisins avec grain, influences, héritage d'âge, réactions, `toric`, `boundaryConditions`) et en mode expert (formule de prédicat, liste d'actions) est écrit et relu à l'identique.

- Couverture vérifiée par `core/src/commonTest/.../PersistenceTest.kt` : aller-retour d'un modèle complet (toutes les clés ci-dessus, `updatePlan` séquentiel, `settings`), avec ré-encodage identique au bit près, puis contrôle valeur par valeur ; second test : un fichier « ancien » (sans `formula`, `actions`, `fieldPredicates`, `agentCountPredicates`, `boundaryConditions`, `toric`) se charge avec les valeurs par défaut documentées et se ré-enregistre.
- Valeurs par défaut **écrites explicitement** quand elles sont significatives : `sourceReactive = 0` (héritage d'âge), `reaction[].sourceReactive = 0`, `agentCountPredicates[].grainId = 0`, seuils de champs `>= 0` (l'objet `Predicate` n'a pas de valeur par défaut).
- `agePredicates` (lignes « Âge de la simulation », omises si la liste est vide) : le prédicat unique historique `agePredicate` reste écrit et évalué ; l'éditeur le replie dans la liste à l'ouverture (`migrateLegacyAgePredicate`), donc un ancien fichier continue de fonctionner et se normalise dès la première édition.
- Compatibilité : une ancienne simulation reste chargeable par cette version. En revanche, un fichier produit ici n'est pas lisible par `centyllion-app` d'origine : son `Json` n'active pas `ignoreUnknownKeys` et rejette donc les clés ajoutées (`formula`, `actions`, `fieldPredicates`, `agentCountPredicates`, `toric`, `boundaryConditions`, `sourceReactive` de réaction). NISMA, lui, ignore les clés inconnues et préserve désormais toutes ces clés lors de ses propres sauvegardes.

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
- `grainId < 0` compte **tous les agents** ; `grainId >= 0` ne compte que ce grain (équivalent de `count()` / `count(grainId)` dans une formule de prédicat)
- Évalué dans `Behaviour.applicable()` via `neighbours.count { it.second.reactiveId == p.grainId }`

---

## 7. Interface utilisateur (Compose Multiplatform)

### 7.1 Couche commune (`compose:shared` / `ui/`)  <!-- module compose supprimé : version bureau retirée -->

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
| `ShowPage.kt` | Page principale : en-tête (nom, boutons nouvelle/export/import/tutoriel, mode expert), bouton maison du dossier de travail dans la rangée du bouton « Informations sur le modèle », contrôle du modèle, sauvegarde en localStorage, gestion des tutoriels. |
| `ModelController.kt` | Contrôleur de la page modèle : liste grains/comportements/champs (chaque section se replie indépendamment via son chevron), recherche, édition, graphiques. |
| `SimulationRunController.kt` | Contrôleur de la simulation : lecture complète, lecture, pas, settings, répercussion sur les charts. |
| `PlotterController.kt` | Graphique via **data2viz** (LinePlotter) + Legende; `push(x, ys)`, `reset()`. |
| `BulmaPage.kt` | Interface commune pour les écrans Bulma (modal, notification, erreur). |
| `capture/WorkFolder.kt` / `capture/WorkFolderController.kt` | Dossier de travail des captures : base IndexedDB propre (`centyllion-home`/`handles`/`home`, ancienne clé `workFolder` de `centyllion-files` supprimée sans migration), picker (`startIn` sur le dossier courant), permission (`queryPermission`/`requestPermission`), écriture dans `<dossier>/captures/<modèle>/` ou téléchargement ; bouton maison plein (`fa-home`) dans la rangée du bouton « Informations sur le modèle » : magenta (`ElementColor.Danger`, `#d24193`) quand aucun dossier n'est défini, bleu (`ElementColor.Primary`, `#9595f8`) quand il l'est, même bleu atténué quand la permission doit être renouvelée ; infobulle `title`/`aria-label` portant l'état, aucune note adjacente. Aucune image en stockage navigateur. |
| `TutorialLayer.kt` | Tour du tutorial (étapes, validation, navigation, conclusion) ; supprime ses widgets au redémarrage. |
| `BacteriasTutorial.kt` / `FieldTutorial.kt` | Tutoriels débutants pas-à-pas (bactéries, champs). |
| `GameOfLifeTutorial.kt` / `ActionsTutorial.kt` / `FieldFormulaTutorial.kt` | Tutoriels experts : `count(0)`, actions + `age(1)`, formule de champ animée. |
| `formulas.kt` | Vérifications sémantiques des formules saisies dans les tutoriels experts. |

### 8.3 Contrôleurs de l'éditeur (`controller/model/`)

Chaque contrôleur (`*.kt`) est une classe descendante d'un contrôleur de base, et gère un élément UI bulma (input, button, slider, etc.) généré à partir d'un élément du modèle. Ils utilisent le pattern : "observer vers `data` (valeur du modèle)", "modifier `data` via `onUpdate`".

- `ModelController.kt` : racine de la page modèle ; orchestre les contrôleurs enfants ; gère la recherche (`SearchController`), l'export CSV, les graphiques, et le repli indépendant des trois sections (`sectionHeader`) — ajouter un élément déplie sa section.
- `SimulationRunController.kt` : contrôle la lecture complète (play/stop, pas, slider de vitesse, settings), appelle `simulator.oneStep()` et met à jour les graphs.
- `GrainEditController.kt`, `FieldEditController.kt`, `BehaviourEditController.kt` : édition détaillée des grains, champs et comportements.
- `FieldSelectController.kt`, `FieldChangeController.kt`, `FieldDisplayController.kt` : sélection/affichage/suppression de champs.
- `GrainSelectController.kt`, `GrainDisplayController.kt` : sélection/affichage de grains.
- `BehaviourDisplayController.kt`, `ReactionEditController.kt`, `FieldPredicateController.kt`, `FloatPredicateController.kt`, `IntPredicateController.kt`, `SourceReactiveSelectController.kt`, `DirectionController.kt`, `ColorSelectController.kt`, `IconSelectController.kt`, `SimulationSettingsController.kt`, `Simulator3dViewController.kt` : sous-composants détaillés.
- `ModelSearchController.kt` : recherche de modèles sur le serveur.
- `SearchController.kt`, `UndoRedoSupport.kt`, `stringControllers.kt` (utils).

### 8.3bis Éditeur de comportement : modes normal et expert

`BehaviourEditController` présente trois blocs (Information / Conditions / Réactions) séparés par des lignes bleues, avec titres centrés en rouge foncé. Le bloc du milieu est intitulé **Conditions** (clé i18n `Predicates`, renommée sans changer la clé) et contient les prédicats graphiques ou la formule, puis les quatre interrupteurs de **Conditions aux limites** du voisinage.

| Élément | Mode normal | Mode expert |
|---|---|---|
| Nom, description | oui | oui |
| Prédicat graphique d'âge (« Âge de la simulation », plusieurs lignes via +) | oui | — |
| Prédicat graphique « Seuils des champs » (si au moins un champ existe) | oui | — |
| Prédicat graphique « Nb voisins » (grain + relation + valeur 0..8) | oui | — |
| Formule de prédicat (+ aide) | — | oui |
| Conditions aux limites du comportement (4 interrupteurs, mêmes libellés que dans l'éditeur de grain) | oui | oui |
| Vitesse | oui | oui |
| Influences des champs (si au moins un champ existe) | oui | oui |
| Réactions + colonne « Avec l'âge de » (héritage d'âge) | oui | oui |
| Formule d'actions (+ aide) | — | oui |

- Les trois prédicats graphiques et la formule sont combinés en **ET** dans `applicable()`.
- Le mode expert n'est imposé (`ModelAndSimulation.expert`) que si un comportement utilise une **formule de prédicat** ou une **liste d'actions** non vide : ces deux champs ne sont éditables qu'en mode expert. Les champs (création, seuils, influences), les prédicats graphiques et l'héritage d'âge sont accessibles dans les deux modes, donc la création d'un champ ne bascule plus en mode expert.
- Sélecteur de grain du prédicat « Nb voisins » : « Tous les agents » (`grainId = -1`) ou un grain précis (équivalent de `count(grainId)`).
- Chaque groupe de prédicats graphiques a un bouton **Aide** qui ouvre une aide contextuelle (sémantique du prédicat, règle du ET, exemples) ; l'aide de la formule de prédicat rappelle que les prédicats graphiques sont aussi évalués (ET).
- Le graphique des champs et l'export CSV des champs (`fields.csv`) sont disponibles dans **les deux modes** ; ils n'apparaissent que si le modèle contient au moins un champ (le graphique des agents occupe alors 40 % de la hauteur, 80 % sinon).

### 8.4 Graphiques (Plotter) et vue de simulation 2D

- **data2viz** (`plotter/`) : tracé des courbes (nombre de grains, quantité de champs) dans `Plotter` (Canvas Skia) avec axes, ticks et courbes coloriées.
- **Vue de simulation** (`controller/model/SimulatorViewController.kt`) : vue de dessus en canvas 2D, **sans 3D ni éclairage**. Un buffer hors écran de la taille de la grille (1 pixel = 1 cellule) reçoit les agents (couleur du grain, aplats stricts) puis la couche des champs (alpha identique à l'ancienne texture 3D) ; il est ensuite agrandi en `imageSmoothingEnabled = false` dans le canvas affiché (backing store en pixels de périphérique, `devicePixelRatio`), sur lequel sont tracés la grille et l'aperçu du pinceau. Outils : stylo, ligne, **rectangle**, **cercle**, spray, gomme, déplacement (panoramique) ; le rectangle et le cercle sont ancrés sur la cellule d'appui et tracés au relâchement, le rayon du cercle étant la distance au point d'appui. L'interrupteur **« Fill ? » / « Remplir ? »**, à côté des outils et dans le style de « Torique ? » et « Écraser ? », décide pour les deux formes entre plein et contour ; leurs cellules viennent des fonctions pures du noyau (`rectangleRows`, `circleRows`, `shapes.kt`), et non d'un tracé propre à la vue, avec repli torique des cellules qui débordent quand « Torique ? » est actif. Zoom molette ×0,25 à ×16 ; bouton plein écran ; vignettes/captures par `canvas.toDataURL` / `toBlob`. `captureJpeg()` fournit l'image de la méta-action `Capture()` (JPEG 0,92, `toDataURL` synchrone décodé en `Blob`, pour lire le canevas avant le pas suivant). Les grains masqués (case à cocher) et les champs masqués sont rendus en couleur de fond. Les clés `simulation.assets` et `settings.gridTextureUrl` restent dans le modèle (compatibilité) mais ne sont plus rendues.
- Coût mesuré sur `coeur` (8 694 agents, grille 100×100) : ~0,5 ms par repeint, contre ~31 ms par rendu de scène Babylon et 17 404 maillages ; débit en lecture ≈ 7 pas/s contre ≈ 1,8 pas/s (même modèle, même machine). Bundle JS : 806 Ko contre 3,5 Mo.

### 8.5 Galerie personnelle : disque et repli navigateur

La galerie personnelle (les modèles ajoutés par le visiteur, par opposition à la galerie
distribuée `Gallery/gallery.json`, en lecture seule) a **deux emplacements possibles, un seul
actif à la fois**. Le choix n'est pas un réglage : il suit l'état du **dossier de travail**
(`WorkFolder`, `web/.../client/capture/`), et la fenêtre de la galerie annonce toujours l'emplacement
actif — chemin du dossier ou « navigateur ».

| état du dossier de travail | emplacement de la galerie |
|---|---|
| défini et permission accordée (`WorkFolderState.DEFINED`) | `<dossier de travail>/galerie/` (disque) |
| non défini, oublié, permission à renouveler, navigateur sans File System Access | IndexedDB `centyllion-gallery` v1, magasin `entries` (repli) |

**Format sur le disque** (`core/src/commonMain/kotlin/com/centyllion/model/gallery/GalleryDisk.kt`) :
`galerie/galerie.json` porte les métadonnées (`id`, `title`, `description`, `image` = nom du
fichier image, `date`, plus `file`/`model`/`hidden` comme l'export) et chaque image est un fichier
JPEG séparé du même dossier, nommé `image-<id>.jpg` par `galleryImageFileName()` (id nettoyé avec
les règles de `sanitizeCaptureName()` : `/ \ : * ? " < > |` → `_`, bords nettoyés, longueur bornée,
préfixe `_` pour un nom de périphérique Windows, repli `entry` pour un id vide ; deux id qui se
nettoient pareil reçoivent `-2`, `-3`…). Le JSON est celui de l'export : `version: 1`,
`exportedAt`, clé `models` — donc un dossier `galerie/` est directement réutilisable et importable,
et **aucune image n'est écrite en base64** dedans. `toDisk()` produit le JSON + la liste des
fichiers, `fromDisk()` / `withDataUrls()` refont les data URLs, `decodeImageDataUrl()` /
`encodeImageDataUrl()` font l'aller-retour base64.

**Lecture et écriture** (`web/src/jsMain/kotlin/com/centyllion/client/gallery/GalleryDisk.kt`,
`GalleryDiskStorage`) : lecture au chargement de la galerie, disque si `WorkFolder.isFolderReady`
sinon IndexedDB. À chaque écriture, `galerie.json` est **relu juste avant** puis fusionné par id
(`mergeById(disk, mine, removed)`) : dernier écrivain gagnant par entrée, entrées absentes du
fichier conservées, ids explicitement supprimés retirés des deux côtés. Suppression d'une entrée :
confirmation, puis `removePictures()` efface **uniquement** le fichier nommé par cette entrée (et
seulement s'il est bien `galleryImageFileName(id)` et qu'aucune entrée restante ne le référence).
`galerie.json` absent → fichier neuf ; illisible (`decodeDisk` → `GalleryDiskRead.Unreadable`) →
copie datée `galerie.bak-AAAAMMJJ-HHMMSS.json`, puis fichier neuf, et message à l'utilisateur —
jamais d'écrasement direct.

**Premier usage du disque** : si la galerie du disque est absente ou vide et qu'IndexedDB contient
des entrées, `seedIfEmpty()` les copie une fois (message affiché). IndexedDB n'est **jamais vidé** ;
il est en revanche tenu à jour après chaque écriture sur le disque (`GalleryStore.save` en
arrière-plan), pour que le repli soit à jour si le dossier disparaît.

**Fichiers** : `gallery/Gallery.kt` (typealiases `GalleryEntry`/`GalleryIndex`/`GalleryExport` vers
le core, `GalleryStore` : IndexedDB, export/import, fusion avec l'index distribué),
`gallery/GalleryDisk.kt` (emplacement, lecture/écriture disque, JS `galleryFiles` : `readText`,
`writeText`, `writeBytes`, `copyText`, `remove`, `readDataUrls`), `gallery/GalleryController.kt`
(fenêtre, ligne d'emplacement, aide « Où est ma galerie ? », formulaire, suppression).
`WorkFolder` expose `directoryHandle`, `isFolderReady` et `whenReady()` (restauration unique du
handle, partagée entre le bouton maison et la galerie). Tests : `core/src/commonTest/.../gallery/GalleryDiskTest.kt`
— conversion data URL ↔ fichier, absence de base64 dans le JSON, nom de fichier dérivé et assaini,
fusion par id, JSON illisible, nom daté ; aucun test ne dépend d'un navigateur (`:core:jvmTest`).

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
- **Kotlin** : `2.1.0`, multi-platform (jvm, js, wasm via composeShared).  <!-- module compose supprimé : version bureau retirée -->
- **Compose** : `1.7.1` (Compose Multiplatform) + compilateur `compose-compiler` (kotlin 2.1).  <!-- module compose supprimé : version bureau retirée -->
- **SQLDelight** : `2.0.1` (dialect postgresql).
- **Ktor** : `2.3.6` (server core, cio, content-negotiation, serialization, cors, calllog).
- **Bulma-kotlin** : `0.5` (UI).

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
./gradlew :compose:shared:compileKotlinJvm  <!-- module compose supprimé : version bureau retirée -->
./gradlew :compose:shared:compileKotlinWasmJs  <!-- module compose supprimé : version bureau retirée -->
./gradlew :db:build

# Exécuter le serveur (port 4545 par défaut)
./gradlew :db:run --args="--server-port=4545"

# Docker (db/)
./gradlew :db:copyForDocker
```

### Build standalone (sans serveur)

```bash
# ── Application desktop autonome (JRE incluse, ~180 Mo) ──
./gradlew :compose:shared:createDistributable  <!-- module compose supprimé : version bureau retirée -->
# Sortie : compose/shared/build/compose/binaries/main/app/Centyllion/  <!-- module compose supprimé : version bureau retirée -->
# Lancement (headless, rendu logiciel) :
JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE" \
  xvfb-run -a ./compose/shared/build/compose/binaries/main/app/Centyllion/bin/Centyllion  <!-- module compose supprimé : version bureau retirée -->

# ── Site web statique ──
./gradlew :web:jsBrowserProductionWebpack :web:syncJs
# Bundle : web/webroot/js/centyllion/centyllion.js
# Service :
python3 -m http.server 8081 --directory web/webroot
# → http://localhost:8081
# Variante HTTPS (certificat auto-signé dans web/certs/, ignoré par git) :
# python3 serve_https.py 8081
```

### Format de fichier et contrat de sauvegarde

Le fichier `.netbiodyn` est le JSON de `ModelAndSimulation`
(`{"model": …, "simulation": …}`), sérialisé par kotlinx.serialization avec
`encodeDefaults = false` (défaut) : une valeur égale à sa valeur par défaut
est omise, ce qui rend les fichiers compacts et lisibles à l'œil.

Quatre champs obéissent toutefois à un contrat explicite (2026-11-12) pour
que les fichiers sauvegardés soient **autodescriptifs** :

| Champ | Contrainte d'écriture | Contrainte de lecture |
|---|---|---|
| `grains[].boundaryConditions` | omis si le grain est torique partout ; sinon **les 4 parois `left/right/top/bottom` sont toujours écrites** (`@EncodeDefault(ALWAYS)`) | parois absentes = `true` (les fichiers anciens partiels, ex. `{"left":false}`, restent lisibles) |
| `behaviours[].boundaryConditions` | même contrat que pour les grains (même classe) : omis si les 4 parois sont toriques, sinon les 4 parois explicites | clé absente = torique partout (un comportement d'un fichier ancien se relit tout torique) |
| `fields[].toric` | omis si `true` ; `false` est écrit | absent = `true` (torique) |
| `behaviours[].agentCountPredicates` | omis si liste vide ; chaque prédicat présent **écrit toujours** `grainId`, `op` et `count` (`@EncodeDefault(ALWAYS)`), même aux valeurs par défaut | liste absente = vide |
| `behaviours[].reaction[].sourceReactive` | écrite si ≥ 0, omise si < 0 (convention antérieure) | absente = -1 |

Exemple de grain non torique sauvegardé :

```jsonc
"boundaryConditions": { "left": false, "right": true, "top": true, "bottom": true }
```

et d'un comportement avec un prédicat de count :

```jsonc
"agentCountPredicates": [ { "grainId": -1, "op": "GreaterThan", "count": 3 } ]
```

Le projet **NISMA** (C#/.NET) est aligné sur ce contrat : mêmes classes
(`BoundaryConditions`, `AgentCountPredicate`, `Field.Toric`), mêmes règles
d'omission dans `JsonUtils.SaveNbd`, et lecture rétrocompatible des fichiers
partiels. Voir `NISMA/PROJECT_DOCUMENTATION.md` §2.4 et `NISMA/README.md`
§4.1ter. Tests dédiés : `core/src/commonTest/.../SerializationTest.kt`
(7 tests de (dé)sérialisation).

### Sauvegarde de fichiers (web)

L'API `showSaveFilePicker` (File System Access) n'existe que dans les navigateurs
Chromium (Chrome, Chromium, Edge) et uniquement dans un contexte sécurisé
(HTTPS ou `localhost`). Le bouton **Save As** utilise donc cette cascade :

1. `showSaveFilePicker` si disponible (navigateur Chromium + contexte sécurisé) ;
2. sinon, le **pont local** `save_bridge.py` : le web envoie le contenu du
   fichier en `POST http://127.0.0.1:8090/save?name=<nom>` (CORS ouvert,
   écoute sur `127.0.0.1` uniquement) ; le processus local ouvre la boîte de
   dialogue native tkinter et écrit le fichier au chemin choisi. Fonctionne
   dans **tous les navigateurs (Firefox compris)**, à condition que la page
   soit servie en **HTTP** (mixed-content : une page HTTPS ne peut pas
   fetcher `http://127.0.0.1`) ;
3. sinon, téléchargement simple via `<a download>`.

Mode headless du pont : `SAVE_BRIDGE_AUTO=<répertoire> python3 save_bridge.py`
(écrit directement dans le répertoire, sans boîte de dialogue).

> **Recommandation** : Chrome/Chromium/Edge sur `http://localhost:8081`
> (aucun assistant nécessaire) ; Firefox ou accès par IP →
> `python3 save_bridge.py` sur la machine du navigateur.

> **Mise en garde — Opera** : sous Opera, les graphiques (page Simulation)
> peuvent présenter un artefact de double dessin : l'ensemble de la scène
> (labels X/Y, courbes) est rendu deux fois avec un décalage de 1–3 px, plus
> des segments écho détachés au bord droit. Vérifié que ce n'est pas une
> régression de notre code : bundle à jour (marqueur détectable par
> `fetch('js/centyllion/centyllion.js').then(r=>r.text()).then(t=>console.log(t.indexOf('127.0.0.1:8090/save')>=0))`),
> 3 canvas dans le DOM (un seul renderer par canvas visible), reproductible
> uniquement sous Opera. Chromium et Firefox sont propres (y compris en mode
> Play à pleine vitesse). L'artefact pointe vers le compositeur d'Opera.
> Utiliser un autre navigateur pour un affichage fiable.

### Environnement headless (conteneur)

Pour exécuter l'application desktop dans un conteneur sans écran :

```bash
# Prérequis
apt-get install -y xvfb libxtst6

# Lancement avec rendu logiciel (Skiko ne peut pas créer de contexte GL sans GPU)
JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE" xvfb-run -a ./gradlew :compose:shared:run  <!-- module compose supprimé : version bureau retirée -->
```

| Variable / flag | Rôle |
|---|---|
| `xvfb-run -a` | Crée un serveur X virtuel éphémère |
| `-Dskiko.renderApi=SOFTWARE` | Force le rendu logiciel Skia (pas de GPU nécessaire) |

> La compilation complète nécessite le référentiel GitHub (`maven.pkg.github.com/centyllion/...`) pour bulma-kotlin, avec les credentials `grpUser`/`grpToken` (définis dans les variables de projet local, ou dans les fichiers de configuration métier).

---

## 11. Déploiement

### 11.1 Web (site en ligne)

Le site `www.centyllion.com` sert :
1. `webroot/index.html` (charge le CSS, Font Awesome, et `centyllion.js`).
2. `webroot/js/centyllion/centyllion.js` (généré par Gradle : `syncJs` + `generateVersion`).
3. Le serveur `db/` (Ktor) qui expose l'API REST.
4. La base PostgreSQL hébergée (cf. `db/docker/` Dockerfile + `db-*.tar` pour l'image).

### 11.2 Bureau (Compose)

`compose:shared` cible `jvm`, avec `mainClass = com.centyllion.ui.NetbiodynKt` ; le build multiplateforme produit des packages `.dmg`, `.msi`, `.deb` (nativeDistributions).  <!-- module compose supprimé : version bureau retirée -->

### 11.3 Web Assembly (WASM)

`compose:shared` cible `wasmJs` avec `src/wasmJsMain` : `index.html`, `styles.css`, `com.centyllion.ui.main` (UI Compose, exécuté dans le navigateur WASM).  <!-- module compose supprimé : version bureau retirée -->

---

## 12. Tutoriels (en ligne)

Le bouton **Tutorial** ouvre une boîte de dialogue contenant une **liste déroulante native** (`<select>`, « Choisir un tutoriel ») : les noms des tutoriels sont longs et la liste est dessinée par le navigateur, donc jamais tronquée ni coupée par la fenêtre. Le corps de la boîte a une hauteur minimale de 200 px. La liste propose cinq tutoriels, en deux niveaux ; choisir une entrée ferme la boîte et ouvre l'introduction du tutoriel. Ils sont aussi enchaînés : la conclusion d'un tutoriel propose le suivant (`next`).

**Débutant**

- **BacteriasTutorial** : crée une bactérie, la déplace, ajoute une division (comportement) pour faire croître une colonie.
- **FieldTutorial** : ajoute un champ sucre, un grain source, un impact d'attraction et un seuil.

**Expert** (l'agent et la structure sont créés par `prepare()`, l'utilisateur écrit les formules)

- **GameOfLifeTutorial** : le jeu de la vie de Conway avec `count(0)` — survie (`count(0) == 2 || count(0) == 3`), mort (`count(0) < 2 || count(0) > 3`, produit principal « aucun ») et naissance par génération spontanée (réactif principal « aucun », `count(0) == 3`). Recette vérifiée par `GameOfLifeTest` (clignotant de période 2, bloc stable, planeur translaté d'une case en 4 pas).
- **ActionsTutorial** : un agent mobile laisse une piste (`Add(1, 0, 1)`) et la miette disparaît au bout de six pas (`age(1) > 5`, produit « aucun »). Vérifié par `ExpertRecipesTest`.
- **FieldFormulaTutorial** : un champ piloté par `0.5 + 0.5 * sin(x / 10 + step / 5)` — gradient qui varie en espace et en temps. Vérifié par `ExpertRecipesTest`.

Chaque étape est une classe `TutorialStep` avec `title`, `content`, `selector` (éléments DOM à attendre), `validated` (condition booléenne) et `nextCallback` (action). `TutorialLayer` orchestre le succès et la navigation et supprime ses widgets avant un redémarrage (`next` ou nouveau choix dans le menu).

Les étapes des tutoriels experts ne comparent pas le texte tapé : `formulas.kt` (module web) évalue la formule avec un `NetbioDynEvaluator` autonome et vérifie sa **sémantique** (`matchesCountRule` : vraie pour 2 et 3 voisins, fausse pour 0, 1, 4, 5, 8 ; `matchesAgeRule` ; `actionAddsGrain` ; `isMovingGradient` : la valeur du champ doit varier à la fois en x et avec `step`). Toute formulation équivalente est donc acceptée.

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
- `GameOfLifeTest` : recettes du jeu de la vie (clignotant, bloc, planeur, formule sans naissance)
- `ExpertRecipesTest` : piste de miettes qui s'efface (`Add(1, 0, 1)` + `age(1) > 5`) et gradient de champ animé

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
- **Ajout de voie de rendu** : graphiques data2viz, vue canvas 2D, etc.

### 14.5 Tests

- Ajouter des tests dans `core/src/commonTest/kotlin/com/centyllion/model/` pour valider le nouveau comportement.

---

## 15. Fiches de lecture rapides

- **Lancer le site en local** : `./gradlew :db:run` + ouvrir `webroot/index.html` (avec le serveur cours ou un proxy).
- **Lancer l'interface bureau** : `./gradlew :compose:shared:jvmExec` (si configuré) ou build jar.  <!-- module compose supprimé : version bureau retirée -->
- **Structure du modèle** : `Grain` + `Behaviour` + `Field` → `GrainModel` → `Simulation` → `Simulator`.
- **Simuler** : `simulator.oneStep()` → `step++` → historiques mis à jour.
- **Exporter** : `db/Export.kt` → JSON `{"model":..., "simulation":...}`.
- **API** : `/simulation/{id}` (JSON), `/search/{query}`, `/asset/{id}...`.

---

## 16. Historique Git (extrait)

Derniers commits (extension CentyllionExtend) : `223850fb Add boundary conditions: per-grain wall authorisation and per-field toric flag`, `51c2d113 Rework behaviour editor: 3 blocks with blue separators, collapsible Predicates/Actions, age + Nb Neighbours graphical predicates in normal mode, formulas in expert mode, any-agent count predicate semantics`, `67242047 Add Opera warning to web docs (chart double-draw artifact)`, `91b1372a Fix doubled chart axes, continuous sliding window, cross-browser save bridge`, `3b809be5 Add quick Save (same file, no dialog) and Save As (picker) with remembered file name and handle; document reactions/actions order in help`, `3b1a1a9c Save through the native file dialog; remember the project file name across saves and reloads`, puis l'historique du dépôt monté : `unwanted tests removed`, `Adds model name edition in showpage...`, `Allows simulation of 200`, `Support wasm browser`, `Prepare compose multiplatform`, `Add formula performance test`, `Center direction`, `Replace kotlinx-uuid by benasher44`, `Correct pointer color change`, `Age predicate and source reactive are for expert mode`, `Adds Compose version`, `Updates to kotlin 2`, `Add Compose version`, `Adds grid for behaviour`, `Adds square for directions`, `Reworked properties title and combo`, `Removes split-panes`, `Important re-layout`, `Adds compose UI`, `Update field tutorial`, `Support zip assets`, `Adds examples tab...`, `Adds support for asset in server and use of thumbnail`, `Adds validation for fields`, `Adds search query`, `Add validation for fields`, `Test docker`, `Adds native image`, `Update translation`, `Add formula performance test`, `Update kotlin`, `Prevent chart resize if result is < 0`, `Correct still drawing even getting outside plane`, `Working on Compose version`, `Allows simulation of 200`, `Use value as key for remember in text field`, `Cleaning`, `Corrects missing value error`, `Support wasm browser`, `Adds model name edition...`, `Allows simulation of 200`.  <!-- module compose supprimé : version bureau retirée -->

Branches distantes : `main`, `compose`, `master`, `purehtml`, `share`. Dépôt upstream : `github.com/jeancharles-roger/centyllion-app`.  <!-- module compose supprimé : version bureau retirée -->

---

*Document généré à partir de l'inspection du code source du projet Centyllion (`Prog/Simulateurs/NetBioDyn_Simulators/centyllion-app`). Aucune modification n'a été apportée au dépôt.*
