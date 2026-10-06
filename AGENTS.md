# Reprendre le travail sur Hamigo

Ces consignes résument les préférences exprimées par l'utilisateur pour ce projet. Ses demandes actuelles priment. Lire `README.md`, puis les sections pertinentes des notes `docs/UPDATES-*.md` et de `docs/VALIDATION.md` avant de modifier une fonctionnalité ; ne pas déduire l'état actuel d'une ancienne conversation ou d'un ancien APK.

## Intention du produit

- Hamigo est une app Android native, en français, pour préparer le certificat d'opérateur des services d'amateur. Identifiant : `com.malfreyt.alexandre.hamigo`. Usage personnel entre amis, dépôt GitHub **privé** `AlexMalfr/Hamigo`.
- Le design et les interactions ludiques sont une raison d'être du produit : Material 3 et Compose, palette crème/turquoise/corail/jaune, mascotte Pico. Préserver les composants et le langage visuel existants, avec des textes clairs, concrets et peu kitsch.
- Parcours est la page principale. Ordre des onglets : Défis, Mémo, Parcours, Équipe, Moi. Retour depuis un autre onglet principal ramène à Parcours ; les écrans internes reviennent à leur parent.
- Les cours enseignent des notions avant de les évaluer. Une progression ne doit pas récompenser une séance ratée comme une maîtrise acquise. Varier les exercices pertinents et expliquer les corrections.
- Mémo doit rendre les connaissances utiles du cours consultables dans un format portable : faits structurés, exemples explicitement distingués, astuces, outils interactifs lorsque cela aide. Éviter les listes d'exemples arbitraires qui remplacent l'explication d'une notion.

## Contenu et sources

- Références principales : [F6KGL](http://f6kgl.free.fr/COURS.html) et [Exam1 REF](https://exam1.r-e-f.org/). Consulter `docs/SOURCES-COURSES.md`, `docs/SOURCES-EXAM1.md`, `docs/MEMO-COVERAGE-2026-10.md` et les audits du contenu.
- Vérifier les affirmations techniques et réglementaires à leur source ; les textes réglementaires actuels doivent être distingués des tableaux d'une édition ancienne. Documenter les limites et exclusions, sans présenter un audit automatisé comme une certification pédagogique exhaustive.
- Conserver les archives, attributions et imports reproductibles. Le caractère privé du projet n'autorise pas à présumer la licence de chaque source.
- Les exercices procéduraux doivent rester cohérents avec la notion enseignée, avec réponses, tolérances et explications vérifiables. Préserver les identifiants stables des cartes/questions déjà utilisés par la progression.
- Représenter le Morse avec les composants graphiques de l'app, y compris dans outils, flashcards et corrections ; ne pas réintroduire un affichage ASCII comme représentation pédagogique. Une notation technique peut rester nécessaire au parsing et à l'échange.

## Façon de travailler

- Avancer de manière autonome jusqu'au résultat demandé : implémentation, vérifications, correction des défauts, livraison. Ne pas redemander une permission déjà donnée pour une action dans le périmètre autorisé.
- L'autorisation existante couvre le versionnement Git, le push et les releases sur le dépôt privé, ainsi que l'installation sur le téléphone via ADB. Elle ne couvre pas une publication publique, un effacement de données ou des messages réels à des amis.
- Donner des mises à jour courtes en français : résultat appris, choix utile, point restant à vérifier. Terminer avec ce qui a changé, ce qui a réellement été testé et si l'APK a réellement été installé.
- Pour du travail en parallèle demandé par l'utilisateur, attribuer des fichiers ou responsabilités distincts aux agents. Partager le contrat des interfaces, intégrer leurs changements et inspecter leurs résultats. Un agent principal coordonne builds, tests Android et livraison ; éviter les compilations concurrentes sur les mêmes sorties et les commandes ADB concurrentes.
- Utiliser un worktree si l'isolation est utile, sans perdre les changements locaux. Ne pas imposer des worktrees quand des fichiers séparés suffisent. Résoudre les conflits en conservant le comportement attendu, pas en choisissant aveuglément un côté.
- Lire l'état Git avant les modifications. Ne pas écraser le travail d'un autre agent ou de l'utilisateur. Garder les changements concentrés sur la demande et éviter les refontes ou dépendances inutiles.

## Revue visuelle exigeante

Une compilation ou un test de clic réussi ne valide pas le rendu. L'utilisateur relève les défauts fins : les captures doivent être examinées avec le même soin.

- Inspecter les écrans modifiés sur un appareil ou l'émulateur, à leur taille réelle ; une planche réduite sert au repérage, pas à valider les détails. Vérifier les états pertinents : début/fin de scroll, header détaché, section ouverte/fermée, vide/chargé, clavier, popup, animation intermédiaire et finale.
- Chercher activement les marges excessives, alignements, labels trop espacés, textes tronqués, collisions, ombres coupées et fonds parasites. Garder une densité lisible : réduire l'espace gratuit sans sacrifier les zones de toucher ni l'accessibilité.
- Les headers sticky et leur ombre doivent suivre leur silhouette et toute la largeur prévue. Un débordement d'avatar doit découler naturellement du layout, sans déplacement artificiel au scroll.
- Le bouton central de navigation dépasse réellement de la barre. L'espace négatif est transparent, avec raccords arrondis ; aucun faux fond crème ne doit masquer le contenu. Les effets d'appui restent dans les boutons et l'ombre du cercle reste plus douce que celle de la barre.
- Tester les éléments proches du clavier avec l'IME réellement ouvert : boutons accessibles, calculatrice, insets et fond inférieur. Contrôler les retours prédictifs annulés et validés ; ne pas animer une sortie qui doit d'abord demander confirmation.
- Pour un composant redimensionnable, vérifier les formats étroits, courts, hauts et larges ainsi qu'une police agrandie. Tester les widgets dans un vrai hôte Android, pas seulement dans une image de présentation. Restaurer les réglages de l'émulateur ensuite.
- Revoir une capture après la correction, et pas seulement celle qui a révélé le défaut. Ne pas déclarer un rendu « bon » sans inspection ; signaler ce qui demeure non observé.

## Données, GitHub et appareils

- Préserver progression, amis, préférences et identifiants de sauvegarde. Ne pas utiliser suppression des données, désinstallation ou reset comme raccourci de debug sur le téléphone personnel. Une remise à zéro nécessite une demande explicite et doit respecter les exceptions demandées.
- Les deux Gists ont des rôles distincts : sauvegarde complète et résumé social. Ils sont non répertoriés, mais accessibles avec leur URL ; ne pas les qualifier de privés/chiffrés. Respecter les règles de fusion et tombstones documentées dans `docs/SOCIAL.md`.
- Employer des fixtures isolées sur l'émulateur et des serveurs locaux pour les tests. Ne jamais envoyer de demande/commentaire réel à un ami pour tester. Ne pas confondre un succès synthétique OAuth avec une connexion personnelle complète.
- Ne jamais afficher ou versionner des jetons, mots de passe, clés de signature, secrets OAuth ou fichiers personnels de sauvegarde. `.tools/`, `local.properties` et `output/` sont ignorés ; garder les credentials hors des logs et des arguments visibles lorsque possible.
- Réutiliser la clé de release existante et la configuration OAuth locale. Ne pas régénérer la clé pour résoudre un problème de build : les mises à jour et App Links dépendent de sa continuité.
- Vérifier `adb devices -l` et cibler explicitement l'appareil. L'adresse/port du téléphone changent : ne pas graver une ancienne IP dans les outils ou cette documentation. Installer l'APK release sur le téléphone avec `adb install -r`, sans effacer ses données ; utiliser l'émulateur pour les tests instrumentés.

## Compiler, vérifier et livrer

Le projet utilise Gradle Wrapper, JDK 17 ou plus et le SDK Android configuré localement. Sur cette machine, le JBR d'Android Studio est disponible ; adapter les chemins à l'environnement futur.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

- Reprendre un répertoire de build local existant si utile. `-PhamigoBuildRoot=<chemin local>` permet d'isoler les sorties lorsque Windows/OneDrive verrouille un ancien build ; ce contournement n'est pas une obligation et son ancien nom n'est pas une version produit.
- Exécuter les tests pertinents pour le changement et les régressions touchées. Des assertions utiles portent sur le comportement réel, la conservation des données, les calculs ou les contraintes de layout ; ne pas ajouter des tests qui recopient simplement l'implémentation. Après succès, élargir seulement si un risque ou un changement supplémentaire le justifie.
- Consigner dans `docs/VALIDATION.md` les résultats réellement exécutés, les échecs réparés, les captures inspectées et les limites. Garder APK, captures et rapports locaux dans `output/`. Mettre à jour les notes de version et le README lorsque le comportement documenté change.
- Les versions normales suivent le nombre de commits : `versionCode = nombre de commits`, `versionName = 0.<nombre>+<SHA>`. Éviter les commits intermédiaires gratuits qui gonflent la numérotation. Une révision au même numéro utilise `-PhamigoVersionCommit=<nombre>` uniquement si l'utilisateur l'a demandée.
- Construire la release depuis le commit propre destiné à être livré, avec l'historique Git complet. Vérifier la version effective (`:app:printAppVersion`), la signature et le SHA-256 de l'APK, puis pousser et publier la release privée avec cet APK.
- Après installation, contrôler la version du package sur le téléphone. Conserver une preuve de livraison locale (commit, version, hash, signature, lien de release, installation). Ne pas annoncer une installation ou un push sur la seule base d'une tentative.
- Si le téléphone est indisponible, terminer les vérifications et la release réalisables, puis indiquer clairement que l'installation reste à faire. Ne pas prétendre une validation sur téléphone à partir de l'émulateur.
