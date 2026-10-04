# Hamigo 📻

Ton compagnon pour préparer le **certificat d’opérateur des services d’amateur** français. Une app Android native, en français, conçue pour des séances courtes et un usage personnel entre amis.

## Apprendre et réviser

- Parcours de **21 chapitres, 94 leçons et 826 exercices originaux**, avec un chapitre Morse de 14 leçons couvrant les lettres, les chiffres, la ponctuation, l'écoute et la transmission de groupes.
- **15 formats d'exercice et de révision** : QCM, vrai/faux, phrases à compléter, sélection multiple, associations, étapes à ordonner, calculs, résistances dessinées, cadran VHF, estimation au curseur, interrupteurs binaires, formes d'onde, écoute Morse, composition Morse et flashcards. Treize de ces formats sont présents dans les cours.
- **2 961 questions communautaires Exam1 REF**, avec toutes leurs illustrations archivées. Le mélange courant utilise 2 950 questions après 11 exclusions documentées.
- **5 188 variantes procédurales** aux identifiants stables pour les retrouver en répétition espacée. Les séances de cours mélangent les questions et ajoutent, lorsque la notion le permet, des variantes des concepts enseignés.
- Page Défis organisée en examen blanc, laboratoire de 12 exercices et mix sur mesure. Le mix propose 10/20/40/80/150 questions et un nombre libre de **1 à 1 000**, dans la limite des questions disponibles pour la sélection. Les sous-thèmes sont regroupés sous Réglementation et Technique dans une section repliable ; les variantes Hamigo peuvent être incluses.
- Examen blanc : 20 questions de réglementation en 15 minutes, puis 20 de technique en 30 minutes. Chaque partie commence par une introduction, permet de revenir sur les réponses et propose une grille de relecture ; le chrono continue jusqu'à sa finalisation. +1 bonne réponse, 0 autrement, 10/20 requis dans chaque partie. Le résultat final inclut un récapitulatif des réponses et corrections.
- **18 fiches mémo et 390 flashcards**, avec un bouton de révision dans un ordre aléatoire. La recherche s'ouvre à la demande ; le retour d'une fiche conserve la position dans la liste. Les anneaux de résistances montrent leurs couleurs, leur sens de lecture et un exemple de tolérance ; les signaux Morse sont dessinés avec de grands points et traits alignés, y compris dans les cours et les corrections.
- Outils interactifs dans les fiches adaptées : lecture et composition des résistances à quatre ou cinq anneaux, associations série/parallèle, traducteur texte ↔ Morse avec son, rapports ↔ décibels, loi d'Ohm et fréquence ↔ longueur d'onde.
- Carte officielle des trois régions UIT disponible hors ligne et agrandissable ; fiche des indicatifs français, dont FY pour la Guyane, et repères internationaux avec raccourcis Maps. Écoute de l'alphabet international avec la voix anglaise Android, et des codes Morse/Q avec une synthèse locale.
- Calculatrice scientifique flottante pendant les questions : opérations, puissances, racines, logarithmes, trigonométrie DEG/RAD, notation scientifique et mémoire Ans. Une réponse numérique peut reprendre son résultat. Les curseurs affichent une précision adaptée à l'exercice, sans décimales parasites.
- Répétition espacée inspirée de SM-2, corrections expliquées, XP, niveaux, objectif quotidien, série de jours et rappels Android. Pico accompagne les questions avec **8 expressions et 6 poses** ; les notifications illustrées changent selon le jour et la progression de la série ou de l'objectif.

Les mauvaises réponses reviennent après dix minutes, puis les bonnes réponses s'espacent. Relire prématurément une carte déjà acquise n'allonge pas artificiellement son intervalle. Une question rapporte 3 XP si elle est réussie, 1 XP en cas d'erreur, au plus une attribution par question et par jour ; une première validation de leçon ajoute 6 XP. Une leçon exige **au moins 80 % de bonnes réponses au premier essai et aucune erreur restante** : corriger ensuite une séance entièrement ratée ne suffit pas à valider la leçon.

Moi permet de parcourir l'historique des XP par tranches de sept jours avec un glissement horizontal ou les boutons. Le retour prédictif anime l'écran et laisse la navigation inchangée si le geste est annulé. Les illustrations Exam1 utilisent un aperçu dérivé plus lisible ; toucher l'image ouvre l'original en plein écran avec zoom et déplacement.

## Jouer ensemble

Compare vos XP de la semaine et envoie un « coup d'antenne » via ton application de messagerie. **Partager mon bilan** depuis Moi produit une image personnelle avec un graphique d'activité ; **Partager le classement** depuis Équipe montre les équipiers et leurs XP hebdomadaires. **Partager mes résultats** à la fin d'un examen ou d'un mix produit une image de la séance avec ses scores, sa durée et son graphique. Les leçons et flashcards proposent **Partager mon parcours**.

La connexion GitHub est facultative et proposée dès l'onboarding, puis mise en avant dans Équipe. L'application OAuth **Hamigo commune** utilise Device Flow : aucun jeton à coller, aucun Client ID personnel et aucun secret OAuth embarqué. GitHub peut s'ouvrir dans une fenêtre WebView interne, avec remplissage du code lorsque la page le permet et un bouton vers le navigateur externe. La fenêtre se ferme dès la réception du jeton ; la vérification du compte et la première sauvegarde continuent séparément. Les interruptions réseau temporaires du polling sont réessayées jusqu'à l'expiration du code. Aucun compte ni réseau n'est requis pour apprendre. Voir [la synchronisation et les invitations](docs/SOCIAL.md).

Deux Gists distincts conservent la **sauvegarde complète** de l'apprentissage et le **résumé social**. La sauvegarde comprend les leçons, l'XP, les tentatives, les échéances de révision et les préférences ; le résumé ne contient que les statistiques partageables et leurs points journaliers. Les deux Gists sont secrets au sens GitHub : **non répertoriés, mais lisibles par toute personne possédant leur URL**. La sauvegarde complète n'est pas chiffrée ; les jetons, les clés Keystore et la liste locale des amis n'y figurent jamais.

La lecture, la fusion et la publication se font à la connexion, au retour dans l'app, en fin de séance et sur actualisation manuelle. Les réponses déclenchent aussi un travail persistant regroupé **8 secondes après la dernière réponse**. En arrière-plan, WorkManager demande une actualisation **toutes les heures** avec réseau disponible et batterie suffisante ; Android peut retarder son exécution. La fusion conserve les événements de plusieurs appareils et évite de réattribuer l'XP lors d'une nouvelle lecture.

Les invitations utilisent un **lien HTTPS et son QR code**, partageables dans Discord et les autres messageries. Android App Links ouvre Hamigo ; une page statique GitHub Pages propose également un bouton d'ouverture de l'app. L'invitation ne contient que l'identifiant du Gist social. Le partage et l'import de profils JSON sont supprimés ; une sauvegarde JSON complète reste disponible parmi les options avancées des réglages.

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

Les tests portent sur le calendrier SRS, les séries de jours et changements d'heure, les seuils d'examen et de maîtrise, les réponses numériques, les variantes procédurales, les outils de calcul, la fusion de sauvegardes, les liens et QR codes, la protection des jetons, les images partagées et les rappels contextuels.

Le transport GitHub repose sur OkHttp, avec appels annulables, délais et réponses bornés, refus des redirections et distinction des erreurs réseau/API. Les tests synthétiques de polling et de transport sont distincts d'une autorisation personnelle complète : leur présence ne prouve ni la cause exacte de l'incident OAuth ni la validation de bout en bout d'un compte utilisateur. Les résultats d'exécution sont consignés dans [VALIDATION.md](docs/VALIDATION.md).

Les captures, résultats de validation et APK livrés sont dans `output/` (non versionné). La banque source complète et son import reproductible restent dans `data/sources/exam1/` et `tools/import_exam1.ps1`.

La comparaison ciblée avec le plan F6KGL révèle encore des notions à approfondir ; voir [l'audit de contenu](docs/CONTENT-AUDIT-2026-10.md) et [les correctifs et limites d'octobre 2026](docs/FIXES-2026-10.md). Le parcours n'est pas présenté comme une couverture exhaustive validée par un formateur.

Attributions et sources pédagogiques : [F6KGL et textes officiels](docs/SOURCES-COURSES.md), [Exam1 REF](docs/SOURCES-EXAM1.md). Les cours de référence F6KGL sont sous CC BY-NC-SA 4.0 ; les données pédagogiques adaptées conservent cette attribution. Le statut de licence propre de la banque Exam1 n’est pas présumé ; l’archive est utilisée dans ce projet privé personnel. Aucun code de l’app Android Exam1RA n’a été repris.

Identifiant : `com.malfreyt.alexandre.hamigo`. Mascotte : **Pico**, poste radio illustré par des formes vectorielles originales. Palette crème, turquoise, corail et jaune ; composants Material 3 et interactions Compose.
