// Original Hamigo lessons and a lossless reorganisation of existing reference identities.
// Called after the older generators; safe to repeat without duplicating lessons or cards.
import fs from 'node:fs';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
const topic = 'Circuits numériques';
const source = 'http://f6kgl.free.fr/COURS.html#T084';
const Q = (prompt, correct, other, explanation, visual = '') => ({kind:'choice', prompt, choices:[correct,...other], answer:0, explanation, visual});
const TF = (prompt, value, explanation) => ({kind:'truefalse', prompt, choices:['Vrai','Faux'], answer:value?0:1, explanation});
const N = (prompt, value, unit, explanation) => ({kind:'number', prompt, value, unit, tolerance:0, choices:[], answer:0, explanation});
const M = (prompt, pairs, explanation) => ({kind:'match', prompt, choices:[], answer:0, pairs:pairs.map(([left,right])=>({left,right})), explanation});
const B = (prompt, value, explanation) => ({kind:'binary', prompt, value, unit:'8', choices:[], answer:0, explanation});
const lesson = (number, title, summary, body, formula, visuals, questions) => {
    const id = `c22-l${String(number).padStart(2,'0')}`;
    return {id,title,summary,topic,body,formula,visuals,questions:questions.map((q,i)=>({...q,id:`${id}-q${String(i+1).padStart(2,'0')}`,topic,section:'technique',source:number===2?'http://f6kgl.free.fr/COURS.html#T085':source}))};
};

export function splitDigitalCurriculum(data) {
    const digital = data.chapters.find(c=>c.id==='c20');
    const previous = data.chapters.find(c=>c.id==='c22');
    const bits = digital?.lessons.find(l=>l.id==='c20-l01') ?? previous?.lessons.find(l=>l.id==='c20-l01');
    if (!bits) throw Error('The stable binary lesson c20-l01 is missing');
    const chapter = {id:'c22',title:'Binaire et portes logiques',subtitle:'Lire les bits, reconnaître les portes et suivre un circuit',color:'#558C85',icon:'binary',lessons:[bits,
        lesson(2,'Du binaire à l’hexadécimal','Les poids des bits et les groupes de quatre',[
            'En binaire, chaque position vaut deux fois celle à sa droite. Pour huit bits, lis les poids 128, 64, 32, 16, 8, 4, 2 et 1. Additionne seulement les poids dont le bit vaut 1 : 00101101₂ = 32 + 8 + 4 + 1 = 45.',
            'Un octet contient huit bits, soit 256 combinaisons ; un entier non signé sur un octet va de 0 à 255. Un zéro placé à gauche ne change pas la valeur, mais permet de garder une largeur fixe.',
            'L’hexadécimal utilise seize chiffres : 0 à 9, puis A pour 10, B pour 11, C pour 12, D pour 13, E pour 14 et F pour 15. Chaque chiffre correspond exactement à quatre bits. Sépare donc 00101101₂ en 0010 et 1101 : tu obtiens 2D₁₆.',
            'Attention aux unités : bit et octet diffèrent d’un facteur huit. Les préfixes SI sont décimaux (1 ko = 1 000 octets) ; les préfixes IEC sont binaires (1 Kio = 1 024 octets). Le cours ancien utilise parfois « ko » pour cette seconde valeur.'
        ],'1 octet = 8 bits · 1 chiffre hexadécimal = 4 bits',[],[
            B('Compose 45 sur huit bits.',45,'45 = 32 + 8 + 4 + 1 : 00101101.'),
            N('Quelle est la valeur décimale de 00110010₂ ?',50,'base 10','32 + 16 + 2 = 50.'),
            Q('Quel nombre représente A₁₆ ?','10',['12','16','1'],'Les chiffres A à F représentent 10 à 15.'),
            Q('11110000₂ s’écrit en hexadécimal…','F0',['0F','FF','10'],'1111 vaut F et 0000 vaut 0. L’ordre des groupes compte.'),
            N('Quelle est la plus grande valeur entière non signée d’un octet ?',255,'base 10','Huit bits donnent 256 valeurs, de 0 à 255.'),
            M('Associe chaque unité à sa taille.',[['1 octet','8 bits'],['1 Kio','1 024 octets'],['1 ko','1 000 octets'],['1 chiffre hexadécimal','4 bits']],'Unité de données et préfixe donnent deux informations distinctes.'),
            TF('00001010₂ et 1010₂ représentent le même entier.',true,'Les zéros à gauche ne changent pas la somme des poids activés.'),
            B('Compose la valeur de FF₁₆ sur huit bits.',255,'F vaut 1111 ; deux F donnent huit bits à 1 : 255.')
        ]),
        lesson(3,'Lire ET, OU et NON','Des symboles aux tables de vérité',[
            'Une porte logique transforme des entrées binaires en une sortie binaire. 0 et 1 désignent des niveaux logiques ; les tensions réelles et leurs seuils dépendent de la famille du composant. Ne suppose pas que 1 signifie toujours exactement 5 V.',
            'Dans la représentation rectangulaire CEI utilisée en France, les entrées arrivent à gauche et la sortie S est à droite. La marque dans le rectangle indique la fonction. Un petit cercle à une borne signifie une inversion logique.',
            'La porte ET porte la marque &. Elle donne 1 seulement si A ET B valent tous deux 1. Pour 00, 01 et 10, la sortie vaut 0 ; pour 11, elle vaut 1.',
            'La porte OU porte la marque ≥1 : il suffit d’au moins une entrée à 1. Ses sorties pour 00, 01, 10 et 11 sont 0, 1, 1 et 1. Ce OU est inclusif : deux entrées à 1 donnent aussi 1.',
            'La porte NON possède une seule entrée. Son rectangle porte 1, et le cercle en sortie indique que la sortie est le complément : 0 devient 1 et 1 devient 0. Une table de vérité énumère les entrées possibles et la sortie correspondante.'
        ],'ET : toutes les entrées à 1 · OU : au moins une à 1 · NON : inverse', ['', '', 'logic:and', 'logic:or', 'logic:not'],[
            Q('Quelle fonction représente cette porte ?','ET',['OU','NON','OU exclusif'],'La marque & désigne la fonction ET.','logic:and'),
            Q('Cette porte reçoit A = 1 et B = 0. Quelle est S ?','1',['0','2','Impossible à déterminer'],'OU donne 1 dès qu’au moins une entrée vaut 1.','logic:or'),
            Q('Cette porte reçoit A = 1. Quelle est S ?','0',['1','2','Elle conserve la dernière valeur'],'Le cercle inverse le niveau : NON 1 = 0.','logic:not'),
            TF('Une porte ET donne 1 pour A = 0 et B = 1.',false,'ET exige que les deux entrées soient à 1.'),
            M('Associe la marque à la fonction.',[['&','ET'],['≥1','OU'],['1 avec cercle en sortie','NON']],'Lis la marque du rectangle, puis applique une éventuelle inversion.'),
            N('Deux entrées binaires indépendantes donnent combien de combinaisons ?',4,'combinaisons','Les quatre combinaisons sont 00, 01, 10 et 11 : 2² = 4.'),
            Q('Les deux entrées d’une porte OU valent 1. La sortie vaut…','1',['0','2','Un niveau indéfini'],'OU est inclusif. Ce n’est pas une addition arithmétique de 1 + 1.'),
            TF('Le niveau logique 1 correspond à la même tension sur tous les composants.',false,'Les seuils et tensions dépendent de la technologie et de l’alimentation.')
        ]),
        lesson(4,'Inverser et combiner les portes','NON ET, NON OU et OU exclusif',[
            'NON ET, ou NAND, est une porte ET suivie d’une inversion. Son rectangle porte & et un cercle en sortie. Elle vaut 0 seulement pour A = B = 1 ; elle vaut 1 pour les trois autres combinaisons.',
            'NON OU, ou NOR, est une porte OU suivie d’une inversion. Son rectangle porte ≥1 et un cercle en sortie. Elle vaut 1 seulement pour A = B = 0 ; elle vaut 0 dès qu’une entrée vaut 1.',
            'Le OU exclusif, ou XOR, à deux entrées porte =1 : exactement une entrée doit valoir 1. Les entrées différentes (01 ou 10) donnent 1 ; les entrées identiques (00 ou 11) donnent 0. Les exemples de cette leçon utilisent toujours deux entrées.',
            'Pour lire un circuit combiné, suis les fils de gauche à droite. Calcule chaque sortie intermédiaire, puis utilise-la comme entrée de la porte suivante. Exemple : ET(1, 1) = 1, puis NON(1) = 0. Ce circuit équivaut à NON ET(1, 1).',
            'Une inversion agit à l’endroit où son cercle est placé. Inverser A avant une porte ET n’est pas équivalent à inverser la sortie de ET. Pour A = 0 et B = 0, ET(NON A, B) vaut 0, alors que NON(ET(A, B)) vaut 1.'
        ],'NAND = NON(ET) · NOR = NON(OU) · XOR = entrées différentes',['logic:nand','logic:nor','logic:xor'],[
            Q('A = 1 et B = 1 pour cette porte. Quelle est S ?','0',['1','2','La valeur de A seulement'],'ET vaut 1 ; le cercle l’inverse en 0.','logic:nand'),
            Q('A = 0 et B = 0 pour cette porte. Quelle est S ?','1',['0','2','Un niveau indéfini'],'OU vaut 0 ; le cercle l’inverse en 1.','logic:nor'),
            Q('A = 1 et B = 1 pour cette porte. Quelle est S ?','0',['1','2','Elle oscille forcément'],'À deux entrées, =1 signifie exactement une entrée active. Deux entrées identiques donnent 0.','logic:xor'),
            TF('Pour deux entrées différentes, un OU exclusif donne 1.',true,'01 et 10 sont les deux lignes où XOR vaut 1.'),
            Q('ET(A, B), puis NON : pour A = 1 et B = 0, la sortie finale vaut…','1',['0','2','Une tension toujours égale à 5 V'],'ET(1, 0) = 0 ; NON(0) = 1. C’est une fonction NON ET.'),
            Q('A = 0, B = 1. On inverse A avant une porte ET avec B : sortie ?','1',['0','2','Impossible avec deux portes'],'NON A vaut 1 ; ET(1, 1) vaut 1.'),
            M('Associe chaque condition à la porte donnant 1.',[['Toutes les entrées sont 1','ET'],['Au moins une entrée est 1','OU'],['Toutes les entrées sont 0','NON OU'],['Deux entrées sont différentes','OU exclusif']],'Ces conditions distinguent les fonctions, pour des portes à deux entrées.'),
            TF('Déplacer un cercle d’inversion de la sortie à une seule entrée conserve toujours le résultat.',false,'ET(NON A, B) et NON(ET(A, B)) sont deux fonctions différentes.')
        ])]};
    digital.lessons = digital.lessons.filter(l=>l.id!=='c20-l01');
    digital.subtitle = 'Symboles, échantillons et correction d’erreurs';
    data.chapters = data.chapters.filter(c=>c.id!=='c22');
    data.chapters.splice(data.chapters.findIndex(c=>c.id==='c20'),0,chapter);
    data.description = `${data.chapters.flatMap(c=>c.lessons).length} leçons originales, des premiers contacts aux calculs radio approfondis. Chapitres Morse et logique numérique, révisions par notion.`;
    return data;
}

export function splitDigitalReferences(categories) {
    const old = categories.find(c=>c.id==='logic-digital');
    const existing = categories.filter(c=>['binary-logic','digital-signals'].includes(c.id)).flatMap(c=>c.rows);
    const rows = [...old.rows,...existing];
    // The fragment historically received its IDs during merging, under the old
    // category name. Materialise those identities BEFORE moving the rows.
    rows.forEach(r=> {
        r.cardId ??= `flash-logic-digital-${crypto.createHash('sha256').update(r.term).digest('hex').slice(0,12)}`;
        if (/^flash-(binary-logic|digital-signals)-[a-f0-9]{12}$/.test(r.cardId))
            r.cardId = r.cardId.replace(/^flash-(binary-logic|digital-signals)-/, 'flash-logic-digital-');
    });
    const distinct = [...new Map(rows.map(r=>[r.cardId||r.term,r])).values()];
    old.rows = distinct.filter(r=>!['Logique','Binaire','Signal numérique'].includes(r.group));
    old.title = 'Amplificateurs opérationnels';
    old.intro = 'Entrées, contre-réaction et montages utiles des AOP.';
    const binary = {id:'binary-logic', title:'Binaire et portes logiques', subtitle:'',group:old.group,order:30.1,intro:'Des poids des bits aux tables de vérité, avec les symboles rectangulaires CEI utilisés en France.',source,flashcards:true,rows:distinct.filter(r=>['Binaire','Logique'].includes(r.group))};
    const visuals = {'Porte ET':'logic:and','Porte OU':'logic:or','Porte NON':'logic:not','NON ET et NON OU':'logic:nand-nor','OU exclusif':'logic:xor'};
    binary.rows.forEach(r=>{
        r.source = `http://f6kgl.free.fr/COURS.html#${r.group==='Binaire'?'T085':'T084'}`;
        if (visuals[r.term]) r.visual=visuals[r.term];
    });
    // New orientation row has a stable explicit identity; all existing card IDs are untouched.
    if (!binary.rows.some(r=>r.term==='Lire les symboles rectangulaires')) binary.rows.push({term:'Lire les symboles rectangulaires',description:'Entrées à gauche, sortie S à droite. La marque du rectangle donne la fonction : & pour ET, ≥1 pour OU, =1 pour OU exclusif à deux entrées. Un cercle à une borne inverse le niveau.',extra:'La porte NON a une seule entrée, la marque 1 et un cercle en sortie. Les niveaux électriques réels dépendent de la famille du composant.',group:'Logique',kind:'fact',visual:'',source,cardId:'flash-binary-logic-reading-symbols'});
    binary.rows.sort((a,b)=>['Binaire','Logique'].indexOf(a.group)-['Binaire','Logique'].indexOf(b.group) || Number(b.term==='Lire les symboles rectangulaires')-Number(a.term==='Lire les symboles rectangulaires'));
    const signals = {id:'digital-signals',title:'Conversion et traitement numérique',subtitle:'',group:old.group,order:30.2,intro:'Passer d’un signal analogique aux nombres : conversions, échantillonnage, quantification et traitement.',source:'http://f6kgl.free.fr/COURS.html#T085',flashcards:true,rows:distinct.filter(r=>r.group==='Signal numérique').map(r=>({...r,source:'http://f6kgl.free.fr/COURS.html#T085'}))};
    return categories.filter(c=>!['binary-logic','digital-signals'].includes(c.id)).flatMap(c=>c===old?[old,binary,signals]:[c]);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === fs.realpathSync(process.argv[1])) {
    const curriculum=splitDigitalCurriculum(JSON.parse(fs.readFileSync('data/curriculum.json','utf8')));
    for(const path of ['data/curriculum.json','app/src/main/assets/curriculum.json']) fs.writeFileSync(path,JSON.stringify(curriculum,null,2)+'\n');
    const refs=JSON.parse(fs.readFileSync('data/reference-additions.json','utf8'));
    refs.categories=splitDigitalReferences(refs.categories);
    fs.writeFileSync('data/reference-additions.json',JSON.stringify(refs,null,2)+'\n');
}
