# Refonte progressive du Parcours — 20 octobre 2026

Le parcours contient **13 sections numérotées, 64 chapitres, 173 leçons et 1 229 questions actives**. Les 97 identifiants de leçon et les 842 UUID de questions de la 0.54 sont conservés. La refonte ajoute au total 76 leçons et 393 nouveaux UUID de questions, répartis dans les cours nouveaux et historiques. Six questions historiques sont archivées sans réattribuer leurs UUID ; la banque complète compte donc **1 235 questions**, dont 1 229 actives et 6 archivées.

La première candidate comptait 163 leçons et 1 130 questions actives. La [revue de la densité de pratique](PRACTICE-DENSITY-55.md) ajoute 99 questions choisies pour leur apport et dix séances issues de découpages. Sur la même cohorte des 66 leçons nouvelles initiales, le nombre de cours à quatre questions passe de 51 à 11, et la moyenne de 4,42 à 5,88. Ces nombres décrivent la structure du parcours ; ils ne mesurent ni sa durée réelle ni son efficacité pédagogique.

Le certificat demeure la priorité. Les fondations partent des décimaux, fractions, rapports, unités et formules avant de demander des calculs électriques. Les cours pondérés pour le certificat sont tous situés avant ou dans la section 9. Des découvertes courtes, notamment le Morse, ponctuent déjà cette partie ; la suite approfondit les usages pratiques dans **le même parcours**. Aucun verrou ne transforme les prérequis conseillés en obligation.

## Organisation

| Section | Nom | Chapitres | Leçons | Fil pédagogique |
|---|---|---:|---:|---|
| 1 | Prendre ses repères | 2 | 4 | Bienvenue Hamigo, outils, service amateur, examen et inscription |
| 2 | Les premières bases | 3 | 9 | Décimaux, proportions, carrés/racines, formules, unités, onde, identité et premières lettres OTAN |
| 3 | Faire circuler le courant | 6 | 18 | Boucle, tension, courant, Ohm, puissance puis énergie, réseaux, mesures ; premier retour productif Morse |
| 4 | Lire les signaux | 4 | 12 | Temps, fréquence, efficace, oscilloscope, condensateurs, bobines et charge RC |
| 5 | Choisir et transformer | 6 | 17 | Impédance, dB/dBm avant les filtres, résonance, sources réelles, suite OTAN et Morse |
| 6 | Construire le signal | 5 | 13 | Diodes, alimentation, transistors, classes, AOP, oscillateurs et tubes ; rappel Morse |
| 7 | Du signal au message | 6 | 21 | Modulation, blocs émetteur/récepteur, image, bruit, logique et échantillonnage |
| 8 | Relier deux stations | 12 | 31 | Bandes, antennes, lignes, retour logique, propagation, sécurité, CEM, identification, journal et règles |
| 9 | Cap sur le certificat | 1 | 5 | Méthode de calcul, synthèses, matériel et préparation des deux épreuves |
| 10 | Ta première station | 6 | 13 | QSO, diagnostic, antenne mesurée et rappels productifs des caractères Morse plus longs |
| 11 | Du terrain à l’espace | 3 | 8 | Autonomie, POTA, SOTA, passage ISS, liaisons spatiales et alphabet Morse complet |
| 12 | Des messages et des images | 6 | 13 | SDR, cascade, SSTV, APRS, modes à signaux faibles, reconnaissance et composition des chiffres Morse |
| 13 | Ton atelier radio | 4 | 9 | Groupes, ponctuation et échanges CW, automatisation encadrée, expérience personnelle |

Les titres et numéros des sections sont des données éditoriales. Ils ne servent jamais d’identifiants. Les anciens chapitres existent toujours ; leurs leçons peuvent avoir rejoint un autre groupe sans être renumérotées. Par exemple `c20-l01` reste la leçon binaire déjà publiée et `c02-l03` devient un retour sur le rythme après les premiers caractères.

## Enseigner avant d’évaluer

Chaque nouveau cours possède au moins deux paragraphes, généralement trois, qui introduisent ses notions, exemples et limites avant ses questions. Une courte leçon peut comporter quatre exercices. Le nombre retenu dépend des applications nécessaires : comparer deux représentations, choisir une méthode, expliquer une erreur, vérifier les limites du modèle ou transférer la notion. Une nouvelle valeur dans une variante ne remplace pas une situation distincte ; un rappel espacé d’une même opération reste identifié comme tel.

Les introductions historiques utiles sont conservées, avec des amorces supplémentaires lorsque leur vocabulaire supposait déjà des connaissances. `c16-l03`, consacré aux carrés, racines et rapports, est placé dans les fondations avant les calculs de puissance ; ses exemples précoces restent mathématiques. Huit exercices qui supposaient des grandeurs physiques ont rejoint les cours enseignant leurs prérequis, notamment tension efficace, période/fréquence, charge d’un condensateur, résonance LC et module d’impédance. Les cours de dB/dBm précèdent maintenant les usages en filtrage. `l-filtre-coupure` précède `c07-l02` pour enseigner coupure, octave et décade avant leur évaluation. Ces déplacements conservent les UUID.

La conservation des identifiants n’implique pas celle de tous les libellés et réglages de réponse. **270 corrections de questions historiques** sont documentées dans `data/question-editorial-revisions.json` : empreintes avant/après, motif et sources. Elles couvrent notamment les distracteurs plausibles, les consignes qui révélaient la réponse, les contextes et unités manquants, les dessins et les tolérances numériques. Chaque UUID reste attaché à son concept ; une question nouvelle reçoit un nouvel UUID.

Les reprises concernent aussi les notions hors Morse : proportions dans les fils puis les filtres, loi d’Ohm dans la pile réelle puis le câble de la station portable, énergie dans les phases réception/émission et une réserve explicite, dB dans les pertes puis le bruit et la cascade SDR. Les AOP et les limites du gain d’un transistor reviennent dans la synthèse technique ; la classe AB revient face à une émission réellement écrêtée. Les questions de logique auparavant mêlées aux oscillateurs sont réunies après les cours sur les portes, les antennes et les lignes. Les [exemples et limites de cette revue](PRACTICE-DENSITY-55.md) distinguent consolidation voisine, rappel à distance et transfert de méthode.

Le découpage change aussi les cours historiques lorsque leurs objectifs se séparent : `c03-l04` se concentre sur puissance et dissipation, tandis que les calculs d’énergie rejoignent `l-energie-duree`. `c08-l04` garde les oscillations ; ses six questions de logique conservent leurs UUID dans une séance dédiée. Un calcul de rendement quitte sa série immédiate pour revenir dans la synthèse.

Le Morse revient en plusieurs groupes : E/T/I/M et mots possibles, A/N/S/O, D/U/R/K, G/W/B/V et F/L/P/J, fin de l’alphabet, chiffres/ponctuation puis indicatifs et sens des échanges. Les petits mots sont choisis dans les caractères déjà rencontrés. Les 42 caractères présents dans les signaux réels sont contrôlés contre leur introduction graphique avant leur premier usage. Les notations ASCII demeurent uniquement dans les données d’encodage.

Neuf anciens cours Morse de 13 à 19 questions séparent désormais reconnaissance et production, sans supprimer leurs exercices. La reconnaissance garde 8 à 12 questions selon les caractères ; les rappels productifs en comptent 5 à 7 et occupent des séances distinctes. Ce découpage et l’ordre prévu ne constituent pas, à eux seuls, une preuve que chaque reprise est assez éloignée : le contrôle des intervalles et les résultats réellement exécutés sont consignés dans [VALIDATION.md](VALIDATION.md).

## Questions et variations

- **81 questions `text`**, avec réponses acceptées explicites, et **12 questions `spoken`**, avec saisie clavier disponible. Les rappels OTAN ne demandent plus d’ordonner une liste contenant déjà les mots attendus.
- **3 questions `circuit`** : DEL basse tension, branchement des appareils de mesure et synoptique de réception. Les emplacements et les composants corrects sont explicites ; le synoptique ne se présente pas comme un câblage électrique.
- **18 gabarits `template`** : prénom du profil ou d’un ami, épellation d’un groupe renouvelé, composition/écoute Morse et passage Morse → alphabet international. Les noms ne sont utilisables que si leur forme normalisée reste dans les caractères connus. Les exemples d’indicatifs ne constituent aucune attribution.
- **7 réservoirs de distracteurs dépassent les trois mauvaises réponses affichées**. La présentation peut varier sans changer la réponse correcte. Les caractères des variantes d’écoute doivent aussi respecter le contexte appris ; ce contrôle appartient au moteur de séance.

Les six exercices archivés restent dans `course-questions.json.archivedQuestions`, résolubles pour l’historique mais absents des appartenances actives : quatre exercices OTAN d’association/ordre et deux exercices d’ordre sur la méthode de calcul ou de révision. Cela comprend les UUID explicitement signalés par l’utilisateur : `q-e4dd3e4a-94df-4987-b408-845fa71de721` et `q-e72e1034-4044-43e5-9301-3e309c9106cd`.

Les explications de QSO n’enseignent pas les traductions approximatives proposées comme exemples dans la demande : `CQ` est un appel général, `DE` introduit l’identité, `QRL ?` demande si la fréquence est occupée et `73` est une salutation. Les questions sur des échanges complets évaluent le sens et l’identité de l’émetteur.

Deux écoutes CW demandent de résumer un bref échange complet simulé : reports RST échangés et demande de ralentir. Leurs signaux encodés contiennent respectivement **304 et 255 caractères**, au-delà de l’ancienne troncature à 240. Le moteur lit l’ensemble et rejette explicitement un signal dépassant 180 secondes, amorce et fin incluses. Les deux mires SSTV dessinées dans l’app opposent une inclinaison régulière à une perte de lignes. Ce sont des simulations annoncées, pas des captures de réception ; elles guident un essai de diagnostic sans prouver à elles seules la cause du défaut.

## Contrat des données et progression

`data/curriculum.json` est désormais la source complète et autoritative. Le générateur ne reconstruit plus de chapitres avec un ordre codé en dur. Il vérifie et projette cette source dans la banque et les assets, en conservant les UUID explicites et le registre historique.

| Champ | Rôle |
|---|---|
| `sections[].id/number/title/subtitle` | Sections et affichage |
| `chapters[].sectionId` | Appartenance à une section |
| `lesson.skillIds` | Compétences enseignées |
| `lesson.prerequisiteSkillIds` | Conseils de prérequis, sans blocage |
| `lesson.referenceIds` | Liens Mémo explicites, indépendants du rang ou du nom d’ID |
| `lesson.certificateWeight/explorationWeight` | Poids séparés dans les deux barres de cours réalisés |
| `lesson.knownCharacters` | Caractères autorisés pour les exercices de cette étape |
| `lesson.introducedCharacters` | Caractères nouvellement enseignés dans l’introduction |
| `lesson.legacyLessonIds` | Parents historiques équivalents pour un découpage compatible |
| `lesson.links` | Liens actionnables de l’introduction, notamment démarches et présentation ANFR |
| `lesson/question.studyToolsHint` | Repère des outils calculatrice et brouillon pendant l’entraînement |
| `question.skillIds` | Compétence évaluée par cette question, prioritaire sur l’héritage de leçon |
| `question.acceptedAnswers` | Réponses d’un rappel libre ou vocal |
| `question.template/distractors` | Variation de présentation sans réattribution d’identité |

`data/skills.json` contient **139 compétences**. Plusieurs variantes d’Ohm ou de longueur d’onde partagent leur compétence ; le suivi individuel des UUID et les récompenses restent des données distinctes. Les transferts peuvent déclarer plusieurs compétences lorsque la question les mobilise effectivement. Une réponse à corriger ne prouve pas une maîtrise durable. Une compétence Morse générique ne dispense pas de filtrer les variantes par caractères réellement étudiés. La compétence `hamigo-tools`, consacrée à l’utilisation de l’app, est exclue du SRS ; ses réponses et récompenses de cours restent conservées.

La reprise des validations de leçons est volontairement prudente. Une leçon nouvelle qui apporte une notion nouvelle n’a pas de parent historique et reste à faire. Dix-neuf leçons déclarent des parents équivalents ; la synthèse identification/journal exige ses deux parents. Les anciennes validations sont capturées séparément dans `legacyCompletedBefore55`, avant toute expansion ou nouvelle réussite. Lorsqu’un ancien état n’a pas encore ce marqueur, sa capture tient aussi compte des complétions présentes dans le socle et les événements synchronisés. La fusion unit ces historiques ; une liste explicite vide ne devient pas un nouvel historique à chaque relecture.

Seule cette provenance ancienne peut couvrir les autres parties d’un découpage. Réussir aujourd’hui le seul cours parent ne valide pas automatiquement ses nouvelles parties. La reprise ajoute les validations équivalentes sans attribuer d’XP, de réponse, d’événement ou de nouvelle récompense ; elle conserve les UUID de révision et les autres données de progression. Les comptes affichés portent sur les leçons actives, sans supprimer les identifiants historiques inconnus.

Les barres sont des proportions pondérées des cours réalisés. Elles ne constituent ni une note prédictive ni une attestation que l’utilisateur est prêt à l’examen. Le jalon de section 9 termine les cours pondérés certificat ; l’examen blanc demeure l’entraînement au format de l’épreuve.

## Narration et génération

Les adaptations de `data/lesson-narration.json` sont conservées lorsqu’un paragraphe source identique existe encore, avec son index recalculé. Une adaptation périmée est retirée et la préparation orale automatique prend le relais. Le générateur refuse toute adaptation dont le texte source ne correspond plus.

```sh
node tools/expand_curriculum.mjs
node tools/generate_lesson_links.mjs
node tools/validate_curriculum.mjs
node tools/validate_question_identities.mjs
node tools/validate_logic_content.mjs
node tools/validate_practice_density.mjs
```

`redesign_curriculum.mjs` conserve la transformation éditoriale initiale et refuse une seconde allocation sur une source déjà refondue. `refine_curriculum.mjs` documente les raffinements. Pour les modifications futures, éditer directement la source complète puis utiliser les deux générateurs ci-dessus.

Les validateurs contrôlent les références, la résolution active/archive, les identités antérieures, le contexte Morse et la régénération octet pour octet des fichiers produits. Le contrôle historique compare la source de référence au contenu actuel : une différence dans les champs suivis doit correspondre aux empreintes et au motif du manifeste des 270 révisions, plutôt qu’à une hypothèse de réponses toutes inchangées. `data/practice-review-55.json` conserve séparément la candidate de départ, les ajouts, déplacements, découpages et motifs de la revue de pratique. Les résultats réellement exécutés sont consignés dans [VALIDATION.md](VALIDATION.md). L’inventaire et les contrats des données ne prouvent ni le rendu Android, ni la compatibilité d’une installation réelle, ni une justesse pédagogique exhaustive.

## Sources et limites

F6KGL/F5KFF demeure la référence de préparation, avec son archive et sa licence conservées. La matrice Mémo complète reste distincte de ce remaniement des cours. Les ajouts pratiques sont documentés dans [SOURCES-PRACTICE.md](SOURCES-PRACTICE.md).

L’ANFR confirme actuellement l’absence d’épreuve Morse, les deux épreuves de vingt QCM, l’inscription téléphonique auprès du centre choisi et la présentation avec convocation et pièce d’identité. Les deux nouvelles leçons d’examen donnent des liens ANFR actionnables pour choisir le centre et découvrir l’interface. La démo officielle sans chrono reste distinguée des épreuves réelles chronométrées.

Le type de calculatrice est présenté avec une preuve locale datée : la session universitaire de Limoges de décembre 2026 demande un modèle collège non programmable et annonce un prêt. Ces consignes ne deviennent pas une garantie nationale d’acceptation d’un modèle ou de prêt. La fourniture du papier, le crayon, le moment autorisé pour écrire et les éventuelles notes préparées sont à confirmer auprès du centre ; aucun délai de préparation avant le chrono n’est promis. Les sources et leurs limites figurent dans [SOURCES-PRACTICE.md](SOURCES-PRACTICE.md).

Les cours de découverte ne figent ni les événements SSTV, ni les fréquences actives de l’ISS, ni des autorisations de voyage ou de site. L’atelier SSTV explique la chaîne et le diagnostic avec ses deux mires simulées ; ce n’est pas un décodeur SSTV embarqué. Les cours d’automatisation expliquent des restrictions sans promettre qu’un bot quelconque peut émettre en permanence.

Les contrôles structurels et les recoupements ciblés ne valent pas relecture de toutes les questions par un opérateur formateur. Le parcours conserve des simplifications explicitement exposées ; les installations réelles, normes complètes et cas juridiques particuliers exigent leurs sources et compétences adaptées.
