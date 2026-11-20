# Notes préalables :
[OK] - Les agents restent inchangés.
[OK] - Les fields restent inchangés.
[Pas OK : à faire] - Les fields doivent être accessibles (on peut les créer et les utiliser dans les comportements) en mode expert ET en mode normal
[OK] - Dans les comportements l'héritage de l'age pour chaque réaction ("avec l'age de") doit être visible en mode normal et en mode expert.

# Interface des comportements

## Description générale de l'Interface commune aux modes Normal et Expert
[OK] La fenetre de behaviour est divisée en 3 parties séparées par des lignes bleues et portant comme titres les titres donnés ci-dessous entre guillemets.
[OK] Les titres sont affichés en haut de chaque bloc, bien visibles, centrés, affichés en rouge foncé, gras, soulignés
[OK] Les titres des 3 blocs sont :
[OK] - bloc du haut : "Information" (français) / "Misc" (anglais)
[OK] - bloc du milieu : "Prédicats" (français) / "Predicates" (anglais)
[OK] - bloc du bas : "Reactions"


### Séparation en 3 blocs :
[OK] La fenetre de behaviour est divisée en 3 parties séparées par des lignes bleues et portant comme titres les titres donnés éventuellement ci-dessous entre guillemets. NB : Certains points seront affichés ou non selon le mode normal ou expert.


## Interface des comportements en Mode Normal

### bloc du haut (informations)
[OK] - Le nom
[OK] - La description

### bloc du milieu (prédicats)
[OK] - Le prédicat graphique sur l'age encore présent dans centyllion-app se présentant comme suit : 'Texte "When age"' +  'Relation (>, <, >= ...)' + 'Valeur'
[Pas OK : à faire] - Le prédicat graphique du seuil de champ (Field thresholds, uniquement si au moins un champ existe) qui est encore présent dans la version centyllion-app d'origine, mais mal placé et qui se présente comme suit : 'Texte : "Seuils des champs"' + 'un bouton + qui permet de créer un seuil'; puis à chaque fois qu'un seuil est créé, cela affiche 'un champ parmi un menu déroulant' + 'une relation' + 'une valeur'
[OK mais à modifier] - Un nouveau prédicat graphique sur le nombre de voisins (que tu n'as pas fait, donc à créer sur un modèle similaire à celui des champs) : comme pour les réactions, tu vas :
[OK] a) titrer ce prédicat "Nb voisins" ou " Nb Neighbours"
[OK] b) mettre un bouton '+' pour ajouter une ligne de prédicat sur les counts
[A modifier] c) quand on fait bouton '+', on crée une ligne de prédicat (et on peut en ajouter en cliquant sur '+' à nouveau). Cette ligne de prédicat affiche : Nb, [Debut modification] puis on a un menu déroulant du type d'agent compté dans cette relation (ce qui permet l'équivalent du "count(id)" des formules de prédicat) [fin modification] puis un symbole relationnel (>,<, >=, <=, ==, !=), puis une valeur (menu déroulant) comprise entre 0 (le nombre min de voisins) et 8 (le nombre max de voisins)
- NB : la relation entre ces 3 prédicats (sur l'age, sur le seuil des champs, et sur les counts) est un ET (toutes les conditions doivent etre remplies en même temps).
- NB : plus tard, nous travaillerons sur une autre version plus élaborée de ces 3 prédicats graphiques.
[OK] - La formule de prédicat ne doit pas apparaitre !!!

### bloc du bas (reactions)
[OK] - La vitesse (tu réécris "vitesse" ou "speed" selon langue)
[doit apparaitre en mode normal] - Les 'field influences' sont présent si un champ existe
[OK] - Les reactions (tu réécris "Reactions" comme c'est déjà le cas.
[OK] - La formule d'actions ne doit pas apparaitre

## interface des comportements en Mode Expert

### bloc du haut (informations)
[OK] - Le nom
[OK] - La description

### bloc du milieu (prédicats)
[OK] - Les prédicats graphiques (sur l'age, sur les seuils des champs et sur les counts) ne doivent pas apparaitre
[OK] - La formule de prédicat doit apparaitre (c'est le seul prédicat qu'il est possible de faire en mode expert)

### bloc du bas (reactions)
[OK] - La vitesse (tu réécris "vitesse" ou "speed" selon langue)
[OK] - Les 'field influences' sont présent si un champ existe
[OK] - Les reactions (tu réécris "Reactions" comme c'est déjà le cas.
[OK] - La formule d'actions doit apparaitre

