import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const theme=JSON.parse(fs.readFileSync(path.join(root,'data/tally/hamigo-feedback.theme.json'),'utf8'));
const blocks=[],fields={},rules=[];
function id(name){const b=crypto.createHash('sha256').update('hamigo-feedback-v1:'+name).digest().subarray(0,16);b[6]=(b[6]&15)|80;b[8]=(b[8]&63)|128;const h=b.toString('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;}
function block(name,type,payload,groupType=type,groupUuid=id(name)){const value={uuid:id(name),type,groupUuid,groupType,payload};blocks.push(value);return value;}
function title(name,text,hidden=false){const html=text.replace(/ (\([^()]*\))$/,' <span style="font-style: italic;">$1</span>');return block(name+'-title','TITLE',{html,...hidden?{isHidden:true}:{}},'QUESTION');}
function retainedText(uuid,groupUuid,html,hidden=false){const value={uuid,type:'TEXT',groupUuid,groupType:'TEXT',payload:{html:html||'<br>',...hidden?{isHidden:true}:{}}};blocks.push(value);return value;}
const prefilledSuffix=' (pré-rempli)';
function input(name,label,{type='TEXTAREA',hidden=false,required=false,placeholder='',prefill,origin}={}){
 const heading=title(name,label,hidden);
 const prefilledHeading=prefill?title(name+'-prefilled',label+prefilledSuffix,true):undefined;
 const data={name,isRequired:required,isHidden:hidden,...placeholder?{placeholder}:{}};
 // URL defaults initialise hidden inputs too, causing unrelated contexts in submissions.
 // Keep manual fields empty and show a read-only value only for the matching origin.
 const item=block(name,type,data);
 const prefilledValue=prefill?block(name+"-prefilled-value","TEXT",{html:`<span class="mention" data-uuid="${fields[prefill].ref.uuid}" contenteditable="false">@${fields[prefill].ref.title}</span>`,isHidden:true}):undefined;
 const field={name,label,kind:type,required,initiallyHidden:hidden,...prefill?{prefill,origin,prefilledHeading:prefilledHeading.uuid,prefilledValue:prefilledValue.uuid}:{},ref:{uuid:item.uuid,type:'INPUT_FIELD',questionType:type,blockGroupUuid:item.groupUuid,title:label},blocks:[heading.uuid,item.uuid],answer:item.uuid};
 fields[name]=field;return field;
}
function dropdown(name,label,options,{hidden=false,required=false}={}){
 const heading=title(name,label,hidden),group=id(name+'-group');
 const normalized=options.map((option,index)=>typeof option==='string'?{key:index,label:option}:option);
 const opts=normalized.map(({key,label:text},index)=>block(name+'-'+key,'DROPDOWN_OPTION',{text,index,isFirst:index===0,isLast:index===options.length-1,isHidden:hidden,isRequired:required,name},'DROPDOWN',group));
 const field={name,label,kind:'DROPDOWN',required,initiallyHidden:hidden,ref:{uuid:group,type:'INPUT_FIELD',questionType:'DROPDOWN_OPTION',blockGroupUuid:group,title:label},blocks:[heading.uuid,...opts.map(x=>x.uuid)],options:opts.map((x,i)=>({id:x.uuid,label:normalized[i].label})),answer:opts[0].uuid};fields[name]=field;return field;
}
function condition(name,comparison,value){return {uuid:id('condition-'+name+'-'+comparison+'-'+JSON.stringify(value)),type:'SINGLE',payload:{field:fields[name].ref,comparison,...value===undefined?{}:{value}}};}
function conditionGroup(name,logicalOperator,conditionals){return {uuid:id('condition-group-'+name),type:'GROUP',payload:{logicalOperator,conditionals}};}
function contextAllowed(){return conditionGroup('context-feedback-type','OR',contextNatureOptions.map(value=>condition('nature','IS',value)));}
function selectedContext(origin){return conditionGroup('selected-context-'+origin,'AND',[contextAllowed(),conditionGroup('active-area-'+origin,'OR',[condition('app_area','IS',contextAreaOptions[origin]),conditionGroup('url-context-'+origin,'AND',[condition('app_area','IS_EMPTY'),condition('url_content','IS',origin)])])]);}
function logicRule(name,conditions,show=[],hide=[],logicalOperator='AND'){
 const targetIds=targets=>targets.flatMap(x=>fields[x]?.blocks??[x]);
 const actions=[];
 if(show.length)actions.push({uuid:id('action-'+name),type:'SHOW_BLOCKS',payload:{showBlocks:targetIds(show)}});
 if(hide.length)actions.push({uuid:id('hide-action-'+name),type:'HIDE_BLOCKS',payload:{hideBlocks:targetIds(hide)}});
 block('logic-'+name,'CONDITIONAL_LOGIC',{conditionals:conditions,logicalOperator,actions});rules.push({name,conditions,logicalOperator,show,hide});
}
block('form','FORM_TITLE',{title:'Un retour sur Hamigo',html:'Un retour sur Hamigo',logo:theme.logoUrl,button:{label:'Envoyer mon retour'}},'TEXT');
block('intro','TEXT',{html:'Une question, une idée ou un problème ? Ton retour aide à améliorer Hamigo.'});
const diagnosticIntro=block('diagnostics-intro','TEXT',{html:'Les données de contexte jointes par Hamigo sont consultables en bas du formulaire.',isHidden:true});
const hiddenNames=['content','question_id','question_number','question_count','question_type','question_variant','topic','section','source','source_kind','lesson_id','image','visual','title','session_title','memo_id','app_version','app_version_code','android_api','android_version','device_manufacturer','device_model','locale','screen_width_px','screen_height_px','window_width_dp','window_height_dp','density_dpi','font_scale','orientation','page','diagnostic_mode','entry_point'];
const hidden=block('context','HIDDEN_FIELDS',{hiddenFields:hiddenNames.map(name=>({uuid:id('hidden-'+name),name}))});
for(const name of hiddenNames)fields['url_'+name]={name,label:name,ref:{uuid:id('hidden-'+name),type:'HIDDEN_FIELD',questionType:'HIDDEN_FIELDS',blockGroupUuid:hidden.groupUuid,title:name},blocks:[]};
const nature=dropdown('nature','Quel type de retour veux-tu faire ?',['Bug ou comportement inattendu','Erreur dans le contenu','Suggestion','Question ou besoin d’aide','Autre'],{required:true});
const [bug,content,suggestion]=nature.options.map(x=>x.id);
const contextNatureOptions=[bug,content,suggestion];
input('summary','Objet',{type:'INPUT_TEXT',required:true,placeholder:'Ex. Le brouillon se ferme quand je tourne le téléphone'});
input('description','Ton message',{required:true,placeholder:'Décris le problème, ton idée ou ta question. Pour une erreur de contenu, précise le passage et la correction souhaitée.'});
const appAreas=dropdown('app_area','Quelle partie de l’app est concernée ?',[
 {key:0,label:'Parcours'},{key:1,label:'Question ou exercice'},{key:'lesson',label:'Introduction de cours'},
 {key:2,label:'Défis / examens / révisions'},{key:3,label:'Mémo'},{key:4,label:'Équipe'},
 {key:5,label:'Moi'},{key:6,label:'Réglages'},{key:7,label:'Autre / je ne sais pas'}],{hidden:true,required:true});
const contextAreaOptions=Object.fromEntries([['question','Question ou exercice'],['memo','Mémo'],['lesson','Introduction de cours']].map(([origin,label])=>[origin,appAreas.options.find(option=>option.label===label).id]));
input('question_context','Question concernée',{type:'INPUT_TEXT',hidden:true,prefill:'url_title',origin:'question'});
input('question_reference','Identifiant de la question',{type:'INPUT_TEXT',hidden:true,prefill:'url_question_id',origin:'question'});
dropdown('content_part','Quelle partie pose problème ?',['Énoncé','Réponse attendue ou choix proposés','Correction ou explication','Illustration / montage','Lecture vocale (TTS)','Autre'],{hidden:true});
input('memo_context','Fiche Mémo concernée',{type:'INPUT_TEXT',hidden:true,prefill:'url_title',origin:'memo'});
input('lesson_context','Introduction de cours concernée',{type:'INPUT_TEXT',hidden:true,prefill:'url_title',origin:'lesson'});
input('steps','Comment reproduire le problème ?',{hidden:true,required:true,placeholder:"1. J’ouvre…\n2. J'appuie sur…\n3. Il se passe…"});
input('expected','Qu’attendais-tu à la place ?',{hidden:true,required:true,placeholder:"Il devrait se passer..."});
dropdown('frequency','À quelle fréquence cela arrive-t-il ?',['À chaque fois','Parfois','Une seule fois','Je ne sais pas'],{hidden:true,required:true});
title('files','Captures ou fichiers (facultatif)');
block('files','FILE_UPLOAD',{name:'attachments',hasMultipleFiles:true,hasMaxFiles:true,maxFiles:5,hasMaxFileSize:true,maxFileSize:10,maxFileSizeUnit:'MB',isRequired:false});
block('files-help','TEXT',{html:'Jusqu’à 5 fichiers de 10 Mo. Évite les mots de passe et sauvegardes personnelles.'});
input('email','E-mail (facultatif, pour recevoir une réponse)',{type:'INPUT_EMAIL',placeholder:'ton-adresse@exemple.fr'});
const diagnosticsLabel='Cocher cette case pour afficher les données de diagnostic envoyées avec ce formulaire';
retainedText("09af38ce-d7da-4e88-9cae-84ff860aa282","cfe64a0a-ceb0-4b1f-a7c3-4dc1b5fa2b81","",false);
const noDiagnostics=block('diagnostics-absent','TEXT',{html:"<span style=\"color: rgb(89, 118, 120);\"><span style=\"font-style: italic;\">Aucune donnée de diagnostic n’est envoyée avec ce formulaire.\n</span></span>",isHidden:true});
const diagnosticsCheckbox=block('show-diagnostics','CHECKBOX',{name:diagnosticsLabel,text:diagnosticsLabel,index:0,isFirst:true,isLast:true,isRequired:false,isHidden:true},'CHECKBOXES',id('show-diagnostics-group'));
fields.show_diagnostics={name:'show_diagnostics',label:diagnosticsLabel,kind:'CHECKBOX',required:false,initiallyHidden:true,ref:{uuid:diagnosticsCheckbox.groupUuid,type:'INPUT_FIELD',questionType:'CHECKBOX',blockGroupUuid:diagnosticsCheckbox.groupUuid,title:diagnosticsLabel},blocks:[diagnosticsCheckbox.uuid],answer:diagnosticsCheckbox.uuid};
const diagnosticGroups=[
 {"name":"application","label":"Application","parts":[["app_version"]," (code ",["app_version_code"],")"]},
 {"name":"android","label":"Android","parts":[["android_version"]," (API ",["android_api"],")"]},
 {"name":"device","label":"Appareil","parts":[["device_manufacturer"]," ",["device_model"]]},
 {"name":"locale","label":"Langue de l’appareil","parts":[["locale"]]},
 {"name":"display","label":"Écran","parts":[["screen_width_px"]," × ",["screen_height_px"]," px"]},
 {"name":"window","label":"Fenêtre","parts":[["window_width_dp"]," × ",["window_height_dp"]," dp"]},
 {"name":"density","label":"Densité","parts":[["density_dpi"]," dpi"]},
 {"name":"font","label":"Taille du texte","parts":[["font_scale"]]},
 {"name":"orientation","label":"Orientation","parts":[["orientation"]]},
 {"name":"origin","label":"Origine","parts":[["entry_point"]]},
 {"name":"page","label":"Page","parts":[["page"]]},
 {"name":"content-kind","label":"Nature du contenu","parts":[["content"]]},
 {"name":"content","label":"Contenu","parts":[["title"]]},
 {"name":"source","label":"Source","parts":[["source"]," (",["source_kind"],")"]},
 {"name":"question","label":"Question","parts":[["question_id"]," · position ",["question_number"]," sur ",["question_count"]]},
 {"name":"type","label":"Type","parts":[["question_type"]]},
 {"name":"variant","label":"Présentation","parts":[["question_variant"]]},
 {"name":"path","label":"Thème","parts":[["topic"]]},
 {"name":"section","label":"Section","parts":[["section"]]},
 {"name":"lesson","label":"Cours","parts":[["lesson_id"]]},
 {"name":"session","label":"Séance","parts":[["session_title"]]},
 {"name":"media","label":"Fiche Mémo","parts":[["memo_id"]]},
 {"name":"image","label":"Image","parts":[["image"]]},
 {"name":"visual","label":"Illustration","parts":[["visual"]]},
 {"name":"mode","label":"Mode diagnostics","parts":[["diagnostic_mode"]]}
];
// Tally supports inline spans and line breaks, but strips HTML ul/li containers.
// A single text block keeps this bulleted list compact and toggles it atomically.
const diagnosticHtml=diagnosticGroups.map(group=>{
 const value=group.parts.map(part=>typeof part==='string'?part:
  `<span class="mention" data-uuid="${fields['url_'+part[0]].ref.uuid}" contenteditable="false">@${part[0]}</span>`).join('');
 return `<span style="color: #597678;">• <span style="font-weight: bold;">${group.label} :</span> <span style="font-style: italic;">${value}</span></span>`;
}).join('<br>');
const diagnosticBlocks=[block('diagnostics-application','TEXT',{html:diagnosticHtml,isHidden:true}).uuid];
diagnosticBlocks.push(retainedText("29fb94fa-e36c-4769-9b58-027eac6eaa8f","39988c72-bbd7-4efd-ac8a-f7f48c5cdaab","<span style=\"font-style: italic;\">« Non transmis » signifie que cette information ne figure pas dans le lien. Hamigo n’ajoute ni réponse personnelle, progression, liste d’amis, identifiant de sauvegarde ni clé secrète.</span>",true).uuid);
const diagnosticPrivacy=block('diagnostics-privacy','TEXT',{html:"<span style=\"font-style: italic;\">Ces données de diagnostic (utiles pour identifier un problème dans certains cas) sont jointes même si cette section reste repliée. Pour ne pas inclure ces données, tu peux décocher « Joindre les données de diagnostic » avant d’ouvrir le formulaire dans Hamigo pour ne pas les transmettre.</span>",isHidden:true});
diagnosticBlocks.push(diagnosticPrivacy.uuid,retainedText("21242298-c790-41a5-b76c-c774adc0d6ae","a917e397-a0bd-4f83-9e88-60951f63f399","<span style=\"font-style: italic;\">Ces données ne seront pas utilisées à d'autres fins que l'amélioration de l'application Hamigo.</span>",true).uuid,retainedText("6aad5627-5966-475c-8f5e-7a19e143400c","d97f8eb7-aa9b-4fba-93b0-00b8e3b1257f","<span style=\"font-style: italic;\">\n</span>",true).uuid);
logicRule('context-by-feedback-type',[contextAllowed()],['app_area']);
logicRule('question-origin',[selectedContext('question')],['question_context','question_reference']);
logicRule('memo-origin',[selectedContext('memo')],['memo_context']);
logicRule('lesson-origin',[selectedContext('lesson')],['lesson_context']);
logicRule('bug-details',[condition('nature','IS',bug)],['steps','expected','frequency']);
logicRule('content-details',[condition('nature','IS',content),selectedContext('question')],['content_part']);
for(const field of Object.values(fields).filter(f=>f.prefill)){
 logicRule(field.name+'-prefilled',[condition('url_content','IS',field.origin),condition(field.prefill,'IS_NOT_EMPTY'),selectedContext(field.origin)],[field.prefilledHeading,field.prefilledValue],[field.blocks[0],field.answer]);
}
logicRule('diagnostics-present',hiddenNames.map(name=>condition('url_'+name,'IS_NOT_EMPTY')),[diagnosticIntro.uuid,'show_diagnostics'],[],'OR');
logicRule('expand-diagnostics',[condition('show_diagnostics','IS_NOT_EMPTY')],diagnosticBlocks);
// Apply absence last so even a stale or pre-populated viewing answer cannot reveal empty diagnostics.
logicRule('diagnostics-absent',hiddenNames.map(name=>condition('url_'+name,'IS_EMPTY')),[noDiagnostics.uuid],[diagnosticIntro.uuid,'show_diagnostics',...diagnosticBlocks]);
// Tally resolves mentions globally from FORM_TITLE, including those rendered by TEXT blocks.
// TEXT.payload.mentions is rejected by the API even though its OpenAPI union permits it.
blocks[0].payload.mentions=hiddenNames.map(name=>({uuid:fields['url_'+name].ref.uuid,field:fields['url_'+name].ref,defaultValue:'Non transmis'}));
const body={status:'DRAFT',settings:{language:'fr',hasSelfEmailNotifications:true,saveForLater:false,redirectOnCompletion:{html:'https://alexandre.malfre.yt/projects/Hamigo/retour.html'}},blocks};
const out=path.join(root,'data/tally');fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(path.join(out,'hamigo-feedback.create.json'),JSON.stringify(body,null,2)+'\n');
const contract={schemaVersion:6,theme,contextNatureOptions,contextAreaOptions,completionRedirect:body.settings.redirectOnCompletion.html,description:'Hamigo feedback form; update existing 9NqlpK, preserving URL names and stable field IDs',prefilledSuffix,hiddenUrlFields:hiddenNames,fields:Object.fromEntries(Object.entries(fields).filter(([name])=>!name.startsWith('url_')).map(([name,f])=>[name,{label:f.label,kind:f.kind,required:f.required,initiallyHidden:f.initiallyHidden,...f.prefill?{prefill:f.prefill.replace(/^url_/,''),origin:f.origin,prefilledHeading:f.prefilledHeading,prefilledValue:f.prefilledValue}:{},ref:f.ref,blocks:f.blocks,...f.options?{options:f.options}:{}}])),diagnostics:{listBlock:diagnosticBlocks[0],control:'show_diagnostics',presence:{urlFields:hiddenNames,introBlock:diagnosticIntro.uuid,absentBlock:noDiagnostics.uuid,absentMessage:'Aucune donnée de diagnostic n’est envoyée avec ce formulaire.'},nativeAccordion:false,implementation:'show view-only diagnostics only when a recognized URL parameter is nonempty; otherwise show only the absence message; diagnostic opt-out happens in Hamigo before opening Tally',blocks:diagnosticBlocks,groups:diagnosticGroups.map(g=>({name:g.name,urlFields:g.parts.filter(Array.isArray).map(p=>p[0])}))},rules};
fs.writeFileSync(path.join(out,'hamigo-feedback.contract.json'),JSON.stringify(contract,null,2)+'\n');
const md=['# Un retour sur Hamigo','','Formulaire unique : bug, erreur de contenu, suggestion, question ou autre. Pas de connexion imposée.','',...Object.values(contract.fields).flatMap(f=>['## '+f.label+(f.required?' (obligatoire si visible)':' (facultatif)'),...(f.options?.map(o=>'- '+o.label)??[f.kind==='CHECKBOX'?'Case qui affiche ou masque les données de diagnostic en lecture seule.':'Réponse libre.']), '']),'Pièces jointes facultatives : 5 fichiers maximum, 10 Mo par fichier.','','Le choix de la partie de l’app et ses champs de contexte apparaissent pour Bug, Erreur dans le contenu et Suggestion. Ses choix Question ou exercice, Mémo et Introduction de cours affichent le contexte correspondant juste en dessous. En l’absence de choix manuel, l’origine URL choisit le contexte. Les titres portent « (pré-rempli) » seulement si l’origine correspond et que le paramètre URL est non vide.','','Le récapitulatif de diagnostic est disponible seulement si au moins un des 33 paramètres URL reconnus est non vide. Sinon, seule la ligne « Aucune donnée de diagnostic n’est envoyée avec ce formulaire. » apparaît. Aucune valeur personnelle ni aucun secret ne doit être ajouté à ces paramètres.','','Les règles et champs cachés sont décrits dans hamigo-feedback.contract.json ; importer ce Markdown seul ne garantit pas leur reprise.'];
fs.writeFileSync(path.join(root,'docs/tally/questionnaire-a-importer.md'),md.join('\n')+'\n');
console.log(JSON.stringify({blocks:blocks.length,hiddenFields:hiddenNames.length,visibleFields:Object.keys(contract.fields).length,conditionalRules:rules.length,status:body.status}));
