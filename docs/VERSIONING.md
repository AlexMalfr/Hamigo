# Versions, branches et publication Play

Contrat du cycle 1.0, à suivre par tous les agents. L'état réellement exécuté est consigné dans [VALIDATION.md](VALIDATION.md) et dans les preuves locales de livraison. Une configuration CI ne prouve pas une publication.

## Identités et canaux

| Canal | Branche de distribution | Tag déclencheur | versionName | Destination Play |
|---|---|---|---|---|
| release | `main` | `v1.0` | `1.0` | production |
| beta | `beta` | `v1.0-beta2` | `1.0-beta2` | **test ouvert** |
| alpha | `alpha` | `v1.0-alpha5` | `1.0-alpha5` | **test interne**, liste fournie par l'éditeur |
| dev | `v1.0` et autres branches de travail | aucun tag public | `1.0-dev57+abcdef12[-dirty]` | aucune distribution Play |

Les canaux publics partagent `com.malfreyt.alexandre.hamigo`, les données et la signature historique. La dev, choisie par défaut, est une installation indépendante `.dev`, libellée Hamigo Dev, avec ses fichiers, préférences, tâches, widgets et clés Android. Le namespace Kotlin reste public : ne pas déduire les noms de classes de l'applicationId.

Une inscription au test interne exclut les tests ouverts/fermés. Pour passer d'alpha à bêta, le testeur doit quitter l'interne et rejoindre l'ouvert. Play ne remplace pas un code élevé par un code plus faible. [Tests Play](https://support.google.com/googleplay/android-developer/answer/9845334).

## Une branche durable par version, un squash sur main

1. Créer la branche `v<version>` depuis `main`, mettre `version.properties` à jour, travailler et conserver les commits utiles dans cette branche. Les branches de sous-tâches peuvent s'y intégrer. Ne jamais supprimer cette branche après livraison : elle garde la granularité masquée par le squash.
2. Préparer chaque candidat alpha/bêta dans un commit propre. Vérifier tests et vraie release optimisée. Avancer la branche `alpha`/`beta` vers ce commit, pousser la branche de version et la branche de canal, puis créer/pousser un **tag annoté** correspondant. Les branches de canal sont des pointeurs de distribution, pas une deuxième histoire de développement. Si un ancien squash rend le fast-forward impossible au cycle suivant, créer un merge explicite du nouveau candidat conservant exactement son arbre ; ne pas faire de force push.
3. Pour la finale, geler la branche de version, intégrer son arbre par **un squash** sur `main`, vérifier que les arbres sont identiques et pousser `main`. Créer le tag annoté `v<version>` sur ce squash. Conserver la branche de version gelée. Le SHA de livraison est celui du squash, même si les sources ont aussi un historique détaillé ailleurs.
4. Après un upload Play, ne jamais amender/déplacer/supprimer le tag de livraison ni son tag de réservation. Toute correction est une nouvelle version/tag/candidat et un code supérieur. Les anciennes règles de remplacement des 0.x restent seulement historiques.

Branche et tag portent le même nom, par exemple `v1.0`, mais sont des références différentes : `refs/heads/v1.0` garde les commits détaillés et `refs/tags/v1.0` désigne le squash livré. Toujours préciser ces références dans les commandes automatisées (`git switch v1.0` pour travailler, `git switch --detach refs/tags/v1.0` pour construire). Les branches historiques `v0.xx` pointent aux commits de leurs tags d'origine ; elles ne recréent pas des commits intermédiaires absents. Les branches de sauvegarde existantes restent conservées ; leur renommage enlève uniquement `codex/`.

`tools/play/check_source.py` exige le tag conforme à `version.properties`, l'historique complet, un checkout propre, l'appartenance à la branche de canal et à la branche de version. Pour la finale, il compare les arbres du squash et de la branche gelée. Le SHA complet reste dans les métadonnées et les liens vers les sources ; aucun SHA dans le nom public.

La CI de vérification `android.yml` tourne sur les pushes/PR. La CI de livraison `play.yml` tourne **seulement sur les tags `v*`** (ou relance explicite du même tag). Un push de branche ne crée pas un autre AAB. Les anciens tags 0.x n'embarquent pas ce workflow et les tags modernes invalides sont refusés.

Une maintenance de CI peut avancer `main` par un commit dédié après le gel d'une version, sans modifier son tag, sa branche détaillée ni ses binaires. Elle ne crée pas une nouvelle version produit. Vérifier la CI sur une branche de maintenance puis avancer `main` sans réécriture. Ne pas attribuer ces nouveaux outils aux sources du binaire déjà livré.

## Numéro de build automatique

Le `versionCode` public est un simple entier global croissant : **57, 58, 59…**, indépendant de la version produit et du canal. Android autorise cette séparation, jusqu'à 2 100 000 000. Les codes 0.x distribués s'arrêtent à 56. L'encodage `releaseOrdinal × 10 000` de la préparation initiale a été abandonné avant tout upload public. [Versionnement Android](https://developer.android.com/studio/publish/versioning).

`tools/play/reserve_build.py` réserve atomiquement le prochain numéro dans un tag annoté `ci-build/N`, pointant au commit exact et contenant le tag de livraison. La CI est sérialisée entre tous les canaux ; un conflit de push provoque une nouvelle lecture et une nouvelle tentative. Ces tags sont permanents. Une relance du même tag retrouve son numéro ; déplacer le tag est refusé. Une réservation échouée peut laisser un trou, ce qui est normal. Aucun commit artificiel pour incrémenter le compteur.

Tout upload manuel doit **d'abord utiliser cet allocateur**. Si un code a été consommé en dehors du processus, passer `--floor N` au premier rattrapage. Le préflight Play compare aussi tous les codes visibles des tracks et des bundles ; ne pas traiter cette liste comme l'archive exhaustive de tous les codes retirés. La réservation Git est l'archive durable. Un même code ne désigne jamais deux livraisons différentes.

Le `buildNumber=57` de `version.properties` est uniquement une valeur de repli pour la dev. Les builds publics exigent une propriété explicite et le tag de réservation correspondant au commit. Le numéro visible alpha/bêta vient du tag, via `hamigoPrereleaseNumber`. La dev garde un plancher technique 570001, car une précédente dev de préparation avait déjà ce code ; ce package est distinct et ce plancher ne concerne pas Play.

Exemple de cycle futur : trois tags successifs alpha, bêta et finale reçoivent trois codes croissants. Pour la première 1.0, les réservations 57 et 58 ont été abandonnées avant construction d'un AAB et distribution ; le prochain code disponible est 59. Toujours utiliser le résultat de l'allocateur.

## Construire et livrer

```sh
# Dev sans credentials publics
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
bash gradlew :app:assembleRelease # dev optimisée R8

# Après push des branches et création du tag annoté, checkout propre sur le tag
python tools/play/check_source.py --tag v1.0
python tools/play/reserve_build.py --tag v1.0
# Utiliser N retourné, jamais un numéro deviné
bash gradlew :app:bundleRelease :app:writeVersionMetadata -PhamigoChannel=release -PhamigoBuildNumber=N -PhamigoSigningProfile=upload
# APK GitHub/local : clé de distribution historique (profil par défaut)
bash gradlew :app:assembleRelease -PhamigoChannel=release -PhamigoBuildNumber=N
```

Sous Windows, utiliser `gradlew.bat`, JDK17+, SDK36 et `printBuildDirectory` pour les sorties hors OneDrive. Les canaux publics utilisent exclusivement R8 et la réduction des ressources. Une recette dev n'est pas la recette du binaire public, dont les constantes OAuth diffèrent. Conserver l'AAB exact, son mapping, version.json, SHA256, certificat, commit/tag et résultats R8. Ne pas reconstruire un code déjà archivé : récupérer les artefacts existants. Les GitHub Releases en brouillon conservent l'AAB et le mapping au-delà de l'expiration des artefacts Actions (90 jours).

## Signature et secrets

Choix explicitement autorisé le 26 octobre 2026 et enregistré dans Play Console : Google utilise une copie chiffrée PEPK de la **clé historique de distribution**. Empreinte SHA256 `28:67:8A:0E:BE:9A:44:F4:C1:D6:C5:A3:85:94:6B:E4:58:69:24:F6:61:52:14:6D:56:54:32:96:D9:72:39:DA`. Elle maintient les mises à jour APK GitHub ↔ Play. Protéger sa copie locale et sa sauvegarde ; la CI ne la reçoit pas.

Une clé d'import distincte est enregistrée, SHA256 `0F:98:B7:FB:BB:9C:17:E1:80:80:C3:D5:47:D5:80:9F:DB:E2:4B:5E:04:DC:F2:D5:4E:E1:5F:98:F3:D2:8C:A2`. Elle signe l'AAB envoyé, puis Google signe les APK distribués avec la clé historique. Un APK signé de la seule clé d'import **ne met pas à jour Hamigo public** : ne pas le publier comme APK utilisateur. La clé d'import peut être réinitialisée auprès de Google si perdue/compromise. [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756).

Fichiers locaux ignorés : `.tools/hamigo-release.jks` et `signing.properties` pour la distribution ; `.tools/hamigo-upload.jks` et `upload-signing.properties` (`storeFile=hamigo-upload.jks`, `keyAlias=hamigo-upload`) pour l'import ; `.tools/oauth.properties` pour l'OAuth public. Jamais de valeur secrète dans le dépôt, les arguments de commande, un ticket ou un message. `prepare_ci.py` matérialise uniquement la clé d'import et OAuth à partir des secrets d'environnement, puis la CI supprime ces fichiers.

Les App Links et les fournisseurs API doivent enregistrer le **certificat distribué**, pas seulement celui d'import. Le site public possède déjà l'empreinte historique. La dev lit uniquement `.tools/oauth.dev.properties`, facultatif, avec client/callback dédiés ; elle ne reprend pas les credentials publics. Public : `/projects/Hamigo/`, `hamigo` ; dev : `/projects/Hamigo-dev/`, `hamigo-dev`. Les anciennes invitations GitHub Pages restent acceptées dans leur identité correspondante. OAuth valide le callback canonique strictement. Site/callback/assetlinks dev et retour Tally distinct restent à configurer avant un usage personnel dev complet ; le formulaire public revient actuellement à `hamigo://return`.

## Accès CI à Google Play

Le workflow utilise des environnements `play-alpha`, `play-beta`, `play-production`, des secrets de signature d'import/OAuth, et la fédération d'identité GitHub OIDC via un compte de service Google, sans clé JSON de compte de service. Restreindre la fédération à l'ID immuable du dépôt `1403424361`, au propriétaire `54336210`, au workflow `play.yml` et aux tags de livraison. Donner au compte de service uniquement les droits Play nécessaires sur **cette app** ; ne pas lui donner administrateur du compte ou propriétaire du projet Cloud. [API Play](https://developers.google.com/android-publisher/getting_started), [action Google officielle](https://github.com/google-github-actions/auth).

Secrets des trois environnements : `HAMIGO_KEYSTORE_BASE64`, `HAMIGO_STORE_PASSWORD`, `HAMIGO_KEY_PASSWORD`, `HAMIGO_OAUTH_CLIENT_SECRET`. Variables publiques : `HAMIGO_UPLOAD_CERT_SHA256`, `PLAY_WIF_PROVIDER`, `PLAY_SERVICE_ACCOUNT`, `PLAY_TRACK` (ID exact retourné par l'API, pas déduit du nom de branche).

La première réception AAB reste coordonnée avec l'agent de publication : import manuel dans Console, vérification de la signature distribuée, de la version, des tracks et de l'accès API. `PLAY_FIRST_RELEASE_COMPLETED` et `PLAY_UPLOAD_ENABLED` restent faux jusqu'à cette vérification. Le workflow construit et archive avant ce bootstrap, puis vérifie réellement l'échange OIDC en demandant un jeton court au seul scope Android Publisher ; cette authentification seule ne prouve pas l'accès à l'app ni un upload. L'API Android Publisher est activée dans le projet dédié `hamigo-play-ci`.

Pour activer les envois après bootstrap : lire `python tools/play/publish.py --list-tracks`, vérifier les IDs, définir les variables de track et les deux portes à `true`. Les envois actuels créent un **brouillon**, sans soumettre de revue. Le script refuse un track avec un brouillon/publication inachevée, une collision de code et une revue concurrente ; il supprime son edit temporaire si aucun commit ne réussit. Ne jamais supprimer une release Console existante pour débloquer la CI sans comprendre son état.

Le workflow manuel **Play API preflight**, exécuté uniquement depuis `main` protégé, récupère l'AAB existante d'un tag choisi, vérifie source gelée, réservation, métadonnées, hashes et certificat, puis lit les IDs avec Publisher API. Il n'utilise aucun secret de build, ne reconstruit rien, n'upload rien et ne soumet aucune revue. La lecture utilise un edit temporaire supprimé sans commit. Sa fédération doit autoriser exactement `play-preflight.yml@refs/heads/main`, en plus du workflow de livraison sur tags. Les trois variables publiques WIF/compte/certificat sont aussi définies au niveau dépôt pour ce contrôle sans environnement secret. Une relance du workflow de construction refuse les binaires déjà archivés ; employer ce préflight pour contrôler leur liaison Play.

Le jeton GitHub de ce contrôle garde seulement `contents: read` et `actions: read`. Les assets d'une release publiée sont privilégiés ; une release encore en brouillon n'est pas visible avec ce jeton. Dans ce cas, récupérer l'unique artefact CI `release-<tag>` non expiré du commit exact, par son ID. Refuser une archive absente ou ambiguë, puis appliquer les mêmes contrôles de source, hashes et certificat. Les artefacts CI restent 90 jours : publier la release GitHub avant leur expiration pour conserver ce contrôle, sans reconstruire ni réutiliser son numéro.

Une publication entièrement automatique peut ensuite utiliser `--status completed --send-for-review`, avec une décision explicite de l'éditeur sur les destinations et les protections des environnements. Elle n'est pas activée pour la première 1.0. Le managed publishing ne retient pas les sorties internes.

## Échec avant toute distribution

Un tag final ne se corrige avant distribution qu'après preuve qu'aucun AAB n'a été importé ni archivé dans une release. Conserver l'ancien commit et tag annoté sous `preflight/`, conserver sa réservation `ci-build/N`, puis ajouter un tag annoté `ci-retired/N` sur le même commit, expliquant l'échec et sa preuve. L'allocateur ignore cette réservation pour les relances mais continue de compter son numéro comme consommé. Le tag corrigé reçoit un code supérieur. Ne jamais employer cette procédure après un upload ou une livraison ; ne jamais supprimer/réutiliser le numéro. La première réservation 57 a été abandonnée après l'échec du contrôle de tag dans CI40275087338, avant construction/envoi ; le défaut de checkout est corrigé dans le validateur.

## Promotion et revue

Promouvoir le même AAB conserve les octets testés, le code et le suffixe embarqué. Changer le nom privé de la release Console ne change pas `versionName`. Pour avoir `1.0-betaN` puis `1.0`, reconstruire avec un nouveau code, tester le **bundle final** en interne puis réutiliser ce même bundle en production, sans nouvelle reconstruction. C'est le compromis retenu pour garder les noms de canal demandés. [Préparer une release](https://support.google.com/googleplay/android-developer/answer/9859348).

Une promotion de bêta approuvée ne garantit pas une production sans revue. Les mises à jour internes passent généralement sans revue préalable (contrôles rétroactifs possibles), avec exceptions pour la première sortie d'une app entièrement configurée et après rejet. Les tests ouverts et la production peuvent demander une revue ; la Console fait foi. Ni la CI ni un choix de version ne peuvent garantir de l'éviter. [Revues Play](https://support.google.com/googleplay/android-developer/answer/9859654).

## Données conservées

Le retrait demandé de `StreakMigration52` supprime l'ancien rétrocalcul et ses tests temporaires. Les historiques `streakHistory` déjà enregistrés sont conservés, validés et fusionnés sans réécriture. Une absence d'historique initialise une structure vide, sans inventer une série à partir des anciens XP. `StudyDayMigration52`, distincte, n'est pas supprimée par cette demande. Préserver les UUID des questions, le schéma cloud courant et la démo examinateur ; ne pas réinitialiser les installations personnelles pour tester.
