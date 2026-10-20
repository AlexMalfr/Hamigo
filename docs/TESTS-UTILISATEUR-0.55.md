# Essais utilisateur de la grosse mise à jour 0.55

Il n’est pas nécessaire de tout faire d’une traite. Les priorités sont le tutoriel, la voix sur ton Samsung, le clavier et le déplacement des montages, puis quelques cours que tu ne connais pas encore. Pour un défaut, note la page, le geste, l’orientation, ce que tu attendais et ce qui s’est passé. Une capture et l’ID de question sont particulièrement utiles.

## Tester sans toucher à ta progression

Ouvre la calculatrice depuis Mémo ou un cours, saisis **738873882026**, puis `=`. Dans Diagnostics, les boutons « Tester », les Intros et les Aperçus utilisent un profil fictif en mémoire. Les questions personnalisées y sont également isolées. N’active pas le bac à sable global ni un effacement pour suivre cette liste. Les valeurs de l’onglet Compétences sont lues sur ton vrai profil, sans être modifiées par l’inspecteur.

Le microphone des exemples Diagnostics utilise maintenant la même permission et le même service Android que les véritables questions, sans enregistrer de progression. Pour l’essai acoustique, tu peux ouvrir un exemple oral dans Diagnostics ou une leçon OTAN dans le Parcours. Tu peux quitter la séance avant de valider une réponse si tu veux seulement tester la transcription. Un nouveau cours dans le vrai Parcours enregistre normalement tes réponses et tes XP.

## 1. Parcours et navigation

- [ ] Vérifier le nom du prochain cours et le bouton « Commencer le cours » dans la carte de mission.
- [ ] Descendre loin, puis toucher la flèche : le prochain cours doit arriver dans la partie centrale de l’écran, dégagé du header et de la barre du bas.
- [ ] Lorsque ce cours est déjà visible et que la liste est presque en haut, la flèche est cachée. Plus bas, elle permet de remonter en haut ; sa rotation reste fluide lors des changements de sens.
- [ ] Déplier un chapitre, descendre jusqu’à un autre, puis l’ouvrir. Le premier se ferme ; le nouveau doit venir en douceur dans la partie centrale de l’écran. Répéter avec un chapitre nettement plus haut et un nettement plus bas.
- [ ] Replier le chapitre ouvert : la liste ne doit pas faire un saut qui fait perdre le contexte.
- [ ] Ouvrir une introduction puis revenir : retrouver le chapitre et la position de lecture du Parcours.
- [ ] Vérifier le petit séparateur avant la section 1, puis les séparateurs et titres des sections suivantes.
- [ ] Aller tout en bas : Pico avec ses lunettes doit faire coucou, sans label inutile ni ombre coupée.
- [ ] Essayer une ouverture de chapitre en paysage et avec une police Android agrandie.

## 2. Tutoriel complet, depuis Diagnostics → Aperçus → Guide Hamigo

- [ ] Première étape : le cours d’accueil est bas, accessible au pouce ; la popup est en haut et présente Parcours, l’ordre des cours et le certificat.
- [ ] La première étape assombrit la page autour d’une cible assez haute. Le cours éclairé reste réellement cliquable ; le menu à trois points du cours ne s’ouvre pas. Les étapes suivantes laissent la page claire, puis la popup finale possède un léger voile.
- [ ] « Suivant » est à droite, « Passer le tutoriel » est rouge. Essayer une fois le passage d’étape, puis recommencer le guide pour suivre le chemin normal.
- [ ] Ouvrir le cours : la petite aide « Lis, puis à toi de jouer » avec son Pico reste fixe en bas, centrée en portrait et alignée à droite en paysage. Aucun outil flottant ne se trouve dans l’introduction.
- [ ] Faire défiler l’introduction jusqu’au dernier paragraphe : tout doit pouvoir se lire au-dessus de l’aide. Tester aussi une police agrandie et le paysage.
- [ ] Toucher Pico pour écouter, puis arrêter. Aucun son ne doit partir simplement parce qu’une étape s’affiche.
- [ ] Lancer « À toi de jouer » : aucune sollicitation du tutoriel pendant les questions.
- [ ] Faire Retour pendant l’introduction guidée ou les premières questions : demander confirmation avant de quitter le tutoriel, sans animation de sortie prédictive trompeuse. Annuler conserve exactement l’étape ; confirmer revient au Parcours sans valider une séance inachevée.
- [ ] Finir les questions et lire les corrections : le guide reprend sur le bilan et indique le retour au Parcours.
- [ ] Revenir au Parcours : la popup explique le retour et invite à Défis. Le clic sur l’onglet demandé passe immédiatement à la suite.
- [ ] Sur Défis, changer un nombre de questions ou déplier les thèmes : ces interactions locales restent possibles. Essayer d’ouvrir l’historique ou une séance : un message indique de terminer le tutoriel, sans quitter la page.
- [ ] Sur Mémo, faire défiler et rechercher une fiche. Ouvrir une fiche ou un lien extérieur doit être bloqué pendant cette étape.
- [ ] Sur Équipe, déplier les informations de synchronisation ; essayer d’ouvrir un profil, une invitation ou une connexion. Les actions qui quittent la page doivent être bloquées.
- [ ] Sur Moi, consulter l’activité ; essayer les réglages. Seuls l’onglet courant et l’onglet demandé pour la suite sont disponibles.
- [ ] Revenir au Parcours : grand Pico, encouragement final, léger voile et bouton « Commencer », sans « Suivant » ni « Passer le tutoriel ». Le chapitre contenant le prochain cours est ouvert.
- [ ] Fermer la simulation : les vrais XP, la progression et le tutoriel personnel n’ont pas changé.

## 3. Réponses orales sur le Samsung

Réglages → Gameplay → Réponse vocale propose « Maintenir pour parler » et « Appuyer pour démarrer ». Le premier est le défaut. Pour trouver un exercice, ouvre un cours d’alphabet international ou Diagnostics → Types → Réponse orale ou écrite. Les deux utilisent la même permission et le même service de reconnaissance ; les séances Diagnostics ne modifient pas la progression. Hamigo ne conserve pas l’audio.

- [ ] Dans l’accueil, essayer le micro ou passer cette étape. À la première question orale, une permission manquante est demandée à l’ouverture ; après un refus, une phrase explique le problème et le lien de réglages reste disponible, ainsi que le clavier.
- [ ] Maintenir le bouton, attendre le passage de « Préparation » à « Écoute », dire un mot OTAN, puis relâcher. Le texte doit arriver dans le champ avant validation.
- [ ] Essayer une séquence de plusieurs mots et chiffres ; relire le champ. Tester « alpha », « juliette », « whisky », « x ray » : l’écriture attendue est Alfa, Juliett, Whiskey, X-ray.
- [ ] Dire volontairement un autre mot OTAN : la transcription ne doit pas inventer la bonne lettre attendue par la question.
- [ ] Essayer à voix normale, doucement, puis en chuchotant, à distance comparable du micro. Noter les mots ratés, les erreurs affichées et si les ondes réagissent ; cela permet de distinguer capture et reconnaissance.
- [ ] Vérifier couleur d’écoute, respiration du bouton et ondes au-dessus. Le bouton doit rester centré, assez haut et plus gros que le clavier.
- [ ] Relâcher avant que le service soit prêt : aucun enregistrement ne doit commencer ensuite tout seul.
- [ ] Essayer deux réponses successives, une écoute sans parole, puis un passage de l’app en arrière-plan : le micro doit se libérer et pouvoir repartir.
- [ ] Passer au mode « Appuyer pour démarrer » : un appui démarre, le second termine. Revenir au mode maintenu et vérifier que le choix persiste après relance.
- [ ] Refuser une fois l’autorisation, ou couper le réseau : une explication doit apparaître et le clavier rester utilisable. Le fonctionnement sans réseau dépend du service vocal et de sa langue installée.

## 4. Clavier, outils et autres formes de questions

Diagnostics → Types possède une recherche et des exemples par variante d’interface. « Voir les questions » donne les vraies questions correspondantes et leur leçon. « Tester un exemple » lance une séance isolée.

- [ ] Réponse écrite : une courte réponse garde une ligne ; une réponse développée dispose de plusieurs lignes.
- [ ] Dans une réponse orale, ouvrir le clavier à gauche du micro : champ, texte saisi et bouton Vérifier restent accessibles, sans déplacement des outils à droite.
- [ ] Faire ce test avec une réponse longue, en portrait puis paysage. Fermer et rouvrir le clavier ; faire défiler jusqu’à la correction.
- [ ] Ouvrir calculatrice et brouillon dans Mémo, une question de cours et un examen blanc. Vérifier qu’ils restent utilisables et ne masquent pas durablement une réponse.
- [ ] Passer la calculatrice en paysage : expression en haut à gauche, résultat et ses actions en haut à droite, fonctions et pavé numérique dessous. Calculer `2+3`, essayer `sin(30)` en DEG et insérer le résultat depuis l’en-tête. Réouvrir et vérifier l’expression et le mode DEG/RAD ; essayer aussi avec une police agrandie.
- [ ] **Montage** : glisser depuis le texte, le bord et l’icône d’une carte, sans appui long. Les options utilisées quittent la réserve ; les options restantes sont centrées.
- [ ] Toucher un emplacement pour choisir sans glisser ; retirer ou remplacer un composant. Essayer une mauvaise place, puis corriger après vérification.
- [ ] Comparer les trois montages : appareils de mesure, maquette DEL en série, synoptique de réception.
- [ ] **Réponses par ligne** : retrouver B/D/V en texte dans « Épeler un groupe nouveau », puis choisir les relations dans « Choisir avant de calculer ». Chaque ligne a sa propre réponse ; aucune sélection ne doit répondre à une autre ligne.
- [ ] Essayer aussi la variante mixte dans Diagnostics. Laisser une ligne vide, puis mettre une seule erreur : la validation doit attendre toutes les lignes et la correction doit distinguer chaque ligne.
- [ ] **SSTV** : chercher SSTV dans Types. Comparer inclinaison et lignes manquantes ; les deux mires sont des simulations différentes à interpréter.
- [ ] **Morse** : écouter/caractère, composer, écouter puis écrire, écouter puis épeler à l’oral, et résumer un échange complet. Vérifier écoute/arrêt, séparations et corrections graphiques.
- [ ] Associations et ordre : recommencer le même exemple ; les positions changent et la bonne correction conserve son sens. Essayer aussi les alternatives aux gestes.
- [ ] Images Exam1 : agrandir, zoomer puis dézoomer, déplacer et fermer par un appui extérieur. Une image agrandie reste touchable au-delà de son ancien rectangle.
- [ ] Toucher Pico et l’énoncé pour le TTS ; plusieurs appuis rapides provoquent seulement une réaction temporaire. Tester son arrêt lors d’un changement d’écran.

## 5. Contenu et rythme des cours

Il n’est pas nécessaire de tout relire pour faire un retour utile. Choisir deux ou trois notions nouvelles et noter précisément le premier terme ou raisonnement qui n’a pas été expliqué.

- [ ] Relire l’accueil : comprend-on où apprendre, retrouver une formule, utiliser les flashcards, réviser, signaler un problème et choisir les cours du certificat ?
- [ ] Essayer une suite de bases : fractions/décimaux → proportions → unités/préfixes → lire une formule → application d’Ohm. Les introductions doivent enseigner ce que les questions utilisent.
- [ ] Comparer un exercice direct, une erreur fréquente et un choix de méthode. Les questions ajoutées doivent apporter un vrai raisonnement, pas seulement changer les nombres.
- [ ] Revenir plus loin sur énergie/durée, mesures, dB/bruit, logique ou antennes : voir si le rappel est compréhensible après d’autres sujets.
- [ ] Lire les deux cours pratiques d’examen : inscription, centre, convocation, identité, déroulement, calculatrice et brouillon. Les consignes propres au centre doivent être distinguées des règles générales.
- [ ] Goûter aux explorations : réception SDR, SSTV, ISS/satellites, POTA/SOTA, station portable et station automatique. Les activités doivent donner une idée concrète de ce qu’on prépare et de ce qu’on observe.
- [ ] Noter une séance ressentie comme trop longue ou trop courte, avec son titre. Une question plus exigeante ne prend pas le même temps qu’un QCM.
- [ ] En cas de question ambiguë, utiliser le drapeau et conserver l’ID. Les métadonnées « Origine » dans Diagnostics distinguent l’appui à la rédaction native des questions importées Exam1. Les liens documentaires génériques ne sont plus affichés dans les corrections.

## 6. Révisions, progression et Moi

- [ ] Après un cours réel, retrouver les notions étudiées dans Défis → Révisions. Les questions propres à l’utilisation de Hamigo ne doivent pas revenir dans le SRS.
- [ ] Après des erreurs, vérifier que les rappels varient dans la notion ; une petite part peut reprendre une compétence voisine déjà travaillée. Un cours jamais étudié ne doit pas apparaître simplement parce qu’il appartient à la même famille.
- [ ] Réussir une séance difficile seulement après corrections : elle ne doit pas être présentée comme acquise si le seuil du premier essai manque.
- [ ] Comparer avant/après une vraie leçon les deux barres Certificat/Exploration de Moi. Elles peuvent évoluer différemment et ne représentent pas une note d’examen.
- [ ] Sur Moi, « Ton parcours » suit Bilan d’apprentissage ; l’astuce est tout en bas, après les barres.
- [ ] Vérifier que l’installation a conservé pseudo, amis, XP, leçons validées, paramètres et historique personnel. Les découpages équivalents d’anciens cours peuvent changer le nombre de leçons cochées sans donner d’XP supplémentaire.

## 7. Diagnostics et historique d’examens

- [ ] Questions : chercher un mot ou un ID, descendre dans les résultats, ouvrir une question puis revenir. Recherche, filtres et position doivent être conservés.
- [ ] Types : rechercher SSTV, oral, Morse ou montage ; tester les différentes compositions d’un même type et leur JSON brut.
- [ ] Compétences : ouvrir/replier les familles, rechercher une feuille, toucher sa ligne ou une famille pour voir les questions filtrées. Les parents résument les preuves des feuilles et n’affichent pas une maîtrise inventée.
- [ ] Intros : chercher une formule ou un titre, lire/copier le texte TTS, ouvrir le cours puis revenir. « Repères TTS » (ancien « À contrôler ») repère des notations suspectes ; il ne demande aucune action de ta part. Un éventuel repère demande une revue du texte ou de la prononciation, pas une validation de l’utilisateur ; il ne prouve pas à lui seul un défaut.
- [ ] Aperçus : essayer la recherche et les catégories. Ouvrir successivement **Historique · aucun examen**, **deux examens**, **trois examens**.
- [ ] État vide : explication claire, aucune fausse courbe. Deux examens : points présents, personnalisé encore fermé. Trois : statistiques et tendance descriptive, personnalisé proposé lorsque la banque est disponible.
- [ ] Ouvrir le transcript d’un examen simulé : date/heure, scores par partie, réponses, erreurs et explications doivent rester cohérents.
- [ ] Essayer aussi les aperçus d’accueil, Mémo, Défis, réglages, équipe vide et bilans de cours réussi/à consolider.
- [ ] Dans un examen blanc, essayer les introductions des deux épreuves en paysage ou avec une police agrandie : faire défiler jusqu’aux boutons, puis démarrer. Le chrono commence au lancement de l’épreuve.
- [ ] En usage réel, terminer un examen blanc seulement si tu souhaites alimenter ton historique : aucun verdict de justesse avant le bilan de l’épreuve.

## 8. Quelques régressions utiles

- [ ] Revenir depuis chaque onglet principal : Parcours ; depuis une page interne : son parent. Tester aussi un retour prédictif annulé.
- [ ] Mettre Sons/Vibrations sur arrêt, puis vérifier quiz et fin de séance. Une page qui se recompose ou défile ne doit pas jouer de sons.
- [ ] Désactiver les animations Android : les gestes restent possibles et le micro ne respire plus.
- [ ] Passer hors ligne après téléchargement Exam1 : cours, Mémo, banque et historique restent consultables.
- [ ] Vérifier une photo GitHub déjà chargée lors des changements d’onglet, les barres de progression d’un ami et son niveau XP. Aucun message réel à un ami n’est nécessaire pour ces essais.

Pour le retour, un format simple suffit : **titre/ID — geste — résultat observé — résultat attendu**, avec capture si le défaut est visuel. Les essais acoustiques sur ton téléphone complètent particulièrement les contrôles de code et d’émulateur.


## 9. Derniers retours : vérifications ciblées

- [ ] Ignorer un cours : lire la confirmation avant de valider. Il doit être grisé/barré, sauté par la flèche et compter dans les barres, sans gagner d’XP. Aucune réponse ni révision SRS ne doit être inventée.
- [ ] Retoucher le cours ignoré : Annuler ne change rien ; « Réintégrer et ouvrir » le rend disponible. Ignorer/désignorer ne rapporte rien ; suivre ensuite le cours donne les XP habituels des réponses et sa première vraie fin donne le bonus de cours.
- [ ] Appui long sur un chapitre : confirmer l’ignorance de ses cours, puis les réintégrer. Un chapitre commencé porte un contour blanc pointillé.
- [ ] Diagnostics → Types : lancer les présentations courantes, toutes les variantes, puis toutes les variantes d’un seul type. Chercher « signé » pour le bouton + / −, « SSTV », « lignes », « texte développé », « logique » et « Morse ». Revenir doit conserver recherche et défilement.
- [ ] Brouillon : dessin/texte, gomme, tailles/couleurs, undo/redo, annulation de « Tout effacer », déplacement vertical du papier. En paysage, déplacer la barre d’outils d’un côté à l’autre. Toucher en dehors dans la moitié basse ne ferme pas la fenêtre.
- [ ] Calculatrice paysage : expression à gauche, résultat/actions à droite. Tourner puis revenir : la page Mémo, la fiche, l’expression et la calculatrice ouverte doivent rester en place.
- [ ] Mémo : saisir dans un outil, en ouvrir un autre, revenir puis rouvrir la fiche. Retrouver valeur, mode, recherche et scroll. Essayer Ohm/dB, résistances, portes logiques et Morse.
- [ ] Autorisation micro : dans l’accueil, essayer ou passer. Si refus, une question orale peut redemander une fois ; après refus, explication et lien vers les autorisations système, clavier toujours disponible. Aucune boucle après rotation.
- [ ] Voix : appui maintenu par défaut, bascule dans Gameplay, sons d’ouverture/fermeture, Pico en écoute, ondes pleine largeur. Essayer voix normale et chuchotement. Vérifier Israël → X-ray, Eco → Echo, Joliette → Juliett, sous l eau → Zulu, sans correction arbitraire hors vocabulaire OTAN.
- [ ] Sons coupés : aucun son de micro ou de Pico. Tester l’easter egg depuis toute la zone d’énoncé et le 360° du Pico en bas du Parcours.
- [ ] Formulaire : drapeaux question/Mémo/introduction et Contact des réglages ouvrent le même Tally avec les bons champs préremplis. Changer Bug → Suggestion masque les détails de bug ; contact e-mail facultatif. N’envoyer une soumission que si tu souhaites la conserver dans ton compte Tally.
- [ ] Deux appareils connectés au même compte : réglages Sons/Vibrations, mode vocal et options Morse se retrouvent après synchro ; désignorer reste désignoré après fusion avec une ancienne sauvegarde.

## 10. Corrections du 22 octobre

- [ ] Chapitre commencé : le contour blanc en pointillés est celui du futur badge doré à droite, pas celui du numéro.
- [ ] Ignorer et réintégrer : aucun XP au changement ; une première vraie réussite reste récompensée. Les anciens faux bonus identifiables sont corrigés sans supprimer les réponses.
- [ ] Calculatrice paysage : X à hauteur du titre ; résultat et deux flèches à hauteur du champ. Vérifier aussi l’insertion et une petite fenêtre.
- [ ] Nombre signé : bouton + / − à gauche, seul le nombre est dans le champ ; coller −3,5 place le signe sur le bouton. La correction doit prendre ce signe en compte après rotation.
- [ ] SSTV : les mires suivent de près l’énoncé ; davantage d’espace les sépare des réponses.
- [ ] Micro de l’accueil : demande immédiate, refus puis bouton de réessai, paramètres de Hamigo uniquement, choix Maintenir/Bascule conservé et bouton Continuer sans microphone.
- [ ] Premier highlight : halo resserré autour de toute la ligne du cours ; les voisins éclairés restent désactivés.
- [ ] Brouillon portrait : épaisseur et couleur sur une seule ligne (pastilles visibles et dernier segment pour les couleurs restantes), Stylet uniquement sur la ligne des actions, barre de scroll et deux doigts, gomme visible dès le contact, undo/redo par geste.
- [ ] Brouillon paysage : papier sur toute la hauteur, pas de titre ; fermeture en tête de barre et flèche de changement de côté au pied. « Stylet seul » réserve le dessin au stylet ; le doigt déplace alors la feuille. Vérifier aussi les commandes avec le vrai clavier ouvert.
- [ ] Pico : même petit son doux dans les questions, les intros, Version et au pied du Parcours ; Sons désactivés le coupe partout.

### Formulaire du 22 octobre

- [ ] Le même formulaire Tally s’ouvre depuis les drapeaux et Contact. « Objet » remplace la question longue ; aucun prénom n’est demandé et l’e-mail est facultatif.
- [ ] Un titre/ID transmis porte « (pré-rempli, ne pas modifier) » ; un champ vide ne porte pas ce suffixe.
- [ ] En bas, ouvrir puis replier « Données de diagnostic envoyées avec ce formulaire » ; les données sont lisibles, sans champs supplémentaires à remplir. Une réouverture repart du contexte courant.
- [ ] Au prochain vrai retour, vérifier la réception de la notification propriétaire. L’activation a été contrôlée, sans envoi de test.

- [ ] Sur petit écran, les repères Certificat/Exploration suivent la dernière ligne du titre, sans écraser les mots dans une colonne étroite.

- [ ] Introductions : aucun outil flottant ; le dernier paragraphe et le bouton de lancement restent lisibles. Dans le tutoriel, l’aide occupe la largeur libérée, reste à droite en paysage et ne gêne pas la fin du texte. Gameplay ne propose plus la gestion des assistants ; Autorisation du microphone ouvre les paramètres de Hamigo.

- [ ] Dicter plusieurs mots et relâcher au dernier : attendre Analyse, puis vérifier que les derniers mots arrivent. Une nouvelle pression pendant Analyse ne coupe pas le calcul et Vérifier ne valide pas une ancienne réponse. Le bouton clavier permet toujours de reprendre manuellement.
- [ ] Brouillon en paysage : lire Stylet uniquement en entier ; en fenêtre basse, épaisseur et couleur se choisissent dans deux menus sur une ligne.
- [ ] Un cours utile au certificat montre uniquement ce repère dans Parcours et dans son introduction ; les autres cours de découverte montrent Explorer la radio.

- [ ] Relâcher le micro juste après une syllabe : l’indicateur reste brièvement en écoute (250 ms), puis le son de fermeture et Analyse prennent le relais. Quitter ou choisir le clavier ferme immédiatement l’écoute.


### Mise en forme des introductions

- [ ] Relire Bienvenue dans Hamigo : astuce de Pico en italique, premières phrases des onglets en gras, icônes et couleurs des onglets et des deux repères. Le drapeau apparaît aussi dans la question sur le signalement.
- [ ] Comparer une fiche de calcul, une fiche Morse, les portes logiques et la préparation du jour J : les sous-titres aident à retrouver une idée, les schémas et les écoutes restent utilisables.
- [ ] Dans Du rythme à l’épellation, toucher « l’alphabet international » souligné en pointillés : un aperçu s’ouvre sans quitter le cours. Le faire défiler, fermer par la croix ou Retour, puis vérifier la position du cours d’origine.
- [ ] Écouter une fiche mise en forme avec Pico : aucune balise ni nom d’icône ne doit être lu, et l’astuce n’est pas répétée.
- [ ] Vérifier en paysage et avec une grande police : texte entier accessible au scroll, aperçu fermable et bouton À toi de jouer toujours accessible. Terminer aussi le tutoriel pour contrôler la lecture au-dessus de l’aide fixe.
