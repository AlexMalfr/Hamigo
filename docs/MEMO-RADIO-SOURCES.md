# Sources et contrôle des fiches radio

Contrôle du 4 octobre 2026. Le fragment `data/reference-radio.json` enrichit dix fiches radio. Il ne modifie ni les leçons ni les questions d’Exam1. `RadioReferenceContent.kt` contient le sélecteur des régions UIT, les tableaux RS/RST et les diagrammes de lecture d’un code d’émission. Le rendu général et la fusion de la catégorie satellite sont réalisés par la bibliothèque Mémo.

## Couverture du cours

La source principale est le [cours F6KGL/F5KFF, novembre 2025](http://f6kgl.free.fr/COURS.html), conservé dans `data/sources/f6kgl/COURS.html` et `course-text.txt` (SHA-256 du HTML : `bbe11fc30b7ad1b2ca10d13984c661e6766cedb104aebc3be533c2415c6f214a`). L’archive déclare la licence CC BY-NC-SA 4.0 ; son attribution est conservée. Les explications et exemples sont reformulés pour l’application ; les nomenclatures, bornes de fréquences et codes restent factuels.

| Fiche | Source précise | Contenu vérifié |
|---|---|---|
| Alphabet international / OTAN | R-3.1, ancre `#R31` | 26 mots normalisés, graphies Alfa et Juliett, chiffres prononcés tels quels pour l’examen, exemple F5PTC |
| Code Morse | UIT-R M.1677-1, annexe 1 | 26 lettres + É, dix chiffres, treize signes, trois sections successives ; le caractère est affiché, son nom est une aide |
| Morse : durées et procédure | M.1677-1 ; R-3.2 | Durées 1/3/7, signaux collés, SOS, AR, SK/VA, K, AS, erreur |
| Code Q | R-3.2, après `#R31` | Les 22 codes HAREC, sens question/avis, QRH ajouté, distinction QRK/QSA/RST, compléments usuels séparés |
| Abréviations | R-3.2.d | Les quinze abréviations HAREC complètes ; les autres termes radio sont dans une section distincte |
| RS / RST | R-3.2.b et guide officiel ARRL | Sens des trois critères, échelles compactes R1–5/S1–9/T1–9, report à deux ou trois chiffres, exemples 59/599/43 ; anciennes cartes de niveaux conservées mais masquées dans la fiche |
| Classes d’émission | R-1.2.a, après `#R11` | Les trois tables complètes du tableau du cours, les six modulations d’impulsions détaillées, X en position centrale, exemples décodés et préfixe de largeur de bande |
| Bandes et satellite | R-2.1.b/c/d, ancre `#R21` | Les 27 bandes × trois régions, sept gammes d’ondes, statuts A/B/C/D, satellites dans chaque bande, sens de liaison, notes et exceptions importantes |
| Indicatifs | R-4.6 / R-4.7, ancre `#R46` | Tous les préfixes français dont FY/FX/FO-Clipperton/FT, structure des indicatifs, suffixes, radio-clubs et relais, préfixes internationaux cités dans le cours et exemple de visite CEPT |
| Régions UIT | R-2.1.a et R-4.6.a | Carte UIT existante, distinction avec les zones de concours, territoires français répartis selon les trois régions |

Les dix fiches contiennent **458 lignes**, dont **28 anciennes cartes masquées** (16 échelles RS/RST, onze sous-bandes satellite et une région UIT). La fiche Bandes contient 81 lignes régionales visibles et onze repères partagés. Chaque région montre 27 bandes ; lorsqu’une bande n’est pas attribuée dans cette région, son absence reste explicitement affichée.

## Vérifications avec les sources primaires actuelles

- [OTAN : alphabet phonétique](https://www.nato.int/en/news-and-events/articles/news/2017/12/21/nato-phonetic-alphabet-codes-and-signals) : le nom OTAN est ajouté sans supprimer le nom international. Le cours précise que la table internationale des chiffres n’est pas à connaître pour le certificat français.
- [UIT-R M.1677-1](https://www.itu.int/rec/R-REC-M.1677-1-200910-I) : recommandation toujours en vigueur ; lettres, signes et temporisation. Les signes de procédure sont transmis sans espace entre leurs lettres, contrairement au mot écrit ordinaire.
- [ARRL : Quick Reference Operating Aids](https://www.arrl.org/quick-reference-operating-aids) : définitions des échelles de réception et emploi de RS en phonie. Les descriptions des neuf valeurs de T sont reformulées en français.
- [ARCEP 2019-1412](https://www.arcep.fr/uploads/tx_gsavis/19-1412.pdf), annexe pages 3–9 : limites, statuts et puissances pour les régions françaises 1 et 2. Le texte de 2012 seul omet les bandes 630 m et 60 m ouvertes ensuite. La fiche n’assimile pas PIRE et puissance de sortie.
- [ANFR : TNRBF](https://www.anfr.fr/planifier/le-tnrbf/le-tnrbf), [version du 26 mai 2026, restitution corrigée le 14 août](https://www.anfr.fr/fileadmin/mediatheque/documents/tnrbf/TNRBF_2026-05-26c.pdf), page 121 : renvoi 5.332A (protection de la radionavigation) et note F53b. La limitation de 1 258–1 300 MHz selon ECC(25)01 prend effet le **27 juin 2028** dans ce document. Cette date actualise le cours, qui évoquait une transposition au plus tard fin 2028.
- [CEPT : T/R 61-01](https://docdb.cept.org/document/925), version active du 18 octobre 2024 : distinction entre les préfixes de révision et l’autorisation d’exploitation temporaire. La présence d’un pays dans la liste ne promet aucune réciprocité actuelle. Les pays/territoires sont des repères issus du cours ; les annexes d’application et l’autorité locale décident des conditions de voyage.
- [ARKEP : règlement du service amateur](https://arkep-rks.org/desk/inc/media/3ECE7097-9725-461B-B269-50B487C09751.pdf) : Z6 est le préfixe employé par l’autorité locale du Kosovo. Le [bulletin UIT du 1er mai 2018](https://www.itu.int/dms_pub/itu-t/opb/sp/T-SP-OB.1149-2018-OAS-PDF-E.pdf) précise qu’à cette date l’UIT n’avait pas attribué cette série à un État membre. La fiche décrit l’usage national et la réserve datée, sans en déduire une attribution internationale actuelle.

## Divergences rendues explicites

- Le tableau du cours donne le statut A à la sous-bande satellite 10,45–10,50 GHz. L’annexe ARCEP 2019-1412 indique **D** en régions 1 et 2. La fiche emploie D pour ces régions ; sa ligne région 3 conserve le repère pédagogique du cours en le signalant.
- Le tableau satellite affiche 1 240–1 300 MHz, tandis que le renvoi RR 5.282, également repris au TNRBF 2026, vise particulièrement 1 260–1 270 MHz et le sens Terre → espace. Les deux informations apparaissent et ne sont pas réduites à un seul intervalle interprété comme permission inconditionnelle.
- La région UIT n’est pas un pays. Le tableau reproduit les repères français du cours, pas toutes les dérogations mondiales. La région 3 reste explicitement datée de l’édition novembre 2025 ; les restrictions locales (dont les repères Tahiti/Moorea du cours) sont affichées avec ce contexte, sans prétendre avoir audité chaque réglementation territoriale de 2026.
- Les anciens libellés 2,5 mm et 1 mm sont conservés pour préserver les identifiants des cartes. Les appellations 2,4 mm et 1,2 mm du cours sont également indiquées. La fréquence est la référence précise.

## Intégration et conservation des révisions

Les termes existants conservent leur `cardId=flash-catégorie-index`. Les cartes R1…T9 existantes sont `kind=flashcard-only` : leur calendrier SRS demeure utilisable, mais l’utilisateur lit les trois échelles sous forme de tableaux. Les lignes régionales 2 et 3 sont exclues du réservoir standard de flashcards centré sur la région 1. Les quinze anciennes cartes satellite sont rattachées au réservoir Bandes : onze bornes cachées conservent `flash-satellite-0` à `10`, les quatre procédures visibles conservent `11` à `14`. L’ancienne carte `flash-bands-25` sur la région 1 demeure cachée. L’audit compare tous les anciens identifiants radio à ceux du fragment et ne constate aucun identifiant perdu.

Le fichier est un fragment de travail : il doit être fusionné dans `data/reference.json`, puis copié dans les assets. Il n’est pas chargé seul par l’application.
