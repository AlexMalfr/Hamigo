# Signalements et contact

Les questions (Hamigo et Exam1), les fiches Mémo, les introductions de cours et Contact dans les réglages ouvrent le même formulaire : https://tally.so/r/9NqlpK. Le drapeau des questions reste près du compteur ; celui des introductions se trouve dans la ligne de navigation ; Mémo conserve son action en bas de fiche. L’ouverture ne change ni réponse, ni SRS, ni progression.

Le formulaire Tally est publié et prend en charge les champs d’URL. Selon l’origine, il préremplit le titre et l’ID concernés ; les détails supplémentaires dépendent du choix Bug, Erreur de contenu, Suggestion, Question ou Autre. Captures et fichiers facultatifs : cinq pièces au plus, 10 Mo chacune. L’e-mail n’est demandé que si une réponse par e-mail est souhaitée. Configuration complète, neuf règles et 33 paramètres : [guide Tally](tally/FORMULAIRE-HAMIGO.md).

| Contexte | Métadonnées |
| --- | --- |
| Tous | Version de l’app/Android, fabricant/modèle, langue, taille physique de l’écran, dimensions réelles de la fenêtre, densité, police, orientation, mode Diagnostics, point d’entrée/page |
| Question | ID stable, type/variante d’interface, position dans la séance, nombre de questions, thème/section, type de source, cours/séance et références d’image/schéma |
| Mémo | ID, titre et provenance documentaire |
| Introduction | ID du cours, titre et thème |

Les valeurs vides sont omises et les autres encodées avec le constructeur d’URI Android. Aucun jeton, mot de passe, lien de Gist, sauvegarde, liste d’amis ou réponse de l’utilisateur n’est ajouté. Les prénoms insérés dans un énoncé personnalisé sont remplacés par un repère dans le titre envoyé. Les fichiers sont choisis volontairement dans Tally. La clé API Tally reste uniquement locale aux outils d’administration, hors Git et hors APK.

## Questions Exam1 et origines documentaires

Exam1 ouvre directement ce même formulaire, avec source_kind=exam1 et l’ID numérique officiel ; aucune popup de choix ni application de courrier ne s’interpose. Les questions natives utilisent leur propre ID stable. Leur provenance documentaire est une référence de rédaction, pas une affirmation de reproduction de la question ; les imports Exam1 sont distingués dans Diagnostics. Les liens génériques ne sont plus affichés dans les corrections. Les liens contextuels utiles, comme l’inscription ANFR dans une introduction, restent disponibles.

Mix, Parcours et Révisions partagent le constructeur de lien. L’identifiant ne dépend jamais de sa position ni de la version : déplacer une question ou mélanger ses choix conserve son ID. La version sert au diagnostic et ne le remplace pas.

## Vérifications et limites

Schéma et conditions du formulaire relus par API, préremplissage d’une question fictive observé sur le lien public, branches Bug/Suggestion et e-mail observées dans le navigateur. Aucune soumission ni pièce jointe envoyée pendant cette préparation. Les scénarios Android interceptent les intents et contrôlent métadonnées, conservation de la séance et absence de données personnelles ; ils n’envoient aucun retour réel. La validation d’un envoi et des limites de fichiers reste une recette volontaire du compte Tally.
