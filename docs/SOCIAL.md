# Progression entre amis, partage et rappels

Hamigo fonctionne hors ligne. Les leçons, réponses, échéances de révision et paramètres restent sur le téléphone. Le volet social utilise au choix des fichiers échangés ou un Gist GitHub contenant un résumé de progression. Aucune API personnelle, clé GitHub ou configuration serveur n'est nécessaire pour le partage par image.

## Sans compte

- La carte de progression est un PNG 1080 × 1350, dessiné sur l'appareil, avec pseudo, XP, série, leçons terminées et XP de la semaine.
- Un encouragement ouvre le menu de partage Android avec un message prêt à envoyer ; l'utilisateur choisit lui-même le destinataire et l'application.
- Un fichier de comparaison contient uniquement `schema`, `name`, `xp`, `streak`, `lessons`, `weeklyXp` et `updatedAt`. L'import de ce résumé met à jour la comparaison avec l'ami ; il ne remplace pas les données de révision personnelles.
- La sauvegarde complète est un fichier JSON partagé explicitement depuis le téléphone. Elle est distincte du résumé social et n'est jamais envoyée au Gist par le client social. Elle ne doit contenir aucun jeton GitHub.

## GitHub, pour deux personnes sans serveur

1. Créer un [jeton personnel GitHub classique](https://github.com/settings/tokens/new?scopes=gist&description=Hamigo) avec uniquement le scope `gist`, ou un jeton fin disposant de l'autorisation utilisateur **Gists: write**. Choisir une date d'expiration.
2. Dans l'application, saisir le jeton et connecter le compte. Le client vérifie l'identité GitHub avant de le conserver.
3. Publier une première fois la progression. Le client crée un Gist **secret** contenant `hamigo-progress.json`, ou retrouve et met à jour le Gist secret existant du même compte avec ce nom de fichier.
4. Partager le lien du Gist avec l'ami ; chacun colle le lien de l'autre pour consulter sa progression.
5. Les publications suivantes mettent à jour ce résumé. L'UI peut les déclencher en fin de session ou au retour au premier plan ; un échec de connexion ne doit jamais interrompre une révision. Les comparaisons se rafraîchissent lors d'une action explicite de lecture.

Un Gist secret est **non répertorié, pas privé** : toute personne ayant le lien peut le lire. Le résumé contient donc seulement des indicateurs choisis pour le partage. Il ne comporte ni historique de réponses, ni identifiant d'appareil, ni échéances SRS, ni adresse email, ni token. GitHub conserve les versions précédentes du Gist. Ces propriétés sont documentées par [GitHub sur les Gists](https://docs.github.com/en/get-started/writing-on-github/editing-and-sharing-content-with-gists/creating-gists).

Le jeton est chiffré en AES-256-GCM dans les préférences privées. La clé reste dans Android Keystore ; chaque écriture utilise un IV aléatoire. Le jeton n'est ni journalisé ni inclus dans les exports. La déconnexion supprime le jeton et la clé locale, mais conserve le Gist sur GitHub. Un jeton expiré se remplace par une nouvelle connexion. Le mécanisme suit les [recommandations Android Keystore](https://developer.android.com/privacy-and-security/keystore) et le [contrat AES-GCM](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setRandomizedEncryptionRequired(boolean)).

## OAuth facultatif

Il faut enregistrer sa propre application OAuth GitHub et activer **Device Flow**. Aucun Client ID ni secret n'est embarqué par défaut. Avec le Client ID saisi par l'utilisateur, `DeviceOAuth.start(clientId)` fournit une URL de vérification et un code. Après validation dans le navigateur, `DeviceOAuth.awaitToken(clientId, session)` attend l'autorisation en respectant l'intervalle demandé et `slow_down`. Le jeton obtenu se valide et se conserve via `GitHubSync.connect(token)`. L'attente est annulable et expire en fonction du code. Les jetons expirants demandent une nouvelle connexion ; le client ne conserve pas de refresh token. Voir le [flux officiel GitHub](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow).

## Intégration Android

- `DailyReminder.configure(context, enabled, hour, minute)` écrit les clés `reminderEnabled`, `reminderHour` et `reminderMinute` dans les préférences `hamigo` puis programme ou annule le rappel. Valeurs initiales : désactivé, 20:00.
- Un `AlarmManager.setAndAllowWhileIdle` **inexact** est recalculé chaque jour en heure locale et après redémarrage, changement de fuseau ou d'heure. Le système peut retarder sa livraison pour économiser la batterie. Il n'est pas nécessaire de demander l'autorisation des alarmes exactes. Voir les [rappels Android](https://developer.android.com/develop/background-work/services/alarms).
- L'UI demande `POST_NOTIFICATIONS` sur Android 13 et suivants avant activation. Le canal `daily_practice` est créé, et toucher le rappel ouvre l'application. Voir l'[autorisation Android de notification](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- Déclarer `INTERNET`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, le receiver `.platform.ReminderReceiver` (non exporté) pour `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, et un `FileProvider` non exporté avec l'autorité `${applicationId}.files`.
- Les partages utilisent uniquement `<cache-path name="shared" path="share/"/>`, un URI `content://`, `ClipData` et une permission de lecture temporaire. Aucun accès général au stockage n'est demandé. Voir le [FileProvider Android](https://developer.android.com/reference/androidx/core/content/FileProvider).
- Les lectures d'amis sont anonymes et utilisent une URL API reconstruite après validation stricte de l'hôte. Le jeton n'est jamais envoyé à un lien fourni par l'utilisateur. Les connexions n'acceptent pas de redirection, ont des délais de 15 secondes et bornent les réponses.
- `GitHubSync.push(ShareProgress)` renvoie `GistSnapshot(id, url, progress)` ; `read(urlOrId)` renvoie un `ShareProgress`. Les erreurs réseau ont des messages compréhensibles. La création utilise `public: false`, et les publications suivantes utilisent PATCH. Les appels respectent les [endpoints REST Gist officiels](https://docs.github.com/en/rest/gists/gists).

## Vérification

Les tests instrumentés `PlatformInstrumentedTest` couvrent le cycle de vie d'un jeton isolé, les IV aléatoires, l'absence de clair dans les préférences, le contrat JSON social, le PNG et son URI FileProvider, l'heure locale lors du changement d'heure, ainsi que la programmation/annulation sans autorisation d'alarme exacte. Ils ne réalisent aucune connexion GitHub ni publication, et ne touchent pas le véritable jeton de l'utilisateur.
