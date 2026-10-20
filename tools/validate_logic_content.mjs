import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {splitDigitalReferences} from './split_logic_content.mjs';
const git=path=>JSON.parse(execFileSync('git',['show',`HEAD:${path}`],{encoding:'utf8'}));
const current=JSON.parse(fs.readFileSync('data/curriculum.json','utf8'));
const packaged=JSON.parse(fs.readFileSync('app/src/main/assets/curriculum.json','utf8'));
const bank=JSON.parse(fs.readFileSync('app/src/main/assets/course-questions.json','utf8'));
const byQuestion=new Map(bank.questions.map(q=>[q.id,q]));
for(const lesson of packaged.chapters.flatMap(c=>c.lessons)){
 lesson.questions=lesson.questionIds.map(id=>{assert(byQuestion.has(id),'Missing membership '+id);return byQuestion.get(id)});
 delete lesson.questionIds;
}
// Archives are deliberately separate from active lesson membership.
packaged.archivedQuestions=bank.archivedQuestions;
assert.deepEqual(packaged,current,'Packaged membership must resolve to the complete editorial bank');
const lessons=current.chapters.flatMap(c=>c.lessons);
assert.equal(current.chapters.find(c=>c.id==='c22').lessons[0].id,'c20-l01','Stable binary lesson moved without renumbering');
for(const lesson of lessons.filter(l=>l.id.startsWith('c22-'))){
 assert((lesson.visuals?.length||0)<=lesson.body.length,'Every teaching diagram has a paragraph');
 for(const visual of lesson.visuals||[])assert(['','logic:and','logic:or','logic:not','logic:nand','logic:nor','logic:xor'].includes(visual));
 for(const question of lesson.questions){
  assert(question.source&&question.explanation);
  if(question.kind==='binary')assert(question.value>=0&&question.value<2**Number(question.unit));
  if(question.kind==='number')assert(Number.isFinite(question.value)&&question.tolerance===0);
 }
}
const references=JSON.parse(fs.readFileSync('data/reference.json','utf8'));
const rows=new Map(references.categories.flatMap(c=>c.rows).map(r=>[r.cardId,r]));
const oldRows=git('data/reference.json').categories.flatMap(c=>c.rows);
for(const row of oldRows){
 assert(rows.has(row.cardId),'Lost reference identity '+row.cardId);
 assert.equal(rows.get(row.cardId).description,row.description,'Existing review answer changed '+row.cardId);
}
const fragments=JSON.parse(fs.readFileSync('data/reference-additions.json','utf8')).categories;
assert.deepEqual(splitDigitalReferences(structuredClone(fragments)),fragments,'Reference split remains idempotent');
console.log(JSON.stringify({lessons:lessons.length,activeQuestions:bank.questions.length,archivedQuestions:bank.archivedQuestions.length,preservedReferenceRows:oldRows.length,membershipResolved:true}));
