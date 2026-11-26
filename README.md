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

> **Note** : le build nécessite le dépôt GitHub (`maven.pkg.github.com/centyllion/...`) pour `bulma-kotlin`. Les credentials `grpUser`/`grpToken` doivent être définis (fichier `local.properties` ou variables de projet).

---

## 3. Exécution

### 3.1 Version bureau — supprimée

La version de bureau (module `compose`) a été retirée du dépôt : elle n'est plus
maintenue et seuls les fichiers de la version web sont conservés. Le bouton Aide de
la version web sert `web/webroot/help.html`, qui vit désormais dans le dépôt web.

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
- Variante HTTPS (certificat auto-signé, généré dans `web/certs/`, ignoré par git) :
  `python3 serve_https.py 8081`

#### Sauvegarde de fichiers et recommandation de navigateur

Le bouton **Save As** essaie, dans cet ordre :

1. **Explorateur natif du navigateur** (API File System Access) — disponible dans
   **Chrome, Chromium ou Edge**, et uniquement sur une page sécurisée
   (HTTPS ou `localhost`).
2. **Pont local** (`save_bridge.py`) — ouvre la boîte de dialogue de
   l'explorateur de fichiers de l'OS depuis un processus local : fonctionne
   **dans tous les navigateurs, Firefox compris**, à condition que
   l'application soit chargée en **HTTP** (une page HTTPS ne peut pas appeler
   `127.0.0.1`, blocage de mixed-content) et que l'assistant tourne sur la
   machine où se trouve le navigateur.
3. **Téléchargement** simple (le fichier atterrit dans le dossier de
   téléchargements, sans explorateur).

**Assistant local** (optionnel) :

```bash
python3 save_bridge.py            # écoute sur 127.0.0.1:8090 (port par défaut)
```

- Nécessite Python 3 avec tkinter (standard sous Windows et macOS ;
  `sudo apt install python3-tk` sous Debian/Ubuntu).
- Chaque enregistrement est confirmé par la boîte de dialogue ; le serveur
  n'écoute que sur `127.0.0.1` (inaccessible depuis le réseau).

**Recommandation** : utilisez **Chrome, Chromium ou Edge** si possible — sur
`http://localhost:8081` l'explorateur natif fonctionne sans aucun assistant.
Sous Firefox, ou en accédant à l'application via l'IP de la machine, lancez le
pont local (`python3 save_bridge.py`) pour conserver un véritable explorateur
de fichiers.

#### Captures (`Capture()`) et dossier de travail

La méta-action `Capture()` demande une image de la vue de simulation ; le fichier
s'appelle `NOM_MODELE__PAS.jpg` (ex. `GoL_Modifiers__12.jpg`). Le **format** (JPEG
qualité 0,95 par défaut, ou PNG sans perte), la **qualité** et la **résolution** de
cette image se règlent dans l'onglet **Enregistrements** des paramètres (§4.5bis et
aide §9.6) ; en mode « taille de la grille », l'image fait `largeur × N` par
`hauteur × N` pixels (N = 1 à 8, défaut 4), quelle que soit la fenêtre, et le
fichier porte l'extension `.png` quand le format est PNG.
Le **bouton maison** de la rangée du bouton « Informations sur le modèle »
— icône `fa-home` pleine, infobulle et `aria-label` portant l'état (« Dossier de
travail — Défini », « Dossier de travail — Non défini », « Dossier de travail —
Permission à renouveler ») — laisse choisir un dossier du disque, redonner la
permission ou oublier le dossier ; le sélecteur s'ouvre dans le dossier de travail
courant. Les images vont alors dans `captures/NOM_MODELE/NOM_MODELE__PAS.jpg`
(`MonDossier/captures/GoL_Modifiers/GoL_Modifiers__12.jpg`), les deux niveaux étant
créés au besoin. Le bouton est **magenta plein** (`#d24193`, la couleur danger du
thème) quand aucun dossier n'est défini, **bleu plein** (`#9595f8`, le bleu des
boutons `+` du modèle) quand le dossier est défini et accessible, et **même bleu
atténué** quand la permission doit être renouvelée.

Le dossier est mémorisé **à part de tout le reste** : son `handle` vit dans sa
propre base IndexedDB (`centyllion-home` v1, magasin `handles`, clé `home`), qui ne
partage rien avec la galerie (`centyllion-gallery`) ni avec les handles du dialogue
d'enregistrement (`centyllion-files`) ; `localStorage` ne garde que deux chaînes
(nom affiché, mode de repli). L'ancienne clé `workFolder` de `centyllion-files` n'est
ni lue ni migrée, seulement supprimée si elle existe. **Aucune image ne transite par
le stockage du navigateur** (IndexedDB, `localStorage`, `sessionStorage`, Cache
Storage) : la capture est un `Blob` en mémoire, écrit dans le dossier ou téléchargé.

| Navigateur | Dossier de travail | Captures |
|---|---|---|
| Chrome, Chromium, Edge — HTTPS ou `localhost` | oui | écrites dans `captures/` puis le sous-dossier du modèle |
| Chrome, Chromium, Edge — HTTP sur une IP | non (hors contexte sécurisé) | téléchargées |
| Firefox, Safari | non | téléchargées |

Sans dossier, sans permission ou en cas d'échec d'écriture, l'image est
téléchargée et le **journal** (§4.5ter) dit toujours laquelle des deux voies a été
suivie : aucune capture ne bascule silencieusement en téléchargement, et rien n'est
réservé à Chrome ou à Edge. Détails : aide intégrée §9.

> **⚠ Mise en garde — Opera** : les graphiques de la page Simulation peuvent
> s'afficher avec un artefact de double dessin (labels d'axes et courbes
> reproduits avec un léger décalage). Cet artefact est propre au compositeur
> d'Opera (le code et le DOM sont corrects ; Chromium et Firefox ne le
> présentent pas). Utilisez Chrome/Chromium, Edge ou Firefox pour un affichage
> fiable des graphiques.

### 3.3 Serveur de développement web

```bash
./gradlew :web:jsBrowserDevelopmentRun
# → http://localhost:8080
```

### 3.4 Archive de déploiement

`tools/version_deployment.py` construit l'archive d'une version et sait faire la
différence entre les deux dépôts voisins (`Endless_Bloom` et
`NetBioDyn_Forever`) :

```bash
python3 tools/version_deployment.py              # menu : quelle version déployer ?
python3 tools/version_deployment.py bloom        # Endless Bloom  (branche main)
python3 tools/version_deployment.py forever      # NetBioDyn Forever (branche NetBioDyn_Forever)
python3 tools/version_deployment.py all --label 2.0.1
```

Le bundle est **toujours reconstruit** à partir des sources avant l'archivage :
seules les sources sont versionnées, une archive ne contient donc jamais un
bundle périmé.

L'archive (`<version>/dist/<nom>-<version>-web.zip`) contient le contenu de
`web/webroot` **à la racine**, sans `certs/` ni `test_*.json` : elle se dézippe
directement dans le répertoire servi par le serveur web. Le script affiche la
branche et le SHA archivés, la taille, le nombre de fichiers et le `sha256`.

Le script ne contient aucun chemin absolu : tout est déduit de son emplacement.
`forever` utilise le dépôt voisin `../NetBioDyn_Forever` ; s'il est ailleurs,
`--forever-path /chemin/vers/NetBioDyn_Forever`.

La construction réclame les identifiants du dépôt Maven privé de bulma-kotlin
(`grpUser` / `grpToken`, propriétés Gradle) — ou un cache Gradle déjà chaud.

Après mise à jour sur le serveur, vérifier la date affichée dans le menu About :
le navigateur ressert l'ancienne version si les en-têtes de cache ne sont pas en
`no-cache`.

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

### 4.1bis Réactif « n'importe quel agent » (Any)

Une ligne de **réaction** peut accepter n'importe quel agent comme réactif : la ligne s'applique dès
qu'une case voisine autorisée est **occupée**, quelle que soit l'espèce de l'agent qui s'y trouve, et
le produit remplace cet agent sur place.

```
C0 : Agent_A -> Agent_A
Réaction : Any -> Agent_D   (toutes directions)
```

- Nouvelle sentinelle `ANY_AGENT = -3` (`core/.../model/model.kt`), utilisable **uniquement** comme
  `reaction[].reactiveId`.
- La valeur est `-3` parce que les trois espaces d'identifiants se recouvrent déjà : les ids de
  grains sont `>= 0`, `-1` vaut « case vide » dans une ligne de réaction (et « aucun » pour un
  produit), `-2` vaut « cases vides » dans les prédicats de comptage, et `-1` y vaut aussi « tous les
  agents ». Aucune valeur négative existante n'était libre.
- **Jamais côté produits** : un produit est toujours un agent précis (ou `-1` = aucun). Un
  `productId = -3` est refusé par la validation avec un message explicite, et la liste déroulante
  des produits ne propose pas « Any ».
- Dans l'éditeur de comportement, la liste du **réactif** d'une ligne propose dans cet ordre :
  aucun agent (case vide), **Any** (n'importe quel agent), puis les agents définis.
- L'héritage de l'âge est inchangé : il vient du numéro de ligne de réaction (`sourceReactive`), pas
  du type d'agent atteint ; avec Any, c'est l'âge du voisin réellement choisi.
- Moteur : l'appariement par tri des voisins et des lignes sur `reactiveId` ne peut pas fonctionner
  avec une sentinelle négative. Les comportements qui en contiennent sont appariés par **index de
  ligne** (les voisins choisis sont déjà dans l'ordre des lignes) ; tous les autres gardent
  l'appariement trié historique, donc une trajectoire identique (`ReactionTrajectoryTest`).


### 4.2 Comptage de voisins (prédicats structurés)

```
C0 : Agent_A -> None
Quand : Agent_B > 3 AND Agent_C == 5
```

- `agentCountPredicates` : liste de `AgentCountPredicate(grainId, op, count)`
- Opérateurs : `=`, `!=`, `<`, `<=`, `>`, `>=`
- Dans l'interface (mode normal), un prédicat « Nb voisins » est une ligne `Nb [type d'agent] [relation] [0..8] [×]` ajoutée par le bouton `+` ; « Tous les agents » correspond à `grainId = -1` (tout voisin occupé) et un type donné à `count(grainId)`

### 4.2bis Prédicats graphiques (mode normal)

Le bloc **Prédicats** de l'éditeur de comportement propose trois groupes de prédicats graphiques, chacun avec un bouton `+` pour ajouter une ligne, un bouton `Aide` contextuel et une corbeille par ligne :

- **Âge de la simulation** (`Simulation age`) : lignes `[relation] [valeur entière]`. Chaque ligne porte sur l'âge de l'agent au pas courant (1 au premier pas de l'agent, 0 pour une génération spontanée).
- **Seuils des champs** (`Field thresholds`, visible si le modèle a au moins un champ) : lignes `[champ] [relation] [valeur 0..1]` sur la valeur du champ dans la case du comportement.
- **Nb voisins** (`Nb Neighbours`) : lignes `Nb [type d'agent] [relation] [0..8]`.

**Toutes les lignes de tous les groupes sont combinées en ET** (et avec la formule de prédicat si elle est renseignée en mode expert) : le comportement ne s'applique que si chaque condition est vraie.

Stockage : `Behaviour.agePredicates`, `Behaviour.fieldPredicates`, `Behaviour.agentCountPredicates`. Le prédicat unique historique `Behaviour.agePredicate` reste lu et évalué ; l'éditeur le replie dans la première ligne d'âge à l'ouverture (`migrateLegacyAgePredicate`), donc un ancien fichier reste utilisable et se normalise dès la première édition.

### 4.3 Expressions de condition

Expressions logiques avec parenthèses et opérateurs :

```
count(1) > 2 && (count(2) == 1 || count(3) == 0)
```

- `count(grainId)` : nombre de voisins du type donné (les 8 cases adjacentes)
- `onAgent(id)` : vrai si **au moins un des huit voisins** contient un agent du grain `id` (la case centrale n'est pas comptée, comme pour `count()`) ; `onAgent(id, dx, dy)` teste une **case unique** à l'offset (dx, dy), la case centrale étant **incluse** pour `(0, 0)`. `dx` et `dy` valent -1, 0 ou 1 — toute autre valeur est refusée par la validation de formule. Les deux formes renvoient un booléen, donc elles se composent avec `&&`, `||` et `!` (`onAgent(0) && !onAgent(1)`) ; les offsets sont toriques.
- **Prédicats d'ensemble sur le voisinage** : `hasAnyOf({0,1})` (au moins un des types listés est présent), `hasAllOf({0,1})` (tous), `hasOnly({0,1})` (aucun type hors de la liste ; les types listés peuvent être absents), `hasExactly({0,1})` (exactement ceux-là, ni plus ni moins). Les noms disent la direction de la comparaison, contrairement à `isSubsetOf`/`isSupersetOf`. La liste `{…}` n'est acceptée **que** comme argument de ces quatre fonctions : `{0,1}` seule, `count(0) + {1}` ou `field({0})` sont refusés par la validation, la liste vide `{}` aussi (deux conventions s'opposeraient : `hasAnyOf({})` toujours faux, `hasAllOf({})` toujours vrai), ainsi qu'un identifiant inexistant. Ce sont des booléens, donc ils se composent avec les autres prédicats (`hasAnyOf({0,1}) && hasOnly({0,1}) && step > 3`), dans un comportement **ou** dans une formule de champ ; la condition d'un modificateur les refuse (un modificateur s'évalue hors de toute case, donc sans voisinage).
- **Limite des prédicats d'ensemble : 30 types d'agents.** L'ensemble des types présents est un masque d'entiers, un bit par type dans un entier 32 bits dont le bit 31 est le signe : les identifiants **0 à 29** sont représentables. Au-delà de 30 types de grains, un masque débordé répondrait un résultat faux en silence : le **diagnostic du modèle** signale alors explicitement la formule (`Predicate` ou `Formula`), et non l'exécution. Utiliser `count(grainId)` dans ce cas.
- Variables : `step` (étape courante de la simulation), `age(n)` (âge du réactif `n` : 1 = agent principal, 2 à N+1 = voisins des réactions, même numérotation que la colonne « Avec l'âge de »), `x`, `y`, `z` (position)
- Opérateurs : `&&`, `||`, `!`, comparaisons, parenthèses
- La formule doit être un **prédicat logique** (renvoyer `true`/`false`) : `sin(step)` est refusé par la validation
- `field(id)` / `field(id, dx, dy)` (case courante ou offset) et `agent(x, y)` sont disponibles dans les formules de comportement
- Validation automatique dans l'interface et dans le diagnostic du modèle
- Dans l'interface, la ligne de formule est libellée **Predicate** (mode expert uniquement)
- La formule est combinée en **ET** avec les prédicats graphiques du mode normal (âge, seuils de champs, nb voisins)

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
- Les arguments sont des expressions numériques (`step`, `x`, `y`, `count(grainId)`, `field(id, dx, dy)`, fonctions mathématiques)
- Validation automatique dans l'interface (champ **Actions** + aide) et dans le diagnostic du modèle

### 4.5bis Méta-actions des modificateurs (`modifiers[].actions`)

Les méta-actions ne s'écrivent **plus** dans un comportement : `validateActionFormula()` les
refuse. Elles vivent dans la formule d'actions d'un **modificateur de simulation**
(`model.modifiers[]`), exécutée une fois par pas, après les comportements, quand sa condition
est vraie.

**Règle de casse** : les méta-fonctions et les méta-tests commencent par une **majuscule** ; les
fonctions de formule et les prédicats restent en **minuscules** (`abs`, `min`, `max`, `sin`,
`cos`, `count`, `field`, `age`, `agent`, variables `step`, `x`, `y`, `z`, `current`).

**Boucle de course** — ces actions pilotent la simulation, pas la grille (drapeaux du
`Simulator` consommés par la boucle, jamais la file des commandes de grille) :

| Action | Effet |
|---|---|
| `Pause()` | arrête la course à la fin du pas courant, **définitivement** (un bandeau d'avertissement le signale, la course ne repart que par le bouton Run ou le pas à pas) |
| `Pause(t)` | **pause minutée** : gèle la course pendant `t` secondes **réelles** (`t` > 0, littéral ou expression, ex. `2.5`) puis la relance seule ; aucun pas n'est exécuté et le bandeau indique la durée |
| `RunOneStep()` | exécute exactement un pas de plus, puis se met en pause |
| `RunNStep(N)` | exécute N pas de plus (N >= 1, arrondi) puis se met en pause ; `N <= 0` est ignoré |
| `Rewind()` | revient à l'état initial **sans relancer** (même effet que le bouton de rembobinage) |
| `Restart()` | revient à l'état initial **et relance** la course |

**Captures** — `Capture()` (sans argument) demande une image (JPEG qualité 0,95 par défaut, ou PNG
sans perte) de la vue de simulation, prise à la fin du pas qui l'émet, après le rendu complet et
avant le pas suivant. Le
noyau ne dessine rien : il incrémente un simple compteur (`Simulator.captureRequestCount`, sur le
modèle de `modifierPauseCount`), que la vue consomme (`consumeCaptureRequest()`) pour encoder le
canevas. Le fichier s'appelle `NOM_MODELE__PAS.jpg` (`GoL_Modifiers__12.jpg`), le nom du modèle
étant nettoyé pour le système de fichiers (caractères interdits, espaces et points en début et en
fin, longueur bornée à 64 ; repli explicite `model`). Il est écrit dans `captures/NOM_MODELE/` du
**dossier de travail** (bouton maison de la rangée « Informations sur le modèle », icône `fa-home`
pleine, infobulle portant l'état : `showDirectoryPicker` qui s'ouvre dans le dossier courant,
`handle` dans la base IndexedDB
`centyllion-home` — seule de son espèce, le magasin `handles`, clé `home` —, permission
renouvelable d'un clic), ou **téléchargé** quand aucun dossier n'est défini — un message dit
toujours laquelle des deux voies a été suivie. Aucun octet d'image n'est stocké par le navigateur :
la capture reste un `Blob` en mémoire jusqu'à l'écriture ou au téléchargement. Voir l'aide
intégrée §9 pour les limites par navigateur.

### 4.5quater Onglet **Enregistrements** (réglages de capture et de CSV)

Le dernier onglet des paramètres, **après Modèle**, décide de ce que l'application écrit. Ses
réglages sont des préférences **du navigateur** : ils vivent dans `localStorage`, sous la clé
`centyllion-recording-settings` (une ligne `clé=valeur` par réglage), et **jamais** dans le fichier
`.nbd2` — ouvrir un projet venu d'un autre poste ne change pas la façon dont ce poste enregistre.

| Réglage | Valeurs | Défaut |
|---|---|---|
| Format des images | JPEG (avec perte) / PNG (sans perte) | JPEG |
| Qualité JPEG | curseur 0,50 → 1,00, pas de 0,05 (désactivé en PNG) | 0,95 |
| Résolution | « taille de l'écran » (le canevas affiché) / « taille de la grille » | taille de l'écran |
| Pixels par cellule | 1 → 8 (n'agit qu'en taille de grille) | 4 |
| Séparateur CSV | tabulation / virgule / point-virgule / espace | tabulation |

**Taille de la grille** : la grille entière est redessinée **hors écran** à `N` pixels par cellule,
puis encodée. L'image fait exactement `largeur × N` par `hauteur × N` pixels (grille 100 × 100 à
4 pixels par cellule → 400 × 400), quel que soit la fenêtre, et ne contient que les cellules et les
champs — la grille de dessin, la sélection et l'aperçu du pinceau sont des aides de l'écran. Les
deux modes réutilisent le **même rendu** (`SimulatorViewController.paint`), paramétré par
l'échelle : il n'existe pas de second moteur de rendu. C'est ce réglage, et non la qualité, qui
change réellement le détail d'une capture : à qualité 0,50 comme à 1,00, les dimensions sont
identiques et seul le poids du fichier change.

**Séparateur CSV** : il est écrit entre les colonnes des **deux** exports de courbes
(`agents.csv`, `fields.csv`), en-tête compris. Un nom qui contient le séparateur est **mis entre
guillemets** (guillemets internes doublés) : `step,"Membrane, interne",Autre` en virgule, si bien
que le nombre de colonnes reste celui du modèle. Les règles pures (bornes, conversions, échappement,
sérialisation) sont dans `core/.../model/recording.kt` et testées par
`core/src/commonTest/.../RecordingSettingsTest.kt` — une valeur absente, corrompue ou hors bornes
retombe sur sa valeur par défaut (séparateur inconnu → tabulation).

`Pause(t)` est une **pause de confort** (démonstration), pas une horloge de modèle : elle
n'exécute aucun pas, donc la grille et la trajectoire des états ne dépendent pas du temps et
restent **reproductibles** dès que le déclencheur l'est (`step == 10`, un comptage, une valeur de
champ). Seule sa **durée** vient de l'horloge de la machine : au moins `t` secondes, davantage dans
un onglet d'arrière-plan. Un `Pause()` ou un `Pause(t)` émis par un modificateur affiche un bandeau
`notification is-warning` au-dessus de la vue de simulation ; celui d'une pause minutée disparaît à
la reprise automatique.

**Population et activation** : `Add(id, N)`, `AddRect(id, N, x1, y1, x2, y2[, fill])`,
`AddCircle(id, N, x, y, radius[, fill])`, `DrawLine(id, x1, y1, x2, y2)`,
`EraseRect(x1, y1, x2, y2[, fill])` / `EraseRect(id, x1, y1, x2, y2[, fill])`,
`EraseCircle(x, y, radius[, fill])` / `EraseCircle(id, x, y, radius[, fill])`, `Erase(id)`,
`Activate(id)`, `Inactivate(id)`, `Use(id)`, `Unuse(id)`.

Le drapeau **`fill`** des quatre formes est un **littéral** `true` / `false`, écrit en dernier
(`AddRect(1, 10, 0, 0, 9, 9, false)`) : `true` (le défaut quand il est omis) trace la forme pleine,
`false` son contour seul. Un argument non booléen à cette place est refusé avec un message explicite.
Le contour est calculé à partir du plein de la même forme — rectangle des mêmes coins moins son
intérieur, disque de rayon `r` moins celui de rayon `r − 1` — par les deux fonctions pures
`rectangleRows` / `circleRows` (`core/.../model/shapes.kt`), que le simulateur **et** la barre de
dessin utilisent : il n'existe qu'une seule définition du disque dans l'application.

**Commandes de grille** : `Show(id)`, `Hide(id)`, `Select(x1, y1, x2, y2)`, `Cut()`, `Copy()`,
`EraseSelection()`, `Paste(x, y, overwrite)`, `FlushSelection()`, `Unselect()`,
`PasteAndFlush(x, y, overwrite)`, `Rotate(N)`, `FlipX()`, `FlipY()`,
`AllowToricSelection(enabled)`, `AllowOverwriting(enabled)` (voir l'aide §7).

**Fin d'opération et rectangle de sélection** : quatre commandes *terminent* une opération et
**lâchent donc le rectangle de sélection**, exactement comme `Unselect()` — `EraseSelection()`,
`FlushSelection()`, `PasteAndFlush(x, y, overwrite)` et `Unselect()` elle-même
(`GridCommand.dropsSelection`, appliqué par `applyGridCommands` après le travail de la commande).
Ce lâcher ne touche que l'affichage : `FlushSelection()` vide la mémoire (le presse-papiers), la
grille n'est pas modifiée, et `PasteAndFlush()` colle d'abord (le collage a besoin du
presse-papiers) avant de vider la mémoire. `Unselect()` ne fait que cela : ni la grille ni la
mémoire ne changent — l'action « Déselectionner » existe donc déjà, dans la barre d'édition
(bouton, Ctrl+Maj+S, clic droit dans la sélection) comme dans les formules.

**Tests d'état**, réservés à la condition d'un modificateur : `IsBehaviourActive(id)`,
`IsModifierActive(id)`, `IsToricSelectionOn()`, `IsOverwritingOn()`, `IsVisible(id)`.

`pause()` en minuscules reste accepté comme **alias de compatibilité** de `Pause()` pour les
modèles déjà enregistrés (il a été la seule méta-fonction à ne pas respecter la règle de casse) ;
il est **obsolète** et n'est plus documenté ailleurs que comme alias. `AddDisc` et `EraseDisc` sont
de même les anciens noms de `AddCircle` et `EraseCircle` : acceptés, obsolètes, remplacés dans
l'aide par les noms du cercle.

### 4.5ter Journal de la simulation

Sous l'écran de simulation — sous la barre de dessin et la barre d'édition — un panneau
**journal** remplace les messages flottants qui recouvraient le haut de la grille. Une ligne
porte son **niveau en toutes lettres** (la couleur n'est jamais le seul signal), le **numéro de
pas affiché** et le message :

| Niveau | Couleur | Ce qu'il signale |
|---|---|---|
| `META` | bleu | une méta-action exécutée par un modificateur, par exemple `Pause(2) exécutée` |
| `INFO` | vert | un événement normal : capture écrite ou téléchargée, journal exporté, import ou chargement depuis la galerie |
| `WARN.` | orange | un point à vérifier : pause demandée par un modificateur (avec sa durée), permission du dossier à renouveler, fichier illisible |
| `ERREUR` | rouge | un échec : capture impossible, écriture refusée, fichier non décodable |

Le tampon vit dans le noyau (`Simulator.journal`, `core/.../model/journal.kt`) et il est **borné à
200 entrées** : les plus anciennes sortent. Les méta-actions l'alimentent **au moment où elles
s'exécutent** ; la vue y ajoute ce qu'elle est seule à savoir (capture écrite, pause demandée par
un modificateur, messages de la galerie et de l'import). Un rembobinage ne l'efface pas, un nouveau
projet repart d'un journal vide. Les **dialogues de confirmation restent des dialogues** : le
journal ne remplace pas une question.

L'en-tête **replie** la liste ; replié, il annonce le **nombre d'avertissements**. L'état est
mémorisé dans `localStorage` (`centyllion-journal-collapsed`). L'**icône d'export**, à droite de
l'en-tête, est désactivée quand le journal est vide et ne le vide jamais : elle écrit
`journal/NOM_MODELE__journal.txt` dans le dossier de travail — nom nettoyé comme celui d'une
capture — ou télécharge le même fichier. Le texte contient une ligne d'en-tête (date et nom du
modèle) puis une ligne par entrée, `niveau`, `pas` et `message` séparés par des tabulations, avec
les mots fixes `meta`, `info`, `avert`, `erreur`. Détails : aide intégrée §10.

### 4.6 Barre de dessin

La barre sous la vue aligne les outils (stylo, ligne, **rectangle**, **cercle**, spray, gomme,
déplacement), la taille du pinceau, l'ajout aléatoire et l'effacement total. Le rectangle et le
cercle s'ancrent sur la cellule d'appui et se tracent au relâchement du bouton ; le rayon du cercle
est la distance entre le point d'appui et le pointeur. L'interrupteur **« Fill ? » / « Remplir ? »**,
à côté des outils et dans le style de « Torique ? » et « Écraser ? » de la barre d'édition, décide
pour les deux formes entre **plein** (allumé, le défaut) et **contour** (éteint). Le tracé utilise
les mêmes fonctions pures que les méta-actions `AddRect`/`AddCircle`/`EraseRect`/`EraseCircle`
(`rectangleRows`, `circleRows`) ; une cellule qui déborde de la grille est **repliée** du côté
opposé si « Torique ? » est actif, **perdue** sinon.

### 4.6bis Charger un stencil (« Stencil » dans la barre d'édition)

Le bouton **Stencil** (icône tampon), à droite de Coller dans la barre d'édition, ouvre une
fenêtre qui lit un **motif** et le place dans le **presse-papiers en sélection flottante** : rien
n'est écrit sur la grille, le motif flotte sous la souris exactement comme après un Copier, puis
s'ancre d'un clic gauche, se tourne avec les boutons existants (Ctrl+R, Ctrl+F, Ctrl+Maj+F) et se
colle avec Ctrl+V.

Le format est **détecté tout seul**, sans sélecteur ; quatre formats sont acceptés :

| Format | Reconnaissance | Identifiants |
|---|---|---|
| RLE (Golly) | une ligne `x = L, y = H`, puis éventuellement `rule = …`, commentaires `#N` `#C` `#r` `#c` | `b`/`.` vide, `o` cellule vivante : un corps **sans majuscule** est à deux états et `o` désigne le **premier grain** (id 0), comme `O` dans `.cells` ; une majuscule (`A`…`Z` = états 10 à 35) rétablit la règle **état n = grain n** (`2A` = deux cellules de grain 10) |
| `.cells` | lignes de `.` et `O` (`o`), commentaires `!`, nom après `!Name:` | le format n'a que deux états : `O` désigne le **premier grain** du modèle (id 0) |
| matrice | un caractère par cellule, `,` espace et tabulation ignorés, lignes `!` et `#` ignorées | `.` vide, `0`-`9` puis `a`-`f` (`A`-`F`) pour les grains 0 à 15 |
| projet `.nbd2` | l'objet JSON du fichier | l'environnement peuplé est extrait avec le grain de chaque cellule, puis **recadré sur la boîte englobante** des agents |

Tout autre contenu est **refusé** : la fenêtre affiche alors la liste des formats acceptés et
rien n'est chargé.

Un identifiant de grain **absent du modèle courant** laisse la cellule **vide** ; le journal
signale une seule fois par chargement le nombre de cellules ignorées et la liste des identifiants
en cause. Un motif qui perd toutes ses cellules n'est pas chargé du tout (un presse-papiers
entièrement transparent effacerait sa cible sous « Écraser »).

Le collage d'un stencil suit **exactement** les règles du collage manuel : « Écraser ? » actif, il
remplace les agents rencontrés ; inactif, il ne remplit que les cellules vides du motif, les
autres cellules du motif restant transparentes. « Torique ? » décide du débordement. L'analyse est
pure et vit dans le noyau (`core/.../model/stencil.kt`, `parseStencil` / `Stencil.restrictTo`),
testée sans navigateur ; la fenêtre (`controller/model/StencilDialog.kt`) ne fait qu'appeler
`loadStencil` de la vue, qui remplit le presse-papiers comme `copySelection`. Détails : aide
intégrée §7.5, et bouton **?** à côté de Stencil.

### 4.7 Visibilité des agents et des champs

Dans le panneau latéral (liste des agents, comportements et champs), chaque grain et
chaque champ possède une **case à cocher** (cochée par défaut) à gauche de sa couleur :
- décocher masque l'affichage de ce type d'agent ou de ce champ dans la simulation
  (desktop et web) ;
- la visibilité est session-only (non persistée dans le modèle).

### 4.8 Exemples complets

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

### 4.9 Conditions aux limites

Chaque agent et chaque champ est **torique par défaut** (retour en bout de
plateau). En **mode expert** :

- **Agent** (éditeur de grain, entre *Movement* et *Fields*) : 4 interrupteurs
  *Boundary conditions* (Gauche/Droite/Haut/Bas) ; décocher une paroi
  empêche les agents de ce grain de la traverser. L'asymétrie est autorisée
  (ex. Gauche décoché + Droite coché : un agent du bord gauche ne sort pas en
  allant à gauche, un agent du bord droit sort en allant à droite).
- **Comportement** (éditeur de comportement, section *Conditions*, sous les
  prédicats) : les mêmes 4 interrupteurs *Conditions aux limites*, avec les
  mêmes libellés. Ils ne gouvernent pas un déplacement mais le **voisinage
  visible** : une paroi décochée rend invisibles les voisins situés au-delà
  (ni réactif, ni case cible, ni compté, ni champ lu). Les quatre parois
  cochées (défaut) = torique, cas de tous les fichiers existants.
- **Champ** (éditeur de champ) : interrupteur *Toric* ; décoché = bordure
  **imperméable** (la diffusion ne traverse pas les bords de la grille,
  « best effort » sans solveur dédié).

Les conditions sont stockées dans le modèle (`boundaryConditions` par grain et
par comportement, `toric` par champ), sauvegardées (cf. §4.10) ; celles du
comportement sont visibles dans les deux modes, comme celles du grain.

### 4.10 Format de sauvegarde (`.netbiodyn`)

Les fichiers sauvegardés sont compacts : une valeur égale à sa valeur par
défaut est omise (`encodeDefaults = false`), sauf quatre champs qui obéissent
à un contrat explicite d'**écriture complète** pour rester autodescriptifs :

- `boundaryConditions` (grain, comportement) : omis si torique partout ; sinon les **4
  parois sont toujours écrites** (`{"left":false,"right":true,"top":true,"bottom":true}`) ;
- `toric` (champ) : écrit seulement si `false` ;
- `agentCountPredicates` (comportement) : liste omise si vide ; chaque
  prédicat écrit toujours `grainId`, `op` et `count`
  (`[{"grainId":-1,"op":"GreaterThan","count":3}]`) ;
- `sourceReactive` (réaction) : écrite si ≥ 0.

Lecture rétrocompatible : un `boundaryConditions` partiel (fichiers anciens,
ex. `{"left":false}`) est accepté, les parois absentes valant `true`. Le
projet **NISMA** (C#) est aligné sur ce même contrat
(`NISMA/PROJECT_DOCUMENTATION.md` §2.4).

---

## 5. Structure du projet

```
CentyllionExtend/
├── core/           # Modèle, simulation, expressions (Kotlin Multiplatform)
├── compose/
│   └── shared/     # Interface Compose Multiplatform (desktop)
├── web/            # Interface navigateur (Kotlin/JS + canvas 2D)
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
| `web` | js | Interface navigateur (Bulma + canvas 2D) |
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
- Journal de la simulation : bornage à 200 entrées, ordre, format d'export (`JournalTest`)
- Lecture des stencils : RLE (commentaires, `rule`, multi-états), `.cells`, matrice et séparateurs,
  `.nbd2` recadré, refus explicites et identifiants de grains inconnus (`StencilTest`)

---

## 7. Documentation détaillée

- [`PROJECT_DOCUMENTATION.md`](PROJECT_DOCUMENTATION.md) — documentation complète du projet (architecture, modèle, simulateur, UI)
- [`README_Centyllion_Structure.md`](README_Centyllion_Structure.md) — structure des classes et diagramme UML

---

## 8. Références

- Projet original : [github.com/jeancharles-roger/centyllion-app](https://github.com/jeancharles-roger/centyllion-app)
- Simulateur en ligne : [www.centyllion.com](https://www.centyllion.com/)
- Site de référence : [virtulab.univ-brest.fr/netbiodyn.html](https://virtulab.univ-brest.fr/netbiodyn.html)

## 9. Galerie de modèles, cas d'usage et déploiement

### 9.1 À quoi sert la galerie

La galerie est une collection de modèles « exposés » : le modélisateur décide qu'un
modèle mérite d'être montré, lui associe une image (capture de la simulation, ou
vignette générée depuis l'état initial faute d'image), un titre affiché sur
l'image, et une micro-description. Les miniatures sont présentées en grille ; un
clic propose d'ouvrir le modèle dans NetBioDyn (OK) ou d'annuler.

Ces métadonnées (titre, description, image) ne sont **jamais** écrites dans le
fichier de sauvegarde `.nbd2` : le modèle reste un modèle. La description du
modèle, elle, appartient au modèle et s'édite par le bouton « Infos modèle » ;
la description de galerie est une présentation, distincte.

### 9.2 Deux galeries distinctes

| | galerie distribuée | galerie personnelle |
|---|---|---|
| emplacement | `Gallery/gallery.json` + modèles, livrés avec le logiciel | `galerie/` du dossier de travail (disque) **ou** IndexedDB du navigateur |
| contenu | les modèles exposés par l'équipe (Autopulse 2, Game of Life using Modifiers) | les modèles que l'utilisateur ajoute |
| visibilité | tous les visiteurs, identique | ce poste (dossier choisi) ou ce navigateur |
| écriture | jamais modifiée par l'application | immédiate, à chaque ajout ou suppression |

Le dossier `Gallery/` est à la racine du projet et **recopié dans `webroot/Gallery/`
par `syncJs`** à chaque build : il doit rester hors du webroot, qui est effacé à
chaque compilation. Il est distribué avec le logiciel.

Un modèle ajouté par un utilisateur n'est donc **ni** publié, **ni** distribué,
**ni** visible des autres visiteurs. C'est un choix assumé : sans comptes
utilisateurs, un serveur ne peut pas savoir à qui appartient un modèle.

**Deux emplacements, un seul actif.** La galerie personnelle vit dans
`galerie/` à la racine du **dossier de travail** quand ce dossier est défini et
que sa permission est accordée (`WorkFolderState.DEFINED`) ; sinon elle reste
dans l'IndexedDB du navigateur, comme avant. La fenêtre de la galerie affiche
toujours l'emplacement actif (chemin du dossier, ou « navigateur ») : il n'y a
pas de bascule silencieuse.

**Format sur le disque.** `galerie/galerie.json` ne contient que des
métadonnées — `id`, `title`, `description`, `image` (nom du fichier image),
`date` — et chaque image est un fichier JPEG séparé du même dossier, nommé
`image-<id>.jpg` (id nettoyé : `/ \ : * ? " < > |` remplacés, longueur bornée).
Le JSON a **le format de l'export** (version 1, clé `models`, plus `exportedAt`),
donc un dossier `galerie/` est directement réutilisable et importable, et jamais
une image en base64 n'y est écrite.

**Écriture.** `galerie.json` est relu juste avant chaque écriture puis fusionné
par id (`mergeById`) : le dernier écrivain gagne pour une entrée, les entrées
absentes du fichier — écrites par un autre onglet — sont conservées. Une
suppression confirmée retire l'entrée des deux côtés et ne supprime **que** le
fichier image référencé par cette entrée. Si `galerie.json` est illisible, il
n'est jamais écrasé : il est copié sous `galerie.bak-AAAAMMJJ-HHMMSS.json`, un
fichier neuf est écrit, et la galerie le signale.

**Premier usage.** Si la galerie du disque est vide ou absente et que celle du
navigateur contient des entrées, elles y sont copiées une fois, avec un message.
IndexedDB n'est **jamais vidé** : c'est le repli si le dossier est oublié ou sa
permission perdue ; il est aussi tenu à jour après chaque écriture sur le disque.

Conséquence pratique : vider les données du navigateur n'efface plus la galerie
quand elle vit sur le disque. Sans dossier de travail, elle disparaît avec les
données du navigateur — d'où les deux boutons **« Exporter ma galerie »** et
**« Importer ma galerie »** : le fichier `ma-galerie.json` exporté est autonome
(il contient les textes des modèles) et la copie du dossier `galerie/` suffit sur
le disque. La galerie distribuée n'est pas concernée : elle fait partie du
déploiement.

### 9.3 Cas d'usage

**Modélisateur, en local.** Il construit un modèle, l'essaie, puis l'expose dans sa
galerie personnelle. Si un dossier de travail est choisi, la galerie est écrite dans
`galerie/` de ce dossier : il la retrouve en copiant le dossier sur un autre poste,
ou en important `ma-galerie.json`. Pour proposer un modèle à la distribution, il
dépose le `.nbd2` et une image dans `Gallery/`, puis ajoute une entrée dans
`Gallery/gallery.json` — opération d'équipe, versionnée avec le projet.

**Enseignant, en salle.** Les étudiants se connectent à l'application déployée. Ils
partent de la galerie distribuée, qui donne des exemples immédiatement ouvrables,
puis construisent les leurs. Chaque poste garde ses ajouts — dans le dossier de
travail quand ils en choisissent un (salle de TP avec un dossier partagé, par
exemple), sinon dans le navigateur ; rien n'est centralisé, et l'enseignant n'a pas à
administrer de comptes.

**Visiteur d'une instance publique.** Il consulte les modèles exposés, en ouvre un,
l'explore, éventuellement l'édite pour lui-même. Sans dossier de travail, ses ajouts
ne quittent pas son navigateur ; la ligne d'emplacement de la galerie le lui rappelle.

### 9.4 Déploiement

**Développement local** (le seul cas où un dossier local est disponible) :

```bash
./gradlew :web:jsBrowserProductionWebpack :web:syncJs
python3 serve.py                      # http://localhost:8081
```

`serve.py` sert `web/webroot` et ajoute trois routes (`GET /api/gallery`,
`POST /api/gallery`, `POST /api/model`) qui écrivent dans `Gallery/local-gallery.json`
et `Gallery/local/`. C'est un outil de développement : l'application fonctionne sans
lui, et ne doit pas en dépendre.

```bash
python3 -m http.server 8081 --directory web/webroot   # équivalent, sans persistance côté dossier
```

**Serveur partagé** (cas d'usage général, par exemple l'instance de Brest) : le
dossier `web/webroot` est publié par n'importe quel serveur web statique (nginx,
Apache, un hébergement de fichiers). Il faut y inclure `webroot/Gallery/`, donc le
contenu de `Gallery/` au moment du build. Côté client :

- aucun dossier local, aucune écriture côté serveur ;
- galerie distribuée en lecture seule, identique pour tous ;
- galerie personnelle dans le navigateur de chaque visiteur — ou dans le dossier de
  travail que le visiteur choisit, si son navigateur offre le File System Access
  (Chrome, Edge, sur HTTPS ou `localhost`) — sauvegardable par export et par copie
  du dossier `galerie/`.

Ce que l'instance doit fournir : le bundle `web.js`, `css/`, `font/`, `index.html`,
`help.html`, `favicon.ico` et `Gallery/`. Aucun service particulier, aucune base de
données, aucun compte utilisateur.

**Trois points à vérifier avant la mise en service** (ils ne se voient qu'en
production) :

- **racine du domaine obligatoire** : `index.html` référence ses ressources par des
  chemins absolus (`/css/centyllion.css`, `/js/centyllion/centyllion.js`). Servir
  l'application dans un sous-répertoire (`/netbiodyn/`) ne fonctionne pas : il faut
  un sous-domaine dédié, ou adapter les chemins.
- **polices d'icônes externes** : FontAwesome est chargé depuis
  `use.fontawesome.com`. Sur un réseau filtré, les icônes manquent sans qu'aucune
  erreur ne s'affiche ; autoriser ce domaine, ou héberger FontAwesome localement.
- **cache du bundle** : `centyllion.js` porte un nom fixe, sans empreinte de
  version. Sans en-tête `Cache-Control: no-cache` sur les `.js` et `index.html`,
  les visiteurs continuent d'exécuter l'ancienne version après une mise à jour.

**Limites à annoncer aux utilisateurs d'une instance partagée.** Sans dossier de
travail, la galerie personnelle est liée au navigateur et au poste : elle ne suit pas
d'une machine à l'autre et disparaît si les données du navigateur sont effacées. Avec
un dossier de travail, elle suit le dossier (copie sur un autre poste), mais reste
invisible des autres visiteurs de l'instance. Dans les deux cas, l'emplacement est
affiché dans la galerie, et l'export (`ma-galerie.json`) ou la copie du dossier
`galerie/` est la réponse au transfert.

### 9.5 Ce que la galerie n'est pas

Ce n'est pas une publication : ajouter un modèle ne le rend pas visible des autres.
Ce n'est pas un dépôt partagé : il n'y a ni compte, ni modération, ni synchronisation
entre postes. C'est un écrin personnel, adossé à une sélection distribuée.
