# Décisions de conception Hamigo

## Application locale et contenu

Hamigo est une application Android native en Kotlin, Jetpack Compose et Material 3. Le parcours, la banque de questions, les illustrations, les outils et la répétition espacée fonctionnent hors ligne. Aucun modèle d'IA ni serveur applicatif n'est nécessaire pour apprendre.

Le parcours contient **21 chapitres, 94 leçons et 826 exercices originaux**. Les identifiants des anciennes leçons restent stables pour préserver la progression. Les nouveaux chapitres approfondissent les maths, les circuits RLC, les mesures RF, les antennes, le numérique et les méthodes de résolution. Le chapitre Morse, placé après les premiers usages opérateur, comprend 14 leçons : lettres, chiffres, ponctuation, écoute, composition et groupes de trafic.

La banque Exam1 REF conserve ses **2 961 questions et ses illustrations archivées** ; 11 exclusions documentées donnent un ensemble courant de 2 950 questions. L'examen blanc utilise exclusivement cet ensemble. Les cours sont écrits pour Hamigo ; les variantes procédurales sont produites localement par des règles explicites. Le catalogue de **5 188 variantes** possède des identifiants reproductibles, récupérables pour les révisions SRS. Les variantes supplémentaires d'une leçon restent limitées aux concepts identifiés comme enseignés dans cette leçon.

Les attributions pédagogiques sont conservées dans [SOURCES-COURSES.md](SOURCES-COURSES.md) et [SOURCES-EXAM1.md](SOURCES-EXAM1.md). Les adaptations des ressources F6KGL conservent l'attribution CC BY-NC-SA 4.0. Le statut de licence propre de la banque Exam1 n'est pas présumé ; son archive reste destinée à ce projet privé personnel. Aucun code de l'application Android Exam1RA n'a été repris.

## Maîtrise et révision

Les règles officielles actuelles sont deux parties de 20 QCM : réglementation en 15 minutes et technique en 30 minutes. Une bonne réponse rapporte un point, une réponse incorrecte ou absente zéro ; il faut au moins 10/20 dans chaque partie. Le Morse enrichit l'apprentissage, sans être une épreuve obligatoire du certificat actuel. Les sources sont référencées dans SOURCES-COURSES.md.

Chaque épreuve possède une introduction ; son chronomètre démarre lorsque le candidat la commence. Les réponses restent des brouillons modifiables avec retour à la question précédente et grille de relecture par numéro. La relecture n'arrête pas le chrono. Finaliser, ou atteindre le temps limite, enregistre une fois les réponses, l'XP et le SRS de cette partie ; le score devient définitif. La partie technique dispose ensuite de son propre départ. Quitter abandonne les brouillons de l'épreuve courante, mais conserve les parties finalisées. Le résultat final présente un récapitulatif question par question.

Une leçon se valide avec **au moins 80 % de réponses justes au premier essai et aucune erreur restant à corriger**. Les reprises pendant la séance servent à consolider les acquis ; elles ne transforment pas une première tentative ratée en validation automatique. Les erreurs peuvent revenir jusqu'à deux fois dans la même séance et restent accessibles ensuite en révision.

L'XP est volontairement modéré : 3 XP pour une réponse juste, 1 XP pour une réponse incorrecte, au plus une attribution par question et par jour, puis 6 XP pour la première validation d'une leçon. Les bonus de leçon ne sont pas renouvelés. La répétition espacée est une variante SM-2 explicite : une erreur revient après dix minutes, puis les réussites allongent les intervalles. Relire tôt une carte acquise n'allonge pas artificiellement son échéance.

## Interactions et repères visuels

Hamigo propose **15 formats d'exercice et de révision**, dont 13 dans les cours : QCM, vrai/faux, phrases à compléter, sélection multiple, associations, ordre, calculs, résistances visuelles, estimation, bits binaires, formes d'onde, écoute et composition Morse, auxquels s'ajoutent le cadran de fréquence et les flashcards. Chaque format affiche une consigne explicite et un repère visuel ; les associations dessinent les connexions créées.

La page Défis distingue l'examen blanc, le labo de 12 exercices renouvelés et le mix sur mesure. Le mix propose 10, 20, 40, 80 ou 150 questions, avec une saisie de 1 à 1 000 ouverte uniquement par « Nombre libre ». Les thèmes sont regroupés sous Réglementation et Technique dans une section repliable ; chaque groupe contrôle ses sous-thèmes. Le mélange de variantes procédurales reste optionnel et le nombre demandé doit être disponible dans la sélection.

Les **18 fiches et 390 flashcards** proposent une révision selon les échéances ou dans un ordre aléatoire. La recherche s'ouvre à la demande ; le titre et les actions de révision restent accessibles dans l'en-tête de fiche, et revenir à la bibliothèque conserve sa position. Les couleurs de résistances sont représentées par des anneaux et des pastilles ; le guide montre sens de lecture, chiffres, multiplicateur et intervalle de tolérance. Le Morse utilise le même dessin de points/traits dans les ressources, cours, questions et corrections, avec des séparateurs de lettres et de mots explicites. Les fiches pertinentes intègrent six outils : résistance à quatre/cinq anneaux, associations série/parallèle, traduction Morse dans les deux sens avec écoute, rapports/décibels, loi d'Ohm et fréquence/longueur d'onde.

La carte officielle des trois régions UIT est embarquée pour une consultation hors ligne, avec zoom. La fiche indicatifs inclut les préfixes français, dont FY, et une sélection internationale avec raccourcis Maps ; elle n'est pas un annuaire mondial exhaustif. L'alphabet international utilise la synthèse vocale anglaise Android, qui dépend de la voix disponible sur le téléphone ; les signaux Morse sont synthétisés localement. Les liens Maps et sources externes demandent un réseau.

Une calculatrice scientifique flottante accompagne les questions hors flashcards. Elle conserve l'écran sous-jacent et peut transférer son résultat à une réponse numérique. Son évaluateur local borné accepte opérations, puissances, racines, logarithmes, trigonométrie DEG/RAD, notation scientifique et mémoire Ans ; il n'exécute pas de script. Les curseurs sont ajustés au pas demandé et les nombres affichés selon la tolérance de l'exercice.

Les illustrations Exam1 originales restent intactes : seul un aperçu dérivé retire le fond papier reconnu et réduit les marges. L'agrandissement présente l'original en plein écran, avec pincement, déplacement et double-toucher pour réinitialiser. Le retour prédictif anime l'écran et montre sa destination sous-jacente ; annuler le geste conserve l'écran et ses entrées. L'historique du profil utilise des plages de sept jours parcourables par glissement ou boutons, à partir des XP journaliers enregistrés.

Pico, le petit poste radio, possède **8 expressions et 6 poses**. Un même dessin vectoriel original est utilisé dans les questions, les rappels et les images partageables. Les rappels quotidiens adaptent texte, expression, pose et illustration au jour, à la série et à l'objectif : démarrage, poursuite, reprise après interruption, objectif en cours ou atteint. Les alarmes Android sont inexactes et les notifications utilisent les modèles Android ; leur livraison et leur présentation restent soumises au système et aux réglages du téléphone.

## Compte et synchronisation GitHub

La connexion est facultative, proposée à l'onboarding et directement dans Équipe. L'application OAuth **Hamigo commune** utilise Device Flow avec le scope `gist`. Seul son Client ID public est intégré à l'APK ; aucun secret OAuth ni jeton personnel de développement n'est embarqué. Le jeton utilisateur est chiffré avec Android Keystore et n'est jamais exporté dans une sauvegarde ou une image.

L'écran de connexion peut ouvrir GitHub dans une WebView interne, limitée à GitHub pour la navigation principale, ou dans le navigateur externe. Le remplissage du code ne concerne que la page HTTPS `/login/device` ; il ne soumet pas le formulaire et n'effectue pas le consentement à la place de l'utilisateur. La fenêtre disparaît dès que le polling reçoit le jeton, avant les appels de vérification du compte et de sauvegarde. Le polling respecte l'intervalle GitHub et `slow_down`, et reprend après les erreurs réseau transitoires, HTTP 429 et 5xx jusqu'à l'expiration du code. La vérification du compte réessaie les erreurs réseau ; un échec de première sauvegarde conserve la connexion et programme une nouvelle tentative.

Le transport utilise OkHttp avec annulation des appels liée aux coroutines, limites de taille et délais distincts de connexion/lecture/appel. Les redirections sont refusées ; les destinations API, OAuth et sauvegardes brutes sont contrôlées séparément. Ces mesures traitent des modes de panne possibles ; elles ne prouvent pas la cause exacte de l'incident OAuth ni une autorisation personnelle complète validée de bout en bout.

Deux Gists distincts séparent les données :

- La sauvegarde complète contient XP, jours actifs, tentatives, leçons validées, échéances SRS, pseudo et préférences d'objectif/rappel. Elle permet la restauration et la fusion avec le même compte sur plusieurs appareils. Les jetons, les clés Keystore et la liste locale des amis en sont exclus.
- Le résumé social contient uniquement pseudo, XP, série, nombre de leçons, XP hebdomadaires, date de mise à jour et points journaliers destinés aux graphiques. Seul son identifiant est partagé dans les invitations.

Les deux Gists sont créés avec `public: false` : **secrets/non répertoriés, mais accessibles à toute personne qui possède leur URL**. La sauvegarde complète n'est pas chiffrée ; elle n'est donc jamais présentée comme privée. Déconnecter le compte supprime les credentials locaux et les travaux programmés, sans supprimer les Gists du compte GitHub.

La synchronisation lit les sauvegardes, les fusionne avec l'état local puis republie la sauvegarde et le résumé. Elle se déclenche à la connexion, au retour au premier plan, en fin de séance, après modification du profil/préférences et sur actualisation manuelle. Les réponses déclenchent aussi un travail persistant **8 secondes après la dernière réponse**. WorkManager demande un rafraîchissement **toutes les heures**, avec réseau et batterie suffisante ; Android peut le différer. Désactiver l'automatisation annule ces travaux et conserve l'action manuelle.

La fusion repose sur un socle historique et des événements immuables identifiés par UUID. Elle conserve les leçons par union, déduplique les événements et les attributions XP, et utilise les dates de modification pour les révisions et préférences. Chaque installation garde également son propre fichier dans le Gist de sauvegarde pour conserver les événements de publications concurrentes. Une mutation locale pendant un appel réseau attend au besoin la publication suivante. Le détail des déclencheurs, de la fusion et des limites se trouve dans [SOCIAL.md](SOCIAL.md).

## Invitations et partage

Le lien `https://alexmalfr.github.io/hamigo/?invite=<identifiant-social>` et son QR code passent par les messageries qui reconnaissent HTTPS. Android App Links associe le domaine au package et au certificat release ; une page statique GitHub Pages propose aussi un bouton vers le schéma `hamigo://`. Aucun serveur applicatif ni stockage de progression sur cette page n'est nécessaire. Le parseur vérifie hôte, schéma, identifiant et paramètres ; ajouter un équipier demande une confirmation dans l'app.

Les libellés de partage distinguent les documents produits : « Partager mon bilan » depuis Moi crée les statistiques et le graphique personnels ; « Partager le classement » depuis Équipe crée le classement et ses graphiques ; « Partager mes résultats » à la fin d'un examen ou d'un mix crée les scores, la durée et les graphiques de cette séance. Les leçons et flashcards utilisent « Partager mon parcours ». Les encouragements utilisent l'application de messagerie choisie par l'utilisateur.

Le partage et l'import de profils par JSON sont supprimés. L'export/import JSON de sauvegarde complète reste disponible dans une section avancée repliable des réglages, pour les migrations manuelles.

## Limites de la couverture pédagogique

L'[audit ciblé du 4 octobre 2026](CONTENT-AUDIT-2026-10.md) compare le plan F6KGL aux cours et mémos ; il ne valide pas exhaustivement les explications et exercices. Des approfondissements restent nécessaires pour les conditions réglementaires et limites, la résistivité/conductivité, les piles et accumulateurs, la charge des appareils de mesure, les amplificateurs opérationnels, les tubes et le couplage entre étages, ainsi que les schémas de filtres et d'adaptation. La prochaine passe doit établir une matrice section source → explication → illustration → exercice → mémo et demander une relecture de formateur. Les correctifs de cette série et leurs limites sont résumés dans [FIXES-2026-10.md](FIXES-2026-10.md).
