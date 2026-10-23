# Préparation Google Play

Cette version prépare la distribution ; elle ne constitue pas une approbation Google Play.

## Liens

- Fiche : https://play.google.com/store/apps/details?id=com.malfreyt.alexandre.hamigo
- Site : https://alexandre.malfre.yt/projects/Hamigo/
- Confidentialité : https://alexandre.malfre.yt/projects/Hamigo/confidentialite.html
- Suppression : https://alexandre.malfre.yt/projects/Hamigo/donnees.html
- Règles : https://alexandre.malfre.yt/projects/Hamigo/conditions.html

Le futur lien Play est déjà employé dans l’app, les partages et le site, à la demande de l’éditeur. Sa présence ne prouve pas que la fiche est publiée.

## Accès des examinateurs

La RC contient un accès de démonstration dédié, distinct du menu Diagnostics : **`691427083519640782135906`**. Il s’ouvre uniquement en saisissant ce code exact puis `=` dans la calculatrice, notamment depuis Mémo, avant de confirmer. Aucun bouton de démonstration n’apparaît dans l’accueil ou les réglages. Depuis une installation neuve, continuer sans compte, passer le tutoriel et fermer sa dernière fiche avant d’ouvrir Mémo. Ce code public ouvre uniquement des données fictives ; ce n’est pas un secret de compte.

Les écrans et la logique de synchronisation, fusion, invitations, demandes réciproques et suppression sont les composants de production, alimentés par un service GitHub local persistant. Le bandeau indique ce mode et permet de le quitter. Aucun profil, ami ou jeton personnel n’est copié ; préférences et alias de chiffrement sont séparés. La synchro personnelle en arrière-plan est suspendue. La démonstration persiste après redémarrage ; Quitter restaure les données personnelles. Les actions externes visant des comptes fictifs ne sont pas transmises à GitHub.

Les instructions en anglais prêtes pour Play Console sont dans [PLAY-REVIEW-ACCESS.md](PLAY-REVIEW-ACCESS.md). Elles expliquent explicitement que le transport GitHub, OAuth et les courriels sont simulés dans ce mode ; le vrai parcours OAuth reste disponible en dehors. Le menu Diagnostics continue de servir à la revue des composants, sans connexion externe. Google permet des comptes de démonstration et d’autres ressources d’accès, sans garantir l’acceptation de chaque simulation : [exigences officielles](https://support.google.com/googleplay/android-developer/answer/15748846?hl=en).

## UGC : choix et limite actuels

Les règles sont acceptées avant les fonctions sociales. Les menus des équipiers et demandes reçues ouvrent les outils GitHub de blocage et de signalement. À la demande explicite de l’éditeur, Hamigo ne décide aucun blocage et ne reçoit aucun signalement de compte. Il lit la liste GitHub avec le scope `user`, retire les équipiers concernés et filtre leurs invitations dès la prochaine synchronisation réussie. Le cache de cette liste reste local au compte et ne part pas dans les Gists. Retirer un équipier reste une action distincte du blocage GitHub.

Le signalement propose une bottom sheet avec un modèle à copier (profil, Gist social, description/date/preuves à compléter), puis ouvre GitHub dans le navigateur configuré. Aucun envoi automatique. Le préremplissage de l’ancien formulaire ne peut pas être garanti après ses redirections vers le portail GitHub ; le modèle sert de repli.

Ce choix ne doit pas être présenté comme une conformité UGC acquise : la [politique Google](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en) vise aussi les clients de plateformes tierces et demande des fonctions de signalement/blocage effectives dans l’app. Le blocage est désormais appliqué dans le client ; la suffisance du signalement traité sur GitHub reste un point à examiner lors de la préparation de la demande.

## Données et droits

Le fonctionnement sans compte, les deux Gists non répertoriés et non chiffrés, les notifications GitHub, la voix Android, la caméra locale, les téléchargements REF et les retours Tally sont décrits dans la politique. Le formulaire Data safety doit être rempli d’après ces flux réels : l’absence de serveur de sauvegarde chez l’éditeur ne signifie pas qu’aucune donnée ne quitte l’appareil.

Avant toute nouvelle autorisation GitHub, les règles sociales et le transfert des données font l’objet de deux confirmations distinctes. Le second écran décrit les données, les destinataires, les écritures automatiques en arrière-plan, la portée Gist et les alternatives. Accepter les règles seul ne démarre pas OAuth. Les connexions antérieures attendent cette nouvelle confirmation avant de reprendre la synchro. Les licences et attributions intégrales sont accessibles hors ligne dans les paramètres.

La suppression intégrée vérifie les Gists reconnus du compte connecté, refuse les Gists mixtes contenant d’autres fichiers et suspend les publications après une interruption. Elle conserve l’apprentissage local et déconnecte après succès. Les commentaires sur les Gists d’autrui et les autres appareils demandent une démarche distincte, expliquée sur le site.

Le code est sous GPL-3.0-or-later. Les contenus adaptés du cours F6KGL/F5KFF sont sous CC BY-NC-SA 4.0 ; cette restriction non commerciale signifie que tous les contenus de la distribution ne sont pas des œuvres sous licence libre au même sens que le code. Les droits Exam’1 restent distincts et non présumés. Le mode de téléchargement de cette banque est conservé, conformément au choix de l’éditeur.

## Suite différée

L’éditeur dispose déjà d’un ancien compte développeur. La préparation de la fiche, des captures, de l’audience, de la classification, des déclarations de données et la saisie des instructions d’accès dans Play Console restent à faire. L’accès lui-même est inclus dans la RC. L’icône de fiche 512 × 512 est sous `assets/store/` ; les autres visuels sont différés.

CI, Play App Signing, clé d’envoi, AAB, éventuelles empreintes App Links/OAuth et nouvelle stratégie de versionnement seront traités séparément. Ne pas régénérer la clé existante. Une automatisation GitHub Actions vers l’API de publication Google Play est possible, mais aucun workflow ni accès de publication Play n’est créé ici.

Les retours de support reçus via Tally doivent suivre la durée annoncée par la politique : au plus douze mois après le dernier échange, hors nécessité légale ou demande explicite. La publication de cette politique implique aussi ce suivi, pas seulement l’existence de la page.
