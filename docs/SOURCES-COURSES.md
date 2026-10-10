# Sources pédagogiques et vérifications

Contenu préparé le 3 octobre 2026 et ressources Mémo complétées les 5 et 7 octobre pour Hamigo. Le parcours comporte **22 chapitres, 97 leçons et 842 exercices originaux actifs**. Huit rappels Morse répétitifs ont été retirés des 850 exercices historiques en 0.47. Les 56 leçons initiales ont chacune huit exercices, avec un paragraphe supplémentaire pour les pièges et le transfert. Les fiches de référence contiennent **40 catégories, 1 093 entrées et 970 flashcards**, dont les identifiants des 390 anciennes cartes sont conservés. Les paragraphes, exemples chiffrés et exercices du parcours sont rédigés pour l’application ; ils ne recopient pas les paragraphes du cours. Les connaissances et tableaux des ressources sont suivis dans [la matrice Mémo](MEMO-COVERAGE-2026-10.md), [les sources radio](MEMO-RADIO-SOURCES.md) et [les sources techniques](MEMO-TECHNICAL-SOURCES.md). L'audit antérieur du parcours demeure dans [CONTENT-AUDIT-2026-10.md](CONTENT-AUDIT-2026-10.md).

## Approfondissements du parcours

Les identifiants des chapitres et leçons initiaux sont conservés. Les anciennes clés de questions du Parcours sont associées à des UUID en 0.47, avec une migration temporaire préservant leur historique : [identités et retrait prévu en 0.52](QUESTION-IDENTITIES.md). Les approfondissements suivants sont placés après les notions nécessaires :

- **c15 — Le Morse, de A à Z** : 14 leçons; les 26 lettres, les dix chiffres, la ponctuation courante, la temporisation, l’écoute, la composition, les mots et les groupes d’indicatif. Cette compétence reste facultative pour le certificat français, qui ne comporte pas d’épreuve Morse actuelle.
- **c16 — Les maths du poste** : conversions, isolation d’inconnues, carrés et racines, rapports et lecture d’oscilloscope.
- **c17 — RLC, au-delà des recettes** : énergie stockée, réactances calculées, module/phase, accord, Q et chargement.
- **c18 — RF : voir ce qui sort du poste** : spectres et harmoniques, mélangeurs et images, compression/intermodulation, dBm et rapport signal/bruit.
- **c19 — Antennes et lignes à la loupe** : coefficient de réflexion, puissance réfléchie et ROS, longueur électrique, pertes et diagnostic mesuré.
- **c20 — Le numérique décodé** : symboles et débit, échantillonnage/repliement, détection et correction d’erreurs.
- **c22 — Binaire et portes logiques** : la leçon historique `c20-l01` est déplacée ici, avec son contenu et ses identifiants intacts. Trois leçons originales supplémentaires enseignent octets/hexadécimal, ET/OU/NON puis NAND/NOR/XOR et lecture combinatoire ; 24 nouveaux exercices. Placé avant c20, sans modifier les données sauvegardées des joueurs.
- **c21 — Le labo des bons réflexes** : reports honnêtes, diagnostic reproductible, réglage d’émission et méthodes de révision.

Les leçons techniques ajoutées contiennent huit exercices chacune ; après la séparation de la logique, c20 en compte trois et c22 quatre. Les leçons Morse utilisent de plus grands réservoirs pour couvrir chaque caractère en lecture, écoute et composition. La progression conserve un ordre pédagogique; la sélection de questions en séance peut varier. Ces nouvelles questions sont distinctes de la banque Exam’1 et ne prétendent pas être des sujets officiels.

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
- [ARRL : apprentissage du Morse](https://www.arrl.org/learning-morse-code/) et [temporisation Farnsworth](https://www.arrl.org/files/file/Technology/x9004008.pdf) : entraînement auditif et espacement augmenté entre caractères.
- [IARU région 1 : plans de bande](https://www.iaru-r1.org/on-the-air/band-plans/) : recommandations d’organisation des usages.
- [INRS : opérations électriques](https://www.inrs.fr/risques/electriques/operations-installations.html) : principes de mise en sécurité.

## Règles contrôlées

L’examen actuel comporte 20 QCM de réglementation en 15 minutes et 20 QCM de technique en 30 minutes. Une bonne réponse vaut 1 point; une erreur ou omission vaut 0. L’admission demande au moins 10/20 **dans chaque partie**. Une partie réussie demeure acquise un an si l’autre est échouée; une nouvelle présentation après échec attend deux mois. Le Morse n’est pas une épreuve de cet examen.

L’identification intervient au début et à la fin d’une période d’émission, au moins toutes les quinze minutes d’une émission longue sur une même fréquence et au début après un changement de fréquence. Le journal doit conserver les informations du contact et rester disponible au moins un an après sa dernière inscription. Les communications destinées aux tiers non amateurs sont réservées aux situations d’urgence ou de secours en catastrophe.

Le contenu utilise la **France métropolitaine, région 1 UIT**, pour les limites de bandes. Il distingue la puissance de sortie, la PAR et la PIRE. Les repères de bandes ne remplacent pas toutes les conditions nationales d’installation ni les sous-bandes de modes. La fiche 23 cm rappelle spécifiquement le partage avec la radionavigation.

## Format des données

`data/curriculum.json` contient `chapters[].lessons[]`, avec un identifiant stable, un thème, des paragraphes explicatifs et au moins huit exercices par leçon. La version distribuée référence leurs UUID dans la banque séparée `course-questions.json` ; elle n'est donc plus une copie identique du fichier d'édition. Les types sont :

- `choice` : `choices` et `answer`, indice **commençant à zéro**.
- `number` : `value`, `unit`, `tolerance` absolue. La liste de choix est vide.
- `match` : `pairs[{left,right}]`.
- `order` : `choices` contient la séquence correcte; l’interface en mélange la présentation.
- `resistor` : choix et réponse comme un QCM, avec `bands` pour le dessin des anneaux.
- `truefalse` : deux choix Vrai/Faux et un indice de réponse.
- `cloze` : texte à compléter avec `___`, choix et indice de réponse.
- `multiselect` : plusieurs bonnes réponses; `bands` contient leurs indices sous forme de chaînes.
- `morseListen` : `bands[0]` contient les points et traits ASCII, les espaces séparent les lettres, `/` sépare les mots; choix et indice de réponse identifient le signal entendu.
- `morseEncode` : la même notation dans `bands[0]` définit le signal à composer avec les boutons de points, traits et séparateurs.
- `binary` : `value` est l’entier non signé à composer; `unit` est la largeur binaire (1 à 8 bits).
- `waveform` : `bands` contient le nom de chaque illustration (`sine`, `square`, `dc`, `am`, `fm`, `noise`), aligné sur les choix.
- `estimate` : `value`, `unit` et `tolerance`, plus `bands` avec minimum, maximum et pas du curseur.

Les choix des QCM sont distribués de manière déterministe : la réponse correcte n’occupe pas systématiquement le premier emplacement. Chaque réponse dispose d’une explication. Les valeurs numériques sont choisies pour être réalisables sur la calculatrice autorisée, avec unités explicites.

`data/reference.json` contient `categories[].rows[{term,description,extra?}]`. Les catégories sont activées pour les flashcards. Les fiches ne sont pas une reproduction de tableaux photographiés; elles combinent nomenclatures usuelles et explications originales.

## Contrôles de contenu

Les contrôles structurels vérifient les identifiants uniques, les paragraphes, les indices de réponses, les valeurs finies, les tolérances, les séquences et les paires sans ambiguïté. Les exercices de calcul ont été recalculés à partir des relations exposées : loi d’Ohm, puissance, série/parallèle, pont, RC, sinusoïde, transformateur, mélangeur, décibels, longueur d’onde et bilan de liaison. Les règles datées ont été comparées aux sources officielles ci-dessus. Les nouvelles notions de spectre, échantillonnage et lignes s’appuient aussi sur les sections correspondantes du cours F6KGL/F5KFF déjà archivé.

`node tools/expand_curriculum.mjs` produit l’extension à partir des données actuelles et contrôle les contrats des types. Le script est idempotent : ses repères internes d'édition résolvent les UUID déjà attribués, tandis que les chapitres et leçons gardent leurs IDs. Il ne télécharge aucune donnée et ne génère pas de question arbitraire en substituant des mots. Les variations numériques en séance appartiennent au générateur de l’application. Les contrôles d'identité et de séparation du contenu sont décrits dans [QUESTION-IDENTITIES.md](QUESTION-IDENTITIES.md).

Pour le Morse, la table factuelle et les durées ont été recoupées avec la **recommandation UIT-R M.1677-1 en vigueur** ([PDF français](https://www.itu.int/dms_pubrec/itu-r/rec/m/R-REC-M.1677-1-200910-I!!PDF-F.pdf)). Les explications pédagogiques et les activités sont originales; les tableaux et paragraphes du PDF ne sont pas reproduits comme document.

Les données d’Exam’1 sont archivées et traitées séparément du parcours original. Voir la documentation de leur import pour le nombre de questions, leur provenance, les médias et les exceptions détectées.

