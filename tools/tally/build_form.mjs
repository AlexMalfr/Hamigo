import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const blocks=[],fields={},rules=[];
function id(name){const b=crypto.createHash('sha256').update('hamigo-feedback-v1:'+name).digest().subarray(0,16);b[6]=(b[6]&15)|80;b[8]=(b[8]&63)|128;const h=b.toString('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;}
function block(name,type,payload,groupType=type,groupUuid=id(name)){const value={uuid:id(name),type,groupUuid,groupType,payload};blocks.push(value);return value;}
function title(name,text,hidden=false){return block(name+'-title','TITLE',{html:text,...hidden?{isHidden:true}:{}},'QUESTION');}
const prefilledSuffix=' (pré-rempli, ne pas modifier)';
function input(name,label,{type='TEXTAREA',hidden=false,required=false,placeholder='',prefill,origin}={}){
 const heading=title(name,label,hidden);
 const prefilledHeading=prefill?title(name+'-prefilled',label+prefilledSuffix,true):undefined;
 const data={name,isRequired:required,isHidden:hidden,...placeholder?{placeholder}:{}};
 if(prefill){data.hasDefaultAnswer=true;data.defaultAnswer=fields[prefill].ref;}
 const item=block(name,type,data);
 const field={name,label,kind:type,required,initiallyHidden:hidden,...prefill?{prefill,origin,prefilledHeading:prefilledHeading.uuid}:{},ref:{uuid:item.uuid,type:'INPUT_FIELD',questionType:type,blockGroupUuid:item.groupUuid,title:label},blocks:[heading.uuid,item.uuid],answer:item.uuid};
 fields[name]=field;return field;
}
function dropdown(name,label,options,{hidden=false,required=false}={}){
 const heading=title(name,label,hidden),group=id(name+'-group');
 const opts=options.map((text,index)=>block(name+'-'+index,'DROPDOWN_OPTION',{text,index,isFirst:index===0,isLast:index===options.length-1,isHidden:hidden,isRequired:required,name},'DROPDOWN',group));
 const field={name,label,kind:'DROPDOWN',required,initiallyHidden:hidden,ref:{uuid:group,type:'INPUT_FIELD',questionType:'DROPDOWN_OPTION',blockGroupUuid:group,title:label},blocks:[heading.uuid,...opts.map(x=>x.uuid)],options:opts.map((x,i)=>({id:x.uuid,label:options[i]})),answer:opts[0].uuid};fields[name]=field;return field;
}
function condition(name,comparison,value){return {uuid:id('condition-'+name+'-'+comparison+'-'+JSON.stringify(value)),type:'SINGLE',payload:{field:fields[name].ref,comparison,...value===undefined?{}:{value}}};}
function logicRule(name,conditions,show=[],hide=[],logicalOperator='AND'){
 const targetIds=targets=>targets.flatMap(x=>fields[x]?.blocks??[x]);
 const actions=[];
 if(show.length)actions.push({uuid:id('action-'+name),type:'SHOW_BLOCKS',payload:{showBlocks:targetIds(show)}});
 if(hide.length)actions.push({uuid:id('hide-action-'+name),type:'HIDE_BLOCKS',payload:{hideBlocks:targetIds(hide)}});
 block('logic-'+name,'CONDITIONAL_LOGIC',{conditionals:conditions,logicalOperator,actions});rules.push({name,conditions,logicalOperator,show,hide});
}
block('form','FORM_TITLE',{title:'Un retour sur Hamigo',html:'Un retour sur Hamigo',button:{label:'Envoyer mon retour'}},'TEXT');
block('intro','TEXT',{html:'Une question, une idée ou un problème ? Le contexte technique transmis par Hamigo est consultable en bas du formulaire.'});
const hiddenNames=['content','question_id','question_number','question_count','question_type','question_variant','topic','section','source','source_kind','lesson_id','image','visual','title','session_title','memo_id','app_version','app_version_code','android_api','android_version','device_manufacturer','device_model','locale','screen_width_px','screen_height_px','window_width_dp','window_height_dp','density_dpi','font_scale','orientation','page','diagnostic_mode','entry_point'];
const hidden=block('context','HIDDEN_FIELDS',{hiddenFields:hiddenNames.map(name=>({uuid:id('hidden-'+name),name}))});
for(const name of hiddenNames)fields['url_'+name]={name,label:name,ref:{uuid:id('hidden-'+name),type:'HIDDEN_FIELD',questionType:'HIDDEN_FIELDS',blockGroupUuid:hidden.groupUuid,title:name},blocks:[]};
const nature=dropdown('nature','Quel type de retour veux-tu faire ?',['Bug ou comportement inattendu','Erreur dans le contenu','Suggestion','Question ou besoin d’aide','Autre'],{required:true});
const [bug,content]=nature.options.map(x=>x.id);
input('summary','Objet',{type:'INPUT_TEXT',required:true,placeholder:'Ex. Le brouillon se ferme quand je tourne le téléphone'});
input('description','Ton message',{required:true,placeholder:'Décris le problème, ton idée ou ta question. Pour une erreur de contenu, précise le passage et la correction souhaitée.'});
input('question_context','Question concernée',{type:'INPUT_TEXT',hidden:true,prefill:'url_title',origin:'question'});
input('question_reference','Identifiant de la question',{type:'INPUT_TEXT',hidden:true,prefill:'url_question_id',origin:'question'});
dropdown('content_part','Quelle partie pose problème ?',['Énoncé','Réponse attendue ou choix proposés','Correction ou explication','Illustration / montage','Lecture vocale (TTS)','Autre'],{hidden:true});
input('memo_context','Fiche Mémo concernée',{type:'INPUT_TEXT',hidden:true,prefill:'url_title',origin:'memo'});
input('lesson_context','Introduction de cours concernée',{type:'INPUT_TEXT',hidden:true,prefill:'url_title',origin:'lesson'});
dropdown('app_area','Dans quelle partie de l’app ?',['Parcours','Cours ou questions','Défis / examens / révisions','Mémo','Équipe','Moi','Réglages','Autre / je ne sais pas'],{hidden:true});
input('steps','Comment reproduire le problème ?',{hidden:true,required:true,placeholder:'1. J’ouvre…\n2. Je touche…\n3. Le problème apparaît…'});
input('expected','Qu’attendais-tu à la place ?',{hidden:true,required:true});
dropdown('frequency','À quelle fréquence cela arrive-t-il ?',['À chaque fois','Parfois','Une seule fois','Je ne sais pas'],{hidden:true});
title('files','Captures ou fichiers (facultatif)');
block('files','FILE_UPLOAD',{name:'attachments',hasMultipleFiles:true,hasMaxFiles:true,maxFiles:5,hasMaxFileSize:true,maxFileSize:10,maxFileSizeUnit:'MB',isRequired:false});
block('files-help','TEXT',{html:'Jusqu’à 5 fichiers de 10 Mo. Évite les mots de passe et sauvegardes personnelles.'});
input('email','E-mail (facultatif, pour recevoir une réponse)',{type:'INPUT_EMAIL',placeholder:'ton-adresse@exemple.fr'});
const diagnosticsLabel='Données de diagnostic envoyées avec ce formulaire';
const diagnosticsCheckbox=block('show-diagnostics','CHECKBOX',{name:diagnosticsLabel,text:diagnosticsLabel,index:0,isFirst:true,isLast:true,isRequired:false},'CHECKBOXES',id('show-diagnostics-group'));
fields.show_diagnostics={name:'show_diagnostics',label:diagnosticsLabel,kind:'CHECKBOX',required:false,initiallyHidden:false,ref:{uuid:diagnosticsCheckbox.groupUuid,type:'INPUT_FIELD',questionType:'CHECKBOX',blockGroupUuid:diagnosticsCheckbox.groupUuid,title:diagnosticsLabel},blocks:[diagnosticsCheckbox.uuid],answer:diagnosticsCheckbox.uuid};
const diagnosticGroups=[
 {name:'application',parts:['Application : ',['app_version'],' (code ',['app_version_code'],'). Android : ',['android_version'],' (API ',['android_api'],').']},
 {name:'device',parts:['Appareil : ',['device_manufacturer'],' ',['device_model'],'. Langue : ',['locale'],'.']},
 {name:'display',parts:['Écran : ',['screen_width_px'],' × ',['screen_height_px'],' px. Fenêtre : ',['window_width_dp'],' × ',['window_height_dp'],' dp. Densité : ',['density_dpi'],' dpi. Taille du texte : ',['font_scale'],'. Orientation : ',['orientation'],'.']},
 {name:'origin',parts:['Origine : ',['entry_point'],'. Page : ',['page'],'. Nature du contenu : ',['content'],'.']},
 {name:'content',parts:['Contenu : ',['title'],'. Source : ',['source'],' (', ['source_kind'],').']},
 {name:'question',parts:['Question : ',['question_id'],' · position ',['question_number'],' sur ',['question_count'],'. Type : ',['question_type'],'. Présentation : ',['question_variant'],'.']},
 {name:'path',parts:['Thème : ',['topic'],'. Section : ',['section'],'. Cours : ',['lesson_id'],'. Séance : ',['session_title'],'.']},
 {name:'media',parts:['Fiche Mémo : ',['memo_id'],'. Image : ',['image'],'. Illustration : ',['visual'],'. Mode diagnostics : ',['diagnostic_mode'],'.']}
];
const diagnosticBlocks=[];
for(const group of diagnosticGroups){
 const html=group.parts.map(part=>{
  if(typeof part==='string')return part;
  const name=part[0],uuid=fields['url_'+name].ref.uuid;
  return `<span class="mention" data-uuid="${uuid}" contenteditable="false">@${name}</span>`;
 }).join('');
 diagnosticBlocks.push(block('diagnostics-'+group.name,'TEXT',{html,isHidden:true}).uuid);
}
const diagnosticPrivacy=block('diagnostics-privacy','TEXT',{html:'Ces données de contexte sont jointes même si cette section reste repliée. « Non transmis » signifie que cette information ne figure pas dans le lien. Hamigo n’ajoute ni réponse personnelle, progression, liste d’amis, identifiant de sauvegarde ni clé secrète.',isHidden:true});
diagnosticBlocks.push(diagnosticPrivacy.uuid);
logicRule('question-origin',[condition('url_content','IS','question')],['question_context','question_reference']);
logicRule('memo-origin',[condition('url_content','IS','memo')],['memo_context']);
logicRule('lesson-origin',[condition('url_content','IS','lesson')],['lesson_context']);
logicRule('generic-app',[condition('url_content','IS','app'),condition('url_content','IS_EMPTY')],['app_area'],[],'OR');
logicRule('bug-details',[condition('nature','IS',bug)],['steps','expected','frequency']);
logicRule('content-details',[condition('nature','IS',content)],['content_part']);
for(const field of Object.values(fields).filter(f=>f.prefill)){
 logicRule(field.name+'-prefilled',[condition('url_content','IS',field.origin),condition(field.prefill,'IS_NOT_EMPTY')],[field.prefilledHeading],[field.blocks[0]]);
}
logicRule('expand-diagnostics',[condition('show_diagnostics','IS_NOT_EMPTY')],diagnosticBlocks);
// Tally resolves mentions globally from FORM_TITLE, including those rendered by TEXT blocks.
// TEXT.payload.mentions is rejected by the API even though its OpenAPI union permits it.
blocks[0].payload.mentions=hiddenNames.map(name=>({uuid:fields['url_'+name].ref.uuid,field:fields['url_'+name].ref,defaultValue:'Non transmis'}));
const body={status:'DRAFT',settings:{language:'fr',hasSelfEmailNotifications:true,saveForLater:false},blocks};
const out=path.join(root,'data/tally');fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(path.join(out,'hamigo-feedback.create.json'),JSON.stringify(body,null,2)+'\n');
const contract={schemaVersion:2,description:'Hamigo feedback form; update existing 9NqlpK, preserving URL names and stable field IDs',prefilledSuffix,hiddenUrlFields:hiddenNames,fields:Object.fromEntries(Object.entries(fields).filter(([name])=>!name.startsWith('url_')).map(([name,f])=>[name,{label:f.label,kind:f.kind,required:f.required,initiallyHidden:f.initiallyHidden,...f.prefill?{prefill:f.prefill.replace(/^url_/,''),origin:f.origin,prefilledHeading:f.prefilledHeading}:{},ref:f.ref,blocks:f.blocks,...f.options?{options:f.options}:{}}])),diagnostics:{control:'show_diagnostics',nativeAccordion:false,implementation:'optional checkbox with conditional read-only text',blocks:diagnosticBlocks,groups:diagnosticGroups.map(g=>({name:g.name,urlFields:g.parts.filter(Array.isArray).map(p=>p[0])}))},rules};
fs.writeFileSync(path.join(out,'hamigo-feedback.contract.json'),JSON.stringify(contract,null,2)+'\n');
const md=['# Un retour sur Hamigo','','Formulaire unique : bug, erreur de contenu, suggestion, question ou autre. Pas de connexion imposée.','',...Object.values(contract.fields).flatMap(f=>['## '+f.label+(f.required?' (obligatoire si visible)':' (facultatif)'),...(f.options?.map(o=>'- '+o.label)??[f.kind==='CHECKBOX'?'Case qui affiche ou masque les données de diagnostic en lecture seule.':'Réponse libre.']), '']),'Pièces jointes facultatives : 5 fichiers maximum, 10 Mo par fichier.','','Les titres de contexte portent « (pré-rempli, ne pas modifier) » seulement si le paramètre URL correspondant est non vide.','','Le récapitulatif de diagnostic montre les 33 valeurs URL reçues ; aucune valeur personnelle ni aucun secret ne doit être ajouté à ces paramètres.','','Les règles et champs cachés sont décrits dans hamigo-feedback.contract.json ; importer ce Markdown seul ne garantit pas leur reprise.'];
fs.writeFileSync(path.join(root,'docs/tally/questionnaire-a-importer.md'),md.join('\n')+'\n');
console.log(JSON.stringify({blocks:blocks.length,hiddenFields:hiddenNames.length,visibleFields:Object.keys(contract.fields).length,conditionalRules:rules.length,status:body.status}));
