# Densité de pratique et reprises — candidate 0.55

Complément du 21 octobre : deux questions indépendantes par lignes portent désormais le total actif à 1 231 et la moyenne de la même cohorte à 5,91. Les chiffres « après revue » ci-dessous décrivent la passe de 99 ajouts du 20 octobre ; les deux ajouts suivants et leurs motifs figurent dans `data/finishing-review-55.json`.

Revue du 20 octobre 2026, à la suite du constat de 51 leçons à quatre questions parmi les 66 nouvelles leçons de la première candidate. La réponse combine un enrichissement sélectif et le déplacement d’exercices existants. Elle n’impose pas un nombre identique de questions à chaque leçon.

## Périmètre et chiffres comparables

La source éditoriale est `data/curriculum.json`. Le manifeste `data/practice-review-55.json` conserve les appartenances des questions de la candidate précédente, les motifs d’ajout, les mouvements et les propositions écartées. Il permet de distinguer un nouvel exercice d’un exercice existant déplacé ou d’une variante produite en séance.

| Inventaire | Première candidate | Après revue |
|---|---:|---:|
| Sections | 13 | 13 |
| Chapitres | 55 | 64 |
| Leçons actives | 163 | 173 |
| Questions actives | 1 130 | 1 229 |
| Questions archivées résolubles | 6 | 6 |

Les **99 nouveaux UUID** se répartissent dans 48 leçons. Les dix séances supplémentaires comprennent neuf rappels productifs Morse et une reprise de logique. Leur création ne signifie pas dix nouveaux blocs de notions à mémoriser : elle redistribue aussi des exercices existants, avec leurs identifiants.

Pour mesurer l’évolution de la densité, on conserve exactement la cohorte des **66 nouvelles leçons initiales** ; on n’ajoute pas les dix séances suivantes au dénominateur.

| Même cohorte de 66 leçons | Avant | Après |
|---|---:|---:|
| Questions attribuées à ces leçons | 292 | 388 |
| Moyenne par leçon | 4,42 | 5,88 |
| Leçons à quatre questions | 51 | 11 |

La cohorte finale contient 11 leçons à quatre questions, 13 à cinq, 25 à six, 11 à sept, quatre à huit et deux à dix. La hausse de ses appartenances n’est pas le nombre total d’exercices créés : les déplacements entrent aussi dans ce calcul, tandis que certains ajouts enrichissent les cours historiques.

Ces décomptes sont un inventaire structurel. Ils ne donnent ni le temps de réponse réel ni un score d’efficacité pédagogique. Une question de circuit, un rappel libre et la compréhension d’un échange Morse n’ont pas la même charge qu’un QCM de reconnaissance.

## Choisir les situations qui manquaient

La revue a recherché quatre apports : application graduée d’une relation, erreur fréquente, choix de méthode et transfert à un autre contexte. Les introductions sont complétées lorsque la démarche ou les conditions du modèle doivent être enseignées avant l’évaluation.

| Domaine | Exemples d’apport retenu |
|---|---|
| Décimaux et rapports | Comparer fraction et décimal, calculer une variation qui traverse zéro, reconnaître qu’un prix avec coût fixe n’est pas proportionnel |
| Formules | Écarter une donnée inutile, conserver les parenthèses en remplaçant les lettres, choisir la relation qui calcule la grandeur demandée |
| Électricité et mesures | Combiner longueur et section d’un fil, préparer bornes/fonction/calibre du multimètre, estimer une résistance interne à partir d’une chute mesurée |
| Signaux et composants | Rapprocher crête à crête et efficace entre deux instruments, suivre le courant de roue libre, calculer une coupure RC et choisir son réglage |
| Circuits actifs | Vérifier la marge d’un régulateur pendant les creux, calculer sa dissipation, dimensionner un gain d’AOP puis détecter une saturation |
| Réception et dB | Traiter une image de l’autre côté de l’oscillateur, séparer hausse de niveau et amélioration du rapport signal/bruit, préserver le message en réduisant la largeur |
| Pratique radio | Prévoir réception/émission et réserve d’énergie, interpréter une comparaison d’antennes, lire une cascade SDR, diagnostiquer une chaîne SSTV et comparer des essais reproductibles |
| Règles et procédures | Appliquer une chronologie d’identification, repérer un journal incomplet, distinguer règle radio et admissibilité d’un contact dans une activité |

La relecture a aussi retiré des propositions. Un nouveau calcul élémentaire de −10 dBm faisait doublon avec un historique : l’UUID existant a été déplacé plus tôt. Même choix pour deux RF qui tombent sur la même FI. Une question nommait elle-même le démodulateur manquant ; une autre livrait sa conclusion par les mots « environ » et « loin de la coupure ». Elles ne sont pas ajoutées pour atteindre un nombre.

Les variantes aléatoires continuent de renouveler les valeurs, groupes ou distracteurs dans une même compétence. Elles ne sont pas comptées comme autant de situations nouvelles. De même, réutiliser à distance un calcul d’autonomie constant est un rappel ; le bilan réception/émission, la réserve disponible et la chute dans le câble sont des applications distinctes.

## Des reprises hors Morse

L’espacement est porté par l’ordre du parcours et par les questions qui réemploient les bases, pas seulement par une mention de prérequis. Lorsque le transfert évalue réellement plusieurs compétences, ses `skillIds` les déclarent.

| Base | Applications et retours dans le parcours |
|---|---|
| Proportions et préfixes | Mathématiques, résistance du fil, coupure RC, réactances, longueur d’antenne, décalage d’une descendante et lecture MHz/kHz sur cascade |
| Carrés, racines et puissance | Maths, dissipation résistive, efficace, énergie stockée et impédance ; la résistance d’une DEL réemploie la puissance |
| Loi d’Ohm et tension | Réseaux, source réelle avec sa charge, chute du câble en station portable, choix de mesures sous charge au projet final |
| Énergie et durée | Première leçon énergétique, comparaison V×Ah, bilan composé de station portable, réserve explicite et rappel de méthode en synthèse |
| Décibels et bruit | Repères, dBm, filtres, PIRE, comparaison de lignes, rapport signal/bruit puis réglage du filtre et lecture de cascade SDR |
| AOP, transistor et classes | Premiers modèles, puis choix d’un suiveur et limites de β dans la synthèse ; classe AB réutilisée devant une émission écrêtée |
| Logique | Portes puis combinaisons en section 7 ; rappel des états et petite commande en section 8, après les cours d’antennes et de lignes |
| Diagnostic | Méthode de station, audio SSTV, créneaux numériques, arrêt d’une émission automatique et protocole A/B/A du projet final |

Deux leçons voisines qui enseignent puis appliquent ne suffisent pas à constituer un retour éloigné. Les cours initiaux de dB et dBm en sont un exemple : les usages ultérieurs dans les pertes, le bruit et le SDR assurent le retour dans d’autres problèmes. La reprise des états logiques est aussi placée après des cours hors logique, plutôt qu’immédiatement après les tables et les combinaisons.

L’ordre éditorial ne garantit pas le temps écoulé pour chaque utilisateur : celui-ci choisit son rythme et peut ouvrir les leçons. Le SRS est un mécanisme complémentaire. Les contrôles des intervalles réellement exécutés doivent être lus dans [VALIDATION.md](VALIDATION.md), sans déduire un résultat de la seule présence de ce tableau.

## Découper les cours existants lorsque cela sert l’apprentissage

`c03-l04` conserve son ID et devient **Puissance et dissipation**, avec sept questions. Trois calculs historiques d’énergie rejoignent **Des watts aux wattheures** (`l-energie-duree`) ; la définition de puissance remonte dans la première partie. La seconde compte huit questions après deux ajouts distincts : consommation en plusieurs phases et autonomie inverse.

`c08-l04` devient **Entretenir une oscillation**, avec quatre questions. Ses six questions de logique gardent leurs UUID dans **Retrouver les états logiques** (`l-logique-commandes`), qui en compte sept avec une application de commande. Les explications sur les portes précèdent désormais ces questions ; les oscillations ne servent plus de lieu d’introduction prématurée à ET/OU/NON.

Un calcul direct de rendement est déplacé de sa série immédiate vers la synthèse de calcul. Le retour dBm/bruit remplace de son côté une conversion élémentaire par une comparaison avant/après préamplification : un signal plus fort peut s’accompagner d’un rapport signal/bruit moins bon.

Les neuf anciens cours Morse de 13 à 19 questions séparent reconnaissance et rappel productif. La première partie contient 8 à 12 questions suivant le groupe de caractères ; la seconde 5 à 7. Les douze questions des groupes de six lettres ou de six signes ne sont pas ramenées artificiellement à huit : leur couverture demeure explicite. Les séances sont redistribuées dans le parcours avec leurs UUID ; la validation de chaque intervalle, notamment des derniers signes, reste une vérification distincte à consigner.

## Les onze cours à quatre questions de la cohorte restent intentionnels

| Leçon | Pourquoi garder cette étape courte |
|---|---|
| Une onde transporte une variation | Premier repérage ; période, formes et modulation sont enseignées plus loin |
| Une boucle pour le courant | Ouverture, court-circuit, tension sans courant et maquette adaptée ; Ohm vient ensuite |
| La tension entre deux points | Unité, référence, branchement et signe ; les décisions d’appareil reviennent dans le cours de mesures |
| Reconnaître les tubes | Reconnaissance et énergie résiduelle ; pas de développement artificiel sur des montages haute tension |
| Que fait chaque bloc ? | Synoptique manipulable, démodulation et distinction des fonctions ; pas de rappel tautologique supplémentaire |
| Le certificat dans la valise | Repères de préparation ; éviter une liste de pays ou de conditions périmées |
| Installer sans supposer | Distinguer conditions radio, urbanisme et accès au site ; le projet réel appelle une vérification locale |
| Une station sous responsabilité | Objectifs limités ; pas de sanctions ou de procédures ajoutées sans nécessité |
| Une image dans un signal audio | Introduction SSTV ; les applications et diagnostics sont dans la leçon de réception |
| Des nombres dans le trafic | Petite transition vers groupes, reports et écoute de chiffres, après leurs séances de reconnaissance et de composition |
| Du rythme à l’épellation | Combiner rappel Morse et alphabet international dans une tâche déjà plus exigeante qu’une reconnaissance isolée |

D’autres cours restent ciblés sans être à quatre : choix d’un condensateur, isolation d’inconnue, matériel d’examen, premiers contacts POTA et découverte SOTA. Les premières manipulations SDR comptent six questions après les transferts FM et largeur/bruit ; la cascade quantitative possède son propre cours. Les cours historiques de première station, de contact et de routine ne sont pas allongés pour répéter les mêmes recommandations.

## Identifiants et validations anciennes

Les 97 IDs de leçon et les 842 UUID publiés en 0.54 sont conservés ; les IDs de la première candidate ne sont pas réattribués. Un mouvement conserve l’UUID, ses réponses et son historique. Un exercice nouveau reçoit un nouvel UUID. Les six archives restent résolubles, sans revenir dans les séances actives.

Dix-neuf leçons déclarent des parents équivalents par `legacyLessonIds`. Cela ne doit pas transformer une nouvelle réussite partielle en validation de tous les morceaux. `LessonCompletionMigration55` capture donc les validations antérieures dans **`legacyCompletedBefore55`**, avant l’expansion et avant les nouvelles mutations. Lorsqu’un état ancien ne possède pas ce champ, la capture tient compte des validations du cache, du socle synchronisé et des événements de complétion. Un champ explicite vide demeure vide.

Les équivalences sont dérivées uniquement de cette provenance ; une leçon issue de plusieurs parents exige tous ces parents anciens. La fusion fait l’union des provenances. Un succès récent de la seule première partie reste un succès de cette partie. La reprise de validation n’ajoute ni XP, ni événement de réponse, ni récompense supplémentaire, et ne réécrit pas les revues UUID ni les amis.

Ces règles décrivent le contrat et les chemins de code relus. Elles ne constituent pas un résultat de test d’import ou de fusion : les scénarios effectivement exécutés, leurs limites et les contrôles de la vraie release sont consignés séparément dans [VALIDATION.md](VALIDATION.md).

## Sources et limites de la revue

[F6KGL/F5KFF](http://f6kgl.free.fr/COURS.html) reste la référence pédagogique principale, avec son archive locale et son attribution conservées. Les cas d’instruments et de composants ont aussi été recoupés avec les documents de fabricants [Fluke](https://www.fluke.com/en-us/learn/blog/digital-multimeters/how-to-measure-dc-voltage-with-a-digital-multimeter), [Texas Instruments](https://www.ti.com/video/6018730150001) et [Analog Devices](https://www.analog.com/en/resources/technical-articles/a-beginners-guide-to-filter-topologies.html). Les questions de bruit utilisent le signet F6KGL **T117d**, distinct du passage CEM T116.

Les sources réglementaires, pédagogiques et opérationnelles n’ont pas la même portée. Les textes français étayent les obligations nationales ; les règles d’une activité comme POTA ou SOTA n’attribuent pas une autorisation radio. ARISS, APRS, WSJT-X et les documents SDR/SSTV servent leurs usages respectifs. Le détail et les limites se trouvent dans [SOURCES-COURSES.md](SOURCES-COURSES.md) et [SOURCES-PRACTICE.md](SOURCES-PRACTICE.md). Les fréquences d’exercice, modèles de batterie et comparaisons de niveaux ne promettent ni conditions d’exploitation actuelles ni résultats réels sur le terrain.

La revue qualitative et les recalculs ciblés ont éliminé des doublons et des réponses ambiguës. L’inventaire automatique vérifie des appartenances, des identifiants et des contrats ; il ne certifie pas que toute question possède la meilleure difficulté ou qu’aucun prérequis implicite ne subsiste. Il ne remplace ni une relecture exhaustive par un opérateur formateur, ni l’observation de débutants, ni les vérifications Android à taille réelle.

La candidate reste destinée à l’essai : **aucune publication GitHub n’est autorisée pour cette livraison**. L’utilisateur a ensuite autorisé l’installation sur téléphone ; son résultat effectif figure dans la preuve locale de livraison. Les résultats de compilation, tests et inspection ne doivent pas être déduits de ce document ; seuls les contrôles réellement consignés font foi.
