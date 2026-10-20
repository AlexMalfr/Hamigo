# Revue transversale des derniers retours — 21 octobre 2026

Cette revue porte sur les sources du worktree `parcours-progressif`, après la candidate installée `71012264`. Elle ne remplace ni les scénarios Android ni l’inspection des captures. Les résultats d’exécution et l’identité de l’APK livré appartiennent à `VALIDATION.md` et à la preuve locale de livraison. Aucun build, ADB, appel de compte personnel ou test acoustique n’a été exécuté pour cette revue.

## Constat général

Les demandes ne sont plus limitées au formulaire : les modifications couvrent effectivement Parcours, guide, permissions et réponse orale, outils, diagnostics, réglages synchronisés et signalements. La lecture croisée n’a pas identifié de fonctionnalité entière oubliée dans cette dernière série. Deux écarts concrets ont été remontés et corrigés : les marqueurs Certificat/Exploration sont replacés à droite du titre dans `PathLessonRow`, et plusieurs consignes anciennes du guide de test décrivaient des comportements remplacés. Le guide de test et le README du probe sont harmonisés avec les sources. Ne pas déduire leur rendu du seul audit source.

## Correspondance des dernières demandes et du code

| Demande | Ce qui est présent dans les sources | Point de contrôle final |
| --- | --- | --- |
| Types compacts et tous les lancements | Présentation courante en premier, variantes repliables, actions icônes de 48 dp, trois lancements groupés ; une occurrence par composition malgré les noms de composants différents | Parcourir les trois batchs, revenir et tourner l’activité |
| Variantes réellement distinctes | ± numérique ; SSTV inclinaison/perte ; images ; trois compositions par lignes ; écrit et oral court/développé ; flashcard Morse graphique ; logique simple/colonne ; trois montages ; écoutes et productions Morse | Permission, PTT/bascule, orientation et police sont une matrice d’essai, pas des questions dupliquées |
| Recherche du primaire | Le corpus inclut la présentation courante même retirée des variantes pour éviter un doublon | Rechercher le nom exact du montage primaire et le lancer |
| Retour et rotation | État de destination conservé par SaveableStateHolder, modèles diagnostics détenus par AppModel, listes/recherches conservées ; outils Mémo saveables ; intent initial non rejoué à la recréation | Vraie recréation Activity et retours fiche/outil/quiz ; cela ne promet pas toute reprise après destruction arbitraire du processus |
| Parcours | Groupes dépliés mesurés ; centrage avec repli si le groupe dépasse la fenêtre ; prochain cours demandé au centre ; flèche cachée près du haut si cible visible ; Pico 360°, son et haptique | Long chapitre, ouvertures successives, premier frame et arrivée, portrait et paysage |
| Guide | Cible agrandie, popup de lecture avec Pico et gouttière en paysage, navigation contrôlée avec toast, confirmation de passage, fin avec Commencer et voile léger | Tester le retour système et la croix de séance, qui ont des chemins distincts ; prochain chapitre après une vraie fin |
| Cours ignorés | Registre réversible synchronisé ; parcours/barres incluant les ignorés ; bonus de fin de cours de 6 XP partagé avec la vraie complétion ; garde d’ouverture globale ; confirmation avant ignorer cours/chapitre | Une seule prime malgré toggles, complétion ultérieure et fusion concurrente ; aucune réponse ni preuve SRS synthétique |
| Micro et permissions | Même hôte réel dans Diagnostics, essai dans l’accueil, demande à l’ouverture de question, refus expliqué et lien autorisations ; historique local empêchant les boucles ; PTT par défaut, bascule, sons, ondes et Pico en écoute | Refaire le refus natif après réparation du harnais, puis la liaison au service ; distinguer test contrôlé et parole réelle |
| Orthographe OTAN | Alias Alfa/Juliett/Whiskey/X-ray et Israël/Eco/Joliette/sous l eau, indépendants de la réponse attendue | Une mauvaise lettre reste une mauvaise réponse ; transcription libre hors OTAN |
| Calculatrice et brouillon | Expression à gauche et résultat/actions à droite en paysage ; modes icônes, outils en tête/côté, gomme/tailles/couleurs/undo/redo/confirmation, papier long et déplacement | IME réel, extérieur bas ignoré, petite largeur, police agrandie, conservation à la rotation |
| Montage et SSTV | Cible prête surlignée, drag immédiat sur la carte, réserve sans éléments utilisés ; mires sans ancien label ni bandes supérieure/inférieure | Geste depuis texte/bord, place restante pour les réponses |
| Pico et sons | Même contrôleur de rafales sur Pico et l’énoncé TTS, nouvel échantillon doux, footer distinct, Sons/Vibrations respectés | Écoute à volume comparable et feedback coupé ; le code ne juge pas le caractère agréable d’un son |
| Réglages | Registres par clé pour Sons/Vibrations, sons/espaces Morse, mode micro et autoSync ; anciennes préférences conservées ; permission système et moteur restent locaux | Import ancien sans perte, changements distincts, import explicite, replanification de synchro |
| Tally | URL publique https://tally.so/r/9NqlpK ; drapeaux question/Mémo/introduction et contact ; contexte sans réponse/compte/jeton ; titres personnalisés masqués | Publication et préremplissage observés séparément par l’agent principal ; upload et soumission réelle non revendiqués |
| Sources et TTS | Corrections sans liens génériques ; métadonnées conservées pour audit ; repères TTS explicités comme heuristiques sans validation demandée | Distinguer rédaction native, référence documentaire et import Exam1 |

## Jugement sur l’objectif pédagogique initial

Le fichier d’objectif a été relu. L’inventaire des assets confirme **13 sections, 64 chapitres, 173 leçons, 1 231 questions actives et six archives**. Aucune leçon active n’a encore 13 questions ou plus. L’introduction de bienvenue contient les six volets annoncés : lecture préalable, ordre et repères certificat, Mémo/flashcards, révisions et corrections, autres onglets et liberté de connexion, signalement et clavier facultatif. Ses cinq questions évaluent ces usages ; elles restent exclues du SRS par `hamigo-tools`.

La revue de densité antérieure a de vrais motifs d’ajout et des déplacements traçables ; chaque tirage aléatoire n’est pas compté comme une situation pédagogique. Les reprises hors Morse couvrent proportions, mesures, puissance/énergie, décibels/bruit, chaînes radio et diagnostic. Le suivi par familles diversifie de manière bornée les notions déjà rencontrées, sans fabriquer de maîtrise des parents ni remplacer une question réellement due. Ces éléments répondent à l’intention du goal. Aucun contenu du Parcours ni identifiant pédagogique n’a été modifié par cette revue ; l’exemple écrit long ajouté appartient exclusivement à Diagnostics.

La profondeur reste volontairement inégale : apprendre une grandeur, comparer des méthodes et comprendre un échange Morse ne demandent pas le même nombre de questions. Les simulations SSTV et montages ne remplacent pas une manipulation physique. L’ordre conseille l’espacement sans imposer des jours entre les leçons. Les tableaux de couverture et tests ne valent pas certification pédagogique ou réglementaire exhaustive. `CONTENT-AUDIT-2026-10.md` décrit la couverture du 4 octobre ; ne pas le lire comme une liste des omissions actuelles sans le croiser avec `PRACTICE-DENSITY-55.md`, `MEMO-COVERAGE-2026-10.md` et `AUDIT-FINITIONS-2026-10.md`.

## Limites à exprimer honnêtement avant livraison

- **Parole réelle et chuchotement** : service Android, puis repli borné vers le service local si nécessaire. Aucun petit modèle Vosk livré ou téléchargé par l’APK. Le test local du lexique n’est pas un benchmark acoustique ; permission et service fake ne valident pas une diction sur Samsung.
- **Rendu et nouveaux tests** : les séries de reprise et captures de la candidate actuelle sont consignées dans `VALIDATION.md`. Les échecs initiaux restent archivés ; seules leurs reprises effectivement réussies valent validation. Les résultats R8 et l’installation sont liés au SHA final dans la preuve locale de livraison.
- **Tally** : structure, branches et préremplissage sont vérifiables sans conserver de réponse. Limites effectives des fichiers et soumission volontaire demeurent distinctes ; ne pas envoyer un retour réel pour prétendre les tester automatiquement.
- **Droits Exam1** : logiciel de lecture et banque téléchargée ont des statuts distincts. Aucun droit général de redistribution de la banque établi ; elle n’est pas embarquée. Une comparaison lexicale native ne prouve pas à elle seule la titularité de droits.
- **Conservation** : installation avec la signature existante et `adb install -r`, puis contrôle de version. Cet audit ne constitue pas une installation, un push ou une publication GitHub.

## Reprise du harnais de mémoire

Le premier passage de `android-ui-speech-tools.txt` s’arrête dans la recherche répétée du scénario de réouverture, avant son assertion de tas. Le test ajoutait `proc-ohm-` au texte déjà conservé ; le deuxième cycle recherchait donc `proc-ohm-proc-ohm-`. Le harnais remplace désormais la recherche au lieu de la concaténer et vérifie que chaque fermeture libère la référence à son inspecteur. Le seuil de tas et les douze cycles sont conservés. La reprise `android-retained-pages-fixed.txt` atteint ses assertions de tas et ses douze cycles avec succès ; elle ne certifie pas chaque allocation de l’app.

## Défauts complémentaires observés pendant la recette

La revue des captures a trouvé un en-tête de cours dont les éléments se superposaient après ajout du drapeau : il a été remis en lignes/colonnes explicites. La matrice à 640 × 360 dp et police 130 % a ensuite révélé le recouvrement du bouton Suivant de la popup de lecture par les outils flottants. La popup réserve désormais leur hauteur et son texte défile séparément de ses actions. Les scénarios contrôlent aussi l’absence d’intersection avec chacun des deux boutons. Les résultats exécutés après ces corrections sont consignés dans `VALIDATION.md`.
