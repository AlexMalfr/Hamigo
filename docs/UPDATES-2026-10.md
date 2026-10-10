# Retours intégrés — octobre 2026

## Synchronisation et équipe

L'application OAuth GitHub **Hamigo** est enregistrée dans le compte AlexMalfr, avec Device Flow activé. Son identifiant public est intégré dans l'APK ; aucun secret OAuth ni jeton de développeur ne l'est. Chaque joueur autorise son propre compte. L'accueil propose cette connexion et la page Équipe commence par une carte de synchronisation avec compte, statut, date et commande d'actualisation.

La connexion lit, fusionne et écrit la progression complète dans un Gist de sauvegarde. Un second Gist publie seulement le pseudo, les compteurs et les points journaliers nécessaires à l'équipe et aux graphiques. Les deux sont **non répertoriés**, sans chiffrement de la sauvegarde complète ; leur URL permet de les lire. Seul l'identifiant du résumé social circule dans les invitations. La liste des amis et le jeton restent sur l'appareil.

Les lectures et écritures se déclenchent à la connexion, au premier plan, à la fin ou à la sortie d'une séance, sur actualisation manuelle, puis huit secondes après la dernière réponse ou modification du pseudo, de l'objectif, des rappels ou après restauration. Un travail persistant demande aussi une actualisation horaire, sous contraintes réseau/batterie ; Android peut la retarder. Les amis sont relus lors des actualisations, y compris par le travail horaire si le compte est connecté. Voir [SOCIAL.md](SOCIAL.md) pour les règles de fusion et les limites.

Les profils JSON sont retirés. La sauvegarde manuelle complète reste une section avancée repliée dans Réglages. Le partage d'amis utilise un lien HTTPS et un QR identiques : `https://alexmalfr.github.io/hamigo/?invite=…`. GitHub Pages sert une page statique d'ouverture de l'app ; Android App Links associe le domaine à sa signature. La page indique de demander l'APK à l'ami si Hamigo n'est pas installé, car sa release demeure privée. Aucune progression n'est stockée sur cette page et aucun serveur applicatif n'est ajouté.

« Partager ma progression » produit deux images différentes : un carnet personnel avec graphique quotidien dans Moi, et un classement d'équipe avec barres des XP hebdomadaires, totaux de tous les équipiers et graphique personnel dans Équipe. L'invitation peut également se partager sous forme d'image QR via le menu Android.

## Apprentissage et côté ludique

Le parcours passe de 56 à **94 leçons**, dans 21 chapitres, avec **826 exercices rédigés**. Les identifiants historiques sont conservés. Le chapitre Morse contient 14 leçons : alphabet complet, chiffres, ponctuation, rythme, écoute, composition et transmissions de groupes. Les maths, RLC, spectres RF, antennes/lignes, numérique et méthodes de travail ont aussi des chapitres d'approfondissement.

Les séances conservent les questions enseignées, mélangent leur ordre et les positions des réponses, et ajoutent jusqu'à deux variantes des concepts effectivement couverts. Le catalogue procédural possède **5 188 variantes** récupérables en répétition espacée ; il comprend les calculs radio et des exercices interactifs. Les questions Exam1 restent une banque séparée de 2 961 questions, dont 2 950 dans le mélange courant après les exclusions documentées.

Les cours utilisent treize formats : QCM, vrai/faux, texte à compléter, choix multiples, liaisons, ordre, saisie numérique, résistances, estimation, binaire, tracés d'ondes, écoute Morse et composition Morse. Le cadran de fréquence et les flashcards complètent les formats de révision. Chaque question porte une consigne de manipulation explicite et une couleur associée à son format. Les liaisons dessinent les connexions. Les réponses et corrections font intervenir Pico, avec huit expressions et six poses, dont les attitudes goofy, danse et saut.

Une première réponse juste vaut 3 XP, une erreur 1 XP, au plus une attribution par question/jour. La première validation d'une leçon donne 6 XP ; les objectifs proposés sont 20, 30, 60 et 100 XP. Une leçon exige **80 % au premier essai** et aucune erreur restante. Une séance ratée suivie de corrections ne débloque pas automatiquement la suite. Les explications viennent à l'écran après la réponse et le résultat propose de reprendre le cours lorsque le seuil n'est pas atteint.

Les rappels quotidiens ont neuf variantes par contexte : première série, série à continuer, reprise après interruption, objectif en cours et objectif atteint. Ils utilisent une illustration colorée, Pico dans une expression adaptée, un avatar et le modèle Android BigPictureStyle. Android conserve la présentation extérieure et les commandes de la notification.

## Ressources, défis et densité

Les résistances montrent leurs anneaux et des pastilles de couleur. Le Morse utilise des points et traits agrandis, alignés, et l'écoute. Chaque fiche permet une révision normale ou en ordre aléatoire. Six outils sont présents selon le thème : code des résistances à quatre/cinq anneaux dans les deux sens, associations série/parallèle, texte ↔ Morse avec audio, rapports ↔ dB, loi d'Ohm et fréquence ↔ longueur d'onde. Les outils s'ouvrent à la demande pour laisser les repères visibles.

Défis comporte trois cartes distinctes : examen blanc, labo des calculs visible près du haut, et mix sur mesure. Le mix propose 10/20/40/80/150 questions, puis « Nombre libre » qui ouvre un dialogue de saisie de 1 à 1 000. Ses thèmes sont dans une section repliable interne, avec sélections globales Réglementation/Technique et sous-thèmes compacts. La recherche des thèmes est retirée.

Les marges, espacements, titres et cartes ont été resserrés dans l'ensemble de l'app, avec des cibles tactiles conservées à 48 dp. Le CTA des cartes mémo a disparu du parcours. Les captures Android couvrent les cinq onglets, réglages, accueil, cours, toutes les fiches/outils, tous les formats et corrections, résultats, équipe et QR, images partagées, notifications et les 48 combinaisons de Pico. Voir [VALIDATION.md](VALIDATION.md) pour les résultats de livraison.

## Retour critique : les prochaines améliorations utiles

1. **Mesurer la maîtrise par notion.** Le SRS est aujourd'hui attaché à une question. Relier les variantes d'un même concept permettrait de choisir une difficulté pertinente et de distinguer les connaissances transférables de la mémorisation d'un énoncé. Un bilan des erreurs au premier essai serait plus utile que le seul taux global incluant les corrections.
2. **Transformer davantage les cours en démonstrations.** Les widgets sont variés, mais plusieurs cours restent des paragraphes longs. Des circuits manipulables, des animations de phase/résonance et un atelier d'écoute Morse à vitesse réglable renforceraient l'intuition.
3. **Faire une vraie session de calibration à deux.** Les tests vérifient les règles, pas le ressenti : observer deux joueurs durant une semaine permettrait de régler l'XP, la durée et le nombre de variantes, puis d'éprouver la synchronisation avec deux comptes et deux appareils réellement utilisés hors ligne. L'autorisation OAuth personnelle reste un acte volontaire à effectuer dans l'app.
4. **Faire relire les contenus par un opérateur formateur.** Les contrôles de cohérence et la revue de calculs ne remplacent pas une lecture pédagogique de toutes les questions communautaires et de chaque nouvel approfondissement.
5. **Chiffrer la sauvegarde si l'usage s'élargit.** Un Gist secret est accessible avec son URL. Un chiffrement avec une méthode explicite de récupération serait l'étape suivante si l'on stocke des informations plus personnelles ou partage Hamigo au-delà du petit groupe prévu.

## Amis sauvegardés et liens Gists — 5 octobre 2026

La sauvegarde GitHub et l'export manuel incluent désormais les amis. La reconnexion sur une installation vide restaure l'équipe. Les relations se fusionnent par identifiant de Gist ; les suppressions horodatées empêchent les anciennes sauvegardes de réintroduire un ami retiré. Une actualisation de ses statistiques ne vaut pas nouvel ajout. Les anciens exports sans relations restent compatibles et préservent les amis locaux.

Les réglages permettent d'ouvrir ses Gists social et de sauvegarde dans les options manuelles repliées. Le menu d'un équipier permet d'ouvrir son Gist social ou de le retirer. Les URLs sont validées et reconstruites sur le domaine Gist ; afficher les liens ne provoque aucune découverte réseau. Le [schéma social et les règles de conflit](SOCIAL.md) détaillent le stockage, les invitations HTTPS/QR et leur caractère à sens unique.

## Photos GitHub et demandes réciproques — 5 octobre 2026

Les photos GitHub s'affichent en cercle à côté des équipiers et sur Moi, Équipe et les réglages pour le compte connecté. Le menu d'un ami ouvre son profil GitHub et son Gist social. Le retrait apparaît en rouge et ouvre une confirmation avec annulation.

Ouvrir une invitation connecté permet d'ajouter l'ami et de demander l'ajout en retour, ou d'ajouter seulement. Une demande est un commentaire GitHub sur le Gist social du destinataire, publié uniquement sur une action explicite. Celui-ci voit une pastille avec le nombre de demandes sur l'onglet Équipe et une section Accepter/Ignorer. L'acceptation sauvegarde la relation et la décision, puis publie un accusé que l'expéditeur peut lire. Un ami déjà présent dispose aussi de cette action dans son menu.

Les auteurs et propriétaires sont vérifiés par l'API GitHub. Les UUID et états persistants évitent les doublons après interruption ; les demandes importées ne repartent jamais seules. Les demandes expirent après trente jours. Les commentaires sont accessibles aux personnes possédant le lien du Gist ; ce mécanisme repose sur les actualisations de Hamigo, sans notification push ni serveur dédié. Voir [SOCIAL.md](SOCIAL.md) et [VALIDATION.md](VALIDATION.md).

## Navigation, Équipe et graphique vide — 5 octobre 2026

Le bouton Parcours est orange lorsqu'il est sélectionné et turquoise autrement. Son ombre est élargie, celle du bord supérieur suit la découpe avec un contraste renforcé. Les deux raccords de la découpe sont arrondis ; le contenu reste visible derrière le vrai espace transparent et la hauteur de la barre ne change pas.

Équipe présente un compte compact avec actualisation et réglages dépliables, puis Invitations, Demandes reçues/envoyées, Classement hebdomadaire avec partage, et Tes équipiers. Les détails de synchronisation dépliés évitent de répéter le compte et le bouton d'actualisation. Les demandes, photos et menus amis sont conservés.

Moi affiche « Pas de progressions cette semaine. » en gris au centre du graphique pour chaque tranche de sept jours sans XP, y compris dans l'historique.

## Calendrier et bilan personnel

Moi ajoute un calendrier mensuel au-dessus du graphique, avec navigation dans tout l'historique. Les cercles se remplissent depuis le centre proportionnellement aux XP rapportés à l'objectif actuel ; aujourd'hui est cerclé d'orange et les dates futures restent discrètes. L'app ne prétend pas conserver les anciens objectifs, qui ne sont pas historisés.

Le graphique hebdomadaire montre l'objectif journalier par une ligne orange pointillée. Le Bilan d'apprentissage présente les statistiques dans quatre tuiles et Mieux retenir adopte l'ampoule et le fond jaune des astuces Mémo. Partager mon bilan rejoint la carte niveau/XP, et tous les boutons de partage d'image portent une icône de partage.

L'en-tête de Parcours reste fixe au scroll ; son bloc série/XP mène à Moi avec une petite flèche. Dans Équipe et les réglages, la dernière synchronisation est le sous-titre de Synchronisation automatique, et le bouton des données/fréquence est resserré.

## Animations de retour et flashcards — 5 octobre 2026, 0.21

- Le retour prédictif d’une fiche Mémo anime les dimensions réelles de son cadre, ses arrondis et le passage du contenu détaillé à la ligne exacte de la bibliothèque. Les glyphes ne sont plus écrasés verticalement ; le titre rejoint sa taille dans la liste. La recherche et le scroll sont conservés lors d’une annulation ; un retour validé retrouve sa ligne même hors écran.
- Les paramètres se rangent vers le bouton Réglages en haut à droite de Moi, aussi via leur flèche de retour. Les séances nécessitant une confirmation utilisent un retour classique sans déplacer la page pendant le geste.
- La calculatrice se réduit vers les coordonnées réelles de son bouton sur Mémo et dans les questions. Fond, taille et position évoluent ensemble ; le calcul et le mode DEG/RAD restent disponibles après réouverture. Le clavier et les barres système restent pris en compte.
- Les flashcards montrent la question au recto et la réponse avec son explication au verso, avec ombre et rotation horizontale en perspective. Les deux faces restent consultables librement. Après la première révélation, les boutons À revoir, Difficile, Bien et Facile restent disponibles côté question aussi, avec une hauteur accrue et des fonds rouge, orange, vert et bleu.

- Le raccord d’ombre de la navbar suit désormais exclusivement son contour arrondi. La seconde ombre radiale découpée au bord supérieur créait deux coupures rectangulaires autour de Parcours ; elle est supprimée, avec contrôle de continuité par pixels.

- La carte de compte GitHub sur Équipe utilise 8 dp de marge verticale au lieu de 16, 4 dp entre ses rangées au lieu de 12 et un avatar de 36 dp. Ses contrôles dépliés n’ajoutent plus une seconde carte avec un second jeu de marges. Les contrôles communs dans les paramètres sont également resserrés.
- Une ombre apparaît progressivement sous les en-têtes fixes de Parcours, de la bibliothèque Mémo et des fiches une fois leur marge supérieure dépassée ; elle disparaît au retour en haut. L’ombre n’affecte ni les dimensions ni la position du contenu.
- Les en-têtes et leur ombre couvrent toute la largeur du viewport, marges latérales comprises. La portée de 10 dp et la densité utilisent les mêmes paramètres que le contour de la navbar. Le cercle Parcours a son propre halo visible sur toute sa circonférence, dessiné avant son découpage circulaire.

### Révision de la 0.21 : en-tête Moi

La photo, le titre, le sous-titre et le bouton Paramètres restent fixes pendant le défilement de Moi. L’ombre pleine largeur apparaît lorsqu’ils se détachent, avec les mêmes paramètres que les autres en-têtes. Cette révision a été construite séparément des widgets, livrée sous le numéro 0.21 demandé et installée sans effacer les données.

## Widgets et report des rappels — 5 octobre 2026, 0.23

- Trois widgets dans le sélecteur du launcher : **Série** (Pico, compteur et cercles de la semaine), **Objectif du jour** (XP, reste à gagner et jauge circulaire) et **Cette semaine** (XP, jours actifs et histogramme avec objectif pointillé). Chaque widget ouvre Parcours au toucher.
- Tous sont redimensionnables horizontalement et verticalement. Les formats compacts gardent l’essentiel sans tronquer les valeurs ; les formats étroits/hauts et larges affichent davantage de visuels. Android 12+ utilise quatre seuils de layouts responsive ; Android 8–11 utilise la taille transmise par le launcher. Textes et boutons restent des vues natives accessibles, avec descriptions des données dessinées.
- Mise à jour locale après réponses, fin de leçon, changement d’objectif, import ou fusion GitHub, avec regroupement des écritures et rendu hors du thread des questions. Le launcher demande aussi des mises à jour toutes les 30 minutes ; une alarme recalcule les widgets au changement de jour. Les redémarrages et changements de fuseau/horaire relancent leur mise à jour. Aucune connexion réseau ni chargement de la banque de questions n’est nécessaire au rendu.
- L’action **Me rappeler plus tard** ferme la notification et affiche « Rappel reporté de 30 minutes. ». Un seul report est programmé, sans décaler l’horaire quotidien. Le contenu est recalculé à la livraison ; le report peut traverser minuit et est restauré après redémarrage.
- Atteindre l’objectif ou désactiver les rappels annule le report. Une notification d’un ancien jour ne crée pas de nouveau report, et un report expiré depuis plus de six heures est abandonné. L’alarme reste inexacte et Android peut retarder la livraison selon ses contraintes d’énergie. Le report est local à l’appareil et n’est pas exporté dans la sauvegarde GitHub.

Références de plateforme : [layouts responsive des widgets](https://developer.android.com/develop/ui/views/appwidgets/layouts), [mise à jour des widgets](https://developer.android.com/develop/ui/views/appwidgets/advanced).

## Finitions UI et refonte des widgets — 5 octobre 2026, 0.24

- Les graphes de Moi gardent leurs données et leur navigation historique, sans les paragraphes explicatifs sous le calendrier et l'histogramme. Le total d'XP de la période accompagne son titre ; l'objectif reste dessiné en pointillés et décrit pour l'accessibilité.
- L'avatar GitHub de Moi passe de 56 à 72 dp et descend de 16 dp lorsque l'en-tête est fixé, pour dépasser légèrement sous son bord. Une fine bordure couleur fond le détache du contenu.
- La découpe de Parcours passe de 6 à 3 dp, avec des épaules arrondies raccordées par des arcs tangents. Le bouton conserve un halo diffus plus léger que l'ombre de la barre ; son label descend légèrement. Parcours, Équipe et Moi gagnent 40 dp de respiration en bas.
- Les trois widgets ont quatre compositions réelles : compacte, horizontale, verticale et grande. Série utilise des tons chauds et ses jours de révision ; Objectif conserve une seule jauge avec Pico ; Cette semaine met le graphique en avant sur un fond turquoise sombre. Le contenu illustré et les textes s'adaptent à l'espace, avec des aperçus propres dans le sélecteur du launcher et des métriques natives accessibles.

### Correction de l'avatar — 0.25

La photo reste à sa place pendant le scroll : le fond et l'ombre du header s'arrêtent 8 dp au-dessus de son bas. Le débordement est une propriété fixe du layout, sans translation ni animation de la photo lorsque l'en-tête devient fixe.

## Finitions des pages et préférences de gameplay — 0.26

- Le calendrier de Moi ne répète plus la série affichée au-dessus ; son bouton calendrier revient directement au mois courant. L'ombre du header suit une seule silhouette, y compris la portion circulaire de l'avatar qui dépasse.
- Défis et Équipe rejoignent les trois autres pages principales avec un en-tête fixe et une ombre pleine largeur. Le titre et le sous-titre de l'équipe occupent deux lignes distinctes.
- Les chapitres complets reçoivent un sticker rond doré, dentelé, avec reflet, liseré, coche et ombre. Son angle et son léger décalage sont déterminés par l'identifiant du chapitre ; ils ne changent pas à chaque recomposition. Le texte dispose d'une colonne plus étroite, sans chevauchement.
- Les paramètres deviennent Profil & objectif, Compte & synchronisation, Gameplay, Rappels et Sauvegarde & application. Les liens vers nos Gists sont dans les détails du panneau GitHub, séparés de l'import/export manuel.
- Le Morse propose deux boutons ou un bouton unique basé sur la durée de l'appui. Le seuil réglable entre 150 et 600 ms distingue point et trait ; un démonstrateur permet d'essayer sans XP. Annuler un appui ne transmet rien, les actions d'accessibilité distinguent point/trait, et Lettre/Mot restent explicites. Questions et traducteur utilisent le même composant.
- Les réglages Morse sont sauvegardés et fusionnés avec les préférences horodatées. Une ancienne sauvegarde qui n'a pas ces clés conserve les valeurs locales ; les types et bornes sont validés avant import.
- La calculatrice conserve son fond de fenêtre transparent et ses marges de contenu défilent avec les touches : le clavier ne crée plus de bande crème fixe sous le viewport.
- `AGENTS.md` résume les habitudes de travail, sources, vérifications visuelles, protections des données et procédure de livraison pour les prochaines conversations.

## Aperçu Morse et placement des stickers — 0.27

- L'essai Morse à un bouton dans les paramètres reconnaît les pauses entre lettres et mots, affiche le code graphique et le texte décodé, et matérialise aussi une pause de fin de mot. Une lettre utilise trois unités de point, un mot sept ; l'unité vaut la moitié du seuil point/trait. Les durées sont indiquées dans l'essai. Effacer ou changer de réglage réinitialise cet essai sans affecter la progression. Les questions et le traducteur conservent leurs séparateurs explicites.
- Le sticker doré est agrandi à 68 dp et centré verticalement sur le bloc titre/description, qu'il peut chevaucher comme un véritable autocollant, ainsi que le chevron. Son inclinaison varie de 7 à 16 degrés dans les deux sens, avec de petits décalages horizontaux et verticaux ; ces variations sont stables par chapitre. Sa couche superposée ne contribue pas à la mesure de la carte. Le titre conserve toute sa largeur ; seule la description réserve un espace au badge. Son interligne passe explicitement à 16 sp pour une taille de texte de 12 sp.
- Le contenu, les identifiants des cours et la sauvegarde des joueurs sont inchangés. Lecture seule du parcours : il contient déjà la composition de DE, CQ, 73, F4ABC, F6KGL et CQ DE. L'évolution du parcours et sa migration sont reportées à une demande ultérieure.

Référence pour les proportions temporelles : [UIT-R M.1677-1, annexe 1, §2](https://www.itu.int/dms_pubrec/itu-r/rec/m/R-REC-M.1677-1-200910-I!!PDF-E.pdf).

## Scanner les invitations et tirer pour actualiser — 0.28

- Équipe → Ajouter propose un lecteur QR avec caméra intégrée, cadre, fermeture et lampe si disponible. Autorisation demandée seulement à l'ouverture ; lien toujours disponible et accès aux réglages après refus. Décodage local avec ZXing Android Embedded 4.3.0, limité aux QR ; aucune image enregistrée ou transmise. Validation du lien puis confirmation habituelle avant ajout et demande réciproque.
- Tirer la liste Équipe vers le bas relance l'actualisation manuelle complète. Indicateur lié au travail social, regroupement des appels concurrents et remise à zéro dans un bloc `finally` en cas d'annulation.
- Documentation de la limite des notifications par mail des commentaires Gist : aucune désactivation automatique revendiquée, scope OAuth inchangé faute d'API publique de mise en silence d'un Gist.

## Premier lancement en quatre étapes — 0.29

- Onboarding plein écran : pseudo, objectif quotidien (20/30/60/100 XP), rappel quotidien puis connexion GitHub facultative. Quatre barres segmentées indiquent l'avancement ; Pico est agrandi et animé, avec une expression et une pose différentes par étape. Les transitions vont dans le sens de la navigation.
- Le rappel propose une heure via le sélecteur Android et demande `POST_NOTIFICATIONS` à l'appui sur Activer. L'activation attend l'autorisation ; refuser ou passer permet de continuer sans rappel. Une heure non confirmée reste dans le brouillon, sans modifier un rappel actif.
- Retour à l'étape précédente, clavier pris en compte, texte défilable et actions fixes accessibles. La taille de Pico s'adapte aux grandes polices et les petits textes ont un interligne explicite ; les cartes d'objectif sont de hauteur égale par ligne.
- Étape et brouillon conservés localement lors d'une interruption ; fin sur Parcours sans compte ou ouverture de GitHub depuis Équipe. Les anciens profils marqués `welcomed` ne repassent pas par cet accueil, et aucune progression n'est réinitialisée.
- Clarification documentée des mails Gist : le manque d'API concerne aussi un appel séparé après création. Vérifications en lecture seule ; scopes OAuth inchangés.

## Présentation publique du projet — 0.30

- README réorganisé autour de l'installation, des fonctionnalités et de huit captures de démonstration. Les anciennes notes détaillées sont conservées dans `APP-DETAILS.md`.
- Avertissement en tête : « Vibe codée avec ❤️ par @AlexMalfr », développement avec Codex, auteur débutant en RF et risque d'erreurs pédagogiques.
- Lien vers le dépôt GitHub ajouté dans Paramètres → Sources & version. Documentation adaptée au passage du dépôt en public décidé par l'utilisateur.

## Formulaire de contact — 0.31

- Bouton « Contacter / faire un retour » avec icône d'enveloppe tout en bas des paramètres, après les sources. Il ouvre le formulaire public Notion fourni par l'utilisateur dans le navigateur.
- Aucun champ prérempli, donnée personnelle jointe automatiquement ou formulaire intégré ; le contenu du formulaire reste administré dans Notion.

## Correctifs de saisie, navigation et schémas — 0.32

- Calculatrice : les touches travaillent à la position du curseur et remplacent la sélection. Les fonctions et parenthèses créent des groupes équilibrés ; la touche de fermeture traverse une parenthèse déjà présente. Carré et inverse utilisent la sélection ou le dernier opérande complet, y compris une fonction imbriquée.
- Équipe : les demandes envoyées acceptées disparaissent de la liste. Leur état de confirmation reste sauvegardé, sans nouvelles vérifications réseau répétées ni nouvel envoi.
- Défis : crayon centré dans le bouton de nombre personnalisé lorsqu'il n'y a pas de valeur. Navbar : les quatre labels secondaires sont rapprochés de leur icône ; Parcours garde son label plus bas.
- Paramètres rangés en sept catégories : Profil, Rappels & Objectif, Gameplay, Sauvegarde & Synchronisation, Sources, Version et Contact & Projet. Les liens de contact et du dépôt GitHub partagent le même style de bouton.
- Une séance terminée retrouve le retour prédictif sans confirmation et revient à son onglet d'origine, également via son bouton de retour. La confirmation reste présente pendant une séance en cours.
- Mémo : flèche de crête réellement au sommet de la sinusoïde ; repères de période/longueur d'onde alignés sur les sommets, niveau efficace calculé. Raccords des condensateurs/bobines, filtres RC et circuits LC corrigés ; bornes du transformateur et instruments de mesure clarifiées. Point d'alimentation des antennes séparé visuellement, porteuse supprimée en BLU en pointillés, transitions CW adoucies.
- Aucun changement des identifiants de cours/cartes ni remise à zéro de progression, d'amis ou de préférences.

## Widgets miniatures — 0.33

- Les trois widgets autorisent maintenant une réduction à une seule case de la grille du launcher : minima de redimensionnement abaissés à 40 × 40 dp, également avant Android 12.
- Une composition miniature conserve un libellé court et la valeur principale, avec une jauge pour les XP du jour. Les textes s'adaptent à la place disponible ; les grands nombres sont abrégés dans ce format, et restent détaillés pour l'accessibilité.
- Les formats existants réapparaissent en agrandissant le widget. La taille d'ajout initiale est conservée et toute la tuile ouvre Parcours.

## Widgets composés pour leur taille — 0.34

- Les trois widgets calculent leur composition depuis les dimensions réelles du launcher. Les bandes, colonnes, petites cases et grandes cartes répartissent autrement les statistiques, Pico et le graphique ; une grande colonne ne reste plus une miniature presque vide.
- Chaque modèle garde son identité : calendrier et palette chaude pour la Série, jauge turquoise pour l'Objectif, barres sur fond sombre pour la Semaine. Les petites cases conservent Pico et un graphique miniature. Les grandes cartes utilisent une hiérarchie sobre, avec moins de compteurs répétés, des titres moins imposants et une illustration mieux mise en scène.
- Espacement renforcé entre Pico, les cercles du calendrier et l'anneau de la jauge. Le pourcentage a une zone distincte sous la mascotte. La ligne d'objectif en pointillés reste devant les barres du graphique, y compris celles dépassant l'objectif.
- Statistiques et libellés en texte Android natif, ajustés à la place disponible et à la police système. Les données exactes restent accessibles quand les petits formats abrègent les grands nombres.
- Les variantes correspondent aux formats annoncés par le launcher ; les dimensions min/max servent de repli pour les orientations. Le budget d'images est partagé entre les variantes. La tuile ouvre toujours Parcours et les mises à jour de progression sont conservées.
- Un outil d'audit produit une planche de 121 tailles pour chaque widget, avec captures détaillées, formes extrêmes et plusieurs états de progression. Documentation et commandes dans `WIDGETS.md`.

## Recherche Mémo et chapitre de logique — 0.35

- La loupe de la bibliothèque Mémo place immédiatement le curseur dans la recherche et ouvre le clavier. Fermer le clavier par Retour ou toucher hors du champ replie une recherche vide ; un filtre saisi reste affiché. Revenir d’une fiche garde le filtre sans rouvrir le clavier.
- Nouveau chapitre « Binaire et portes logiques » avec quatre leçons : la leçon binaire existante est déplacée sans changement d’identité, puis trois leçons ajoutent 24 exercices. Les validations et anciennes questions sont conservées. Parcours : 22 chapitres, 97 leçons, 850 exercices.
- Mémo sépare les AOP, le binaire/les portes et la conversion/le traitement numérique. Les six fonctions utilisent les symboles rectangulaires CEI, également dans les leçons, exercices et flashcards. Un outil permet de manipuler les entrées et observer la sortie. Les cartes gardent leurs identifiants et échéances.
- Documentation des sources, de la réorganisation et des limites dans `LOGIC.md`.

## Portes logiques à formes distinctes — 0.36

- Les dessins de portes utilisent les formes demandées : ET en D, OU courbé, NON triangulaire, XOR avec une courbe supplémentaire. Les cercles d’inversion distinguent NON, NAND et NOR. Même représentation dans Mémo, le simulateur, les cours, les questions, corrections et flashcards.
- Les explications et exercices de reconnaissance suivent les formes. La convention rectangulaire CEI reste expliquée comme alternative, sans classement exclusif par pays. Identifiants, tables de vérité et progression conservés.
## Calculatrice et brouillon par question — 0.37

- Les touches C et effacement ont un fond rose et des symboles foncés. L’effacement retire toujours un caractère au clic ; un appui long efface tout comme C, avec action accessible.
- Un bouton Brouillon se trouve au-dessus de la calculatrice : notes au clavier avec sélection conservée, feuille quadrillée au doigt ou au stylet, annulation de trait, effacement et mode Stylet seul. L’entrée native prend en compte les points historiques, la pression et les contacts annulés. Les panneaux se replient vers leur bouton et restent utilisables avec clavier.
- Sur une même question, fermer/réouvrir conserve le calcul, les notes et les dessins. Passer à une autre question vide expression, résultat, erreur, mémoire Ans, texte et traits ; les choix de mode du brouillon sont conservés dans la séance. Le brouillon reste temporaire, hors progression/Gists.
- Détails et limites de la saisie stylet dans `SCRATCHPAD.md`.
## Lire les cours et réviser les notions étudiées — 0.38

- Chaque cours présente directement ses fiches Mémo associées, avec les lignes de la bibliothèque. Le menu ⋮ à droite ouvre une lecture seule sans CTA de questions, avant ou après validation. Retour d'une fiche vers le cours conserve sa position et son mode, avec le retour prédictif.
- Les sous-titres d'origine restent affichés pour les leçons terminées et conseillées. Une leçon terminée utilise une icône de rejeu à gauche et ✅ près de son titre. L'effet d'appui du texte de leçon est retiré.
- Les liens externes Mémo visent des paragraphes F6KGL, avec une table vérifiable pour les 97 leçons et les 40 fiches. Les sources primaires distinctes sont conservées ; les fiches transversales et le Morse ont des limites explicites dans `LESSON-RESOURCES.md`. La mention redondante des sources des paramètres disparaît des introductions.
- Défis propose Révisions aléatoires après Mix : 10, 20 ou 40 questions sur les cours déjà étudiés. Les rappels SRS dus les plus anciens sont prioritaires mais répartis parmi les autres questions ; la séance replanifie les réponses via le suivi habituel, sans valider automatiquement des leçons.
- Les quatre objectifs deviennent 30, 60, 120 et 240 XP/jour. Chaque joueur garde la même position de choix, avec des clés stables en préférences et dans la sauvegarde, sans migration ni réinitialisation.
- Les conversions d'anciens formats sont retirées : sauvegarde/résumé social v1, progression sans registre et relations sans horodatage. Le format actuel, ses événements, ses socles déjà sauvegardés et ses suppressions restent intacts. Une installation neuve initialise uniquement un registre vide.
- Import et fusion enregistrent les données dans une seule transaction locale : le rafraîchissement d'un écran ouvert ne peut plus remplacer la progression importée par l'état antérieur.

### Correctifs de la 0.38

- Les menus ⋮ des leçons partagent un bord droit fixe ; les icônes et textes des cours gardent leur indentation alternée et leur largeur précédente.
- Révisions reprend la file des rappels SRS de Parcours, y compris les flashcards Mémo et questions d'entraînement dues. Le compteur correspond donc aux notions à revoir de Parcours, même sans leçon terminée.
- La section et la séance s'appellent Révisions. Le nombre à revoir rejoint la fin du paragraphe entre parenthèses, sans ligne de label séparée.
- Révision demandée au même numéro de version : `-PhamigoVersionCommit=38`, commit de correction distinct, APK et release 0.38 remplacés avec la signature existante.

### Accord du nombre de jours — 0.38

- Les séries utilisent le même accord français partout : 0 jour, 1 jour, 2 jours. Correctif sur Parcours, Moi, Équipe, les récapitulatifs de séance, les images et textes partagés, les notifications illustrées et les widgets, y compris leur accessibilité.
- Les variantes de notification gardent des formulations correctes autour du compteur ; les jours actifs des widgets suivent également le singulier/pluriel. Aucun changement du calcul de série, des objectifs ou des données sauvegardées.

### Lecture des cours et respiration de Défis — 0.38

- Le bouton de départ dit simplement « À toi de jouer », sans annoncer une fourchette de défis. Un bouton audio à droite de Pico lit titre, résumé, paragraphes et formule avec le moteur TTS français du téléphone ; un second appui arrête la lecture. La lecture s'arrête en quittant le cours ou l'application et libère le moteur/focus audio.
- Défis retrouve le même espace de 56 dp sous le dernier contenu que les autres pages, ajouté à la compensation de la navbar.

## Rappels, objectifs et signalements — 0.42

- Paramètres : l’heure du rappel se choisit avec le sélecteur Android natif, suivant le format 12/24 h du téléphone. Sa confirmation enregistre directement l’heure, même lorsque le rappel est désactivé ; « Tester le rappel » est à droite, sans bouton de sauvegarde. L’avertissement sur les délais Android commence par une icône d’information cerclée.
- Moi et widgets : les jours ayant atteint l’objectif actuel sont dorés, avec un léger reflet statique et une coche dans les calendriers assez grands et les barres de Moi. Aucune légende supplémentaire. Les pointillés d’objectif restent au premier plan des barres. Les widgets libèrent l’espace des anciens CTA et conservent l’ouverture de Parcours sur toute leur surface.
- Pico montre cinq expressions/poses dans les widgets : première activité, série à poursuivre sans activité aujourd’hui, activité sous l’objectif, objectif atteint et reprise après interruption. Les notifications gardent leur variété quotidienne.
- Widgets : marges de sécurité renforcées près des bords pour les arrondis des launchers. Les graduations de la jauge verticale restent dans sa largeur et passent devant le remplissage.
- Les lettres de Ma/Me sont empilées dans les timelines horizontales des widgets ; les autres initiales restent alignées sur leur première ligne.
- Défis : Mix et Révisions utilisent le même sélecteur compact. Mix garde 10, 20, 40, 80, 150 et le crayon personnalisé. Révisions masque les valeurs supérieures à la banque étudiée et ajoute son nombre exact à la fin des choix s’il manque, sans doublon. « Questions : » reste sur la ligne si la place le permet, sinon au-dessus ; chaque ligne de choix se répartit alors sur toute la largeur, et un choix seul est centré aux formats très étroits. Le champ personnalisé n’apparaît qu’à l’appui sur le crayon et est borné à la banque disponible en Révisions. Le CTA dit simplement « Lancer les révisions », sans nombre ni précision redondante ; la valeur sélectionnée correspond à la longueur lancée.
- Équipe : la date de dernière synchro est à gauche d’Actualiser l’équipe, sans doublon sous Synchronisation automatique. Les paramètres conservent leur date dans le sous-titre.
- Signalements : drapeau en haut à droite des questions et bouton libellé tout en bas des fiches Mémo. Le formulaire reçoit en paramètres les identifiants de contenu et la version/environnement, sans données personnelles de progression. Notion ne les exploite pas encore automatiquement. Les questions Exam1 proposent d’abord le contact officiel du responsable de la banque, tout en laissant signaler un problème d’app à Hamigo. Détails : [FEEDBACK.md](FEEDBACK.md).

### Correction des releases 0.38 et 0.42 — 8 octobre 2026

- Heure du rappel enregistrée à la confirmation du sélecteur, test à droite. Cette interface est aussi reportée dans la release 0.38.
- Révisions indique les notions à revoir et le nombre total de questions disponibles dans les mêmes parenthèses, en 0.38 et 0.42.
- En 0.42, les URLs de signalement incluent aussi l'énoncé et le titre de séance. Le lien Exam1 de secours, utilisé sans client mail, conserve maintenant tous les paramètres ; le brouillon mail contient également le contexte. Les identifiants de questions restent inchangés.
- Commits des deux releases amendés et descendants rebasés ; numéros 38/42 conservés. Aucun APK 0.39–0.41 n'a été publié : ces commits avaient corrigé la 0.38 au même numéro. La règle d'amendement demandée pour les prochaines corrections est consignée dans `AGENTS.md`.

## Pico présente les questions — 0.43

- Les questions natives ont un énoncé plus grand lorsqu’il est court (24 sp, réduction progressive jusqu’à 16 sp pour limiter les lignes), aligné vers Pico et le centre de son boîtier, placé de façon variable à gauche ou à droite. Les longues questions passent sur toute la largeur avec Pico au-dessus, selon les lignes et la hauteur mesurées ; les phrases courtes gardent davantage d’espace avec lui. L’encart de type de question et les phrases d’encouragement répétitives disparaissent. Pico se place aussi au-dessus des flashcards et des illustrations Exam1 ; le flip et l’image originale agrandie sont conservés.
- Comparaisons sur huit types réels : haut/centre/bas à environ 30/70, puis haut/centre à 40/60. La version finale privilégie les réponses en haut et l’énoncé à environ 40 %, avec adaptation aux petits écrans et au clavier. Calculatrice et brouillon flottent sur fond transparent, en colonne ou en ligne si la hauteur manque ; la marge de fin de contenu permet de dégager toutes les réponses au scroll. Le bouton de validation garde sa position, tandis que le fond du footer se prolonge derrière la navigation système.
- Toucher Pico lance/arrête la lecture française de la question, indiquée par un petit haut-parleur discret près de lui ; son visage et sa silhouette s’animent pendant la parole. Le moteur est préparé au premier appui et hors du fil UI, sans lancement audio automatique. La lecture s’arrête au changement de question, à la sortie et en arrière-plan.
- Associations : appui ou glissement depuis les deux côtés, fil suivant le doigt, ancrages dessinés au même plan, textes centrés et numéros vers le milieu. Au reveal, chaque paire prend une variante de vert ou de rouge selon sa justesse. Les deux lignes d’aide/comptage sont retirées.
- Ordre : éléments glissables, poignée à six points, positions mises à jour et carte soulevée pendant le déplacement. Des actions accessibles permettent aussi de monter/descendre une étape ; annuler un geste restaure l’ordre précédent.
- Textes à trous : emplacement moins haut, réponses rectangulaires avec le même arrondi, glissement du bas de l’écran jusqu’à l’emplacement de l’énoncé et libellés longs conservés dans leur choix. Aucun ID de contenu, règle de score, sauvegarde ou migration modifié.

- Les introductions du Parcours ont également un Pico plus grand (124 dp, adapté aux écrans étroits), avec la même animation de bouche/silhouette pendant le TTS et le même alignement sur le boîtier. Ses expressions varient au fil du temps ; toucher Pico ou le bouton de lecture provoque une réaction discrète. Les flashcards ont un Pico plus grand, qui réagit aussi au retournement. Léger balancement, respiration et clignement des yeux ; les changements d’expression temporisés s’arrêtent en arrière-plan.
- La réduction de police tient compte de la hauteur disponible, en plus de la largeur. Sur les écrans étroits, les associations libèrent de la largeur pour les mots, tout en conservant les badges centraux et des ancrages séparés.
- Le repère audio repose sur un fond rond crème. L’ombre sous les pieds de Pico est réellement transparente : son opacité était rétablie par le helper de peinture. Correction dans le dessin partagé des écrans, widgets, rappels et images générées.

### Correctifs TTS et Exam1 au même numéro — 0.43

- Un appui sur Pico lance/arrête le TTS dans les questions **et les introductions**. Le haut-parleur devient Stop pendant la préparation/lecture et contrôle le même audio. Les grimaces sont réservées à une rafale d'au moins trois appuis rapides et disparaissent après 2,8 secondes ; aucune réaction de grimace au premier appui. Le rectangle gris d'appui disparaît.
- Bouche et silhouette parlent plus lentement, avec une amplitude légère. L'ombre reste dans un plan fixe et la retouche de bouche utilise le vrai blanc du visage. Une réserve intérieure protège l'antenne lors des inclinaisons et expressions.
- L'énoncé peut dépasser sa hauteur indicative : toute la question et ses réponses partagent un seul scroll. Les illustrations et les longues phrases grandissent sans scroll interne ni contenu coupé. Le champ numérique se dégage aussi des outils flottants à l'ouverture du clavier. Le footer projette son ombre vers le haut, sur toute sa largeur, avec les mêmes paramètres que la navbar.
- Aperçus Exam1 : suppression prudente des minuscules parasites neutres isolés, notamment les 20978 et 20003, avant recadrage. Les illustrations originales restent intactes et leur agrandissement flotte sur la page assombrie, avec pinch, déplacement, double appui pour restaurer et appui extérieur pour fermer. Une explication absente reste absente, sans message de remplacement.
- Reconnaissance automatique des formules dans les réponses et des égalités au milieu des énoncés textuels natifs. Fractions, indices, puissances et racines ont un rendu mathématique natif, sans corriger les distracteurs. Les notations ambiguës gardent leur texte et les équations uniquement présentes dans les PNG restent dans l'illustration ; aucune reprise manuelle de toute la banque ni OCR ajouté. Détails et limites : [QUESTION-UI.md](QUESTION-UI.md).

### Visage en couches, gestes et cache des avatars — toujours 0.43

- Pico utilise des couches distinctes pour les jambes, les bras, le boîtier, les yeux, la bouche et les joues. Les bras restent derrière le boîtier. Parole et yeux fermés remplacent réellement les traits concernés, sans rectangle de masquage ni bouche de repos résiduelle. Ce dessin commun sert aux écrans, widgets, notifications et images partagées.
- La bouche parle à un rythme intermédiaire (cycle de 720 ms) ; le mouvement léger du corps garde son cycle de 2,2 secondes et l'ombre reste fixe. Toucher l'énoncé textuel natif commande également sa lecture ; les images conservent leur agrandissement et les flashcards leur retournement.
- Les gestes d'agrandissement Exam1 sont mesurés dans le plan fixe de l'écran : pinch dans les deux sens, déplacement et double appui pour agrandir/restaurer. Un appui sur l'image visible, même zoomée au-delà de son rectangle initial, ne ferme plus l'overlay ; seul le fond extérieur ou Retour le ferme.
- Les avatars GitHub disposent d'un cache mémoire et d'un cache d'images sur disque, indépendant des directives HTTP de GitHub. Une photo conservée est affichée immédiatement pendant son éventuel rafraîchissement, au plus tôt après 24 heures ; un échec réseau conserve l'ancienne photo. Android peut libérer ce cache comme les autres données temporaires.
- Commit de la 0.43 amendé et APK remplacé au même numéro, avec la signature habituelle. Progression, amis et identifiants inchangés.

### Recadrage Exam1 et ombres des cartes — toujours 0.43

- La question 20081 contenait des pixels de papier légèrement différents du fond : l'inversion du compositing les prenait pour de l'encre et agrandissait le recadrage. Une tolérance prudente du papier corrige ce cas sans branche par identifiant ; l'original reste intact.
- Les cartes d'ordonnancement corrigées conservent leur teinte pastel avec un fond opaque, pour que leur propre ombre ne transparaisse plus en rectangle gris. Glissement et accessibilité conservés.

### Énoncés centrés et outils stables avec le clavier — toujours 0.43

- La zone de présentation retrouve sa vraie hauteur indicative de 40 % du viewport des questions, sans réduction du pourcentage sur petit écran ou à l'ouverture du clavier. Pico et l'énoncé sont centrés comme un groupe ; un long énoncé peut toujours grandir librement et toute la page conserve un scroll unique.
- Calculatrice et brouillon restent en colonne par défaut. Seule une fenêtre entière réellement courte les place en ligne : le clavier ne change plus leur orientation. Le fond derrière eux reste transparent, avec davantage d'espace sous leurs ombres et une réserve adaptée pour dégager les réponses et le champ numérique.
- Aucun changement de contenu, notation, progression ou identifiant. Cette correction est destinée à l'amendement de la 0.43 ; validation visuelle, résultats Android et remplacement effectif de la release restent à confirmer avant livraison.

## Sons, haptiques et fin de séance — 0.44

- Une palette originale de déclics mécaniques, cordes pincées et ressort accompagne boutons/onglets, sélections, gestes, réponses et réactions volontaires de Pico. Six samples courts préchargés, sans banque tierce ni bip générique ; les clics sont nettement plus discrets que les verdicts.
- Les vibrations varient en force et en durée : sélection douce, prise/pose d'une carte, réponse correcte/à corriger et petite progression de fin de séance. Pas de vibration sur chaque bouton ou scroll ; les événements rapprochés sont temporisés. Le volume média, le mute de l’app et les réglages tactiles Android sont respectés.
- Sons et Vibrations se règlent séparément côte à côte dans Gameplay. Une icône à droite du drapeau des questions contrôle le même réglage Sons. Ces choix sont conservés localement, sans changer le schéma des Gists ; les écoutes pédagogiques restent disponibles.
- Les bilans de toutes les séances partagent une explosion de confettis depuis le bas, avec un motif sonore/haptique de fin. Version plus sobre si la séance reste à consolider, présentation unique par séance, aucun blocage des boutons et respect des animations désactivées. Les brouillons d'examen ne révèlent pas leur justesse par le feedback.
- Morse : souffle très léger dans le tampon audio, précédant le premier point/trait de 180 ms pour amorcer la sortie ; timings des signaux et espaces conservés. Les voix et le Morse ont priorité sur les effets d'interface.
- Reprend aussi le centrage 40/60, la pile d’outils indépendante du clavier et les correctifs 20081/cartes d’ordre publiés dans la 0.43 amendée. Détails et limites : [INTERACTION-FEEDBACK.md](INTERACTION-FEEDBACK.md).
- La ligne heure/test du rappel s'adapte aux écrans étroits et à une police agrandie, avec deux lignes si nécessaire plutôt qu'une heure coupée verticalement.

### Confettis plus amples — amendement 0.44

- Morceaux agrandis, gerbes plus hautes et plus larges ; vol et rotation ralentis, durée de 4,2 secondes avec extinction douce. Le lancement reste accompagné du même son et du même motif tactile.

## Diagnostics et personnalisation des widgets — 0.45

- Les questions ont des retours tactiles adaptés à leurs gestes : crans/repères/butées et relâchement des sliders sans sons répétés, flip des flashcards, points/traits Morse, bits binaires et positions déplacées. Les indices ne dépendent jamais de la bonne réponse et les préférences tactiles sont respectées.
- Un code saisi dans la calculatrice ouvre des outils de diagnostics sur appareil : catalogue complet avec IDs, recherche et filtres, test d'une question précise, tour des formats, aperçus natifs et statistiques. Le mode persistant affiche ID/type/version en surimpression.
- Les séances du menu sont isolées ; une option persistante permet aussi de geler XP, validation et SRS des séances ordinaires. Un bac à sable client temporaire conserve ses données en mémoire, sans credentials personnels ni envoi GitHub. XP/série/cours peuvent être réglés, chaque cours coché arbitrairement ou tous marqués terminés. Le retour aux données réelles détruit les données fictives et reprend la synchro autorisée.
- Les trois widgets sont reconfigurables depuis le launcher : fond transparent facultatif, aperçu, texte clair/sombre pour le fond d'écran. Réglages locaux par instance, conservés au redimensionnement ; aspect par défaut inchangé.
- Aucun contenu pédagogique, identifiant ou schéma de sauvegarde modifié. Utilisation et limites : [DIAGNOSTICS.md](DIAGNOSTICS.md), [WIDGET-CONFIGURATION.md](WIDGET-CONFIGURATION.md), [INTERACTION-FEEDBACK.md](INTERACTION-FEEDBACK.md).

### Gameplay et aperçus corrigés — même 0.45

- Les questions de test et le bac à sable héritent aussi des préférences Gameplay, dont le mode Morse et son timing. Ces copies restent isolées des paramètres réels.
- L'aperçu « Équipe · deux amis » n'avait pas de registre de résultats Android pour son launcher de permission caméra et plantait à sa création. Le contexte diagnostic fournit désormais un registre local qui annule tous les lancements ; Équipe et les paramètres peuvent s'afficher sans activer les permissions ou les actions externes.

## Morse entendu pendant la composition — 0.46

- Le point/trait saisi est audible, et le manipulateur à un bouton sonne pendant l'appui : plus besoin de relire toute la transmission pour l'entendre. S'applique aux questions, aux diagnostics, au traducteur et à l'essai de Gameplay.
- Réglage « Écoute pendant la saisie » dans Gameplay → Saisie du Morse, activé par défaut et soumis au réglage Sons. Les écoutes pédagogiques explicitement demandées restent disponibles indépendamment de ces deux réglages.
- Une piste préparée, silencieuse au repos, évite de recréer la sortie à chaque appui. Arrêt au relâchement/annulation, au mute, à la sortie de l'écran et en arrière-plan ; priorité aux lectures et aux voix.
- Choix sonore local, aucune migration ni modification du schéma des Gists. Détails : [INTERACTION-FEEDBACK.md](INTERACTION-FEEDBACK.md).

## Identités, Morse et lecture vocale — 0.47

- Le rappel vrai/faux sur la pause entre lettres reste seulement dans la première leçon de groupes de lettres. Huit doublons sont retirés : 842 questions originales actives, avec leur historique conservé. L'ordre Point / Trait / Pause entre les mots ne révèle plus les durées dans les choix ; la correction les explique toujours.
- Chaque question originale du Parcours possède maintenant un UUID attribué une seule fois, et la banque est séparée des listes d'appartenance aux leçons. Réordonner ou déplacer du contenu ne change pas l'ID. Chapitres, leçons, Exam1, flashcards et variantes procédurales existantes gardent leurs identifiants.
- Migration des anciens IDs en local, à l'import et avant fusion GitHub : SRS et clés de récompense traduits, XP/événements/amis/préférences préservés. Schéma global 2 conservé, marqueur `questionIdsVersion: 1`. **Retrait demandé à la 0.52**, consigné dans AGENTS et protégé par Gradle ; UUID et registre permanent restent conservés. Détails : [QUESTION-IDENTITIES.md](QUESTION-IDENTITIES.md).
- TTS : préparation commune des points/traits, équations, fractions, racines, indices, puissances, lettres grecques, unités/préfixes et abréviations radio, avant découpage de la voix. Les distracteurs et le texte affiché ne sont pas corrigés. Pas de reprise manuelle par question ni d'OCR des images ; [règles et limites](SPEECH.md).
- Diagnostics : recherche dans tous les champs, dont choix, explications, paires, valeurs et unités. Un résultat peut combiner des mots venant de champs différents ; les anciens IDs du Parcours restent recherchables pendant la migration.
- Fréquence : une valeur affichée à la limite de tolérance est acceptée, notamment 147,05 MHz pour 147 ±0,05 MHz. Manipuler le réglage déclenche du souffle, puis une vraie voix TTS de plus en plus intelligible à l'approche de la fréquence demandée. Respect de Sons, du premier plan et de la priorité pédagogique ; aucune simulation en examen blanc.

### Introductions et correspondances Morse — amendement 0.47

- « Ponctuer sans confusion » distingue les signes littéraux des séparateurs transmis : `/` désignant une barre oblique reste un caractère normal, et les codes gardent leur représentation graphique. La même distinction est appliquée au composant commun, y compris hors de cette introduction.
- Les correspondances deviennent des phrases naturelles pour la voix : « Le caractère arobase se lit point trait trait point trait point ». Lettres, chiffres et groupes sont nommés ; tableaux de durée, préfixes SI et listes d'équations partagent les règles de préparation, sans correction des distracteurs.
- Chaque nouveau caractère présenté dans une introduction possède son bouton d'écoute Morse. Toute la ligne est également touchable ; l'écoute prend la place de la voix du cours et s'arrête à la sortie ou en arrière-plan. Disponible aussi quand on relit l'introduction seule et lorsque les sons d'interface sont désactivés.
- Aucun contenu de question, UUID, sauvegarde ou règle de progression changé. Commit, tag, APK et notes de la 0.47 remplacés au même numéro ; retrait de la migration toujours prévu à la 0.52.

### Récepteur et catalogue Diagnostics — amendement 0.47, 11 octobre

- La question de test visible n'hérite plus du silence réservé à la séance située derrière le menu Diagnostics. La simulation Fréquence fonctionne dans le catalogue et le tour des types, avec Sons, arrêt à la sortie et priorité aux lectures pédagogiques conservés.
- Le catalogue affiche le chemin chapitre → leçon → position de chaque question du Parcours, dans la liste et son détail. Ces repères sont recherchables ; les UUID restent inchangés.
- Filtres explicites par banque avec effectifs, dont Exam’1 et ses illustrations. Sur petit écran, les filtres défilent horizontalement et le clavier ne masque plus la zone de résultats.
- Commit et APK 0.47 amendés ; la 0.48 est rebasée pour conserver ses optimisations et le téléchargement séparé d'Exam1.

### Réception continue — complément du 11 octobre

- La réception reste active après le premier réglage, sans arrêt temporisé au relâchement. Une lecture de Pico ou de Morse la suspend puis elle reprend seule ; validation, mute, sortie et arrière-plan arrêtent la piste.
- Le message français est allongé et bouclé avec une courte pause. La voix est progressivement filtrée, saturée et modulée lorsqu'on s'éloigne, en plus du souffle.
- Le rééchantillonnage des longues synthèses ne déborde plus un entier lors du calcul des positions. Les samples restent bornés, et la boucle ne dépend plus d'un compteur qui pourrait déborder après une longue écoute.
