import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const review=read('data/practice-review-55.json'),data=read('data/curriculum.json');
const lessons=data.chapters.flatMap(c=>c.lessons),byLesson=new Map(lessons.map(l=>[l.id,l]));
const membership=new Map(lessons.flatMap(l=>l.questions.map(q=>[q.id,l.id])));
const allIds=new Set([...membership.keys(),...(data.archivedQuestions||[]).map(q=>q.id)]);
const published=JSON.parse(execFileSync('git',['show',review.sourceBaseline+':data/curriculum.json'],{encoding:'utf8'}));
const publishedIds=new Set(published.chapters.flatMap(c=>c.lessons.map(l=>l.id)));
for(const l of review.candidateBaseline){
 assert(byLesson.has(l.lessonId),'Lost candidate lesson '+l.lessonId);
 for(const id of l.questionIds)assert(allIds.has(id),'Lost candidate question '+id);
}
for(const a of review.additions)for(const id of a.questionIds)assert.equal(membership.get(id),a.lessonId,'Reviewed addition membership '+id);
for(const r of review.rejected)assert(!allIds.has(r.questionId),'An intentionally rejected draft was added '+r.questionId);
for(const m of review.moves)assert.equal(membership.get(m.questionId),m.to,'Reviewed move membership '+m.questionId);
for(const split of review.morseSplits){
 const first=byLesson.get(split.originalLessonId),second=byLesson.get(split.newLessonId);
 assert.equal(first.questions.length,split.recognition);
 assert.equal(second.questions.length,split.production);
 assert.equal(first.questions.length+second.questions.length,split.before);
 assert.deepEqual(new Set(second.questions.map(q=>q.id)),new Set(split.preservedQuestionIds));
 assert.equal(second.introducedCharacters,'','Production recalls previously introduced characters');
 const start=lessons.findIndex(l=>l.id===first.id),end=lessons.findIndex(l=>l.id===second.id);
 assert(end>start,'Recognition before production');
 assert(lessons.slice(start+1,end).some(l=>!l.id.startsWith('c15-')&&!l.id.startsWith('l-morse-')&&l.id!=='c02-l03'),'Interleave a different topic before production');
}
const position=id=>lessons.findIndex(l=>l.id===id);
assert(position('l-decibels-reperes')<position('l-filtre-coupure'),'Teach dB before filter cutoffs');
assert(position('l-logique-commandes')-position('c22-l04')>=5,'The logical recall follows other concepts');
assert.equal(byLesson.get('c08-l04').questions.some(q=>q.skillIds.includes('logic-basic')),false,'Oscillator course excludes premature logic');
for(const id of ['q-93d9ddfc-c631-4629-9b22-0fe632bb6dec','q-b9bffd6d-3e3a-439f-8b81-5bfddf36e349','q-38a30fbf-9474-4cb0-a902-d85933b05e22']){
 assert.equal(membership.get(id),'l-energie-duree');
 assert(byLesson.get('l-energie-duree').questions.find(q=>q.id===id).skillIds.includes('energy-duration'));
}
const cohort=review.candidateBaseline.filter(l=>!publishedIds.has(l.lessonId));
const density=rows=>({lessons:rows.length,fourQuestions:rows.filter(l=>l.questionIds.length===4).length,
 average:Number((rows.reduce((n,l)=>n+l.questionIds.length,0)/rows.length).toFixed(2)),
 min:Math.min(...rows.map(l=>l.questionIds.length)),max:Math.max(...rows.map(l=>l.questionIds.length))});
const updated=cohort.map(l=>({lessonId:l.lessonId,questionIds:byLesson.get(l.lessonId).questions.map(q=>q.id)}));
const baselineQuestionIds=new Set(review.candidateBaseline.flatMap(l=>l.questionIds));
const actualNew=lessons.flatMap(l=>l.questions).filter(q=>!baselineQuestionIds.has(q.id));
const finishing=read('data/finishing-review-55.json');
const finishingIds=finishing.result.newQuestionIds;
assert.equal(actualNew.length,review.stats.addedQuestions+finishingIds.length);
assert.deepEqual(new Set([...review.additions.flatMap(a=>a.questionIds),...finishingIds]),new Set(actualNew.map(q=>q.id)),'Every authored addition has an explicit pedagogical rationale');
for(const id of finishingIds)assert(finishing.additions.some(q=>q.questionId===id&&q.reason?.trim()),'Finishing rationale '+id);
const skillLocations=new Map();
lessons.forEach((l,index)=>l.questions.forEach(q=>q.skillIds.forEach(skill=>{
 if(!skillLocations.has(skill))skillLocations.set(skill,[]);skillLocations.get(skill).push(index);
})));
const nonMorseReturns=[];
lessons.forEach((l,index)=>{
 if(l.id.startsWith('c15-')||l.id.startsWith('l-morse-')||l.id==='c02-l03')return;
 for(const q of l.questions.filter(q=>!baselineQuestionIds.has(q.id)))for(const skill of q.skillIds){
  const first=Math.min(...skillLocations.get(skill));
  if(index-first>=10)nonMorseReturns.push({lessonId:l.id,questionId:q.id,skillId:skill,interveningLessons:index-first-1});
 }
});
console.log(JSON.stringify({candidateIdsPreserved:true,newAuthoredQuestions:actualNew.length,originalNewCohortBefore:density(cohort),sameCohortAfter:density(updated),
 splitMorseLessons:review.morseSplits.length,maxMorseAuthoredQuestions:Math.max(...lessons.filter(l=>l.id.startsWith('c15-')).map(l=>l.questions.length)),
 logicalRecallInterval:position('l-logique-commandes')-position('c22-l04')-1,nonMorseTaggedReturns:nonMorseReturns.length,returnSkills:[...new Set(nonMorseReturns.map(r=>r.skillId))],
 lessons:lessons.length,chapters:data.chapters.length,questions:membership.size}));
