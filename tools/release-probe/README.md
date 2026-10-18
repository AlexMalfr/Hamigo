# Contrôle de l’APK release optimisé

Ce petit APK d’instrumentation Java n’utilise que les API Android. Il reste indépendant du Kotlin et des classes Compose renommées par R8 dans la **vraie** release, contrairement à l’APK AndroidTest du debug. Il n’est pas une dépendance de Hamigo et n’est jamais publié avec elle.

`speech` contrôle la lecture réelle d’une introduction et d’une question avec le moteur installé, les commandes Lire/Arrêter et la suppression des WAV temporaires, sur l’APK signé et optimisé. Il vérifie que la narration ne modifie ni XP ni SRS. Aucun compte n’est connecté. Pour un probe fonctionnel sur un émulateur lent, les échelles d’animation peuvent être désactivées temporairement puis restaurées ; la revue visuelle du mouvement se fait séparément avec animations actives.

Il se signe avec la clé Hamigo locale existante. Il refuse un appareil physique ; les modes `flow`, `diagnostics`, `catalog` et `social` utilisent des fixtures sans compte et modifient seulement les préférences de l’émulateur. Le mode `storage` lit les tailles du package via StorageStatsManager, sans lire la progression.

Depuis la racine, avec JDK et SDK Android configurés :

```powershell
.\gradlew.bat -p tools/release-probe assembleRelease
adb -s emulator-5554 install -t -r tools/release-probe/build/outputs/apk/release/HamigoReleaseProbe-release.apk
adb -s emulator-5554 shell am instrument -w -e mode flow com.malfreyt.alexandre.hamigo.releaseprobe/.ReleaseProbe
```

Pour une simple mesure, remplacer `flow` par `storage`. Le résultat doit contenir **OK**. Les captures et `storage-probe.json` sont écrits dans le dossier externe de Hamigo sur l’émulateur.

Le scénario contrôle l’accueil sans barre de téléchargement, la banque réellement téléchargée et complète, l’examen et ses images originales/traitées, les fiches natives, la version et la vérification manuelle, ainsi que les constructeurs des workers persistés. Il ne connecte pas GitHub et n’envoie aucun signalement ni commentaire.

`diagnostics` contrôle la réception Fréquence continue, sa priorité face au TTS et son mute. `catalog` contrôle le lecteur d'illustration, ses fermetures, le balayage horizontal et les flèches entre les résultats filtrés, sans modifier la progression. Pour rejouer un scénario, arrêter d'abord le processus Hamigo sur l'émulateur (`adb -s emulator-5554 shell am force-stop com.malfreyt.alexandre.hamigo`), sans effacer ses données. Les sélecteurs excluent les éléments hors écran ; le compteur visible identifie la page du pager, qui peut précomposer ses voisines.

`social` contrôle une fiche d’ami depuis des données fictives mises en cache après le chargement initial, son historique quotidien/horaire, l’objectif personnel, le raccourci de révisions et l’ouverture de l’onglet Équipe par l’intent du widget. Il vérifie aussi la calculatrice native (sélection, déplacements atomiques, suppression d'une fonction vide, résultat), lie le provider social via AppWidgetManager et configure sa période sur la release optimisée. Aucun jeton, commentaire ou demande réelle ; le journal de progression reste inchangé pendant les consultations. Captures dans `social-0.49-release`.

`narration` recherche une introduction par `½LI²`, vérifie sa narration éditoriale, joue/arrête la vraie voix et copie la sortie exacte de l’essai TTS. La progression reste identique. Ce mode n’a pas besoin de la banque Exam1 pour ses assertions et utilise seulement des données isolées sur émulateur.
