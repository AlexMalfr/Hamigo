# Hamigo 📻

Ton compagnon pour préparer le **certificat d’opérateur des services d’amateur** français. Une app Android native, en français, conçue pour des séances courtes et un usage personnel entre amis.

## Apprendre et réviser

- Parcours stable de **14 chapitres, 56 leçons et 224 exercices originaux**.
- QCM, associations, étapes à ordonner, saisie de calculs, résistances dessinées et cadran VHF interactif.
- **2 961 questions communautaires Exam1 REF**, avec toutes leurs illustrations archivées. Le mélange courant utilise 2 950 questions après 11 exclusions documentées.
- Entraînement tous thèmes ou thèmes sélectionnés, séances de 5/10/20 questions, laboratoire de calculs à valeurs renouvelées.
- Examen blanc : 20 questions de réglementation en 15 minutes, puis 20 de technique en 30 minutes. +1 bonne réponse, 0 autrement, 10/20 requis dans chaque partie.
- **17 fiches mémo et 364 flashcards** : alphabet international, Morse avec écoute, codes Q, résistances, unités, formules, bandes, indicatifs, émissions, RST, propagation et examen.
- Répétition espacée inspirée de SM-2, corrections expliquées, XP, niveaux, objectif quotidien, série de jours et rappels Android.

Les mauvaises réponses reviennent après dix minutes, puis les bonnes réponses s’espacent. Relire prématurément une carte déjà acquise n’allonge pas artificiellement son intervalle. Le bonus de leçon est attribué une seule fois ; l’XP d’une même question ne peut pas être accumulé à répétition le même jour. Une leçon se valide quand toutes les erreurs ont été corrigées.

## Jouer ensemble

Partage une carte de progression via le menu Android, importe le profil JSON d’un ami, compare vos XP de la semaine et envoie un « coup d’antenne » via ton application de messagerie.

La connexion GitHub est facultative : un Gist secret conserve le résumé de progression, actualisé à l’ouverture et après les séances. Les amis s’actualisent grâce à leur lien. Connexion par jeton limité aux Gists, ou Device Flow d’une application OAuth personnelle. Aucun compte ni réseau n’est requis pour apprendre. Voir [la configuration sociale](docs/SOCIAL.md).

Le résumé social ne contient pas ton historique de réponses ni tes credentials. Un Gist secret est non répertorié et accessible avec son URL. Les sauvegardes complètes sont exportées/importées localement depuis les réglages.

## Compiler

Android 8.0 (API 26) minimum ; compile/target SDK 36, JDK 17 ou plus. Gradle 9.2.1 / AGP 9.0.1 / Kotlin intégré 2.2.10 / Compose Material 3.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME="$env:USERPROFILE\.gradle"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Renseigner le chemin du SDK dans `local.properties` (non versionné), ou `ANDROID_HOME`. Android Studio peut ouvrir ce dossier directement.

Pour une version signée personnelle :

```powershell
pwsh -File tools/create_signing.ps1
.\gradlew.bat :app:assembleRelease
```

La clé et ses propriétés sont dans `.tools/`, exclus du dépôt. Les conserver ensemble pour signer les futures mises à jour. Le téléphone reçoit l’APK release ; les tests instrumentés utilisent l’émulateur et l’APK debug.

`versionCode` est le nombre de commits Git, comme PoPS-App. `versionName` vaut `0.<nombre>+<SHA>`, avec `-dev` si la copie de travail est modifiée. Compiler les versions distribuées à partir d’un commit propre et d’un historique complet.

## Tests et sources

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Les tests portent sur le calendrier SRS, les séries de jours et changements d’heure, les seuils d’examen, les réponses numériques, les données importées, la sauvegarde, la protection des jetons, les fichiers partagés et les rappels.

Les captures, résultats de validation et APK livrés sont dans `output/` (non versionné). La banque source complète et son import reproductible restent dans `data/sources/exam1/` et `tools/import_exam1.ps1`.

Attributions et sources pédagogiques : [F6KGL et textes officiels](docs/SOURCES-COURSES.md), [Exam1 REF](docs/SOURCES-EXAM1.md). Les cours de référence F6KGL sont sous CC BY-NC-SA 4.0 ; les données pédagogiques adaptées conservent cette attribution. Le statut de licence propre de la banque Exam1 n’est pas présumé ; l’archive est utilisée dans ce projet privé personnel. Aucun code de l’app Android Exam1RA n’a été repris.

Identifiant : `com.malfreyt.alexandre.hamigo`. Mascotte : **Pico**, poste radio illustré par des formes vectorielles originales. Palette crème, turquoise, corail et jaune ; composants Material 3 et interactions Compose.
