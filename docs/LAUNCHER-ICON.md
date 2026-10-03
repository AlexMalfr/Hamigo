# Icône adaptative Hamigo

L'icône suit le format Android AdaptiveIconDrawable :

- **Background** : couleur turquoise opaque, couvrant tout le calque.
- **Foreground** : mascotte vectorielle détourée, transparente autour du dessin. Ce calque est indépendant du fond ; aucun carré ni masque rond n'est dessiné dans le logo.
- **Monochrome**, à partir d'Android 13 : silhouette alpha dédiée que le launcher peut recolorer avec son thème et son papier peint.

Les vecteurs ont un viewport 108 × 108 et le logo est placé dans la zone sûre centrale. Le launcher applique sa forme (rond, squircle, etc.), ses effets et, si les icônes thématiques sont activées et prises en charge, ses couleurs. Le fond turquoise appartient uniquement à la version couleur ; la version thématique est produite par le launcher à partir du calque monochrome.

`android:icon` et `android:roundIcon` référencent cette même ressource adaptative dans `mipmap-anydpi-v26` ; la variante `v33` ajoute le monochrome. L'application exige Android 8.0 minimum.

Référence : [documentation Android officielle des icônes adaptatives](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
