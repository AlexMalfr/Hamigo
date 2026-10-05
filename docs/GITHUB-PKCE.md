# Connexion native GitHub configurée le 5 octobre 2026

## Parcours et configuration

Le formulaire Device Flow de GitHub répartit le collage natif mais pas certaines insertions des claviers logiciels. Un onglet du navigateur habituel ne permet pas à Hamigo de modifier le JavaScript de GitHub. La connexion directe avec Authorization Code + PKCE évite ce formulaire : autorisation sur GitHub, retour HTTPS dans Hamigo, échange du code puis vérification du compte et synchronisation.

Ce parcours réutilise l'application OAuth Hamigo et son scope `gist`. Le callback `https://alexmalfr.github.io/hamigo/oauth/` est déjà enregistré sans wildcard dans son portail. Le domaine est associé au package et au certificat release par Digital Asset Links ; son état Android a été vérifié sur le téléphone. Aucune modification de permission ni backend n'est nécessaire.

GitHub demande encore un `client_secret` pour l'échange PKCE. Son guide recommande ce flux pour les clients natifs, tout en précisant que le secret distribué dans une application ne peut pas rester confidentiel. Ce paramètre commun n'est pas un jeton utilisateur et ne donne pas seul accès à un compte. Hamigo ne s'en sert pas pour prouver qu'une installation est authentique.

Le secret commun de l'application OAuth Hamigo a été généré dans le portail GitHub le **5 octobre 2026**, avec l'autorisation de l'utilisateur, puis renseigné dans le fichier local ignoré `.tools/oauth.properties`. Le build lit sa propriété `githubClientSecret` et intègre ce paramètre dans l'APK pour activer le parcours direct. Le secret reste hors des fichiers versionnés et des rapports. Les personnes installant cet APK utilisent toutes la même application OAuth et ne fournissent aucun secret personnel.

Une compilation dépourvue de cette configuration locale conserve Device Flow. Il s'agit bien du secret de l'application OAuth, jamais d'un jeton personnel GitHub. Le parcours direct avec ce paramètre intégré a été validé sur le téléphone avec le compte déjà autorisé ; les observations et limites figurent ci-dessous.

## Protection de la transaction

Chaque tentative possède un état aléatoire et un vérificateur de 256 bits, avec challenge SHA-256 `S256`. Le callback doit correspondre exactement à l'origine et au chemin enregistrés : ports explicites, userinfo, fragments, chemins encodés et paramètres dupliqués sont refusés. L'état est comparé avant que le code ou une erreur puisse terminer la tentative. Un lien forgé ou provenant d'une ancienne connexion ne ferme pas la tentative actuelle.

Le vérificateur et l'état sont conservés temporairement sous AES-GCM avec une clé Android Keystore, séparément du jeton utilisateur et des sauvegardes. Ils expirent après dix minutes. Une reconstruction du processus reprend l'attente avant de traiter l'intent reçu. Dès qu'un callback est accepté, la transaction est consommée avant l'échange réseau ; un deuxième callback ne peut pas déclencher une seconde requête. Annuler, expirer ou échouer supprime les données temporaires.

Le transport OAuth garde ses destinations fixes, sans redirections HTTP. L'échange demande `client_id`, `client_secret`, `code`, `redirect_uri` et `code_verifier`. Seule une réponse Bearer contenant le scope `gist` est acceptée. Les descriptions d'erreur du serveur ne sont pas recopiées dans l'interface. Après échange, le compte est vérifié comme auparavant avant de conserver le jeton dans Keystore et de synchroniser.

## Navigateur et retour

AndroidX Auth Tab utilise le navigateur par défaut et son stockage de session ordinaire. Il attend le host et le chemin HTTPS exacts, puis ferme l'onglet au retour. Le domaine doit être vérifié ; les versions de navigateur incompatibles se replient sur Custom Tabs et Android App Links. Les deux canaux de retour passent par la même vérification de transaction. L'URI contenant le code est retirée de l'intent pour éviter une reprise lors d'une recréation d'activité.

Une annulation rapportée par le navigateur ne détruit pas immédiatement la transaction : un repli Custom Tabs peut la rapporter après avoir livré l'App Link. Les actions Rouvrir et Annuler restent dans Équipe. Une véritable erreur d'ouverture termine proprement l'attente. Le repli Device Flow garde ses barres contenant code et copie.

## Validation

Les **65 tests JVM et 42 tests Android locaux passent**, avec identifiants fictifs et serveurs de transport locaux. Ils ne constituent pas une autorisation personnelle réussie auprès de GitHub. Le retour d'un App Link depuis un vrai Custom Tab a reproduit une seconde instance avec `singleTop` ; `singleTask` retrouve le même ViewModel et retire le navigateur de la pile. La fermeture est attendue jusqu'à sa destruction effective, après la reprise de l'app. Une reprise à froid et une recréation effacent le code de l'intent. Les preuves et limites figurent dans [VALIDATION.md](VALIDATION.md).

Le **5 octobre 2026**, l'APK release signé **0.10+4a37256b** a été installé sur le Samsung SM-S938B. Après déconnexion locale du compte puis lancement du parcours direct, Hamigo a repris la même `MainActivity` et affiché successivement « GitHub a autorisé Hamigo. Vérification du compte… », puis « Connecté · AlexMalfr » avec une synchronisation terminée. Le navigateur observé était un **Custom Tab** : son activité terminée ne figurait plus dans l'historique de la tâche. Le relevé final confirme une seule `MainActivity`, aucun onglet navigateur dans la tâche et Hamigo au premier plan.

Les **386 XP et trois leçons** étaient conservés. Après arrêt forcé et relancement, le compte restait connecté et une nouvelle synchronisation était affichée ; le domaine Android App Links était toujours vérifié. Le rapport local `output/pkce-validation/phone-live-stack.json` consigne la pile et la persistance observées, sans code OAuth ni jeton.

Le compte avait déjà autorisé Hamigo. La connexion réelle, l'échange PKCE, le retour, la fermeture du Custom Tab, la synchronisation et la persistance sont donc validés pour ce cas. Cette passe n'a pas montré de nouveau consentement, de saisie de mot de passe ou de double authentification ; elle ne valide pas ces écrans pour un nouvel utilisateur ni le canal de retour d'un Auth Tab natif distinct du Custom Tab observé.

## Sources

- [Recommandations GitHub pour les clients natifs](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/best-practices-for-creating-an-oauth-app)
- [Authorization Code, PKCE et paramètres d'échange GitHub](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)
- [Auth Tab et compatibilité Custom Tabs](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab)
- [RFC 7636 : PKCE](https://www.rfc-editor.org/rfc/rfc7636)
- [RFC 8252 : OAuth dans les applications natives](https://www.rfc-editor.org/rfc/rfc8252)
