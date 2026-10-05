# Mémo technique : lecture, calculs et schémas

Cette mise à jour remplace les cinq fiches `resistors`, `units`, `prefixes`, `formulas` et `decibels`. Le fragment de travail est `data/reference-technical.json` ; la bibliothèque publiée les charge depuis le fichier `reference.json` après intégration. La mise à jour conserve les 30 formules qui existaient, leur ajoute une explication de leur utilité, les unités, les conditions d’application et un schéma natif. Les exemples et les astuces sont identifiés dans les données, séparément des faits.

## Sources et choix éditoriaux

Source principale : [cours F6KGL/F5KFF, novembre 2025](http://f6kgl.free.fr/COURS.html), archive locale `data/sources/f6kgl/COURS.html` et extraction `course-text.txt`. Attribution et licence du cours : CC BY-NC-SA 4.0. Les explications et dessins ont été reformulés pour une lecture mobile ; ils ne sont pas des copies des illustrations du cours.

| Fiche | Chapitres du cours utilisés | Présentation |
| --- | --- | --- |
| Grandeurs et unités | Technique §0.2, §1.1–1.3, §2.1–2.3, §12.4 | Grandeur, définition, symbole, usage et groupes cohérents |
| Préfixes et conversions SI | Technique §0.2 | Sous-multiples, multiples, conversions, attention à la casse |
| Résistances : couleurs et lecture | Technique §1.5a–c | Chiffres/multiplicateurs, cinq anneaux, exemples, tolérance à part, mnémonique, séries normalisées et puissance admissible |
| Formules et schémas | Technique §1.2–1.3, §1.7–1.8, §2.1–2.5, §3.1, §4.1–4.4, §9–10, §12 ; Réglementation §R-5.1d | Chaque relation illustrée, avec rôle, unités et conditions |
| Décibels : gains, pertes et niveaux | Technique §4.1, §4.4, §11.6 ; Réglementation §R-1.3 et §R-5.1 | Définitions/équations, table simplifiée, références absolues, exemples et astuces |

### Résistances à cinq anneaux et examen

Le §1.5b présente bien cinq anneaux : trois chiffres significatifs, multiplicateur, puis tolérance. Le §1.5a dit que le code des couleurs de **tolérance** n’a pas à être connu pour l’examen. Il ne dit pas que la lecture des cinq anneaux est globalement exclue. La fiche reprend cette nuance au lieu d’affirmer une exclusion générale sans fondement. Exemples ajoutés : brun/rouge/orange/brun/brun = 1 230 Ω ±1 % ; jaune/violet/noir/brun/brun = 4 700 Ω ±1 %.

La table F6KGL laisse certaines tolérances peu courantes vides. La valeur du gris ±0,05 % est conservée et confirmée dans la [table du fabricant Vishay](https://www.vishay.com/docs/49411/resistor_color_code_calculator.pdf). Or et argent ne codent jamais des chiffres significatifs : ils restent des multiplicateurs ou des tolérances selon leur position.

Le guide dessine les légendes **sous la bande correspondante**, à la même position horizontale que le dessin. La tolérance et ses bornes minimum/maximum ont un encadré distinct. Un sélecteur permet de lire quatre ou cinq anneaux.

Le mnémonique « Ne Mangez Rien Ou Je Vous Battrai Violemment, Grand BOA » est celui du cours. Il se décode avec les chiffres 0 à 9 ; les trois lettres finales BOA signifient Blanc, Or, Argent. Brun/marron est explicité, ainsi que les deux B et les deux V.

### Décibels et précision

La table du cours arrondit notamment +3 dB à ×2, +6 dB à ×4, +7 dB à ×5, +9 dB à ×8. L’outil calcule la relation logarithmique exacte ; les exemples indiquent la différence. Les tensions utilisent 20 log et supposent des résistances égales pour être équivalentes au gain de puissance. La distinction dB/dBm/dBW/dBµV/dBc/dBi/dBd est conservée.

Le premier outil dispose d’un bouton compact +/− à gauche du champ ; le signe n’est plus dupliqué dans le champ. Le nouveau schéma utilise de véritables puissances d’entrée/sortie, un gain de bloc et une perte de câble. Les unités W/mW/µW se convertissent à chaque changement. La cascade est calculée sans arrondir les étapes.

`ResourceMath.powerGain` prend une différence de logarithmes au lieu de diviser d’abord les puissances. Cela évite de perdre un rapport valide lorsque le quotient déborde numériquement. `powerAfterGain` procède aussi dans le domaine logarithmique. Les zéros, valeurs négatives, NaN, infinis et débordements/sous-flux de sortie sont rejetés explicitement.

## Vérification

Deux tests JVM complémentaires vérifient une mesure entrée/sortie, une cascade gain/perte, une atténuation, des gains nuls, des rapports extrêmes et les entrées invalides. Les dessins distinguent série/parallèle, tension/courant, temps/distance, spires et puissance. Le graphe de charge RC marque 63 % à τ ; celui de sélectivité marque la largeur aux points de demi-puissance (−3 dB).

Les schémas AM/FM sont des repères de largeur, pas des spectres exacts de chaque modulation. La formule de Carson est présentée comme une approximation. Les conditions (sinusoïde, circuit idéal, absence de charge/couplage, unités de base) font partie de la fiche et des flashcards.

Ce document décrit le sous-ensemble technique demandé. L’audit global de couverture du cours et les nouvelles fiches des composants, montages, propagation et modulations sont suivis séparément dans l’intégration principale.
