# Formulaire Hamigo

Formulaire publié : https://tally.so/r/9NqlpK. Conserver cet ID et les UUID des champs/options ; mettre à jour le formulaire existant, sans en créer un nouveau pour l’app.

## Choix des diagnostics dans Hamigo

À partir de 0.56, les quatre entrées (question, Mémo, introduction et Contact) ouvrent d’abord un dialogue. « Joindre les données de diagnostic » est cochée par défaut. La décocher ouvre exactement `https://tally.so/r/9NqlpK`, sans aucun paramètre, y compris le titre et l’identifiant du contenu. Annuler conserve l’écran. Le choix vaut pour ce retour ; l’ouverture suivante repart cochée.

Ce choix doit précéder le navigateur : Tally enregistre les champs cachés issus de l’URL même si une condition masque leurs blocs. Les actions de calcul ne permettent pas non plus de vider un champ caché ou une saisie de cette façon. Deux essais fictifs sur une copie sans notifications ont reproduit ces limites. Une case dans Tally qui ne ferait que masquer les diagnostics ne constituerait donc pas un opt-out.

Les anciennes versions de l’app continuent d’ajouter leur contexte à l’URL. Le nouveau choix exige l’APK 0.56 ou ultérieur. Les données de diagnostic ne comprennent ni réponses de l’utilisateur, progression, amis, identifiant de sauvegarde, jeton ni secret. Les pièces jointes sont choisies volontairement dans Tally.

## Présentation du formulaire

Le type de retour, l’objet et le message sont requis. L’e-mail et les fichiers sont facultatifs, avec au plus cinq fichiers de 10 Mo. Aucun e-mail automatique n’est envoyé au répondant ; les notifications du propriétaire restent activées.

**« Quelle partie de l’app est concernée ? » apparaît uniquement pour Bug, Erreur dans le contenu et Suggestion.** Il reste masqué avant sélection du type, pour Question ou besoin d’aide et pour Autre. Ce choix est requis lorsqu’il est visible. Les champs Question ou exercice, Fiche Mémo et Introduction de cours sont placés juste en dessous et suivent le même filtre de type, même après un changement de réponse.

Le choix manuel de la partie de l’app prime sur l’origine URL. Sans choix manuel, une origine question, memo ou lesson détermine le contexte. Le titre et l’ID de question, le titre Mémo et le titre d’introduction deviennent des textes en lecture seule seulement si leur paramètre est non vide et si l’origine URL correspond au contexte actif. Leurs titres portent « (pré-rempli) » en italique. Choisir une autre partie affiche une saisie vide adaptée ; le titre d’une fiche Mémo ne devient jamais celui d’une question. Aucun input ne possède de defaultAnswer global : une valeur cachée pouvait sinon être enregistrée dans une branche invisible, d’où les contextes multiples observés dans les anciennes notifications.

Objet et Ton message conservent chacun leur champ et leur placeholder. Les actions conditionnelles Tally ne modifient pas les placeholders ; dupliquer ces champs créerait des réponses distinctes. Un même UUID désigne un seul bloc et ne permet pas deux variantes conditionnelles indépendantes.

La case **« Cocher cette case pour afficher les données de diagnostic envoyées avec ce formulaire »** est décochée à l’ouverture. Elle apparaît seulement si au moins un des 33 paramètres URL reconnus est non vide. Elle affiche toute la liste et les trois paragraphes explicatifs. Le choix de transmission reste effectué dans Hamigo, avant l’ouverture du navigateur.

Sans paramètre de diagnostic non vide, l’introduction consacrée aux diagnostics, la case, la liste et tous ses paragraphes sont masqués. Seule la ligne en italique **« Aucune donnée de diagnostic n’est envoyée avec ce formulaire. »** apparaît. Un paramètre tiers comme utm_source, ou des paramètres reconnus vides, ne déclenchent pas la section. La règle d’absence est appliquée en dernier pour neutraliser aussi une réponse d’affichage conservée.

Les **25 lignes à puces** présentent les **33 valeurs** : libellés en gras, valeurs en italique, teinte gris-vert #597678. Une information absente vaut « Non transmis ». La liste constitue un seul bloc pour éviter des lignes visibles isolément après une modification de mise en page. Tally filtre les conteneurs HTML ul/li : les puces Unicode, spans enrichis et sauts de ligne sont conservés à la publication.

## Thème gratuit et Pico

Les réglages natifs gratuits donnent un fond crème #FAF8F2, du texte #173F42, un accent turquoise #087F82 et un bouton corail #FF7869 avec texte #173F42. La police Inter et la marque Tally restent présentes ; aucun CSS ni réglage Pro n’est employé. Pico est le logo visible en tête de page, depuis l’image publique du site Hamigo. Ce logo ne constitue pas une personnalisation du favicon payant.

La palette et l’URL du logo sont consignées dans data/tally/hamigo-feedback.theme.json. Appliquer les cinq couleurs via Customize et le logo via Add logo. Ne pas envoyer settings.styles dans le PATCH : la réponse serveur contient un objet pour le thème natif, alors que le schéma public d’entrée attend une chaîne de CSS. Le script ne modifie que les réglages documentés du modèle et préserve ainsi le thème gratuit existant. La relecture vérifie les cinq couleurs et Pico.

## Treize règles

| Condition | Action |
| --- | --- |
| Nature = Bug, Erreur dans le contenu ou Suggestion | Afficher le choix de partie de l’app |
| Type autorisé ET contexte actif question | Titre et référence de question |
| Type autorisé ET contexte actif memo | Titre de fiche Mémo |
| Type autorisé ET contexte actif lesson | Titre d’introduction |
| Nature = Bug | Étapes, résultat attendu et fréquence, requis si visibles |
| Nature = Erreur de contenu ET contexte actif question | Partie de la question concernée |
| Contexte question actif, origine URL question et titre présent | Valeur en lecture seule ; saisie normale masquée |
| Contexte question actif, origine URL question et ID présent | Même traitement, indépendamment du titre |
| Contexte memo actif, origine URL memo et titre présent | Valeur Mémo en lecture seule |
| Contexte lesson actif, origine URL lesson et titre présent | Valeur de cours en lecture seule |
| Au moins un des 33 paramètres non vide | Introduction diagnostics et case d’affichage |
| Case d’affichage cochée | Liste complète, trois paragraphes et espace de fin |
| Les 33 paramètres sont vides | Ligne d’absence ; masquer tout le reste de la section diagnostics |

Les conditions de contexte utilisent des groupes AND/OR imbriqués. Elles filtrent aussi les valeurs préremplies afin qu’un choix de partie conservé n’affiche rien pour Question/aide ou Autre. Le passage de Bug à Suggestion masque les étapes, le résultat attendu et la fréquence. Les champs requis d’une branche masquée ne sont pas demandés dans les autres branches.

## Paramètres d’URL

Conserver les noms exacts :

| Famille | Noms |
| --- | --- |
| Origine/contenu | `content`, `entry_point`, `page`, `title`, `source`, `source_kind` |
| Question | `question_id`, `question_number`, `question_count`, `question_type`, `question_variant`, `topic`, `section`, `lesson_id`, `image`, `visual`, `session_title` |
| Mémo | `memo_id` |
| Appareil | `app_version`, `app_version_code`, `android_api`, `android_version`, `device_manufacturer`, `device_model`, `locale` |
| Affichage | `screen_width_px`, `screen_height_px`, `window_width_dp`, `window_height_dp`, `density_dpi`, `font_scale`, `orientation` |
| Diagnostics | `diagnostic_mode` |

Les valeurs URL peuvent être modifiées par la personne qui ouvre le lien : elles ne prouvent aucune identité. `saveForLater=false` évite la réutilisation du contexte d’un précédent retour après réouverture.

## Outils et recette

```powershell
node tools/tally/build_form.mjs
node tools/tally/validate_form.mjs
.\tools\tally\Update-HamigoForm.ps1 -Publish
node tools/tally/verify_readback.mjs
```

Le script d’administration lit la clé locale dans tools/.env, ou accepte -ApiKeyFile pour un autre checkout. Ne jamais afficher ni versionner cette clé. -SnapshotOnly effectue uniquement une sauvegarde locale. Avant publication, relever updatedAt et vérifier les éventuels brouillons. -ExpectedUpdatedAt bloque une modification concurrente ; -IncludeReviewedDraft exige cette date et ne doit être employé qu’après examen et préservation du brouillon. Le corps de création reste DRAFT ; la mise à jour existante publie explicitement les blocs complets.

Le modèle comporte **82 blocs, 33 paramètres/mentions et 13 règles**. Le validateur couvre **2 952 combinaisons**, dont six origines, six états de type (aucun compris), dix états de partie de l’app, titre/ID manquants et diagnostic ouvert/fermé. Il vérifie aussi chaque paramètre individuellement, les paramètres vides, l’absence et une réponse d’affichage conservée. La relecture serveur parcourt récursivement les spans de safeHTMLSchema et vérifie les 33 mentions, les 25 titres gras, 25 valeurs italiques et 24 sauts de ligne de la liste. Les mentions sont enregistrées dans FORM_TITLE.payload.mentions, jamais dans TEXT.payload. Un TEXT vide est refusé par l’API ; les espaces éditoriaux conservés utilisent un saut de ligne.

La recette du 23 octobre confirme dans le navigateur les cinq types, les changements de type avec choix conservé, les trois contextes manuels sans paramètres, la priorité du choix manuel sur l’URL, le préremplissage adapté, l’ouverture/fermeture des diagnostics et leur absence complète hors ligne d’information. Captures du rendu publié inspectées sur ordinateur et à 390 pixels de large. La nouvelle recette n’a envoyé aucune soumission de production, pièce jointe ni notification de test. Les essais fictifs de la réparation précédente sur une copie sans notifications restent documentés dans VALIDATION.md ; les emails historiques ne sont pas modifiés.

Sources officielles : [API de mise à jour](https://developers.tally.so/api-reference/endpoint/forms/patch), [champs cachés](https://tally.so/help/hidden-fields), [conditions](https://tally.so/help/conditional-form-logic), [personnalisation gratuite](https://tally.so/help/customize-your-form), [placeholders](https://tally.so/help/how-to-add-placeholder-text-to-form-fields). Résultats réellement exécutés et limites : [VALIDATION.md](../VALIDATION.md). Preuves locales : output/tally-theme-2026-10-23/, output/tally-repair-2026-10-23/ et output/tally-retours-22/.

## Retour vers Hamigo après envoi

La redirection publiée est https://alexandre.malfre.yt/projects/Hamigo/retour.html. Cette page HTTPS est désormais en ligne et propose le retour vers Hamigo. Le PATCH et la relecture conservent ce réglage ; aucun nouvel APK n’est produit pour les changements de thème et de visibilité réalisés uniquement dans Tally.
