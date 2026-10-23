# Pages publiques Hamigo

`privacy/` contient la landing page et les pages légales publiées sur
`https://alexandre.malfre.yt/projects/Hamigo/`. Les liens et styles sont locaux ;
aucune dépendance JavaScript externe, publicité ou mesure d’audience n’est ajoutée.
Le retour Tally ne transmet aucune donnée à l’app : il ouvre `hamigo://return`.

Les boutons flottants reprennent les dimensions 56 px, arrondis 16 px, couleurs
Mist/Teal et élévation des FAB Material 3 de Hamigo. Les deux logos proviennent
de `sources/images/icons/{github,playstore}.svg` du portfolio de l’éditeur ;
leurs marques gardent leurs droits propres. Les feuilles de style sont référencées
avec leur empreinte de contenu dans l’URL pour éviter un ancien CSS en cache CDN.

Pico est exporté directement de `PicoRenderer` : HAPPY / WAVE, bitmap transparent
560 × 560, bounds `(24,24,536,536)`, phase fixe `0.25`, sans ombre au sol. Le PNG
fixe sert aussi de source aux icônes. `tools/website/WebsiteArtworkExport.kt`
exporte 32 images du même moteur natif avec une phase cosinusoïdale, sans compte
ni modification de progression. Il est conservé hors des sources de tests :
pour une régénération, le copier temporairement dans le package AndroidTest,
compiler/exécuter son entrée `export` sur l’émulateur, récupérer le dossier
`pico-website-frames`, puis retirer la copie temporaire.

`python tools/website/encode_pico.py <dossier-frames> web/privacy/pico.webp`
encode ces images en WebP animé sans perte (32 × 100 ms). Une balise picture
sélectionne le PNG fixe lorsque `prefers-reduced-motion` est activé. L’ombre
au sol est un élément CSS fixe, séparé du personnage animé. Pico est plus grand
sur l’accueil, à côté du titre sur PC et au-dessus sur mobile. Sur les autres
pages, il ramène à l’accueil, comme le bouton Retour. L’accueil n’a pas de FAB
redondants : le lien discret vers le code source suit le téléchargement GitHub.

Sur PC (à partir de 1040 px), les FAB suivent une colonne sticky bornée par la
carte, décalée de son contenu et hors liens de pied de page. Sur mobile, ils
flottent au bas de la fenêtre, indépendamment de la fin de la carte.
Un favicon
PNG local est déclaré sur toutes les pages avec son empreinte de contenu.

Chaque page déclare aussi ses métadonnées Open Graph : titre, description,
URL publique et icône PNG de 512 px. L’image reprend exactement l’icône Play
dans `assets/store/icon-512.png` ; son URL contient son empreinte de contenu.
La couleur de thème turquoise `#087f82` donne sa couleur à l’aperçu Discord.

La publication utilise le SFTP du compte OVH de l’éditeur avec validation de sa
clé SSH déjà connue. Les identifiants et outils locaux de publication restent
hors du dépôt. Les anciennes pages sont sauvegardées dans `output/` avant remplacement.

Les invitations utilisent `rejoindre.html?invite=<identifiant-du-Gist-social>`.
La page ne télécharge aucun Gist : elle ouvre le lien natif et l’app demande
confirmation. `oauth/index.html` est le repli statique du callback PKCE
`https://alexandre.malfre.yt/projects/Hamigo/oauth/`, sans traitement du code.
Les deux pages gardent une politique de referrer restrictive. L’association
Android de `web/invite/assetlinks.json` doit aussi être publiée à la racine du
domaine sous `/.well-known/assetlinks.json`, en préservant les autres associations.

`web/invite/index.html` est désormais uniquement la redirection de compatibilité
des anciennes invitations GitHub Pages, avec conservation de leur query.
L’ancien callback reste enregistré dans l’app OAuth GitHub pour les APK déjà
installés. Les nouvelles versions utilisent exclusivement le nouveau callback,
vérifié strictement par origine, chemin et état PKCE.
