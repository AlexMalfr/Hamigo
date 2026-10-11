# Configuration des widgets

Les quatre widgets Hamigo — série, objectif quotidien, semaine et équipe — ont des compositions adaptées aux formats étroits, courts, hauts et larges. Pico reste présent, y compris dans les plus petits formats.

Sur un launcher qui propose la reconfiguration, **appui long sur le widget → Paramétrer → Fond transparent → Appliquer**. La configuration affiche un aperçu. Annuler ou revenir en arrière conserve le réglage précédent.

Ce choix est stocké **par instance de widget**, localement dans `hamigo_widgets`. Il est conservé lors des actualisations et redimensionnements, et retiré lorsque le widget est supprimé. Il ne fait pas partie de la progression ou des Gists. Deux widgets du même modèle peuvent avoir des fonds différents.

En mode transparent, le fond dégradé et ses vagues décoratives disparaissent réellement ; pas de rectangle crème ou de couleur d'appoint. Pico, anneaux, jauges et graphiques restent présents. Le toggle **Texte clair / Texte sombre** adapte les textes et les labels dessinés aux fonds d'écran sombres ou clairs. Le texte clair a une ombre discrète ; l'aperçu montre un fond de comparaison adapté au choix. Les marges et la composition ne changent pas. Le contraste dépend toujours du fond d'écran choisi.

Implémentation RemoteViews sans dépendance supplémentaire : activité Android de configuration avec `EXTRA_APPWIDGET_ID`, résultat annulé initialement, mise à jour du widget puis résultat OK après validation. Les métadonnées communes et Android 12+ déclarent cette activité. Les flags `reconfigurable|configuration_optional` permettent la configuration après placement tout en gardant le placement initial sans étape obligatoire sur les hosts Android 12+ compatibles. Les versions Android plus anciennes ignorent ces flags et peuvent ouvrir la configuration lors de l'ajout. Les intitulés du menu appartiennent au launcher.

Référence : [configuration et reconfiguration des app widgets Android](https://developer.android.com/develop/ui/views/appwidgets/configuration). Vérifications réellement exécutées : [VALIDATION.md](VALIDATION.md).

## Comparaison sociale — 0.49

Le widget **Équipe** compare les XP des sept derniers jours, ou les XP d’aujourd’hui selon le choix **Comparer** dans sa configuration. Ce choix, le fond transparent et la couleur du texte sont indépendants pour chaque instance. Il ouvre la page Équipe au toucher.

Le classement emploie les résumés publics déjà conservés sur l’appareil et les avatars en cache disque. Le widget ne lit aucun jeton et ne démarre aucune requête réseau. Les synchronisations habituelles de Hamigo actualisent ces données ; elles ne constituent pas une comparaison en direct. Un total quotidien absent apparaît comme inconnu, pas comme zéro. La personne qui utilise le widget conserve sa place dans la composition même lorsqu’elle n’est pas parmi les premiers ; les formats minuscules montrent son rang, et les autres formats une sélection lisible d’équipiers.

Le modèle utilise des textes Android natifs et le même dessin par couches de Pico que l’app. Il s’adapte aux tailles réellement fournies par le launcher, avec une marge qui protège les contenus de ses arrondis. Les préférences de widgets ne sont pas envoyées dans les Gists.

## Aperçus et placement — amendement 0.49

Les images du sélecteur du launcher sont des exemples statiques **rendus avec les véritables RemoteViews**, via `WidgetPreviewAssetsInstrumentedTest.regeneratePickerAssetsFromTheRealWidgets` sur l’émulateur. Les données de démonstration sont déterministes ; chaque image ajoute une petite mention « ↔ Redimensionnable » hors du widget. Les WebP sont conservés dans `drawable-nodpi` et utilisés par `previewImage` ainsi que les layouts de preview Android 12+. Ce sont des aperçus, pas des XP fictifs affichés au placement : le layout initial montre seulement Pico en attendant les vraies données.

Les dimensions préférées viennent des mêmes ressources pour les métadonnées, le rendu de repli, la configuration et la génération : série 120 × 120 dp, objectif 160 × 150 dp, semaine 220 × 150 dp et équipe 200 × 160 dp. Android 12+ demande respectivement 2 × 2, 2 × 2, 3 × 2 et 3 × 2 cellules ; les dimensions réellement attribuées dépendent du launcher. Les minima redimensionnables restent 40 dp dans chaque direction. Un widget déjà placé conserve ses dimensions.

Dans Paramétrer, l’aperçu reste **dynamique**, avec les données locales, les préférences par instance et la composition à ses proportions par défaut ; un label rappelle le redimensionnement depuis l’écran d’accueil. Après une modification des dessins ou des dimensions, régénérer les quatre assets, les copier depuis le dossier externe `widget-previews-0.49` de l’émulateur et les inspecter à taille native avant livraison.
