# Contrôle de l’APK release optimisé

Ce petit APK d’instrumentation Java n’utilise que les API Android. Il reste indépendant du Kotlin et des classes Compose renommées par R8 dans la **vraie** release, contrairement à l’APK AndroidTest du debug. Il n’est pas une dépendance de Hamigo et n’est jamais publié avec elle.

Il se signe avec la clé Hamigo locale existante. Il refuse un appareil physique ; le mode `flow` utilise une fixture sans compte et modifie seulement les préférences de l’émulateur. Le mode `storage` lit les tailles du package via StorageStatsManager, sans lire la progression.

Depuis la racine, avec JDK et SDK Android configurés :

```powershell
.\gradlew.bat -p tools/release-probe assembleRelease
adb -s emulator-5554 install -t -r tools/release-probe/build/outputs/apk/release/HamigoReleaseProbe-release.apk
adb -s emulator-5554 shell am instrument -w -e mode flow com.malfreyt.alexandre.hamigo.releaseprobe/.ReleaseProbe
```

Pour une simple mesure, remplacer `flow` par `storage`. Le résultat doit contenir **OK**. Les captures et `storage-probe.json` sont écrits dans le dossier externe de Hamigo sur l’émulateur.

Le scénario contrôle l’accueil sans barre de téléchargement, la banque réellement téléchargée et complète, l’examen et ses images originales/traitées, les fiches natives, la version et la vérification manuelle, ainsi que les constructeurs des workers persistés. Il ne connecte pas GitHub et n’envoie aucun signalement ni commentaire.
