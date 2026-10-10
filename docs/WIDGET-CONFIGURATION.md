# Configuration des widgets

Les trois widgets Hamigo — série, objectif quotidien et semaine — conservent leur fond et leurs proportions par défaut.

Sur un launcher qui propose la reconfiguration, **appui long sur le widget → Paramétrer → Fond transparent → Appliquer**. La configuration affiche un aperçu. Annuler ou revenir en arrière conserve le réglage précédent.

Ce choix est stocké **par instance de widget**, localement dans `hamigo_widgets`. Il est conservé lors des actualisations et redimensionnements, et retiré lorsque le widget est supprimé. Il ne fait pas partie de la progression ou des Gists. Deux widgets du même modèle peuvent avoir des fonds différents.

En mode transparent, le fond dégradé et ses vagues décoratives disparaissent réellement ; pas de rectangle crème ou de couleur d'appoint. Pico, anneaux, jauges et graphiques restent présents. Le toggle **Texte clair / Texte sombre** adapte les textes et les labels dessinés aux fonds d'écran sombres ou clairs. Le texte clair a une ombre discrète ; l'aperçu montre un fond de comparaison adapté au choix. Les marges et la composition ne changent pas. Le contraste dépend toujours du fond d'écran choisi.

Implémentation RemoteViews sans dépendance supplémentaire : activité Android de configuration avec `EXTRA_APPWIDGET_ID`, résultat annulé initialement, mise à jour du widget puis résultat OK après validation. Les métadonnées communes et Android 12+ déclarent cette activité. Les flags `reconfigurable|configuration_optional` permettent la configuration après placement tout en gardant le placement initial sans étape obligatoire sur les hosts Android 12+ compatibles. Les versions Android plus anciennes ignorent ces flags et peuvent ouvrir la configuration lors de l'ajout. Les intitulés du menu appartiennent au launcher.

Référence : [configuration et reconfiguration des app widgets Android](https://developer.android.com/develop/ui/views/appwidgets/configuration). Vérifications réellement exécutées : [VALIDATION.md](VALIDATION.md).
