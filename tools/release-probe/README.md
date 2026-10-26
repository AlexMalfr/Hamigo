# Contrôle de l’APK release optimisé

Ce petit APK d’instrumentation Java n’utilise que les API Android. Il reste indépendant du Kotlin et des classes Compose renommées par R8 dans la **vraie** release, contrairement à l’APK AndroidTest du debug. Il n’est pas une dépendance de Hamigo et n’est jamais publié avec elle.

`speech` contrôle la lecture réelle d’une introduction et d’une question avec le moteur installé, les commandes Lire/Arrêter et la suppression des WAV temporaires, sur l’APK signé et optimisé. Il vérifie que la narration ne modifie ni XP ni SRS. Aucun compte n’est connecté. Pour un probe fonctionnel sur un émulateur lent, les échelles d’animation peuvent être désactivées temporairement puis restaurées ; la revue visuelle du mouvement se fait séparément avec animations actives.

Il se signe avec la clé Hamigo locale existante pour la cible publique, ou la même clé debug que la cible dev. Il refuse un appareil physique ; les modes `flow`, `diagnostics`, `catalog`, `social` et `learning` utilisent des fixtures sans compte sur l’émulateur. Le mode `storage` lit les tailles du package via StorageStatsManager, sans lire la progression.

Depuis la racine, avec JDK et SDK Android configurés :

```powershell
.\gradlew.bat -p tools/release-probe assembleRelease
adb -s emulator-5554 install -t -r tools/release-probe/build/outputs/apk/release/HamigoReleaseProbe-release.apk
adb -s emulator-5554 shell am instrument -w -e mode flow com.malfreyt.alexandre.hamigo.releaseprobe/.ReleaseProbe
```

Pour une simple mesure, remplacer `flow` par `storage`. Le résultat doit contenir **OK**. Les captures et `storage-probe.json` sont écrits dans le dossier externe de Hamigo sur l’émulateur.

Le scénario contrôle l’accueil sans barre de téléchargement, la banque réellement téléchargée et complète, l’examen et ses images originales/traitées, les fiches natives, la version et la vérification manuelle, ainsi que les constructeurs des workers persistés. Il ne connecte pas GitHub et n’envoie aucun signalement ni commentaire.

`diagnostics` contrôle la réception Fréquence continue, sa priorité face au TTS et son mute. `catalog` contrôle le lecteur d'illustration, ses fermetures, le balayage horizontal et les flèches entre les résultats filtrés, sans modifier la progression. Pour rejouer un scénario, arrêter d'abord le processus Hamigo sur l'émulateur (`adb -s emulator-5554 shell am force-stop com.malfreyt.alexandre.hamigo`), sans effacer ses données. Les sélecteurs excluent les éléments hors écran ; le compteur visible identifie la page du pager, qui peut précomposer ses voisines.

Cycle 1.0 : pour la release optimisée dev indépendante, compiler ce helper avec `'-PhamigoTargetPackage=com.malfreyt.alexandre.hamigo.dev'` (garder les guillemets sous PowerShell). Le helper dev a lui-même le package `com.malfreyt.alexandre.hamigo.releaseprobe.dev`, pour coexister avec le helper public déjà installé et sa signature distincte. Lancer son instrumentation par le composant complet `com.malfreyt.alexandre.hamigo.releaseprobe.dev/com.malfreyt.alexandre.hamigo.releaseprobe.ReleaseProbe`. Le package public reste la cible par défaut. La classe MainActivity conserve son namespace original dans les deux installations. Cibler explicitement l’émulateur et adapter les commandes `force-stop` au package choisi ; ne pas lancer ces fixtures sur le téléphone personnel.

Le mode `identity`, réservé à la cible dev optimisée, vérifie son libellé, sa version, son FileProvider et le vrai composant du launcher. Avant de remplacer la dev debug par la dev R8, écrire dans son dossier privé `files/versioning-upgrade-sentinel.txt` le texte exact `hamigo-dev-upgrade-fixture-v1` (sans fin de ligne). Le mode confirme que l’installation `-r` a conservé ce fichier, sans lire de progression ; résultat `identity-probe.json`. Ne pas désinstaller ni effacer les données entre ces deux installations.

`social` contrôle une fiche d’ami depuis des données fictives mises en cache après le chargement initial, son historique quotidien/horaire, l’objectif personnel, le raccourci de révisions et l’ouverture de l’onglet Équipe par l’intent du widget. Il vérifie aussi la calculatrice native (sélection, déplacements atomiques, suppression d'une fonction vide, résultat), lie le provider social via AppWidgetManager et configure sa période sur la release optimisée. Aucun jeton, commentaire ou demande réelle ; le journal de progression reste inchangé pendant les consultations. Captures dans `social-0.49-release`.

`narration` recherche une introduction par `½LI²`, vérifie sa narration éditoriale, joue/arrête la vraie voix et copie la sortie exacte de l’essai TTS. La progression reste identique. Ce mode n’a pas besoin de la banque Exam1 pour ses assertions et utilise seulement des données isolées sur émulateur.

`learning` contrôle les nouveaux apprentissages dans la vraie release optimisée. Après installation de cet APK de probe et de la release signée à vérifier, arrêter Hamigo sur l’émulateur, puis exécuter :

```powershell
adb -s emulator-5554 shell am force-stop com.malfreyt.alexandre.hamigo
adb -s emulator-5554 shell am instrument -w -e mode learning com.malfreyt.alexandre.hamigo.releaseprobe/.ReleaseProbe
```

Ajouter `-e hamigoRotation 0` pour imposer le portrait après ouverture de Hamigo (`1` pour le paysage). Le lanceur peut rester en portrait ; le harnais attend la fenêtre de l’app, suit l’activité recréée, puis contrôle les dimensions réelles de chaque capture. Une rotation seulement demandée dans le shell ou avant l’ouverture ne suffit pas à valider un format. Restaurer les réglages de l’AVD après les contrôles.

Le scénario utilise exclusivement le framework Android, les assets installés et l’interface accessible. Il ne référence aucune classe interne de Hamigo ou de Compose susceptible d’être renommée par R8. Il contrôle :

- les assets attendus (173 cours, 1 233 questions actives et 139 compétences), le nombre de cours réellement chargé dans Diagnostics et le catalogue de questions incluant les archives, distinct des exemples fictifs ;
- le bouton « Commencer le cours » du nouveau Parcours, l’introduction de bienvenue et ses sept questions réellement jouées malgré leur ordre aléatoire : association, texte à trou, choix multiples et choix unique. Les UUID enregistrés, les sept réponses justes, les 27 XP, la validation et des données de compétences v2 doivent apparaître dans la progression de la fixture ;
- une introduction sans outils flottants, puis le résultat `2+3=5` et la saisie du brouillon dans Mémo, sans progression supplémentaire ;
- l’historique d’examens vide, le bilan d’apprentissage, les deux barres de progression de Moi avec leurs valeurs accessibles, puis l’astuce finale. Les panneaux sont recherchés successivement : une fenêtre courte n’a pas à afficher le bilan et les barres dans un seul viewport ; leur ordre est vérifié séparément dans les tests Compose ;
- les valeurs de la compétence réellement acquise dans Diagnostics, puis le JSON source/résolu et une vraie réponse vocale saisie par le bouton clavier : l’IME doit être visible et le verdict doit être « Signal reçu ! », sans changer la progression de la fixture. Le microphone de Diagnostics utilise aussi le vrai service Android ; ce probe vérifie seulement le repli clavier et ne mesure pas une transcription acoustique.

Si un cours des assets porte `studyToolsHint=true`, son introduction est aussi ouverte dans Diagnostics : ni les outils ni leur ancienne astuce ne doivent y apparaître. `introductionHintRemoved` consigne ce contrôle dans le rapport. Les captures attendent les frames de la fenêtre au premier plan, y compris les dialogues, sur API 29 et plus.

La progression de ce scénario appartient à une fixture temporaire : les quatre fichiers de préférences Hamigo, social, sécurisé et Diagnostics sont sauvegardés uniquement en mémoire, remplacés par des données sans compte, puis restaurés en `finally` après fermeture de l’activité, même en cas d’assertion échouée. La synchronisation, les rappels et les sons sont désactivés pendant l’essai. L’option d’accessibilité utilisée pour détecter le clavier est également restaurée. Une interruption brutale du processus empêche le `finally` ; réserver ce mode à l’émulateur de test, sans compte personnel. Le probe ne supprime ni ne remplace le téléchargement Exam1 et ses assertions n’en dépendent pas.

Un succès produit `OK: learning` et `learning-release/learning-probe.json` dans le dossier externe de Hamigo, avec les captures `learning-*.png`. Une assertion interrompue produit un échec, pas un rapport de succès. Ce probe complète les tests de logique et l’inspection visuelle : il ne valide pas chaque question ou compétence individuellement, le moteur de reconnaissance vocale, un examen complet ni l’historique non vide. Son ajout au code ne constitue pas une exécution réussie ; consigner séparément la version effectivement testée et le résultat dans `docs/VALIDATION.md`.

Le mode `welcome` isole le premier cours : mêmes sept réponses natives, UUID,
27 XP et bilan, puis restauration de la fixture sans parcourir Mémo et Diagnostics.
Il produit `welcome-release/welcome-probe.json` et convient notamment au contrôle
du cours en petit paysage avec police agrandie. Le résultat précise son périmètre.

Les captures du scénario learning attendent le focus puis deux frames de la fenêtre au premier plan, avec attente bornée et contrôle de stabilité. Une fenêtre en transition produit un échec sur le thread d’instrumentation si elle ne devient pas prête ; une assertion de capture ne doit jamais tuer le thread principal de l’app ni empêcher la restauration de la fixture. Le rapport vérifie aussi les 173 présentations d’introduction distribuées.

Le mode learning prépare RECORD_AUDIO sur l’émulateur : la question vocale demande normalement cette autorisation à son ouverture et sa fenêtre système ne fait pas partie des fenêtres de Hamigo. Le probe reste au clavier et ne lance jamais la reconnaissance. L’autorisation peut être révoquée après la suite, lorsque l’instrumentation est terminée.

Les sélecteurs repartent d’un arbre d’accessibilité frais sur les versions Android récentes et rafraîchissent les parents avant d’utiliser leur action : le changement de question peut réutiliser des informations virtuelles mises en cache. En cas d’échec de clic, la hiérarchie et les états des actions sont enregistrés dans la fixture pour distinguer une sélection périmée d’un bouton réellement désactivé.

Le mode `feedback` vérifie le dialogue de diagnostic sur la release R8 : case cochée par défaut, annulation, réinitialisation au prochain retour, URL strictement sans paramètres après opt-out et contexte conservé si coché. Les intents de navigateur sont interceptés, aucun formulaire ni e-mail réel n’est envoyé. Les préférences sont isolées et restaurées ; rapport `feedback-release/feedback-probe.json`.

Le mode `icons` rend les ressources réellement empaquetées via AdaptiveIconDrawable : fond couleur transparent, couche monochrome présente, masque Android et aperçus couleur/thèmes clair et sombre en 384 et 48 px. Il ne change aucun réglage du launcher ; rapport et PNG dans `icons-release/`.

Le mode `review` ouvre l’accès de démonstration par les paramètres et son code public. Il vérifie les licences dans le vrai APK, la création des Gists fictifs par la synchro native, l’acceptation d’une demande, la persistance après fermeture/réouverture, la synchro manuelle, la suppression des Gists de démo et le retour au profil personnel inchangé. Les quatorze magasins de préférences concernés sont isolés puis restaurés en finally. Aucun compte GitHub réel n’est utilisé. Rapport et captures dans `review-release/`. Comme les autres modes avec fixture, il est réservé à l’émulateur ; une exécution réussie doit être consignée séparément de sa compilation.
