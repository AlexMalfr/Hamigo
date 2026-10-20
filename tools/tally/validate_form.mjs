import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const read=p=>JSON.parse(fs.readFileSync(path.join(root,p),'utf8').replace(/^\uFEFF/,''));
const specification=read('output/tally-retours-22/openapi-2026-10-22.json');
const body=read('data/tally/hamigo-feedback.create.json'),contract=read('data/tally/hamigo-feedback.contract.json');
const Ajv=createRequire(import.meta.url)('ajv');
const schema={...specification.paths['/forms'].post.requestBody.content['application/json'].schema,components:specification.components};
const validate=new Ajv({allErrors:true,nullable:true,schemaId:'auto',unknownFormats:'ignore'}).compile(schema);
assert.ok(validate(body),JSON.stringify(validate.errors));
// The OpenAPI BlockPayload union accepts keys for a different block type. The API does not.
for(const block of body.blocks){
 const blockSchema=Object.values(specification.components.schemas).find(s=>s.properties?.type?.enum?.length===1&&s.properties.type.enum[0]===block.type&&s.properties.payload?.['$ref']);
 assert.ok(blockSchema,'Missing schema for '+block.type);
 const payloadSchema=specification.components.schemas[blockSchema.properties.payload['$ref'].split('/').at(-1)];
 for(const key of Object.keys(block.payload))assert.ok(key in payloadSchema.properties,'Unsupported '+block.type+'.payload.'+key);
}
const byId=new Map(body.blocks.map(b=>[b.uuid,b]));assert.equal(byId.size,body.blocks.length);
const hidden=body.blocks.find(b=>b.type==='HIDDEN_FIELDS').payload.hiddenFields;
assert.deepEqual(hidden.map(h=>h.name),contract.hiddenUrlFields);
const namesByRef=new Map(Object.entries(contract.fields).map(([name,f])=>[f.ref.uuid,name]));
function matches(c,answers){
 const name=c.payload.field.type==='HIDDEN_FIELD'?c.payload.field.title:namesByRef.get(c.payload.field.uuid);
 assert.ok(name,'Unknown condition field');
 const v=answers[name]??'';
 switch(c.payload.comparison){case 'IS':return v===c.payload.value;case 'IS_EMPTY':return v===''||Array.isArray(v)&&v.length===0;case 'IS_NOT_EMPTY':return v!==''&&(!Array.isArray(v)||v.length>0);default:assert.fail(c.payload.comparison);}
}
const logic=body.blocks.filter(b=>b.type==='CONDITIONAL_LOGIC');
function visible(answers){
 const ids=new Set(body.blocks.filter(b=>!b.payload.isHidden).map(b=>b.uuid));
 for(const {payload:p}of logic)if(p.conditionals[p.logicalOperator==='OR'?'some':'every'](c=>matches(c,answers))){
  for(const a of p.actions){
   const targets=a.type==='SHOW_BLOCKS'?a.payload.showBlocks:a.type==='HIDE_BLOCKS'?a.payload.hideBlocks:assert.fail(a.type);
   for(const target of targets){assert.ok(byId.has(target),'Unknown target');a.type==='SHOW_BLOCKS'?ids.add(target):ids.delete(target);}
  }
 }
 return ids;
}
const origin={question:['question_context','question_reference'],memo:['memo_context'],lesson:['lesson_context'],app:['app_area'],'':['app_area']};
let cases=0;
for(const [content,contextFields]of Object.entries(origin))for(let kind=0;kind<5;kind++)for(const prefillValues of [{},{title:'Énergie : E = ½CU²'},{question_id:'demo-uuid'},{title:'Énergie : E = ½CU²',question_id:'demo-uuid'}])for(const expanded of [false,true]){
 const answers={content,nature:contract.fields.nature.options[kind].id,...prefillValues,...expanded?{show_diagnostics:['yes']}:{}};
 const ids=visible(answers);
 const expected=new Set(['nature','summary','description','email','show_diagnostics',...contextFields,...kind===0?['steps','expected','frequency']:kind===1?['content_part']:[]]);
 for(const [name,f]of Object.entries(contract.fields)){
  const answerIds=f.blocks.filter(id=>byId.get(id).type!=='TITLE');
  assert.equal(answerIds.every(id=>ids.has(id)),expected.has(name),'Visibility '+JSON.stringify({content,kind,prefillValues,expanded,name}));
  if(f.prefill){
   const active=expected.has(name)&&Boolean(answers[f.prefill]);
   assert.equal(ids.has(f.prefilledHeading),active,name+' conditional prefilled title');
   assert.equal(ids.has(f.blocks[0]),expected.has(name)&&!active,name+' ordinary title');
   const answer=byId.get(answerIds[0]);
   assert.equal(answer.payload.hasDefaultAnswer,true);assert.equal(answer.payload.defaultAnswer.title,f.prefill);
  }
 }
 for(const id of contract.diagnostics.blocks)assert.equal(ids.has(id),expanded);
 cases++;
}
assert.ok(!contract.fields.name&&!contract.fields.reply&&!contract.fields.observed);
assert.equal(contract.fields.summary.label,'Objet');
assert.equal(body.settings.hasSelfEmailNotifications,true);
assert.equal(body.settings.saveForLater,false);
const groups=contract.diagnostics.groups.flatMap(g=>g.urlFields);assert.equal(groups.length,33);assert.deepEqual([...groups].sort(),[...contract.hiddenUrlFields].sort());
for(const b of body.blocks.filter(b=>b.payload.mentions)){
 for(const m of b.payload.mentions){assert.ok(hidden.some(h=>h.uuid===m.field.uuid));assert.ok(body.blocks.some(text=>text.payload.html?.includes(m.uuid)));assert.equal(m.defaultValue,'Non transmis');}
}
const files=body.blocks.find(b=>b.type==='FILE_UPLOAD').payload;assert.equal(files.maxFiles,5);assert.equal(files.maxFileSize,10);assert.equal(files.hasMultipleFiles,true);
const report={officialSchemaValid:true,blockSpecificPayloadKeysChecked:true,blocks:body.blocks.length,hiddenFields:hidden.length,visibleFields:Object.keys(contract.fields).length,conditionalRules:logic.length,visibilityCombinationsChecked:cases,all33DiagnosticsRepresented:true,realSubmissionSent:false};
fs.mkdirSync(path.join(root,'output/tally-retours-22'),{recursive:true});fs.writeFileSync(path.join(root,'output/tally-retours-22/validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
