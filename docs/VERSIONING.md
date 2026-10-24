# Versions produit et canaux Play

Spécification et implémentation préparées le 24 octobre 2026, reprises le 25 sur la RC finale 0.56 `a8fe0b40` ; le nettoyage reste distinct. **Aucun compte, secret, track ou déploiement Play n’est créé par cette modification.** La 1.0 finale doit réunir ces trois chantiers avant son squash sur `main`.

## Contrat retenu

| Canal | Branche de distribution | versionName, exemple | Identifiant Android | Destination |
|---|---|---|---|---|
| release | `main` | `1.0` | `com.malfreyt.alexandre.hamigo` | Production |
| beta | `beta` | `1.0-beta2` | identique à release | **Test ouvert**, choix explicite de l’éditeur |
| alpha | `alpha` | `1.0-alpha5` | identique à release | Test interne, liste fournie par l’éditeur |
| dev | branches de travail, par défaut localement | `1.0-dev8+a1b2c3d4` | `com.malfreyt.alexandre.hamigo.dev` | Développement local uniquement |

Les trois canaux publics partagent applicationId et continuité de signature : ils se mettent à jour mutuellement si le code augmente. Ils ne s’installent pas côte à côte. La variante dev a ses propres fichiers, préférences, Keystore Android, tâches WorkManager, widgets et FileProvider ; elle peut coexister avec Hamigo. `Hamigo Dev` est son libellé. Le suffixe visible `-dirty` indique des sources dev modifiées.

L’éligibilité Play compte aussi : une inscription au test interne exclut les tests ouverts/fermés. Un testeur souhaitant passer d’alpha interne à bêta ouverte doit quitter l’interne puis rejoindre l’ouvert. Quitter un track ne force pas l’installation d’un code inférieur ; conserver un code croissant dans les destinations prévues. [Fonctionnement des tests Play](https://support.google.com/googleplay/android-developer/answer/9845334).

Le SHA disparaît du **nom de version public**. Le commit complet reste dans les métadonnées techniques et les liens « Sources de la version », indispensables pour retrouver le code distribué et ses licences. Ces liens n’inventent plus de tag `v0.<versionCode>`.

## Un squash par version sur main

Cette organisation convient. `main` est l’historique des versions produit ; les commits intermédiaires vivent dans les branches de travail. Un push sur `main` peut déclencher la CI sans tag obligatoire. Protéger cette branche : checks obligatoires, fusion squash, pas de force push après un upload Play. Un tag annoté `v1.0`, puis `v1.1`, reste conseillé pour archiver une livraison et ses notes ; il ne déclenche pas un deuxième upload.

Le nombre de commits de `main` seul ne permet pas plusieurs alpha/bêta avant le squash. L’encodage retenu est :

```text
versionCode = releaseOrdinal × 10 000 + buildNumber
```

`releaseOrdinal` est le rang prévu du squash dans l’historique **first-parent** de `main`. Le dépôt actuel a 56 commits first-parent ; si `main` part de la RC 0.56, la 1.0 aura le rang 57. La création de `main` attend la RC corrigée, pour conserver ce parent. Le nom produit se choisit dans `version.properties` et ne se déduit jamais de ce rang.

`buildNumber` est une réservation **commune à tous les canaux publics**, de 1 à 9 999 par rang. `prereleaseNumber` est le numéro visible alpha/bêta. Exemple volontairement distinct :

| Ordre de distribution | buildNumber | versionName | versionCode |
|---|---:|---|---:|
| cinquième alpha | 5 | `1.0-alpha5` | 570005 |
| deuxième bêta | 8 | `1.0-beta2` | 570008 |
| finale reconstruite | 9 | `1.0` | 570009 |
| première alpha suivante, rang 58 | 1 | `1.1-alpha1` | 580001 |

Ne pas utiliser des plages par canal : elles empêcheraient de revenir à une alpha technique après une bêta. Ici, une alpha reconstruite après la bêta doit simplement réserver un numéro plus grand. Le nom du canal n’impose pas l’ordre technique.

Avant **chaque nouvel AAB à envoyer**, incrémenter `buildNumber` au-dessus de toutes les réservations déjà envoyées, dans les branches concernées. Même règle si seuls le nom, la signature ou la configuration changent. Les réservations inutilisées peuvent être sautées. Le script compare le candidat aux codes des tracks et bundles renvoyés par Play ; l’API demeure l’autorité pour les codes historiques déjà consommés. Les envois sont sérialisés entre les trois canaux. Un rejet de collision demande une nouvelle réservation ; le script ne devine pas un nouveau numéro et ne reconstruit pas silencieusement.

Une relance locale du même commit/configuration calcule le même code ; elle ne crée pas une nouvelle identité. **Un code déjà envoyé ne se réattribue jamais à un autre bundle.** Une relance de job après succès ne renvoie pas le même code : réutiliser l’AAB archivé et le promouvoir dans Console, ou réserver un nouveau code pour une nouvelle construction. Un résultat d’upload incertain doit d’abord être vérifié dans Console, avant tout nouvel essai.

Les codes 55/56 installés actuellement sont inférieurs à la première plage 570001 : la hausse préserve les mises à jour et les données, si la signature est conservée. Le maximum du modèle est 2 099 999 999, sous la limite Play de 2 100 000 000. La validation refuse tout débordement. Voir [versioning Android](https://developer.android.com/studio/publish/versioning).

## Bêta, finale et revue Google

La promotion d’un AAB bêta n’est pas obligatoire. Reconstruire le même code source avec `versionName=1.0` et un **nouveau** `versionCode` est possible. Modifier le nom privé de release dans Console ne change pas le nom embarqué dans Android.

Une promotion conserve les octets testés, le code **et le suffixe beta**. Pour le choix de noms de Hamigo, la recommandation est de reconstruire la finale, vérifier sa signature et son comportement R8, tester **ce bundle final** en interne puis ajouter ce même bundle depuis la bibliothèque à la production. Ne pas reconstruire à nouveau après cette recette. Le modèle CI prépare un brouillon production ; Console peut affecter l’AAB de sa bibliothèque à l’interne avant de terminer la production. [Préparer une release](https://support.google.com/googleplay/android-developer/answer/9859348).

Prévoir une revue pour les versions ouvertes/fermées et la production. Promouvoir une bêta approuvée ne garantit pas l’absence de revue en production. Les mises à jour internes passent généralement sans revue préalable, avec possibilité de contrôle ultérieur. Exceptions : première sortie d’une app entièrement configurée et soumission après un refus. Le test interne d’une app pas encore entièrement configurée bénéficie d’une exception à cette première revue. Les changements de fiche, audience, contenu ou distribution peuvent aussi nécessiter une revue ; ajouter une soumission pendant une revue peut en retarder la fin. La page Publishing overview de Console fait foi. [Contrôle des revues](https://support.google.com/googleplay/android-developer/answer/9859654?hl=en-GB).

Ne pas confondre revue des versions et accès à la production imposé à certains nouveaux comptes personnels depuis le 13 novembre 2023. Le compte de l’éditeur est annoncé comme ancien ; vérifier ses exigences réelles dans Console, sans lui attribuer automatiquement le parcours des nouveaux comptes. [Exigences de tests](https://support.google.com/googleplay/android-developer/answer/14151465).

## Construire

```sh
# Dev par défaut, application indépendante. Aucun credential public repris.
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
bash gradlew :app:assembleRelease  # dev optimisée R8, même identifiant .dev

# Depuis un commit propre, après intégration du nettoyage et des corrections Play :
bash gradlew :app:printAppVersion -PhamigoChannel=beta
bash gradlew :app:bundleRelease :app:assembleRelease :app:writeVersionMetadata -PhamigoChannel=beta
# Même commande avec alpha ou release pour les autres canaux.
```

Sous Windows, employer `gradlew.bat` avec JAVA_HOME/SDK locaux. `printBuildDirectory` donne le répertoire effectif ; les chemins CI Linux restent `app/build/`. Les canaux publics utilisent exclusivement le build type `release` optimisé. Le build type debug est désactivé lorsqu’un canal public est explicitement choisi. `-PhamigoBuildNumber=N` permet une réservation explicite, mais la CI utilise normalement le fichier versionné. L’ancienne option `hamigoVersionCommit` est refusée ; les anciennes 0.x se reconstruisent avec leurs sources historiques.

Les builds publics exigent un vrai commit propre et un historique complet. Le garde-fou de retrait de `StreakMigration52` est conservé pour la livraison 1.0 ; le retrait appartient au chantier nettoyage. Aucun code de migration ni schéma de données n’est supprimé ici.

## Identité dev et intégrations

Il s’agit d’une **identité de variante Android**, choisie par une propriété de build, pas d’un feature flag dans l’app : changer applicationId crée une autre installation et un autre espace de données. Le namespace des classes Kotlin reste inchangé ; le fallback des rappels utilise désormais la classe MainActivity réelle, plutôt que de déduire son nom du package d’installation.

Le manifeste, le parsing et la génération des liens sont accordés :

- public : `https://alexandre.malfre.yt/projects/Hamigo/rejoindre.html`, callback sous `/projects/Hamigo/oauth/`, schéma `hamigo` ;
- dev : même domaine sous `/projects/Hamigo-dev/rejoindre.html`, callback sous `/projects/Hamigo-dev/oauth/`, schéma `hamigo-dev`.

Les deux préfixes HTTPS sont disjoints : chaque app refuse les invitations et callbacks de l’autre. Les anciennes invitations GitHub Pages restent compatibles pour l’identité correspondante (`/hamigo/` public, `/hamigo-dev/` dev) ; OAuth valide uniquement son callback canonique actuel. Le manifeste dev ne déclare aucun chemin public. Les intents de widgets/rappels et les PendingIntent ciblent des composants explicites, dans leur package ; les noms de travaux et fichiers identiques sont isolés par Android. L’autorité FileProvider et les alias Keystore dérivent déjà de `context.packageName`.

La dev debug et la dev optimisée R8 utilisent toutes deux la clé debug existante, pour pouvoir se remplacer sans effacement. Les canaux publics utilisent exclusivement la clé release/envoi existante. Le probe reçoit la même signature que sa cible. Une recette R8 dev ne remplace pas celle du bundle public final, dont les constantes OAuth peuvent entraîner d’autres optimisations.

Le dev lit uniquement `.tools/oauth.dev.properties`, facultatif et ignoré, avec `githubClientId` et `githubClientSecret` d’une application OAuth dev. Il ne reprend jamais les credentials publics. Sans ces valeurs, les fixtures restent utilisables mais aucune connexion personnelle GitHub dev n’est configurée. Pour l’activer, il faudra enregistrer le callback dev et servir sa page/assetlinks avec le bon package et certificat ; rien de cela n’est publié ici. Utiliser un compte GitHub de test : une autre application OAuth n’isole pas, à elle seule, les Gists d’un même compte.

Les liens Play et les partages vers la fiche continuent de viser le package public. Confidentialité et licences restent communes. **Limite externe actuelle** : le formulaire Tally public redirige encore vers `hamigo://return` ; depuis dev, cela peut ouvrir Hamigo public. Un retour Tally entièrement distinct demandera une URL/formulaire de développement, à coordonner avant activation de cette intégration. Aucun changement du formulaire/site publié n’est inclus dans ce chantier.

## CI et activation différée

`.github/workflows/android.yml` prépare les checks sans secrets sur push/PR : contrats version/Play/signature, tests JVM, APK dev, APK de tests instrumentés, lint et compilation du helper indépendant ciblant dev. **Compiler ces APK de tests ne les exécute pas** ; la recette d’émulateur et le probe R8 restent requis avant livraison.

`.github/workflow-templates/play.yml` est un **modèle inactif**. Il prévoit les branches `main/alpha/beta`, vérifie l’ordinal du squash, teste sans credentials, puis construit APK/AAB signés avec la clé existante. Il vérifie le certificat d’envoi, archive bundle/APK/mapping/version/hash, puis peut envoyer un **brouillon** au track configuré. Le script n’annule pas une revue en cours et refuse un track avec une publication inachevée. Aucun tag n’est requis.

Avant son activation, coordonner avec le chantier publication :

1. Intégrer les corrections Play et le nettoyage, puis créer `main` depuis le parent historique convenu et préparer le squash 1.0. Ne pas déplacer la branche de travail d’une autre conversation.
2. Finaliser Play App Signing en préservant la clé existante pour la **signature distribuée**, afin que les installations APK 0.55/0.56 puissent recevoir les mises à jour Play. Ne pas accepter une autre clé d’app par défaut sans évaluer cette rupture. La clé d’envoi peut avoir un rôle distinct ; garder la clé actuelle tant que ce choix n’est pas fait. Les empreintes App Links doivent correspondre à la signature Play réellement distribuée.
3. Créer l’app Console et faire l’initialisation/first release manuelles convenues. Configurer test ouvert et liste interne. Aucun compte ou première publication n’est automatisé ici.
4. Autoriser un compte de service Google avec les droits minimaux nécessaires sur cette app, activer Android Publisher API, puis fournir ses credentials à GitHub Environments. Ce chantier ne les crée pas.
5. Lire les tracks via `python tools/play/publish.py --list-tracks` avec ce credential. Cet appel crée une edit temporaire puis la supprime, sans changer les tracks. Reprendre **les identifiants exacts** retournés : `production` pour main, track ouvert attendu `beta` pour beta, track interne retourné pour alpha. La documentation de présentation de Google mentionne `qa` pour l’interne ; des outils emploient `internal`. Ne pas décider à partir du nom de branche. [API tracks.list](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks/list).
6. Créer `play-production`, `play-beta`, `play-alpha`. Y limiter les branches respectives et exiger une approbation, au moins en production. Conserver l’auto-approbation possible pour un dépôt personnel à un seul propriétaire. Les secrets d’environnement ne sont délivrés qu’après approbation. [GitHub Environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments).
7. Fournir les secrets `HAMIGO_KEYSTORE_BASE64`, `HAMIGO_STORE_PASSWORD`, `HAMIGO_KEY_PASSWORD`, `HAMIGO_OAUTH_CLIENT_SECRET`, `PLAY_SERVICE_ACCOUNT_JSON`. La clé existante doit posséder l’alias `hamigo`, comme le build local. Ajouter la variable publique `HAMIGO_UPLOAD_CERT_SHA256` et `PLAY_TRACK` dans chaque environnement. Ne jamais afficher les valeurs secrètes dans un ticket/chat ou les versionner.
8. Après validation finale seulement, déplacer le modèle dans `.github/workflows/`, puis définir au niveau dépôt `PLAY_FIRST_RELEASE_COMPLETED=true` et `PLAY_UPLOAD_ENABLED=true`. Tant que ces deux portes ne sont pas ouvertes, l’envoi est refusé aussi par le script. Les premiers uploads restent des brouillons ; finaliser/revoir/publier depuis Console.

Une automatisation complète ultérieure peut choisir `--status completed --send-for-review`, après décision explicite sur la protection des environnements et le contrôle de production. Cela n’est pas activé par la présente préparation. Le managed publishing ne retient pas les sorties internes ; ne pas s’en servir comme protection universelle.

Le `version.properties` initial est une préparation : aucun code 570001 n’a été envoyé. Une réservation périmée échouera, plutôt que de réutiliser un numéro déjà consommé. L’artefact signé et son mapping doivent être conservés durablement au moment de la livraison ; les artefacts CI expirent après 90 jours.

## Points d’intégration

Le rebase sur `a8fe0b40` conserve les sept questions de bienvenue, leurs UUID et l’audit `welcome-review-56.json`, les icônes séparées, le nouveau domaine social, les derniers ajustements Mémo/graphe/pseudo/clavier, `OAUTH_SCOPE="gist user"`, `hasRequiredScopes`, les blocages et l’annonce/déclenchement Exam’1 après Continuer. Les constantes de callback/client restent propres à la variante. Les deux historiques de validation et les extensions récentes du probe sont conservés. Le nettoyage gère les migrations. La présence d’un autre utilisateur en 0.55 ne démontre pas à elle seule l’inutilité des anciens imports ou schémas cloud.

Résultats réellement exécutés et limites : [VALIDATION.md](VALIDATION.md). La CI distante, les tracks, l’accès API et une installation téléphone ne sont validés qu’après exécution réelle, pas par la présence de ces fichiers.
