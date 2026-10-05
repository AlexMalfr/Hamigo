import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
const read=name=>JSON.parse(fs.readFileSync(`data/reference-${name}.json`,'utf8'));
const additions=read('additions'),technical=read('technical');
const category=id=>additions.categories.find(c=>c.id===id);
const row=(term,description,extra='',group='',visual='',source='http://f6kgl.free.fr/COURS.html#T014b',kind='fact')=>({term,description,extra,group,kind,visual,source});
const append=(cat,rows)=>{for(const r of rows)if(!cat.rows.some(x=>x.term===r.term))cat.rows.push(r);};

const materials=[['Argent','1,6 × 10⁻⁸','Métaux et alliages'],['Cuivre écroui','1,8 × 10⁻⁸','Métaux et alliages'],['Or','2,2 × 10⁻⁸','Métaux et alliages'],['Aluminium','3 × 10⁻⁸','Métaux et alliages'],['Laiton','6 × 10⁻⁸','Métaux et alliages'],['Fer','1 × 10⁻⁷','Métaux et alliages'],['Constantan','4,9 × 10⁻⁷','Métaux et alliages'],['Nichrome','1,1 × 10⁻⁶','Métaux et alliages'],['Eau de mer','0,3','Autres matériaux'],['Germanium','0,46','Autres matériaux'],['Silicium','640','Autres matériaux'],['Eau pure','2 × 10⁵','Autres matériaux'],['Air sec','1,13 × 10⁹','Isolants'],['Porcelaine','10¹¹','Isolants'],['Polyéthylène','10¹⁵','Isolants'],['Papier','10¹⁵','Isolants'],['Bakélite','10¹⁶','Isolants'],['Plexiglas','10¹⁷','Isolants'],['Quartz','7 × 10¹⁷','Isolants'],['Polystyrène','10²⁰','Isolants']];
if(!category('materials')) additions.categories.push({id:'materials',title:'Matériaux : résistivité à 20 °C',subtitle:'',group:'Électricité',order:15,intro:'Les 20 repères du tableau F6KGL. Une faible résistivité facilite la conduction. Pour un fil homogène : R = ρ × longueur / section. L’humidité, la pureté, l’état du matériau et la température changent ces valeurs : ce sont des repères du cours, pas des spécifications garanties.',source:'http://f6kgl.free.fr/COURS.html#T014b',flashcards:true,rows:materials.map(([term,value,group])=>row(term,`ρ = ${value} Ω·m`,'',group))});
// Repair existing entries too: rerunning the enrichment must retain exact source anchors.
category('materials').source='http://f6kgl.free.fr/COURS.html#T014b';
for(const r of category('materials').rows) r.source='http://f6kgl.free.fr/COURS.html#T014b';

append(technical.categories.find(c=>c.id==='resistors'),[6,12,24].map(n=>row(`Série E${n}`,`${n} valeurs nominales par décennie`,'Multiplie ces nombres par une puissance de dix pour choisir des Ω, kΩ ou MΩ. Une série plus fine fournit des valeurs plus rapprochées.','Valeurs normalisées','extra:preferred-series','http://f6kgl.free.fr/COURS.html#T015')));

append(category('transformers-batteries'),[
 row('Zinc-charbon','Pile : environ 1,5 V par élément','Couple électrolytique donné par le cours ; la tension baisse en usage.','Chimie et tension des éléments','','http://f6kgl.free.fr/COURS.html#T033'),
 row('Cadmium-nickel (Ni-Cd)','Accumulateur : environ 1,2 V par élément','Le cours emploie cette chimie pour distinguer une pile d’un accumulateur rechargeable.','Chimie et tension des éléments','','http://f6kgl.free.fr/COURS.html#T033'),
 row('Plomb : électrodes et électrolyte','Plomb Pb côté négatif ; dioxyde de plomb PbO₂ côté positif ; acide sulfurique H₂SO₄.','La décharge forme du sulfate de plomb PbSO₄ ; la recharge inverse la réaction.','Chimie et tension des éléments','','http://f6kgl.free.fr/COURS.html#T033'),
 row('Plomb : repères de tension','Le cours donne par élément : chargé 2,2 V ; en décharge environ 2 V ; déchargé 1,8 V.','Valeurs pédagogiques selon l’état et les conditions de mesure. Six éléments nominaux de 2 V forment une batterie de 12 V.','Chimie et tension des éléments','','http://f6kgl.free.fr/COURS.html#T033')
]);

const attach=(id,term,visual)=>{const r=category(id).rows.find(r=>r.term===term);if(!r)throw Error(`Missing ${id}/${term}`);r.visual=`extra:${visual}`;};
// Native tables make the same fixed facts easier to compare without prose full of 0/1 strings.
append(category('logic-digital'),[
 row('Tables de vérité des six portes','Compare la sortie de chaque porte pour les quatre combinaisons de A et B.','ET, OU, NON, NAND, NOR et XOR : les définitions sont détaillées dans cette section.','Logique','extra:logic-table','http://f6kgl.free.fr/COURS.html#T084'),
 row('Décimal, binaire et hexadécimal','Les seize combinaisons d’un groupe de quatre bits.','Un chiffre hexadécimal correspond à quatre bits.','Binaire','extra:binary-table','http://f6kgl.free.fr/COURS.html#T085')
]);
append(category('dc-circuits'),[row('Comparer série et parallèle','Quelles grandeurs sont identiques et comment se répartissent les autres ?','','Groupements','extra:series-parallel-table','http://f6kgl.free.fr/COURS.html#T017')]);
append(category('filters'),[row('RLC : comparer les trois modèles','L’emplacement de la résistance de pertes change l’impédance et le facteur Q.','','RLC','extra:rlc-table','http://f6kgl.free.fr/COURS.html#T044')]);
append(category('transistors-tubes'),[row('Symboles NPN et PNP','La flèche de l’émetteur indique le sens conventionnel du courant.','Elle sort du NPN et entre dans le PNP. Les bornes restent B : base, C : collecteur, E : émetteur.','Bipolaires','extra:transistor-symbols','http://f6kgl.free.fr/COURS.html#T061')]);

const tos=category('transmission-lines').rows.find(r=>r.term==='ROS et TOS');
tos.description='Le ROS est un rapport. Dans le cours, le TOS est le taux d’amplitude réfléchie : TOS = 100 × |Γ| %. La fraction de puissance réfléchie vaut |Γ|².';
tos.extra='Pour ROS = 2 : |Γ| = 1/3, TOS ≈ 33,3 %, mais puissance réfléchie ≈ 11,1 %. Certains usages appellent le ROS « TOS » ; vérifie toujours la grandeur demandée.';
tos.visual='extra:reflection-table';
append(category('radio-blocks'),[row('S-mètre HF : tous les niveaux','Repères du cours pour une entrée de 50 Ω, de S0 à S9 + 30 dB.','La calibration et la définition peuvent différer en VHF/UHF.','Qualité','extra:s-meter-table','http://f6kgl.free.fr/COURS.html#T114')]);
append(technical.categories.find(c=>c.id==='decibels'),[
 row('Table : puissance et tension','Les rapports usuels pour un gain ou une atténuation.','','Repères de calcul','extra:db-table','http://f6kgl.free.fr/COURS.html#T041'),
 row('Dizaines et unités de dB','Décompose le nombre de dB puis multiplie les deux rapports.','','Repères de calcul','extra:db-unit-table','http://f6kgl.free.fr/COURS.html#T041')
]);

const old=JSON.parse(execFileSync('git',['-c',`safe.directory=${process.cwd().replaceAll('\\','/')}`,'show','fa933d65:data/reference.json'],{encoding:'utf8'}));
const legacy=old.categories.find(c=>c.id==='exam-rules').rows;
const exam=category('exam-rules');
const legacyExamAnchors={2:'Intro2',3:'Intro2',4:'Intro2',5:'Intro2',6:'R45',7:'Intro1',8:'R33a',9:'R41',10:'R41',11:'R42e',12:'R54b',13:'R34',14:'R34'};
const remap={2:['Bonne réponse','Une bonne réponse vaut 1 point.'],3:['Erreur ou omission','Une réponse incorrecte ou une absence de réponse vaut 0 point.'],4:['Admission','Il faut au moins 10 sur 20 dans chacune des deux épreuves.'],5:['Une seule épreuve réussie','Le bénéfice d’une épreuve réussie se conserve un an.'],6:['Après un échec','Une nouvelle présentation exige un délai minimal de deux mois.'],7:['Morse au certificat actuel','Le Morse n’est plus une épreuve du certificat français.']};
for(let i=2;i<legacy.length;i++) {
 const term=legacy[i].term;
 if(exam.rows.some(r=>r.cardId===`flash-exam-rules-${i}`))continue;
 let description=remap[i]?.[1]??legacy[i].description;
 if(i===11)description='Déclarer à l’ANFR une installation fixe dont la puissance apparente rayonnée dépasse 5 W ; actualiser les caractéristiques.';
 const r=row(term,description,legacy[i].extra??'','Compatibilité des révisions','',`http://f6kgl.free.fr/COURS.html#${legacyExamAnchors[i]??'R45'}`,'flashcard-only');
 r.cardId=`flash-exam-rules-${i}`;exam.rows.push(r);
}
const visibleExamAnchors={'Barème':'Intro2','Épreuve acquise':'R45','Aménagement':'Intro5b','Morse':'Intro1','À apporter':'Intro5a','Après réussite':'R45','Classe unique et HAREC':'Intro1','Préparer son brouillon':'Intro5c'};
for(const r of exam.rows) {
 const legacyId=r.cardId?.match(/^flash-exam-rules-(\d+)$/);
 const anchor=r.kind==='flashcard-only'&&legacyId?legacyExamAnchors[Number(legacyId[1])]:visibleExamAnchors[r.term];
 if(anchor)r.source=`http://f6kgl.free.fr/COURS.html#${anchor}`;
}
for(const [name,data] of [['technical',technical],['additions',additions]]) fs.writeFileSync(`data/reference-${name}.json`,JSON.stringify(data,null,2)+'\n');
console.log(JSON.stringify({additionalCategories:additions.categories.length,additionalRows:additions.categories.reduce((n,c)=>n+c.rows.length,0)}));
