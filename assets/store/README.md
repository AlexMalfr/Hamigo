# Icône Hamigo

`icon-512.png` est la version carrée et opaque destinée à la fiche Google Play :
512 × 512 pixels, fond turquoise `#087f82`, sans coins arrondis ni ombre extérieure.

Pico provient directement de `PicoRenderer`, dans sa pose `HAPPY / WAVE`,
avec une phase de `0.25`, sans ombre au sol : export PNG de 560 × 560 pixels,
dessiné dans `RectF(24, 24, 536, 536)`. Ce même export sert au site
(`web/privacy/pico.png`). Pico est incliné de 14°, avec le visage bien visible et
le bras levé décollé du bord. Le dessin entier est conservé, bras et jambes compris :
seules les limites du cadrage final et la forme du launcher le recoupent. Les ondes
supérieures restent présentes, même partiellement hors cadre.

Sur la fiche Play, les ondes deviennent crème et le dessous du boîtier devient
encre pour se distinguer du fond turquoise ; leurs tracés natifs restent identiques.
Ces deux adaptations de contraste servent aussi à l’avant-plan Android pour son
fond turquoise standard.

Pour régénérer la fiche et les couches Android, avec Python et Pillow :

```sh
python tools/render_pico_icons.py
```

Les PNG Android restent détourés, sans fond ni forme de launcher intégrés dans
l’avant-plan. Le fond adaptatif séparé est opaque et turquoise `#087f82`, comme
sur la fiche Play : un launcher standard n’a donc plus à lui substituer du noir.
Un launcher qui prélève l’avant-plan seul peut toujours afficher Pico en couleur
sans fond ; les masques cercle, carré arrondi ou autres restent natifs.
La version monochrome Android 13 reprend le même cadrage avec un
contour de boîtier léger, des yeux et un sourire pleins sur une face transparente,
une molette et une grille discrètes. Les traits du visage sont extraits des pixels
natifs. Les jambes reprennent leurs positions natives et restent raccordées au
contour du boîtier. Le launcher applique sa couleur et son éventuel fond de thème.

Les aperçus cercle/carré arrondi, couleur sur fonds clair/sombre, thèmes et version
48 px sont produits dans `output/playstore-56/icons/` ; ils complètent la
vérification dans un vrai launcher.
