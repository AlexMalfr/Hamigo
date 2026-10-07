# Couverture des ressources Mémo — F6KGL, 5 octobre 2026

Source de travail : [cours F6KGL, édition novembre 2025](http://f6kgl.free.fr/COURS.html), archive locale `data/sources/f6kgl/COURS.html`, extraction `course-text.txt`, images de tableaux/schémas `COURS_fichiers`. Licence de l’adaptation : CC BY-NC-SA 4.0, attribution F6KGL/F6GPX, même licence. La page couvre le premier livre, introduction et T0–T12 ; le recueil d’exercices est un autre document.

Cette matrice ne mesure pas la couverture en comptant les pages : elle relie chaque sous-section de connaissance à des repères identifiables et précise ce qui reste lié au cours original. Les explications sont reformulées, les unités et les conditions des formules restent visibles. « Exemples » et « Astuces » sont distincts des faits et ne créent pas des cartes de mémorisation ambiguës.

## Résultat de l'intégration

La bibliothèque fusionnée dans `data/reference.json` contient **40 fiches, 1 093 entrées et 970 flashcards**. Le nombre d'entrées comprend les faits, exemples, astuces et cartes de compatibilité : ce n'est pas un nombre de faits indépendants ni un total de lignes toutes visibles. Les identifiants des **390 anciennes flashcards** sont préservés pour conserver leurs échéances de révision.

`data/reference-additions.json` contient **25 fiches / 489 entrées**, dont 18 cartes de compatibilité invisibles dans la fiche et des exemples/astuces. Il remplace les anciennes fiches `propagation` et `exam-rules`, et ajoute 21 fiches, dont les **20 matériaux du tableau de résistivités**. Les autres fragments remanient alphabet, Morse, codes Q, bandes/satellites, indicatifs, classes d’émission, rapports, abréviations, régions UIT, résistances, dB, unités, préfixes SI et formules. L'ancienne fiche satellite est intégrée aux bandes amateur.

La bibliothèque distingue six familles, normalisées lors de la fusion : Communiquer ; Réglementation et station ; Bases et calculs ; Électricité et composants ; Électronique ; Radio et antennes. La liste et les fiches ont un en-tête fixe, une recherche déployable, une calculatrice et un accès discret au cours complet. La fiche des bandes propose le choix de région et le regroupement par gamme ; les allocations satellite y sont intégrées. Les outils sont séparés du contenu factuel par leur fond turquoise et de l'espace. Les exemples et les astuces ont leur propre traitement visuel.

La fiche dB ajoute une chaîne de puissance : puissance d'entrée ou de sortie, gain et perte des étages, unités W/mW/µW et schéma dynamique. Les formules indiquent leur usage, leurs grandeurs, leurs unités et leurs conditions. Les comparaisons série/parallèle, L/C, RLC, logique, binaire/hexadécimal, dB, ROS/réflexion, S-mètre et séries E6/E12/E24 utilisent de vrais tableaux natifs, en complément des schémas.

Hors Mémo, le mix propose des tailles prédéfinies et un nombre libre de **1 à 1 000**, limité au nombre de questions distinctes du choix courant. Les variantes Hamigo sont incluses en permanence. Un index en mémoire prépare les correspondances entre thèmes et questions ; le laboratoire vient après le mix.

Les schémas natifs de `MemoExtraDiagrams.kt` acceptent les seules clés `extra:` connues : circuits série/parallèle, diviseur, nœud, batterie, branchement V/A, filtres, résonance, π, diode, alimentation, PLL/DDS/mélangeur/AOP, trajets radio, dipôle/quart d’onde/Yagi, coaxial, réception/émission, spectres AM/BLU, CW/FM et CEM. Ils ne représentent pas un montage prêt à construire ; les légendes annoncent un modèle idéal ou un schéma de principe quand nécessaire.

## Inventaire des tableaux HTML

Les **16 tableaux HTML** de l'archive ont été inventoriés, avec une numérotation de 0 à 15. Cet inventaire ne compte pas les tableaux incorporés sous forme d'image ; la matrice ci-dessous couvre aussi leurs notions. Les données sont réorganisées pour l'écran plutôt que présentées comme des captures du document.

| Tableau de l'archive | Contenu | Représentation dans l'app |
|---|---|---|
| 0 | Classes de certificat, épreuves, puissances et émissions | `exam-rules`, `transmitter-limits` |
| 1 | Trois positions des classes d'émission | `emissions`, sections factuelles par position |
| 2 | Bandes, régions et satellites | `bands`, choix de région, gammes et statuts |
| 3 | Droits de la classe unique et de l'ancienne classe 3 | `transmitter-limits`, conditions et limites |
| 4 | Alphabet international | `nato`, lettres et lecture TTS |
| 5 | Code Q : question et avis | `qcodes`, formulations distinctes |
| 6 | dB et rapports de puissance usuels | `decibels`, tableau natif puissance/tension |
| 7 | Gammes, longueurs d'onde et fréquences | `propagation`, section Gammes ; `bands` |
| 8 | Vingt résistivités de matériaux | `materials`, valeurs en Ω·m à 20 °C |
| 9 | Couleurs, chiffres, multiplicateurs et tolérances | `resistors`, bandes dessinées, exemples et astuce mnémotechnique |
| 10 | Série/parallèle : R, U, I et P | `dc-circuits`, tableau natif, formules et outils |
| 11 | Application numérique série/parallèle | Notions et exemples de `dc-circuits`, `formulas` et outils ; les neuf calculs et valeurs de cet exercice ne sont pas recopiés à l'identique |
| 12 | Dizaines/unités de dB et rapports | `decibels`, tableau natif de décomposition |
| 13 | Décimal, binaire et hexadécimal de 0 à 15 | `binary-logic`, tableau natif |
| 14 | ROS, coefficient de réflexion et puissance réfléchie | `transmission-lines`, tableau natif et définitions |
| 15 | S-mètre HF, dB/S9 et µV sous 50 Ω | `radio-blocks`, tableau natif ; `reports`, interprétation des reports |

## Introduction et cadre de l’examen

| Source | Informations utiles | Fiche / repères |
|---|---|---|
| Intro 1 | Classe unique, ex-classes, HAREC, limites historiques novice, absence de graphie | `exam-rules` « Classe unique et HAREC », « Morse » ; `transmitter-limits` « Anciens opérateurs de classe 3 » |
| Intro 2 | Deux épreuves indépendantes, moyenne séparée, un an de bénéfice | `exam-rules` « Barème », « Épreuve acquise » |
| Intro 3–4 | QCM, limites de la démo officielle, stratégie de préparation | `exam-rules` épreuves et relecture ; l’ANFR distingue explicitement présentation et entraînement |
| Intro 5 | Convocation, identité, calculette, handicap, obtention d’indicatif, délai après échec | `exam-rules` « S’inscrire », « À apporter », « Aménagement », « Après réussite », « Épreuve acquise » |
| Intro 6–7 | Utiliser les variantes, calculatrice, différence texte/usage, références du cours | `math-basics` ; `radio-regulations` « Règle et usage » ; lien au cours complet |
| Intro 8 | Plan des R1–R5 et T0–T12 | Organisation des familles et présente matrice |

Les anciens frais, formulaires, coordonnées téléphoniques et délais postaux racontés dans l’édition ne sont pas figés dans l’interface : liens officiels ANFR pour l’organisation pratique. Le cours complet reste accessible pour les conseils aux formateurs et les détails historiques.

## Réglementation R1–R5

| Sous-section / tableau | Contenu de référence | Fiche / repères identifiables |
|---|---|---|
| R1.1a | Définitions amateur, satellite, station ; UIT/RR, UIT-R, CMR | `radio-regulations` « Service amateur », « Service amateur par satellite », « Station amateur », « UIT », « CMR et UIT-R » |
| R1.1b | CEPT, ECC, ECO, T/R 61-01 et T/R 61-02 | `radio-regulations` « CEPT et ECC » ; `operating-rules` « CEPT en visite » |
| R1.1c | CPCE, ARCEP, ANFR, ministère, TNRBF/affectataires, textes fondamentaux | `radio-regulations` autorités et textes ; `operating-rules` administration |
| R1.1d | Histoire de la réglementation | `radio-regulations` « Chronologie utile » conserve les changements affectant les connaissances actuelles ; récit historique hors programme lié au cours |
| R1.2a / R-1.2.a | Trois caractères des classes, largeur de bande codée, lettres/symboles | `emissions` : table factuelle par position, pas uniquement une liste d’exemples |
| R1.2b | Modulations, vues temporelle et fréquentielle | `modulations` « Porteuse et modulation », AM/BLU/CW/FM/PM et schémas natifs |
| R1.3a | Indicateur de puissance ; obligations actuelles vs anciens matériels | `transmitter-limits` « Indicateur de puissance » |
| R1.3b | Largeurs 6 / 12 / 20 kHz, emission intégralement dans la bande | `transmitter-limits` « Bande occupée… », « Émettre près d’une limite » |
| R1.3c | Non essentiel/hors bande, atténuation dBc, minima de frontière | `transmitter-limits` « Rayonnements non essentiels », « Émissions hors bande », « Domaines hors bande et parasites » |
| R1.3d–e | Perturbations conduites CISPR 11 et EN 301 783 | `transmitter-limits` « Perturbations conduites : cours », « EN 301 783 » ; contexte de conformité, aucune promesse d’édition actuelle de norme |
| R2.1a | TNRBF, compétence métropole/DROM/COM, trois régions | `bands`, `itu-regions` et `radio-regulations` |
| R-2.1.b | Bandes, limites, statuts et amateur par satellite selon région | `bands` : choix de région, regroupement par gamme, satellites intégrés |
| R2.1c | Statut primaire/secondaire, protection contre brouillages | `transmitter-limits` « Service secondaire » ; statut détaillé dans `bands` |
| R2.2a | 500 / 250 / 120 W ; 1 W/15 W PIRE ; novice 10 W ; PEP | `transmitter-limits` puissance et « PEP », « PAR et PIRE » |
| R2.2b | Exposition public et restrictions techniques | `station-safety` « Exposition HF », « Champ électrique du cours » ; `antennas` modèles de champ lointain avec limites |
| R2.2c | Servitudes, zones de protection/garde | `station-safety` « Servitudes radioélectriques » |
| R2.2d | Urbanisme, hauteur, façade, sites protégés, installation temporaire | `station-safety` « Urbanisme », « Mâts et pylônes » ; Légifrance actuel plutôt qu’ancien R422-2 |
| R2.2e | Droit à l’antenne en immeuble collectif | `station-safety` « Droit à l’antenne » |
| R3.1 | Alphabet international / OTAN | `nato`, écoute TTS, distinction lettres/chiffres |
| R3.2 | Codes Q et abréviations ; épellation | `qcodes`, `abbreviations`, `morse`, `morse-rhythm` |
| R3.3a | Écoute, identification, intervalles 15 min, changement fréquence, split | `operating-rules` « Avant l’appel », « Identification », « Split et cross-band » |
| R3.3b–c | Protocoles, urgence/détresse/sécurité | `operating-rules` « Messages de tiers », « Détresse, urgence, sécurité » ; détails maritimes anciens hors programme signalés comme procédures de radiotéléphonie |
| R3.4a | Objet des messages, absence d’intérêt pécuniaire, chiffrement/commande satellite, tiers | `operating-rules` section Messages ; définition dans `radio-regulations` |
| R3.4b | Secret des correspondances | `operating-rules` « Secret des correspondances » |
| R4.1 | Mentions du journal, contrôle, durée un an, utilisateurs radio-club | `operating-rules` « Journal de bord », « Conservation du journal » |
| R4.2a–c | Fixe/P/M/MM, adresse, maritime, aéronef, usages de sous-localisation | `operating-rules` exploitation ; `callsigns` préfixes et suffixes |
| R4.2d | Conformité commercialisation vs construction amateur | `operating-rules` « Construction personnelle » |
| R4.2e | Déclaration PAR > 5 W, WGS84, bandes et mise à jour | `operating-rules` « Déclaration PAR » |
| R4.2f | Réseau ouvert au public : état du texte au moment de l’édition | `operating-rules` « Réseau ouvert au public » indique explicitement novembre 2025, sans transformer une observation du cours en droit 2026 garanti |
| R4.2g | Réquisition / affectations de crise | `operating-rules` « Réquisition » |
| R4.3a–c | Club, supervision, répétiteurs, contrôle satellite | `operating-rules` « Radio-club », « Relais et balise », « Satellite amateur » |
| R4.4a–b | Sanctions administratives / pénales, usurpation, confiscation | `operating-rules` Sanctions ; références Légifrance séparées |
| R4.5 | Modalités actuelles examen et calculette | `exam-rules` |
| R4.6 / tableau R-4.6.a | Formation français, localisation et indicatifs particuliers | `callsigns` et `operating-rules` |
| R4.7 / tableau R-4.7.a | CEPT et préfixes européens/nationaux/dépendances | `callsigns` table géographique ; `operating-rules` « CEPT en visite », « Indicatif à l’étranger » |
| R5.1 | Puissance, PEP, rapports, dB/dBm/dBW/dBc, gain et rendement | `decibels`, `formulas`, `transmitter-limits`, `antennas` |
| R5.2 | Antennes, caractéristiques, dBi/dBd, PAR/PIRE, polarisation | `antennas` Types, Rayonnement, Puissance |
| R5.3 | Lignes, pertes, impédance, adaptation, balun, ROS | `transmission-lines` |
| R5.4 | CEM, brouillages, protections, intermodulation/transmodulation | `emc-noise` |
| R5.5 | Protection électrique, personnes, aériens, foudre | `station-safety` |

## Technique T0–T4 : bases et composants passifs

| Sous-section | Référence utile | Fiche / repères |
|---|---|---|
| T0.1 | Isoler inconnue, priorités, carré/racine, rapports/produit en croix | `math-basics` Équations |
| T0.2 | Puissances de dix, SI, ingénieur, surfaces | `math-basics` Conversions ; `prefixes` ; `units` |
| T0.3 | Calculette π/√/log/exposants/parenthèses | `math-basics` « Calculette » ; calculatrice flottante |
| T1.1 | Charge, sens courant/électrons, tension, conducteurs | `dc-circuits` Bases et Matériaux |
| T1.2 | Ohm/Joule, puissance dissipée, variantes | `dc-circuits`, `formulas` |
| T1.3 | Charge Q=It, énergie Pt/Wh/J | `dc-circuits` « Charge et courant », « Puissance et énergie » ; `units` |
| T1.4a | R=ρL/S, section/diamètre, température | `dc-circuits` résistivité et coefficient température ; `math-basics` Surfaces |
| T1.4b | Tableau de résistivités des matériaux du cours | `materials` vingt matériaux avec leurs valeurs à 20 °C ; `dc-circuits` donne le sens de la résistivité et la formule |
| T1.4c–e | Conductivité, conductance, densité courant, effet de peau | `dc-circuits` Matériaux |
| T1.5 | Couleurs chiffres/multiplicateurs/tolérance, 4/5 anneaux, puissance et séries normalisées | `resistors`, rôle sous chaque bande, tolérance séparée, exemples 4/5 anneaux, astuce mnémotechnique et tableaux E6/E12/E24 |
| T1.6 | Kirchhoff nœuds/mailles | `dc-circuits` section Kirchhoff, schéma nœud |
| T1.7 | Série/parallèle, division U/I, équivalents | `dc-circuits` Groupements, tableau comparatif natif et exemples ; outils |
| T1.8 | Circuits complexes/ponts, calculs mixtes | `dc-circuits` « Pont de résistances » ; les valeurs des exercices ne sont pas des faits supplémentaires |
| T2.1 | Sinusoïde, période/fréquence, formes, superposition, Fourier | `ac-circuits` Signaux |
| T2.2 | Crête, efficace, moyenne, crête à crête, sinusoïde + continu | `ac-circuits` Signaux et exemples, schéma sinusoïde |
| T2.3a–b | Opposition L/C, réactances, unités et facteur 159 | `ac-circuits` ; `physical-constants` ; `formulas` |
| T2.3c | C=εS/e, diélectriques, rigidité, codage, variable/polarisé, Q=CU, énergie | `ac-circuits` Composants et Énergie |
| T2.3d–f | Bobine, inductance, noyau, μ, énergie magnétique | `ac-circuits` « Bobine », « Perméabilité », « Solénoïde long », « Énergie stockée » |
| T2.3g | Groupements C/L, mutuelle inductance | `ac-circuits` Groupements et tableau comparatif L/C |
| T2.4 | Charge/décharge exacte RC, τ=RC, RL=L/R, surtension Lenz | `ac-circuits` Transitoires et schéma RC |
| T2.5 | R/X/Z, somme vectorielle, pertes et parasites, déphasage RLC | `ac-circuits` Impédance |
| T3.1 | Transfo, N, U/I/Z, VA, idéal | `transformers-batteries` Transformateurs ; schéma natif |
| T3.2 | Rendement, couplage, pertes, tôles/ferrite, autotransfo/isolement | `transformers-batteries` Transformateurs |
| T3.3 | Chimie piles/accus, FEM/Ri, capacité, court-circuit, recharge | `transformers-batteries` Sources et exemples |
| T3.4 | Galvanomètre, branchements, résistance série/shunt/calibres | `measurements` Branchement et Instruments ; schémas V/A |
| T3.5 | Ω/V et charge de mesure, numérique/analogique | `measurements` « Qualité en Ω/V », « Erreur de charge » |
| T3.6 | Ohmmètre/wattmètre, échelles de lecture | `measurements` Branchement et « Échelles analogiques » |
| T3.7 | Audio, micros, HP, relais, diode roue libre, circulateur | `measurements` Transducteurs |
| T4.1 | dB puissance/tension, logarithmes, références, chaîne de gains | `decibels`, tableaux natifs, conversion et chaîne de puissance dynamique ; `formulas` |
| T4.2 | RC passe-bas/haut, coupure, octave/décade, −3 dB, pente | `filters` RC et Types ; graphes/circuits natifs |
| T4.3 | LC passe-bas/haut, série/bouchon, Thomson, pente/ordre | `filters` LC, Montages et schémas de résonance |
| T4.4 | RLC réel, Z, Q série/parallèle, surtension, B=f₀/Q, facteur forme | `filters` RLC et tableau des trois modèles ; `ac-circuits` Impédance |
| T4.5 | π/T, adaptation et filtrage | `filters` Montages, schéma π |
| T4.6 | Variantes L/C/f/Q/R à la résonance | `filters` Calculs et facteurs Q ; `formulas` |

## Technique T5–T8 : électronique

| Sous-section | Référence utile | Fiche / repères |
|---|---|---|
| T5.1 | PN, anode/cathode, polarisation | `diodes-power` Diodes |
| T5.2 | Seuils Si/Ge, inverse/claquage, dopage | `diodes-power` Diodes |
| T5.3 | Redresseurs simple/double/pont, Varicap/Zener/LED/PIN/Schottky | `diodes-power` Familles et Redressement |
| T5.4 | Alimentation, √2Ueff, chutes de diodes, ripple/filtrage, régulation/ballast | `diodes-power` Alimentations et synoptique natif |
| T6.1 | Bipolaires, bornes, NPN/PNP, polarisation | `transistors-tubes` Bipolaires |
| T6.2 | β/hFE, Ic/Ib/Ie, limites fréquence/température | `transistors-tubes` Bipolaires |
| T6.3 | Émetteur/collecteur/base communs, gains, impédances, phase, commutation | `transistors-tubes` Montages |
| T6.4 | JFET/MOSFET, gm, grille/source/drain, doubles portes, UJT/thyristor/triac | `transistors-tubes` Effet de champ et Commutation |
| T6.5 | Diode thermoïonique, cathode/filament/plaque | `transistors-tubes` Tubes |
| T6.6 | Triode/écran/suppresseuse, tétrode/pentode | `transistors-tubes` Tubes, explicitement complément |
| T7.1 | Classes A/B/AB/C/D et conduction, linéarité/rendement | `amplifiers-oscillators` Amplification |
| T7.2 | Point repos, charge, blocage/saturation, écrêtage | `amplifiers-oscillators` « Charge et point de repos » |
| T7.3 | Liaisons directe/capacitive/transfo/accordée | `amplifiers-oscillators` « Couplage entre étages » |
| T7.4 | RF, découplage/choc, thermique, contre-réaction, distorsions | `amplifiers-oscillators` Amplification ; `transistors-tubes` thermique ; `emc-noise` produits parasites |
| T7.5 | Quartz/piézoélectricité, VFO/VCO/VXO, stabilité, PLL/DDS | `amplifiers-oscillators` Génération, synoptiques et rapports |
| T7.6 | Multiplication par entier, déviation FM, limites AM/BLU | `amplifiers-oscillators` « Multiplicateur », exemple |
| T7.7 | Produit/mélange vs superposition, sommes/différences | `amplifiers-oscillators` Conversion et exemple |
| T8.1 | AOP idéal, entrées +/−, gain ouvert, saturation | `logic-digital` AOP |
| T8.2 | Inverseur, contre-réaction, masse virtuelle, −R₂/R₁ | `logic-digital` « Montage inverseur » et schéma natif |
| T8.3 | Non-inverseur, suiveur, soustracteur, intégrateur, filtres actifs/PWM | `logic-digital` AOP ; classe D dans `amplifiers-oscillators` |
| T8.4 | Tables ET/OU/NON/NAND/NOR/XOR, niveaux et combinaisons | `binary-logic` Logique avec tableau de vérité natif et définitions |
| T8.5a–b | Bits/octets, binaire/hexadécimal, SI/IEC | `binary-logic` Binaire avec tableau natif des seize combinaisons |
| T8.5c | CRC/ARQ/FEC | `digital-signals` Signal numérique |
| T8.5d–h | CAN/CNA, Nyquist/alias, quantification, FFT/IQ, FIR/IIR/SAW | `digital-signals` Signal numérique ; `filters` « Quartz et SAW » |

## Technique T9–T12 : radioélectricité

| Sous-section | Référence utile | Fiche / repères |
|---|---|---|
| T9.1 | λ=v/f, vitesse, Doppler | `propagation` Fondamentaux et « Effet Doppler » ; `formulas` |
| T9.2 | Champs, modes sol/direct/ionosphère, gammes/longueurs | `propagation` Fondamentaux, Modes et Gammes |
| T9.3a–d | Ionosphère D/E/F1/F2, altitudes, jour/nuit, absorption | `propagation` Ionosphère |
| T9.3e–f | MUF/LUF/ECOF/FOT, parcours, bonds/zone silence | `propagation` Ionosphère |
| T9.3g | Cycle solaire, flux/taches, activité géomagnétique K/A | `propagation` Repères |
| T9.3h–j | Sol, diffraction, tropo/ducts/météores/aurore/EME/pluie | `propagation` Modes et Phénomènes |
| T9.4 | Dipôle, dimensions/raccourcissement, I/U, impédance | `antennas` Types ; dessin dipôle |
| T9.5 | Quart d’onde, plan/radians, dimensions/impédance, bobine charge | `antennas` Types et Environnement |
| T9.6 | Yagi, réflecteur/directeurs, lobes | `antennas` Types ; schéma Yagi |
| T9.7 | Gain isotrope/dipôle, diagrammes, champ proche/environnement | `antennas` Rayonnement |
| T9.8 | PAR/PIRE, perte de ligne, références dBi/dBd | `antennas` Puissance ; `transmitter-limits` |
| T9.9 | Ouverture −3 dB et rapport avant/arrière | `antennas` « Angle d’ouverture » |
| T9.10a | Densité, champ lointain, surface effective/Friis | `antennas` Puissance et « Champ et surface effective » |
| T9.10b–d | Nœuds/ventres, champs/polarisation | `antennas` courant/tension du dipôle, Rayonnement ; `propagation` Fondamentaux |
| T9.10e–j | Smith, rendement, trappes/multi-dipôles, couplage, ouvert/fermé, paraboles | `antennas` Types/Environnement ; `transmission-lines` « Diagramme de Smith » |
| T10.1 | Coax/bifilaire, pertes, modes différentiel/commun | `transmission-lines` Lignes ; schéma coaxial |
| T10.2 | Z₀, vélocité, diélectrique, guide d’onde | `transmission-lines` Lignes |
| T10.3 | Conjugaison, Γ, ROS/TOS, réflexion et pertes | `transmission-lines` Réflexion, tableau natif et exemple |
| T10.4a | λ/2 répétition, λ/4 inversion, adaptation, stubs | `transmission-lines` Adaptation |
| T10.4b | Balun courant/tension et transformation | `transmission-lines` Adaptation ; limites explicitement indiquées |
| T11.1 | Direct : RF/démod/audio | `radio-blocks` Réception, synoptique natif |
| T11.2 | Superhétérodyne, OL/FI, somme/différence, double conversion, DSP/SDR | `radio-blocks` Réception et synoptique |
| T11.3 | Image et filtrage avant mélange, formules/cas | `radio-blocks` « Fréquence image » et exemple 14/5/9/4 MHz |
| T11.4 | Sensibilité, S9 HF/µV/dBm, 6 dB/S, limites d’étalonnage | `radio-blocks` Qualité et tableau S-mètre natif de S0 à S9 + 30 dB ; interprétation des reports dans `reports` |
| T11.5 | Émetteur, conversion, filtre final, transceiver/commutation | `radio-blocks` Émission, synoptique natif |
| T11.6 | CEM, émission/immunité/susceptibilité, conduit/rayonné | `emc-noise` CEM |
| T11.7a–c | Intermodulation, IP3, transmodulation | `emc-noise` Non-linéarités |
| T11.7d–e | kTB/−174 dBm/Hz, bruit extérieur/propre, Friis bruit en cascade | `emc-noise` Bruit ; `physical-constants` |
| T12.1 | Amplitude/fréquence/phase, temps/spectre, AM/BLU/CW/FM/PM/numérique | `modulations` Principes/Modes/Numérique |
| T12.2 | Modulateurs/démodulateurs | `modulations` AM/BLU/FM ; `radio-blocks` |
| T12.3 | AM, enveloppe, CAG, taux/surmodulation, puissance/PEP | `modulations` AM et exemple ; `radio-blocks` « CAG / AGC » |
| T12.4 | FM/PM, déviation/indice/Carson, discriminateurs, limiteur/squelch/accentuation | `modulations` FM et exemple |
| T12.5 | CW, manipulation, piaulement/claquements, réception BFO | `modulations` CW et BLU |
| T12.6a–e | BLU/DBL, porteuse, bandes, mélangeur équilibré, quartz | `modulations` BLU ; `amplifiers-oscillators` Conversion ; `filters` |
| T12.6f–i | Deux tons, déphasage, BFO, affichage, conversion directe | `modulations` BLU ; `radio-blocks` Réception |

## Corrections de fond : ne pas propager les coquilles du cours

Les ressources suivent les notions utiles du cours, avec les corrections suivantes, documentées pour éviter des formulations séduisantes mais fausses :

- T2.4 : 63,2 % de charge / 36,8 % résiduel après τ ; les approximations « 2/3 » puis « 8/9 » du cours ne sont pas les fractions exactes. Les équations exponentielles sont indiquées.
- T6.1 : un transistor n’est pas équivalent à deux diodes séparées ; β dépend du point de fonctionnement et n’est pas applicable quelle que soit la tension collecteur.
- T6.4 : transconductance = variation du courant / variation de tension, pas systématiquement le quotient des valeurs absolues ; polarité MOSFET non généralisée à un seul cas.
- T8.5/T7.5 : CAN = ADC, CNA = DAC ; la mention « CNA ou ADC » dans le DDS est inversée. SI ko=1 000, IEC Kio=1 024.
- T9.1 : le Doppler ne modifie pas la vitesse de la lumière dans le vide ; rapprochement/éloignement change la fréquence observée.
- T10.4a4 : perméabilité μ₀ en H/m et permittivité ε₀ en F/m, contrairement aux intitulés/formules intervertis dans ce passage. Valeurs recoupées avec [CODATA 2022/NIST](https://physics.nist.gov/cuu/pdf/all.pdf).
- T11.7 : le gain d’une cascade est la somme des gains moins les pertes en dB ; le facteur de bruit n’est pas une perte à soustraire du gain. Le premier étage influe sur le bruit, pas sur l’addition des gains.
- T12.1 : avec M états, bits/symbole = log₂ M, pas M ; largeur de spectre FM n’est pas simplement 2Δf, d’où Carson. Ces relations sont également vérifiables par dimensions et définitions.
- T8.5 : un circulateur ferrite n’est pas un filtre SAW ; deux familles distinctes.

## Vérifications primaires et limites honnêtes

Les faits de droit cités dans les fiches sont des repères d’apprentissage, avec textes sources. Les règles d’examen, sanctions administratives, actualité du R421-9 et usurpation ont été recoupées sur des sources primaires le 4 octobre 2026 :

- [Arrêté du 21 septembre 2000 consolidé](https://www.legifrance.gouv.fr/loda/id/JORFTEXT000000401783/) — articles 2, 7, 7-3.
- [ANFR : certificats et examen](https://www.anfr.fr/gerer/radioamateurs/les-certificats).
- [ANFR : présentation de l’examen et relecture avant finalisation](https://www.anfr.fr/gerer/radioamateurs/presentation-des-epreuves-dexamen).
- [CPCE L39-1](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000031318701/) ; [L39-8](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000006465930).
- [Urbanisme R421-9 en vigueur](https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000037799137).
- Bandes/CEPT : recoupements de l’agent radio dans `docs/MEMO-RADIO-SOURCES.md` ; puissances et pureté spectrale suivent les valeurs de l’édition et les décisions qu’elle cite.
- Normes CISPR/ETSI : valeurs explicitement attribuées à l’édition ; l’app ne prétend ni remplacer la norme complète ni certifier la conformité d’un matériel.
- ROP : l’état du cours en novembre 2025 est explicite ; pas d’affirmation que l’absence de texte demeure certaine en octobre 2026.
- Installation : conditions de PLU, site protégé, exposition et foudre exigent l’évaluation réelle du site. Les modèles simples ne valent que sous leurs hypothèses.

Ce qui ne constitue pas une reproduction exhaustive : les biographies/histoire, coordonnées évolutives, chaque calcul numérique d’exemple, les planches détaillées de montage hors programme (Hartley/Colpitts/tubes et tous démodulateurs anciens), le guide complet de normes et chaque variante d’urbanisme. Les fonctions/notions utiles sont présentées et ces développements restent disponibles dans le cours complet. La matrice documente les correspondances par sous-section ; elle ne prétend pas qu’une fiche remplace mot à mot les 100 pages et toutes leurs illustrations, ni qu'un formateur a validé une équivalence exhaustive. Le périmètre de cette intégration est la bibliothèque Mémo ; l'allongement et la révision des leçons du parcours restent un travail distinct.

## Compatibilité et validation des données

La fusion centrale conserve les `cardId` des 390 anciennes flashcards. L’identifiant `propagation` est conservé. Ses 18 anciens termes de révision sont gardés : HF, VHF, UHF, SHF, Ionosphère, Couche D, Couches F, MUF, LUF, Fading, Sporadique E, Troposphère, Polarisation, Balun, Facteur de vélocité, Charge fictive, PAR, PIRE. Les cinq derniers existent comme `flashcard-only` pour conserver leurs sujets et identifiants pendant que leurs faits visibles ont rejoint la bonne fiche.

Contrôles structurels des données : JSON analysable, ids de catégories distincts, termes distincts dans chaque fiche, descriptions non vides, types fact/example/tip/flashcard-only connus, liens F6KGL pointant sur des ancres présentes dans l’archive, et clés visuelles prises en charge par le dispatch natif. Ces contrôles et les nombres ci-dessus décrivent les données intégrées ; ils ne constituent pas une réussite des tests Android finaux. Les résultats de compilation, des tests et de la revue visuelle sont consignés séparément dans [VALIDATION.md](VALIDATION.md) après exécution.
