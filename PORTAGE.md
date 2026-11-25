# Portage entre Endless Bloom et NetBioDyn Forever

Ce document existe pour une raison précise : quatre fichiers ont été oubliés lors de
portages successifs alors qu'ils compilaient des deux côtés, et chaque oubli a coûté
une tentative de portage ratée, une explication et une reprise. Lire ce fichier
**avant** de porter quoi que ce soit.

Les deux dépôts sont jumeaux : même noyau, même vue web, même format de fichier. La
seule différence de fonctionnalité est celle des **blocs d'exécution**, présents dans
Endless Bloom seulement.

## État au dernier audit

| | Endless Bloom | NetBioDyn Forever |
|---|---|---|
| Tests du noyau | 317 | 304 |
| Build web | vert | vert |

L'écart de 13 tests est entièrement dû à `ExecutionBlockTest.kt`, propre à Endless
Bloom. Aucun autre écart de test.

## Fichiers propres à Endless Bloom — à ne jamais copier dans Forever

`core/src/commonTest/kotlin/com/centyllion/model/ExecutionBlockTest.kt`

Les blocs d'exécution touchent aussi l'interface, dans `ShowPage.kt` et le contrôleur
de modèle. Copier ces fichiers en bloc dans Forever casse la compilation : Forever
n'a pas les classes correspondantes.

## Divergences légitimes — à conserver, à ne pas « aligner »

**`web/src/jsMain/kotlin/com/centyllion/client/page/ShowPage.kt`** — environ 96 lignes
d'écart, mais presque uniquement de l'**ordre de déclaration** et le nom de la
distribution (`distributionName` : « Endless Bloom » / « NetBioDyn Forever »). Les deux
fichiers portent le même contenu.

Conséquence pratique : **ne jamais copier `ShowPage.kt` d'un dépôt à l'autre.** Une
copie introduit les blocs d'exécution dans Forever et supprime son nom. Les ajouts à
ce fichier se font par patch ciblé, en localisant la déclaration réelle des deux côtés.

## Les pièges déjà rencontrés

Ces fichiers ne ressemblaient pas à des écarts : ils compilaient, ou leur absence ne
se voyait qu'à l'exécution d'une fonctionnalité précise.

- `core/.../model/validation.kt` — chemin de diagnostic des modificateurs. Son absence
  dans Forever faisait échouer un test de diagnostic sans rapport apparent.
- `web/.../client/utils.kt` — `decodeModelAndSimulation`. Sans lui, la galerie de
  Forever ne pouvait pas décoder un modèle importé depuis le disque.
- `web/.../controller/model/GrainDisplayController.kt`,
  `FieldDisplayController.kt`, `ModelController.kt` — les interrupteurs « Show ? ».
  Forever affichait encore des cases à cocher, et le défaut de recochage après
  `Show`/`Hide` subsistait pour la même raison.
- `core/.../model/tools.kt` — `EditTool` vit dans le **noyau**, pas côté web. Chercher
  `tools.kt` sous `web/src` mène à un fichier absent et à une erreur « unresolved
  reference » trompeuse.
- `core/src/commonTest/.../gallery/GalleryDiskTest.kt` — test présent d'un seul côté
  alors que le code testé existait des deux.

## Procédure de portage

1. **Comparer avant de copier.** Lister les fichiers qui diffèrent et ceux qui
   n'existent que d'un côté, avec le nombre de lignes. Ne jamais partir de la liste
   des fichiers « que le sous-agent dit avoir touchés ».
2. **Classer chaque écart** : identique / propre à EB / divergence légitime / dérive.
   Une dérive se corrige, une divergence légitime se conserve.
3. **Copier les fichiers de code**, jamais ceux de la liste « propres à EB » ni
   `ShowPage.kt`.
4. **Patcher à la main les fichiers divergents**, en lisant les déclarations réelles
   des deux côtés plutôt qu'en devinant leur emplacement.
5. **Compiler et tester les deux dépôts** avant de commiter ; si Forever échoue,
   annuler ses modifications plutôt que le laisser cassé.
6. **Commiter séparément**, avec le portage explicite dans le message.

## Vérification

```bash
./gradlew --offline :core:jvmTest
./gradlew --offline :web:jsBrowserProductionWebpack :web:syncJs
```

Puis, pour l'audit lui-même :

```bash
# fichiers présents seulement dans un dépôt
git ls-files 'core/*' 'web/*' | while read f; do [ -f ../NetBioDyn_Forever/$f ] || echo $f; done

# écarts, du plus gros au plus petit
for f in $(git ls-files 'core/src/*' 'web/src/*'); do
  [ -f ../NetBioDyn_Forever/$f ] && { d=$(diff "$f" ../NetBioDyn_Forever/$f | grep -c '^[<>]'); [ "$d" -gt 0 ] && echo "$d $f"; }
done | sort -rn
```

Un écart non expliqué par l'une des catégories ci-dessus est un défaut, pas une
curiosité : c'est ainsi que les quatre fichiers oubliés ont été retrouvés.
