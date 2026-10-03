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
