# Cours, fiches et paragraphes F6KGL

`data/lesson-resources.json`, identique à l'asset Android, associe les 97 identifiants de leçons aux 40 fiches Mémo existantes. Aucune question, carte ou leçon n'est renommée. Les correspondances sont éditoriales, regroupées par chapitre dans `tools/generate_lesson_links.mjs` ; elles ne reposent pas sur une recherche approximative dans les titres.

Le générateur vérifie que chaque leçon possède des fiches valides, que chaque fiche possède une destination F6KGL et que toutes les ancres existent dans `data/sources/f6kgl/COURS.html`. Exécution : `node tools/generate_lesson_links.mjs`. L'archive est celle documentée dans `SOURCES-COURSES.md` ; l'accès au cours en ligne a renvoyé HTTP 502 pendant cette mise à jour. Les ancres ont donc été contrôlées dans cette archive, sans revendication d'un accès en ligne réussi.

Les fiches ouvrent leur chapitre ou paragraphe pertinent, par exemple `#R31` pour l'alphabet international, `#R32b` pour les rapports RS/RST, `#R12` pour les classes d'émission, `#T015` pour les résistances et `#T041a` pour les décibels. Une fiche transversale peut couvrir plusieurs paragraphes : Formules utiles commence à la loi d'Ohm (`#T012a`), et circuits alternatifs au début de son chapitre. Un seul lien ne signifie pas que l'intégralité d'une fiche provient de ce seul paragraphe.

L'alphabet Morse et ses durées gardent leur source primaire UIT lorsqu'elle existe déjà. Leur lien complémentaire au cours F6KGL (`#R32d`) mène aux abréviations Morse et au contexte réglementaire, car le cours ne constitue pas un tableau exhaustif du code. La nouvelle table ne remplace jamais un lien primaire distinct de F6KGL.

Les introductions affichent les fiches directement, avec le composant de ligne de la bibliothèque. Une consultation ne démarre aucune question et ne modifie ni XP ni échéance SRS. Le mode de lecture seule et la position restent conservés lors d'un aller-retour entre introduction et fiche ; ouvrir à nouveau une introduction depuis Parcours repart du début.
