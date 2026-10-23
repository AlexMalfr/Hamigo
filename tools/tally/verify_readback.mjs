import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=name=>JSON.parse(fs.readFileSync(path.join(root,name),'utf8').replace(/^\uFEFF/,''));
const local=read('data/tally/hamigo-feedback.create.json');
const remote=read('output/tally-retours-22/published-form.readback.json');
const contract=read('data/tally/hamigo-feedback.contract.json');
let htmlNormalizations=0;
const safeText=nodes=>nodes.map(node=>Array.isArray(node[0])?safeText(node[0]):node[0]).join('');
const safeNodes=nodes=>nodes.flatMap(node=>[node,...Array.isArray(node[0])?safeNodes(node[0]):[]]);
const plainHtml=html=>html.replace(/<[^>]*>/g,'').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>');
function compare(expected,actual,p){
 if(Array.isArray(expected)){
  assert.ok(Array.isArray(actual),p+' must be an array');assert.equal(actual.length,expected.length,p+'.length');
  expected.forEach((v,i)=>compare(v,actual[i],p+'['+i+']'));
 }else if(expected&&typeof expected==='object'){
  for(const [k,v]of Object.entries(expected)){
   if(k==='html'&&actual?.html===undefined&&actual?.safeHTMLSchema){
    assert.equal(safeText(actual.safeHTMLSchema),plainHtml(v),p+'.safeHTMLSchema text');htmlNormalizations++;
   }else compare(v,actual?.[k],p+'.'+k);
  }
 }else assert.equal(actual,expected,p);
}
compare({...local,status:'PUBLISHED'},remote,'form');
assert.equal(remote.id,'9NqlpK');
assert.equal(remote.settings.styles.theme,contract.theme.styles.theme);
for(const [key,value]of Object.entries(contract.theme.styles.color))assert.equal(remote.settings.styles.color[key].toLowerCase(),value.toLowerCase(),'Free theme color '+key);
assert.equal(remote.blocks.find(b=>b.type==='FORM_TITLE').payload.logo,contract.theme.logoUrl);
assert.equal(remote.settings.hasSelfEmailNotifications,true);
const hidden=remote.blocks.find(b=>b.type==='HIDDEN_FIELDS').payload.hiddenFields;
assert.deepEqual(hidden.map(f=>f.name),contract.hiddenUrlFields);
const logic=remote.blocks.filter(b=>b.type==='CONDITIONAL_LOGIC');assert.equal(logic.length,contract.rules.length);
const registry=remote.blocks.find(b=>b.type==='FORM_TITLE').payload.mentions;
assert.equal(registry.length,33);
const mentionIds=new Set(registry.map(m=>m.uuid));
const diagnosticBlocks=remote.blocks.filter(b=>contract.diagnostics.blocks.includes(b.uuid));
const renderedMentionIds=diagnosticBlocks.flatMap(b=>safeNodes(b.payload.safeHTMLSchema??[]).flatMap(node=>(node[1]??[]).filter(attr=>attr[0]==='mention').map(attr=>attr[1])));
assert.equal(renderedMentionIds.length,33);
const diagnosticNodes=diagnosticBlocks.filter(block=>block.uuid===contract.diagnostics.listBlock).flatMap(b=>safeNodes(b.payload.safeHTMLSchema??[]));
const styled=(key,value)=>diagnosticNodes.filter(node=>(node[1]??[]).some(attr=>attr[0]===key&&attr[1]===value)).length;
assert.equal(styled('font-weight','bold'),25);assert.equal(styled('font-style','italic'),25);assert.equal(styled('tag','br'),24);
assert.deepEqual([...renderedMentionIds].sort(),[...mentionIds].sort());
const files=remote.blocks.find(b=>b.type==='FILE_UPLOAD').payload;
const report={formId:remote.id,status:remote.status,remoteApiUpdated:true,remoteApiReadBack:true,allRequestedBlocksPersisted:true,freeThemeVerified:true,picoLogoVerified:true,htmlNormalizedByTally:htmlNormalizations,hiddenUrlFields:hidden.length,conditionalRules:logic.length,diagnosticMentionsRegistered:registry.length,diagnosticMentionsPersisted:renderedMentionIds.length,ownerEmailNotificationsEnabled:true,restoreOldFormProgressEnabled:remote.settings.saveForLater,files:{maximum:files.maxFiles,megabytesEach:files.maxFileSize},renderingObservedSeparately:true,realSubmissionSent:false,testEmailSent:false,published:true};
fs.writeFileSync(path.join(root,'output/tally-retours-22/server-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
