# Identifiants des questions

Depuis la **0.47**, les questions écrites pour le Parcours possèdent un identifiant opaque `q-<UUID v4>`. Cet identifiant est attribué une seule fois : ce n'est ni un numéro de cours, ni un rang, ni la version de l'application. Les identifiants des chapitres, leçons, flashcards, questions Exam1 et variantes procédurales existantes restent inchangés.

## Banque et appartenance au Parcours

- `data/curriculum.json` est la source d'édition, avec les questions et leurs UUID explicites dans les leçons.
- `data/course-questions.json` et sa copie distribuée sont la banque séparée des **842 questions actives**.
- `app/src/main/assets/curriculum.json` contient les introductions et les listes `questionIds`. `Content` résout ces références dans la banque ; déplacer une question ne change pas son identité.
- `data/question-identities.json` conserve les **850 identités historiques**, dont les huit rappels Morse répétitifs retirés. Les `bindings` relient les repères internes des générateurs aux UUID. Ces fichiers doivent être versionnés ensemble.

`node tools/expand_curriculum.mjs` produit les fichiers distribués avec `tools/question_identities.mjs`. Un nouvel exercice reçoit un UUID aléatoire conservé dans le registre. Les UUID explicites existants priment. Une empreinte du contenu sert à reconnaître un déplacement lors de la génération ; elle n'est pas l'identifiant publié. Si plusieurs exercices ont la même empreinte, conserver leur UUID explicite pour éviter une attribution ambiguë. Une correction de texte sur un exercice existant conserve son UUID ; une nouvelle question ne doit pas réutiliser celui d'une question différente.

`node tools/validate_question_identities.mjs` contrôle l'unicité, la régénération byte pour byte et des déplacements/réordonnancements en mémoire. `node tools/validate_logic_content.mjs` vérifie aussi la résolution des appartenances et les contenus préservés.

## Compatibilité temporaire des sauvegardes

La migration introduite en **0.47** est volontairement temporaire. `CourseQuestionAliases47` associe chaque ancien ID à son UUID distinct, y compris pour les questions retirées ; on ne fusionne donc pas rétroactivement leurs XP. `CourseQuestionMigration47` s'applique à la progression locale chargée, aux imports et à chaque sauvegarde distante avant fusion.

Elle traduit les clés des révisions SRS et des récompenses, ainsi que les `awardKey` des événements. Les UUID des événements, dates, compteurs, XP, historique quotidien, leçons terminées, amis et préférences restent conservés. Si une sauvegarde contient les deux noms d'une même révision, la plus récente gagne avec un départage déterministe. Les identifiants inconnus sont conservés. Les rappels des questions retirées restent archivés mais ne sont plus proposés dans la banque active.

La progression porte `questionIdsVersion: 1`, tandis que le schéma de sauvegarde complète reste **2**. Ce marqueur distingue l'espace d'identifiants ; ce n'est pas le numéro de version de l'APK. Un marqueur inconnu est refusé. La conversion précède la déduplication des événements, afin qu'une sauvegarde d'un appareil ancien et celle d'un appareil mis à jour ne récompensent pas deux fois la même réponse question/jour. Relire la sauvegarde convertie est idempotent.

## Retrait demandé à la 0.52

À **0.52**, après cinq versions, supprimer `CourseQuestionMigration47`, `CourseQuestionAliases47`, leurs appels, les tests temporaires de migration et l'émission des aliases Kotlin dans `tools/question_identities.mjs`. Le contrôle Gradle bloque une livraison 0.52 tant que la classe de migration existe. Conserver les UUID publiés, le registre d'identités, la banque et son marqueur de format.

Après ce retrait, une sauvegarde restée exclusivement sur les anciens IDs ne sera plus convertie automatiquement. Les appareils doivent avoir ouvert une version 0.47–0.51 et synchronisé leur sauvegarde pendant cette période. Cette limite résulte du retrait explicitement demandé ; ce mécanisme n'est pas une promesse de compatibilité permanente avec toutes les anciennes sauvegardes.
