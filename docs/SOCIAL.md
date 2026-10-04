# Progression, synchronisation et invitations

Hamigo reste utilisable hors ligne. Le stockage local enregistre immédiatement les réponses d'entraînement, l'XP, les leçons et les échéances SRS. En examen blanc, les brouillons de la partie courante ne sont enregistrés dans la progression qu'à sa finalisation ou à l'expiration du temps. La connexion GitHub est facultative et passe par l'application OAuth Hamigo commune : l'utilisateur autorise le scope `gist` dans la fenêtre GitHub interne ou le navigateur. Aucun jeton manuel ni Client ID personnel n'est demandé, et aucun secret OAuth n'est embarqué dans l'APK. Voir le [Device Flow GitHub](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow).

## Connexion OAuth et reprise réseau

L'écran de connexion affiche le code à usage unique et un bouton pour ouvrir GitHub dans Hamigo. La WebView peut renseigner ce code lorsque les champs de la page `https://github.com/login/device` sont reconnus ; le bouton de copie reste disponible. Le script ne lit pas les mots de passe, ne soumet aucun formulaire et ne clique pas sur l'autorisation. L'utilisateur reste responsable du consentement GitHub. Un bouton permet d'ouvrir le navigateur externe lorsque la connexion web l'exige.

La navigation principale HTTPS sur `github.com` reste dans la fenêtre ; les autres destinations sont confiées à l'application externe. La WebView n'a pas d'accès aux fichiers/contenus locaux et n'expose aucun pont JavaScript Android. La fenêtre se ferme automatiquement dès la réception du jeton par Device Flow. L'état « Vérification du compte », puis la première sauvegarde, continuent dans Hamigo : une sauvegarde lente ne maintient pas l'écran GitHub ouvert.

Le polling respecte l'intervalle fourni par GitHub et augmente cet intervalle après `slow_down`. Une interruption réseau, HTTP 429 ou une erreur 5xx laisse la session en attente, avec délai croissant plafonné à 30 secondes pour la composante de reprise réseau, jusqu'à expiration du code. Les refus d'autorisation et codes expirés restent des erreurs explicites. Les réponses OAuth HTTP 400 contenant le protocole d'attente sont décodées, au lieu d'être réduites à une erreur HTTP générique.

Après réception du jeton, la vérification du compte fait au plus trois essais en cas d'erreur réseau. Le jeton validé est conservé même si la première synchronisation échoue avec une erreur API/réseau ; un travail persistant demande alors une nouvelle tentative. Cela distingue l'autorisation, l'identité du compte et la publication des Gists. Ces corrections ne constituent pas une preuve de la cause exacte de l'incident OAuth ; la validation d'une autorisation personnelle complète doit être rapportée séparément.

## Deux Gists distincts

- `hamigo-progress.json` contient le résumé partageable : pseudo, XP, série, nombre de leçons terminées, XP de la semaine, date de mise à jour et jusqu'à 30 jours de points journaliers pour les graphiques. Il ne contient ni réponses, ni échéances SRS, ni jeton.
- `hamigo-backup.json`, dans un **autre Gist**, contient la progression complète : XP et jours actifs, compteurs de réponses, leçons terminées, révisions SRS, historique des nouveaux événements, pseudo et préférences d'objectif/rappel. Il est retrouvé avec le même compte GitHub sur une nouvelle installation. Le jeton OAuth, les clés Keystore et la liste locale des amis ne font pas partie de cette sauvegarde.
- Chaque installation conserve aussi son fichier `hamigo-device-<UUID>.json` dans le Gist de sauvegarde. Un PATCH ne touche que le fichier de cette installation et le résumé agrégé. Les autres fichiers restent intacts, conformément au [contrat PATCH des Gists](https://docs.github.com/en/rest/gists/gists#update-a-gist). Cela conserve les événements de deux appareils qui publient simultanément.

Les deux Gists sont créés avec `public: false`, donc **secrets / non répertoriés**. GitHub ne propose pas de Gist réellement privé : une personne ayant son URL peut le lire, et GitHub garde son historique. La sauvegarde complète n'est pas chiffrée. Son identifiant n'est jamais inclus dans l'invitation d'amis, le QR code ou l'image partagée. L'interface doit annoncer ces propriétés sans qualifier la sauvegarde de « privée ». Voir la [confidentialité des Gists](https://docs.github.com/en/get-started/writing-on-github/editing-and-sharing-content-with-gists/creating-gists).

La suppression locale de la connexion supprime le jeton et les travaux programmés ; elle ne détruit pas les Gists sur GitHub. L'utilisateur peut supprimer les Gists depuis son compte. Une autorisation expirée demande une nouvelle connexion.

## Déclencheurs et fréquence

La lecture de la sauvegarde personnelle, sa fusion avec les données locales, puis l'écriture de la sauvegarde complète et du résumé social se font :

1. lors de la connexion GitHub ;
2. au retour de l'application au premier plan ;
3. à la fin d'une session ou en la quittant ;
4. sur l'action explicite d'actualisation de l'équipe ;
5. après les réponses d'entraînement enregistrées, ou la finalisation d'une partie d'examen, via un travail persistant retardé de **8 secondes après la dernière mutation** ;
6. en arrière-plan, avec un travail unique demandé **toutes les heures**, dans une fenêtre flexible de 15 minutes.

Les actualisations de l'équipe relisent les résumés des amis et conservent leur dernière carte si un ami est inaccessible. Le travail en arrière-plan actualise aussi ces cartes. Une modification du pseudo, de l'objectif, des rappels ou une restauration programme également le même déclencheur persistant à huit secondes. Sans connexion GitHub, les amis se relisent au premier plan ; le travail périodique est réservé aux comptes connectés.

Le travail persistant demande une connexion réseau et une batterie suffisamment chargée. Ce n'est pas un minuteur exact : Android peut le retarder avec Doze et les restrictions constructeur. Les erreurs réseau temporaires provoquent au maximum trois nouvelles tentatives avec délai croissant. Désactiver la synchronisation automatique annule les deux travaux ; l'actualisation explicite demeure possible. Voir les [contraintes et travaux périodiques WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

## Fusion entre appareils

`CloudProgress` conserve un socle pour les données antérieures à la mise à jour. Les anciens totaux et XP journaliers se fusionnent par maximum, et les leçons terminées par union : l'ancien format ne permet pas de distinguer deux historiques indépendants qui avaient déjà été cumulés.

Les nouvelles tentatives sont des événements immuables identifiés par UUID. Leur union additionne les réponses sans les compter à nouveau lors d'une seconde lecture. L'XP associé à la même question et au même jour se fusionne par maximum ; le bonus d'une même leçon n'est attribué qu'une fois. Chaque révision porte une date de modification : la plus récente gagne, avec une règle déterministe en cas d'égalité. Le pseudo et les préférences sont horodatés uniquement lorsque l'utilisateur les modifie, afin que les valeurs par défaut d'un nouvel appareil n'écrasent pas le profil existant.

Les fichiers de tous les appareils sont fusionnés à chaque lecture, puis republient une vue agrégée. Les lectures et mutations locales sont protégées par `Progress.CLOUD_LOCK`, et les publications dans un même processus par une coroutine `Mutex`. Le Gist n'est pas une base transactionnelle : une activité locale survenant pendant une publication peut attendre la prochaine actualisation, mais ses événements restent conservés localement et dans le fichier de son appareil dès publication. Aucune fusion ne remplace aveuglément l'état local par le dernier fichier reçu.

Un écouteur des préférences rafraîchit les valeurs affichées lorsqu'un travail termine alors que la page reste ouverte. Il recharge la progression et les équipiers sans déclencher une nouvelle synchronisation, pour éviter une boucle de publications. Les cartes d'amis plus récentes ne sont pas remplacées par un ancien retour réseau.

## Lien HTTPS et QR code

`FriendInvite.link` produit `https://alexmalfr.github.io/hamigo/?invite=<identifiant-du-Gist-social>`. Ce lien peut circuler dans Discord et les autres messageries qui reconnaissent HTTPS. Android App Links ouvre Hamigo ; la page statique GitHub Pages propose aussi un bouton vers `hamigo://join?invite=...`. Si l'application est absente, elle invite à demander l'APK à l'ami : les releases restent dans le dépôt privé. Le fichier `/.well-known/assetlinks.json` lie le domaine au package Android et au certificat de signature. Aucun serveur applicatif n'est nécessaire.

Le QR code encode exactement le même lien HTTPS, avec une marge blanche et une correction d'erreur M. L'appareil photo du téléphone suffit pour le lire ; Hamigo ne demande donc pas une permission caméra uniquement pour partager son invitation. Le parseur refuse les hôtes différents, les identifiants invalides, les URL contenant des identifiants de connexion, les fragments et les paramètres ambigus/répétés. L'import de profils via JSON est supprimé. Une sauvegarde JSON complète reste un outil avancé de migration, distinct du partage social.

Les actions de partage utilisent des libellés différents selon leur contenu : **Partager mon bilan** dans Moi, **Partager le classement** dans Équipe, **Partager mes résultats** à la fin d'un examen ou mix, et **Partager mon parcours** après une leçon ou des flashcards. Les images sont rendues localement et passées au menu de partage Android ; elles contiennent leurs graphiques et ne portent aucun jeton ni identifiant du Gist de sauvegarde. Le graphique de Moi permet également de remonter l'historique local par plages de sept jours ; le résumé social conserve un historique récent borné.

## Protection du jeton et limites

Le jeton est chiffré en AES-256-GCM dans les préférences privées, avec une clé Android Keystore et un IV aléatoire. Il n'est ni journalisé, ni exporté, ni inclus dans les Gists. Les appels API reconstruisent l'hôte fixe `api.github.com` après validation de l'identifiant ; une URL d'invitation n'est jamais appelée avec le jeton. Les lectures d'amis utilisent la connexion GitHub lorsqu'elle existe, sinon elles sont anonymes et soumises au plafond anonyme de GitHub. Voir [Android Keystore](https://developer.android.com/privacy-and-security/keystore).

Le transport API/OAuth utilise **OkHttp**, avec appels annulables lorsque la coroutine est annulée, un délai de connexion de 15 secondes, de lecture de 20 secondes et un plafond de 30 secondes par appel. Les redirections HTTP et HTTPS sont refusées. Les réponses API sont bornées à 20 Mio, OAuth à 128 Kio et les sauvegardes brutes à 8 Mio. Les erreurs DNS, délai dépassé et TLS sont distinguées des refus API, sans enregistrer le corps des credentials.

Une sauvegarde est bornée à 8 Mio et 100 000 nouveaux événements, avec au maximum 20 000 révisions. Une sauvegarde tronquée par l'API Gist est lue uniquement sur une URL `gist.githubusercontent.com` strictement validée à partir de l'identité, du Gist, de sa révision et du nom de fichier ; cette lecture brute ne porte aucun jeton. Le format social est borné à 32 Kio et accepte les anciens résumés de schéma 1 ainsi que le schéma 2 avec graphiques.

## Vérification

`CloudSyncInstrumentedTest` vérifie hors réseau la fusion commutative et idempotente, les événements de deux appareils, la déduplication XP question/jour et bonus de leçon, les anciens socles, le choix des révisions/préférences, le rejet des données malformées, les liens d'invitation et le décodage réel du QR produit. Les tests de plateforme existants couvrent le chiffrement du jeton, les URI de partage et les rappels. Ces tests n'utilisent aucun compte réel et ne publient aucun Gist.

`AuthFlowInstrumentedTest` utilise un serveur HTTP local pour les scénarios OAuth d'attente, de ralentissement, de coupure puis reprise, d'annulation et de refus des redirections ; il couvre aussi le format des PATCH et une restauration entre installations fictives. Les scénarios réseau réel de `LiveGitHubTransportInstrumentedTest` sont séparés, réservés à un audit explicitement activé sur émulateur : attente `authorization_pending` sans consentement personnel, puis éventuels Gists temporaires d'audit avec données fictives et nettoyage. Ces scénarios ne prouvent pas une autorisation personnelle complète dans la WebView ni la cause de l'incident initial. Les résultats effectivement exécutés restent dans [VALIDATION.md](VALIDATION.md).
