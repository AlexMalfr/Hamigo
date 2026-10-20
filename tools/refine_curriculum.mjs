// Follow-up editorial checks after the one-time restructuring. Stable identities are never reallocated.
import fs from 'node:fs';
import {randomUUID} from 'node:crypto';
const path='data/curriculum.json';
const data=JSON.parse(fs.readFileSync(path,'utf8'));
const lessons=data.chapters.flatMap(c=>c.lessons);
const get=id=>lessons.find(l=>l.id===id);
const rhythm=get('c02-l03');
const first=data.chapters.find(c=>c.id==='c15');
const second=data.chapters.find(c=>c.id==='ch-morse-retour-a');
first.lessons=first.lessons.filter(l=>l.id!==rhythm.id);
if(!second.lessons.some(l=>l.id===rhythm.id))second.lessons.unshift(rhythm);
rhythm.knownCharacters='ETIMAN';
const introduction='Deux rythmes inversés : A se compose d’un point puis d’un trait ; N d’un trait puis d’un point. A : •━ ; N : ━•. Ils rejoignent les caractères E, T, I et M déjà rencontrés.';
if(!rhythm.body.includes(introduction))rhythm.body.push(introduction);
get('c15-l13').knownCharacters='ABCDEFGHIJKLMNOPQRSTUVWXYZ';
get('c02-l01').knownCharacters='ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const introductions={
 'c15-l01':'ETIM','c02-l03':'AN','c15-l02':'SO','c15-l03':'DURK','c15-l04':'GWBV','c15-l05':'FLPJ','c15-l06':'CQXYZH','c15-l07':'12345','c15-l08':'67890','c15-l10':'?/.,=@',
 'l-nato-premiers':'ABCDEF','l-nato-milieu':'GHIJKLMNOP','l-nato-fin':'QRSTUVWXYZ'
};
for(const lesson of lessons)lesson.introducedCharacters=introductions[lesson.id]||'';
// A mathematical example must not require a physical concept that is introduced later.
const movedQuestions=[
 ['q-afdaf1f1-a08a-4570-a836-d568f0e27316','c16-l02','l-rms-sans-confusion','rms-sine'],
 ['q-4b849548-bf6f-4489-89c8-98abaa59c0ca','c16-l02','c06-l02','period-frequency'],
 ['q-082699e8-1a27-47d0-a926-3facbd6894c9','c16-l02','l-condensateur-polarite','capacitors'],
 ['q-f88fb19d-f946-4f5c-8317-c4c838adb7d1','c16-l03','c07-l01','lc-resonance'],
 ['q-71e6ef72-3eca-4793-b526-f69b796b6d11','c16-l03','c17-l03','impedance-phase']
];
for(const [id,from,to,skill]of movedQuestions){
 const source=get(from);const target=get(to);const index=source.questions.findIndex(q=>q.id===id);
 if(index>=0){const [question]=source.questions.splice(index,1);question.skillIds=[skill];if(!target.questions.some(q=>q.id===id))target.questions.push(question);}
}
const rootLesson=get('c16-l03');
rootLesson.body=rootLesson.body.map(p=>p.replace('Une racine carrée agit dans l’autre sens : multiplier LC par neuf divise la résonance par trois.','Une racine carrée agit dans l’autre sens : pour un nombre positif, multiplier le nombre par neuf multiplie sa racine par trois.'));
// Explicit skill-level prerequisites guide review without making the free path inaccessible.
for(const lesson of lessons){
 if(lesson.id.startsWith('l-morse-'))lesson.prerequisiteSkillIds=[...new Set([...lesson.prerequisiteSkillIds,'morse-spacing'])];
}
const pools={
 'Quel mot représente la lettre W ?':['William','Washington','Water','Wellington','Walter'],
 'Dans l’alphabet international, la lettre X s’épelle ___.':['Xylophone','Xavier','Xenon','Xtra','Xylitol'],
 '0 dBm correspond à…':['0 W','1 W','1 V','10 mW','1 µW'],
 'Un rapport de puissance de 10 correspond à…':['1 dB','3 dB','20 dB','−10 dB','30 dB'],
 'Quel repère de temps utilise le total journalier POTA ?':['L’heure locale de l’activateur','La journée Hamigo commençant à 03 h','Le fuseau du correspondant','Le dernier jour de connexion','L’heure locale du parc sans conversion'],
 'Dans POTA, comment appelle-t-on la station située dans le parc ?':['Chasseur','Relais','Contrôleur','Récepteur témoin','Point de passage'],
 'Dans cette chaîne, à quoi sert le décodeur SSTV ?':['À attribuer un certificat','À augmenter l’orbite','À remplacer l’antenne','À créer une émission RF sans poste','À garantir un report de 59'],
 'Des signaux fantômes se multiplient en montant le gain. Que tester ?':['Toujours pousser le gain à fond','Émettre sans indicatif','Changer seulement la couleur du graphe','Augmenter le volume du haut-parleur','Ignorer tous les signaux forts'],
 'Quelle formulation demande si la fréquence est occupée ?':['73','599','QRT','QTH ?','QRS'],
 'Une ligne étroite persistante permet-elle d’identifier à elle seule le service ?':['Oui toujours','Oui si elle est rouge','Oui avec un grand écran','Oui avec le seul niveau maximal','Oui sans connaître la fréquence']
};
for(const lesson of lessons)for(const q of lesson.questions)if(q.kind==='choice'&&pools[q.prompt]){
 const correct=q.choices[q.answer];q.distractors=pools[q.prompt].filter(x=>x!==correct);q.choices=[correct,...q.distractors.slice(0,3)];q.answer=0;
}
for(const lesson of lessons)for(const question of lesson.questions){
 const isCall=question.kind==='morseEncode'&&/\bF\d[A-Z]{2,3}\b/.test(question.prompt)||question.kind==='morseListen'&&question.choices.some(choice=>/^F\d[A-Z]{2,3}$/.test(choice));
 if(isCall){
  const fallback=question.kind==='morseListen'?question.choices[question.answer]:question.prompt.match(/\bF\d[A-Z]{2,3}\b/)[0];
  question.template={type:'morse-call',sourceText:question.kind==='morseEncode'?'Compose le groupe d’entraînement {{call}} en Morse.':'Écoute ce groupe d’entraînement puis choisis les caractères transmis.',explanationText:'Un groupe d’entraînement se transmet caractère par caractère ; il ne constitue pas un indicatif attribué par Hamigo.',characters:'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789',fallback,mode:question.kind==='morseEncode'?'encode':'listen'};
 }
 if(question.template){
  if(question.template.type==='nato-call')question.template.explanationText='Épelle chaque lettre dans son ordre avec son mot international, et prononce distinctement le chiffre.';
  if(question.template.type==='morse-name')question.template.explanationText='Retrouve les caractères du groupe en respectant les pauses entre lettres.';
  if(question.template.type==='morse-call')question.template.explanationText||='Retrouve le groupe transmis caractère par caractère avant de l’interpréter.';
  if(question.template.characters==='0123456789')question.template={...question.template,type:'morse-call',length:2};
 }
}
const additionSources={rules:'https://www.arcep.fr/uploads/tx_gsavis/12-1241.pdf',f6:'http://f6kgl.free.fr/COURS.html#R12',anfr:'https://www.anfr.fr/gerer/radioamateurs/cadre-juridique'};
const extraSkills=[['amateur-messages','Connaître les limites des messages amateur'],['emission-designation','Lire les désignations des émissions'],['station-control','Comprendre le contrôle de la station']];
const registry=JSON.parse(fs.readFileSync('data/question-identities.json','utf8'));
const question=(prompt,correct,wrong,explanation)=>({id:'q-'+randomUUID(),kind:'choice',prompt,choices:[correct,...wrong],answer:0,explanation});
function regulatoryLesson(id,title,summary,skill,body,questions,refs,source){
 if(get(id))return get(id);
 for(const q of questions){q.skillIds=[skill];q.source=additionSources[source];q.section='regulation';registry.entries[q.id]={legacyId:'',fingerprint:''};}
 const lesson={id,title,summary,topic:title,body,questions,skillIds:[skill],certificateWeight:2,explorationWeight:1,referenceIds:refs,knownCharacters:'',introducedCharacters:'',legacyLessonIds:[],prerequisiteSkillIds:[]};
 lessons.push(lesson);return lesson;
}
if(!get('l-messages-amateur')){
 const messages=regulatoryLesson('l-messages-amateur','Ce qu’un échange amateur peut porter','Objet du service, tiers et sens des transmissions','amateur-messages',[
  'Les transmissions amateur servent l’instruction, l’intercommunication et les études techniques, sans intérêt pécuniaire. Le contenu doit rester lié à cet objet ou aux remarques personnelles prévues par les textes.',
  'L’article 1 de la décision française interdit de coder les échanges pour en obscurcir le sens, avec l’exception prévue pour les signaux de commande de satellites amateurs. Un code documenté comme le Morse n’est pas, par sa seule utilisation, un chiffrement destiné à cacher le sens.',
  'Les communications pour des tiers non amateurs sont réservées aux situations d’urgence ou aux secours en catastrophe dans ce cadre. Un logiciel ou une passerelle ne supprime pas ces conditions.'
 ],[
  question('Une station amateur peut-elle vendre régulièrement un service de transmission ?','Non',['Oui avec un logiciel','Oui à faible puissance','Oui après dix contacts'],'Le service est sans intérêt pécuniaire.'),
  question('Quel usage du codage est interdit ici ?','Obscurcir le sens hors exception prévue',['Employer du Morse lisible','Compresser des données documentées','Épeler un indicatif'],'Le texte vise le but de cacher le sens, avec une exception définie pour la commande spatiale.'),
  question('Dans ce cadre, des communications pour des tiers non amateurs peuvent concerner…','L’urgence ou les secours en catastrophe',['Un service commercial courant','Un catalogue publicitaire','Des livraisons payantes permanentes'],'L’exception est limitée aux situations prévues.'),
  question('Un mode numérique change-t-il ces obligations ?','Non',['Oui toujours','Oui après minuit','Oui si son nom contient automatique'],'Le mode technique ne change pas l’objet autorisé du service.')
 ],['radio-regulations','operating-rules'],'rules');
 const emissions=regulatoryLesson('l-emissions-designation','Lire F3E et J3E','Une désignation décrit plusieurs caractéristiques','emission-designation',[
  'La désignation d’une émission combine plusieurs symboles. Dans les exemples à trois caractères, le premier décrit la modulation de la porteuse, le deuxième la nature du signal modulant et le troisième le type d’information.',
  'F3E correspond à une modulation de fréquence, un canal contenant de l’information analogique et de la téléphonie. J3E désigne la téléphonie en bande latérale unique avec porteuse supprimée. A1A est un repère usuel de télégraphie par manipulation de porteuse.',
  'La désignation ne donne pas à elle seule la largeur occupée, la puissance ou le droit d’utiliser une bande. Le mode doit rester compatible avec les conditions nationales et le plan de bande.'
 ],[
  question('Quel exemple désigne ici de la téléphonie FM ?','F3E',['J3E','A1A','Un numéro de parc'],'F décrit la modulation de fréquence et E la téléphonie.'),
  question('Quel exemple désigne la téléphonie BLU à porteuse supprimée ?','J3E',['F3E','A1A','RS 59'],'J3E est le repère donné pour la BLU à porteuse supprimée.'),
  question('A1A correspond ici à…','De la télégraphie par manipulation de porteuse',['Un report de réception','La tension d’alimentation','Un indicatif français'],'La désignation décrit la nature de l’émission.'),
  question('La seule désignation F3E suffit-elle pour connaître tous les droits d’émission ?','Non',['Oui','Oui si le poste la propose','Oui avec une antenne de 2 m'],'La bande, la puissance et la largeur ont leurs propres conditions.')
 ],['emissions','modulations'],'f6');
 const control=regulatoryLesson('l-controle-responsabilite','Une station sous responsabilité','Contrôle, brouillage et conséquences','station-control',[
  'Le respect du cadre amateur reste une responsabilité de l’exploitant. L’administration peut contrôler la station et demander les renseignements prévus, dont le journal. Conserver des traces exactes aide à répondre et à rechercher un brouillage.',
  'L’ANFR distingue les conditions d’exploitation, les modalités d’attribution ou de retrait des indicatifs et les dispositions pénales du code. Une sanction administrative et une sanction pénale ne sont pas la même procédure.',
  'Une infraction ne devient pas autorisée parce qu’elle a duré peu de temps ou n’a pas été signalée immédiatement. Pour les textes, peines et procédures exacts, consulte la version en vigueur ; les liens anciens d’une édition peuvent viser un état abrogé.'
 ],[
  question('Le journal peut-il être demandé lors d’un contrôle ?','Oui',['Non jamais','Uniquement par les amis','Seulement si Hamigo est connecté'],'Le cadre français prévoit sa présentation aux autorités de contrôle.'),
  question('Sanction administrative et sanction pénale désignent-elles la même procédure ?','Non',['Oui','Oui si l’antenne est petite','Oui après le certificat'],'Leurs bases et procédures sont distinctes.'),
  question('Une émission brève hors des conditions autorisées devient-elle légale par sa brièveté ?','Non',['Oui','Oui si personne ne répond','Oui à faible volume audio'],'La durée ne remplace pas les conditions d’utilisation.'),
  question('Pour vérifier une peine ou une procédure précise, que consulter ?','Le texte en vigueur',['Une capture ancienne sans date','Un classement XP','La couleur d’un poste'],'Il faut distinguer les versions successives du droit.')
 ],['operating-rules','radio-regulations'],'anfr');
 const insertion=data.chapters.findIndex(c=>c.id==='ch-trafic-propre');
 data.chapters.splice(insertion,0,{id:'ch-messages-regles',sectionId:'sec-links',title:'Décrire une émission et son message',subtitle:'Désignations et objet des échanges',lessons:[emissions,messages]},{id:'ch-controle',sectionId:'sec-links',title:'La responsabilité de l’opérateur',subtitle:'Comprendre les contrôles et les textes',lessons:[control]});
}
fs.writeFileSync(path,JSON.stringify(data,null,2)+'\n');
const skills=JSON.parse(fs.readFileSync('data/skills.json','utf8'));
skills.skills.find(s=>s.id==='morse-characters').title='Reconnaître les caractères Morse';
for(const [id,title]of extraSkills)if(!skills.skills.some(s=>s.id===id))skills.skills.push({id,title});
fs.writeFileSync('data/skills.json',JSON.stringify(skills,null,2)+'\n');
fs.writeFileSync('data/question-identities.json',JSON.stringify(registry,null,2)+'\n');
const narration=JSON.parse(fs.readFileSync('data/lesson-narration.json','utf8'));
for(const [id,overrides]of Object.entries(narration)){
 const lesson=get(id);overrides.body=(overrides.body||[]).flatMap(entry=>{const index=lesson.body.indexOf(entry.source);return index<0?[]:[{...entry,index}];});
 if(overrides.formula?.source!==lesson.formula)delete overrides.formula;
 if(!overrides.body.length&&!overrides.formula)delete narration[id];
}
fs.writeFileSync('data/lesson-narration.json',JSON.stringify(narration,null,2)+'\n');
console.log('Morse prerequisites, taught-character metadata and distractor reservoirs refined.');
