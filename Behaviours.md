# Behaviours

## au 12/11/26

**Phase de délibération** (par cellule, sur l'état en début de step — rien n'est modifié) :
1. Réactif principal : mainReactiveId == -1 → cellule vide ; sinon elle doit contenir ce grain.
2. Prédicat d'âge : agePredicate.check(age).
3. Seuil de field (fieldPredicates) : valeur du field dans la cellule, après flatten(1e-14), comparée au seuil.
4. Prédicats de nombre de voisins (agentCountPredicates) : comptage des 8 directions.
5. Formule condition (formula, « Predicate » de l'UI).
6. Réactions : au moins un voisin du bon type dans une direction autorisée, par réaction.
→ Si plusieurs comportements passent : un tirage aléatoire en choisit un (1 par cellule).

7. fieldInfluences : ce n'est pas un test oui/non, il ne peut pas empêcher le comportement — il décide quels voisins seront consommés. Parmi les combinaisons de voisins valides (une par réaction, directions autorisées) : si le comportement a une influence non nulle, tirage pondéré par les fields (scores log-différence × poids, amplifiés à la puissance 6) ; sinon tirage uniforme.

**Phase d'exécution** (après délibération de toutes les cellules en mode « Parallel » / immédiatement en « Sequential ») :
8. Réaction principale (mainReactiveId → mainProductId).
9. Réactions : appliquées avec les voisins choisis en 7, triées par reactiveId.
10. Actions : exécutées en dernier, après la réaction principale et les réactions — elles voient donc les changements qu'elles viennent de faire (un Remove peut cibler un agent posé par la réaction principale, un Add peut poser un agent retiré par un Remove suivant, etc. — l'ordre dans la liste d'actions compte). Les count() dans leurs arguments sont calculés sur l'état de début de step (8 voisins), pas sur la grille modifiée.


# fieldInfluences — calcul et moment

**Quand :** seulement après que le comportement a passé tous les tests ci-dessus et a été choisi (un tirage aléatoire parmi les applicables). Il ne décide pas si le comportement a lieu, mais quel(s) voisin(s) est/sont consommé(s) : parmi les combinaisons valides de réactifs (un voisin par réaction, direction ∈ allowedDirection), le choix est pondéré par les fields au lieu d'être uniforme. C'est utilisé uniquement si le comportement a au moins une influence non nulle (fieldInfluenced) et que le modèle a des fields, sinon tirage uniforme (simulator.kt:397).

Calcul (simulator.kt:402-431) :

Pour chaque combinaison c (liste de voisins choisis) :

score(c) = Σ sur chaque voisin v de c, Σ sur chaque (fieldId → w) :
             log10(valeur du field dans la cellule du voisin v)
           − log10(valeur du field dans la cellule principale)
           puis  score += delta × w


Puis conversion en probabilités :

m = min des scores
pour chaque c :  intervalle(c) = (score(c) − m + 1)^6        // shift positif + 1, amplification à la puissance 6
cumul des intervalles → un tirage uniforme dans [0, total] sélectionne la combinaison


**Effet :**
- w > 0 : le voisinage dont la valeur du field est plus élevée que celle de la cellule principale augmente son score → attraction vers les fortes concentrations (chimiotaxie positive).
- w < 0 : attraction vers les valeurs plus basses.
- La puissance 6 rend l'effet très fort : la combinaison la mieux notée capte l'écrasante majorité de la probabilité, les autres ne sont choisies que très rarement (et le +1 garantit qu'aucune n'a une probabilité nulle).
- L'échelle logarithmique rend l'influence relative (un ratio de concentration ×10 vaut le même delta quel que soit le niveau absolu).

Les valeurs lues sont celles du début de step (tableaux de fields courants + snapshot des voisins) ; le choix détermine les usedNeighbours qui sont verrouillés pour l'application de la réaction (dans le mode « Parallel » par défaut, l'application effective a lieu après délibération de toutes les cellules).

Note : les valeurs de field sont bornées en dessous par minField = 10⁻¹⁵, donc log10 est toujours défini dans ce calcul.
