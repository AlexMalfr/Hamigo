# Signalements de contenu

Le drapeau à droite du compteur/progress bar ouvre un signalement de question. Sur une fiche Mémo, « Signaler un problème » se trouve tout en bas, après les liens de cours, avec la même icône. L’ouverture du formulaire ne change ni réponse, ni SRS, ni progression.

Le formulaire reste celui de Contact & Projet. Son URL contient des paramètres préparant un futur outil de feedback ; **Notion ne les exploite pas automatiquement** et aucun préremplissage n’est revendiqué.

| Contexte | Paramètres |
| --- | --- |
| Tous | `content`, `app_version`, `app_version_code`, `android_api`, `device_model`, `locale` |
| Question | `question_id`, `question_number` (position à partir de 1), `question_count`, `question_type`, `topic`, `section`, `source`, `lesson_id`, `image`, `visual`, `title` (énoncé), `session_title` (mode/séance) |
| Mémo | `memo_id`, `title`, `source` |

Les valeurs vides sont omises et les valeurs restantes encodées avec le constructeur d’URI Android. Identifiants de contenu, noms publics et environnement uniquement : pas de pseudo de joueur, réponses, jeton, lien de Gist ou sauvegarde. L’identifiant reste utile pour une question procédurale sans modifier sa banque ni enregistrer de nouveau schéma.

## Questions Exam1

Un lien source dont l’hôte exact est `exam1.r-e-f.org` déclenche d’abord une popup : la question vient d’Exam1. Une erreur d’énoncé/réponse est à adresser à Jean-Luc F6GPX ; un défaut d’affichage ou d’interaction peut être envoyé à Hamigo.

« Contacter Exam1 » ouvre **un brouillon**, via `ACTION_SENDTO` : destinataire public `jfortin@club.fr`, sujet avec l’ID et corps contenant le lien source, la version et un lien de contexte avec les mêmes paramètres. Aucun mail n’est envoyé par l’app. Si aucun logiciel de courrier n’est disponible, la page officielle du radio-club est ouverte avec les métadonnées en paramètres, comme le formulaire Hamigo. « Signaler à Hamigo » ouvre le formulaire habituel avec les métadonnées de la question.

Mix, Parcours et Révisions partagent le même constructeur de lien. Les IDs identifient le contenu, indépendamment de sa position dans la séance. Les questions fixes gardent leur ID lors d'un déplacement ; les variantes procédurales ont un ID reproductible. Un changement futur du générateur doit conserver ses anciennes variantes ou créer de nouveaux IDs. La version accompagne toujours le contexte, sans remplacer l'identifiant.

Contact vérifié le 8 octobre 2026 sur la [page officielle Exam1 du radio-club](https://f6kgl-f5kff.fr/exam1/), rubrique d’amélioration de la base, et dans l’archive de l’interface web Exam1. Le mainteneur du site web et celui de la banque sont distincts ; ce contact concerne la banque.
