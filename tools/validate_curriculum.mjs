import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const read=path=>JSON.parse(fs.readFileSync(path,'utf8'));
const data=read('data/curriculum.json');
const lessons=data.chapters.flatMap(c=>c.lessons);
const bank=read('data/course-questions.json');
const all=[...bank.questions,...bank.archivedQuestions];
const sourceIds=new Set(read('data/question-identities.json').entries?Object.keys(read('data/question-identities.json').entries):[]);
assert.equal(new Set(all.map(q=>q.id)).size,all.length,'Distinct active and archived UUIDs');
assert(all.every(q=>sourceIds.has(q.id)),'Every question remains registered');
assert.equal(data.sections.length,13);
assert.deepEqual(data.sections.map(s=>s.number),Array.from({length:13},(_,i)=>i+1));
assert.equal(lessons[0].id,'l-hamigo-bienvenue');
const milestone=data.sections.findIndex(s=>s.id===data.certificateMilestoneSectionId);
for(const chapter of data.chapters){
 const index=data.sections.findIndex(s=>s.id===chapter.sectionId);
 if(index>milestone)assert(chapter.lessons.every(l=>l.certificateWeight===0),'Certificate completion is possible before exploration ends');
}
const refs=new Set(read('data/reference.json').categories.map(c=>c.id));
for(const lesson of lessons)assert(lesson.referenceIds.every(id=>refs.has(id)),'Unknown memo reference '+lesson.id);
const mapping={A:'.-',B:'-...',C:'-.-.',D:'-..',E:'.',F:'..-.',G:'--.',H:'....',I:'..',J:'.---',K:'-.-',L:'.-..',M:'--',N:'-.',O:'---',P:'.--.',Q:'--.-',R:'.-.',S:'...',T:'-',U:'..-',V:'...-',W:'.--',X:'-..-',Y:'-.--',Z:'--..','0':'-----','1':'.----','2':'..---','3':'...--','4':'....-','5':'.....','6':'-....','7':'--...','8':'---..','9':'----.','?':'..--..','/':'-..-.','.':'.-.-.-',',':'--..--','=':'-...-','@':'.--.-.'};
const reverse=Object.fromEntries(Object.entries(mapping).map(([a,b])=>[b,a]));
const taught=new Set();
for(const lesson of lessons){
 const isMorse=lesson.id.startsWith('c15-')||lesson.id==='c02-l03'||lesson.id.startsWith('l-morse-');
 if(isMorse){
  for(const ch of lesson.introducedCharacters||''){
   // The source teaches an actual character and its rhythm, not only a metadata flag.
   const body=lesson.body.join(' ');
   const graphic=mapping[ch].replaceAll('.','•').replaceAll('-','━');
   assert(body.includes(ch+' : '+graphic),'Character and its actual rhythm not taught in introduction '+lesson.id+':'+ch);
   taught.add(ch);
  }
  for(const q of lesson.questions){
   if(!['morseListen','morseEncode'].includes(q.kind)&&!q.template?.type?.startsWith('morse-'))continue;
   const chars=(q.bands?.[0]||'').split(/[ /]+/).filter(Boolean).map(code=>{assert(reverse[code],'Unknown code '+q.id);return reverse[code]});
   if(q.transmittedText)assert.equal(q.bands[0].split('/').map(word=>word.trim().split(/\s+/).map(code=>reverse[code]).join('')).join(' '),q.transmittedText,'Complete listening transcript '+q.id);
   for(const ch of chars){
    assert(lesson.knownCharacters.includes(ch),'Unlisted Morse character '+lesson.id+':'+ch);
    assert(taught.has(ch),'Morse character used before its teaching '+lesson.id+':'+ch);
   }
   if(q.template?.characters)for(const ch of q.template.characters)assert(taught.has(ch),'Template may generate an untaught character '+lesson.id+':'+ch);
  }
 }
}
const editorial=read('data/question-editorial-revisions.json');
const historicalHash=q=>createHash('sha256').update(JSON.stringify(editorial.fields.map(field=>q[field]??null))).digest('hex');
const revisions=new Map(editorial.revisions.map(r=>[r.id,r]));
assert.equal(revisions.size,editorial.revisions.length,'Distinct reviewed changes');
const baseline=JSON.parse(execFileSync('git',['show',editorial.baseRevision+':data/curriculum.json'],{encoding:'utf8'}));
const oldLessons=baseline.chapters.flatMap(c=>c.lessons);
const existing=new Set(lessons.map(l=>l.id));
for(const lesson of oldLessons)assert(existing.has(lesson.id),'Lost published lesson ID '+lesson.id);
const byId=new Map(all.map(q=>[q.id,q]));
for(const q of oldLessons.flatMap(l=>l.questions)){
 const current=byId.get(q.id);assert(current,'Lost published question UUID '+q.id);
 const revision=revisions.get(q.id);
 if(revision){
  assert(revision.reason.trim(),'Missing review rationale '+q.id);
  assert.equal(historicalHash(q),revision.before,'Published baseline changed '+q.id);
  assert.equal(historicalHash(current),revision.after,'Change outside the reviewed revision '+q.id);
 }else assert.equal(historicalHash(current),historicalHash(q),'Unreviewed historical question change '+q.id);
}
const oldIds=new Set(oldLessons.flatMap(l=>l.questions.map(q=>q.id)));
for(const id of revisions.keys())assert(oldIds.has(id),'Review of an unknown historical question '+id);
const files=['data/curriculum.json','data/course-questions.json','data/question-identities.json','data/lesson-presentation.json','app/src/main/assets/lesson-presentation.json','data/lesson-narration.json','data/lesson-resources.json','data/skills.json','app/src/main/assets/curriculum.json','app/src/main/assets/course-questions.json','app/src/main/assets/lesson-resources.json','app/src/main/assets/skills.json'];
const digests=()=>files.map(p=>createHash('sha256').update(fs.readFileSync(p)).digest('hex'));
const before=digests();
execFileSync(process.execPath,['tools/expand_curriculum.mjs']);
execFileSync(process.execPath,['tools/generate_lesson_links.mjs']);
assert.deepEqual(digests(),before,'Complete generation must be byte-for-byte idempotent');
console.log(JSON.stringify({sections:data.sections.length,chapters:data.chapters.length,lessons:lessons.length,questions:bank.questions.length,archived:bank.archivedQuestions.length,preservedLessons:oldLessons.length,preservedQuestions:oldLessons.flatMap(l=>l.questions).length,morseCharactersTaught:[...taught].length,text:bank.questions.filter(q=>q.kind==='text').length,spoken:bank.questions.filter(q=>q.kind==='spoken').length,circuits:bank.questions.filter(q=>q.kind==='circuit').length,templates:bank.questions.filter(q=>q.template).length,variableDistractorPools:bank.questions.filter(q=>q.distractors?.length>3).length,idempotent:true}));
