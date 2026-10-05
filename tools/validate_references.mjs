import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';

const content=fs.readFileSync('data/reference.json','utf8');
assert.equal(content,fs.readFileSync('app/src/main/assets/reference.json','utf8'),'Packaged references must match the source');
const data=JSON.parse(content);
const baseline=JSON.parse(execFileSync('git',['-c',`safe.directory=${process.cwd().replaceAll('\\','/')}`,'show','fa933d65:data/reference.json'],{encoding:'utf8'}));
const isCard=r=>(!['example','tip'].includes(r.kind)||r.kind==='example'&&/^flash-.+-\d{1,3}$/.test(r.cardId))&&!['2','3'].includes(r.region);
const cards=data.categories.flatMap(c=>c.flashcards===false?[]:c.rows.filter(isCard));
const ids=new Set(cards.map(r=>r.cardId));
assert.equal(ids.size,cards.length,'Every flashcard needs a unique stable identity');
const oldCards=baseline.categories.flatMap(c=>c.rows.map((r,i)=>`flash-${c.id}-${i}`));
for(const id of oldCards)assert(ids.has(id),`Previously reviewed flashcard disappeared: ${id}`);

const html=fs.readFileSync('data/sources/f6kgl/COURS.html','latin1');
const anchors=new Set([...html.matchAll(/(?:name|id)\s*=\s*(?:["']([^"']+)["']|([^\s>]+))/gi)].map(m=>m[1]??m[2]));
const sources=[...new Set(data.categories.flatMap(c=>[c.source,...c.rows.map(r=>r.source)]).filter(Boolean))];
for(const source of sources.filter(s=>s.startsWith('http://f6kgl.free.fr/COURS.html#')))assert(anchors.has(source.split('#')[1]),`Broken course anchor: ${source}`);

const kotlin=name=>fs.readFileSync(`app/src/main/java/com/malfreyt/alexandre/hamigo/${name}.kt`,'utf8');
const technical=kotlin('TechnicalReferenceContent'),radio=kotlin('RadioReferenceContent'),extra=kotlin('MemoExtraDiagrams');
for(const row of data.categories.flatMap(c=>c.rows).filter(r=>r.visual)) {
  const key=row.visual.replace(/^extra:/,'');
  const implementation=row.visual.startsWith('extra:')?extra:technical+radio;
  assert(implementation.includes(`"${key}"`),`Missing native visual: ${row.visual}`);
}
assert.equal(data.categories.find(c=>c.id==='materials').rows.length,20,'Keep every material in the course table');
const bands=data.categories.find(c=>c.id==='bands');
for(const region of ['1','2','3'])assert.equal(bands.rows.filter(r=>r.region===region&&r.kind!=='flashcard-only').length,27,'Keep all bands in every region');
console.log(JSON.stringify({categories:data.categories.length,rows:data.categories.reduce((n,c)=>n+c.rows.length,0),flashcards:cards.length,preservedFlashcards:oldCards.length,courseAnchorsChecked:sources.filter(s=>s.startsWith('http://f6kgl.free.fr/COURS.html#')).length,packagedAssetIdentical:true}));
