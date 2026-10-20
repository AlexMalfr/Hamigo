# Refonte progressive du Parcours — candidate locale

Objectif utilisateur du 18 octobre 2026 : apprendre depuis zéro, préparer le certificat assez tôt dans un parcours unique, puis garder des explorations radio attractives. Aucune release publique avant essai utilisateur ; installation signée sans effacement des données.

Base isolée : `37ccc612` (0.54), dans le worktree `parcours-progressif`. Le checkout OneDrive ancien et ses conflits restent intacts.

## Chantiers

- [x] Lire objectif intégral, état Git, README, notes et consignes récentes ; vérifier la révision distante.
- [x] Remanier les cours, leurs introductions, sections, sources et exercices en préservant les identités publiées.
- [x] Ajouter les compétences, une reprise prudente des acquis et des révisions variées ; conserver XP et données synchronisées.
- [x] Refaire la navigation du Parcours, les deux barres de progression et le résumé des amis.
- [x] Ajouter saisies libres/vocales facultatives, circuits à compléter, variantes et personnalisation locale.
- [x] Rendre les données brutes et compétences inspectables dans les diagnostics isolés.
- [x] Accompagner l’arrivée avec une introduction guidée, animée et entièrement passable.
- [x] Ajouter historique, statistiques et entraînements ciblés des examens blancs.
- [x] Terminer la revue des saisies et outils en portrait, police agrandie et paysage ; 36 scénarios Android, 191 JVM et lint passent.
- [x] Vérifier la première candidate signée optimisée avec les probes `learning` et `flow`.
- [x] Vérifier la nouvelle densité, sa migration de validations et les captures représentatives ; 197 JVM, lint et 38 scénarios Android passent.

L’installation sur téléphone est reportée explicitement par l’utilisateur le 20 octobre, après échec de sa connexion VPN. Les APK précédents sont intermédiaires. Le statut de reconstruction et de contrôle de l’APK propre est suivi dans `output/delivery-parcours-progressif.json` ; son commit, sa signature et ses résultats font foi pour la livraison locale. Aucune publication ni push.

## État au 20 octobre

| Exigence | Résultat vérifié |
|---|---|
| Fondations et priorité certificat | 13 sections, 64 chapitres, 173 leçons, 1 229 questions actives ; quatre validateurs passent. 97 leçons et 842 UUID historiques conservés. 270 corrections historiques tracées. |
| Densité et spécialisation | 99 questions supplémentaires ; cohorte initiale de 66 leçons : 51→11 séances à 4 questions, moyenne 4,42→5,88. Neuf découpages Morse, séparation puissance/énergie et reprise logique après 14 autres leçons. Inventaire, conservation, migration/import et captures représentatives vérifiés. |
| Morse et explorations | 42 caractères enseignés avant usage ; deux échanges CW complets, deux mires SSTV simulées, trois circuits, sources pratiques et gestes natifs vérifiés. |
| Rappels et STT facultatif | 81 rappels écrits, 12 oraux ; clavier réel et géométrie du micro testés avant les derniers ajouts. Micro centré, clavier à gauche, outils à droite et marge accrue. Questions `hamigo-tools` exclues du SRS ; import/rechargement et rappels radio vérifiés. Dictée acoustique de bout en bout non observée. |
| Variation et personnalisation | Prénoms locaux, indicatifs, réservoirs de distracteurs et permutations testés ; JSON source/effectif inspectable en Diagnostics. |
| Compétences et données | 139 notions ; atomicité, conservation des XP/UUID, fusions et plus de 100 001 tentatives vérifiés. Compteurs notionnels hors ligne explicitement conservateurs. |
| Navigation et progression | Ancrage au dépliage, prochain cours, barres pondérées et résumé social testés. Retour au chapitre et défilement conservés en mémoire. « Ton parcours » termine Moi sous le bilan, avec une phrase courte. |
| Guide Pico | Parcours guidé et sauts testés ; panneau latéral en fenêtre courte et large pour dégager la cible réelle. |
| Examens blancs | Historique, 40 réponses, corrections, courbe, sujets faibles et examen personnalisé après trois examens testés. La génération de banque du départ est conservée. |
| Conditions concrètes | Deux cours, dix nouveaux UUID et liens ANFR ; consignes nationales et locales distinguées. Astuce avec icônes natives, outils dans les intros et Mémo, conservation de leur contenu au retour et à la recréation d’Activity vérifiées. |
| Formats et IME | Passage final normal : 36/36 ; portrait compact et police 130 % : 5/5 ; paysage avec le vrai clavier : 3/3. Captures natives inspectées. La disposition s’adapte à la place réellement laissée par l’IME en conservant la colonne/rangée d’outils. |
| Livraison | Derniers contrôles debug : 197 JVM, lint et 38 scénarios Android réussis. Première candidate R8 : `learning` et `flow` réussis ; les résultats de l’APK propre actualisé sont suivis dans la preuve locale distincte. Installation téléphone reportée. Les deux AVD sont sur Z, avec copies contrôlées et démarrages vérifiés. Aucune release publique ni push de cette refonte. |

Les résultats, échecs intermédiaires et captures sont décrits dans `VALIDATION.md`. Les rapports locaux se trouvent sous `output/parcours-progressif/`. Le blocage de quota du 19 octobre est levé ; ne pas le considérer comme l’état courant. Ne pas rejouer les scripts ponctuels d’intégration sous `output/`, déjà appliqués. Les réglages de l’émulateur doivent être restaurés après la dernière revue.
