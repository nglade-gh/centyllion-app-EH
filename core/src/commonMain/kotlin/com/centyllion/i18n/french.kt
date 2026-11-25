package com.centyllion.i18n

object French: Locale {
    override val name: String = "fr-FR"

    override val label: String = "Français"

    override fun value(key: String): String? = when (key) {
        "Grain with id %0 doesn't exist" -> "L'agent avec l'id %0 n'existe pas"
        "Behaviour must have a main reactive" -> "Le comportement doit avoir un réactif principal"
        "Probability must be between 0 and 1" -> "La vitesse doit être entre 0 et 1"
        "No direction allowed for reactive %0" -> "Aucune direction autorisée pour le réactif %0"
        "Grain with id %0 doesn't exist for reactive %1" -> "L'agent avec l'id %0 n'existe pas pour le réactif %1"
        "Half-life must be positive or zero" -> "La demi-vie doit être positive ou zéro"
        "Field production for %0 must be between -1 and 1" -> "La production du champ %0 doit être entre -1 et 1"
        "Field permeability will prevent production for %0" -> "La perméabilité du champ %0 empêche sa production"
        "Field influence for %0 must be between -1 and 1" -> "L'influence du champ %0 doit être entre -1 et 1"
        "Field permeability for %0 must be between 0 and 1" -> "La perméabilité du champ %0 doit être entre 0 et 1"
        "Field threshold value for %0 must be between 0 and 1" -> "Le seuil de champ pour %0 doit être entre 0 et 1"

        "Previous" -> "Précédent"
        "Next" -> "Suivant"
        "Ok" -> "Ok"
        "%0 of %1" -> "%0 sur %1"

        "Anonymous" -> "Anonyme"
        "Logout" -> "Se déconnecter"
        "Log In" -> "Se connecter"
        "Register" -> "Créer un compte"

        "Examples" -> "Exemples"
        "Home" -> "Ma page"
        "Show" -> "Présenter"
        "Subscribe" -> "S'abonner"
        "Administration" -> "Administration"

        "Documentation" -> "Documentation"
        "Contact Us" -> "Contactez-nous"

        "version of %0" -> "version du %0"

        "all" -> "tout"
        "Model" -> "Modèle"
        "Models" -> "Modèles"
        "models" -> "modèles"
        "Simulation" -> "Simulation"
        "Simulations" -> "Simulations"
        "simulations" -> "simulations"
        "Environment" -> "Environnement"

        "by %0" -> "par %0"
        "by me" -> "à moi"
        "New Model" -> "Nouveau Modèle"
        "Model Name" -> "Nom du Modèle"
        "Simulation Name" -> "Nom de la Simulation"
        "Description" -> "Description"
        "Model information" -> "Informations sur le modèle"
        "Model Description" -> "Description du Modèle"
        "Simulation Description" -> "Description de la Simulation"
        "Clone Model" -> "Cloner le Modèle"
        "Clone Simulation" -> "Cloner la Simulation"
        "Import" -> "Charger"
        "Export" -> "Sauver"
        "Save As" -> "Enregistrer sous…"
        "Expert Mode" -> "Mode Expert"
        "Log-in to save" -> "Connectez-vous pour sauver"
        "Delete Model" -> "Supprimer le Modèle"
        "Delete Simulation" -> "Supprimer la Simulation"
        "New Simulation" -> "Nouvelle Simulation"
        "Save state as thumbnail" -> "Enregistrer comme miniature"
        "Download screenshot" -> "Télécharger une capture"
        "Download Model" -> "Télécharger le Modèle"
        "Download Simulation" -> "Télécharger la Simulation"
        "Loading simulations" -> "Chargement des simulations"
        "Model %0 saved." -> "Modèle %0 enregistré."
        "Simulation %0 saved." -> "Simulation %0 enregistré."
        "Updated speed for %0 to %1." -> "Mise à jour de la vitesse de %0 à %1."
        "Current state saved as thumbnail." -> "État courant enregistré comme miniature."
        "Model %0 and simulation %1 saved." -> "Modèle %0 et simulation %1 enregistrés."
        "Model and simulation cloned." -> "Modèle et simulation clonés."
        "New simulation." -> "Nouvelle simulation."
        "Simulation cloned." -> "Simulation clonée."
        "Model %0 deleted." -> "Modèle %0 supprimé."
        "Load new simulation. Are you sure ?" -> "Charger la nouvelle simulation. Êtes-vous sur ?"
        "You're about to delete the simulation '%0'." -> "Vous allez supprimer la simulation '%0'."
        "Delete model. Are you sure ?" -> "Supprimer le modèle. Êtes-vous sur ?"
        "You're about to delete the model '%0' and its simulations." -> "Vous allez supprimer le modèle '%0' et ses simulations."
        "This action can't be undone." -> "Cette action ne peut-être annulée."
        "Simulation %0 deleted." -> "La simulation %0 a été supprimée"
        "Modifications not saved. Do you wan't to save ?" -> "Des modifications n'ont pas été enregistrées. Voulez-vous enregistrer ?"
        "You're about to quit the page and some modifications haven't been saved." -> "Vous allez quitter la page mais des modifications n'ont pas été enregistrées."
        "Yes" -> "Oui"
        "No" -> "Non"
        "Don't save" -> "Ne pas enregistrer"
        "Stay here" -> "Rester sur la page"

        "My models and simulations" -> "Mes modèles et simulations"
        "My Recent simulation" -> "Mes simulations récentes"

        "Search" -> "Chercher"
        "Searching" -> "Recherche en cours"
        "No simulation found" -> "Aucune simulation trouvée"
        "No model found" -> "Aucun modèle trouvé"
        "Recent simulations" -> "Simulations récentes"
        "Featured" -> "Sélection de simulations"

        "Public models" -> "Modèles publics"
        "Monitoring" -> "Monitoring"
        "Users" -> "Utilisateurs"
        "Asset" -> "Ressource"
        "Assets" -> "Ressources"
        "Send" -> "Envoyer"
        "Asset created with id %0." -> "Ressource créée avec l'id %0."

        "New tag" -> "Ajouter un tag"
        "Tags" -> "Tags"
        "Popular" -> "Populaires"

        "Select a element to edit it" -> "Sélectionner un élément pour l'éditer"
        "Message" -> "Message"
        "Source" -> "Source"
        "Field" -> "Champ"
        "Fields" -> "Champs"
        "Grain" -> "Agent"
        "Grains" -> "Agents"
        "Behaviour" -> "Comportement"
        "Behaviours" -> "Comportements"
        "Name" -> "Nom"
        "About" -> "À propos"
        "About NetBioDyn" -> "À propos de NetBioDyn"
        "Version" -> "Version"
        "Authors" -> "Auteurs"
        "Git repository" -> "Dépôt git"
        "Open" -> "Ouvrir"
        "unknown" -> "inconnue"
        "Multi-agent modelling and simulation environment." -> "Environnement de modélisation et de simulation multi-agents."
        "Display" -> "Affichage"
        "Invisible" -> "Invisible"
        "Speed" -> "Vitesse"
        "Half-life" -> "Demi-vie"
        "Movement" -> "Déplacement"
        "Boundary conditions" -> "Conditions aux limites"
        "Left" -> "Gauche"
        "Right" -> "Droite"
        "Top" -> "Haut"
        "Bottom" -> "Bas"
        "Toric" -> "Torique"
        "Productions" -> "Productions"
        "Influences" -> "Influences"
        "Permeability" -> "Perméabilités"
        "Size" -> "Taille"
        "Reactives" -> "Réactifs"
        "Directions" -> "Directions"
        "Products" -> "Produits"
        "Sources" -> "Avec l'âge de"
        "none" -> "aucun"
        "reactive %0" -> "réactif %0"
        "Field thresholds" -> "Seuils des champs"
        "Field influences" -> "Influences des champs"
        "Misc" -> "Information"
        "Simulation age" -> "Âge de la simulation"
        "Each line is a condition on the age of the agent at the current step: 1 at the agent's first step, 0 for a spontaneous generation (there is no agent yet)." -> "Chaque ligne est une condition sur l'âge de l'agent au pas courant : 1 au premier pas de l'agent, 0 pour une génération spontanée (aucun agent n'existe encore)."
        "All the lines must be true at the same time (AND). They are also ANDed with the field thresholds, with the neighbour counts and, in expert mode, with the predicate formula." -> "Toutes les lignes doivent être vraies en même temps (ET). Elles sont également combinées en ET avec les seuils des champs, avec les prédicats de voisins et, en mode expert, avec la formule de prédicat."
        "The agent must have existed for at least 10 steps" -> "L'agent doit exister depuis au moins 10 pas"
        "The behaviour applies only between the 10th and the 20th step of the agent" -> "Le comportement ne s'applique qu'entre le 10e et le 20e pas de l'agent"
        "Every age except 5" -> "Tous les âges sauf 5"
        "Each line compares the value of a field on the behaviour cell (between 0 and 1, read at the start of the step) with a threshold." -> "Chaque ligne compare la valeur d'un champ sur la case du comportement (entre 0 et 1, lue au début du pas) à un seuil."
        "All the lines must be true at the same time (AND). They are also ANDed with the simulation age lines, with the neighbour counts and, in expert mode, with the predicate formula." -> "Toutes les lignes doivent être vraies en même temps (ET). Elles sont également combinées en ET avec les lignes d'âge de la simulation, avec les prédicats de voisins et, en mode expert, avec la formule de prédicat."
        "The behaviour applies only if the sugar field on the cell is above 0.3" -> "Le comportement ne s'applique que si le champ sugar de la case dépasse 0,3"
        "Each line counts the neighbours of the chosen type among the 8 adjacent cells (0 to 8) and compares that count with the value." -> "Chaque ligne compte les voisins du type choisi parmi les 8 cases adjacentes (de 0 à 8) et compare ce nombre à la valeur."
        "'All agents' counts every occupied neighbour cell; a chosen agent type counts only that type (same as count(grainId) in a formula)." -> "« Tous les agents » compte toutes les cases voisines occupées ; un type d'agent choisi ne compte que ce type (équivalent de count(grainId) dans une formule)."
        "All the lines must be true at the same time (AND), like the simulation age lines and the field thresholds." -> "Toutes les lignes doivent être vraies en même temps (ET), comme les lignes d'âge de la simulation et les seuils des champs."
        "At least 3 neighbours of type A" -> "Au moins 3 voisins de type A"
        "No occupied neighbour cell at all" -> "Aucune case voisine occupée"
        "The graphical predicates entered in normal mode (simulation age lines, field thresholds and neighbour counts) are also evaluated: the behaviour applies only if they are all true AND the formula is true. Remove the graphical lines to let the formula alone decide." -> "Les prédicats graphiques saisis en mode normal (lignes d'âge de la simulation, seuils des champs et nombres de voisins) sont aussi évalués : le comportement ne s'applique que s'ils sont tous vrais ET que la formule est vraie. Supprimez les lignes graphiques pour laisser la formule décider seule."
        "Example" -> "Exemple"
        "Close" -> "Fermer"
        "Age" -> "Âge"
        "Reactions" -> "Réactions"
        "The distributed models cannot be removed from the gallery." -> "Les modèles distribués ne peuvent pas être retirés de la galerie."
        "Predicates" -> "Conditions"
        "Nb Neighbours" -> "Nb voisins"
        "Nb" -> "Nb"
        "All agents" -> "Tous les agents"
        "Action list" -> "Liste d'actions"
        "Url" -> "Url"
        "Position (x,y,z)" -> "Position (x,y,z)"
        "Position x" -> "Position x"
        "Position y" -> "Position y"
        "Position z" -> "Position z"
        "Scale (x,y,z)" -> "Échelle (x,y,z)"
        "Scale x" -> "Échelle x"
        "Scale y" -> "Échelle y"
        "Scale z" -> "Échelle z"
        "Rotation (x,y,z)" -> "Rotation (x,y,z)"
        "Rotation x" -> "Rotation x"
        "Rotation y" -> "Rotation y"
        "Rotation z" -> "Rotation z"
        "Fine" -> "Fin"
        "Small" -> "Petit"
        "Medium" -> "Moyen"
        "Large" -> "Grand"
        "Formula" -> "Formule"
        "Predicate" -> "Prédicat"
        "Actions" -> "Actions"
        "Arguments" -> "Arguments"
        "Sliding window" -> "Fenêtre glissante"
        "Window" -> "Fenêtre"
        "Predicate (e.g. count(1) > 2 && count(2) == 1)" -> "Prédicat (ex. count(1) > 2 && count(2) == 1)"
        "Actions (e.g. Add(0, 1, 0), Remove(1, 0, 0))" -> "Actions (ex. Add(0, 1, 0), Remove(1, 0, 0))"
        "Actions are executed when the behaviour is applied, after the reactions." -> "Les actions sont exécutées quand le comportement est appliqué, après les réactions."
        "Adds an agent of type id at offset (dx, dy) from the behaviour cell (only if the target cell is empty)" -> "Ajoute un agent de type id à l'offset (dx, dy) depuis la case du comportement (uniquement si la case cible est vide)"
        "Removes the agent of type id at offset (dx, dy) from the behaviour cell" -> "Supprime l'agent de type id à l'offset (dx, dy) depuis la case du comportement"
        "Moves the agent of type id from offset (dx1, dy1) to offset (dx2, dy2) of the behaviour cell (empty target or same type)" -> "Déplace l'agent de type id de l'offset (dx1, dy1) à l'offset (dx2, dy2) de la case du comportement (case cible vide ou même type)"
        "Sets the value of field id at offset (dx, dy) to value (clamped between 0 and 1)" -> "Fixe la valeur du champ id à l'offset (dx, dy) à value (valeur bornée entre 0 et 1)"
        "Adds delta to the value of field id at offset (dx, dy) (result clamped between 0 and 1)" -> "Ajoute delta à la valeur du champ id à l'offset (dx, dy) (résultat borné entre 0 et 1)"
        "Sets the value of field id at offset (dx, dy) to 0" -> "Remet la valeur du champ id à l'offset (dx, dy) à 0"
        "Offsets are relative to the behaviour cell and wrap around the grid (toroidal)." -> "Les offsets sont relatifs à la case du comportement et enroulent sur la grille (torique)."
        "Variables, count(grainId) and field(id, dx, dy) can also be used in the arguments." -> "Les variables, count(grainId) et field(id, dx, dy) peuvent aussi être utilisées dans les arguments."
        "Order of reactions and actions" -> "Ordre des réactions et des actions"
        "Within a single behaviour, the reactions are applied first, then the actions. Within the actions, the order matters: successive actions are applied one after the other, each seeing the effect of the previous ones. Note that count(id) is evaluated on the state at the beginning of the step, before the reactions and the actions are applied." -> "Au sein d'un même comportement, les réactions sont appliquées d'abord, puis les actions. Au sein des actions, l'ordre compte : les actions successives sont appliquées l'une après l'autre, chacune voyant l'effet des précédentes. Notez que count(id) est évalué sur l'état du début du pas, avant l'application des réactions et des actions."
        "Example: behaviour with an empty central cell and a B neighbour to the left at (-1,0):" -> "Exemple : comportement avec une case centrale vide et un voisin B à gauche en (-1,0) :"
        "main reaction, on the empty central cell" -> "réaction principale, sur la case centrale vide"
        "reaction, B allowed only to the left at (-1,0)" -> "réaction, B autorisé uniquement à gauche en (-1,0)"
        "actions, in this order" -> "actions, dans cet ordre"
        "Application (if the behaviour is selected):" -> "Application (si le comportement est tiré au sort) :"
        "centre becomes A (main reaction)" -> "centre → A (réaction principale)"
        "B at (-1,0) stays B (identity)" -> "B à (-1,0) reste B (identité)"
        "removes the A from the centre (placed by the main reaction) -> empty cell" -> "retire l'A du centre (celui posé par la réaction principale) → case vide"
        "removes the B at (-1,0) -> empty" -> "retire le B de (-1,0) → vide"
        "places A in the centre (empty) -> A" -> "pose A au centre (vide) → A"
        "Net result: centre = A, B on the left removed. Remove(0,0,0) has an effect only because it runs after the main reaction (which places the A) and before the Add: if the Add had come before the Remove, the centre would end up empty." -> "Bilan net : centre = A, B à gauche supprimé. Remove(0,0,0) n'a d'effet que parce qu'il s'exécute après la réaction principale (qui pose l'A) et avant l'Add : si l'Add avait précédé le Remove, le centre finirait vide."
        "Save As: saved" -> "Fichier enregistré :"
        "Save As: bridge unavailable" -> "Le fichier a été téléchargé. Pour ouvrir l'explorateur de fichiers natif dans ce navigateur, exécutez l'assistant local sur cette machine (python3 save_bridge.py — voir la documentation du projet) et accédez à l'application en HTTP ; Chrome ou Edge en HTTPS ou sur localhost fonctionne sans l'assistant."
        "Formula Fields" -> "Champs calculés"
        "Formula Parameters" -> "Paramètres de la formule"
        "Current simulation step" -> "Étape de simulation courante"
        "Slot x position" -> "Position en x de la case courante"
        "Slot y position" -> "Position en y de la case courante"
        "Current field value (value if no formula is provided)" -> "Valeur du champ courant (valeur si aucune formule n'est donnée)"
        "Model functions:" -> "Fonctions sur le modèle"
        "Operators and functions" -> "Opérateurs et fonctions"
        "Mathematical operators" -> "Opérateurs mathematiques"
        "Modulo: returns the remainder of a division, after one number is divided by another" -> "Modulo: retourne le reste de la division entière"
        "Exponentiation: a^b means a raised to the power of b" -> "Exponentielle: a^b est a puissance b"
        "Logical 'and', 'or', 'not' operators" -> "Opérateurs logiques 'and', 'or', 'not'"
        "Equality operators" -> "Opérateurs d'égalités"
        "Comparison operators" -> "Opérateurs de comparaisons"
        "If else ternary operator" -> "If else operateur ternaire"
        "PI and E constants" -> "Constantes PI et E"
        "Absolute value" -> "Valeur absolue"
        "Average of n values" -> "Moyenne de n valeurs"
        "Trigonometry function including arc (acos, asin, atan) and hyperbolic (cosh, sinh, tanh)" -> "Fonctions trigonometriques avec arc (acos, asin, atan) et hyperbolique (cosh, sinh, tanh)"
        "Floor, ceil and round functions" -> "Fonctions d'arrondis floor, ceil et round"
        "Logarithmic functions" -> "Fonctions logarithmiques"
        "Min and max functions" -> "Fonctions min et max"
        "Trigonometry functions (also asin, acos, atan)" -> "Fonctions trigonométriques (aussi asin, acos, atan)"
        "Number of neighbour agents with the given id (8 adjacent cells)" -> "Nombre d'agents voisins de l'ID donné (8 cases adjacentes)"
        "Help" -> "Aide"
        "Value of field id at the current cell" -> "Valeur du champ d'ID id sur la case courante"
        "Value of field id at offset (dx, dy) from the current cell (e.g. 1, 0 = east neighbour)" -> "Valeur du champ d'ID id à l'offset (dx, dy) depuis la case courante (ex. 1, 0 = voisin à l'est)"
        "Agent id at position (x, y), -1 if none" -> "ID de l'agent à la position (x, y), -1 s'il n'y en a pas"
        "Evaluated for each agent that could apply the behaviour." -> "Évaluée pour chaque agent susceptible d'appliquer le comportement."
        "The formula must be a logical predicate returning true or false. Any other value (e.g. sin(step)) makes the behaviour never applicable." -> "La formule doit être un prédicat logique renvoyant true ou false. Toute autre valeur (par ex. sin(step)) rend le comportement inapplicable."
        "Age of reactive n (1 = main agent, 2 to N+1 = neighbours of the reactions, same numbering as age inheritance)" -> "Âge du réactif n (1 = agent principal, 2 à N+1 = voisins des réactions, même numérotation que l'héritage d'âge)"
        "Position of the cell" -> "Position de la case"
        "At least 3 neighbours of agent 0" -> "Au moins 3 voisins de l'agent 0"
        "Between 3 and 4 neighbours of agent 0" -> "Entre 3 et 4 voisins de l'agent 0"
        "Age at least 10 and no neighbour of agent 1" -> "Âge au moins 10 et aucun voisin de l'agent 1"
        "At least 4 neighbours of agent 0 and field 1 below 0.5 on the cell" -> "Au moins 4 voisins de l'agent 0 et champ 1 inférieur à 0.5 sur la case"
        "Summation function" -> "Fonctions de somme"

        "Step" -> "Pas"
        "No line to show" -> "Aucune ligne à montrer"

        "Loading" -> "Chargement"

        "Total %0, this week %1, this month %2" -> "Total %0, cette semaine %1, ce mois %2"

        "%0 must be a number" -> "%0 doit être un nombre"
        "%0 must be between %1 and %2" -> "%0 doit être entre %1 et %2"

        "Simulation Settings" -> "Paramètres de simulation"
        "Background Color" -> "Couleur du fond"
        "Grid" -> "Grille"
        "Image URL" -> "URL de l'image"

        "Tutorial" -> "Tutoriel"
        "Tutorial '%0'" -> "Tutoriel '%0'"
        "Create a simple bacterias simulation" -> "Créer une simulation simple de bactéries"
        "With this tutorial you will create a simulation bacterias division with only one grain and one behaviour." -> "Avec ce tutoriel, vous allez créer une simulation de division de bactéries avec un agent et un comportement."
        "Create a bacteria grain" -> "Créer un agent de bacterie"
        "Click on " -> "Cliquez sur "
        " to add a grain to the simulation." -> " pour ajouter un agent à la simulation."
        "Change the name" -> "Changer le nom"
        "You can change the grain name, for instance to 'bact'." -> "Vous pouvez changer le nom, par exemple en 'bact'."
        "Set the speed" -> "Changer la vitesse"
        "Change the speed to 1 to let the bacteria move." -> "Changer la vitesse à 1 pour que la bactérie se déplace."
        "Go to simulation" -> "Aller sur simulation"
        "Open the simulation page to test the model." -> "Ouvrez la page de simulation pour tester le modèle."
        "Draw some grains" -> "Dessiner quelques agents"
        "Draw %0 bacterias with the random spray." -> "Dessinez %0 bacteries avec le l'outil aléatoire."
        "Run the simulation" -> "Lancer la simulation"
        "Watch the bacterias move." -> "Regardez les bactéries se déplacer."
        "Stop the simulation" -> "Arreter la simulation"
        "Ok, the bacterias moves." -> "Bon, les bacteries se déplacent."
        "Go to model" -> "Aller sur le modèle"
        "Open the model page to add a division behaviour to make them grow." -> "Ouvrez la page de modèle pour ajouter un comportement de division pour que les bactéries se développent."
        "Create a division behaviour" -> "Créer un comportement de division"
        " to add a behaviour to the simulation." -> " pour ajouter un comportement à la simulation."
        "First product" -> "Premier produit"
        "Select the bacteria grain as first product." -> "Sélectionnez la bacterie comme premier produit."
        "Adds a second product" -> "Ajouter un second produit"
        " to add a second line in the behaviour" -> " pour ajouter une seconde ligne dans le comportement"
        "Second product" -> "Second produit"
        "Select the bacteria grain as second product." -> "Sélectionnez la bacterie comme second produit."
        "Return to simulation" -> "Retourner à la simulation"
        "Go back to the simulation page." -> "Retournez à la page de simulation."
        "Watch the bacteria colony grow." -> "Regardez la colonie de bactéries se développer."
        "You've just created a simulation with Centyllion, well done 👍." -> "Vous venez de créer une simulation avec Centyllion, bravo 👍."
        "You can now for instance:" -> "Vous pouvez maintenant:"
        "Set a half-life for bacterias to give them a life-span." -> "Donnez une demi-vie aux bacteries afin de limiter leur durée de vie."
        "Add a sugar field to feed the bacterias." -> "Ajoutez un champ de sucre pour nourrir les bactéries."
        "Create another bacteria to compete with." -> "Créez une autre bactérie compétitive."
        "You can find some documentation here " -> "La documentation est disponible ici "
        "Start tutorial" -> "Démarrer le tutoriel"
        "Ok but later" -> "Ok mais plus tard"
        "I don't need it" -> "Je n'ai pas besoin"

        "With this tutorial you will add to the simulation a field to feed the bacterias." -> "Avec ce tutoriel, vous allez ajouter un champ à la simulation pour nourrir les bacteries."
        "Open the model page to add a field." -> "Ouvrez le modèle pour ajouter un champ."
        "Create a sugar field" -> "Créer une champ de sucre"
        " to add a field to the simulation." -> " pour ajouter un champ à la simulation."
        "You can change the field name, for instance to 'sugar'." -> "Vous pouvez changer le nom du champ, par exemple: 'sucre'."
        "Create a source grain" -> "Créer un agent source"
        " to add another grain to produce the 'sugar' field." -> " pour ajouter un autre agent pour produire le champ 'sucre'."
        "Produce 'sugar'" -> "Produire du 'sucre'"
        "Set the production of 'sugar' field to 1." -> "Mettez la production du champ 'sucre' à 1."
        "Select the bacteria grain" -> "Sélectionner l'agent 'bactérie'"
        "Make the bacterias attracted to sugar." -> "Rendez les bactéries attirées par 'sucre'."
        "Influenced by 'sugar'" -> "Influencés par 'sucre'"
        "Set the influence of 'sugar' above to 0.5." -> "Mettez l'influence du 'sucre' au dessus de 0.5."
        "Select the behavior" -> "Sélectionner le comportement"
        "Let's constrain the division with the 'sugar' field." -> "Contraignez la division avec le champ 'sucre'."
        "Adds a field threshold" -> "Ajouter un seuil de champ"
        "Add a field constrain predicate to limit the behaviour to be only executed when the field is present." -> "Ajoutez un prédicat de contrainte pour limiter le comportement que lorsque le champ est présent."
        "Sets the threshold to 0.01" -> "Mettre le seuil à 0.01"
        "The field value around a grain that produces it diminishes rapidly." -> "La valeur du champ autour du agent source diminue rapidement."
        "Select the source grain" -> "Sélectionner l'agent source"
        "Let's add some sources." -> "Ajoutez des sources de 'sucre'."
        "Draw some source" -> "Dessiner des sources"
        "Draw %0 source with the random spray." -> "Dessinez %0 source avec le l'outil aléatoire."
        "Watch the bacteria colony grow around the sugar sources." -> "Regardez les bactéries se développer autour des sources de sucre."
        "Now you know how to use fields with Centyllion, well done 👍." -> "Maintenant vous savez utiliser les champs avec Centyllion, bravo 👍."
        "Change the field threshold for sugar division (try 0.001 or 1e-6 (0.000001)." -> "Changer le seuil du champ sucre pour la division (essayez 0.001 ou 1e-6 (0.000001)."
        "Prevents the sources from moving." -> "Empécher les sources de sucre de bouger."
        "Makes the bacterias consume the sugar (production to -0.5)." -> "Rendre les bacterias consommatrice de sucre (production à -0.5)."
        "Feed bacterias with sugar" -> "Du sucre pour les bactéries"

        // --- tutoriels experts (jeu de la vie, actions, formule de champ)
        "Tutorials" -> "Tutoriels"
        "Choose a tutorial" -> "Choisir un tutoriel"
        "Beginner" -> "Débutant"
        "Expert" -> "Expert"
        "Choose a tutorial. The expert tutorials assume you already know how to create agents, behaviours and fields." -> "Choisissez un tutoriel. Les tutoriels experts supposent que vous savez déjà créer des agents, des comportements et des champs."
        "They are also chained: the conclusion of a tutorial proposes the next one." -> "Ils s'enchaînent aussi : la conclusion d'un tutoriel propose le suivant."
        "Conway's Game of Life (expert mode)" -> "Le jeu de la vie de Conway (mode expert)"
        "With this tutorial you will write Conway's Game of Life: one agent, three behaviours and three formulas that you type yourself." -> "Avec ce tutoriel, vous allez écrire le jeu de la vie de Conway : un agent, trois comportements et trois formules que vous tapez vous-même."
        "The agent and the three behaviours (survive, die, birth) are already created, with their reactants and products. Your job is the rules, written with count(0) — the number of neighbours of the same agent around the cell." -> "L'agent et les trois comportements (survive, die, birth) sont déjà créés, avec leurs réactifs et leurs produits. Votre travail, ce sont les règles, écrites avec count(0) — le nombre de voisins du même agent autour de la case."
        "A cell survives with 2 or 3 neighbours, dies with less than 2 or more than 3, and is born in an empty place surrounded by exactly 3 neighbours." -> "Une cellule survit avec 2 ou 3 voisins, meurt avec moins de 2 ou plus de 3, et naît dans une case vide entourée d'exactement 3 voisins."
        "Open the model page" -> "Ouvrir la page modèle"
        "The model is created: go to the model page to write the three rules." -> "Le modèle est créé : allez sur la page modèle pour écrire les trois règles."
        "Enable expert mode" -> "Activer le mode expert"
        "Formulas are only editable in expert mode: switch on 'Expert Mode'." -> "Les formules ne sont modifiables qu'en mode expert : activez « Expert Mode »."
        "Open the 'survive' behaviour" -> "Ouvrir le comportement « survive »"
        "Select the behaviour named 'survive': it keeps a cell alive, its product is the cell itself." -> "Sélectionnez le comportement nommé « survive » : il maintient la cellule en vie, son produit est la cellule elle-même."
        "Survival rule" -> "Règle de survie"
        "In the predicate formula, write: %0" -> "Dans la formule de prédicat, écrivez : %0"
        "count(0) is the number of neighbouring cells of the same agent (the cell itself is not counted)." -> "count(0) est le nombre de cases voisines occupées par le même agent (la case elle-même n'est pas comptée)."
        "Open the 'die' behaviour" -> "Ouvrir le comportement « die »"
        "Select the behaviour named 'die': its main product is already 'none', so the cell disappears." -> "Sélectionnez le comportement nommé « die » : son produit principal est déjà « aucun », la cellule disparaît donc."
        "Death rule" -> "Règle de mort"
        "Open the 'birth' behaviour" -> "Ouvrir le comportement « birth »"
        "Select the behaviour named 'birth': its main reactive is 'none' (spontaneous generation), its product is the cell." -> "Sélectionnez le comportement nommé « birth » : son réactif principal est « aucun » (génération spontanée) et son produit est la cellule."
        "Birth rule" -> "Règle de naissance"
        "Open the simulation page" -> "Ouvrir la page de simulation"
        "Let's draw a pattern and run it." -> "Dessinons un motif et lançons-le."
        "Draw a blinker" -> "Dessiner un clignotant"
        "Select the cell agent, take the pen and draw three cells in a vertical line (they must touch each other)." -> "Sélectionnez l'agent « cell », prenez le stylo et dessinez trois cellules en ligne verticale (elles doivent se toucher)."
        "Watch the line: it must oscillate between vertical and horizontal at every step. This pattern is a blinker." -> "Observez la ligne : elle doit osciller entre verticale et horizontale à chaque pas. C'est un clignotant."
        "You have just written Conway's Game of Life, well done 👍." -> "Vous venez d'écrire le jeu de la vie de Conway, bravo 👍."
        "Try the R-pentomino (5 cells): it evolves for more than 1000 steps before stabilising." -> "Essayez le R-pentomino (5 cellules) : il évolue plus de 1000 pas avant de se stabiliser."
        "Draw a glider (5 cells) and check that it moves by one cell every four steps." -> "Dessinez un planeur (5 cellules) et vérifiez qu'il avance d'une case tous les quatre pas."
        "Change the rules (for instance a cell survives with 2, 3 or 4 neighbours) and watch the patterns die out." -> "Changez les règles (par exemple une cellule survit avec 2, 3 ou 4 voisins) et regardez les motifs s'éteindre."
        "Add a second agent with another rule to build a two-species predator/prey world." -> "Ajoutez un second agent doté d'une autre règle pour construire un monde proie/prédateur à deux espèces."
        "Documentation on formulas: " -> "Documentation sur les formules : "
        "Actions: a trail that fades (expert mode)" -> "Actions : une piste qui s'efface (mode expert)"
        "With this tutorial you will use actions to make an agent leave a trail of crumbs behind it, and an age predicate to make the trail fade." -> "Avec ce tutoriel, vous allez utiliser les actions pour qu'un agent laisse derrière lui une piste de miettes, et un prédicat d'âge pour que la piste s'efface."
        "Actions are a comma separated list of calls executed when the behaviour is applied: Add(agent, dx, dy), Remove(agent, dx, dy), Move(agent, dx1, dy1, dx2, dy2), SetField(field, dx, dy, value), AddField(field, dx, dy, delta) and ClearField(field, dx, dy)." -> "Les actions sont une liste d'appels séparés par des virgules, exécutés quand le comportement s'applique : Add(agent, dx, dy), Remove(agent, dx, dy), Move(agent, dx1, dy1, dx2, dy2), SetField(champ, dx, dy, valeur), AddField(champ, dx, dy, delta) et ClearField(champ, dx, dy)."
        "dx and dy are offsets in cells around the behaviour cell, and can be formulas (x, y, step, count(id), age(n)…)." -> "dx et dy sont des décalages en cases autour de la case du comportement, et peuvent être des formules (x, y, step, count(id), age(n)…)."
        "The model is created: go to the model page to write the action and the age predicate." -> "Le modèle est créé : allez sur la page modèle pour écrire l'action et le prédicat d'âge."
        "Actions are only editable in expert mode: switch on 'Expert Mode'." -> "Les actions ne sont modifiables qu'en mode expert : activez « Expert Mode »."
        "Open the 'run' behaviour" -> "Ouvrir le comportement « run »"
        "Select the behaviour named 'run': it applies to the runner and keeps it alive." -> "Sélectionnez le comportement nommé « run » : il s'applique au coureur et le maintient en vie."
        "Leave a crumb" -> "Laisser une miette"
        "In the actions field, write: %0" -> "Dans le champ actions, écrivez : %0"
        "Add(1, 0, 1) creates an agent of type 1 (the crumb) one cell below the runner, if that cell is empty." -> "Add(1, 0, 1) crée un agent de type 1 (la miette) une case sous le coureur, si cette case est vide."
        "Open the 'fade' behaviour" -> "Ouvrir le comportement « fade »"
        "Select the behaviour named 'fade': it applies to the crumb, whose main product is already 'none' (the crumb disappears)." -> "Sélectionnez le comportement nommé « fade » : il s'applique à la miette, dont le produit principal est déjà « aucun » (la miette disparaît)."
        "Fade after six steps" -> "Effacement après six pas"
        "age(1) is the age of the main agent: the crumb is removed as soon as it is older than five steps." -> "age(1) est l'âge de l'agent principal : la miette est retirée dès qu'elle dépasse cinq pas."
        "Let's draw some runners and watch the trail." -> "Dessinons quelques coureurs et observons la piste."
        "Draw some runners" -> "Dessiner des coureurs"
        "Select the runner agent and spray a few agents (the spray can) on the grid." -> "Sélectionnez l'agent « runner » et pulvérisez quelques agents (bombe de spray) sur la grille."
        "Watch the runners move: each of them leaves a trail of crumbs that disappears after a few steps." -> "Regardez les coureurs se déplacer : chacun laisse une piste de miettes qui disparaît après quelques pas."
        "You know how to use actions, well done 👍." -> "Vous savez utiliser les actions, bravo 👍."
        "Eat a crumb above the runner with Remove(1, 0, -1)." -> "Mangez une miette au-dessus du coureur avec Remove(1, 0, -1)."
        "Make the crumbs fall with Move(1, 0, 0, 0, 1) in a behaviour applied to the crumb." -> "Faites tomber les miettes avec Move(1, 0, 0, 0, 1) dans un comportement appliqué à la miette."
        "Paint a field where the runner passes with AddField(0, 0, 0, 0.2) (a field must exist)." -> "Peignez un champ sur le passage du coureur avec AddField(0, 0, 0, 0.2) (un champ doit exister)."
        "Use a formula in an action: Add(1, 0, min(3, count(0))) spreads the crumbs further in crowded places." -> "Utilisez une formule dans une action : Add(1, 0, min(3, count(0))) écarte les miettes là où il y a du monde."
        "Field formula: a moving gradient (expert mode)" -> "Formule de champ : un gradient animé (mode expert)"
        "With this tutorial you will drive a field with a formula: instead of diffusing and decaying, the field is computed at every step for every cell." -> "Avec ce tutoriel, vous allez piloter un champ par une formule : au lieu de diffuser et de décroître, le champ est calculé à chaque pas pour chaque case."
        "The formula can use x and y (the cell coordinates), step (the simulation step), current (the value after diffusion and production), count(id) (the number of agents of type id around the cell) and field(id)." -> "La formule peut utiliser x et y (les coordonnées de la case), step (le pas de simulation), current (la valeur après diffusion et production), count(id) (le nombre d'agents de type id autour de la case) et field(id)."
        "The model already contains an agent and a field: you only have to write the formula and run the simulation." -> "Le modèle contient déjà un agent et un champ : il ne vous reste qu'à écrire la formule et à lancer la simulation."
        "The model is created: go to the model page to write the field formula." -> "Le modèle est créé : allez sur la page modèle pour écrire la formule du champ."
        "Open the field" -> "Ouvrir le champ"
        "Select the field named 'wave' to edit it." -> "Sélectionnez le champ nommé « wave » pour l'éditer."
        "Write the formula" -> "Écrire la formule"
        "In the formula field, write: %0" -> "Dans le champ formule, écrivez : %0"
        "x is the column, step the simulation step: the field becomes a wave that travels from left to right." -> "x est la colonne, step le pas de simulation : le champ devient une onde qui se déplace de gauche à droite."
        "Leave the speed and the half-life as they are: the formula replaces their result." -> "Laissez la vitesse et la demi-vie telles quelles : la formule remplace leur résultat."
        "Let's see the field moving." -> "Voyons le champ se déplacer."
        "The field is now a gradient coloured by the agent's field: watch the bands move as the simulation runs." -> "Le champ est maintenant un gradient coloré : regardez les bandes se déplacer pendant que la simulation tourne."
        "You can now drive a field with a formula, well done 👍." -> "Vous savez piloter un champ par une formule, bravo 👍."
        "Write x / 100 for a static gradient from left to right." -> "Écrivez x / 100 pour un gradient statique de gauche à droite."
        "Write current * 0.98 for a field that slowly fades without moving." -> "Écrivez current * 0.98 pour un champ qui s'estompe lentement sans se déplacer."
        "Write min(1, count(0) / 8) to paint the field where the agents are dense." -> "Écrivez min(1, count(0) / 8) pour peindre le champ là où les agents sont denses."
        "Write 0.5 + 0.5 * sin((x - y) / 8 - step / 4) for a diagonal wave." -> "Écrivez 0.5 + 0.5 * sin((x - y) / 8 - step / 4) pour une onde diagonale."
        "Value info" -> "Info valeurs"
        "No field" -> "Aucun champ"
        "Settings" -> "Paramètres"
        "Random seed" -> "Graine aléatoire"
        "Curves" -> "Courbes"
        "Window size" -> "Taille fenêtre"
        "Records" -> "Enregistrements"
        "Captures" -> "Captures"
        "Picture format" -> "Format des images"
        "JPEG" -> "JPEG"
        "PNG" -> "PNG"
        "JPEG quality" -> "Qualité JPEG"
        "Resolution" -> "Résolution"
        "Screen size" -> "Taille de l'écran"
        "Grid size" -> "Taille de la grille"
        "Pixels per cell" -> "Pixels par cellule"
        "CSV column separator" -> "Séparateur de colonnes CSV"
        "Tabulation" -> "Tabulation"
        "Comma" -> "Virgule"
        "Semicolon" -> "Point-virgule"
        "Space" -> "Espace"
        "These settings are kept by this browser, not in the project file." ->
            "Ces réglages sont conservés par ce navigateur, jamais dans le fichier du projet."
        "Open the help" -> "Ouvrir l'aide"
        "Behaviour has no effect (None -> None)" -> "Le comportement n'a aucun effet (None -> None)"
        "Behaviour produces no agent (None -> None): its actions alone act on the simulation, for example SetField(0, 0, 0, 1), possibly under a predicate on the simulation step." -> "Le comportement ne produit aucun agent (None -> None) : seules ses actions agissent sur la simulation, par exemple SetField(0, 0, 0, 1), éventuellement sous condition d'un prédicat sur l'étape de simulation."
        "Display warnings" -> "Afficher les avertissements"
        "Are you sure you want to switch to expert mode? What should be done with the predicates you entered?" -> "Êtes-vous certain de vouloir passer en mode expert ? Que faire des prédicats que vous avez saisis ?"
        "Conversion is not possible: a neighbour count over all agents has no formula equivalent." -> "Conversion impossible : un comptage de voisins « tous les agents » n'a pas d'équivalent en formule."
        "Clear" -> "Vider"
        "Convert" -> "Convertir"
        "Cancel" -> "Annuler"
        "Expert mode" -> "Mode expert"
        "None" -> "Aucun"
        "Custom" -> "Personnalisée"
        "Width" -> "Largeur"
        "Height" -> "Hauteur"
        "Preset" -> "Préréglée"
        "Opacity" -> "Opacité"
        "Meta-actions: act on the whole simulation" -> "Méta-actions : agir sur toute la simulation"
        "Identifiers, activation, opacity and grid size" -> "Identifiants, activation, opacité et taille de grille"
        "Populate and erase" -> "Peupler et gommer"
        "Pause and activation" -> "Pause et activation"
        "Behaviours: identifier and activation" -> "Comportements : identifiant et activation"
        "Field opacity" -> "Opacité des champs"
        "Grid size" -> "Taille de la grille"
        "Simulation modifiers" -> "Modificateurs de simulation"
        "Simulation modifier" -> "Modificateur de simulation"
        "Modifiers" -> "Modificateurs"
        "Modifier" -> "Modificateur"
        "Condition of a modifier" -> "Condition d'un modificateur"
        "Meta-actions" -> "Méta-actions"
        "Condition" -> "Condition"
        "Active" -> "Actif"
        "Probability" -> "Probabilité"

        // model gallery
        "Gallery" -> "Galerie"
        "Model gallery" -> "Galerie de modèles"
        "Add Model" -> "Ajouter un modèle"
        "Add a Model" -> "Ajouter un modèle"
        "Edit the Model" -> "Modifier le modèle"
        "Export my gallery" -> "Exporter ma galerie"
        "Import my gallery" -> "Importer ma galerie"
        "Your additions stay in this browser; export them to keep them or to transfer them." ->
            "Vos ajouts restent dans ce navigateur ; exportez-les pour les conserver ou les transférer."
        "The export is large (%0 MB): the download may take a while." ->
            "L'export est volumineux (%0 Mo) : le téléchargement peut prendre un moment."
        "This file is not a gallery export: nothing was imported." ->
            "Ce fichier n'est pas un export de galerie : rien n'a été importé."
        "Import done: %0 added, %1 replaced." -> "Import terminé : %0 ajoutée(s), %1 remplacée(s)."
        "Loading gallery..." -> "Chargement de la galerie…"
        "The gallery index could not be loaded." -> "L'index de la galerie n'a pas pu être chargé."
        "No model in the gallery yet." -> "Aucun modèle dans la galerie pour le moment."
        "Open this model in NetBioDyn?" -> "Ouvrir ce modèle dans NetBioDyn ?"
        "OK" -> "OK"
        "The current project will be replaced. Unsaved modifications will be lost." ->
            "Le projet courant sera remplacé. Les modifications non enregistrées seront perdues."
        "This model cannot be opened: it has neither a file nor a model text." ->
            "Ce modèle ne peut pas être ouvert : il n'a ni fichier ni texte de modèle."
        "The model file could not be loaded." -> "Le fichier du modèle n'a pas pu être chargé."
        "The model could not be decoded." -> "Le modèle n'a pas pu être décodé."
        "Model loaded from the gallery." -> "Modèle chargé depuis la galerie."
        "Delete this model from the gallery?" -> "Supprimer ce modèle de la galerie ?"
        "The entry is only hidden: gallery.json is never modified." ->
            "L'entrée est seulement masquée : gallery.json n'est jamais modifié."
        "The model is removed from the local gallery of this browser." ->
            "Le modèle est retiré de la galerie locale de ce navigateur."
        "Model removed from the gallery." -> "Modèle retiré de la galerie."
        "Model added to the gallery." -> "Modèle ajouté à la galerie."
        "Model updated in the gallery." -> "Modèle mis à jour dans la galerie."
        "Edit" -> "Modifier"
        "Delete" -> "Supprimer"
        "Save" -> "Enregistrer"
        "Title" -> "Titre"
        "Model file (.nbd2 or .netbiodyn)" -> "Fichier de modèle (.nbd2 ou .netbiodyn)"
        "Choose a model file" -> "Choisir un fichier de modèle"
        "Choose a model file." -> "Choisissez un fichier de modèle."
        "Description (optional)" -> "Description (facultatif)"
        "Image (optional)" -> "Image (facultatif)"
        "Choose an image" -> "Choisir une image"
        "A title is required." -> "Un titre est requis."
        "The model file is too large (over 2 MB)." -> "Le fichier de modèle est trop volumineux (plus de 2 Mo)."
        "The selected file could not be read." -> "Le fichier sélectionné n'a pas pu être lu."
        "Thumbnail generated from the initial state." -> "Vignette générée à partir de l'état initial."
        "The gallery could not be saved in the browser storage." ->
            "La galerie n'a pas pu être enregistrée dans le stockage du navigateur."
        // personal gallery: the disk (working folder) and the fallback of the browser
        "Where is my gallery?" -> "Où est ma galerie ?"
        "The personal gallery has one home at a time, and this line always says which one: the folder galerie of the working folder, or the storage of this browser." ->
            "La galerie personnelle n'a qu'un seul emplacement à la fois, et cette ligne dit toujours lequel : le dossier galerie du dossier de travail, ou le stockage de ce navigateur."
        "On the disk: %0 holds the metadata of each entry (id, title, description, name of its picture, date) and every picture is a separate JPEG file of the same folder — never base64 inside the JSON. The file has the format of the export (version 1, key models)." ->
            "Sur le disque : %0 contient les métadonnées de chaque entrée (id, titre, description, nom de son image, date) et chaque image est un fichier JPEG séparé du même dossier — jamais de base64 dans le JSON. Le fichier a le format de l'export (version 1, clé models)."
        "Sharing: copy the folder %0 to another computer (or into the working folder of another browser) and its models and pictures are there; the same folder can also be imported with Import my gallery." ->
            "Partage : copiez le dossier %0 sur un autre poste (ou dans le dossier de travail d'un autre navigateur), ses modèles et ses images y sont ; le même dossier peut aussi être importé avec Importer ma galerie."
        "In the browser: without a working folder, or without its permission, the gallery stays in the IndexedDB of this browser, as before. The first time a folder is available, the gallery of the browser is copied into it once; the browser copy is never emptied." ->
            "Dans le navigateur : sans dossier de travail, ou sans sa permission, la galerie reste dans l'IndexedDB de ce navigateur, comme avant. La première fois qu'un dossier est disponible, la galerie du navigateur y est copiée une fois ; la copie du navigateur n'est jamais vidée."
        "Two tabs: the file is read again before every write and merged entry by entry, so the models added in the other tab are kept; the last write wins for the same model. Deleting an entry deletes only its own picture file, after confirmation." ->
            "Deux onglets : le fichier est relu avant chaque écriture et fusionné entrée par entrée, les modèles ajoutés dans l'autre onglet sont donc conservés ; le dernier écrivain gagne pour un même modèle. Supprimer une entrée ne supprime que son propre fichier image, après confirmation."
        "Gallery stored on the disk: %0 (%1 for the metadata, one %2 file per picture)." ->
            "Galerie enregistrée sur le disque : %0 (%1 pour les métadonnées, un fichier %2 par image)."
        "Gallery stored in this browser; export it to keep it or to transfer it." ->
            "Galerie enregistrée dans ce navigateur ; exportez-la pour la conserver ou la transférer."
        "The working folder %0 is remembered but its permission must be renewed: renew it with the home button to store the gallery on the disk." ->
            "Le dossier de travail %0 est mémorisé mais sa permission doit être renouvelée : renouvelez-la avec le bouton maison pour enregistrer la galerie sur le disque."
        "This browser has no access to a folder: the gallery stays in its storage." ->
            "Ce navigateur n'a pas accès aux dossiers : la galerie reste dans son stockage."
        "The model and its picture (%0) are removed from %1. Only the file of this entry is deleted: the other models, their pictures and the folder are untouched." ->
            "Le modèle et son image (%0) sont retirés de %1. Seul le fichier de cette entrée est supprimé : les autres modèles, leurs images et le dossier ne sont pas touchés."
        "The gallery could not be written in %0: nothing was changed. Check the permission of the folder, or forget it to use the gallery of this browser." ->
            "La galerie n'a pas pu être écrite dans %0 : rien n'a été modifié. Vérifiez la permission du dossier, ou oubliez-le pour utiliser la galerie de ce navigateur."
        "%0 could not be read: it was kept as %1 and a new file was created." ->
            "%0 n'a pas pu être lu : il a été conservé sous %1 et un nouveau fichier a été créé."
        "%0 could not be read: nothing was changed on the disk." ->
            "%0 n'a pas pu être lu : rien n'a été modifié sur le disque."
        "Gallery copied to %0: %1 entries of the browser are now on the disk; the browser copy is kept." ->
            "Galerie copiée dans %0 : les %1 entrées du navigateur sont maintenant sur le disque ; la copie du navigateur est conservée."
        "The gallery of this browser could not be copied to %0: it stays in the browser." ->
            "La galerie de ce navigateur n'a pas pu être copiée dans %0 : elle reste dans le navigateur."
        "Copy" -> "Copier"
        "Cut" -> "Couper"
        "Paste" -> "Coller"
        "Deselect" -> "Désélectionner"
        "Erase" -> "Suppr"
        "Overwrite" -> "Écraser"
        "Keep" -> "Conserver"
        "Rotate 90° (clockwise)" -> "Rotation 90° (horaire)"
        "Flip horizontal" -> "Miroir horizontal"
        "Flip vertical" -> "Miroir vertical"

        // tooltips of the edit bar: the action and its keyboard shortcut
        "Copy (Ctrl+C)" -> "Copier (Ctrl+C)"
        "Cut (Ctrl+X)" -> "Couper (Ctrl+X)"
        "Paste (Ctrl+V)" -> "Coller (Ctrl+V)"
        "Erase selection (Ctrl+Delete)" -> "Supprimer (Ctrl+Suppr)"
        "Deselect (Ctrl+Shift+S)" -> "Désélectionner (Ctrl+Maj+S)"
        "Rotate 90° (Ctrl+R)" -> "Rotation 90° (Ctrl+R)"
        "Flip horizontal (Ctrl+F)" -> "Miroir horizontal (Ctrl+F)"
        "Flip vertical (Ctrl+Shift+F)" -> "Miroir vertical (Ctrl+Maj+F)"

        // edit bar switches and explorer visibility switches
        "Toric ?" -> "Torique ?"
        "Overwrite ?" -> "Écraser ?"
        "Fill ?" -> "Remplir ?"
        "Show ?" -> "Show ?"

        // help of the meta-actions of a simulation modifier
        "The actions are executed once per step, after the behaviours, when the condition is true." ->
            "Les actions sont exécutées une fois par étape, après les comportements, quand la condition est vraie."
        "Only meta-actions are allowed here: they act on the simulation as a whole, not on one cell." ->
            "Seules les méta-actions sont autorisées ici : elles agissent sur la simulation dans son ensemble, pas sur une cellule."
        "State tests" -> "Tests d'état"
        "Grid commands" -> "Commandes de grille"
        "The identifiers are those displayed in the explorer, after the name: name (id). Arguments can be expressions built on the condition functions." ->
            "Les identifiants sont ceux affichés dans l'explorateur, après le nom : nom (id). Les arguments peuvent être des expressions construites sur les fonctions de la condition."
        "AddRect and AddCircle draw their positions among the empty cells of the zone: asking for more agents than there are free cells adds only the free ones." ->
            "AddRect et AddCircle tirent leurs positions parmi les cellules vides de la zone : demander plus d'agents qu'il n'y a de cellules libres n'ajoute que les agents possibles."
        "Adds N agents of type id on the cells of the rectangle (x1, y1)-(x2, y2): fill true (the default) for the whole rectangle, false for its border only, for instance %0." ->
            "Ajoute N agents de type id sur les cellules du rectangle (x1, y1)-(x2, y2) : fill vaut true (par défaut) pour le rectangle entier, false pour son contour seul, par exemple %0."
        "Adds N agents of type id on the cells of the disc centred on (x, y): fill true (the default) for the whole disc, false for its ring only, for instance %0." ->
            "Ajoute N agents de type id sur les cellules du disque centré sur (x, y) : fill vaut true (par défaut) pour le disque entier, false pour son anneau seul, par exemple %0."
        "Erases every agent of the rectangle, or only the agents of type id; fill false erases the border only, for instance %0." ->
            "Efface tous les agents du rectangle, ou seulement ceux de type id ; fill à false n'efface que le contour, par exemple %0."
        "Erases every agent of the disc, or only the agents of type id; fill false erases the ring only, for instance %0." ->
            "Efface tous les agents du disque, ou seulement ceux de type id ; fill à false n'efface que l'anneau, par exemple %0."
        "fill is written after the other arguments, as the literal true or false: AddRect(1, 10, 0, 0, 9, 9, false) draws the border of the rectangle and EraseCircle(50, 50, 10, false) erases the ring of the disc. Omitting it means true, the solid shape, so a formula written before the flag existed keeps its meaning." ->
            "fill s'écrit après les autres arguments, sous la forme littérale true ou false : AddRect(1, 10, 0, 0, 9, 9, false) trace le contour du rectangle et EraseCircle(50, 50, 10, false) efface l'anneau du disque. L'omettre vaut true, la forme pleine : une formule écrite avant l'existence du drapeau garde donc son sens."
        "AddDisc and EraseDisc are the former names of AddCircle and EraseCircle: they are still accepted for the models already recorded, but they are obsolete: write the circle names." ->
            "AddDisc et EraseDisc sont les anciens noms de AddCircle et EraseCircle : ils restent acceptés pour les modèles déjà enregistrés, mais ils sont obsolètes : écrivez les noms du cercle."
        "The rectangle and the circle are the same shapes as in the drawing bar: the Fill ? switch of the bar traces them solid or outlined, with the same functions as the meta-actions." ->
            "Le rectangle et le cercle sont les mêmes formes que dans la barre de dessin : l'interrupteur Remplir ? y trace la forme pleine ou son contour, avec les mêmes fonctions que les méta-actions."

        "Pauses the simulation at the end of the current step, for good: only the reader starts it again, for instance %0." ->
            "Met la simulation en pause à la fin de l'étape courante, définitivement : seul l'utilisateur relance la course, par exemple %0."
        "Pauses the simulation for t seconds of real time (t > 0, for instance 2.5), then resumes it by itself, for instance %0." ->
            "Met la simulation en pause pendant t secondes réelles (t > 0, par exemple 2,5), puis la relance seule, par exemple %0."
        "A timed pause never advances the simulation: no step runs while it lasts, so the sequence of states stays reproducible as soon as the trigger is deterministic (step == 10). Only its duration comes from the clock of the machine: at least t seconds, and longer in a background tab, where the animation frames that notice the end of the pause are rare." ->
            "Une pause minutée n'avance jamais la simulation : aucun pas n'est exécuté pendant sa durée, la trajectoire des états reste donc reproductible dès que le déclencheur est déterministe (step == 10). Seule sa durée dépend de l'horloge de la machine : au moins t secondes, et davantage dans un onglet d'arrière-plan, où les images d'animation qui constatent la fin de la pause sont rares."
        "Meta-functions and meta-tests start with a capital letter (Pause, RunNStep, IsVisible); the functions used in the conditions and in the arguments stay in lower case (abs, min, max, sin, cos, count, field, age, agent)." ->
            "Les méta-fonctions et les méta-tests commencent par une majuscule (Pause, RunNStep, IsVisible) ; les fonctions utilisées dans les conditions et dans les arguments restent en minuscules (abs, min, max, sin, cos, count, field, age, agent)."
        "pause() in lower case is still accepted for the models already recorded, but it is obsolete: write Pause()." ->
            "pause() en minuscules reste accepté pour les modèles déjà enregistrés, mais c'est obsolète : écrivez Pause()."
        "The simulation loop" -> "La boucle de simulation"
        "These meta-actions drive the run loop, not the grid: they stop it, run a given number of steps, or rewind the simulation to its initial state. A request is applied at the end of the step that emits it." ->
            "Ces méta-actions pilotent la boucle de course, pas la grille : elles l'arrêtent, exécutent un nombre de pas donné ou rembobinent la simulation à son état initial. Une demande est appliquée à la fin du pas qui l'émet."
        "Runs exactly one more step, then pauses the run, for instance %0." ->
            "Exécute exactement un pas de plus, puis met la course en pause, par exemple %0."
        "Runs N more steps (N >= 1, rounded), then pauses the run; N <= 0 is ignored, for instance %0." ->
            "Exécute N pas de plus (N >= 1, arrondi), puis met la course en pause ; N <= 0 est ignoré, par exemple %0."
        "Goes back to the initial state and pauses the run, like the rewind button, for instance %0." ->
            "Revient à l'état initial et met la course en pause, comme le bouton de rembobinage, par exemple %0."
        "Goes back to the initial state and starts the run again, for instance %0." ->
            "Revient à l'état initial et relance la course, par exemple %0."
        // warning shown in the simulation view when a modifier pauses the simulation
        "The model paused the simulation (modifier). Resume it with the Run button or step by step." ->
            "Le modèle a mis la simulation en pause (modificateur). Reprenez avec le bouton Run ou avancez pas à pas."
        "The model paused the simulation for %0 s (modifier). The run resumes by itself at the end of the pause." ->
            "Le modèle a mis la simulation en pause pendant %0 s (modificateur). La course reprend seule à la fin de la pause."
        "Adds N agents of type id on empty cells drawn at random in the whole environment, for instance %0." ->
            "Ajoute N agents de type id sur des cellules vides tirées au hasard dans tout l'environnement, par exemple %0."
        "Draws a line of agents of type id from (x1, y1) to (x2, y2), on the empty cells only, for instance %0." ->
            "Trace une ligne d'agents de type id de (x1, y1) à (x2, y2), sur les cellules vides uniquement, par exemple %0."
        "Erases every agent of type id in the whole environment, for instance %0." ->
            "Efface tous les agents de type id dans tout l'environnement, par exemple %0."
        "Activates the behaviour id, for instance %0." ->
            "Active le comportement id, par exemple %0."
        "Inactivates the behaviour id, for instance %0." ->
            "Désactive le comportement id, par exemple %0."
        "Activates the simulation modifier id, for instance %0." ->
            "Active le modificateur de simulation id, par exemple %0."
        "Inactivates the simulation modifier id, for instance %0." ->
            "Désactive le modificateur de simulation id, par exemple %0."
        "1 when the behaviour id is active, 0 when it is inactive: usable in the condition and in the arguments, for instance %0." ->
            "Vaut 1 quand le comportement id est actif, 0 quand il est inactif : utilisable dans la condition et dans les arguments, par exemple %0."
        "1 when the simulation modifier id is active, 0 when it is inactive: usable in the condition and in the arguments, for instance %0." ->
            "Vaut 1 quand le modificateur de simulation id est actif, 0 quand il est inactif : utilisable dans la condition et dans les arguments, par exemple %0."
        "1 when the Toric ? switch of the edit bar is on, 0 when it is off" ->
            "Vaut 1 quand l'interrupteur Torique ? de la barre d'édition est actif, 0 quand il est inactif"
        "1 when the Overwrite ? switch of the edit bar is on, 0 when it is off" ->
            "Vaut 1 quand l'interrupteur Écraser ? de la barre d'édition est actif, 0 quand il est inactif"
        "1 when the agents of type id are visible in the simulation view, 0 when they are hidden" ->
            "Vaut 1 quand les agents de type id sont visibles dans la vue de simulation, 0 quand ils sont masqués"
        "The tests whose name begins with Is are written here, in the condition of the modifier, and nowhere else: an actions formula refuses them, and so does the condition of a behaviour." ->
            "Les tests dont le nom commence par Is s'écrivent ici, dans la condition du modificateur, et nulle part ailleurs : une formule d'actions les refuse, et la condition d'un comportement aussi."
        "The tests whose name begins with Is (IsBehaviourActive, IsModifierActive, IsToricSelectionOn, IsVisible…) test the state of the simulation: they are written in the Condition of the modifier, never here. An actions formula that calls one of them is refused." ->
            "Les tests dont le nom commence par Is (IsBehaviourActive, IsModifierActive, IsToricSelectionOn, IsVisible…) testent l'état de la simulation : ils s'écrivent dans la Condition du modificateur, jamais ici. Une formule d'actions qui en appelle un est refusée."
        "Makes the agents of type id visible again in the simulation view, like the Show ? switch of the explorer, for instance %0." ->
            "Rend à nouveau visibles les agents de type id dans la vue de simulation, comme l'interrupteur Show ? de l'explorateur, par exemple %0."
        "Hides the agents of type id in the simulation view, without touching the simulation, for instance %0." ->
            "Masque les agents de type id dans la vue de simulation, sans toucher à la simulation, par exemple %0."
        "Selects the rectangle between the two corners, in any order, like Shift + drag, for instance %0." ->
            "Sélectionne le rectangle entre les deux coins, dans n'importe quel ordre, comme Maj + glisser, par exemple %0."
        "Copies the visible agents of the selection into the clipboard, for instance %0." ->
            "Copie les agents visibles de la sélection dans le presse-papiers, par exemple %0."
        "Copies the visible agents of the selection into the clipboard, then erases them from the grid, for instance %0." ->
            "Copie les agents visibles de la sélection dans le presse-papiers, puis les efface de la grille, par exemple %0."
        "Pastes the clipboard with (x, y) as its top-left corner; overwrite is true (replace) or false (keep), for instance %0." ->
            "Colle le presse-papiers avec (x, y) comme coin supérieur gauche ; overwrite vaut true (écraser) ou false (conserver), par exemple %0."
        "Empties the memory: the copied or cut selection is forgotten and nothing is left to paste. The grid is not modified, and the selection rectangle is dropped as well, for instance %0." ->
            "Vide la mémoire : la sélection copiée ou coupée est oubliée et il n'y a plus rien à coller. La grille n'est pas modifiée, et le rectangle de sélection est lâché aussi, par exemple %0."
        "Drops the selection rectangle without touching the grid or the memory: it does only that, for instance %0." ->
            "Lâche le rectangle de sélection sans toucher à la grille ni à la mémoire : elle ne fait que cela, par exemple %0."
        "Erases the visible agents of the selection from the grid, then drops the selection rectangle, for instance %0." ->
            "Efface de la grille les agents visibles de la sélection, puis lâche le rectangle de sélection, par exemple %0."
        "Pastes the clipboard with (x, y) as its top-left corner, then empties the memory and drops the selection rectangle, for instance %0." ->
            "Colle le presse-papiers avec (x, y) comme coin supérieur gauche, puis vide la mémoire et lâche le rectangle de sélection, par exemple %0."
        "Turns the clipboard N quarter turns clockwise (negative: counter-clockwise), for instance %0." ->
            "Fait tourner le presse-papiers de N quarts de tour dans le sens horaire (négatif : anti-horaire), par exemple %0."
        "Mirrors the clipboard left/right, for instance %0." ->
            "Miroir gauche/droite du presse-papiers, par exemple %0."
        "Mirrors the clipboard top/bottom, for instance %0." ->
            "Miroir haut/bas du presse-papiers, par exemple %0."
        "Turns the Toric ? switch of the edit bar on (true) or off (false), like the switch itself, for instance %0." ->
            "Active (true) ou désactive (false) l'interrupteur Torique ? de la barre d'édition, comme l'interrupteur lui-même, par exemple %0."
        "Turns the Overwrite ? switch of the edit bar on (true) or off (false), like the switch itself, for instance %0." ->
            "Active (true) ou désactive (false) l'interrupteur Écraser ? de la barre d'édition, comme l'interrupteur lui-même, par exemple %0."
        "The grid commands work on the current selection and on the clipboard of the simulation view, not on a cell. They are applied at the end of the step, in the order of the formula: a selection or a clipboard built by a modifier can then be used by hand. Copy, Cut, Paste, PasteAndFlush, Rotate, FlipX and FlipY are ignored when the selection or the clipboard they need is empty. Four commands end an operation and drop the selection rectangle as well: EraseSelection() destroys the agents of the selected zone, then deselects; FlushSelection() empties the memory, so the copied or cut selection is forgotten, and deselects; PasteAndFlush() pastes, then empties the memory and deselects. Unselect() does only that: it drops the rectangle, without touching the grid or the memory." ->
            "Les commandes de grille agissent sur la sélection courante et sur le presse-papiers de la vue de simulation, pas sur une cellule. Elles sont appliquées à la fin de l'étape, dans l'ordre de la formule : une sélection ou un presse-papiers construit par un modificateur peut ensuite être utilisé à la main. Copy, Cut, Paste, PasteAndFlush, Rotate, FlipX et FlipY sont ignorés quand la sélection ou le presse-papiers dont ils ont besoin est vide. Quatre commandes terminent une opération et lâchent aussi le rectangle de sélection : EraseSelection() détruit les agents de la zone sélectionnée, puis désélectionne ; FlushSelection() vide la mémoire, donc la sélection copiée ou coupée est oubliée, et désélectionne ; PasteAndFlush() colle, puis vide la mémoire et désélectionne. Unselect() ne fait que cela : elle lâche le rectangle, sans toucher à la grille ni à la mémoire."
        "Paste and PasteAndFlush follow the Toric ? switch of the edit bar: on, the cells that overflow the grid are folded on the opposite side (coordinates wrapped modulo the width and the height), so Paste(-10, 10, true) brings back on the right what comes out on the left; off, the cells outside the grid are lost." ->
            "Paste et PasteAndFlush suivent l'interrupteur Torique ? de la barre d'édition : actif, les cellules qui débordent de la grille se poursuivent du côté opposé (coordonnées ramenées modulo la largeur et la hauteur), si bien que Paste(-10, 10, true) fait réapparaître à droite ce qui sort à gauche ; inactif, les cellules hors grille sont perdues."

        // --- captures : méta-action Capture() et dossier de travail
        "Working folder…" -> "Dossier de travail…"
        "Working folder" -> "Dossier de travail"
        "not defined" -> "non défini"
        "defined" -> "défini"
        "permission to renew" -> "permission à renouveler"
        "Working folder: %0" -> "Dossier de travail : %0"
        "Working folder: %0 (%1)" -> "Dossier de travail : %0 (%1)"
        "Working folder — Set" -> "Dossier de travail — Défini"
        "Working folder — Not set" -> "Dossier de travail — Non défini"
        "Working folder — Permission to renew" -> "Dossier de travail — Permission à renouveler"
        "Captures are downloaded: this browser has no access to a folder." ->
            "Les captures sont téléchargées : ce navigateur n'a pas accès aux dossiers."
        "A capture is a JPEG picture of the simulation view, taken at the end of the step that asks for it. It is written in the folder captures of the working folder, then in a sub-folder named after the model, as %0; both folders are created when needed." ->
            "Une capture est une image JPEG de la vue de simulation, prise à la fin du pas qui la demande. Elle est écrite dans le dossier captures du dossier de travail, puis dans un sous-dossier qui porte le nom du modèle, sous la forme %0 ; les deux dossiers sont créés au besoin."
        "This browser has no access to a folder: every capture is downloaded, one file per step. Chrome and Edge offer the working folder on HTTPS or on localhost; Firefox and Safari do not." ->
            "Ce navigateur n'a pas accès aux dossiers : chaque capture est téléchargée, un fichier par pas. Chrome et Edge proposent le dossier de travail en HTTPS ou sur localhost ; Firefox et Safari non."
        "The browser may ask for the permission again after a restart: the button Reconnect gives it back without choosing the folder a second time. As long as it is not granted, the captures are downloaded." ->
            "Le navigateur peut redemander la permission après un redémarrage : le bouton Reconnecter la rend sans choisir le dossier une seconde fois. Tant qu'elle n'est pas accordée, les captures sont téléchargées."
        "Choose a folder…" -> "Choisir un dossier…"
        "Reconnect" -> "Reconnecter"
        "Forget the folder" -> "Oublier le dossier"
        "The folder is now the working folder: the captures are written in %0." ->
            "Le dossier est maintenant le dossier de travail : les captures y sont écrites (%0)."
        "The folder is used until the page is reloaded: this browser could not remember it, so the captures are written in %0 now and downloaded after a restart." ->
            "Le dossier est utilisé jusqu'au rechargement de la page : ce navigateur n'a pas pu le mémoriser, les captures sont donc écrites dans %0 maintenant, et téléchargées après un redémarrage."
        "The folder could not be opened: the captures are downloaded." ->
            "Le dossier n'a pas pu être ouvert : les captures sont téléchargées."
        "The permission is granted again: the captures are written in %0." ->
            "La permission est de nouveau accordée : les captures sont écrites dans %0."
        "The permission was not granted: the captures are downloaded." ->
            "La permission n'a pas été accordée : les captures sont téléchargées."
        "The folder is forgotten: the captures are downloaded." ->
            "Le dossier est oublié : les captures sont téléchargées."
        "Capture saved in %0." -> "Capture enregistrée dans %0."
        "Capture downloaded: %0 (no working folder)." ->
            "Capture téléchargée : %0 (aucun dossier de travail)."
        "Capture downloaded: %0 (this browser has no access to a folder)." ->
            "Capture téléchargée : %0 (ce navigateur n'a pas accès aux dossiers)."
        "Capture downloaded: %0 (the permission of the working folder must be renewed)." ->
            "Capture téléchargée : %0 (la permission du dossier de travail doit être renouvelée)."
        "Capture downloaded: %0 (the working folder could not be written)." ->
            "Capture téléchargée : %0 (le dossier de travail n'a pas pu être écrit)."
        "The simulation view could not be captured." -> "La vue de simulation n'a pas pu être capturée."
        "Takes a JPEG picture of the simulation view at the end of the step (quality 0.92), for instance %0." ->
            "Prend une image JPEG de la vue de simulation à la fin du pas (qualité 0,92), par exemple %0."
        "The picture is named model__step.jpg: the model name is cleaned for the file system (no slash, backslash, colon, star, question mark, quotation mark, angle bracket or vertical bar; no surrounding space; bounded length) and becomes the sub-folder of the captures folder of the working folder; the number is the step of the picture, that is the counter displayed once the step has run, so a Capture() triggered by step == 5 writes model__6.jpg. Without a working folder, or on a browser without the File System Access API, the picture is downloaded." ->
            "L'image s'appelle modele__pas.jpg : le nom du modèle est nettoyé pour le système de fichiers (ni barre oblique, ni barre inverse, ni deux-points, ni étoile, ni point d'interrogation, ni guillemet, ni chevron, ni barre verticale ; pas d'espace en début ou en fin ; longueur bornée) et devient le sous-dossier du dossier captures du dossier de travail ; le nombre est le pas de l'image, c'est-à-dire le compteur affiché une fois le pas exécuté : une Capture() déclenchée par step == 5 écrit donc modele__6.jpg. Sans dossier de travail, ou sur un navigateur sans File System Access, l'image est téléchargée."
        "Working folder and captures" -> "Dossier de travail et captures"
        "The browser remembers only the folder itself, in its own database: no picture is stored in the browser, a capture goes to the folder or to a download, nowhere else." ->
            "Le navigateur ne retient que le dossier lui-même, dans sa propre base : aucune image n'est stockée dans le navigateur, une capture va dans le dossier ou en téléchargement, nulle part ailleurs."
        "Getting started" -> "Premiers pas"
        "Cut, rotate, paste" -> "Couper, tourner, coller"
        "Skip" -> "Passer"
        "Finish" -> "Terminer"
        "One of the first things to do the first time you use NetBioDyn is to set your home. This folder is where your models are saved by default, and it is also where the model gallery and the captures (in the « captures » sub-folder) are saved." ->
            "Une des premières choses à faire la première fois que vous utilisez NetBioDyn est de définir votre home. C'est dans ce dossier que seront enregistrés par défaut vos modèles, et c'est dans ce même dossier que la galerie des modèles et les captures (sous dossier « captures ») seront enregistrées."
        "Welcome" -> "Bienvenue"
        "NetBioDyn simulates a population of agents on a grid: every agent follows the behaviours of its grain, and fields diffuse around them, cell by cell." ->
            "NetBioDyn simule une population d'agents sur une grille : le modélisateur décrit le comportement de chaque agent comme un ensemble de réactions simples, ainsi que celui de champs continus diffusant autour d'eux."
        "This tour shows the screen in eighteen short steps, from the working folder to the drawing tools. It stops there: the agents, the fields, the behaviours and the formulas have their own tutorials." ->
            "Ce tour présente l'écran en dix-huit étapes courtes, du dossier de travail aux outils de dessin. Il s'arrête là : les agents, les champs, les comportements et les formules ont leurs propres tutoriels."
        "Go on with Next, or leave at any time with Skip: the tour then closes and will not open by itself again." ->
            "Avancez avec Suivant, ou quittez à tout moment avec Passer : le tour se ferme alors et ne s'ouvrira plus de lui-même."
        "The working folder" -> "Le dossier de travail"
        "In the top bar you will find the following icons:" -> "Dans la barre du haut, vous trouverez les icones suivants :"
        "New Model, which starts a new empty model" -> "Nouveau Modèle qui permet d'initier un nouveau modèle vide"
        "Import loads a .nbd2 file from the disk" -> "Charger charge un fichier .nbd2 depuis le disque"
        "Save As writes the model and its simulation to a .nbd2 file." -> "Enregistrer sous écrit le modèle et sa simulation dans un fichier .nbd2."
        "Gallery lets you bookmark models and open them directly" -> "Galerie permet de mettre en favoris des modèles et d'y accéder directement"
        "Tutorial shows a series of tutorials, from beginner to expert level" -> "Tutoriel vous présente une série de tutoriels du niveau débutant au niveau expert"
        "Help gives you access to a detailed documentation of NetBioDyn" -> "l'Aide vous donne accès à une documentation détaillée de NetBioDyn"
        "About tells you about the current version of NetBioDyn." -> "À propos vous renseigne sur la version actuelle de NetBioDyn."
        "The left circular arrow button is Undo" -> "Le bouton à flèche tournante de gauche correspond à Annuler"
        "The right circular arrow button is Redo" -> "Le bouton à flèche tournante de droite correspond à Rétablir"
        "Undo" -> "Annuler"
        "Redo" -> "Rétablir"
        "In « Model information » you can enter a name and a description for your model." ->
            "Dans « Informations sur le modèle » vous pouvez entrer un nom et une description à votre modèle."
        "The simulation screen" -> "L'écran de simulation"
        "A square of the grid may hold one agent of the simulation, or none." -> "Un carré de la grille peut contenir ou non un et un seul agent de la simulation."
        "Fields are overlaid on the grid as a coloured veil, the more opaque the more concentrated. The visibility of the agents and of the fields can be controlled both by a Show ? button and, for the fields, by a control of their transparency." -> "Les champs se superposent à la grille comme un voile coloré, d'autant plus opaque qu'ils sont concentrés. La visibilité des agents et champs peut être contrôlée à la fois par un bouton Show ? et pour les champs par un contrôle de leur transparence."
        "The mouse wheel zooms on the grid. The pointer gives information about the values met in the grid." -> "La molette de la souris permet de zoomer sur la grille. Son pointeur donne des informations sur les valeurs rencontrées dans la grille."
        "The run bar" -> "La barre de simulation"
        "Above the grid, the run bar: Rewind, Run, Step, Stop, the speed in steps per second and the step counter." ->
            "Au-dessus de la grille, la barre de simulation : retour au début, lecture, pas à pas, arrêt, la vitesse en pas par seconde et le compteur de pas."
        "The explorer" -> "L'explorateur"
        "The left column lists what the model contains: Agents (the grains), Fields and Behaviours, then Simulation modifiers in expert mode." ->
            "La colonne de gauche liste le contenu du modèle : Agents, Champs et Comportements, auxquels s'ajoutent les Modificateurs de simulation en mode expert."
        "Each row carries an On/Off radio, a name and its identifier, written name (id): that identifier is what the formulas use. The + of a section adds an element and opens its editor, and the search box above filters the list." ->
            "Chaque ligne porte un bouton de type On/Off contrôlant la visibilité (agents et champs) ou l'activation (comportement et modificateurs), un nom et son identifiant, écrit nom (id) : c'est cet identifiant qu'utilisent les formules. Le + d'une section ajoute un élément et ouvre son éditeur. Notez l'existence d'une zone de recherche au-dessus qui permet de filtrer l'explorateur."
        "The tabs" -> "Les onglets"
        "Simulation holds the grid, the run bar and the edition tools. Model opens the editor of the element selected in the explorer, and Settings holds the size of the grid, the seed, the update plan and the curves." ->
            "Dans cette série d'onglets, Simulation affiche la grille, la barre de simulation et les outils d'édition, Modèle ouvre l'éditeur de l'élément sélectionné dans l'explorateur, et Paramètres permet d'ajuster des éléments relatifs à la simulation comme la taille de la grille, la graine aléatoire, le plan de mise à jour et l'affichage des courbes."
        "The curves" -> "Les courbes"
        "The right column draws one curve per agent: how many there are at each step, in the colour of its grain. The Fields chart below does the same for the amount of each field, and appears as soon as the model has one." ->
            "La colonne de droite trace une courbe par agent avec sa couleur. Le graphique Champs en dessous fait de même."
        "Both start again at a rewind. The CSV button exports the history, and Settings can show a sliding window instead of the whole run." ->
            "Il est possible de faire afficher les courbes selon une fenêtre glissante (voir paramètres). Les boutons CSV exportent l'historique des agents ou des champs."
        "Drawing tools and edit bar" -> "Outils de dessin et barre d'édition"
        "The bar under the grid holds the drawing tools — pen, line, square, circle, spray, eraser and move — with their size, the Fill switch of the square and the circle, the random add and the button that clears the grid." ->
            "La barre sous la grille porte les outils de dessin — crayon, ligne, carré, cercle, pulvérisateur, gomme et déplacement — avec leur taille, l'interrupteur Remplir du carré et du cercle, l'ajout aléatoire et le bouton qui vide la grille."
        "The bar under it copies, cuts, pastes, erases, turns and mirrors a selection of cells; a shift + drag selects a rectangle." ->
            "La barre en dessous copie, coupe, colle, supprime, tourne et retourne une sélection de cellules ; un glissé avec Maj sélectionne un rectangle."
        "The tutorial « Cut, rotate, paste » takes those gestures one by one. This tour stops here." ->
            "Le tutoriel « Couper, tourner, coller » reprend ces gestes un par un. Ce tour s'achève ici ; merci de l'avoir suivi."
        "That is the whole screen. The other tutorials go further: « Cut, rotate, paste » for the edition, the beginner ones for the agents and the fields, the expert ones for the behaviours, the actions, the modifiers and the formulas." ->
            "C'est tout l'écran. Les autres tutoriels vont plus loin : « Couper, tourner, coller » pour l'édition, ceux de niveau débutant pour les agents et les champs, ceux de niveau expert pour les comportements, les actions, les modificateurs et les formules."
        "Hold Shift and drag on the grid: a rectangle follows the pointer and stays selected when the button is released, with a dark veil and a frame on its cells. That selection is what Copy, Cut and Erase work on." ->
            "Maintenez Maj et glissez sur la grille : un rectangle suit le pointeur et reste sélectionné au relâchement, avec un voile sombre et un cadre sur ses cellules. C'est sur cette sélection qu'agissent Copier, Couper et Suppr."
        "A plain drag draws with the tool of the bar instead, so Shift is the way to select without drawing." ->
            "Un glissé simple dessine avec l'outil de la barre : Maj est donc le moyen de sélectionner sans dessiner."
        "This grid is empty: draw a few agents with the pen before selecting them, otherwise the copy has nothing to carry." ->
            "Cette grille est vide : dessinez quelques agents au crayon avant de les sélectionner, sinon la copie n'a rien à emporter."
        "Select a rectangle" -> "Sélectionner un rectangle"
        "Copy (Ctrl+C) or cut (Ctrl+X)" -> "Copier (Ctrl+C) ou couper (Ctrl+X)"
        "Copy keeps the cells in place and memorises their agents; Cut memorises them and empties the selected cells. Both read the visible agents only: an agent hidden by a Show(id)/Hide(id) is not carried away." ->
            "Copier laisse les cellules en place et mémorise leurs agents ; Couper les mémorise et vide les cellules sélectionnées. Les deux ne lisent que les agents visibles : un agent masqué par Show(id)/Hide(id) n'est pas emporté."
        "The two buttons of the edit bar do the same as the shortcuts. Nothing is written in the grid by Copy, and a cut can be undone with Undo." ->
            "Les deux boutons de la barre d'édition font la même chose que les raccourcis. Copier n'écrit rien dans la grille, et une coupe peut être annulée avec Annuler."
        "Anchor the floating selection" -> "Ancrer la sélection flottante"
        "Once copied or cut, the extraction floats under the pointer: a thin cool frame with the ghost of its agents, which shows exactly what a paste would write on the cell below." ->
            "Une fois copiée ou coupée, l'extraction flotte sous le pointeur : un cadre fin et froid avec le fantôme de ses agents, qui montre exactement ce qu'un collage écrirait sur la cellule en dessous."
        "A left click anchors it on that cell: the frame becomes thick and yellow, it no longer follows the pointer, and the cell under it is the origin of the next paste." ->
            "Un clic gauche l'ancre sur cette cellule : le cadre devient épais et jaune, il ne suit plus le pointeur, et la cellule sous lui est l'origine du prochain collage."
        "Release it (right click)" -> "La libérer (clic droit)"
        "A right click detaches the anchor: the extraction floats again under the pointer, ready to be anchored somewhere else. Escape drops the preview without emptying the clipboard." ->
            "Un clic droit détache l'ancre : l'extraction flotte de nouveau sous le pointeur, prête à être ancrée ailleurs. Échap abandonne l'aperçu sans vider le presse-papiers."
        "A right click inside the selection, when nothing is anchored, drops the selection itself; Ctrl+Shift+S and the Deselect button do the same." ->
            "Un clic droit dans la sélection, quand rien n'est ancré, abandonne la sélection elle-même ; Ctrl+Maj+S et le bouton Désélectionner font de même."
        "Turn it (Ctrl+R)" -> "La tourner (Ctrl+R)"
        "Ctrl+R, or the circular arrow of the edit bar, gives the extraction a quarter turn clockwise: the frame and its ghost agents turn at once, and a rectangle that is not square swaps its width and its height." ->
            "Ctrl+R, ou la flèche circulaire de la barre d'édition, fait faire un quart de tour dans le sens horaire à l'extraction : le cadre et ses agents fantômes tournent ensemble, et un rectangle non carré échange sa largeur et sa hauteur."
        "Only the clipboard turns, never the grid: four turns bring the extraction back to its first position, and nothing has been written yet." ->
            "Seul le presse-papiers tourne, jamais la grille : quatre tours ramènent l'extraction à sa position d'origine, et rien n'a encore été écrit."
        "Mirror it (Ctrl+F, Ctrl+Shift+F)" -> "La retourner (Ctrl+F, Ctrl+Maj+F)"
        "Ctrl+F mirrors the extraction left to right, Ctrl+Shift+F top to bottom; the two arrow buttons of the edit bar do the same. The two mirrors can be combined with a turn." ->
            "Ctrl+F retourne l'extraction de gauche à droite, Ctrl+Maj+F de haut en bas ; les deux boutons fléchés de la barre d'édition font de même. Les deux miroirs peuvent se combiner avec une rotation."
        "Paste (Ctrl+V or the button)" -> "Coller (Ctrl+V ou le bouton)"
        "Ctrl+V or the Paste button writes the extraction where the anchor is; without an anchor, it lands under the pointer, or on the corner of the selection when the pointer is outside the grid." ->
            "Ctrl+V, ou le bouton Coller, écrit l'extraction là où est l'ancre ; sans ancre, elle atterrit sous le pointeur, ou sur le coin de la sélection quand le pointeur est hors de la grille."
        "Only the agents of the extraction are written: a cell of the copy that was empty leaves the target cell as it is, unless Overwrite is on — see the switch below. A second Ctrl+V pastes the same extraction again, without the floating preview." ->
            "Seuls les agents de l'extraction sont écrits : une cellule vide de la copie laisse la cellule cible telle quelle, sauf si Écraser est actif — voir l'interrupteur plus bas. Un second Ctrl+V colle de nouveau la même extraction, sans l'aperçu flottant."
        "Toric ? — the borders of the grid" -> "Torique ? — les bords de la grille"
        "Off, a paste that overflows the grid loses the cells that fall outside: they are simply not written." ->
            "Inactif, un collage qui déborde de la grille perd les cellules qui tombent dehors : elles ne sont simplement pas écrites."
        "On, those cells fold on the opposite side of the grid, as if the edges were glued: a paste astride the right edge continues on the left. The preview already shows the folded cells." ->
            "Actif, ces cellules se replient de l'autre côté de la grille, comme si les bords étaient collés : un collage à cheval sur le bord droit continue à gauche. L'aperçu montre déjà les cellules repliées."
        "The switch also drives the Paste(x, y, overwrite) and PasteAndFlush(x, y, overwrite) meta-actions, and the drawing tools that overflow the grid." ->
            "L'interrupteur pilote aussi les méta-actions Paste(x, y, overwrite) et PasteAndFlush(x, y, overwrite), ainsi que les outils de dessin qui débordent de la grille."
        "Overwrite ? — the agents already there" -> "Écraser ? — les agents déjà présents"
        "Off, a paste only writes the empty cells of its target: the agents already present stay where they are, and the paste looks partial over a populated zone." ->
            "Inactif, un collage n'écrit que les cellules vides de sa cible : les agents déjà présents restent en place, et le collage paraît partiel sur une zone peuplée."
        "On, the agents of the extraction replace the ones in place, cell by cell, visible agents only. The frame of the preview turns warm when the switch is on." ->
            "Actif, les agents de l'extraction remplacent ceux en place, cellule par cellule, agents visibles seulement. Le cadre de l'aperçu devient chaud quand l'interrupteur est actif."
        "Erase the selection (Ctrl+Delete)" -> "Supprimer la sélection (Ctrl+Suppr)"
        "Ctrl+Delete, or the red Erase button, empties the visible agents of the selection and drops the selection. The clipboard is not touched: what was copied or cut can still be pasted." ->
            "Ctrl+Suppr, ou le bouton rouge Suppr, vide les agents visibles de la sélection et abandonne la sélection. Le presse-papiers n'est pas touché : ce qui a été copié ou coupé peut encore être collé."
        "The whole edition rests on one rectangle: Shift + drag to select it, Copy or Cut to fill the clipboard, a left click to anchor the paste, a right click to release it, Ctrl+R and Ctrl+F to turn it, Ctrl+V to write it, Ctrl+Delete to empty it. Before a paste, check the Toric and Overwrite switches: they decide what happens at the borders and over the agents already there." ->
            "Toute l'édition repose sur un rectangle : Maj + glissé pour le sélectionner, Copier ou Couper pour remplir le presse-papiers, un clic gauche pour ancrer le collage, un clic droit pour le libérer, Ctrl+R pour la tourner et Ctrl+F pour la retourner, Ctrl+V pour l'écrire, Ctrl+Suppr pour le vider. Avant de coller, vérifiez les interrupteurs Torique et Écraser : ils décident de ce qui se passe aux bords et sur les agents déjà présents."
        "%0 executed" -> "%0 exécutée"
        "Journal" -> "Journal"
        "Journal (%0)" -> "Journal (%0)"
        "%0 warning(s)" -> "%0 avertissement(s)"
        "The journal is empty." -> "Le journal est vide."
        "Journal of the simulation" -> "Journal de la simulation"
        "Export the journal" -> "Exporter le journal"
        "Journal help" -> "Aide du journal"
        "Meta" -> "Méta"
        "Info" -> "Info"
        "Warn." -> "Avert."
        "Error" -> "Erreur"
        "Journal exported to %0." -> "Journal exporté dans %0."
        "Model imported: %0." -> "Modèle importé : %0."
        "Can't load model at %0" -> "Impossible de charger le modèle à l'adresse %0"
        "Journal downloaded: %0 (this browser has no access to a folder)." ->
            "Journal téléchargé : %0 (ce navigateur n'a pas accès à un dossier)."
        "Journal downloaded: %0 (the permission of the working folder must be renewed)." ->
            "Journal téléchargé : %0 (l'autorisation du dossier de travail doit être renouvelée)."
        "Journal downloaded: %0 (the working folder could not be written)." ->
            "Journal téléchargé : %0 (le dossier de travail n'a pas pu être écrit)."
        "Journal downloaded: %0 (no working folder)." ->
            "Journal téléchargé : %0 (aucun dossier de travail)."
        "The journal lists what the simulation does by itself: the meta-actions it executes, the captures it takes, the pauses it asks for, and what happens to the files it writes. It replaces the notices that used to cover the top of the grid." ->
            "Le journal liste ce que la simulation fait d'elle-même : les méta-actions qu'elle exécute, les captures qu'elle prend, les pauses qu'elle demande et ce qui arrive aux fichiers qu'elle écrit. Il remplace les messages qui recouvraient le haut de la grille."
        "Four levels: META (blue) for an executed meta-action, INFO (green) for a normal event, WARNING (orange) for something to check, ERROR (red) for a failure. The level is written at the beginning of every line, so the colour is never the only signal." ->
            "Quatre niveaux : META (bleu) pour une méta-action exécutée, INFO (vert) pour un événement normal, WARNING (orange) pour un point à vérifier, ERROR (rouge) pour un échec. Le niveau est écrit au début de chaque ligne : la couleur n'est jamais le seul signal."
        "The header folds the panel. Folded, it keeps the number of warnings in view; the state is remembered in this browser. The export button writes journal/MODEL__journal.txt in the working folder, or downloads the same text: one line per entry, level, step and message separated by tabulations. The export never empties the journal." ->
            "L'en-tête replie le panneau. Replié, il garde le nombre d'avertissements visible ; l'état est mémorisé dans ce navigateur. Le bouton d'export écrit journal/MODELE__journal.txt dans le dossier de travail, ou télécharge le même texte : une ligne par entrée, niveau, pas et message séparés par des tabulations. L'export ne vide jamais le journal."
        "Stencil" -> "Stencil"
        "Stencil (load a pattern)" -> "Stencil (charger un motif)"
        "Stencil help (formats and paste)" -> "Aide du stencil (formats et collage)"
        "Load a stencil" -> "Charger un stencil"
        "Pattern" -> "Motif"
        "Matrix" -> "Matrice"
        "Validate" -> "Valider"
        "Choose a file" -> "Choisir un fichier"
        "Paste an RLE pattern, a .cells pattern, a character matrix or a .nbd2 project." ->
            "Collez un motif RLE, un motif .cells, une matrice de caractères ou un projet .nbd2."
        "File loaded: %0. Press Validate to read the pattern." ->
            "Fichier chargé : %0. Appuyez sur Valider pour lire le motif."
        "The pattern fills the clipboard and floats under the mouse: click the grid to anchor it, then paste. The Overwrite ? and Toric ? switches of the edit bar keep their usual meaning." ->
            "Le motif remplit le presse-papiers et flotte sous la souris : cliquez sur la grille pour l'ancrer, puis collez. Les interrupteurs Écraser ? et Torique ? de la barre d'édition gardent leur sens habituel."

        "The content is empty: paste a pattern or choose a file." ->
            "Le contenu est vide : collez un motif ou choisissez un fichier."
        "Unrecognized format. Accepted: RLE (header x = 3, y = 3), plain text .cells (lines of . and O), a character matrix (. for an empty cell, 0-9 then a-f for the grains) and a .nbd2 project." ->
            "Format non reconnu. Formats acceptés : RLE (en-tête x = 3, y = 3), texte .cells (lignes de . et O), matrice de caractères (. pour une cellule vide, 0-9 puis a-f pour les grains) et projet .nbd2."
        "The pattern is empty: there is nothing to paste." ->
            "Le motif est vide : il n'y a rien à coller."
        "This project has no populated environment: there is nothing to copy." ->
            "Ce projet n'a pas d'environnement peuplé : il n'y a rien à copier."
        "The RLE pattern cannot be read." -> "Le motif RLE est illisible."
        "The RLE pattern cannot be read at character %0." -> "Le motif RLE est illisible au caractère %0."
        "This file is not a readable NetBioDyn project (.nbd2)." ->
            "Ce fichier n'est pas un projet NetBioDyn (.nbd2) lisible."
        "Stencil: %0 cell(s) ignored, unknown grain id(s) %1." ->
            "Stencil : %0 cellule(s) ignorée(s), identifiant(s) de grain inconnu(s) %1."
        "Stencil loaded: %0 x %1 cells, in the clipboard. Click the grid to anchor it, then paste." ->
            "Stencil chargé : %0 x %1 cellules dans le presse-papiers. Cliquez sur la grille pour l'ancrer, puis collez."
        "Stencil: every cell designates an unknown grain, nothing was loaded." ->
            "Stencil : toutes les cellules désignent un grain inconnu, rien n'a été chargé."

        "Stencil — accepted formats" -> "Stencil — formats acceptés"
        "The format is detected on its own, without a selector. Four formats are accepted; anything else is refused and nothing is loaded." ->
            "Le format est détecté tout seul, sans sélecteur. Quatre formats sont acceptés ; tout le reste est refusé et rien n'est chargé."
        "RLE (Golly): a line x = width, y = height, optionally followed by rule = B3/S23, then the cells on the following lines. Lines starting with # are comments, #N naming the pattern. b (or .) is an empty cell and o a live one; a number repeats the symbol that follows, $ ends a row and ! ends the pattern. A body made of b and o only is two-state: o designates the first grain of the model, id 0, exactly like O in a .cells file, so a Game of Life pattern loads in a one-grain model. An uppercase letter introduces a state of its own (A = 10, B = 11 ... Z = 35): the body is then multi-state, 2A is two cells of grain 10, and o is grain 1 again." ->
            "RLE (Golly) : une ligne x = largeur, y = hauteur, suivie éventuellement de rule = B3/S23, puis les cellules sur les lignes suivantes. Les lignes commençant par # sont des commentaires, #N donnant le nom du motif. b (ou .) est une cellule vide et o une cellule vivante ; un nombre répète le symbole qui suit, $ termine une ligne et ! termine le motif. Un corps composé uniquement de b et de o est à deux états : o désigne le premier grain du modèle, l'identifiant 0, exactement comme O dans un fichier .cells — un motif du jeu de la vie se charge donc dans un modèle à un seul grain. Une lettre majuscule introduit un état à part entière (A = 10, B = 11 ... Z = 35) : le corps est alors multi-états, 2A est deux cellules de grain 10, et o redevient le grain 1."
        "Plain text .cells: one line per row, . for an empty cell and O (or o) for a live one; ! starts a comment and !Name: gives the name of the pattern. The format has two states only, so a live cell designates the first grain of the model, id 0." ->
            "Texte .cells : une ligne par rangée, . pour une cellule vide et O (ou o) pour une cellule vivante ; ! commence un commentaire et !Name: donne le nom du motif. Le format n'a que deux états : une cellule vivante désigne le premier grain du modèle, l'identifiant 0."
        "Character matrix: one character per cell, . for an empty cell and 0-9 then a-f (or A-F) for the grains of id 0 to 15; commas, spaces and tabs are ignored, ! and # lines are comments." ->
            "Matrice de caractères : un caractère par cellule, . pour une cellule vide et 0-9 puis a-f (ou A-F) pour les grains d'identifiant 0 à 15 ; les virgules, espaces et tabulations sont ignorés, les lignes ! et # sont des commentaires."
        ".nbd2 project: the populated environment stored in the file is extracted, with the grain of every cell, and cropped on the bounding box of the agents; the empty border of the grid is dropped." ->
            "Projet .nbd2 : l'environnement peuplé enregistré dans le fichier est extrait, avec le grain de chaque cellule, et recadré sur la boîte englobante des agents ; la bordure vide de la grille est abandonnée."
        "Grains the model does not have" -> "Grains absents du modèle"
        "A cell designating a grain the model does not have becomes empty, since there is nothing to paste there. The journal reports the number of cells ignored and the ids in cause, once per load; a pattern that loses every cell is not loaded at all." ->
            "Une cellule qui désigne un grain absent du modèle devient vide, faute de grain à y coller. Le journal signale le nombre de cellules ignorées et les identifiants en cause, une seule fois par chargement ; un motif qui perd toutes ses cellules n'est pas chargé du tout."
        "Paste and the edit bar" -> "Collage et barre d'édition"
        "The stencil arrives in the clipboard like a copy: it floats under the mouse, is anchored with a left click, turned with Ctrl+R and mirrored with Ctrl+F or Ctrl+Shift+F. The Overwrite ? switch decides whether the paste replaces the agents already there or only fills the empty cells of the pattern; Toric ? decides whether a pattern that overflows the grid wraps on the opposite side. These are the rules of the manual paste, unchanged." ->
            "Le stencil arrive dans le presse-papiers comme une copie : il flotte sous la souris, s'ancre d'un clic gauche, se tourne avec Ctrl+R et se retourne avec Ctrl+F ou Ctrl+Maj+F. L'interrupteur Écraser ? décide si le collage remplace les agents déjà présents ou ne remplit que les cellules vides du motif ; Torique ? décide si un motif qui déborde de la grille se replie du côté opposé. Ce sont les règles du collage manuel, inchangées."
        // tutoriel « Charger un stencil » : une phrase par étape
        "Open the Stencil window" -> "Ouvrir la fenêtre Stencil"
        "The Stencil button, with the stamp icon, sits in the edit bar just after Paste. It opens a window that reads a pattern from a text or from a file and drops it in the clipboard as a floating selection: nothing is written on the grid yet." ->
            "Le bouton Stencil, à l'icône de tampon, se trouve dans la barre d'édition juste après Coller. Il ouvre une fenêtre qui lit un motif dans un texte ou dans un fichier et le dépose dans le presse-papiers comme une sélection flottante : rien n'est encore écrit sur la grille."
        "This tutorial created a one-grain model for itself: a single grain « Cell » of id 0, which already carries the rules of the Game of Life — Birth at exactly 3 neighbours, Death under 2 or over 3. That is the model the two binary formats speak of: a .cells file, and an RLE made of b and o only, designate the first grain." ->
            "Ce tutoriel s'est créé un modèle à un seul grain : un grain « Cell » d'identifiant 0, qui porte déjà les règles du jeu de la vie — naissance à exactement 3 voisins, mort en dessous de 2 ou au-dessus de 3. C'est le modèle dont parlent les deux formats binaires : un fichier .cells, et un RLE composé uniquement de b et de o, désignent le premier grain."
        "Paste the pattern in the zone" -> "Coller le motif dans la zone"
        "Select the pattern below, copy it (Ctrl+C), click the text zone of the window and paste it there (Ctrl+V)." ->
            "Sélectionnez le motif ci-dessous, copiez-le (Ctrl+C), cliquez dans la zone de saisie de la fenêtre et collez-le (Ctrl+V)."
        "The lines are long and the message is narrow: the box scrolls on its own. The very same pattern is written in full in the help, §7.5 « Load a stencil », and it is the catereron of the Conway Life wiki, 50P92.1." ->
            "Les lignes sont longues et le message étroit : l'encadré défile tout seul. Ce même motif est écrit en entier dans l'aide, §7.5 « Charger un stencil » ; c'est le catereron du wiki Conway Life, 50P92.1."
        "Validate reads the zone and closes the window. A text that no format recognises leaves the window open, with a red message saying what is accepted, and loads nothing." ->
            "Valider lit la zone et ferme la fenêtre. Un texte qu'aucun format ne reconnaît laisse la fenêtre ouverte, avec un message rouge qui dit ce qui est accepté, et ne charge rien."
        "Here the header announces x = 32, y = 45 and the body uses nothing but b and o: this is a two-state RLE, so each o becomes grain 0, the only grain of the model. The journal then writes « Stencil loaded: 32 x 45 cells » — a size or a grain id that does not fit would be reported there as ignored cells." ->
            "Ici l'en-tête annonce x = 32, y = 45 et le corps n'utilise que b et o : c'est un RLE à deux états, donc chaque o devient le grain 0, seul grain du modèle. Le journal écrit alors « Stencil chargé : 32 x 45 cellules » — une taille ou un identifiant de grain qui ne conviendrait pas y serait signalé comme cellules ignorées."
        "Anchor the floating selection" -> "Ancrer la sélection flottante"
        "The pattern now floats under the pointer: a thin cool frame with the ghost of its 66 agents, showing exactly what a paste would write on the cell below." ->
            "Le motif flotte maintenant sous le pointeur : un cadre fin et froid avec le fantôme de ses 66 agents, qui montre exactement ce qu'un collage écrirait sur la cellule en dessous."
        "A left click anchors it on that cell: the frame becomes thick and yellow and stops following the pointer. A right click releases it, Ctrl+R turns it, Ctrl+F and Ctrl+Shift+F mirror it." ->
            "Un clic gauche l'ancre sur cette cellule : le cadre devient épais et jaune et ne suit plus le pointeur. Un clic droit le libère, Ctrl+R le tourne, Ctrl+F et Ctrl+Maj+F le retournent."
        "Paste (Ctrl+V)" -> "Coller (Ctrl+V)"
        "Ctrl+V, or the Paste button of the edit bar, writes the 66 agents of the pattern where the anchor is. Without an anchor, the paste lands under the pointer or on the corner of the selection." ->
            "Ctrl+V, ou le bouton Coller de la barre d'édition, écrit les 66 agents du motif là où se trouve l'ancre. Sans ancre, le collage atterrit sous le pointeur ou sur le coin de la sélection."
        "Only the agents of the pattern are written: a cell the pattern leaves empty leaves its target as it is, unless Écraser ? is on — see the next step." ->
            "Seuls les agents du motif sont écrits : une cellule que le motif laisse vide laisse sa cible telle quelle, sauf si Écraser ? est actif — voir l'étape suivante."
        "Accepted formats, Écraser ? and Torique ?" -> "Formats acceptés, Écraser ? et Torique ?"
        "The format is detected on its own, there is no selector. Four are accepted: RLE of Golly (a header x = …, y = … then the cells), plain text .cells (lines of . and O), a character matrix (one character per cell, 0-9 then a-f for the ids) and a .nbd2 project, cropped on the box of its agents." ->
            "Le format est détecté tout seul, il n'y a pas de sélecteur. Quatre sont acceptés : le RLE de Golly (un en-tête x = …, y = … puis les cellules), le texte .cells (des lignes de . et de O), une matrice de caractères (un caractère par cellule, 0-9 puis a-f pour les identifiants) et un projet .nbd2, recadré sur la boîte de ses agents."
        "How a cell is numbered: a two-state pattern — RLE with b and o only, or .cells — designates grain 0. As soon as an RLE body carries an uppercase state (A = 10, B = 11 … Z = 35), the state number is the grain id, so o is grain 1 again and 2A is two cells of grain 10. A cell whose grain the model does not have becomes empty, and the journal counts it once per load." ->
            "Comment une cellule est numérotée : un motif à deux états — RLE composé de b et de o seulement, ou .cells — désigne le grain 0. Dès qu'un corps RLE porte un état en majuscule (A = 10, B = 11 … Z = 35), le numéro d'état est l'identifiant du grain : o redevient le grain 1 et 2A est deux cellules de grain 10. Une cellule dont le grain manque au modèle devient vide, et le journal la compte une fois par chargement."
        "Écraser ? decides what happens over the agents already there: off, the paste only fills the empty cells of its target; on, the agents of the pattern replace the ones in place, cell by cell. Torique ? decides at the borders: off, the cells that overflow the grid are lost; on, they fold on the opposite side. A stencil follows both, exactly like a manual paste." ->
            "Écraser ? décide de ce qui arrive aux agents déjà présents : éteint, le collage ne remplit que les cellules vides de sa cible ; allumé, les agents du motif remplacent ceux en place, cellule par cellule. Torique ? décide aux bords : éteint, les cellules qui débordent de la grille sont perdues ; allumé, elles se replient du côté opposé. Un stencil suit les deux, exactement comme un collage manuel."
        "The ? button beside Stencil opens the same explanations, and the help (§7.5) holds the table of formats and the complete text of this pattern." ->
            "Le bouton ? à côté de Stencil ouvre les mêmes explications, et l'aide (§7.5) contient le tableau des formats et le texte complet de ce motif."
        "Run it — the creature lives" -> "Lancer — la créature vit"
        "The 66 cells pasted on the grid are agents of the model, and the two behaviours it already had apply to them: Birth where exactly three neighbours surround an empty cell, Death where there are less than two or more than three. Press the play button of the run bar, or the step-forward arrow to go one step at a time." ->
            "Les 66 cellules collées sur la grille sont des agents du modèle, et les deux comportements qu'il portait déjà s'y appliquent : naissance là où exactement trois voisins entourent une cellule vide, mort en dessous de deux ou au-dessus de trois voisins. Appuyez sur le bouton play de la barre de simulation, ou sur la flèche pas-à-pas pour avancer d'un pas à la fois."
        "This pattern is 50P92.1, an oscillator of the Game of Life: it does not stay still, it comes back to its shape every 92 steps. Watch the counter and the curve of the grain: the number of living cells changes from one step to the next." ->
            "Ce motif est 50P92.1, un oscillateur du jeu de la vie : il ne reste pas immobile, il retrouve sa forme tous les 92 pas. Regardez le compteur et la courbe du grain : le nombre de cellules vivantes change d'un pas à l'autre."
        "A grid that would not move is the sign of a pattern loaded with a grain the model does not have: nothing was pasted. Rewind brings the model and the grid back to their initial state, and the clipboard is left alone, so the pattern can be pasted again." ->
            "Une grille qui ne bougerait pas serait le signe d'un motif chargé avec un grain absent du modèle : rien n'a été collé. Rembobiner ramène le modèle et la grille à leur état initial, et le presse-papiers n'est pas touché : le motif peut être collé de nouveau."
        "A stencil is a copy that comes from a text: Stencil, paste the pattern, Validate, click the grid to anchor it, Ctrl+V, then press play. Before pasting, check Écraser ? and Torique ? — the stencil obeys them like any other paste, and with Écraser ? on its empty cells erase the ones they cover." ->
            "Un stencil est une copie qui vient d'un texte : Stencil, coller le motif, Valider, cliquer la grille pour l'ancrer, Ctrl+V, puis play. Avant de coller, vérifiez Écraser ? et Torique ? — le stencil y obéit comme tout autre collage, et avec Écraser ? actif ses cellules vides effacent celles qu'elles recouvrent."

        else -> null
    }
}