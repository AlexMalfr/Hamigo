# Signalements et contact

Les questions (Hamigo et Exam1), les fiches Mémo, les introductions de cours et Contact dans les réglages ouvrent le même formulaire : https://tally.so/r/9NqlpK. Le drapeau des questions reste près du compteur ; celui des introductions se trouve dans la ligne de navigation ; Mémo conserve son action en bas de fiche. Avant toute ouverture, un dialogue propose « Joindre les données de diagnostic », coché par défaut. La décocher utilise l’URL nue, sans version, appareil ni identifiant/titre de contenu. Annuler ne lance aucun navigateur ; chaque nouveau retour repart coché. L’ouverture ne change ni réponse, ni SRS, ni progression.

Le formulaire Tally est publié et prend en charge les champs d’URL. Selon l’origine, il présente le titre et l’ID concernés en lecture seule quand leurs valeurs sont présentes ; les détails supplémentaires dépendent du choix Bug, Erreur de contenu, Suggestion, Question ou Autre. Captures et fichiers facultatifs : cinq pièces au plus, 10 Mo chacune. Le champ e-mail est facultatif. Configuration complète, onze règles et 33 paramètres : [guide Tally](tally/FORMULAIRE-HAMIGO.md).

| Contexte | Métadonnées |
| --- | --- |
| Tous | Version de l’app/Android, fabricant/modèle, langue, taille physique de l’écran, dimensions réelles de la fenêtre, densité, police, orientation, mode Diagnostics, point d’entrée/page |
| Question | ID stable, type/variante d’interface, position dans la séance, nombre de questions, thème/section, type de source, cours/séance et références d’image/schéma |
| Mémo | ID, titre et provenance documentaire |
| Introduction | ID du cours, titre et thème |

Les valeurs vides sont omises et les autres encodées avec le constructeur d’URI Android. Aucun jeton, mot de passe, lien de Gist, sauvegarde, liste d’amis ou réponse de l’utilisateur n’est ajouté. Les prénoms insérés dans un énoncé personnalisé sont remplacés par un repère dans le titre envoyé. Les fichiers sont choisis volontairement dans Tally. La clé API Tally reste uniquement locale aux outils d’administration, hors Git et hors APK.

## Questions Exam1 et origines documentaires

Les questions Exam1 ouvrent elles aussi le dialogue de diagnostics avant ce même formulaire, avec source_kind=exam1 et l’ID numérique officiel si les diagnostics sont autorisés ; aucune application de courrier ne s’interpose. Les questions natives utilisent leur propre ID stable. Leur provenance documentaire est une référence de rédaction, pas une affirmation de reproduction de la question ; les imports Exam1 sont distingués dans Diagnostics. Les liens génériques ne sont plus affichés dans les corrections. Les liens contextuels utiles, comme l’inscription ANFR dans une introduction, restent disponibles.

Mix, Parcours et Révisions partagent le constructeur de lien. L’identifiant ne dépend jamais de sa position ni de la version : déplacer une question ou mélanger ses choix conserve son ID. La version sert au diagnostic et ne le remplace pas.

## Vérifications et limites

Schéma et conditions du formulaire relus par API, préremplissage d’une question fictive observé sur le lien public, branches Bug/Suggestion et e-mail observées dans le navigateur. Aucune soumission ni pièce jointe envoyée pendant cette préparation. Les scénarios Android interceptent les intents et contrôlent métadonnées, conservation de la séance et absence de données personnelles ; ils n’envoient aucun retour réel. La validation d’un envoi et des limites de fichiers reste une recette volontaire du compte Tally.

La case du récapitulatif Tally sert uniquement à voir les valeurs. Le vrai opt-out appartient au dialogue Hamigo : Tally conserve les paramètres cachés reçus dans l’URL même si leurs blocs sont masqués. Cette limite a été reproduite par deux soumissions fictives sur une copie aux notifications désactivées. Une soumission après ouverture sans paramètres n’enregistre aucune valeur de diagnostic. Les champs de contexte éditables n’ont plus de valeur par défaut globale : cela évite qu’un titre Mémo soit aussi enregistré comme question et introduction. Voir [la recette du 23 octobre](VALIDATION.md).
