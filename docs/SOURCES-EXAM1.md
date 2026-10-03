# Banque Exam1 hors ligne

Snapshot téléchargé le **3 octobre 2026** depuis [Exam1 Web, hébergé par le REF](https://exam1.r-e-f.org/). La version déclarée dans le JSON est `2026-09-30T08:08:12+00:00`.

## Ce qui est enregistré

| Élément | Nombre |
| --- | ---: |
| Questions actives publiées par le site | 2 961 |
| Réglementation | 1 621 |
| Technique | 1 340 |
| Thèmes | 20 |
| Images dans l'archive source complète | 3 092 |
| Images associées aux questions actives, intégrées à l'application | 2 961 |
| Images source supplémentaires, conservées dans les originaux | 131 |
| Images nécessaires manquantes | 0 |
| Séries prédéfinies source | 397 |
| Contributeurs nommés par la source | 160 |
| Questions avec commentaire explicatif source | 1 983 |
| Questions sans commentaire explicatif source | 978 |

Tous les énoncés, propositions et commentaires sont conservés tels que publiés. Le champ `reponse` source est déjà un index commençant à zéro. Les noms de thèmes sont seulement débarrassés des espaces en fin de chaîne ; leurs versions originales et numéros sont conservés dans `references.json`.

Les PNG sont essentiels. Certains contiennent seulement le texte de la question, mais d'autres contiennent un circuit, une liste d'affirmations, plusieurs formules ou un graphique absents de l'énoncé JSON. L'écran de question doit afficher l'image lisiblement avec possibilité d'agrandissement.

## Fichiers

`data/sources/exam1/` conserve les téléchargements d'origine :

- `questions.raw.json` : [JSON complet des questions et thèmes](https://exam1.r-e-f.org/assets/questions.json).
- `questions.zip` : [archive officielle de tous les PNG](https://exam1.r-e-f.org/assets/questions.zip), 34 815 376 octets.
- `images/` : les 3 092 fichiers extraits de cette archive.
- `series.raw.json` et `contributeurs.raw.json` : métadonnées publiques, sans transformation.
- `index.html`, `main.js`, `source-page.html` : provenance des points de téléchargement et attribution.
- `DbPopulator.java`, `QuestionsDownload.java`, `README-upstream.md`, `LICENCE-Exam1RA.txt` : preuve du téléchargement groupé et de la licence du logiciel Android upstream ; ce code n'est pas intégré à l'application.
- `download.json` : URLs et date de capture.

`app/src/main/assets/exam1/` contient les fichiers utilisés hors ligne :

- `questions.json` : tableau de 2 961 objets normalisés.
- `images/{id}.png` : image de chaque question.
- `topics.json` : identifiants, catégories, libellés et nombres par thème.
- `references.json` : liens de cours originaux, numéros de thèmes source et drapeaux de revue par question.
- `audit.json` : vérification de couverture, images supplémentaires et questions à relire.
- `excluded.json` : les 10 questions historiques et la transcription incohérente, écartées du jeu aléatoire courant.
- `manifest.json` : version, décomptes, hashes et attribution.
- `series.json` et `contributors.json` : métadonnées source.

Schéma d'une question :

```json
{
  "id": "10001",
  "section": "regulation",
  "topic": "Questions entrainement",
  "prompt": "Quelles sont les limites de la bande des 23 cm en France métropolitaine ?",
  "choices": ["430 à 440 MHz", "50,200 à 51,200 MHz", "1240,000 à 1260,000 MHz", "1240,000 à 1300,000 MHz"],
  "answer": 3,
  "explanation": "",
  "image": "exam1/images/10001.png",
  "source": "https://exam1.r-e-f.org/questions-listing?questionId=10001"
}
```

Un commentaire absent devient `""`, sans explication inventée. Le lien de cours de chaque question reste disponible dans `references.json`, notamment les ancres `http://f6kgl.free.fr/COURS.html#...`.

## Provenance et attribution

La [page Exam1 du radio-club F6KGL/F5KFF](https://f6kgl-f5kff.fr/exam1/) explique que les versions Windows, Web et Android partagent la même banque, les mêmes images et les mêmes corrigés. Jean-Luc Fortin **F6GPX** maintient la banque à partir des comptes rendus de candidats et des corrections des contributeurs. **René F5AXG** a créé le logiciel historique, **Valentin Saugnier F4HVV** la version Web, et le **Réseau des Émetteurs Français** héberge le site. Le radio-club **F6KGL/F5KFF de la Haute Île** fournit les ressources et suit les mises à jour.

Le [dépôt public Android Exam1RA de Maxime Favier F4IQN](https://github.com/Maxime-Favier/Exam1RA) porte une [licence GPL-3.0](https://github.com/Maxime-Favier/Exam1RA/blob/master/LICENCE). Son importeur `DbPopulator.java` donne directement les quatre URLs JSON/ZIP utilisées ici. Cette licence est celle du logiciel de ce dépôt. Aucun texte de licence distinct pour la banque JSON/ZIP du site Web n'a été trouvé dans les fichiers et pages consultés ; son statut ne doit donc pas être présenté comme une licence libre vérifiée. Les originaux et crédits sont conservés pour l'usage personnel privé demandé.

## Revue des éléments historiques

Les originaux restent intacts. `audit.json` et `references.json` signalent **26 questions** pour revue, sans prétendre qu'une recherche textuelle constitue une validation exhaustive des réponses.

| Motif | Identifiants |
| --- | --- |
| L'auteur indique une disposition supprimée, une question qui aurait dû être retirée ou une résolution abrogée | 31025, 34705, 34713, 34714, 34715, 34716, 34717, 34718, 34766, 35061 |
| L'auteur considère le sujet hors programme ou à sa limite | 20111, 20146, 20151, 20166, 20167, 30081, 30680, 32515, 32521, 32531, 32542, 33409, 34805, 39080, 39450 |
| Incohérence de transcription confirmée entre texte JSON et schéma | 23814 |

Pour `23814`, le schéma montre **25 Ω en série avec 20 Ω // 5 Ω**, soit 29 Ω, en accord avec la réponse et le commentaire. L'énoncé JSON indique pourtant 15 Ω au lieu de 5 Ω. L'image source a été inspectée et la divergence est signalée ; elle n'est pas silencieusement corrigée dans la copie normalisée.

Les questions historiques peuvent être conservées dans la consultation intégrale et écartées des exercices aléatoires par le drapeau `author_notes_historical_or_repealed`. Les commentaires « hors programme » représentent le jugement pédagogique de l'auteur ; ils ne signifient pas tous que la réponse technique soit erronée.

Le README du dépôt Android upstream contient aussi un **ancien barème**. Il ne sert pas de source au simulateur : l'[article 2 consolidé de l'arrêté du 21 septembre 2000](https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000401783/) indique deux épreuves de 20 questions, 15 minutes en réglementation et 30 minutes en technique ; 1 point par bonne réponse, 0 par réponse fausse ou absente ; 10/20 requis à chaque épreuve ; bénéfice d'une épreuve réussie conservé un an et délai de deux mois pour se représenter après un échec. Le Morse constitue ici une ressource de culture radio et de mémorisation, et non une troisième épreuve actuelle.

## Import et contrôles

Reconstruction depuis les originaux déjà présents :

```powershell
pwsh -File tools/import_exam1.ps1
```

Nouvelle capture depuis les points publics officiels :

```powershell
pwsh -File tools/import_exam1.ps1 -Download
```

Le script requiert PowerShell et Node.js ; il n'installe aucun paquet. Il refuse toute entrée ZIP autre qu'un nom PNG numérique sans sous-répertoire. La normalisation contrôle l'unicité des identifiants, les thèmes, les énoncés, les choix, les réponses indexées à zéro, le nombre déclaré et la signature PNG de chaque média utilisé. Elle écrit les compteurs et références dans le manifeste et l'audit.

SHA-256 du snapshot :

```text
questions.raw.json c47bcd28ce155aed2696c92bbc16d024ea3f0833a2ce73c77e955f093e63aabe
questions.zip      9342fa587b7b17236925643c6f4e085dc963ab52937d0b327369174d12a318db
```

La vérification réalisée porte sur la complétude et la structure, la présence des images, les notes d'obsolescence publiées et les règles actuelles de l'examen. Elle ne remplace pas une revue pédagogique manuelle de chacune des 2 961 réponses.
