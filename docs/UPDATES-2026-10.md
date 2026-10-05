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
