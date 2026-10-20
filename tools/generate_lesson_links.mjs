// Resource relationships are editorial metadata keyed by stable lesson ID, never by position.
import fs from 'node:fs';
import assert from 'node:assert/strict';
const curriculum=JSON.parse(fs.readFileSync('data/curriculum.json','utf8'));
const references=JSON.parse(fs.readFileSync('data/reference.json','utf8'));
const previous=JSON.parse(fs.readFileSync('data/lesson-resources.json','utf8'));
const referenceIds=new Set(references.categories.map(c=>c.id));
const lessonReferences={};
for(const lesson of curriculum.chapters.flatMap(c=>c.lessons)){
 const ids=lesson.referenceIds||previous.lessonReferences[lesson.id]||[];
 for(const id of ids)assert(referenceIds.has(id),'Unknown reference '+id+' in '+lesson.id);
 lessonReferences[lesson.id]=ids;
}
const serialized=JSON.stringify({schema:1,lessonReferences,courseSources:previous.courseSources},null,2)+'\n';
for(const path of ['data/lesson-resources.json','app/src/main/assets/lesson-resources.json'])fs.writeFileSync(path,serialized);
console.log(JSON.stringify({linkedLessons:Object.keys(lessonReferences).length,resourceIds:referenceIds.size}));
