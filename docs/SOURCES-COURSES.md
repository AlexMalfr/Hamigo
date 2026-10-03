# Sources pédagogiques et vérifications

Contenu préparé le 3 octobre 2026 pour Hamigo. Le parcours est fixe : **14 chapitres, 56 leçons, 224 exercices**. Les fiches de référence contiennent **17 catégories et 364 entrées**. Les paragraphes, exemples chiffrés et exercices du parcours sont rédigés pour l’application; ils ne recopient pas les paragraphes du cours.

## Source principale

[Préparation au certificat d’opérateur — F6KGL/F5KFF, édition de novembre 2025](http://f6kgl.free.fr/COURS.html) fournit le cadre pédagogique : réglementation, bases mathématiques, électricité, composants, circuits, radioélectricité, émission, réception et modulation.

L’archive locale conserve la page originale, une extraction de texte en UTF-8 et les **187 illustrations** référencées :

- `data/sources/f6kgl/COURS.html`
- `data/sources/f6kgl/course-text.txt`
- `data/sources/f6kgl/COURS_fichiers/`
- `data/sources/f6kgl/manifest.json` : URL, taille et SHA-256 de chaque fichier.
- `data/sources/f6kgl/archive_assets.py` : outil de téléchargement reproductible.

L’encodage original est Windows-1252. L’extraction est un outil d’analyse, pas un remplacement des schémas. L’archive conserve la licence déclarée par l’auteur : **[Creative Commons Attribution — Pas d’utilisation commerciale — Partage dans les mêmes conditions 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/)**. Les droits de cette source sont distincts du code de l’application.

## Sources officielles

- [ANFR : les certificats](https://www.anfr.fr/gerer/radioamateurs/les-certificats) : organisation, réussite et démarches.
- [Arrêté du 21 septembre 2000 consolidé au 3 octobre 2026](https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000401783/2026-10-03), article 2 : durée, barème, seuils et conservation d’épreuve; article 7 et annexe : indicatifs.
- [ANFR : cadre juridique](https://www.anfr.fr/gerer/radioamateurs/cadre-juridique) : entrée vers les textes applicables.
- [ANFR : questions et réponses](https://www.anfr.fr/gerer/radioamateurs/questions-/-reponses) : exposition, installation et déplacement.
- [ARCEP : décision 2012-1241, articles 1 à 6](https://www.legifrance.gouv.fr/jorf/id/JORFSCTA000027144465) : objet des communications, identification, stations automatiques et journal.
- [ARCEP : décision 2013-1515](https://www.arcep.fr/uploads/tx_gsavis/13-1515.pdf), puis [décision 2019-1412 et annexe](https://www.arcep.fr/uploads/tx_gsavis/19-1412.pdf) : tableau des bandes, puissances et statuts. Le tableau de 2012 seul omet les ouvertures ultérieures.
- [OTAN : alphabet, codes et signaux](https://www.nato.int/en/news-and-events/articles/news/2017/12/21/nato-phonetic-alphabet-codes-and-signals) : alphabet international, notamment **Alfa** et **Juliett**.
- [UIT-R M.1677-1 : code Morse international](https://www.itu.int/rec/R-REC-M.1677-1-200910-I) : caractères et temporisation.
- [IARU région 1 : plans de bande](https://www.iaru-r1.org/on-the-air/band-plans/) : recommandations d’organisation des usages.
- [INRS : opérations électriques](https://www.inrs.fr/risques/electriques/operations-installations.html) : principes de mise en sécurité.

## Règles contrôlées

L’examen actuel comporte 20 QCM de réglementation en 15 minutes et 20 QCM de technique en 30 minutes. Une bonne réponse vaut 1 point; une erreur ou omission vaut 0. L’admission demande au moins 10/20 **dans chaque partie**. Une partie réussie demeure acquise un an si l’autre est échouée; une nouvelle présentation après échec attend deux mois. Le Morse n’est pas une épreuve de cet examen.

L’identification intervient au début et à la fin d’une période d’émission, au moins toutes les quinze minutes d’une émission longue sur une même fréquence et au début après un changement de fréquence. Le journal doit conserver les informations du contact et rester disponible au moins un an après sa dernière inscription. Les communications destinées aux tiers non amateurs sont réservées aux situations d’urgence ou de secours en catastrophe.

Le contenu utilise la **France métropolitaine, région 1 UIT**, pour les limites de bandes. Il distingue la puissance de sortie, la PAR et la PIRE. Les repères de bandes ne remplacent pas toutes les conditions nationales d’installation ni les sous-bandes de modes. La fiche 23 cm rappelle spécifiquement le partage avec la radionavigation.

## Format des données

`data/curriculum.json` contient `chapters[].lessons[]`, avec un identifiant stable, un thème, deux à quatre courts paragraphes et quatre exercices. Les types sont :

- `choice` : `choices` et `answer`, indice **commençant à zéro**.
- `number` : `value`, `unit`, `tolerance` absolue. La liste de choix est vide.
- `match` : `pairs[{left,right}]`.
- `order` : `choices` contient la séquence correcte; l’interface en mélange la présentation.
- `resistor` : choix et réponse comme un QCM, avec `bands` pour le dessin des anneaux.

Les choix des QCM sont distribués de manière déterministe : la réponse correcte n’occupe pas systématiquement le premier emplacement. Chaque réponse dispose d’une explication. Les valeurs numériques sont choisies pour être réalisables sur la calculatrice autorisée, avec unités explicites.

`data/reference.json` contient `categories[].rows[{term,description,extra?}]`. Les catégories sont activées pour les flashcards. Les fiches ne sont pas une reproduction de tableaux photographiés; elles combinent nomenclatures usuelles et explications originales.

## Contrôles de contenu

Les contrôles structurels vérifient les identifiants uniques, les paragraphes, les indices de réponses, les valeurs finies, les tolérances, les séquences et les paires. Les exercices de calcul ont été recalculés à partir des relations exposées : loi d’Ohm, puissance, série/parallèle, pont, RC, sinusoïde, transformateur, mélangeur, décibels, longueur d’onde et bilan de liaison. Les règles datées ont été comparées aux sources officielles ci-dessus.

Les données d’Exam’1 sont archivées et traitées séparément du parcours original. Voir la documentation de leur import pour le nombre de questions, leur provenance, les médias et les exceptions détectées.

