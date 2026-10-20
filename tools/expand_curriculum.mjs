// Project the complete editorial source into Android assets. Never rebuild lessons by their position.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {persistCourseQuestions} from './question_identities.mjs';
const data=JSON.parse(fs.readFileSync('data/curriculum.json','utf8'));
const skills=JSON.parse(fs.readFileSync('data/skills.json','utf8'));
const skillIds=new Set(skills.skills.map(s=>s.id));
const sections=new Set(data.sections.map(s=>s.id));
assert.equal(sections.size,data.sections.length,'Duplicate section');
const lessons=data.chapters.flatMap(c=>c.lessons);
assert.equal(new Set(lessons.map(l=>l.id)).size,lessons.length,'Duplicate lesson');
const authored=data.chapters.flatMap(c=>c.lessons.flatMap(l=>l.questions));
for(const chapter of data.chapters){
 assert(sections.has(chapter.sectionId),'Unknown section '+chapter.id);
 assert(chapter.lessons.length>0,'Empty chapter '+chapter.id);
 for(const lesson of chapter.lessons){
  assert(lesson.body.length>=2&&lesson.body.every(p=>typeof p==='string'&&p.trim()),'Missing introduction '+lesson.id);
  assert(lesson.questions.length>=3,'Short lesson '+lesson.id);
  for(const id of [...lesson.skillIds,...lesson.prerequisiteSkillIds])assert(skillIds.has(id),'Unknown skill '+id);
  assert(Number.isInteger(lesson.certificateWeight)&&lesson.certificateWeight>=0,'Invalid weight');
  assert(Number.isInteger(lesson.explorationWeight)&&lesson.explorationWeight>=0,'Invalid weight');
  for(const link of lesson.links||[])assert(link.label?.trim()&&/^https:\/\//.test(link.url),'Invalid lesson link '+lesson.id);
  for(const question of lesson.questions){
   assert(['choice','truefalse','match','order','sort','number','frequency','estimate','binary','multiselect','morseEncode','morseListen','cloze','resistor','waveform','flash','text','spoken','circuit','rows'].includes(question.kind),'Unknown question interface '+question.id);
   assert(question.prompt&&question.explanation,'Missing content '+question.id);
   assert(question.skillIds.length&&question.skillIds.every(id=>skillIds.has(id)),'Question skills '+question.id);
   if(['choice','cloze','truefalse','resistor','morseListen','waveform'].includes(question.kind)){
    assert(Number.isInteger(question.answer)&&question.answer>=0&&question.answer<question.choices.length,'Answer index '+question.id);
    assert.equal(new Set(question.choices).size,question.choices.length,'Duplicate choices '+question.id);
   }
   if(['text','spoken'].includes(question.kind))assert(question.acceptedAnswers?.length&&question.acceptedAnswers.every(s=>typeof s==='string'&&s.trim()),'Accepted answers '+question.id);
   if(question.kind==='rows'){
    assert(question.rows?.length>=2&&question.rows.length<=6,'Answer rows '+question.id);
    assert.equal(new Set(question.rows.map(r=>r.id)).size,question.rows.length,'Distinct row IDs '+question.id);
    for(const row of question.rows){
     assert(row.id?.trim()&&row.prompt?.trim(),'Row content '+question.id);
     if(row.choices?.length){
      assert(row.choices.length>=3&&new Set(row.choices).size===row.choices.length,'Distinct plausible row choices '+question.id);
      assert(Number.isInteger(row.answer)&&row.answer>=0&&row.answer<row.choices.length,'Row answer '+question.id);
     }else assert(row.acceptedAnswers?.length&&row.acceptedAnswers.every(s=>typeof s==='string'&&s.trim()),'Row accepted answers '+question.id);
    }
   }
   if(['number','binary','estimate','frequency'].includes(question.kind))assert(Number.isFinite(question.value),'Value '+question.id);
   if(question.kind==='multiselect')assert(question.bands?.length&&question.bands.every(n=>question.choices[Number(n)]),'Multi indices '+question.id);
   if(['morseListen','morseEncode'].includes(question.kind)||question.template?.type?.startsWith('morse-'))assert(/^[.\- /]+$/.test(question.bands?.[0]||''),'Morse '+question.id);
   if(question.kind==='waveform')assert.equal(question.bands.length,question.choices.length,'Waveform '+question.id);
   if(['match','circuit'].includes(question.kind)){
    assert.equal(new Set(question.pairs.map(p=>p.left)).size,question.pairs.length,'Ambiguous slot '+question.id);
    assert.equal(new Set(question.pairs.map(p=>p.right)).size,question.pairs.length,'Ambiguous component '+question.id);
   }
   if(question.kind==='circuit')assert(question.pairs.every(p=>question.choices.includes(p.right)),'Missing circuit component '+question.id);
  }
 }
}
// Presentation is editorial metadata, separate from spoken/plain source. Every active introduction is covered.
const presentation=JSON.parse(fs.readFileSync('data/lesson-presentation.json','utf8'));
assert.deepEqual(Object.keys(presentation).sort(),lessons.map(l=>l.id).sort(),'Introduction presentation coverage');
for(const lesson of lessons){
 const entry=presentation[lesson.id];
 assert.equal(entry.bodyHash,createHash('sha256').update(JSON.stringify(lesson.body)).digest('hex'),'Review presentation after editing '+lesson.id);
 assert(Object.keys(entry.paragraphs).length,'Empty presentation '+lesson.id);
 for(const [index,style] of Object.entries(entry.paragraphs)){
  const source=lesson.body[Number(index)];
  assert(source&&String(Number(index))===index,'Invalid paragraph '+lesson.id+':'+index);
  assert(Object.keys(style).every(k=>['heading','emphasis','tip','breakBefore','references'].includes(k)),'Unknown presentation property');
  if(style.heading)assert(style.heading.length<=64,'Long subheading '+lesson.id);
  for(const phrase of [...(style.emphasis||[]),...(style.breakBefore||[]),...(style.tip?[style.tip]:[]),...(style.references||[]).map(r=>r.text)])
   assert(phrase.trim()&&source.includes(phrase),'Stale presentation phrase '+lesson.id+':'+index+':'+phrase);
  if(style.tip)assert(style.tip.startsWith('Astuce : '),'Tip wording '+lesson.id);
  for(const ref of style.references||[])assert(ref.lessonId!==lesson.id&&lessons.some(l=>l.id===ref.lessonId),'Invalid course preview '+lesson.id);
 }
}
fs.writeFileSync('app/src/main/assets/lesson-presentation.json',JSON.stringify(presentation,null,2)+'\n');
// Spoken overrides keep their exact source text; stale editorial adaptations must be repaired.
const spoken=JSON.parse(fs.readFileSync('data/lesson-narration.json','utf8'));
for(const id of Object.keys(spoken))assert(lessons.some(l=>l.id===id),'Unknown narrated lesson '+id);
for(const lesson of lessons){
 const overrides=spoken[lesson.id]||{};
 for(const entry of overrides.body||[])assert.equal(lesson.body[entry.index],entry.source,'Stale spoken paragraph '+lesson.id+':'+entry.index);
 if(overrides.formula)assert.equal(overrides.formula.source,lesson.formula,'Stale spoken formula '+lesson.id);
 lesson.narration=[lesson.title,...lesson.body.map((text,i)=>overrides.body?.find(e=>e.index===i)?.speech||text),overrides.formula?.speech||lesson.formula||''];
}
persistCourseQuestions(data);
fs.writeFileSync('app/src/main/assets/skills.json',JSON.stringify(skills,null,2)+'\n');
console.log(JSON.stringify({sections:data.sections.length,chapters:data.chapters.length,lessons:lessons.length,questions:authored.length,archivedQuestions:data.archivedQuestions?.length||0,skills:skills.skills.length}));
