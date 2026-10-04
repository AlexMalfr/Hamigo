// Original Hamigo exercises. Run with Node.js; stable original IDs are preserved.
import fs from 'node:fs';
const file = 'data/curriculum.json';
const data = JSON.parse(fs.readFileSync(file, 'utf8'));
const Q = (prompt, correct, distractors, explanation) => ({kind:'choice',prompt,choices:[correct,...distractors],answer:0,explanation});
const TF = (prompt, correct, explanation) => ({kind:'truefalse',prompt,choices:['Vrai','Faux'],answer:correct?0:1,explanation});
const N = (prompt,value,unit,explanation,tolerance=.01) => ({kind:'number',prompt,choices:[],answer:0,value,unit,tolerance,explanation});
const M = (prompt,pairs,explanation) => ({kind:'match',prompt,choices:[],answer:0,pairs:pairs.map(([left,right])=>({left,right})),explanation});
const O = (prompt,choices,explanation) => ({kind:'order',prompt,choices,answer:0,explanation});
const C = (prompt,correct,distractors,explanation) => ({...Q(prompt,correct,distractors,explanation),kind:'cloze'});
const S = (prompt,choices,indices,explanation) => ({kind:'multiselect',prompt,choices,answer:0,bands:indices.map(String),explanation});
const B = (prompt,value,bits,explanation) => ({kind:'binary',prompt,choices:[],answer:0,value,unit:String(bits),explanation});
const W = (prompt,names,choices,answer,explanation) => ({kind:'waveform',prompt,choices,answer,bands:names,explanation});
const additions = {
  'c01-l01': [
    'Le certificat s’attache à la personne, alors que l’indicatif identifie sa station dans les échanges. Écouter le trafic, construire des montages et préparer l’examen sont déjà des façons d’apprendre avant de pouvoir émettre. Une activité amateur n’autorise pas à vendre un service de transmission.',
    TF('La formation technique personnelle fait partie du service amateur.',true,'L’apprentissage et l’expérimentation sont des objectifs du service amateur.'),
    C('Pour être identifié sur les ondes, l’opérateur annonce son ___.','indicatif',['mot de passe','numéro de téléphone','niveau dans Hamigo'],'L’indicatif est l’identité radio attribuée à la station.'),
    S('Quelles activités correspondent au service amateur ?',['Expérimenter une antenne','Vendre du temps de transmission','Apprendre la radioélectricité','Diffuser un catalogue publicitaire'],[0,2],'Apprentissage et expérimentation personnelles correspondent au service amateur; le trafic commercial régulier n’en fait pas partie.'),
    Q('Alex prépare le certificat et souhaite déjà tester un circuit. Quel cadre convient ?','Un montage basse tension sans émission radio non autorisée',['Émettre avec un indicatif inventé','Utiliser l’indicatif d’un inconnu','Vendre des communications'],'On peut apprendre et construire avant le certificat; cela ne donne pas le droit d’émettre sans autorisation.')
  ],
  'c01-l02': [
    'Les deux résultats comptent séparément : une bonne note technique ne compense pas une note de réglementation sous le seuil. Comme les mauvaises réponses n’enlèvent pas de point, il reste utile de choisir une réponse réfléchie avant la fin du temps. Réserve une petite marge pour relire les unités et les mots comme « toujours » ou « jamais ».',
    N('Combien de bonnes réponses au minimum faut-il en réglementation ?',10,'/20','Le seuil est 10/20 dans chacune des deux parties.',0),
    TF('Un résultat de 9/20 en technique et 19/20 en réglementation suffit.',false,'Il faut au moins 10/20 dans chaque partie; les scores ne se compensent pas.'),
    Q('Tu réussis réglementation avec 12/20 et technique avec 11/20. Quel résultat ?','Les deux parties sont réussies',['Seule la réglementation est réussie','Il faut 15/20 partout','Il faut additionner les durées'],'12 et 11 sont chacun au moins égaux à 10.'),
    N('20 questions de réglementation en 15 minutes : temps moyen par question en secondes ?',45,'s','15 × 60 / 20 = 45 s. Certaines questions prendront moins de temps pour garder une marge.',0)
  ],
  'c01-l03': [
    'Une identification claire aide les autres opérateurs et les services de contrôle à savoir qui émet. Elle est nécessaire même pour une conversation brève entre amis. Les mots de l’alphabet international évitent de confondre des lettres voisines; ils complètent l’indicatif, ils ne le remplacent pas.',
    TF('Une conversation entre deux amis dispense d’annoncer l’indicatif.',false,'L’identité de la station doit rester reconnaissable même dans un échange privé entre amateurs.'),
    Q('Que faire au début d’une nouvelle période d’émission ?','Identifier sa station',['Attendre le prochain quart d’heure','Émettre anonymement si la bande est calme','Donner uniquement son prénom'],'L’identification intervient notamment au début de la période d’émission.'),
    C('Pour épeler les lettres de l’indicatif sans ambiguïté, on utilise l’alphabet ___.','international',['hexadécimal','publicitaire','secret'],'L’alphabet international associe un mot distinct à chaque lettre.'),
    Q('À la fin d’une période d’émission, quel repère faut-il donner ?','L’indicatif de la station',['Le mot de passe du poste','Uniquement le modèle de l’antenne','Un pseudonyme au choix'],'L’identification intervient aussi à la fin de la période d’émission.')
  ],
  'c01-l04': [
    'Le droit d’utiliser une bande et la bonne façon de s’y organiser sont deux choses complémentaires. Les limites nationales disent où l’émission amateur est autorisée. Les plans de bande recommandent une répartition des modes; toute la largeur du signal doit rester dans la bande autorisée. Écouter une fréquence évite d’interrompre un contact déjà en cours.',
    TF('Un statut secondaire donne priorité sur les services primaires.',false,'Le service secondaire ne doit pas causer de brouillage aux services primaires et ne peut pas exiger leur protection.'),
    S('Avant un appel, quels gestes sont utiles ?',['Écouter la fréquence','Vérifier la bande autorisée','Émettre immédiatement à pleine puissance','Respecter le plan de bande'],[0,1,3],'Écouter, vérifier les limites et choisir la partie adaptée au mode sont des réflexes complémentaires.'),
    Q('Une émission possède une largeur non nulle. Où doit-elle tenir ?','Entièrement dans la bande autorisée',['Seule sa fréquence centrale doit être dedans','Une moitié suffit','Ses harmoniques définissent la bande'],'La totalité de l’émission utile doit rester dans les limites autorisées.'),
    Q('La fréquence 145,5 MHz se situe dans quelle bande amateur métropolitaine ?','2 m',['20 m','40 m','80 m'],'La bande des 2 m s’étend de 144 à 146 MHz en France métropolitaine.')
  ],
  'c02-l01': [
    'L’épellation n’est pas une traduction libre. Chaque lettre garde son mot, même si un nom de ville semble plus familier. En particulier, les graphies officielles sont Alfa et Juliett. Entraîne-toi sur ton prénom, puis sur des groupes mélangés : la récupération du mot doit devenir rapide et régulière.',
    M('Relie ces nouvelles lettres à leur mot international.',[['B','Bravo'],['D','Delta'],['M','Mike'],['V','Victor']],'Un seul mot conventionnel représente chaque lettre.'),
    Q('Quel mot représente la lettre W ?','Whiskey',['William','Washington','Water'],'W s’épelle Whiskey.'),
    C('Dans l’alphabet international, la lettre X s’épelle ___.','X-ray',['Xylophone','Xavier','Xenon'],'X-ray est le mot conventionnel de X.'),
    O('Épelle le groupe VHF dans son ordre de lecture.',['Victor','Hotel','Foxtrot'],'V = Victor, H = Hotel, F = Foxtrot.')
  ],
  'c02-l02': [
    'Les codes Q sont des raccourcis de trafic : une forme interrogative demande une information, et une réponse peut la fournir. Ils n’ont pas besoin d’être multipliés dans une conversation en phonie; une phrase claire reste utile. Distingue QRM, une gêne provenant d’autres signaux, de QRN, le bruit d’origine atmosphérique.',
    M('Associe les codes de bruit et de puissance.',[['QRM','Brouillage par d’autres signaux'],['QRN','Parasites atmosphériques'],['QRP','Faible puissance'],['QRO','Forte puissance']],'QRM et QRN décrivent deux origines de gêne; QRP et QRO concernent la puissance.'),
    Q('Quel code demande une diminution de vitesse en télégraphie ?','QRS',['QTH','QSL','QSY'],'QRS demande de ralentir; QRQ demande d’accélérer.'),
    TF('QTH concerne la localisation de la station.',true,'QTH désigne la position ou le lieu de la station.'),
    C('Pour confirmer la bonne réception d’un échange, on peut utiliser ___.','QSL',['QRT','QSY','QRN'],'QSL exprime une confirmation de réception.')
  ],
  'c02-l03': [
    'On apprend le Morse comme un rythme, pas seulement comme un dessin. Un point vaut une unité, un trait trois; à l’intérieur d’une lettre, la pause vaut une unité. Entre deux lettres, laisse trois unités, et entre deux mots sept. Le chapitre dédié entraîne ensuite toutes les lettres, les chiffres, l’écoute et la composition.',
    N('Entre deux lettres Morse, la pause dure combien d’unités ?',3,'unités','La pause entre lettres vaut trois durées de point.',0),
    N('Entre deux mots Morse, combien d’unités de silence ?',7,'unités','La pause entre mots vaut sept durées de point.',0),
    {...Q('Écoute le signal et choisis la lettre.','A',['N','E','T'],'Pour A, compose un point puis un trait : .-'),kind:'morseListen',bands:['.-']},
    {kind:'morseEncode',prompt:'Compose la lettre N avec les boutons point et trait.',choices:[],answer:0,bands:['-.'],explanation:'N = trait puis point : ━•. Les lettres A et N sont inversées.'}
  ],
  'c02-l04': [
    'Un contact réussi donne des informations compréhensibles, sans les répéter inutilement. Écoute d’abord, annonce l’identité de la station appelée et la tienne, puis échange le report et les informations utiles. En phonie, le report RS utilise la lisibilité sur cinq et la force sur neuf; en télégraphie, RST ajoute la tonalité.',
    Q('Dans un report RS, la première note décrit…','La lisibilité',['La puissance consommée','La longueur du câble','La fréquence'],'R est la lisibilité, graduée de 1 à 5.'),
    Q('Dans un report RS, la seconde note décrit…','La force du signal',['La lisibilité','Le nombre de contacts','La vitesse du Morse'],'S est la force du signal, graduée de 1 à 9.'),
    TF('Dire seulement « 59 » dispense d’identifier sa station.',false,'Le report ne remplace pas l’indicatif.'),
    S('Quelles informations peuvent aider à établir un QSO ?',['Les indicatifs','Un report de réception','Un mot de passe bancaire','La localisation'],[0,1,3],'Identité, report et localisation sont des informations ordinaires d’un contact.')
  ],
  'c03-l01': [
    'Écris d’abord la grandeur dans l’unité de la formule : ampères, volts, ohms, farads ou henrys. Un milli vaut 10⁻³, un micro 10⁻⁶, un kilo 10³. Les préfixes ne sont pas des unités nouvelles; ils servent à écrire des nombres plus petits. Le carré ou le produit d’une grandeur exige de convertir avant de calculer.',
    N('Convertis 2,2 µF en nF.',2200,'nF','1 µF = 1 000 nF, donc 2,2 µF = 2 200 nF.',0),
    N('Combien de volts font 750 mV ?',.75,'V','750 / 1 000 = 0,75 V.',.001),
    C('Le préfixe nano correspond à 10 puissance ___.','−9',['−3','3','6'],'Nano vaut un milliardième, soit 10⁻⁹.'),
    M('Associe le préfixe à son facteur.',[['kilo','1 000'],['milli','0,001'],['micro','0,000001'],['méga','1 000 000']],'Un préfixe multiplie la grandeur exprimée dans son unité de base.')
  ],
  'c03-l02': [
    'Dans un circuit fermé, un déplacement net de charges constitue le courant. La tension décrit une différence de potentiel entre deux points : elle peut exister même si aucun courant ne circule. Le courant conventionnel utilise le déplacement de charges positives; dans un métal, les électrons se déplacent en sens opposé.',
    TF('Une pile peut présenter une tension alors que le circuit est ouvert.',true,'Une différence de potentiel peut exister sans circulation de courant.'),
    N('Un courant de 0,5 A circule pendant 10 s. Quelle charge passe ?',5,'C','Q = I × t = 0,5 × 10 = 5 C.',.001),
    Q('Dans un métal, les électrons se déplacent…','En sens opposé au courant conventionnel',['Dans le même sens par définition','Uniquement en l’absence de tension','Comme des charges positives'],'L’électron porte une charge négative; le courant conventionnel est défini dans le sens positif.'),
    C('Un courant de 1 A correspond à une charge de 1 ___ par seconde.','coulomb',['volt','ohm','joule'],'I = Q/t, donc 1 A = 1 C/s.')
  ],
  'c03-l03': [
    'La loi d’Ohm concerne un conducteur ohmique dans des conditions données. Choisis d’abord l’inconnue : U = RI, I = U/R ou R = U/I. Avant de valider, vérifie le sens du résultat : sous une même tension, une résistance plus grande doit laisser passer moins de courant. Un ordre de grandeur repère les erreurs de préfixe.',
    N('5 V aux bornes de 1 kΩ : quel courant en mA ?',5,'mA','I = 5/1 000 = 0,005 A = 5 mA.',.01),
    N('Une résistance de 220 Ω laisse passer 20 mA. Quelle tension ?',4.4,'V','20 mA = 0,020 A; U = 220 × 0,020 = 4,4 V.',.01),
    TF('À résistance constante, doubler la tension double le courant.',true,'I = U/R : quand R est constante, I est proportionnel à U.'),
    Q('9 V et 3 mA donnent quel ordre de grandeur de résistance ?','Quelques kΩ',['Quelques mΩ','Plusieurs MΩ','Moins de 1 Ω'],'9/0,003 = 3 000 Ω = 3 kΩ. Convertir les mA est essentiel.')
  ],
  'c03-l04': [
    'La puissance est un débit d’énergie : le watt vaut un joule par seconde. Une facture ou une batterie emploie souvent des Wh; pour des joules, le temps doit être en secondes. Pour une résistance ohmique, P = UI = RI² = U²/R. La puissance nominale doit laisser une marge de température.',
    N('20 W pendant 30 minutes : quelle énergie en Wh ?',10,'Wh','30 minutes = 0,5 h; E = 20 × 0,5 = 10 Wh.',.01),
    N('Une résistance de 100 Ω est parcourue par 0,1 A. Quelle puissance ?',1,'W','P = RI² = 100 × 0,1² = 1 W.',.01),
    N('Une puissance de 3 W est dissipée pendant 10 s. Quelle énergie ?',30,'J','E = P × t = 3 × 10 = 30 J.',.01),
    TF('À résistance constante, doubler la tension multiplie la puissance par quatre.',true,'P = U²/R : le carré de deux vaut quatre.')
  ],
  'c04-l01': [
    'En série, il n’existe qu’un chemin pour le courant. La résistance équivalente est la somme, et les chutes de tension s’additionnent. À courant identique, la plus grande résistance présente la plus grande chute de tension. Une coupure sur ce chemin interrompt le courant dans toute la série.',
    N('47 Ω, 47 Ω et 100 Ω en série : résistance équivalente ?',194,'Ω','Req = 47 + 47 + 100 = 194 Ω.',.01),
    N('100 Ω et 200 Ω en série sous 9 V : tension sur 200 Ω ?',6,'V','Le courant vaut 9/300 = 0,03 A; la chute vaut 200 × 0,03 = 6 V.',.01),
    TF('Dans une série, la résistance la plus grande reçoit le plus grand courant.',false,'Le même courant traverse toutes les résistances en série.'),
    Q('Une résistance d’un circuit série devient complètement ouverte. Que se passe-t-il ?','Le courant est interrompu dans la série',['Le courant double','La résistance totale diminue','La tension de la source disparaît forcément'],'Le chemin unique du courant est coupé.')
  ],
  'c04-l02': [
    'En parallèle, chaque branche rejoint les mêmes deux points : la tension est donc commune, mais les courants se répartissent. La conductance totale est la somme des inverses des résistances. Pour deux branches, Req = R1R2/(R1 + R2). N résistances identiques R donnent R/N.',
    N('Trois résistances de 300 Ω en parallèle : résistance équivalente ?',100,'Ω','Trois branches identiques donnent 300/3 = 100 Ω.',.01),
    N('200 Ω et 200 Ω en parallèle sous 10 V : courant total en mA ?',100,'mA','Req = 100 Ω; I = 10/100 = 0,1 A = 100 mA.',.01),
    Q('100 Ω et 200 Ω en parallèle reçoivent la même tension. Quelle branche a le plus de courant ?','La branche de 100 Ω',['La branche de 200 Ω','Les deux obligatoirement autant','Aucune branche'],'I = U/R : sous la même tension, la plus petite résistance prend le plus de courant.'),
    TF('Ajouter une branche résistive positive en parallèle diminue la résistance équivalente.',true,'Une branche supplémentaire augmente la conductance totale.')
  ],
  'c04-l03': [
    'La formule du diviseur suppose que la sortie ne prélève pas un courant significatif. Une charge placée sur R2 lui est parallèle et diminue sa résistance équivalente : la tension de sortie descend. Dans un pont équilibré, les deux points de mesure ont le même potentiel, même si les branches restent parcourues par des courants.',
    N('Diviseur non chargé : R1 = 3 kΩ, R2 = 1 kΩ, entrée 12 V. Sortie sur R2 ?',3,'V','Us = Ue × R2/(R1 + R2) = 12 × 1/4 = 3 V.',.01),
    TF('Un pont équilibré signifie que tous ses courants sont nuls.',false,'C’est le courant dans la diagonale de mesure qui est nul; les branches peuvent conduire.'),
    Q('Pour préserver une tension de diviseur, l’appareil de mesure doit avoir…','Une grande résistance d’entrée',['Une résistance d’entrée proche de zéro','Une sortie en court-circuit','Une batterie vide'],'Une grande résistance d’entrée prélève peu de courant et charge peu le diviseur.'),
    N('Diviseur : R1 = R2 = 1 kΩ, entrée 10 V. Une charge de 1 kΩ est parallèle à R2. Nouvelle sortie, au centième ?',10/3,'V','R2 // charge = 500 Ω; Us = 10 × 500/1 500 ≈ 3,33 V.',.015)
  ],
  'c04-l04': [
    'Dessine des flèches de courant et choisis un sens de parcours pour la maille avant de poser les équations. Un résultat négatif dit que le courant réel est opposé à la flèche choisie, pas que le circuit est impossible. Aux nœuds, les charges ne s’accumulent pas durablement en régime établi.',
    N('Au nœud : 1,5 A et 0,5 A entrent, 0,8 A sortent. Autre courant sortant ?',1.2,'A','La somme entrante est 2 A; il reste 2 − 0,8 = 1,2 A.',.01),
    N('Dans une maille de 24 V, deux chutes sont 7 V et 9 V. Troisième chute ?',8,'V','24 − 7 − 9 = 8 V.',.01),
    Q('Une résolution donne I = −2 A selon la flèche choisie. Cela signifie…','2 A dans le sens opposé à la flèche',['Une énergie négative interdite','L’absence de courant','Une résistance nulle'],'Le signe exprime le sens relatif à la convention choisie.'),
    C('Dans une boucle fermée, la somme algébrique des tensions est ___.','nulle',['toujours 1 V','égale à la somme des résistances','infinie'],'La loi des mailles traduit la conservation de l’énergie.')
  ],
  'c05-l01': [
    'Pour quatre anneaux, lis deux chiffres significatifs, puis le multiplicateur et la tolérance. À cinq anneaux, trois chiffres précèdent le multiplicateur. Les anneaux or et argent correspondent souvent à la tolérance et donnent un repère de sens de lecture. Vérifie la valeur calculée par un ordre de grandeur.',
    {...Q('Quelle valeur indiquent brun, noir, rouge, or ?','1 kΩ',['100 Ω','10 kΩ','12 Ω'],'Brun = 1, noir = 0, rouge = ×100 : 10 × 100 = 1 000 Ω.'),kind:'resistor',bands:['brun','noir','rouge','or']},
    N('Une résistance de 220 Ω à ±5 % : écart maximal autour de la valeur nominale ?',11,'Ω','220 × 0,05 = 11 Ω.',.01),
    Q('Quelle couleur représente le chiffre 6 ?','Bleu',['Vert','Violet','Gris'],'Les chiffres sont noir 0, brun 1, rouge 2, orange 3, jaune 4, vert 5, bleu 6, violet 7, gris 8, blanc 9.'),
    TF('À cinq anneaux, trois anneaux donnent les chiffres significatifs.',true,'Le quatrième anneau est alors le multiplicateur et le cinquième la tolérance.')
  ],
  'c05-l02': [
    'Un condensateur oppose une variation instantanée de tension; sa charge vaut Q = CU. Deux condensateurs en parallèle additionnent leurs capacités, alors qu’en série on additionne les inverses. Les modèles réels ont une tension maximale, des pertes et parfois une polarité à respecter.',
    N('Deux condensateurs de 100 nF en série : capacité équivalente ?',50,'nF','Deux capacités identiques en série donnent C/2 = 50 nF.',.01),
    N('C = 10 µF sous 5 V : charge stockée en µC ?',50,'µC','Q = CU = 10 µF × 5 V = 50 µC.',.01),
    TF('Un condensateur électrolytique polarisé peut se monter dans n’importe quel sens.',false,'Il faut respecter sa polarité et sa tension maximale.'),
    C('La réactance capacitive XC vaut 1 / (2π f ___).','C',['L','R','P'],'XC diminue quand f ou C augmente; C s’exprime en farads.')
  ],
  'c05-l03': [
    'Une bobine oppose une variation rapide de courant. Sa réactance XL = 2πfL augmente avec la fréquence; en continu établi, le modèle idéal ressemble à un fil. Une bobine réelle possède aussi une résistance de cuivre et peut comporter un noyau magnétique qui sature.',
    N('À 1 kHz, une bobine de 10 mH a quelle réactance, au dixième ?',62.83,'Ω','XL = 2π × 1 000 × 0,010 ≈ 62,8 Ω.',.15),
    TF('Le courant d’une bobine idéale peut changer instantanément sans tension infinie.',false,'La bobine s’oppose aux variations du courant; u = L × di/dt.'),
    Q('En continu établi, une bobine idéale se comporte comme…','Un court-circuit',['Un circuit ouvert','Un condensateur chargé','Un redresseur'],'Sans variation de courant, sa tension idéale est nulle. La résistance réelle de cuivre reste présente.'),
    M('Associe chaque composant à sa réaction.',[['Résistance','Dissipe de l’énergie'],['Condensateur','S’oppose aux variations de tension'],['Bobine','S’oppose aux variations de courant']],'Les composants passifs n’agissent pas tous sur la même grandeur.')
  ],
  'c05-l04': [
    'La constante τ = RC fixe l’échelle de temps d’une évolution exponentielle. À chaque τ, la part qui reste à parcourir est multipliée par environ 0,368. Après 5τ, la charge a atteint environ 99,3 % de sa valeur finale, et la décharge laisse environ 0,7 % de sa valeur initiale.',
    N('R = 2,2 kΩ et C = 100 µF : constante de temps ?',.22,'s','2 200 × 0,0001 = 0,22 s.',.001),
    N('Une constante de temps de 0,2 s : temps correspondant à 5τ ?',1,'s','5 × 0,2 = 1 s.',.01),
    TF('Après 5τ de charge, un condensateur idéal a exactement atteint 100 %.',false,'La charge est asymptotique; après 5τ elle atteint environ 99,3 %.'),
    Q('Si R double et C est divisée par deux, τ devient…','Inchangée',['Double','Quatre fois plus grande','Quatre fois plus petite'],'Le produit R × C reste identique.')
  ],
  'c06-l01': [
    'Une sinusoïde peut se décrire par son amplitude, sa fréquence et sa phase. Une valeur moyenne nulle ne veut pas dire qu’elle ne transporte aucune énergie. Pour une résistance, les demi-alternances positive et négative dissipent toutes deux de la puissance. Lorsque les crêtes positive et négative sont symétriques autour de zéro, la valeur crête à crête vaut deux fois la crête.',
    W('Choisis la forme d’une tension sinusoïdale centrée sur zéro.',['sine','square','dc'],['Sinusoïde','Créneau','Tension continue'],0,'Une sinusoïde évolue régulièrement; sa forme n’est ni plate ni faite de plateaux abrupts.'),
    N('Sinusoïde centrée, 12 V crête à crête : amplitude de crête ?',6,'V','La crête vaut la moitié de la valeur crête à crête.',.01),
    TF('Une tension alternative peut changer de signe au cours du temps.',true,'Une sinusoïde centrée passe alternativement au-dessus et au-dessous de zéro.'),
    Q('Quelle grandeur exprime le nombre de cycles par seconde ?','La fréquence',['La résistance','La charge','La phase'],'Le hertz est un cycle par seconde.')
  ],
  'c06-l02': [
    'La période se mesure sur deux points successifs de même phase, par exemple deux sommets. T = 1/f exige des secondes et des hertz. À l’oscilloscope, multiplie le nombre de divisions horizontales par le temps par division pour trouver T, puis prends l’inverse.',
    N('Une période de 20 µs correspond à quelle fréquence en kHz ?',50,'kHz','f = 1/(20 × 10⁻⁶) = 50 000 Hz = 50 kHz.',.01),
    N('Fréquence 5 kHz : période en µs ?',200,'µs','T = 1/5 000 = 0,0002 s = 200 µs.',.01),
    N('Un cycle occupe 4 divisions à 0,5 ms/div. Quelle période en ms ?',2,'ms','4 × 0,5 = 2 ms.',.01),
    TF('Une période quatre fois plus grande correspond à une fréquence quatre fois plus petite.',true,'Fréquence et période sont inverses.')
  ],
  'c06-l03': [
    'La valeur efficace d’une tension produit la même puissance moyenne dans une résistance que la tension continue correspondante. Pour une sinusoïde sans composante continue, Ueff = Ucrête/√2. La phase est une avance ou un retard dans le cycle; elle n’est pas une fréquence supplémentaire.',
    N('Sinusoïde de 14,14 V de crête : valeur efficace approximative ?',10,'V','14,14/√2 ≈ 10 V.',.03),
    N('Un décalage de moitié de période représente combien de degrés ?',180,'°','Un cycle complet vaut 360°, donc une moitié vaut 180°.',0),
    TF('La valeur efficace de toute forme d’onde vaut sa crête divisée par √2.',false,'Ce facteur concerne une sinusoïde pure centrée, pas toutes les formes d’onde.'),
    Q('Dans une résistance de 100 Ω, 10 V efficaces dissipent quelle puissance ?','1 W',['0 W','10 W','100 W'],'P = Ueff²/R = 100/100 = 1 W.')
  ],
  'c06-l04': [
    'L’impédance rassemble la partie résistive et la partie réactive. En série, les réactances inductive et capacitive se soustraient avec leur signe : X = XL − XC. Le module vaut √(R² + X²), donc on n’additionne pas simplement R et X. À résonance série, X devient nul.',
    N('R = 60 Ω, XL = 100 Ω, XC = 20 Ω en série : module Z ?',100,'Ω','X = 80 Ω; Z = √(60² + 80²) = 100 Ω.',.01),
    TF('Si XL = XC en série, la réactance résultante est nulle.',true,'Les effets réactifs inductif et capacitif se compensent à cette fréquence.'),
    Q('Avec XL plus grande que XC, le circuit série est globalement…','Inductif',['Capacitif','Toujours purement résistif','Sans courant possible'],'X = XL − XC est positive, donc le caractère est inductif.'),
    N('R = 10 Ω et X = 0 Ω : module de l’impédance ?',10,'Ω','Z = √(10² + 0²) = 10 Ω.',.01)
  ],
  'c07-l01': [
    'La résonance est la fréquence où les échanges entre champs électrique et magnétique compensent les réactances. f0 = 1/(2π√LC). Un circuit série devient surtout résistif à f0; un circuit parallèle idéal possède au contraire une impédance très grande. Les pertes réelles limitent toujours ces cas idéaux.',
    N('Résonance à 10 MHz, bande passante de 100 kHz : facteur Q ?',100,'','Q = f0/B = 10 000 kHz/100 kHz = 100.',.01),
    Q('Si L et C sont chacune multipliées par deux, la fréquence de résonance devient…','La moitié',['Le double','Le quadruple','Inchangée'],'LC est multiplié par quatre; √LC double, donc f0 est divisée par deux.'),
    TF('Un facteur Q plus élevé correspond à une bande passante plus étroite à f0 fixé.',true,'B = f0/Q : augmenter Q diminue B.'),
    Q('À résonance, l’impédance d’un LC parallèle idéal est…','Très grande, infinie dans le modèle sans perte',['Nulle','Toujours 50 Ω','Toujours égale à XL seul'],'Les courants réactifs se compensent à l’entrée; les pertes réelles rendent l’impédance finie.')
  ],
  'c07-l02': [
    'Un filtre laisse passer une zone de fréquence et atténue une autre; son action est progressive. La fréquence de coupure se définit souvent à −3 dB par rapport au niveau en bande passante. La pente s’exprime en dB par octave ou par décade. Un filtre n’améliore pas une émission déjà hors des limites légales.',
    Q('Quel filtre rejette une petite zone autour d’une fréquence gênante ?','Coupe-bande',['Passe-bas','Passe-haut','Redresseur'],'Le coupe-bande, ou réjecteur, atténue une zone et laisse passer les autres.'),
    TF('−3 dB en puissance signifie une tension divisée par deux à impédance constante.',false,'La puissance est approximativement divisée par deux; la tension est multipliée par environ 0,707.'),
    C('Un passe-haut atténue les fréquences situées ___ sa coupure.','sous',['au-dessus de','exactement au double de','à n’importe quelle distance de'],'Un passe-haut favorise les fréquences élevées.'),
    N('Avec une pente de 6 dB par octave, passer de 1 MHz à 4 MHz dans la zone de pente donne combien de dB supplémentaires ?',12,'dB','1 → 2 MHz puis 2 → 4 MHz font deux octaves; 2 × 6 = 12 dB.',.01)
  ],
  'c07-l03': [
    'Pour un transformateur idéal, Us/Up = Ns/Np et les puissances sont égales : UpIp = UsIs. Le rapport des impédances est le carré du rapport des spires. Le modèle suppose un couplage parfait et aucune perte; un vrai transformateur chauffe et sa réponse dépend de la fréquence.',
    N('Np = 200, Ns = 20, entrée 230 V alternatif. Sortie idéale ?',23,'V','Us = 230 × 20/200 = 23 V.',.01),
    N('Un transformateur idéal fournit 12 V et 2 A. Sous 120 V au primaire, courant absorbé ?',.2,'A','Puissance = 24 W; Ip = 24/120 = 0,2 A.',.001),
    TF('Le rapport d’impédances d’un transformateur idéal est le rapport des spires au carré.',true,'Zs/Zp = (Ns/Np)².'),
    Q('Un transformateur classique connecté à une tension continue constante…','Ne transforme pas durablement cette tension comme en alternatif',['Fournit un secondaire alternatif illimité','Double obligatoirement le courant','Fonctionne sans limite de courant'],'Il faut une variation du flux pour induire une tension; une alimentation continue peut saturer et échauffer le transformateur.')
  ],
  'c07-l04': [
    'Le décibel compare deux puissances : 10 log10(P2/P1). Pour deux tensions à impédance identique, le rapport devient 20 log10(U2/U1). Les gains et pertes en dB s’additionnent. Un dBm est une puissance absolue référencée à 1 mW, alors qu’un dB seul est un rapport.',
    N('Une puissance passe de 10 W à 1 W. Gain algébrique ?',-10,'dB','10 log10(1/10) = −10 dB. La perte vaut 10 dB.',.01),
    N('Gain de 6 dB, perte de 2 dB, puis gain de 10 dB : gain net ?',14,'dB','6 − 2 + 10 = 14 dB.',.01),
    N('30 dBm représentent quelle puissance en watts ?',1,'W','30 dB au-dessus de 1 mW donne 1 000 mW = 1 W.',.01),
    TF('dB et dBm désignent exactement la même grandeur.',false,'dB exprime un rapport; dBm une puissance par rapport à 1 mW.')
  ],
  'c08-l01': [
    'La diode réelle demande une tension directe et possède une limite de courant. Un pont de redressement utilise deux diodes conductrices à chaque alternance; un condensateur peut ensuite réduire l’ondulation. Une Zener fonctionne en inverse dans sa zone prévue avec une limitation de courant, pas comme un court-circuit magique.',
    Q('Dans un pont redresseur, combien de diodes conduisent sur une alternance ?', 'Deux',['Une','Trois','Les quatre en même temps'],'Deux diodes forment le chemin du courant pour chaque alternance.'),
    TF('Une Zener nécessite une limitation de courant dans un montage de stabilisation.',true,'Sans limitation, le courant peut dépasser ses caractéristiques et la détruire.'),
    N('Redressement double alternance d’un réseau à 50 Hz : fréquence de l’ondulation ?',100,'Hz','Les deux alternances donnent deux bosses par période, soit 100 Hz.',.01),
    C('Une LED est une diode qui émet de la ___ en conduction.','lumière',['pression','fréquence fixe de 50 Hz','charge positive uniquement'],'Une LED est une diode électroluminescente; elle demande aussi une limitation de courant.')
  ],
  'c08-l02': [
    'La polarisation fixe le point de repos avant d’ajouter le signal. Le gain β d’un bipolaire n’est pas une constante universelle : il dépend du composant et du fonctionnement. Un FET est commandé par la tension grille-source. En amplification, il faut garder une marge pour éviter la coupure ou la saturation.',
    N('Ic = 40 mA et β = 100 : courant de base en mA ?',.4,'mA','Ib = Ic/β = 40/100 = 0,4 mA.',.001),
    M('Associe les bornes.',[['Bipolaire : entrée courante','Base'],['FET : commande','Grille'],['Bipolaire : courant de sortie','Collecteur'],['FET : borne commune usuelle','Source']],'Les noms des bornes dépendent de la famille du transistor.'),
    TF('Une polarisation inadaptée peut déformer le signal amplifié.',true,'Le point de repos doit laisser une excursion suffisante sans coupure ni saturation.'),
    Q('Quel paramètre commande principalement un FET ?','La tension grille-source',['La résistance de la LED','La fréquence du secteur','Le nom du boîtier'],'VGS commande le canal; les limites de la grille doivent être respectées.')
  ],
  'c08-l03': [
    'Le rendement compare la puissance utile à la puissance absorbée; il ne peut pas dépasser 100 % pour un amplificateur ordinaire. Les pertes deviennent surtout de la chaleur. La linéarité concerne la fidélité du signal, pas seulement le rendement. Une contre-réaction négative échange du gain contre une réponse souvent plus stable et moins déformée.',
    N('Puissance absorbée 80 W, puissance utile 40 W : rendement ?',50,'%','η = 100 × 40/80 = 50 %.',.01),
    N('Ce montage absorbe 80 W et fournit 40 W utiles. Pertes ?',40,'W','80 − 40 = 40 W non délivrés comme puissance utile.',.01),
    TF('Un gain de 20 dB garantit une bonne linéarité.',false,'Le gain et la fidélité du signal sont deux caractéristiques distinctes.'),
    Q('Un étage qui écrête les crêtes du signal produit généralement…','De la distorsion et des composantes supplémentaires',['Une amélioration automatique de la pureté','Une baisse obligatoire de fréquence','Une tension continue parfaite'],'L’écrêtage est non linéaire et génère des harmoniques ou de l’intermodulation.')
  ],
  'c08-l04': [
    'Un oscillateur entretenu compense ses pertes par un apport d’énergie. Un quartz apporte une résonance stable, mais sa température et son environnement ont encore un effet. En logique, ET demande deux entrées vraies; OU demande au moins une; NON inverse l’état. L’état logique ne décrit pas directement une tension universelle.',
    Q('Une porte NON reçoit 1. Quelle sortie ?','0',['1','2','Un état toujours inconnu'],'NON inverse l’état logique.'),
    Q('Une porte OU reçoit 0 et 0. Quelle sortie ?','0',['1','2','Elle oscille forcément'],'OU vaut 1 si au moins une entrée vaut 1.'),
    TF('Une porte ET reçoit 1 et 1 : sa sortie vaut 1.',true,'ET vaut 1 uniquement quand toutes ses entrées valent 1.'),
    Q('Pour entretenir une oscillation réelle malgré les pertes, il faut…','Un apport d’énergie et une réaction adaptée',['Seulement une résistance passive','Une alimentation toujours débranchée','Un fil sans courant'],'Un oscillateur actif compense les pertes de son circuit résonant ou temporisé.')
  ],
  'c09-l01': [
    'Une AM sinusoïdale possède une porteuse à fc et des bandes latérales à fc − fm et fc + fm. Pour une bande audio de largeur Fmax, la largeur RF double bande vaut environ 2Fmax. Un taux de modulation supérieur à 100 % déforme l’enveloppe dans le modèle simple et crée un risque d’émission trop large.',
    N('Porteuse 1 MHz, audio 2 kHz : fréquence de la bande latérale supérieure en kHz ?',1002,'kHz','1 MHz = 1 000 kHz; 1 000 + 2 = 1 002 kHz.',.01),
    N('Même signal : fréquence de la bande latérale inférieure en kHz ?',998,'kHz','1 000 − 2 = 998 kHz.',.01),
    W('Quelle forme représente une porteuse dont l’amplitude varie avec le message ?',['am','fm','sine'],['AM','FM','Sinusoïde non modulée'],0,'En AM, l’enveloppe varie; en FM idéale l’amplitude reste constante.'),
    TF('Une bande audio jusqu’à 4 kHz demande environ 8 kHz en AM double bande.',true,'La largeur vaut approximativement deux fois la fréquence audio maximale.')
  ],
  'c09-l02': [
    'En FM, la fréquence instantanée suit le message et l’amplitude idéale reste constante. L’indice de modulation sinusoïdale vaut β = Δf/fm. La règle de Carson estime B ≈ 2(Δf + Fmax); elle n’est pas la description exacte de toutes les raies du spectre. En PM, c’est la phase qui suit directement le message.',
    N('FM : Δf = 5 kHz et Fmax = 3 kHz. Largeur Carson ?',16,'kHz','B ≈ 2 × (5 + 3) = 16 kHz.',.01),
    N('FM sinusoïdale : excursion 3 kHz et modulation 1 kHz. Indice β ?',3,'','β = Δf/fm = 3/1 = 3.',.01),
    W('Choisis la forme dont l’amplitude reste constante mais dont les cycles se resserrent et s’écartent.',['fm','am','dc'],['FM','AM','Continu'],0,'La FM change la fréquence instantanée, pas l’enveloppe idéale.'),
    TF('Augmenter l’excursion FM augmente généralement la largeur occupée.',true,'La règle de Carson augmente avec Δf; rester dans la largeur prévue évite les débordements.')
  ],
  'c09-l03': [
    'La BLU économise de la puissance et de la bande en gardant une seule bande latérale, avec une porteuse fortement supprimée. Un signal audio de 1 kHz produit une composante à fc + 1 kHz en USB, ou fc − 1 kHz en LSB. La CW forme des symboles en manipulant une porteuse; des fronts excessivement abrupts produisent des clics et élargissent le spectre.',
    N('USB référencée à 14,200 MHz, tonalité audio 1 kHz : composante RF en MHz ?',14.201,'MHz','USB place la composante au-dessus : 14,200 + 0,001 = 14,201 MHz.',.0001),
    Q('LSB signifie…','Bande latérale inférieure',['Bande latérale supérieure','Largeur de signal binaire','Signal de balise local'],'LSB = Lower Side Band.'),
    TF('La BLU transmet normalement les deux bandes latérales de l’AM.',false,'La BLU ne garde qu’une bande latérale.'),
    Q('Des fronts CW trop brusques risquent de produire…','Des clics de manipulation et un spectre plus large',['Une fréquence exactement nulle','Une réception toujours parfaite','Une modulation FM obligatoire'],'Adoucir les fronts de manipulation limite les composantes indésirables.')
  ],
  'c09-l04': [
    'Le baud compte les symboles par seconde; le bit par seconde compte l’information binaire. M états distincts peuvent représenter log2(M) bits par symbole. Le débit utile tient ensuite compte de l’encodage, de la correction d’erreurs et des protocoles. Une constellation dense exige généralement une réception plus fiable.',
    N('8 états distincts représentent combien de bits par symbole ?',3,'bits','2³ = 8, donc trois bits peuvent choisir un état parmi huit.',0),
    N('2 400 bauds à 3 bits par symbole, sans surcoût : débit ?',7200,'bit/s','2 400 × 3 = 7 200 bit/s.',0),
    B('Compose le nombre décimal 5 avec quatre bits.',5,4,'5 = 4 + 1, soit 0101 sur quatre bits.'),
    TF('Le débit utile peut être inférieur au débit binaire transmis.',true,'En-têtes et correction d’erreurs prennent une part du débit.')
  ],
  'c10-l01': [
    'Le signal traverse plusieurs étages, chacun avec un rôle. La modulation crée le message RF; le mélangeur transpose des fréquences; l’amplificateur de puissance augmente l’énergie utile. Le filtre de sortie réduit les produits indésirables, et l’adaptation facilite le transfert vers la ligne. Un amplificateur saturé peut salir toute la chaîne.',
    N('Un mélangeur reçoit 8 MHz et 3 MHz. Produit différence positif ?',5,'MHz','Les produits élémentaires sont 8 + 3 = 11 MHz et |8 − 3| = 5 MHz.',.01),
    N('Un émetteur à 7 MHz produit une troisième harmonique. Fréquence ?',21,'MHz','La troisième harmonique vaut 3 × 7 = 21 MHz.',.01),
    M('Relie les blocs de l’émetteur à leur rôle.',[['Modulateur','Insérer l’information'],['Mélangeur','Transposer la fréquence'],['Amplificateur RF','Augmenter la puissance'],['Filtre de sortie','Atténuer les produits indésirables']],'Chaque étage a une fonction différente dans la chaîne.'),
    TF('Un filtre de sortie peut réduire des harmoniques d’émission.',true,'Un passe-bas adapté laisse la fréquence utile et atténue ses harmoniques supérieures.')
  ],
  'c10-l02': [
    'Le superhétérodyne convertit la fréquence reçue vers une fréquence intermédiaire plus facile à filtrer. Le mélangeur répond à plusieurs combinaisons : une autre fréquence peut donner la même FI, appelée fréquence image. Un filtrage avant le mélangeur est donc nécessaire; la FI seule ne permet pas de les distinguer.',
    N('Signal 10 MHz, oscillateur local 19 MHz : FI différence ?',9,'MHz','FI = |19 − 10| = 9 MHz.',.01),
    N('OL = 19 MHz et FI = 9 MHz, réception utile 10 MHz. Fréquence image supérieure ?',28,'MHz','La fréquence 28 MHz donne aussi |28 − 19| = 9 MHz.',.01),
    Q('Quel étage aide à rejeter la fréquence image avant conversion ?','Le présélecteur RF',['Le haut-parleur','Le BFO seul','Le réglage du volume'],'Un filtre RF avant le mélangeur réduit le signal image.'),
    TF('Deux fréquences différentes peuvent produire la même FI dans un mélangeur.',true,'La somme et la différence créent des possibilités de conversion multiples.')
  ],
  'c10-l03': [
    'La sensibilité ne suffit pas si un fort signal voisin masque le faible signal recherché. La sélectivité vient notamment des filtres, et la dynamique décrit la capacité à gérer des niveaux très différents. Les non-linéarités fabriquent de l’intermodulation; atténuer l’entrée peut parfois améliorer une réception saturée.',
    Q('Un très fort signal voisin sature l’entrée. Quelle action peut aider ?','Insérer une atténuation adaptée',['Augmenter systématiquement le gain RF','Retirer tous les filtres','Changer seulement le volume audio'],'L’atténuation peut rendre l’étage d’entrée plus linéaire si les signaux utiles restent exploitables.'),
    TF('Un récepteur plus sensible est automatiquement plus sélectif.',false,'Sensibilité et sélectivité sont des qualités distinctes.'),
    C('L’AGC ajuste le ___ pour limiter les variations de niveau.','gain',['diamètre de l’antenne','nombre de spires de la prise secteur','débit du Morse'],'AGC signifie contrôle automatique de gain.'),
    Q('Que décrit surtout la dynamique d’un récepteur ?','La gestion de signaux faibles et forts sans dégradation excessive',['Son poids','La couleur de son écran','La longueur de son câble secteur'],'La dynamique concerne les niveaux de signal et la résistance à la surcharge.')
  ],
  'c10-l04': [
    'Une mesure modifie toujours un peu le circuit. Un voltmètre doit prélever peu de courant, donc avoir une forte résistance d’entrée; un ampèremètre se place en série et présente une faible résistance. Pour mesurer une résistance, coupe l’alimentation et assure-toi que les condensateurs sont déchargés. Choisis un calibre et des cordons compatibles.',
    Q('Pour mesurer une tension entre deux points, le voltmètre se branche…','En parallèle entre ces points',['En série à la place du fil','Seulement sur la terre','Sur la sortie audio au hasard'],'Il mesure la différence de potentiel entre les deux points.'),
    Q('Pour mesurer le courant d’une branche, l’ampèremètre se branche…','En série dans cette branche',['Directement en parallèle sur la pile','Sur la masse uniquement','Sans contact avec le circuit'],'Le courant à mesurer doit traverser l’appareil.'),
    TF('Une résistance se mesure normalement dans un circuit alimenté.',false,'L’alimentation doit être coupée et les charges résiduelles contrôlées.'),
    S('Avant une mesure, que vérifier ?',['Le calibre','Le mode tension/courant/résistance','Les bornes où sont branchés les cordons','Uniquement la couleur du boîtier'],[0,1,2],'Mode, calibre et connexions conditionnent une mesure correcte et sûre.')
  ],
  'c11-l01': [
    'Dans l’air, λ = c/f, avec c proche de 300 millions de mètres par seconde. Une écriture pratique est λ(m) ≈ 300/f(MHz). Dans une ligne, la vitesse est plus faible : le facteur de vélocité multiplie la longueur d’onde correspondante. Toujours distinguer longueur d’onde dans l’air et longueur électrique dans un câble.',
    N('À 450 MHz, longueur d’onde dans l’air, au centième ?',2/3,'m','λ ≈ 300/450 ≈ 0,67 m.',.015),
    N('À 100 MHz, longueur d’onde dans une ligne de facteur 0,66 ?',1.98,'m','Dans l’air λ = 3 m; dans la ligne λ = 0,66 × 3 = 1,98 m.',.01),
    TF('Une ligne de facteur de vélocité 0,66 propage plus vite que la lumière dans le vide.',false,'Elle propage à 0,66 fois la vitesse de référence.'),
    N('Une longueur d’onde de 2 m dans l’air correspond à quelle fréquence ?',150,'MHz','f ≈ 300/2 = 150 MHz.',.01)
  ],
  'c11-l02': [
    'Les formules λ/2 et λ/4 donnent un point de départ idéal, pas la cote finale de toutes les antennes. Le diamètre, les isolateurs et les objets voisins modifient la résonance. Le dipôle est symétrique; le coaxial est asymétrique. Un balun adapté aide à gérer cette transition et les courants indésirables sur la gaine.',
    N('À 50 MHz, longueur théorique totale d’un dipôle demi-onde ?',3,'m','λ ≈ 300/50 = 6 m; λ/2 = 3 m.',.01),
    N('À 150 MHz, longueur théorique d’un quart d’onde ?',.5,'m','λ = 2 m; λ/4 = 0,5 m.',.01),
    TF('Une longueur calculée idéale garantit la résonance après installation.',false,'L’environnement et la construction modifient l’antenne; on mesure et ajuste.'),
    Q('Un dipôle alimenté au centre comporte idéalement…','Deux bras symétriques',['Un seul fil forcément relié au secteur','Une charge fictive sans rayonnement','Quatre diodes'],'Les deux bras créent une structure symétrique de part et d’autre du point d’alimentation.')
  ],
  'c11-l03': [
    'Le gain redistribue le rayonnement : il augmente l’intensité dans certaines directions au prix d’autres directions. dBi compare à l’isotrope; dBd compare au dipôle demi-onde et diffère d’environ 2,15 dB. La polarisation décrit l’orientation du champ électrique. Le diagramme, le gain et l’adaptation sont trois propriétés différentes.',
    N('Une antenne annonce 5 dBd. Gain en dBi ?',7.15,'dBi','Gain(dBi) ≈ gain(dBd) + 2,15 = 7,15 dBi.',.01),
    TF('Une antenne passive crée de l’énergie pour obtenir son gain.',false,'Le gain vient de la directivité et du rendement, pas d’une création de puissance.'),
    Q('La polarisation linéaire décrit principalement l’orientation…','Du champ électrique',['Du courant de la prise secteur','Du câble USB','De la résistance de charge'],'Le champ électrique définit la polarisation.'),
    Q('Une antenne plus directive doit souvent être…','Orientée vers la station recherchée',['Branchée sans câble','Éloignée de toute alimentation','Réglée uniquement en audio'],'Le gain maximal est concentré dans certaines directions.')
  ],
  'c11-l04': [
    'Le ROS compare les amplitudes maximale et minimale sur une ligne. Une adaptation idéale donne 1; une forte réflexion ne signifie pas que toute l’énergie est forcément dissipée dans l’antenne. Les pertes du câble peuvent masquer une mauvaise adaptation mesurée côté poste. Une charge fictive peut donner un excellent ROS sans rayonner utilement.',
    N('Charge résistive 25 Ω sur ligne 50 Ω : ROS idéal ?',2,'','Pour une charge purement résistive, ROS = plus grande résistance / plus petite = 50/25 = 2.',.01),
    N('100 W entrent dans une ligne ayant 6 dB de perte. Puissance approximative à la sortie ?',25,'W','6 dB représentent approximativement une division par quatre : 100/4 = 25 W.',.5),
    TF('Un câble très dissipatif peut améliorer le ROS vu au poste sans améliorer l’antenne.',true,'Les réflexions sont elles aussi atténuées; le bon ROS peut cacher les pertes.'),
    Q('Quelle charge est conçue pour absorber la puissance RF de test sans rayonner utilement ?','Une charge fictive adaptée',['Une antenne Yagi','Un haut-parleur','Un simple fil de longueur inconnue'],'Elle dissipe la puissance et doit posséder la bonne impédance et une tenue en puissance suffisante.')
  ],
  'c12-l01': [
    'L’ionosphère évolue avec l’heure, la saison et l’activité solaire. La couche D absorbe surtout les fréquences HF basses de jour; les couches supérieures permettent de nombreux trajets lointains. La fréquence utilisable dépend du trajet et de l’angle, pas seulement du chiffre sur le cadran. Il n’existe pas une bande gagnante à toute heure.',
    TF('La fréquence maximale utilisable est identique pour tous les trajets.',false,'La MUF dépend notamment de la géométrie du trajet et de l’état de l’ionosphère.'),
    Q('L’absorption de la couche D est généralement plus marquée…','De jour',['Uniquement pendant une pleine lune','Quand le câble est court','En l’absence de Soleil'],'L’ionisation diurne de la couche D renforce l’absorption de certaines fréquences HF.'),
    Q('Une zone de silence HF peut apparaître…','Entre la portée de l’onde de sol et le retour ionosphérique',['Seulement dans un câble coaxial','Parce que tous les récepteurs s’éteignent','Uniquement au-dessus de 10 GHz'],'La première onde ionosphérique peut revenir au-delà de la portée de l’onde de sol.'),
    C('Pour choisir une bande HF, on tient compte du trajet, de l’heure et de l’activité ___.','solaire',['publicitaire','du ventilateur','du clavier'],'L’activité solaire influence l’ionisation et les conditions de propagation.')
  ],
  'c12-l02': [
    'En VHF et UHF, la visibilité et le dégagement prennent beaucoup d’importance, sans exclure les trajets par réflexion, diffraction ou propagation exceptionnelle. Un satellite en déplacement crée un décalage Doppler : l’approche augmente la fréquence reçue, l’éloignement la diminue. Un contact satellite demande de suivre à la fois le passage et les fréquences.',
    TF('Un satellite qui s’éloigne produit généralement un décalage Doppler vers une fréquence reçue plus basse.',true,'L’éloignement étire les périodes reçues et diminue la fréquence.'),
    Q('La liaison descendante va…','Du satellite vers la station au sol',['De la station au satellite','D’une pile au poste','Du micro au haut-parleur'],'Uplink monte vers le satellite; downlink descend vers le sol.'),
    Q('Pour une liaison VHF terrestre habituelle, quel choix améliore souvent le trajet ?','Une antenne dégagée des obstacles',['Un câble plus dissipatif','Une antenne dans une armoire métallique','Une fréquence hors bande'],'Hauteur et dégagement facilitent la propagation en visibilité.'),
    S('Quels phénomènes peuvent permettre un trajet radio sans visibilité directe parfaite ?',['Diffraction','Réflexion','Absence de toute énergie','Propagation troposphérique'],[0,1,3],'Les ondes peuvent être diffractées ou réfléchies; la troposphère peut modifier leur propagation.')
  ],
  'c12-l03': [
    'Pose le bilan en dB pour additionner les contributions : puissance émise, gains d’antenne et pertes. Soustrais aussi les pertes de ligne. La PIRE utilise le gain par rapport à l’isotrope, alors que la PAR utilise le gain par rapport au dipôle. Le bilan estime un niveau; il ne garantit pas une qualité sans connaître bruit et largeur de réception.',
    N('20 dBm, gain antenne 6 dBi, câble 2 dB : PIRE en dBm ?',24,'dBm','20 + 6 − 2 = 24 dBm.',.01),
    N('10 dBm à l’antenne émettrice, gains 2 et 3 dBi, trajet 80 dB : reçu ?',-65,'dBm','10 + 2 + 3 − 80 = −65 dBm.',.01),
    TF('PAR et PIRE utilisent la même antenne de référence.',false,'La PAR est référencée au dipôle; la PIRE à l’isotrope.'),
    Q('À PIRE égale, améliorer le gain de réception agit surtout sur…','Le niveau disponible au récepteur',['La puissance déjà sortie de l’émetteur','La vitesse de la lumière','Le certificat de l’opérateur'],'Le gain de réception intervient au bout du trajet dans le bilan.')
  ],
  'c12-l04': [
    'La bande choisie doit permettre le trajet, le mode et la largeur souhaités, tout en restant autorisée. Les appellations en mètres sont des noms historiques arrondis : 20 m désigne par exemple une zone proche de 14 MHz, pas exactement 15 MHz. Les plans de bande organisent la cohabitation des modes à l’intérieur des limites nationales.',
    M('Associe ces bandes à une fréquence repère.',[['80 m','3,6 MHz'],['40 m','7,1 MHz'],['20 m','14,2 MHz'],['70 cm','433 MHz']],'Les désignations en longueur d’onde sont des repères arrondis.'),
    TF('La fréquence centrale suffit : les bords du signal peuvent dépasser la bande autorisée.',false,'Toute la largeur du signal doit rester dans la bande.'),
    Q('Un signal large près d’une limite de bande demande…','Une marge tenant compte de toute sa largeur',['Aucune marge','Un indicatif plus long','Un câble audio plus court'],'La fréquence centrale doit laisser assez de place pour les bandes latérales.'),
    C('Un plan de bande est un guide de cohabitation entre ___ dans une bande autorisée.','modes et usages',['numéros de téléphone','puissances commerciales','mots de passe'],'Il organise les usages sans créer de nouveaux droits hors bande.')
  ],
  'c13-l01': [
    'Le danger ne se résume pas à un appareil branché : une capacité chargée peut conserver de l’énergie après coupure. Pour apprendre, privilégie la très basse tension et une alimentation limitée en courant. Un fusible protège surtout contre les surintensités; il ne remplace ni les protections de personnes ni une procédure de mise en sécurité.',
    TF('Un appareil débranché est forcément dépourvu de toute énergie électrique stockée.',false,'Les condensateurs et batteries peuvent encore stocker de l’énergie.'),
    Q('Pour un premier montage pédagogique, quel support privilégier ?','Une alimentation très basse tension limitée en courant',['Un montage ouvert directement sur le secteur','Une haute tension sans protection','Un condensateur chargé inconnu'],'Cela réduit les risques tout en permettant d’apprendre les lois du circuit.'),
    C('Un fusible correctement choisi protège principalement contre les ___.','surintensités',['erreurs d’indicatif','défauts de propagation','mauvaises réponses au QCM'],'Il coupe en cas de courant excessif selon ses caractéristiques.'),
    S('Avant une intervention adaptée à tes compétences, quels points sont utiles ?',['Couper les sources d’énergie','Contrôler les charges résiduelles','Identifier les risques','Se fier uniquement au voyant éteint'],[0,1,2],'Le voyant ne prouve pas l’absence de tension ou d’énergie stockée.')
  ],
  'c13-l02': [
    'L’installation d’une antenne doit considérer la mécanique, les distances aux lignes, la météo et l’exposition radio. Le champ reçu dépend de la puissance, du gain, du trajet et du temps d’émission. Une bonne orientation ne suffit pas à elle seule pour vérifier les limites. Prépare l’accès et l’arrêt d’émission avant un travail près des antennes.',
    TF('Couper l’émission est une précaution avant un travail à proximité immédiate d’une antenne.',true,'L’installation doit être mise en sécurité avant une intervention.'),
    Q('Un haubanage sert principalement à…','Stabiliser mécaniquement le mât',['Réduire le nombre de lettres de l’indicatif','Créer une autorisation d’émettre','Transformer les watts en décibels'],'Les efforts mécaniques et le vent doivent être pris en compte.'),
    S('Quels paramètres influencent l’exposition RF ?',['Puissance émise','Gain et direction de l’antenne','Temps d’émission','Couleur du boîtier'],[0,1,2],'Puissance, diagramme, distance et durée contribuent à l’exposition.'),
    Q('Une installation peut chuter sur une ligne électrique. Quelle décision ?','Choisir une implantation sûre avant installation',['Installer puis attendre un accident','Compter uniquement sur le fusible du poste','Émettre à faible puissance pour régler le risque mécanique'],'Le risque de contact avec une ligne impose une implantation sûre.')
  ],
  'c13-l03': [
    'Un brouillage peut provenir d’une émission indésirable ou de l’immunité insuffisante d’un équipement voisin. Il faut rechercher le chemin du problème : rayonnement, câbles, alimentation ou courants de mode commun. Filtres, blindage et ferrites ont des rôles différents; choisir au hasard n’assure pas la résolution.',
    TF('Une ferrite peut aider à limiter un courant RF indésirable sur un câble.',true,'Une ferrite adaptée peut augmenter l’impédance des courants de mode commun.'),
    M('Associe le chemin de perturbation au moyen à envisager.',[['Rayonnement vers un circuit','Blindage adapté'],['Harmoniques à la sortie RF','Filtre de sortie'],['RF sur une gaine de câble','Dispositif de mode commun'],['Parasites via alimentation','Filtrage d’alimentation']],'Identifier le chemin aide à choisir une correction appropriée.'),
    Q('Un brouillage survient. Quel premier réflexe utile ?','Identifier les conditions et le chemin de couplage',['Augmenter immédiatement la puissance','Accuser sans vérifier','Supprimer toutes les mises à la terre'],'Une recherche méthodique permet de distinguer émission et immunité.'),
    TF('Un fonctionnement correct exige seulement un faible ROS.',false,'Pureté spectrale, couplages et immunité comptent aussi.')
  ],
  'c13-l04': [
    'Le carnet sert à retrouver un échange : date, heure, indicatif correspondant, fréquence ou bande et autres informations requises. Un déplacement n’étend pas les bandes autorisées. Pour un voyage, vérifie les règles du pays hôte, les conditions de reconnaissance du certificat et les préfixes applicables; n’infère pas une autorisation depuis une application.',
    TF('Les règles du pays hôte restent importantes lors d’un séjour à l’étranger.',true,'La reconnaissance d’un certificat ne remplace pas les conditions locales d’utilisation.'),
    Q('Pour relire un contact dans le journal, quelle donnée identifie le correspondant ?','Son indicatif',['La couleur du micro','Le pourcentage de batterie','Le mot de passe du Wi-Fi'],'L’indicatif du correspondant est un repère fondamental du contact.'),
    S('Quelles informations concernent un contact radio ?',['Date et heure','Indicatif du correspondant','Fréquence ou bande','Mot de passe bancaire'],[0,1,2],'Ces informations permettent de retrouver et contrôler un contact; un secret bancaire n’a aucun rôle.'),
    Q('Avant un voyage radio, où vérifier les conditions applicables ?','Dans les informations officielles du pays hôte',['Dans un souvenir ancien uniquement','Dans le nom de l’antenne','Dans la couleur des anneaux'],'Les droits et conditions peuvent dépendre du pays et de la situation.')
  ],
  'c14-l01': [
    'Les questions de réglementation demandent autant de précision que les calculs. Repère le pays, le statut de service, le type de station et les mots qui restreignent la situation. Une bonne habitude de trafic ne remplace pas une condition légale. Ce défi vérifie les principes; les textes datés restent la référence.',
    TF('Un plan de bande peut agrandir une bande autorisée par le droit national.',false,'Il organise les usages à l’intérieur des limites autorisées.'),
    Q('Une station secondaire peut-elle exiger la protection d’un service primaire ?','Non',['Oui, si elle a plus de puissance','Oui, si elle utilise la BLU','Oui, après un premier QSO'],'Elle doit protéger les services primaires et ne peut pas exiger leur protection.'),
    N('Quelle note minimale faut-il dans chacune des deux épreuves ?',10,'/20','Il faut au moins 10/20 séparément en réglementation et en technique.',0),
    S('Quelles situations demandent une identification ?',['Début de période d’émission','Fin de période d’émission','Début après changement de fréquence','Seulement quand on utilise plus de 100 W'],[0,1,2],'L’identification n’est pas réservée aux stations de forte puissance.')
  ],
  'c14-l02': [
    'Pour réussir une question technique, commence par la grandeur demandée et vérifie les unités. Calcule ensuite, puis teste l’ordre de grandeur. Une résistance équivalente en parallèle doit être inférieure à la plus petite branche; une perte de ligne doit réduire la puissance; une fréquence plus grande doit réduire la longueur d’onde.',
    N('24 V et 0,5 A : puissance consommée ?',12,'W','P = UI = 24 × 0,5 = 12 W.',.01),
    N('Deux résistances de 330 Ω en série : résistance totale ?',660,'Ω','En série, on additionne : 330 + 330 = 660 Ω.',.01),
    N('À 30 MHz, longueur théorique d’un quart d’onde ?',2.5,'m','λ = 300/30 = 10 m; λ/4 = 2,5 m.',.01),
    N('Émetteur 20 W, perte de ligne 3 dB : puissance approximative à l’antenne ?',10,'W','3 dB de perte divisent approximativement la puissance par deux.',.2)
  ],
  'c14-l03': [
    'Un entraînement chronométré apprend à décider quand continuer et quand revenir sur un calcul. Vérifie les questions laissées sans réponse avant l’échéance. Les exemples d’Hamigo ne sont pas une garantie de questions identiques à l’examen; le but est de reconnaître les relations et les règles même avec d’autres valeurs.',
    Q('Une question de calcul semble longue. Quel choix aide à gérer le temps ?','La repérer et avancer, puis y revenir si possible',['Y consacrer tout le temps restant sans réfléchir','Abandonner toutes les autres questions','Changer les unités au hasard'],'Répartir le temps permet de ne pas perdre les questions accessibles.'),
    TF('Le QCM actuel retire des points pour une réponse fausse.',false,'Une erreur vaut zéro point, sans retrait.'),
    N('45 minutes pour 40 questions au total : temps moyen global en secondes par question ?',67.5,'s','45 × 60 / 40 = 67,5 s. Les deux parties ont néanmoins leur propre durée.',.01),
    C('Après le calcul, vérifier les ___ aide à détecter une erreur de préfixe.','unités',['couleurs du clavier','initiales des distracteurs','noms des agents'],'Une bonne unité et un bon ordre de grandeur sont des contrôles rapides.')
  ],
  'c14-l04': [
    'Une première station gagne à rester simple, mesurable et sûre. Vérifie l’alimentation, la charge de test, la ligne puis l’antenne avant le trafic. Monte progressivement la puissance et écoute ton environnement radio. Un carnet de réglages aide à retrouver ce qui a changé et à éviter les corrections aveugles.',
    Q('Avant de raccorder une antenne inconnue à l’émetteur, quel contrôle est utile ?','Mesurer son adaptation et vérifier sa compatibilité',['Émettre immédiatement à puissance maximale','Retirer tous les filtres','Choisir uniquement selon la couleur'],'Une antenne doit être adaptée au poste, à la fréquence et à la puissance utilisée.'),
    TF('La charge fictive doit supporter la puissance et la durée de l’essai.',true,'Sa capacité thermique et son impédance doivent convenir à l’essai.'),
    S('Quels éléments rendent une première station plus facile à diagnostiquer ?',['Schéma simple','Mesures de référence','Carnet de réglages','Modifications multiples sans note'],[0,1,2],'Une configuration connue et des mesures comparables facilitent le diagnostic.'),
    Q('Un contact passe bien à faible puissance. Que faire généralement ?','Garder une puissance suffisante sans l’augmenter inutilement',['Monter toujours au maximum','Émettre hors bande','Modifier la modulation au hasard'],'Une puissance adaptée suffit et limite les gênes et la consommation.')
  ]
};

function finishQuestion(q,id,section,topic) {
  q={...q,id,section,topic};
  if (['choice','cloze','resistor','morseListen'].includes(q.kind) && q.choices.length>1) {
    const offset = [...id].reduce((sum,c)=>sum+c.charCodeAt(0),0)%q.choices.length;
    const answer=q.answer;
    q.choices=q.choices.map((_,i)=>q.choices[(i+offset)%q.choices.length]);
    q.answer=(answer-offset+q.choices.length)%q.choices.length;
  }
  return q;
}

for(const chapter of data.chapters.filter(c=>Number(c.id.slice(1))<=14)) {
  for(const lesson of chapter.lessons) {
    const addition=additions[lesson.id];
    if(!addition) throw Error('No reviewed expansion for '+lesson.id);
    const [paragraph,...questions]=addition;
    if(!lesson.body.includes(paragraph)) lesson.body.push(paragraph);
    const section=lesson.questions[0]?.section || 'technique';
    for(let i=0;i<questions.length;i++) {
      const id=`${lesson.id}-q${String(i+5).padStart(2,'0')}`;
      const existing=lesson.questions.findIndex(q=>q.id===id);
      const question=finishQuestion(questions[i],id,section,lesson.topic);
      if(existing>=0) lesson.questions[existing]=question; else lesson.questions.push(question);
    }
  }
}

const newChapters=[];
function chapter(id,title,subtitle,color,icon,lessons) {
  const entry={id,title,subtitle,color,icon,lessons:lessons.map((lesson,index)=> {
    const lessonId=`${id}-l${String(index+1).padStart(2,'0')}`;
    return {...lesson,id:lessonId,questions:lesson.questions.map((q,n)=>finishQuestion(q,`${lessonId}-q${String(n+1).padStart(2,'0')}`,lesson.section || 'technique',lesson.topic))};
  })};
  newChapters.push(entry);return entry;
}
const L=(title,summary,topic,body,formula,questions,section='technique')=>({title,summary,topic,body,formula,questions,section});

// Dedicated Morse curriculum follows in the second part of this content script.
const morse={A:'.-',B:'-...',C:'-.-.',D:'-..',E:'.',F:'..-.',G:'--.',H:'....',I:'..',J:'.---',K:'-.-',L:'.-..',M:'--',N:'-.',O:'---',P:'.--.',Q:'--.-',R:'.-.',S:'...',T:'-',U:'..-',V:'...-',W:'.--',X:'-..-',Y:'-.--',Z:'--..',0:'-----',1:'.----',2:'..---',3:'...--',4:'....-',5:'.....',6:'-....',7:'--...',8:'---..',9:'----.','?':'..--..','/':'-..-.','.':'.-.-.-',',':'--..--','@':'.--.-.','=':'-...-'};
const visual=code=>code.replaceAll('.','•').replaceAll('-','━');
const encode=word=>[...word].map(char=>char===' '?'/' : morse[char]).join(' ');
const listen=(target,choices,prompt='Écoute le rythme puis choisis le caractère.')=>({...Q(prompt,target,choices.filter(c=>c!==target).slice(0,3),`${target} correspond à ${visual(morse[target] || encode(target))}.`),kind:'morseListen',bands:[morse[target] || encode(target)]});
const compose=(target)=>({kind:'morseEncode',prompt:`Compose ${target.length===1?'le caractère':'le groupe'} ${target} en Morse.`,choices:[],answer:0,bands:[morse[target] || encode(target)],explanation:`${target} = ${visual(morse[target] || encode(target))}. Pour un groupe, sépare les lettres.`});
const group=(title,letters,hint)=>L(title,letters.join(' · ')+' : entendre, reconnaître, composer','Morse',[
  `Cette étape travaille ${letters.join(', ')}. ${hint} Écoute le caractère entier, puis prononce son nom : l’objectif est de reconnaître un rythme, sans compter chaque élément à haute vitesse.`,
  letters.map(letter=>`${letter} : ${visual(morse[letter])}`).join('   ·   '),
  'L’exercice alterne la lecture d’un code, l’écoute d’un son et la composition avec points et traits. La réponse à l’écoute est une lettre; à la composition, utilise les séparateurs uniquement entre les lettres d’un groupe. Rejoue le son si nécessaire.'
], 'point 1 · trait 3 · pause interne 1 · entre lettres 3 · entre mots 7',[
  ...letters.flatMap((letter,index)=>[
    Q(`Quel caractère correspond à ${visual(morse[letter])} ?`,letter,letters.filter(l=>l!==letter).concat(['E','T','A','N'].filter(l=>l!==letter && !letters.includes(l))).slice(0,3),`${letter} correspond à ${visual(morse[letter])}.`),
    listen(letter,[letter,...letters.filter(l=>l!==letter)]),
    compose(letter)
  ]),
  M('Relie les caractères à leurs signaux Morse.',letters.slice(0,5).map(letter=>[letter,visual(morse[letter])]),'Reconnais chaque signal comme un rythme complet.'),
  TF('Une lettre se termine avec une pause plus longue que la pause entre ses points et traits.',true,'La pause interne vaut une unité; la pause entre lettres en vaut trois.')
]);
chapter('c15','Le Morse, de A à Z','Un vrai atelier : tout l’alphabet, chiffres, écoute et manipulation','#FFB75C','morse',[
  group('Points, traits et premiers rythmes',['E','T','I','M'],'Compare le point E au trait T, puis les deux points I aux deux traits M.'),
  group('Inversions et triplets',['A','N','S','O'],'A et N utilisent les mêmes éléments dans l’ordre inverse. S et O sont trois points ou trois traits.'),
  group('Trouver le centre du rythme',['D','U','R','K'],'D commence par un trait et U finit par un trait; R et K alternent avec un centre opposé.'),
  group('Du court au long',['G','W','B','V'],'G et W se répondent; B et V déplacent le trait du début à la fin.'),
  group('Les lettres à quatre éléments',['F','L','P','J'],'Observe où se trouvent les traits : F et L n’en ont qu’un; P et J en ont plusieurs.'),
  group('Finir l’alphabet',['C','Q','X','Y','Z','H'],'Les six caractères complètent A à Z. H contient quatre points; écoute les groupes longs d’un seul souffle.'),
  group('Les chiffres de 1 à 5',['1','2','3','4','5'],'Chaque chiffre possède cinq éléments. De 1 à 5, le nombre de points initiaux augmente.'),
  group('Les chiffres de 6 à 0',['6','7','8','9','0'],'De 6 à 0, les traits initiaux augmentent. Zéro contient cinq traits, à distinguer des trois traits de O.'),
  L('Le silence fait partie du message','Distinguer éléments, lettres et mots','Morse',[
    'Les pauses servent de séparateurs. Une même succession de points sans séparation peut représenter un caractère différent de plusieurs lettres courtes. La cadence compte autant que le choix des points et traits.',
    'Dans la temporisation de référence, un point et une pause interne valent une unité. Un trait et la pause entre lettres en valent trois. Entre mots, la pause totale vaut sept unités.',
    'Si une unité vaut 100 ms, le trait dure 300 ms et la pause de mot 700 ms. Ce sont des durées relatives : changer de vitesse conserve leurs proportions.'
  ],'durée du trait = 3 × durée du point',[
    N('Un point dure 80 ms. Durée d’un trait ?',240,'ms','Le trait vaut trois unités : 3 × 80 = 240 ms.',0),
    N('Un point dure 80 ms. Pause entre deux lettres ?',240,'ms','Entre lettres : trois unités, soit 240 ms.',0),
    N('Un point dure 80 ms. Pause entre deux mots ?',560,'ms','Entre mots : sept unités, soit 560 ms.',0),
    N('Un point dure 80 ms. Pause entre deux éléments d’une lettre ?',80,'ms','La pause interne vaut une unité.',0),
    Q('Deux points séparés par une pause de lettre représentent…','EE',['I','S','M'],'La pause de lettre termine chaque E; I possède deux points avec seulement une pause interne.'),
    Q('Deux points réunis avec une pause interne représentent…','I',['EE','T','N'],'I est un seul caractère formé de deux points.'),
    O('Classe les durées du plus court au plus long.',['Point : 1 unité','Trait : 3 unités','Pause de mot : 7 unités'],'Les durées sont 1, 3 et 7 unités.'),
    TF('Accélérer le Morse de référence change les proportions point/trait.',false,'On raccourcit l’unité de base; les proportions restent les mêmes.')
  ]),
  group('Ponctuer sans confusion',['?','/','.','=',',','@'],'Ces signes servent aux groupes usuels et aux messages. La barre de fraction est elle-même un caractère; elle n’est pas la pause entre mots.'),
  L('Lire des petits mots','DE, CQ, RST, RADIO, 73','Morse',[
    'Après les lettres isolées, garde le rythme dans des groupes courts. Le séparateur entre lettres reste audible; ne fusionne pas les caractères. DE signifie « de » dans l’identification de la station qui appelle.',
    'CQ est un appel général; RST est un report en télégraphie. Le groupe 73 exprime une salutation de trafic. Leur sens appartient aux usages, leur codage suit les caractères ordinaires.',
    'Pour composer un groupe, utilise le bouton de séparation entre lettres. Pour deux mots, utilise le séparateur de mots. La barre affichée comme séparateur de mots n’est pas le caractère slash encodé.'
  ],'lettres séparées par un espace · mots séparés par /',[
    listen('DE',['DE','ET','TE','ED'],'Écoute et choisis le groupe de deux lettres.'),compose('DE'),
    listen('CQ',['CQ','QC','CO','QO'],'Écoute cet appel général.'),compose('CQ'),
    listen('RST',['RST','RTS','STR','SRT'],'Écoute les trois lettres du report.'),compose('73'),
    listen('RADIO',['RADIO','RATIO','RADAR','RADOI'],'Écoute le mot de cinq lettres.'),
    Q('Que signifie habituellement le groupe CQ ?','Un appel général',['L’arrêt du trafic','Une tension de 73 V','Un report de température'],'CQ appelle les stations intéressées à répondre.')
  ]),
  L('Décoder un indicatif','Lettres et chiffres dans un même groupe','Morse',[
    'Un indicatif mélange lettres et chiffres. Il se transmet comme un groupe de caractères ordinaires; le chiffre garde ses cinq éléments. Les exemples de cette leçon sont des groupes d’entraînement, pas des indicatifs attribués par Hamigo.',
    'F4ABC combine F, 4, A, B et C. Chaque séparation de lettre doit rester claire. Pour une barre dans un groupe comme /P, compose le caractère slash lui-même, pas une pause de mot.',
    'Quand un caractère manque à l’écoute, conserve ceux que tu as reconnus et réécoute. Essaie de retrouver la structure globale plutôt que de recommencer mentalement toute la chaîne à chaque hésitation.'
  ],'F4ABC = ..-. ....- .- -... -.-.',[
    listen('F4ABC',['F4ABC','F5ABC','F4ACB','F4ABD'],'Écoute le groupe d’entraînement complet.'),compose('F4ABC'),
    listen('F6KGL',['F6KGL','F5KGL','F6GLK','F6KJL'],'Écoute lettres et chiffre.'),compose('F6KGL'),
    listen('F5KFF',['F5KFF','F4KFF','F5FFK','F5KTF'],'Écoute le groupe et choisis son ordre.'),
    compose('4'),compose('/'),
    TF('Le chiffre d’un indicatif se transmet comme un caractère Morse ordinaire.',true,'Les lettres et chiffres conservent leurs codes habituels.')
  ]),
  L('Apprendre à l’oreille','Rythme, répétition et difficulté progressive','Morse',[
    'Lire et écouter ne demandent pas exactement le même rappel. Révise une petite famille à la fois, puis mélange-la avec les familles précédentes. Une erreur indique une paire à retravailler, pas une raison d’accélérer.',
    'La méthode d’espacement dite Farnsworth peut garder des caractères rapides tout en allongeant les espaces pour laisser le temps de reconnaître. Cela ne change pas le rapport point/trait à l’intérieur d’un caractère.',
    'Garde le volume raisonnable et commence les séances courtes dans un lieu calme. La télégraphie est un enrichissement pratique : elle ne constitue pas une épreuve du certificat français actuel.'
  ],'',[
    listen('B',['B','V','D','H']),listen('V',['V','B','U','S']),listen('F',['F','L','R','P']),listen('L',['L','F','P','R']),
    Q('Que faut-il travailler si B et V sont souvent confondus ?','Leur rythme et la position du trait',['Seulement la couleur des boutons','La fréquence du Wi-Fi','Un tableau sans aucun son'],'B commence par un trait; V finit par un trait. L’écoute compare ces rythmes.'),
    TF('La méthode Farnsworth peut allonger les espaces en conservant des caractères rapides.',true,'L’espacement laisse davantage de temps pour reconnaître les caractères.'),
    Q('Une séance produit beaucoup d’erreurs. Quel choix aide l’apprentissage ?','Réduire le groupe et réviser les confusions',['Accélérer systématiquement','Ignorer toutes les erreurs','Passer directement aux mots longs'],'Il vaut mieux stabiliser la reconnaissance avant d’ajouter de la difficulté.'),
    TF('Le certificat français actuel comporte une épreuve de Morse obligatoire.',false,'Le Morse est une compétence radio facultative dans cet examen actuel.')
  ]),
  L('Le grand mélange Morse','Mobiliser toutes les familles','Morse',[
    'Ce mélange rassemble des lettres courtes, des lettres longues et des chiffres. La réussite repose sur le rappel du rythme complet. Les questions varient entre reconnaissance visuelle, écoute et composition.',
    'Avant de répondre, distingue la consigne : nommer un caractère entendu, retrouver celui d’un code affiché ou fabriquer le signal demandé. Une erreur corrigée se transforme en nouvelle occasion de rappel.',
    'Après cette étape, utilise aussi le traducteur et les flashcards mélangées pour créer de nouveaux groupes. La pratique régulière de quelques minutes donne plus de rappel utile qu’une seule longue séance.'
  ],'',[
    listen('Z',['Z','G','Q','X']),compose('Q'),listen('7',['7','8','2','0']),compose('9'),
    Q('Quel caractère correspond à •••• ?','H',['S','I','5'],'H possède quatre points; S en a trois et 5 en a cinq.'),
    Q('Quel caractère correspond à ━•━━ ?','Y',['C','K','X'],'Y = ━•━━'),
    M('Relie sans confondre lettres et chiffres.',[['O','━━━'],['0','━━━━━'],['S','•••'],['5','•••••']],'La longueur du caractère distingue O de 0 et S de 5.'),
    compose('CQ DE'),listen('73',['73','37','72','83'],'Écoute le groupe de chiffres.'),
    N('Un point vaut 60 ms. Pause standard entre deux mots ?',420,'ms','7 × 60 = 420 ms.',0)
  ])
]);

chapter('c16','Les maths du poste','Calculer proprement avant de brancher les formules','#95CCE8','calculator',[
  L('Préfixes sans piège','Puissances de dix et conversions','Mathématiques',[
    'Une notation scientifique écrit a × 10ⁿ. Les préfixes déplacent l’exposant : kilo 10³, milli 10⁻³, micro 10⁻⁶, nano 10⁻⁹, pico 10⁻¹². Une conversion conserve la grandeur physique.',
    'Pour passer d’unité plus grande à une unité plus petite, le nombre augmente. Ainsi 1 µF vaut 1 000 nF et 1 000 000 pF. Un tableau de préfixes aide au début, puis les rapports deviennent familiers.',
    'Une formule attend généralement les unités de base. Pour RC, utilise Ω et F; pour une longueur d’onde, hertz si la vitesse est en m/s. Certaines écritures simplifiées annoncent explicitement MHz ou kΩ.'
  ],'1 k = 10³ · 1 m = 10⁻³ · 1 µ = 10⁻⁶',[
    N('0,047 µF en nF ?',47,'nF','0,047 × 1 000 = 47 nF.',.001),
    N('4 700 pF en nF ?',4.7,'nF','4 700/1 000 = 4,7 nF.',.001),
    N('2,5 MHz en kHz ?',2500,'kHz','1 MHz = 1 000 kHz.',.01),
    N('33 mH en H ?',.033,'H','33/1 000 = 0,033 H.',.00001),
    C('Le préfixe pico signifie 10 puissance ___.','−12',['−6','−9','12'],'Pico vaut 10⁻¹².'),
    TF('0,001 A et 1 mA représentent le même courant.',true,'Milli vaut un millième.'),
    O('Classe les capacités de la plus petite à la plus grande.',['100 pF','1 nF','10 nF','1 µF'],'En nF : 0,1; 1; 10; 1 000.'),
    Q('Quelle conversion faut-il faire avant τ = RC avec R = 10 kΩ et C = 10 µF ?','10 000 Ω et 0,000010 F',['10 Ω et 10 F','10 000 Ω et 10 F','0,010 Ω et 1 000 F'],'Les préfixes se convertissent séparément dans les unités de base.')
  ]),
  L('Isoler la bonne inconnue','Égalités et contrôle des unités','Mathématiques',[
    'Une relation se transforme en faisant la même opération des deux côtés. À partir de U = RI, on divise par R pour I et par I pour R. Écris ce changement avant de substituer les valeurs.',
    'Les unités aident à contrôler une formule : V/Ω donne A, et W/V donne A. Une somme ne mélange pas des grandeurs incompatibles. Les racines et carrés peuvent modifier les unités et les rapports.',
    'Teste un cas simple : si U augmente à R fixe, I doit augmenter; si R augmente à U fixe, I diminue. Ce raisonnement repère une inversion de fraction.'
  ],'I = U/R · R = U/I · I = P/U',[
    C('Depuis P = UI, on obtient I = ___.','P/U',['U/P','P×U','U−P'],'On divise les deux membres par U.'),
    N('P = 36 W sous 12 V. Courant ?',3,'A','I = P/U = 36/12 = 3 A.',.01),
    N('P = 25 W dans 100 Ω. Tension efficace ?',50,'V','U = √(PR) = √2 500 = 50 V.',.01),
    N('Énergie 120 J à puissance 30 W. Durée ?',4,'s','t = E/P = 120/30 = 4 s.',.01),
    Q('Quel rapport a l’unité d’une résistance ?','V/A',['A/V','W×V','Hz/s'],'R = U/I et l’ohm vaut un volt par ampère.'),
    TF('On peut additionner directement une tension de 5 V et un courant de 2 A.',false,'Une somme exige des grandeurs de même nature et des unités compatibles.'),
    C('Pour f = 1/T, la période T vaut ___.','1/f',['f','f²','2f'],'L’inverse de la fréquence est la période.'),
    N('Capacité C = 20 µF et charge Q = 100 µC : tension ?',5,'V','U = Q/C = 100/20 = 5 V.',.01)
  ]),
  L('Carrés, racines et rapports','Comprendre les variations','Mathématiques',[
    'Une grandeur au carré réagit plus fortement à un facteur. Si U est multipliée par trois à R fixe, U²/R est multipliée par neuf. Une racine carrée agit dans l’autre sens : multiplier LC par neuf divise la résonance par trois.',
    'Pour deux composantes orthogonales, le module √(a²+b²) ne se calcule pas par a+b. Les triangles 3–4–5 et 6–8–10 donnent des repères faciles.',
    'Un rapport compare deux grandeurs de même nature et n’a pas d’unité. Le pourcentage multiplie ce rapport par 100. Énonce ce que représente le numérateur avant de calculer.'
  ],'module Z = √(R² + X²)',[
    N('√(6² + 8²) vaut combien ?',10,'','36 + 64 = 100 et √100 = 10.',.01),
    N('Si une tension triple à résistance fixe, la puissance est multipliée par combien ?',9,'','P est proportionnelle à U² : 3² = 9.',0),
    N('30 W utiles pour 50 W absorbés : rendement ?',60,'%','100 × 30/50 = 60 %.',.01),
    N('Une erreur de 2 Ω sur 100 Ω représente quel pourcentage ?',2,'%','100 × 2/100 = 2 %.',.01),
    TF('√(R²+X²) est toujours égal à R+X pour R et X positifs.',false,'Le carré de la somme contient en plus 2RX; les deux expressions diffèrent.'),
    Q('Si LC est multiplié par neuf, f0 est…','Divisée par trois',['Multipliée par neuf','Divisée par neuf','Inchangée'],'f0 est inversement proportionnelle à √LC.'),
    N('Puissance 2 W puis 8 W : rapport final/initial ?',4,'','8/2 = 4.',.01),
    C('Un rendement de 80 % signifie puissance utile = ___ × puissance absorbée.','0,8',['8','80','1,8'],'80 % = 80/100 = 0,8.')
  ]),
  L('Angles et lecture d’oscilloscope','Temps, phase et amplitude','Mathématiques',[
    'Un cycle complet représente 360°. Une fraction de période se convertit donc en angle : φ = 360° × Δt/T. Précise toujours quel signal est en avance ou en retard.',
    'À l’oscilloscope, les divisions ne sont pas des unités : le temps par division donne l’échelle horizontale, les volts par division l’échelle verticale. Mesure une période entre points équivalents.',
    'La crête, la valeur crête à crête et la valeur efficace décrivent des choses différentes. Lorsque les crêtes positive et négative sont symétriques autour de zéro, crête à crête = 2 × crête. Pour une sinusoïde sans composante continue, efficace = crête/√2.'
  ],'φ = 360° × Δt/T',[
    N('Un retard de 1 ms sur une période de 4 ms : angle ?',90,'°','360 × 1/4 = 90°.',0),
    N('Période sur 5 divisions à 2 µs/div : période ?',10,'µs','5 × 2 = 10 µs.',.01),
    N('Même période de 10 µs : fréquence en kHz ?',100,'kHz','1/(10 × 10⁻⁶) = 100 kHz.',.01),
    N('Une crête à crête mesure 6 divisions à 2 V/div : tension crête à crête ?',12,'V','6 × 2 = 12 V.',.01),
    N('Sinusoïde centrée de 12 V crête à crête : crête ?',6,'V','Crête = 12/2 = 6 V.',.01),
    Q('Pour mesurer la période, quels points choisir ?','Deux sommets successifs identiques',['Un sommet et le creux suivant','Deux points quelconques','Uniquement l’origine de l’écran'],'Deux points de même phase délimitent un cycle.'),
    TF('Une phase de 360° correspond à un cycle entier.',true,'360° représente un tour complet dans le cycle.'),
    M('Associe les fractions de cycle aux angles.',[['1/4','90°'],['1/2','180°'],['3/4','270°'],['1','360°']],'Chaque fraction se multiplie par 360°.')
  ])
]);

chapter('c17','RLC, au-delà des recettes','Énergie, réactance, phase et sélectivité','#9ACDAD','circuit',[
  L('Énergie dans C et L','Deux formes de stockage','Circuits radio',[
    'Le condensateur stocke de l’énergie dans un champ électrique : E = ½CU². La bobine la stocke dans un champ magnétique : E = ½LI². Le carré signifie que doubler tension ou courant multiplie cette énergie par quatre.',
    'Un composant peut stocker une énergie dangereuse même si sa capacité ou son inductance paraît petite. Le modèle énergétique complète les relations Q = CU et u = L di/dt; il ne remplace pas leurs limites de tension et de courant.',
    'Dans un circuit oscillant, l’énergie passe entre condensateur et bobine. Les résistances dissipent une partie de cette énergie, ce qui amortit l’oscillation sans apport extérieur.'
  ],'EC = ½CU² · EL = ½LI²',[
    N('C = 1 000 µF, U = 10 V : énergie stockée ?',.05,'J','½ × 0,001 × 10² = 0,05 J.',.0001),
    N('L = 2 H et I = 1 A : énergie stockée ?',1,'J','½ × 2 × 1² = 1 J.',.01),
    N('La tension d’un condensateur double. L’énergie est multipliée par combien ?',4,'','L’énergie est proportionnelle au carré de la tension.',0),
    TF('L’énergie d’une bobine est liée au carré de son courant.',true,'EL = ½LI².'),
    M('Relie les relations.',[['Charge du condensateur','Q = CU'],['Énergie du condensateur','E = ½CU²'],['Énergie de la bobine','E = ½LI²']],'Charge, énergie et courant ne sont pas la même grandeur.'),
    Q('Dans un LC non alimenté avec pertes, l’amplitude des oscillations…','Diminue progressivement',['Augmente sans limite','Reste forcément parfaite','Devient instantanément infinie'],'Les pertes dissipent l’énergie stockée.'),
    N('C = 2 µF et U = 100 V : énergie en mJ ?',10,'mJ','½ × 2×10⁻⁶ × 10 000 = 0,01 J = 10 mJ.',.01),
    TF('Un condensateur chargé ne présente plus aucun risque dès que l’alimentation est retirée.',false,'L’énergie stockée peut rester présente après la coupure.')
  ]),
  L('Réactances en nombres','Utiliser f, L et C avec les bonnes unités','Circuits radio',[
    'XL = 2πfL et XC = 1/(2πfC). La fréquence doit être en hertz, L en henrys et C en farads pour obtenir des ohms. XL augmente avec la fréquence; XC diminue.',
    'À la même fréquence, une bobine deux fois plus grande a une réactance double. Un condensateur deux fois plus grand a une réactance divisée par deux. Le changement de fréquence suit les mêmes rapports.',
    'Ces calculs emploient les modèles idéaux. Aux fréquences élevées, les composants réels ont des capacités ou inductances parasites et peuvent rencontrer leur propre résonance.'
  ],'XL = 2πfL · XC = 1/(2πfC)',[
    N('f = 1 kHz, L = 20 mH. XL au dixième ?',125.66,'Ω','2π × 1 000 × 0,020 ≈ 125,7 Ω.',.15),
    N('f = 1 kHz, C = 1 µF. XC au dixième ?',159.15,'Ω','1/(2π × 1 000 × 10⁻⁶) ≈ 159,2 Ω.',.15),
    N('XC = 200 Ω à une fréquence donnée. La fréquence double. Nouvelle XC ?',100,'Ω','XC est inversement proportionnelle à f.',.01),
    N('XL = 50 Ω. L est multipliée par trois à fréquence constante. Nouvelle XL ?',150,'Ω','XL est proportionnelle à L.',.01),
    TF('Un condensateur deux fois plus grand présente deux fois plus de réactance à f fixe.',false,'XC est inversement proportionnelle à C.'),
    Q('Quel jeu d’unités convient directement à XL = 2πfL ?','Hz et H',['MHz et mH sans conversion','kHz et µH sans conversion','V et A'],'Les unités de base produisent une réactance en ohms.'),
    O('À L fixée, classe les fréquences de la plus petite XL à la plus grande.',['100 Hz','1 kHz','10 kHz'],'XL augmente proportionnellement à f.'),
    M('Associe les variations à leurs effets.',[['f double, L fixe','XL double'],['C double, f fixe','XC est divisée par deux'],['C divisée par deux, f fixe','XC double']],'XL croît avec f; XC varie en sens inverse de C.')
  ]),
  L('Module et phase d’une impédance','La résistance ne s’ajoute pas comme une réactance','Circuits radio',[
    'Dans un RLC série, la partie réactive vaut X = XL − XC. Le signe décide du caractère inductif ou capacitif. Le module Z = √(R²+X²) sert à calculer l’amplitude du courant.',
    'La phase vérifie tan φ = X/R dans ce modèle. Un angle positif correspond à une tension en avance sur le courant, donc à un comportement inductif; un angle négatif correspond au comportement capacitif.',
    'Quand |X| = R, l’angle a une amplitude de 45°. Quand X = 0, le courant et la tension sont en phase. Ces repères évitent de confondre phase et module.'
  ],'X = XL − XC · Z = √(R²+X²)',[
    N('R = 12 Ω, XL = 30 Ω, XC = 14 Ω. Module Z ?',20,'Ω','X = 16 Ω; √(12²+16²) = √400 = 20 Ω.',.01),
    N('Tension efficace 10 V, module Z = 20 Ω. Courant efficace ?',.5,'A','I = U/Z = 10/20 = 0,5 A.',.001),
    Q('Avec XL = 20 Ω et XC = 50 Ω, le caractère série est…','Capacitif',['Inductif','Forcément résistif','Sans aucune impédance'],'X = 20 − 50 = −30 Ω : signe capacitif.'),
    N('R = 10 Ω et X = 10 Ω inductif. Phase ?',45,'°','tan φ = 1, donc φ = 45°.',.1),
    TF('À résonance série, le courant et la tension sont en phase.',true,'X = 0 : l’impédance série est résistive.'),
    {...N('Estime la phase d’un circuit série purement résistif.',0,'°','Une résistance pure ne déphase pas le courant par rapport à la tension.',2),kind:'estimate',bands:['-90','90','5']},
    M('Associe le signe de X au caractère.',[['X > 0','Inductif'],['X < 0','Capacitif'],['X = 0','Résistif dans le modèle série']],'XL porte un signe positif, XC un signe négatif.'),
    Q('Pourquoi R = 3 Ω et X = 4 Ω donnent-elles Z = 5 Ω, et non 7 Ω ?','Les composantes résistive et réactive sont orthogonales',['Les valeurs doivent toujours être moyennées','La loi d’Ohm disparaît','La résistance devient négative'],'On prend le module du vecteur : √(3²+4²) = 5.')
  ]),
  L('Accorder et sélectionner','Résonance, pertes et largeur utile','Circuits radio',[
    'Un réglage de L ou C déplace la fréquence de résonance. Un circuit à fort Q possède une bande plus étroite, mais toute information modulée a besoin d’une certaine largeur. Une sélection trop étroite peut donc altérer le message.',
    'Pour le modèle de bande étudié, Q = f0/B. Les pertes dissipatives réduisent le Q. En série à résonance, le courant peut être élevé et les tensions individuelles sur L et C peuvent dépasser celle de la source.',
    'Une adaptation ou un couplage réel change le chargement du circuit. Le Q à vide et le Q chargé ne sont pas identiques : brancher le reste du montage modifie la sélectivité.'
  ],'f0 = 1/(2π√LC) · Q = f0/B',[
    N('f0 = 5 MHz et B = 50 kHz. Q ?',100,'','5 000/50 = 100.',.01),
    N('f0 = 2 MHz et Q = 200. B en kHz ?',10,'kHz','B = f0/Q = 2 000/200 = 10 kHz.',.01),
    TF('Un filtre extrêmement étroit convient à tous les signaux modulés.',false,'Il peut supprimer une partie du message si sa bande est trop étroite.'),
    Q('À L fixée, augmenter C fait généralement…','Baisser la fréquence de résonance',['Monter f0','Laisser f0 toujours inchangée','Rendre le courant nul partout'],'f0 est inversement proportionnelle à √C.'),
    N('C est multipliée par neuf à L fixe. f0 est divisée par combien ?',3,'','√9 = 3, donc f0 devient un tiers.',0),
    Q('Les pertes dissipatives supplémentaires tendent à…','Réduire le facteur Q',['Créer de l’énergie','Annuler toute fréquence','Garantir une bande infiniment étroite'],'Elles dissipent plus vite l’énergie stockée.'),
    TF('Dans un RLC série à résonance, les tensions sur L et C peuvent être plus grandes que la tension appliquée.',true,'Leurs contributions réactives se compensent dans la somme malgré des amplitudes individuelles élevées.'),
    C('Brancher une charge au circuit peut modifier son Q ___.','chargé',['alphabétique','binaire','solaire'],'Le chargement modifie les pertes et le couplage vus par le résonateur.')
  ])
]);

chapter('c18','RF : voir ce qui sort du poste','Spectres, mélangeurs, distorsion et niveaux','#C2ACEE','signal',[
  L('Fondamentale et harmoniques','Un signal n’est pas toujours une seule fréquence','Émetteurs et récepteurs',[
    'Une sinusoïde idéale ne comporte qu’une fréquence. Une forme périodique non sinusoïdale se décrit avec une fondamentale et des harmoniques, à des multiples entiers de sa fréquence.',
    'L’écrêtage transforme la forme du signal et ajoute des composantes spectrales. Les filtres de sortie atténuent certains produits, mais limiter leur création dès l’amplificateur reste utile.',
    'Ne confonds pas harmonique et intermodulation : les harmoniques partent d’un seul signal; l’intermodulation combine plusieurs signaux dans un étage non linéaire.'
  ],'harmonique n : fn = n × f0',[
    N('Fondamentale 3,5 MHz. Deuxième harmonique ?',7,'MHz','2 × 3,5 = 7 MHz.',.01),
    N('Fondamentale 14 MHz. Troisième harmonique ?',42,'MHz','3 × 14 = 42 MHz.',.01),
    N('Une harmonique à 28 MHz correspond au rang 4. Fondamentale ?',7,'MHz','28/4 = 7 MHz.',.01),
    TF('Une sinusoïde idéale comporte toutes les harmoniques avec le même niveau.',false,'Elle ne comporte qu’une fréquence; les harmoniques indiquent une autre forme ou une distorsion.'),
    W('Quelle forme périodique possède des transitions abruptes ?',['square','sine','dc'],['Créneau','Sinusoïde','Continu'],0,'Le créneau a des fronts abrupts et un spectre riche en harmoniques.'),
    Q('Un passe-bas de sortie sert notamment à réduire…','Les harmoniques au-dessus de la fréquence utile',['Le certificat de l’opérateur','Toutes les tensions continues de la station','La longueur des messages'],'Le passe-bas atténue les fréquences élevées indésirables.'),
    S('Quelles actions peuvent améliorer la pureté d’un émetteur ?',['Éviter l’écrêtage','Employer un filtre adapté','Vérifier la polarisation','Supprimer toute adaptation sans mesure'],[0,1,2],'Linéarité, filtrage et conditions de fonctionnement agissent ensemble.'),
    C('Une harmonique se situe à un multiple ___ de la fondamentale.','entier',['toujours inférieur à un','négatif uniquement','irrationnel obligatoire'],'Les harmoniques sont f0, 2f0, 3f0, etc.')
  ]),
  L('Mélanger et choisir le bon produit','Somme, différence et image','Émetteurs et récepteurs',[
    'Le mélange idéal de deux signaux produit notamment leur somme et leur différence. Un filtre choisit le produit utile. Un vrai mélangeur peut aussi laisser passer les entrées et fabriquer d’autres combinaisons.',
    'Pour une réception avec OL au-dessus du signal, la FI différence vaut OL − RF. Une fréquence située de l’autre côté de l’OL peut produire la même FI; le présélecteur doit la rejeter.',
    'La fréquence image n’est pas une copie audio du message. C’est une fréquence RF distincte qui crée une conversion indésirable vers la même FI.'
  ],'f somme = f1+f2 · f différence = |f1−f2|',[
    N('Un mélangeur reçoit 12 MHz et 4 MHz. Produit somme ?',16,'MHz','12 + 4 = 16 MHz.',.01),
    N('Mêmes entrées : produit différence positif ?',8,'MHz','|12 − 4| = 8 MHz.',.01),
    N('Réception 7 MHz, FI 9 MHz, OL au-dessus. Fréquence OL ?',16,'MHz','OL = RF + FI = 7 + 9 = 16 MHz.',.01),
    N('OL 16 MHz, FI 9 MHz, réception utile 7 MHz. Fréquence image supérieure ?',25,'MHz','25 − 16 = 9 MHz aussi.',.01),
    Q('Quel bloc choisit un produit à la sortie du mélangeur ?','Un filtre',['Le haut-parleur seul','Une pile seule','L’indicatif'],'Le mélangeur produit plusieurs composantes; le filtre sélectionne la zone utile.'),
    TF('Le filtre FI peut distinguer seul deux RF qui donnent exactement la même FI.',false,'Le signal image doit être réduit avant conversion.'),
    M('Associe ces fréquences dans le cas RF 7 MHz, OL 16 MHz.',[['RF utile','7 MHz'],['FI différence','9 MHz'],['RF image supérieure','25 MHz']],'7 et 25 MHz sont à 9 MHz de l’OL, sur deux côtés opposés.'),
    C('Le présélecteur se place ___ le mélangeur pour rejeter l’image.','avant',['après le haut-parleur','uniquement dans le micro','à la place de la batterie'],'Il filtre la RF avant que les produits ne se confondent en FI.')
  ]),
  L('Intermodulation et compression','Quand les étages cessent d’être linéaires','Émetteurs et récepteurs',[
    'Un amplificateur linéaire reproduit la forme avec un facteur de gain. La compression apparaît quand le gain diminue pour les niveaux élevés; l’écrêtage peut être plus visible encore.',
    'Avec deux tons f1 et f2, des produits d’intermodulation de troisième ordre apparaissent à 2f1−f2 et 2f2−f1. Ils peuvent tomber près des signaux utiles, donc être difficiles à filtrer.',
    'Diminuer le niveau d’entrée ou améliorer la linéarité aide à limiter ces produits. Un récepteur saturé peut parfois mieux fonctionner avec un atténuateur, malgré la baisse du signal utile.'
  ],'IM3 : 2f1−f2 et 2f2−f1',[
    N('f1 = 10 MHz, f2 = 11 MHz. Produit 2f1−f2 ?',9,'MHz','2 × 10 − 11 = 9 MHz.',.01),
    N('Mêmes tons : produit 2f2−f1 ?',12,'MHz','2 × 11 − 10 = 12 MHz.',.01),
    N('f1 = 144 MHz, f2 = 145 MHz. Produit 2f1−f2 ?',143,'MHz','2 × 144 − 145 = 143 MHz.',.01),
    TF('L’intermodulation peut produire une fréquence absente des signaux d’entrée.',true,'Une non-linéarité combine les fréquences pour fabriquer de nouveaux produits.'),
    Q('Une entrée RF saturée peut parfois être améliorée par…','Une atténuation d’entrée adaptée',['Un gain maximal supplémentaire','Une antenne de mauvaise impédance au hasard','Un volume audio plus fort'],'Réduire les signaux à l’entrée peut sortir l’étage de sa zone non linéaire.'),
    C('En compression, le gain d’un étage ___ quand le niveau devient trop élevé.','diminue',['devient infini','reste exactement identique','se transforme en fréquence'],'L’amplification ne reste plus proportionnelle.'),
    S('Quels phénomènes sont liés à une non-linéarité ?',['Écrêtage','Intermodulation','Compression','Conversion parfaite des unités'],[0,1,2],'Ils décrivent différentes manifestations d’un fonctionnement non linéaire.'),
    Q('Pourquoi les produits IM3 proches des tons utiles sont-ils gênants ?','Ils sont difficiles à séparer par un filtre proche de la bande utile',['Ils sont toujours en continu','Ils augmentent la vitesse de la lumière','Ils sont nécessairement hors de toute bande radio'],'Leur proximité spectrale limite l’efficacité d’un simple filtrage.')
  ]),
  L('Des dBm jusqu’au bruit','Puissances absolues et qualité de réception','Émetteurs et récepteurs',[
    'Le dBm fixe une référence de 1 mW. 0 dBm vaut 1 mW, 10 dBm 10 mW, 20 dBm 100 mW et 30 dBm 1 W. Chaque ajout de 10 dB multiplie la puissance par dix.',
    'Le rapport signal/bruit compare deux puissances dans une même largeur de mesure. Si les niveaux sont exprimés en dBm, leur différence donne le rapport en dB. Le bruit mesuré dépend de la bande passante.',
    'Une réception utile ne dépend pas seulement du signal : un bruit plus fort, une bande trop large ou des perturbations peuvent faire perdre l’information.'
  ],'S/B(dB) = signal(dBm) − bruit(dBm)',[
    N('40 dBm représentent quelle puissance en watts ?',10,'W','40 dBm = 10 000 mW = 10 W.',.01),
    N('−10 dBm représentent quelle puissance en mW ?',.1,'mW','10^(-10/10) = 0,1 mW.',.001),
    N('Signal −80 dBm, bruit −100 dBm : rapport S/B ?',20,'dB','−80 − (−100) = 20 dB.',.01),
    N('Signal −90 dBm, bruit −95 dBm : rapport S/B ?',5,'dB','−90 − (−95) = 5 dB.',.01),
    TF('Le bruit mesuré peut augmenter si la bande passante de réception augmente.',true,'Une bande plus large recueille davantage de bruit dans des conditions comparables.'),
    Q('Un signal fort suffit-il toujours pour recevoir correctement ?','Non, le bruit et les perturbations comptent aussi',['Oui, sans aucune autre condition','Oui, quel que soit le mode','Oui, même avec un récepteur saturé'],'La qualité dépend du rapport signal/bruit et du fonctionnement de la chaîne.'),
    C('Ajouter 10 dB à une puissance la multiplie par ___.','10',['2','3','100'],'10 log10(10) = 10 dB.'),
    N('Une chaîne gagne 20 dB sur une entrée de −30 dBm. Sortie idéale ?',-10,'dBm','−30 + 20 = −10 dBm.',.01)
  ])
]);

chapter('c19','Antennes et lignes à la loupe','Réflexions, pertes, longueurs électriques et réglages','#F49F86','antenna',[
  L('Réflexion et ROS','Comprendre ce que le pont mesure','Antennes',[
    'Une discontinuité d’impédance renvoie une partie de l’onde. Pour une charge purement résistive, le coefficient de réflexion en amplitude vaut Γ = (RL−Z0)/(RL+Z0). La fraction de puissance réfléchie vaut |Γ|².',
    'Le ROS vaut (1+|Γ|)/(1−|Γ|). Avec une charge résistive, il se simplifie au rapport de la plus grande résistance sur la plus petite. Cette simplification ne convient pas directement à une charge réactive.',
    'Le signe de Γ décrit aussi une phase de réflexion; il ne signifie pas une puissance négative. Le wattmètre direct/réfléchi mesure des puissances et doit être utilisé dans ses conditions prévues.'
  ],'|Γ| = |(RL−Z0)/(RL+Z0)| · ROS = (1+|Γ|)/(1−|Γ|)',[
    N('RL = 150 Ω sur ligne 50 Ω, charge résistive. ROS ?',3,'','150/50 = 3.',.01),
    N('RL = 50 Ω sur ligne 50 Ω. Coefficient de réflexion ?',0,'','(50−50)/(50+50) = 0.',.001),
    N('RL = 150 Ω sur ligne 50 Ω. |Γ| ?',.5,'','(150−50)/(150+50) = 100/200 = 0,5.',.001),
    N('|Γ| = 0,5. Fraction réfléchie en pourcentage ?',25,'%','La puissance suit le carré : 0,5² × 100 = 25 %.',.01),
    N('100 W incidents, 25 W réfléchis au même point d’une ligne sans pertes. Puissance nette vers la charge ?',75,'W','Puissance nette = incidente − réfléchie = 100 − 25 = 75 W.',.01),
    TF('Un coefficient de réflexion négatif signifie une puissance réfléchie négative.',false,'Le signe exprime une phase; la puissance dépend de |Γ|².'),
    Q('ROS = 1 correspond à…','Aucune réflexion dans le modèle idéal adapté',['Toute la puissance réfléchie','Une charge de 1 Ω forcément','Une antenne forcément efficace'],'Il décrit l’adaptation, pas le rendement de rayonnement.'),
    TF('Le rapport max(RL,Z0)/min(RL,Z0) donne directement le ROS pour toute charge complexe.',false,'Cette expression simplifiée est réservée ici aux charges purement résistives.')
  ]),
  L('Longueur électrique du coaxial','Vitesse et fraction d’onde','Antennes',[
    'Le facteur de vélocité décrit la vitesse relative dans une ligne. À fréquence fixée, λligne = FV × λair. Une même longueur physique ne représente donc pas la même fraction d’onde dans le câble et dans l’air.',
    'Une ligne longue d’un quart d’onde peut transformer une impédance dans les conditions adaptées; une demi-onde idéale répète l’impédance de charge à l’entrée. Ces relations idéales supposent une ligne sans pertes.',
    'Changer la longueur de câble peut changer la mesure au poste sans réparer la charge. Une mesure ou un calcul de transformation exige de préciser fréquence, Z0, charge et facteur de vélocité.'
  ],'λligne = FV × 300/f(MHz)',[
    N('100 MHz, FV = 0,8 : longueur d’onde dans la ligne ?',2.4,'m','λair = 3 m; 0,8 × 3 = 2,4 m.',.01),
    N('Même ligne : longueur physique d’un quart d’onde ?',.6,'m','2,4/4 = 0,6 m.',.01),
    N('50 MHz, FV = 0,66 : demi-onde physique ?',1.98,'m','λair = 6 m; λligne = 3,96 m; moitié = 1,98 m.',.01),
    TF('Une demi-onde idéale sans pertes répète l’impédance de charge à l’entrée.',true,'Cette propriété dépend de la longueur électrique à la fréquence considérée.'),
    Q('Allonger arbitrairement le coaxial répare-t-il forcément une antenne mal adaptée ?','Non',['Oui, dans tous les cas','Oui, si le câble est noir','Oui, même avec une charge ouverte'],'La transformation de mesure ne corrige pas forcément l’antenne, et les pertes peuvent cacher le défaut.'),
    C('Le facteur de vélocité multiplie la ___ d’onde dans l’air pour obtenir celle dans la ligne.','longueur',['puissance','résistance','tension nominale'],'À fréquence fixée, la longueur d’onde suit la vitesse de propagation.'),
    N('Une ligne de 0,5 m représente un quart d’onde. Longueur d’onde dans cette ligne ?',2,'m','λ = 4 × 0,5 = 2 m.',.01),
    S('Quels paramètres faut-il pour déterminer une longueur électrique ?',['Fréquence','Facteur de vélocité','Longueur physique','Couleur de la gaine'],[0,1,2],'La longueur en fraction d’onde dépend de fréquence, vitesse et longueur physique.')
  ]),
  L('Le câble mange des watts','Pertes, connecteurs et compromis','Antennes',[
    'Une perte positive L en dB correspond à un rapport de puissance 10^(−L/10). Pour les repères usuels, 3 dB divisent environ par deux, 6 dB par quatre et 10 dB par dix.',
    'Les pertes augmentent généralement avec la longueur et la fréquence pour un type de câble donné. Des connecteurs mal montés ou de l’humidité peuvent ajouter des défauts. Une ligne courte et de qualité n’est utile que si les autres éléments sont aussi adaptés.',
    'Un faible ROS côté émetteur ne prouve pas que la puissance arrive à l’antenne. Un câble très atténuateur absorbe aussi les réflexions, ce qui peut masquer un défaut distant.'
  ],'Psortie = Pentrée × 10^(−perte/10)',[
    N('50 W entrent dans une ligne perdant 3 dB. Sortie approximative ?',25,'W','La puissance est approximativement divisée par deux.',.5),
    N('50 W entrent dans une ligne perdant 10 dB. Sortie ?',5,'W','10 dB divisent par dix.',.01),
    N('Deux sections perdent chacune 2 dB. Perte totale ?',4,'dB','Les pertes en dB s’additionnent.',.01),
    TF('Une perte de 6 dB en puissance signifie une division par six.',false,'C’est approximativement une division par quatre; les dB sont logarithmiques.'),
    Q('Pour le même câble et la même fréquence, doubler la longueur tend à…','Doubler sa perte exprimée en dB',['Annuler la perte','Diviser toujours la perte par deux','Créer de la puissance RF'],'L’atténuation en dB est approximativement proportionnelle à la longueur.'),
    S('Quels défauts de ligne peuvent gêner une station ?',['Connecteur mal monté','Humidité dans le câble','Perte excessive','Nom du fichier du carnet'],[0,1,2],'Les défauts électriques ou matériels de la ligne diminuent le transfert ou dégradent l’adaptation.'),
    N('Poste 20 W, câble 1 dB, connecteurs 1 dB, autre câble 1 dB : perte totale ?',3,'dB','1 + 1 + 1 = 3 dB.',.01),
    C('Un câble dissipatif peut cacher une mauvaise adaptation en atténuant aussi l’onde ___.','réfléchie',['lumineuse','audio du micro','continue de la batterie'],'Le pont côté poste reçoit une réflexion déjà affaiblie.')
  ]),
  L('Mesurer avant de régler','Antenne, adaptation et mode commun','Antennes',[
    'Mesure d’abord une configuration connue : charge fictive, ligne, puis antenne. Une mauvaise mesure avec une charge connue oriente vers le câble, les connecteurs ou l’appareil. Conserve la fréquence et le point de mesure dans tes notes.',
    'Un dispositif d’adaptation peut permettre au poste de voir une bonne impédance sans éliminer toutes les pertes sur la ligne. Un balun et un dispositif de mode commun ne servent pas automatiquement au même rapport d’impédance.',
    'À la résonance d’un dipôle isolé simple, allonger les éléments tend à faire descendre sa fréquence et les raccourcir à la faire monter. Procède par petites modifications mesurées et respecte les contraintes mécaniques.'
  ],'',[
    O('Ordonne un diagnostic simple du système RF.',['Tester sur charge fictive connue','Contrôler la ligne et les connecteurs','Mesurer l’antenne à la fréquence prévue','Modifier un élément et noter le résultat'],'On part d’un repère connu puis on ajoute progressivement les éléments.'),
    Q('Un dipôle résonne trop haut en fréquence. Dans le modèle simple, il faut…','L’allonger un peu',['Le raccourcir','Supprimer tous les isolateurs','Augmenter le volume audio'],'La fréquence de résonance baisse généralement quand la longueur augmente.'),
    TF('Un accordeur placé au poste supprime forcément toutes les pertes du coaxial.',false,'Il adapte l’impédance vue au poste; les pertes et conditions de la ligne restent présentes.'),
    Q('Le pont donne un mauvais ROS même sur une charge connue. Que regarder d’abord ?','Le câblage, les connecteurs et la mesure',['La propagation ionosphérique','Le correspondant distant','L’alphabet international','La couleur du ciel'],'Le test local sur charge connue isole la mesure et la ligne de l’antenne.'),
    S('Quelles notes rendent les mesures comparables ?',['Fréquence','Point de mesure','Configuration de la ligne','Prénom du voisin uniquement'],[0,1,2],'Les conditions de mesure doivent rester identifiables.'),
    C('Pour réduire la RF circulant sur l’extérieur de la gaine, on peut envisager un dispositif de mode ___.','commun',['numérique','direct seulement','secret'],'Une impédance adaptée en mode commun limite certains courants sur la gaine.'),
    TF('Une bonne adaptation est la même chose qu’un bon rendement de rayonnement.',false,'Une charge fictive est adaptée mais ne rayonne pas utilement.'),
    Q('Après une modification, quel choix aide au diagnostic ?','Refaire la mesure dans les mêmes conditions',['Modifier trois autres paramètres sans note','Émettre immédiatement hors bande','Ignorer le point de mesure'],'Comparer avant/après permet d’identifier l’effet de la modification.')
  ])
]);

chapter('c20','Le numérique décodé','Bits, symboles, échantillons et correction','#8DCFCF','binary',[
  L('Penser en bits','Les nombres à deux états','Modulation',[
    'Un bit possède deux états, 0 ou 1. Les positions d’un entier binaire portent les poids 1, 2, 4, 8, 16… en partant de la droite. Le nombre décimal est la somme des poids activés.',
    'N bits permettent 2ᴺ combinaisons. En entier non signé, elles vont de 0 à 2ᴺ−1. Les zéros à gauche conservent la largeur du mot sans changer sa valeur.',
    'Le code binaire ne garantit pas la fiabilité radio : il faut ensuite représenter ces états par une modulation et gérer les erreurs de réception.'
  ],'N bits → 2ᴺ états',[
    B('Compose 10 en binaire sur quatre bits.',10,4,'10 = 8 + 2, donc 1010.'),
    B('Compose 9 en binaire sur quatre bits.',9,4,'9 = 8 + 1, donc 1001.'),
    B('Compose 15 en binaire sur quatre bits.',15,4,'15 = 8 + 4 + 2 + 1, donc 1111.'),
    N('Combien d’états distincts avec 5 bits ?',32,'états','2⁵ = 32.',0),
    N('Quelle valeur maximale non signée avec 4 bits ?',15,'','2⁴ − 1 = 15.',0),
    Q('Quel nombre décimal représente 0011 ?','3',['2','4','11'],'Les deux bits de droite valent 2 et 1, soit 3.'),
    TF('0101 et 101 donnent la même valeur non signée.',true,'Le zéro ajouté à gauche ne change pas la somme des poids.'),
    M('Associe les poids des quatre positions, en partant de la droite.',[['Position 1','1'],['Position 2','2'],['Position 3','4'],['Position 4','8']],'Les poids doublent à chaque position vers la gauche.')
  ]),
  L('Du symbole au débit','Bauds, bits et surcoût','Modulation',[
    'Un symbole représente un état choisi dans une modulation. Avec quatre états, on peut associer deux bits par symbole; avec huit états, trois bits. Le baud compte les symboles transmis par seconde, même quand deux symboles successifs sont identiques.',
    'Sans surcoût, le débit binaire vaut le débit de symboles multiplié par les bits par symbole. Des en-têtes et de la redondance consomment ensuite une part de ce débit; le débit de données utiles peut être inférieur.',
    'Plus d’états ne donne pas toujours une meilleure liaison : les états deviennent plus difficiles à distinguer dans le bruit. Le mode choisi équilibre débit, largeur occupée et robustesse.'
  ],'débit binaire = bauds × bits/symbole',[
    N('16 états : bits par symbole ?',4,'bits','2⁴ = 16.',0),
    N('1 200 bauds, 4 bits par symbole, sans surcoût : débit ?',4800,'bit/s','1 200 × 4 = 4 800 bit/s.',0),
    N('9 600 bit/s à 2 bits par symbole, sans surcoût : débit de symboles ?',4800,'bauds','9 600/2 = 4 800 bauds.',0),
    N('Débit transmis 1 000 bit/s, dont 20 % sont des en-têtes ou de la redondance. Débit utile ?',800,'bit/s','80 % des bits transmis restent utiles : 1 000 × 0,8 = 800 bit/s.',0),
    TF('Un baud est toujours égal à un bit par seconde.',false,'Cette égalité ne vaut que pour un bit par symbole et sans confusion de débit.'),
    M('Relie les familles de modulation aux grandeurs.',[['ASK','Amplitude'],['FSK','Fréquence'],['PSK','Phase']],'Les états sont portés par différentes grandeurs de la porteuse.'),
    Q('Pourquoi augmenter le nombre d’états peut-il rendre la liaison plus exigeante ?','Les états doivent être distingués malgré le bruit',['Les bits deviennent des watts','La longueur d’onde disparaît','L’indicatif devient facultatif'],'Une constellation plus dense demande généralement une meilleure qualité de réception.'),
    C('Les en-têtes réduisent la part de débit consacrée aux données ___.','utiles',['réfléchies','continues','ionosphériques'],'Le débit de ligne comprend aussi le protocole et la redondance.')
  ]),
  L('Échantillonner un signal','Temps discret et repliement','Modulation',[
    'Un convertisseur prélève des valeurs du signal à une cadence d’échantillonnage fe. Pour un signal limité en bande de base, il faut fe strictement supérieure à deux fois sa fréquence maximale pour la reconstruction idéale.',
    'Un filtre anti-repliement avant la conversion limite les composantes trop hautes. Sans lui, des fréquences au-delà de la zone prévue peuvent apparaître à d’autres fréquences dans les données échantillonnées.',
    'Cette condition de fréquence ne règle pas la précision d’amplitude. Le nombre de bits du convertisseur détermine aussi ses niveaux de quantification; ces deux limites sont différentes.'
  ],'bande de base idéale : fe > 2 × Fmax',[
    Q('Audio limité à 3 kHz : quel choix respecte clairement fe > 2Fmax ?','8 kHz',['4 kHz','3 kHz','2 kHz'],'2Fmax = 6 kHz; 8 kHz est supérieur.'),
    N('Signal jusqu’à 5 kHz : double de sa fréquence maximale ?',10,'kHz','2 × 5 = 10 kHz; on choisit en pratique une cadence supérieure avec marge de filtrage.',.01),
    TF('Le filtre anti-repliement se place avant l’échantillonnage.',true,'Il limite les composantes qui créeraient du repliement.'),
    Q('Un convertisseur à 8 bits possède combien de niveaux de code ?','256',['8','16','64'],'2⁸ = 256 codes.'),
    N('Avec 10 bits, nombre de codes ?',1024,'codes','2¹⁰ = 1 024.',0),
    C('Une composante trop haute peut se ___ vers une fréquence apparente différente.','replier',['redresser automatiquement','protéger légalement','polariser en courant continu'],'Le repliement, ou aliasing, peut confondre des fréquences.'),
    TF('Augmenter seulement les bits garantit une cadence suffisante pour toute fréquence.',false,'Résolution d’amplitude et cadence d’échantillonnage sont distinctes.'),
    S('Quelles caractéristiques concernent une conversion analogique-numérique ?',['Cadence d’échantillonnage','Nombre de bits','Filtre anti-repliement','Indicatif du correspondant'],[0,1,2],'Cadence, quantification et filtrage définissent la conversion; l’indicatif concerne le trafic.')
  ]),
  L('Détecter et corriger les erreurs','Pourquoi transmettre de la redondance','Modulation',[
    'Un bit reçu peut différer du bit envoyé. Une parité ou un contrôle comme un CRC aide à détecter certaines erreurs, mais ne suffit pas toujours à corriger le contenu sans information supplémentaire.',
    'Un code de correction ajoute une redondance organisée; selon le code et le nombre d’erreurs, le récepteur peut retrouver les données. Une autre stratégie demande la retransmission lorsque le contrôle échoue.',
    'Le choix se fait avec un compromis entre débit utile, délai et robustesse. Une redondance n’est pas une garantie absolue : trop d’erreurs dépassent les capacités du système.'
  ],'',[
    Q('Un contrôle CRC sert principalement à…','Détecter des erreurs dans un bloc',['Amplifier la puissance RF','Créer un indicatif','Garantir la correction de toute erreur'],'Un CRC est un contrôle de détection; il ne reconstruit pas à lui seul tous les contenus.'),
    TF('Une redondance peut permettre la correction de certaines erreurs.',true,'Un code de correction organisé ajoute de l’information pour retrouver les données dans ses limites.'),
    Q('Quand un contrôle échoue, quelle stratégie peut être utilisée ?','Demander une retransmission',['Ignorer toujours l’erreur','Multiplier la fréquence par zéro','Déclarer les données parfaites'],'Une retransmission peut fournir une nouvelle copie exploitable.'),
    N('100 bits utiles et 25 bits de redondance : taille totale ?',125,'bits','100 + 25 = 125 bits.',0),
    N('100 bits utiles dans 125 bits transmis : part utile ?',80,'%','100/125 × 100 = 80 %.',.01),
    C('La correction d’erreurs échange souvent du débit utile contre plus de ___.','robustesse',['puissance créée','longueur d’antenne','vitesse de la lumière'],'La redondance améliore la tolérance aux erreurs au prix de ressources.'),
    TF('Tout nombre d’erreurs peut être corrigé avec n’importe quel code.',false,'Chaque code a des capacités limitées; trop d’erreurs peuvent dépasser ces capacités.'),
    M('Associe les fonctions.',[['Détection','Repérer une anomalie'],['Correction','Retrouver certaines données altérées'],['Retransmission','Demander une nouvelle copie']],'Les stratégies se complètent mais ne sont pas identiques.')
  ])
]);

chapter('c21','Le labo des bons réflexes','Écouter, mesurer, diagnostiquer et garder une station saine','#B5CF7A','lab',[
  L('Choisir un report honnête','Lisibilité, force et tonalité','Pratique opérateur',[
    'Un report doit décrire ce que tu reçois, pas seulement répéter une formule. R va de 1 à 5 pour la lisibilité, S de 1 à 9 pour la force. En CW, T de 1 à 9 décrit la qualité de tonalité.',
    'Un signal faible peut rester lisible, et un signal fort peut être déformé ou masqué. Le report sépare ces observations. Les indications d’un S-mètre dépendent aussi du récepteur et de sa calibration.',
    'Annonce des informations utiles : une difficulté de compréhension, du fading ou une tonalité altérée peut aider le correspondant à adapter la liaison.'
  ],'RS en phonie · RST en télégraphie',[
    M('Relie les lettres du report.',[['R','Lisibilité'],['S','Force du signal'],['T','Tonalité']],'Le T s’ajoute notamment en télégraphie.'),
    N('Quelle est la note maximale de lisibilité R ?',5,'','La lisibilité est graduée de 1 à 5.',0),
    N('Quelle est la note maximale de force S ?',9,'','La force est graduée de 1 à 9.',0),
    Q('Un signal faible mais parfaitement compréhensible peut avoir…','Une bonne note R et une note S plus basse',['Forcément R = 1','Obligatoirement 59','Aucun report possible'],'Lisibilité et force sont des observations distinctes.'),
    TF('Un signal fort est forcément sans distorsion.',false,'La force ne décrit pas à elle seule la qualité ou la lisibilité.'),
    C('En télégraphie, la troisième note du report concerne la ___.','tonalité',['température du câble','durée du certificat','tension du secteur'],'Le report RST ajoute la qualité de la tonalité.'),
    Q('Le correspondant demande un report. Quelle réponse est utile ?','Décrire honnêtement les conditions reçues',['Donner toujours 59 sans écouter','Répondre uniquement avec le modèle du micro','Augmenter la puissance du récepteur'],'Un report aide à comprendre la qualité réelle du lien.'),
    S('Quelles observations peuvent compléter un report ?',['Variations de signal','Difficulté de compréhension','Tonalité altérée','Mot de passe du téléphone'],[0,1,2],'Ces observations concernent directement la réception.')
  ]),
  L('Diagnostiquer avec méthode','Une variable à la fois','Station et sécurité',[
    'Un diagnostic commence par décrire le symptôme et les conditions : fréquence, mode, puissance, alimentation et éléments connectés. Reproduire une configuration connue évite de chercher au hasard.',
    'Change une variable à la fois, puis compare la mesure. Pour un problème RF, une charge fictive connue peut isoler l’émetteur de la ligne et de l’antenne. Une note avant/après conserve l’information utile.',
    'Une absence de résultat n’est pas une raison de dépasser tes compétences de sécurité. Les circuits à tension dangereuse et les installations mécaniques demandent les compétences et protections adaptées.'
  ],'',[
    O('Ordonne un diagnostic simple.',['Décrire le symptôme','Revenir à une configuration connue','Modifier une variable','Mesurer et noter le résultat'],'On transforme l’essai en comparaison reproductible.'),
    TF('Changer plusieurs éléments sans noter permet toujours de trouver la cause.',false,'On perd la relation entre la modification et son effet.'),
    Q('Le poste fonctionne sur charge fictive mais pas avec la station complète. Que regarder ensuite ?','La ligne, les connecteurs et l’antenne',['Uniquement le clavier du téléphone','L’examen de réglementation','La couleur du ventilateur'],'Le test local réduit la zone de recherche.'),
    S('Quelles conditions noter pour un défaut RF ?',['Fréquence','Mode','Puissance et alimentation','Dernier emoji envoyé'],[0,1,2],'Les conditions électriques et RF aident à reproduire le défaut.'),
    Q('Une mesure avant et après modification sert à…','Identifier l’effet de cette modification',['Garantir l’absence de toute erreur future','Créer de la puissance','Remplacer toutes les règles de sécurité'],'La comparaison relie l’action au résultat.'),
    C('Une charge de test doit avoir une impédance ___ au montage.','adaptée',['infinie dans tous les cas','nulle dans tous les cas','choisie uniquement selon sa taille'],'L’impédance et la tenue en puissance doivent convenir.'),
    TF('Un appareil qui semble fonctionner peut encore présenter un défaut de sécurité.',true,'Fonctionnement utile et sécurité sont deux contrôles distincts.'),
    Q('Un symptôme n’apparaît qu’en forte puissance. Quelle observation est pertinente ?','Comparer alimentation, échauffement et niveaux RF',['Ignorer la puissance','Modifier l’indicatif','Changer seulement le fond d’écran'],'Le niveau peut révéler une chute d’alimentation, une surcharge ou un couplage RF.')
  ]),
  L('Régler un émetteur propre','Niveau audio, charge et puissance','Émetteurs et récepteurs',[
    'Un micro trop fort peut conduire à l’écrêtage et élargir l’émission. Le réglage se fait avec une procédure et des mesures compatibles avec le poste, pas seulement en cherchant un maximum de puissance affichée.',
    'Commence les essais sur une charge adaptée supportant la durée et la puissance. Vérifie ensuite la ligne, l’antenne et les conditions de bande avant le trafic. Une puissance suffisante est préférable à une hausse sans besoin.',
    'En BLU, la puissance varie avec le contenu audio; en FM idéale, l’enveloppe reste constante. Les modes ne se règlent pas tous en regardant le même indicateur.'
  ],'',[
    Q('Un gain micro excessif peut causer…','De la distorsion et une émission trop large',['Une amélioration garantie de la lisibilité','Une réception HF parfaite','Une baisse automatique des harmoniques'],'Les étages audio ou RF peuvent écrêter le signal.'),
    TF('La puissance maximale affichée suffit à prouver une émission propre.',false,'La pureté et la linéarité exigent d’autres contrôles.'),
    Q('Pour un essai local sans rayonnement utile, utiliser…','Une charge fictive adaptée',['Une antenne inconnue à pleine puissance','Un câble ouvert au hasard','Un micro branché au secteur'],'La charge absorbe la puissance et doit supporter les conditions d’essai.'),
    S('Que vérifier avant l’essai d’émission ?',['Impédance de la charge','Tenue en puissance','Durée supportée','Uniquement la police d’écriture'],[0,1,2],'Les limites électriques et thermiques de la charge sont importantes.'),
    W('Quel exemple a une enveloppe idéale constante ?',['fm','am','noise'],['FM','AM','Bruit'],0,'En FM idéale, l’amplitude de la porteuse reste constante.'),
    C('La BLU à porteuse supprimée possède une puissance qui varie avec le contenu ___.','audio',['du carnet papier','du certificat','du nom du poste'],'L’enveloppe dépend du signal modulant.'),
    TF('Une émission moins puissante peut suffire si le correspondant reçoit correctement.',true,'On adapte la puissance à la liaison sans l’augmenter inutilement.'),
    Q('Après un réglage audio, que doit-on chercher ?','Une modulation intelligible et propre',['Un écrêtage maximal','Une fréquence hors bande','Un ROS choisi au hasard'],'L’intelligibilité et la pureté sont les objectifs utiles.')
  ]),
  L('Créer sa routine radio','Révisions et pratique durable','Pratique opérateur',[
    'Une routine efficace alterne quelques rappels anciens avec une notion nouvelle. Vérifier une erreur le lendemain teste le rappel à distance; relire immédiatement la solution est utile mais ne prouve pas encore la mémorisation durable.',
    'Pour les calculs, change les valeurs et explique la formule à voix haute. Pour les codes et le Morse, mélange l’ordre et ajoute l’écoute. Pour la station, conserve une configuration mesurée avant de modifier.',
    'Le progrès ne se mesure pas seulement à la vitesse du parcours. Une notion solide se reconnaît à sa capacité à être utilisée dans une autre situation et après un délai.'
  ],'',[
    Q('Après une erreur de conversion, quel exercice aide le transfert ?','Un nouveau calcul avec d’autres valeurs et la même conversion',['Recopier uniquement le nombre de la réponse','Éviter toutes les unités','Répondre au hasard plus vite'],'Changer les valeurs oblige à appliquer la méthode.'),
    TF('Reconnaître immédiatement une solution relue prouve toujours une mémorisation durable.',false,'Le rappel après un délai teste une autre capacité que la reconnaissance immédiate.'),
    O('Ordonne une séance de révision courte.',['Rappeler quelques notions anciennes','Travailler une difficulté ciblée','Vérifier les explications','Planifier le prochain rappel'],'La séance combine rappel, correction et espacement.'),
    S('Quelles méthodes varient utilement l’entraînement Morse ?',['Écouter','Composer','Mélanger les caractères','Toujours copier la même liste sans rappel'],[0,1,2],'Reconnaissance auditive, encodage et ordre varié entraînent des rappels complémentaires.'),
    C('Pour apprendre un calcul, expliquer la ___ aide davantage que mémoriser un seul résultat.','méthode',['couleur du bouton','taille du titre','position de la bonne réponse'],'La méthode s’applique à de nouvelles valeurs.'),
    Q('Une notion est encore fragile. Quel rythme est raisonnable ?','Revenir dessus avant d’accumuler les nouvelles notions',['Passer tous les niveaux sans correction','Multiplier seulement l’XP','Ignorer les erreurs'],'La consolidation compte plus qu’une progression précipitée.'),
    TF('Une bonne mesure doit préciser ses unités.',true,'Le nombre seul ne dit pas quelle grandeur a été mesurée.'),
    Q('Quel signe indique un apprentissage transférable ?','Résoudre une situation nouvelle avec la même règle',['Reconnaître seulement l’emplacement du bouton','Finir sans lire les unités','Répéter un nombre appris'],'Le transfert utilise le principe dans un autre contexte.')
  ])
]);

// Name punctuation characters rather than drawing their literal period or slash as Morse E/a word gap.
// Apply after answer shuffling so the existing question IDs and answer indices remain stable.
const punctuationNames={'.':'Point final',',':'Virgule','?':'Point d’interrogation','/':'Barre oblique','=':'Égal','@':'Arobase'};
const punctuationLesson=newChapters.find(c=>c.id==='c15').lessons.find(l=>l.id==='c15-l10');
for(const question of punctuationLesson.questions) {
  for(const [character,name] of Object.entries(punctuationNames)) {
    question.prompt=question.prompt.replace('le caractère '+character+' en Morse.',
      character==='/'?'la barre oblique en Morse.':'le signe « '+name.toLowerCase()+' » en Morse.');
  }
  if(question.kind!=='morseEncode') question.choices=(question.choices||[]).map(choice=>punctuationNames[choice]||choice);
  for(const pair of question.pairs||[]) pair.left=punctuationNames[pair.left]||pair.left;
}

// Keep the original IDs; insert deeper chapters beside their prerequisite concepts.
const byId=new Map([...data.chapters,...newChapters].map(c=>[c.id,c]));
const sequence=['c01','c02','c15','c16','c03','c04','c05','c06','c07','c17','c08','c09','c20','c10','c18','c11','c19','c12','c13','c21','c14'];
data.chapters=sequence.map(id=>byId.get(id));
const lessons=data.chapters.flatMap(c=>c.lessons);
data.description=`${lessons.length} leçons originales, des premiers contacts aux calculs radio approfondis. Chapitre Morse complet et révisions par notion.`;
const all=lessons.flatMap(l=>l.questions);
const seen=new Set();
for(const lesson of lessons) {
  if(lesson.questions.length<8) throw Error('Short lesson '+lesson.id);
  for(const q of lesson.questions) {
    if(seen.has(q.id)) throw Error('Duplicate '+q.id); seen.add(q.id);
    if(!q.prompt || !q.explanation) throw Error('Missing explanation '+q.id);
    if(['choice','cloze','truefalse','resistor','morseListen','waveform'].includes(q.kind)) {
      if(!Number.isInteger(q.answer)||q.answer<0||q.answer>=q.choices.length) throw Error('Answer index '+q.id);
      if(new Set(q.choices).size!==q.choices.length) throw Error('Duplicate choices '+q.id);
    }
    if(['number','binary','estimate','frequency'].includes(q.kind) && !Number.isFinite(q.value)) throw Error('Value '+q.id);
    if(q.kind==='multiselect' && (!q.bands?.length || q.bands.some(n=>!q.choices[Number(n)]))) throw Error('Multi indices '+q.id);
    if(['morseListen','morseEncode'].includes(q.kind) && !/^[.\- /]+$/.test(q.bands?.[0]||'')) throw Error('Morse '+q.id);
    if(q.kind==='waveform' && q.bands.length!==q.choices.length) throw Error('Waveform '+q.id);
    if(q.kind==='match' && (new Set(q.pairs.map(p=>p.left)).size!==q.pairs.length || new Set(q.pairs.map(p=>p.right)).size!==q.pairs.length)) throw Error('Ambiguous match '+q.id);
  }
}
const serialized=JSON.stringify(data,null,2)+'\n';
fs.writeFileSync(file,serialized,'utf8');
fs.writeFileSync('app/src/main/assets/curriculum.json',serialized,'utf8');
console.log(`${data.chapters.length} chapters, ${lessons.length} lessons, ${all.length} questions.`);
console.log(JSON.stringify(Object.fromEntries([...new Set(all.map(q=>q.kind))].sort().map(kind=>[kind,all.filter(q=>q.kind===kind).length]))));
