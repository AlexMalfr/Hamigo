# Notions, progression et révisions

Le parcours conserve les identifiants publiés des questions et des leçons. Une section a un `id`, un numéro, un titre et un sous-titre. `sectionId` rattache un chapitre à sa section ; l'ordre du JSON détermine la lecture du parcours, jamais les identifiants.

## Pondérations et déplacements de leçons

Chaque leçon indique `skillIds`, `certificateWeight`, `explorationWeight`, `referenceIds` et éventuellement `knownCharacters`, `prerequisiteSkillIds` et `legacyLessonIds`. Les prérequis guident l'ordre conseillé sans verrouiller les cours. Les références explicites priment sur l'ancien index `lesson-resources.json`.

Les deux taux de progression sont calculés séparément : somme des poids des leçons terminées divisée par la somme de tous les poids correspondants. Un axe sans poids vaut zéro. Les anciennes validations qui n'appartiennent plus au parcours ne gonflent aucun taux. Ce suivi mesure l'avancement des cours, pas une probabilité de réussite à l'examen.

`legacyLessonIds` signifie que la nouvelle leçon est couverte par **toutes** les anciennes leçons citées. Découper une leçon terminée avant la refonte conserve ainsi la validation de chaque morceau. Réunir plusieurs leçons exige qu'elles aient toutes été terminées. À la première ouverture ou à l’import d’une ancienne sauvegarde, `LessonCompletionMigration55` capture séparément les validations historiques dans `legacyCompletedBefore55`, depuis la liste et le journal immuable (`syncBase` et `syncEvents`). Cette preuve reste stable et se fusionne par union : réussir aujourd’hui la première partie raccourcie ne valide donc pas automatiquement ses nouvelles suites. La migration ajoute les validations couvertes sans retirer les anciennes, sans XP supplémentaire et sans événement de récompense ; la répéter est sans effet supplémentaire. Une leçon introduisant des notions nouvelles ne doit pas déclarer un ancien cours comme équivalent.

Les questions retirées restent dans `archivedQuestions` et `Content.allQuestions`, pour résoudre leurs UUID historiques. Elles sont exclues des révisions proposées. Les banques et les historiques de questions ne sont pas renumérotés ; aucun ancien ID de variante procédurale ne reçoit une nouvelle signification.

## État d'une compétence

`skills.json` fournit les identifiants et titres des notions. Les `skillIds` précis d'une question priment ; la leçon fournit un héritage quand ils manquent. Les générateurs historiques possèdent une correspondance de famille explicite. Un simple thème partagé ne suffit au repli que lorsqu'il désigne une seule compétence du parcours.

`skillReviewsVersion: 2` ajoute un registre `skillReviews` à la progression de schéma 2. Chaque compétence expose échéance, intervalle, facilité, répétitions espacées, erreurs, tentatives, réussites, dernière question et dernier instant. Aucun nom, texte saisi ou enregistrement vocal n'est retenu.

Ce format est celui d'une **fonctionnalité nouvelle, absente de la base publique `37ccc612`**. Le format préparatoire v1 n'a servi qu'aux fixtures de développement. Un petit journal v1, jusqu'à 32 preuves et dans le budget global d'inspection, est converti une seule fois sans perdre ses événements. Un journal préparatoire plus long est refusé explicitement **avant toute écriture**, sans troncature silencieuse. Cette restriction ne concerne pas les sauvegardes publiées, qui n'ont aucun registre notionnel.

L'amorçage depuis les anciennes révisions UUID conserve l'urgence la plus proche et le maximum d'erreurs. Il établit une exposition, avec **zéro réussite espacée**. Une leçon ancienne terminée reste terminée sans lui attribuer rétroactivement une maîtrise notionnelle. Les révisions UUID, les XP, les clés de récompense, les événements, les amis et les préférences restent conservés.

Une réussite avant l'échéance n'allonge pas l'intervalle notionnel, y compris juste après une erreur ou avec une autre variante. Deux rappels réussis à leur échéance sont nécessaires pour l'indicateur `mastered`. Une erreur remet les répétitions espacées à zéro et revient après dix minutes. Cet indicateur est un repère interne de révision, pas une certification pédagogique.

Une séance enregistre la compétence au **premier passage seulement** de chaque question. Son ensemble d'identifiants reste en mémoire pendant la séance, y compris lorsque des corrections sont ajoutées après de nombreuses autres questions. La correction ne devient donc jamais une nouvelle preuve, même après éviction d'un élément de l'échantillon d'inspection. `Progress.answer(question, content, …)` publie en une transaction le SRS UUID, les XP et la preuve notionnelle ; les listeners ne voient aucun état intermédiaire. Les corrections continuent de suivre le chemin UUID et les compteurs globaux ordinaires. Le registre notionnel ne crée aucune récompense ; le ledger existant reste l'unique responsable des XP.

Les compteurs notionnels sont exacts sur une séquence locale, puis se fusionnent par **maximum**, sans additionner des journaux qui pourraient se recouvrir. Ils deviennent donc des **bornes inférieures** lorsque deux appareils ont appris séparément hors ligne. Les diagnostics l'indiquent avec « au moins ». Ce compromis concerne uniquement les compteurs par notion : les compteurs globaux de réponses et d'XP gardent leurs événements, leurs règles et leurs limites existants.

## Choix des révisions et synchronisation

Les questions de `hamigo-tools` (fonctionnement de l’app) sont exclues de tous les tirages SRS, dus ou aléatoires, y compris avec un ancien rappel UUID et après synchronisation. Leur historique de réponses, leurs XP et la validation du cours restent conservés. Diagnostics garde les compteurs et affiche « Hors révisions SRS » à la place de l’échéance et de la maîtrise.

Les noms de ponctuation des réponses Morse, comme « Égal » ou « Point final », sont filtrés selon leur signe appris, et non selon les lettres du libellé français. Le signal transmis conserve son contrôle strict des caractères ; une phrase libre n’est pas interprétée comme un nom de signe.

Les nouveaux overloads de `CourseRevisionBuilder` recherchent une autre question éligible de la même compétence avant de reprendre la dernière question échouée. Ils peuvent utiliser une variante dont la notion est déjà étudiée. Une leçon jamais ouverte ne devient pas éligible uniquement parce qu'elle partage un thème ; les variantes procédurales restent dans le pool de la leçon terminée et parmi les notions connues. Les cartes et Exam1 sans correspondance notionnelle conservent leur SRS UUID.

`Progress.due` représente chaque compétence à échéance par une question éligible. Un ancien rappel UUID ne redevient pas dû pendant l'intervalle futur de sa compétence ; le rappel UUID demeure le repli des questions sans correspondance. La réussite d'une autre variante met donc à jour l'échéance du concept pour toutes ses questions.

Le Morse possède un contrôle supplémentaire : les caractères décodés du signal et les distracteurs qui représentent des caractères ou des groupes transmis doivent appartenir aux caractères enseignés dans les cours vus/terminés ou aux signaux déjà rencontrés. Pour une écoute de compréhension (`morseComprehension`), les choix sont des résumés en français : seul le signal transmis est soumis à ce filtre. Une compétence générique d'écoute ne débloque donc pas arbitrairement les chiffres ou les lettres encore inconnus.

Le SRS n'est plus reconstruit depuis un journal croissant. Deux registres suffisent : le dernier échec, et le dernier rappel espacé avec son état SRS, le dernier échec qu'il connaissait et les dates du rappel précédent. Les ordres de choix sont déterministes. Une fusion retient le dernier échec et n'accepte qu'un rappel fondé sur **cet échec exact**. Une réussite calculée sur un appareil qui ignorait un échec concurrent ne peut donc pas établir une maîtrise, même si son horodatage est ultérieur. Deux premières réussites indépendantes ne sont jamais additionnées pour fabriquer deux rappels espacés. Après synchronisation d'un échec, il faut à nouveau des rappels valables pour consolider la notion.

La fusion des registres et des maxima est commutative, associative et idempotente. Une ancienne sauvegarde ne réinjecte pas ses événements dans les compteurs. Les dates, types, qualités, liens échec/rappel et preuve de délai sont validés avant import ; les champs inconnus des preuves sont refusés. Le SRS et les compteurs ne dépendent jamais de l'échantillon d'inspection.

Chaque compétence conserve au plus **32 événements d'inspection**, avec au plus **1 024 dans tout le registre**. Leur classement repose sur les UUID stables, pas sur une date modifiable : l'échantillon est déterministe et ne prétend pas être la liste des dernières réponses. L'éviction d'un élément ne retire ni ses compteurs agrégés ni le dernier échec ou la preuve SRS. Les getters lisent cette preuve de taille fixe et ne rejouent aucun historique croissant.

Le registre garde la borne existante de 2 000 compétences et possède un budget de 4 Mio distinct des 8 Mio déjà réservés au reste de la sauvegarde. Le nombre de tentatives notionnelles n'augmente donc plus sa taille après saturation de l'échantillon. Les limites du ledger historique UUID/XP restent en place : ce changement ne prétend pas rendre `syncEvents` illimité. Les anciennes versions peuvent ignorer ces champs ; les appareils doivent être mis à jour pour participer à ce suivi.

## Vérifications

Les tests JVM couvrent les corrections immédiates, les rappels espacés, les maxima de compteurs, les fusions commutatives/associatives/idempotentes, l'échec concurrent inconnu, plus de 100 001 tentatives et les échantillons évincés. Les tests Android isolés couvrent la restauration après cette charge, les grandes branches hors ligne, le budget global d'inspection, les imports préparatoires refusés ou convertis sans perte et les preuves mal formées. Le vrai listener AppModel vérifie aussi l'écriture atomique et une correction après 100 questions, dont la preuve initiale a quitté l'échantillon. L'exécution réelle est consignée séparément dans `VALIDATION.md` par l'agent principal ; la présence des tests ne prouve pas leur réussite.
