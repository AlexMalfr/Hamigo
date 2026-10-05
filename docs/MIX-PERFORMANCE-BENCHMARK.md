# Vérification de la sélection des thèmes

`MixPerformanceInstrumentedTest` lit directement les ressources Android empaquetées via `Content`. Il ne lance aucune activité, ne change pas la progression et ne contacte aucun service. L’exécution est limitée à l’émulateur de test.

Le premier test vérifie la sélection de toute la partie Technique (y compris les variantes Morse et décibels), la séparation de la Réglementation, l’absence de thèmes vides, l’absence d’exercices numériques dans la seule sélection des couleurs de résistances et des mélanges de 1 à 1 000 questions sans identifiants répétés.

Le second compare **1 000 changements de sélection** avec le calcul de disponibilité historique du commit `fa933d65` : parcours des deux banques à chaque changement, puis normalisation avec une nouvelle expression régulière à chaque comparaison question/thème. Le chemin actuel utilise les listes et correspondances préindexées lors du chargement de `Content`.

La banque employée dans les deux chemins est la banque actuelle, avec les thèmes procéduraux corrigés. Les sélections partielles du code des couleurs et la sélection de toute la partie Technique ont volontairement changé de sens : leur justesse est vérifiée séparément, et elles sont exclues des comparaisons de disponibilité supposées identiques. Une sélection globale reste comparable, car l’ancien chemin court-circuitait le filtrage dans ce cas.

Les deux chemins alternent leur ordre de mesure. Le test compare les comptes de questions pour toutes les sélections comparables et publie les temps médians, les totaux et le rapport des médianes. Il ne possède **aucune assertion de durée** : un émulateur chargé ou un changement de matériel ne doit pas créer une défaillance fonctionnelle artificielle.

Le rapport détaillé est écrit dans le répertoire externe de l’application sous `mix-benchmark.json` et journalisé avec le tag `HamigoMixBenchmark`. Il mesure le calcul de disponibilité sur le thread d’instrumentation. Il ne mesure ni la fréquence des images affichées, ni toute la recomposition Compose, ni le temps de démarrage ; ces valeurs doivent donc être présentées comme un benchmark ciblé du filtrage, pas comme une promesse générale de fluidité.

## Mesure du 4 octobre 2026

Sur le Pixel 9a émulé, API 36, les deux tests passent. Les 1 000 sélections utilisent 2 950 questions Exam1 et 5 188 variantes procédurales. Tous les comptes comparables sont égaux.

| Calcul de disponibilité | Médiane | Temps total des 1 000 sélections |
|---|---:|---:|
| Filtrage antérieur | 250,382 ms | 211 741,083 ms |
| Index actuel | 0,02285 ms | 34,838 ms |

Le rapport brut est conservé dans `output/mix-benchmark-0.9.json`. Le ratio des médianes est environ 10 958 sur cette mesure ; il porte exclusivement sur ce calcul et ne constitue pas une mesure de fluidité globale de l’écran.
