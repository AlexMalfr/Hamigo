# Widgets d’accueil

Les trois widgets ont une identité distincte : la Série utilise une palette chaude et les sept jours, l’Objectif du jour une jauge turquoise, et Cette semaine un graphique sur fond sombre. Une statistique principale, une illustration et des informations secondaires limitées donnent la hiérarchie ; agrandir la carte ne fait pas apparaître un tableau supplémentaire.

## Dimensions et composition

`WidgetPresentation` calcule les rectangles depuis la largeur et la hauteur en dp, sans étirer un ancien template. Les bandes donnent leur longueur au graphique ; les colonnes passent aux jours ou barres empilés et à une jauge verticale. Les petites cases gardent Pico et le graphique, les formats plus généreux lui donnent une vraie place dans la composition. Les détails inutiles sont retirés plutôt que répétés.

Les libellés, compteurs et raccourcis sont des `TextView` natifs. Leur taille est ajustée aux mesures réelles de leur texte et à la police système, avec une réserve pour les arrondis en pixels. La mascotte, le fond et les graphiques sont dessinés avec le Canvas Android partagé par l’app. La jauge reste circulaire lorsqu’elle utilise un anneau.

Le fond porte l’identifiant Android `background` et possède un contour arrondi avec `clipToOutline`, pour signaler aux launchers conformes que le widget gère déjà son découpage. Cela évite qu’un arrondi imposé supplémentaire coupe les libellés des petites cases. La bordure transparente fournit le contour, l’illustration fournit la couleur. Références : [compatibilité des widgets Android 12](https://developer.android.com/about/versions/12/features/widgets?hl=en) et [contrat d’arrondi du launcher AOSP](https://android.googlesource.com/platform/packages/apps/Launcher3/+/6df1743d23595bc9b800c9e81fcaae085ad47951/src/com/android/launcher3/widget/RoundedCornerEnforcement.java).

Sur Android 12 et plus, seuls les formats réellement annoncés par le launcher deviennent des variantes `RemoteViews`. S’il ne fournit que les minima et maxima, la composition prévoit portrait et paysage. Avant Android 12, les deux variantes d’orientation utilisent ces mêmes dimensions. Le budget de pixels est partagé entre les variantes pour borner le transfert au système.

Le minimum déclaré est 40 × 40 dp ; une case du launcher a généralement une autre taille effective. L’ajout initial conserve les dimensions habituelles de chaque widget. L’action sur toute la tuile et les événements de mise à jour ne changent pas : progression, actualisation, minuit et période de 30 minutes du fournisseur Android.

## Planches reproductibles

Avec un émulateur connecté et les dépendances Android configurées :

```powershell
.\tools\audit_widgets.ps1 -Serial emulator-5554 -Label current
.\tools\audit_widgets.ps1 -Serial emulator-5554 -Label current-large-font -FontScale 1.3 -SkipBuild
```

Le script installe uniquement sur l’appareil explicitement ciblé. Utiliser un émulateur, car il installe les APK debug et de tests. La police initiale est restaurée à la fin ; les préférences et la progression ne sont pas effacées. L’option `-SkipBuild` réutilise les derniers APK compilés. `-SkipInstall` est réservé aux répétitions lorsque ces mêmes APK sont déjà installés et évite d’invalider leur optimisation ART.

Pour une itération portant seulement sur les dessins, `-VisualOnly` produit les trois planches et vérifie leurs textes natifs sans répéter les scénarios supplémentaires de géométrie et de progression. Ces derniers restent dans la passe complète par défaut.

Le test dessine **une planche par widget**, directement depuis les vues Android, avec les 121 combinaisons des côtés 40, 56, 72, 96, 120, 160, 220, 300, 420, 600 et 800 dp. Les tailles choisies pour l’inspection détaillée sont aussi conservées à la résolution native. Le rapport vérifie les textes tronqués et les dépassements ; les essais complémentaires couvrent les frontières de composition, les formes 40 × 1600 / 1600 × 40, les compteurs élevés et plusieurs états de progression. Une grille géométrique indépendante des captures recherche les chevauchements jusqu’à 1200 dp.

Les fichiers sont copiés dans `output/widgets-0.34/atlas/<label>-font<échelle>/`. Les rapports et images sont locaux et ignorés par Git. Une planche réduite permet de repérer les défauts, puis chaque format suspect doit être ouvert à taille réelle. Les tests `HomeWidgetInstrumentedTest` complètent cet audit dans un vrai `AppWidgetHost`, notamment pour le choix d’une variante et le passage des images par l’IPC Android.

Une grille finie ne certifie pas toutes les dimensions continues ni le comportement de chaque launcher tiers. Le placement est calculé pour les dimensions reçues ; l’audit documente les formes réellement rendues et inspectées. Les validations Android antérieures à 12 restent à exécuter sur un appareil de cette génération.
