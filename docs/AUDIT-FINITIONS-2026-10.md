# Revue critique de la candidate 0.55 — 21 octobre 2026

## Jugement sur l’objectif initial

Le parcours répond désormais à l’intention principale : partir des bases, préparer le certificat avant la fin du chemin, puis donner des raisons de continuer avec des activités radio concrètes. Il compte 13 sections, 64 chapitres, 173 leçons et 1 231 questions actives, avec six anciennes questions encore résolubles. Le nombre de questions ne constitue pas une preuve de qualité : l’audit distingue les situations nouvelles, les reprises et les variantes d’un même exercice.

La première candidate avait deux insuffisances réelles : une pratique trop uniforme dans beaucoup de nouvelles leçons et l’absence du format de réponses indépendantes par ligne demandé dans le goal. La revue de pratique a apporté 99 exercices justifiés et redistribué neuf longues séances Morse. La présente finition ajoute deux situations par lignes : rappel OTAN B/D/V et choix de relations pour calculer courant, durée et puissance. Une composition mixte est disponible comme exemple fictif dans Diagnostics ; elle ne gonfle pas artificiellement le Parcours.

| Intention | Réponse du produit et preuve examinée |
| --- | --- |
| Débutants | Nombres, proportions, unités, formules et circuits simples avant les applications ; introductions spécifiques et fiches Mémo liées |
| Certificat prioritaire | Cours pondérés certificat jusqu’au jalon de section 9 ; exploration ensuite, avec de courtes découvertes déjà intercalées |
| Reprises à distance | Morse distribué, rappels de logique, énergie, dB, mesures, antennes et diagnostic dans d’autres contextes ; voir `PRACTICE-DENSITY-55.md` |
| Pratique variée | Rappel libre, parole facultative, calculs, choix de méthode, erreurs fréquentes, lecture de schéma et montages |
| Aléatoire utile | Valeurs, prénoms locaux, indicatifs compatibles avec les lettres étudiées, positions et distracteurs ; identité stable de la notion |
| Radio après le certificat | SDR/cascade, SSTV, ISS/satellites, numérique, sorties POTA/SOTA, énergie portable, stations automatiques, mesures et projets |
| Suivi des notions | 139 compétences feuilles ; familles éditoriales non persistées ; révisions limitées aux leçons déjà étudiées |
| Navigation | Sections nommées, ancrages mesurés, retours de listes conservés, cours d’accueil et visite guidée |
| Examen | Format réel, démarches et préparation pratique, historique/transcripts, tendances descriptives et personnalisé après trois examens complets |
| Progression ancienne | Identifiants publiés conservés ; provenance des complétions antérieures à 0.55, sans XP ni validation attribués par une simple ouverture |

La progression libre laisse l’utilisateur ouvrir un cours avancé : les prérequis sont des conseils, pas un verrou. L’ordre éditorial ne garantit pas un nombre de jours entre deux reprises. Les barres Certificat/Exploration mesurent les cours réalisés et ne prédisent pas une réussite à l’épreuve. Les simulations SSTV et montages sont des modèles pédagogiques ; elles ne remplacent pas une réception réelle ou une mesure physique. Les tendances d’examens ne promettent pas une date de réussite.

## Introduction et lecture vocale

L’accueil est réécrit en six paragraphes spécialisés : lire avant les questions, choisir le chemin, utiliser Mémo et les flashcards, revenir aux révisions, retrouver les onglets et faire un signalement. Ses cinq UUID sont conservés ; deux formulations sont précisées, sans changer leur connaissance évaluée. Le manifeste `data/finishing-review-55.json` conserve les textes avant/après et les motifs.

« À contrôler » était un filtre heuristique du texte préparé pour le TTS. Ce n’était ni une demande d’avis de l’utilisateur ni un statut de cours inachevé. L’exécution réelle de `FrenchSpeech.prepare` sur les introductions a trouvé 35 segments à revoir dans 26 leçons, dont des erreurs : désignations A1A prises pour des ampères, suffixes /P et /MM traités comme des divisions, variables et sigles non préparés. Les règles communes et quatorze adaptations orales explicites les corrigent. La préparation JVM des 851 segments n’avait plus d’alerte ; la vérification Android complète a encore trouvé RC dans une formule de coupure, d’où une dernière adaptation explicite du produit. Les tests distinguent unités, indicatifs et divisions, et la narration des lignes ne divulgue pas les réponses libres.

Ce contrôle porte sur le texte effectivement préparé. Il ne signifie pas que chaque phrase a été écoutée sur chaque moteur vocal Android. Le filtre reste actif pour détecter les futures régressions.

## Référence documentaire ou question importée

Pour les questions natives du Parcours, les URLs désignent les références documentaires utilisées pour rédiger les faits, les applications et leurs corrections. Elles ne signifient pas que l’énoncé a été copié. L’interface emploie maintenant « Référence documentaire ». Une comparaison lexicale du contenu natif et de l’archive F6KGL n’a trouvé aucune séquence commune de douze mots normalisés ; ce résultat étaye la relecture mais ne démontre pas, à lui seul, une titularité ou une absence de contrefaçon.

L’archive F6KGL conserve sa notice [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/). Cette licence autorise certains usages sous conditions, dont l’attribution, l’absence d’usage commercial et le partage dans les mêmes conditions des adaptations concernées. Elle ne devient pas la licence générale de tous les autres fichiers du projet. Voir `SOURCES-COURSES.md` et les notices conservées dans l’archive.

Exam1 est différent : ses énoncés, choix, images et corrections sont importés fidèlement, avec les identifiants numériques source. Le libellé le précise. La [présentation du radio-club](https://f6kgl-f5kff.fr/exam1/) décrit l’origine collective de la banque. La [GPL du logiciel Android Exam1RA](https://github.com/Maxime-Favier/Exam1RA/blob/master/LICENCE) ne prouve pas une licence libre distincte de la banque JSON/ZIP hébergée par le REF. Aucune autorisation explicite de redistribution de cette banque n’a été établie dans les pièces consultées. Les nouveaux APK ne l’embarquent pas : ils la téléchargent du serveur officiel. Cela ne constitue pas une garantie générale de droits. Une diffusion publique de la banque ou de ses images demande une clarification préalable auprès des ayants droit. Aucun contact n’a été envoyé.

## Réponse orale : périmètre vérifiable

Le service vocal Android par défaut est utilisé, avec un repli limité vers le service local lorsqu’un démarrage échoue avant le signal de disponibilité. Le bouton distingue préparation, écoute et analyse. Le relâchement d’un appui pendant la préparation invalide les callbacks tardifs. Le mode appui maintenu est le défaut ; le mode bascule est un réglage local. Le clavier demeure accessible.

L’orthographe OTAN est normalisée sur tout le vocabulaire, indépendamment de la réponse attendue : Alpha → Alfa, Juliette/Juliet → Juliett, Whisky → Whiskey et X ray → X-ray. Une mauvaise lettre reste une mauvaise réponse. Android 13+ reçoit le vocabulaire complet comme [indication facultative au moteur](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_BIASING_STRINGS). Le niveau dessiné vient des callbacks RMS ; certains moteurs peuvent ne pas en fournir.

Un moteur embarqué n’est pas assimilé à une garantie de reconnaissance au chuchotement. Le [petit modèle français Vosk officiel](https://alphacephei.com/vosk/models) est proposé sous Apache 2.0 et pèse environ 41 Mo. Son vocabulaire et son intérêt doivent être mesurés avant toute intégration. Aucun nouveau modèle ni aucune dépendance de reconnaissance n’est embarqué dans cette candidate. Le comportement acoustique, surtout au chuchotement, reste un essai distinct des tests de cycle, de correction et de mise en page.

Un essai local a réellement chargé `vosk-model-small-fr-0.22` avec le runtime Windows Vosk 0.3.45 et interrogé son lexique. `foxtrot`, `x-ray` et `xray` n’y sont pas présents ; `juliett` et `uniform` manquent aussi, avec toutefois les formes françaises `juliette` et `uniforme` disponibles. Cela ne mesure pas sa précision acoustique ni ses possibilités d’adaptation, mais suffit à écarter son ajout immédiat comme solution prête à l’emploi pour l’OTAN. Le ZIP testé a le SHA-256 `CABF6180E177EB9B3A9A9D43A437BD5E549F3A7D09525E5D69A3FED787BE12AD`. ZIP, runtime et résultats demeurent dans les fichiers locaux ignorés, sans entrer dans l’APK ou Git.

## Preuves et limites de validation

Les résultats de génération, identités, tests, captures examinées et livraison sont consignés dans `VALIDATION.md`. Les rapports locaux restent dans `output/finitions-2026-10/`. La recherche de doublons, les prérequis déclarés et les comptages ne sont pas présentés comme une certification exhaustive du contenu. Les essais utilisateur proposés dans `TESTS-UTILISATEUR-0.55.md` complètent les vérifications automatisées sans exiger de remise à zéro du téléphone.
