# Formulaire Tally de Hamigo

Le [formulaire « Un retour sur Hamigo »](https://tally.so/r/9NqlpK) est **mis à jour et publié le 22 octobre 2026**, sur le même identifiant `9NqlpK`. Il est déjà branché dans Hamigo. Son [éditeur Tally](https://tally.so/forms/9NqlpK/edit) reste disponible pour les modifications manuelles.

La nouvelle configuration contient **76 blocs, 33 champs cachés d’URL, 14 champs dans le contrat et 11 règles conditionnelles**. Les champs ne sont pas tous visibles à la fois. Les pièces jointes disposent en plus de leur propre bloc. La clé API sert uniquement à administrer le formulaire depuis les outils locaux ; elle ne doit jamais être embarquée dans l’APK ni versionnée.

## Ce qui a été simplifié

Le formulaire commence par le type de retour, **Objet** et **Ton message**. Les champs séparés qui répétaient le message (résultat observé, correction proposée, situation d’usage, essais précédents), le choix préalable d’une réponse par mail et le prénom ont été retirés. Un seul e-mail facultatif permet de demander une réponse.

| Choix | Complément affiché |
| --- | --- |
| Bug ou comportement inattendu | Étapes pour reproduire, résultat attendu et fréquence |
| Erreur dans le contenu | Partie concernée : énoncé, réponses, correction, illustration, TTS ou autre |
| Suggestion, Question ou Autre | Le message suffit |

L’origine ajoute uniquement le titre de la question et son identifiant, le titre de la fiche Mémo, ou le titre de l’introduction de cours. Sans origine spécialisée, le formulaire propose le choix de la page de l’app.

Les titres de contexte portent **« (pré-rempli, ne pas modifier) » uniquement lorsque leur propre paramètre URL est non vide**. Le titre et l’identifiant d’une question sont indépendants : fournir uniquement le titre ne marque pas l’identifiant vide comme prérempli. Ces champs restent techniquement modifiables ; le suffixe est une consigne, pas un verrou de sécurité.

Les captures et fichiers sont facultatifs : **5 fichiers maximum, 10 Mo chacun**. Le formulaire accepte les fichiers généraux, sans restriction ajoutée aux seules images. La limite correspond à celle du [plan gratuit Tally](https://tally.so/help/file-uploads).

## Partie de diagnostic repliée

En bas du formulaire, la case discrète **« Données de diagnostic envoyées avec ce formulaire »** affiche ou masque huit paragraphes de contexte en lecture seule. Elle est décochée à l’ouverture. Il s’agit d’un affichage conditionnel natif de Tally ; ce n’est pas un accordéon HTML ni une zone de saisie supplémentaire. Les 33 valeurs y figurent, avec **« Non transmis »** lorsqu’un paramètre manque.

Le dépliant en syntaxe Markdown essayé sur le formulaire de test apparaît dans l’éditeur, mais l’utilisateur a constaté qu’il disparaît dans l’aperçu. Le formulaire publié conserve donc la case conditionnelle ci-dessus, dont l’affichage et le masquage ont été observés. Ce constat porte sur cet essai ; il ne présume pas des possibilités futures de Tally.

Ces données restent jointes au retour même si le récapitulatif est fermé. Elles proviennent du lien fourni par Hamigo. Ni progression, réponse personnelle, liste d’amis, identifiant de sauvegarde, jeton ni fichier local n’est ajouté automatiquement. Les pièces jointes sont choisies volontairement.

Tally conservait par défaut les réponses et les champs cachés entre deux ouvertures. Cela pouvait réutiliser le contexte d’une ancienne question dans un nouveau retour sans paramètres. **`saveForLater` est désormais désactivé** : une réouverture repart des paramètres courants, avec le message vierge. Un retour en cours n’est donc pas conservé si la page est fermée puis rouverte.

## Notifications par e-mail

**Les notifications au propriétaire sont activées** (`hasSelfEmailNotifications=true`), puis leur activation a été relue par API après publication. Aucun destinataire supplémentaire n’a été ajouté ; Tally utilise son comportement standard pour le propriétaire du formulaire. [Documentation des notifications Tally](https://tally.so/help/self-email-notifications).

Aucune vraie soumission ni aucun e-mail de test n’a été envoyé pendant la recette. L’activation est vérifiée ; la réception effective dans la boîte mail reste à constater au prochain vrai retour. Le formulaire n’envoie pas de réponse automatique à la personne qui le remplit.

## Onze règles présentes dans le modèle

| Nº | Condition | Action |
| --- | --- | --- |
| 1 | `content=question` | Afficher le titre et l’identifiant de question |
| 2 | `content=memo` | Afficher le titre de fiche Mémo |
| 3 | `content=lesson` | Afficher le titre d’introduction |
| 4 | `content=app` ou vide | Afficher le choix de page |
| 5 | Nature = Bug | Afficher étapes, résultat attendu, fréquence |
| 6 | Nature = Erreur de contenu | Afficher la partie concernée |
| 7 | Origine question et `title` non vide | Remplacer le titre normal par le titre avec suffixe |
| 8 | Origine question et `question_id` non vide | Ajouter le suffixe uniquement au titre de l’identifiant |
| 9 | Origine Mémo et `title` non vide | Ajouter le suffixe au titre Mémo |
| 10 | Origine introduction et `title` non vide | Ajouter le suffixe au titre d’introduction |
| 11 | Case de diagnostic cochée | Afficher les paragraphes de diagnostic |

Les conditions utilisent les UUID stables des champs/options. Les champs requis d’une branche ne doivent bloquer l’envoi que lorsqu’elle est visible. Le contrat lisible est conservé dans `data/tally/hamigo-feedback.contract.json`.

## Champs cachés : conserver ces noms exacts

Tally [récupère les paramètres d’URL portant le nom d’un champ caché](https://tally.so/help/hidden-fields). Ces valeurs servent au contexte ; elles ne prouvent pas l’identité de la personne et peuvent être modifiées dans l’URL.

| Contexte | Paramètres |
| --- | --- |
| Origine et contenu | `content`, `entry_point`, `page`, `title`, `source`, `source_kind` |
| Question | `question_id`, `question_number`, `question_count`, `question_type`, `question_variant`, `topic`, `section`, `lesson_id`, `image`, `visual`, `session_title` |
| Fiche Mémo | `memo_id` |
| Version et appareil | `app_version`, `app_version_code`, `android_api`, `android_version`, `device_manufacturer`, `device_model`, `locale` |
| Affichage | `screen_width_px`, `screen_height_px`, `window_width_dp`, `window_height_dp`, `density_dpi`, `font_scale`, `orientation` |
| Mode de diagnostic | `diagnostic_mode` |

## Mettre à jour le formulaire existant

Ne pas relancer la création pour modifier le formulaire utilisé par l’app. Le script de mise à jour refuse tout autre ID que `9NqlpK`, conserve une copie de l’état initial sous `output/tally-retours-22/`, fusionne les réglages existants, remplace les blocs puis relit l’état publié.

```powershell
node tools/tally/build_form.mjs
node tools/tally/validate_form.mjs
.\tools\tally\Update-HamigoForm.ps1 -Publish
node tools/tally/verify_readback.mjs
```

Le script lit `TALLY_API_KEY` dans `tools/.env`, ou demande une saisie masquée si ce fichier n’existe pas. `-ApiKeyFile <chemin>` permet de réutiliser le fichier d’un autre checkout sans le copier. `-SnapshotOnly` effectue uniquement la lecture et la sauvegarde de la configuration. Aucun secret n’est imprimé.

Le modèle local reste au statut **DRAFT** pour une éventuelle création volontaire sur un autre compte avec `Create-HamigoForm.ps1`. Le script de mise à jour applique explicitement **PUBLISHED** au formulaire existant. Le JSON est un corps d’API ; l’import Markdown/JSON du site ne garantit pas la reprise des conditions. [Documentation de l’API de mise à jour](https://developers.tally.so/api-reference/endpoint/forms/patch).

Pour le récapitulatif dynamique, les `span.mention` des paragraphes `TEXT` référencent les 33 mentions enregistrées globalement dans `FORM_TITLE.payload.mentions`. **Ne pas placer `mentions` dans `TEXT.payload`** : l’API rejette cette propriété, même si son union OpenAPI générique l’accepte. Le validateur contrôle aussi les clés autorisées pour chaque type de bloc. [Exemple officiel de mention](https://developers.tally.so/documentation/creating-a-mention).

## Recette exécutée et limites

La validation locale passe **200 combinaisons** : cinq origines, cinq natures de retour, quatre états de préremplissage du titre/identifiant et volet ouvert/fermé. La relecture serveur confirme les 76 blocs, 11 règles, 33 champs cachés, 33 mentions dynamiques, préremplissages, limites de pièces jointes et notifications activées.

Le formulaire publié a été contrôlé dans le navigateur : titre et identifiant préremplis, aucun suffixe quand les deux valeurs manquent, suffixe du titre seul, origines Mémo et introduction, Bug puis Suggestion avec disparition des champs de bug, affichage/masquage du diagnostic et **33 valeurs fictives effectivement visibles**. Une nouvelle ouverture sans paramètres retrouve maintenant le choix de page et aucun ancien contexte. Les captures repliée et dépliée ont été inspectées.

Les preuves locales se trouvent dans `output/tally-retours-22/` : `validation.json`, `server-validation.json`, `browser-validation.json`, `published-form.json`, `published-form.readback.json`, `published-collapsed.png`, `published-expanded.png` et `diagnostics-visible.png`. Les erreurs API conservées datent des essais réparés ; le reçu et la relecture finale attestent la publication réussie.

Restent à observer lors d’un usage réel : réception du mail, envoi de pièces jointes et refus des dépassements de limites. Aucun clic sur **Envoyer mon retour**, aucun upload et aucun e-mail de test n’a été effectué.

Exemples fictifs pour compléter les essais sur mobile :

```text
https://tally.so/r/9NqlpK?content=question&question_id=demo-nato&question_type=spoken&title=Alphabet%20international&app_version=TEST&orientation=portrait
https://tally.so/r/9NqlpK?content=question&title=Alphabet%20international
https://tally.so/r/9NqlpK?content=memo&memo_id=demo-ohm&title=Loi%20d%27Ohm
https://tally.so/r/9NqlpK?content=lesson&lesson_id=demo-intro&title=Pr%C3%A9parer%20le%20jour%20de%20l%27examen
https://tally.so/r/9NqlpK?content=app&page=Parcours&app_version=TEST
```
