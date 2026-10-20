# Présentation des introductions

Le texte pédagogique reste dans `data/curriculum.json`, les adaptations orales dans `data/lesson-narration.json`. La présentation éditoriale des 173 introductions est décrite dans `data/lesson-presentation.json` et distribuée par `node tools/expand_curriculum.mjs`. Aucun UUID, ordre de cours ou enregistrement de progression ne dépend de cette présentation.

Chaque entrée contient l’empreinte SHA-256 de `JSON.stringify(lesson.body)` et des annotations par indice de paragraphe :

- `heading` : un sous-titre court, à une transition réelle du raisonnement ; ne pas ajouter un titre à chaque phrase.
- `emphasis` : des extraits exacts à mettre en gras, généralement une définition ou une idée clé. Pas de mise en couleur automatique des notions techniques.
- `tip` : un extrait exact commençant par « Astuce : », détaché en plus grand et en italique. Il reste lu une seule fois dans le texte source.
- `breakBefore` : début exact d’une partie à présenter sur un nouveau paragraphe visuel, sans changer les indices du texte source.
- `references` : couples `text` / `lessonId` explicites. Le terme devient gras et souligné en pointillés ; le toucher ouvre une fenêtre défilante, fermable par la croix, Fermer, l’extérieur ou Retour. Il ne démarre pas ce cours, ne quitte pas l’introduction et ne change aucune progression. Les références sont inactives dans cet aperçu pour éviter d’empiler des fenêtres.

Les noms exacts des onglets, les repères Certificat/Exploration et le mot drapeau utilisent les icônes et couleurs de l’app. Ce traitement commun couvre aussi les énoncés, propositions et corrections affichés comme prose. Le texte accessible et la narration conservent leurs mots d’origine ; les icônes décoratives ne sont pas prononcées. Les mots techniques ordinaires comme « certificat », « exploration » ou « mémoire » ne sont pas assimilés à des boutons.

La génération vérifie la couverture, l’empreinte de chaque introduction, les extraits et les cibles de cours. Après modification d’un texte, revoir les annotations concernées avant de recalculer son empreinte. Ne pas contourner un échec d’empreinte sans relire les transitions. Les tableaux de Morse et les illustrations restent rendus par les composants natifs, sans remplacer le Morse par des caractères ASCII.

La mise en page conserve un item de liste par paragraphe source, afin que les retours depuis les fiches Mémo conservent leur ancrage. La fiche de bienvenue sépare les présentations d’Équipe et de Moi, souligne les premières phrases des descriptions d’onglets, et détache l’astuce de lecture de Pico. Les autres fiches emploient des sous-titres adaptés au sujet, sans ajout de nouvelles assertions techniques dans cette passe.
