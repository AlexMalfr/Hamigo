import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {splitDigitalCurriculum,splitDigitalReferences} from './split_logic_content.mjs';
import {authoredIdFor} from './question_identities.mjs';
const git = path => JSON.parse(execFileSync('git',['-c',`safe.directory=${process.cwd().replaceAll('\\','/')}`,'show',`8370a6e6:${path}`],{encoding:'utf8'}));
const current=JSON.parse(fs.readFileSync('data/curriculum.json','utf8'));
const packaged=JSON.parse(fs.readFileSync('app/src/main/assets/curriculum.json','utf8'));
const bank=JSON.parse(fs.readFileSync('app/src/main/assets/course-questions.json','utf8')).questions;
const byQuestion=new Map(bank.map(q=>[q.id,q]));
for(const l of packaged.chapters.flatMap(c=>c.lessons)){l.questions=l.questionIds.map(id=>byQuestion.get(id));delete l.questionIds;}
assert.deepEqual(packaged,current,'Packaged membership must resolve to the authoring bank');
const lessons=current.chapters.flatMap(c=>c.lessons);
const questions=lessons.flatMap(l=>l.questions);
const ids=new Map(lessons.map(l=>[l.id,l]));
assert.equal(ids.size,lessons.length,'No duplicate lesson identities');
assert.equal(new Set(questions.map(q=>q.id)).size,questions.length,'No duplicate question identities');
const old=git('data/curriculum.json');
for(const lesson of old.chapters.flatMap(c=>c.lessons).filter(l=>!l.id.startsWith('c15-'))) {
    for(const q of lesson.questions){q.id=authoredIdFor(q.id);q.topic ||= lesson.topic;}
    assert.deepEqual(ids.get(lesson.id),lesson,`Existing lesson or exercises changed: ${lesson.id}`);
}
assert.equal(current.chapters.find(c=>c.id==='c22').lessons[0].id,'c20-l01');
assert.equal(current.chapters.find(c=>c.id==='c20').lessons.length,3);
for(const lesson of current.chapters.find(c=>c.id==='c22').lessons) {
    assert((lesson.visuals?.length??0)<=lesson.body.length,'Every teaching diagram has an associated paragraph');
    for(const visual of lesson.visuals??[]) assert(['','logic:and','logic:or','logic:not','logic:nand','logic:nor','logic:xor'].includes(visual));
}
const added=lessons.filter(l=>l.id.startsWith('c22-')).flatMap(l=>l.questions);
for(const q of added) {
    assert(q.prompt && q.explanation && q.source);
    if(['choice','truefalse'].includes(q.kind)) {
        assert(q.answer>=0 && q.answer<q.choices.length);
        assert.equal(new Set(q.choices).size,q.choices.length);
    }
    if(q.kind==='binary') assert(q.value>=0 && q.value<2**Number(q.unit));
    if(q.kind==='number') assert(Number.isFinite(q.value) && q.tolerance===0);
    if(q.kind==='match') for(const side of ['left','right']) assert.equal(new Set(q.pairs.map(p=>p[side])).size,q.pairs.length);
}
const rebuilt=splitDigitalCurriculum(structuredClone(current));
for(const q of rebuilt.chapters.flatMap(c=>c.lessons.flatMap(l=>l.questions))) q.id=authoredIdFor(q.id);
assert.deepEqual(rebuilt,current,'Curriculum generation must be idempotent');
const references=JSON.parse(fs.readFileSync('data/reference.json','utf8'));
const rows=new Map(references.categories.flatMap(c=>c.rows).map(r=>[r.cardId,r]));
const oldRows=git('data/reference.json').categories.flatMap(c=>c.rows);
for(const row of oldRows) {
    assert(rows.has(row.cardId),`Lost reference identity: ${row.cardId}`);
    assert.equal(rows.get(row.cardId).description,row.description,`Existing review answer changed: ${row.cardId}`);
}
const fragments=JSON.parse(fs.readFileSync('data/reference-additions.json','utf8')).categories;
assert.deepEqual(splitDigitalReferences(structuredClone(fragments)),fragments,'Reference split must be idempotent');
console.log(JSON.stringify({chapters:current.chapters.length,lessons:lessons.length,questions:questions.length,preservedLessons:old.chapters.flatMap(c=>c.lessons).length,preservedQuestionIds:old.chapters.flatMap(c=>c.lessons).flatMap(l=>l.questions).length,preservedReferenceRows:oldRows.length,newExercises:added.length,idempotent:true}));
