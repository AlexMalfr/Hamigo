import fs from 'node:fs';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';

const root=process.cwd().replaceAll('\\','/');
// The baseline supplies the pre-existing SM-2 identities, regardless of row ordering.
const previous=JSON.parse(execFileSync('git',['-c',`safe.directory=${root}`,'show','fa933d65:data/reference.json'],{encoding:'utf8'}));
const categories=new Map(previous.categories.map(c=>[c.id,c]));
const previousIds=new Map(previous.categories.flatMap(c=>c.rows.map((row,i)=>[`${c.id}|${row.term}`,`flash-${c.id}-${i}`])));
const oldResistors=new Map(previous.categories.find(c=>c.id==='resistors').rows.map((r,i)=>[r.term.toLocaleLowerCase('fr'),`flash-resistors-${i}`]));
for(const name of ['radio','technical','additions']) {
    const fragment=JSON.parse(fs.readFileSync(`data/reference-${name}.json`,'utf8'));
    for(const c of fragment.categories) categories.set(c.id,c);
}
categories.delete('satellite'); // Integrated in the regional amateur-band reference.
const families=['Communiquer','Réglementation et station','Bases et calculs','Électricité et composants','Électronique','Radio et antennes'];
for(const c of categories.values()) {
    if(c.group==='Réglementation') c.group='Réglementation et station';
    if(c.group==='Électricité') c.group='Électricité et composants';
    if(c.id==='math-basics') c.group='Bases et calculs';
    if(!families.includes(c.group)) throw new Error(`Unknown family ${c.group}`);
}
const result=[...categories.values()].sort((a,b)=>families.indexOf(a.group)-families.indexOf(b.group)||(a.order??999)-(b.order??999));
const ids=new Set();
for(const c of result) {
    if(!c.group || !c.source || !c.rows.length) throw new Error(`Incomplete category ${c.id}`);
    const terms=new Set();
    for(const row of c.rows) {
        if(!row.term || !row.description || terms.has(row.term)) throw new Error(`Missing/duplicate row ${c.id}/${row.term}`);
        terms.add(row.term);
        row.kind??='fact';
        row.extra??='';
        row.cardId??=previousIds.get(`${c.id}|${row.term}`)??(c.id==='resistors'?oldResistors.get(row.term.toLocaleLowerCase('fr')):undefined)??`flash-${c.id}-${crypto.createHash('sha256').update(row.term).digest('hex').slice(0,12)}`;
        const oldExample=row.kind==='example' && /^flash-.+-\d{1,3}$/.test(row.cardId);
        if(c.flashcards!==false && (oldExample || !['example','tip'].includes(row.kind)) && !['2','3'].includes(row.region)) {
            if(ids.has(row.cardId)) throw new Error(`Duplicate flashcard ${row.cardId}`);
            ids.add(row.cardId);
        }
    }
}
const output={schemaVersion:2,language:'fr-FR',verifiedAt:'2026-10-05',sourceEdition:'F6KGL/F5KFF novembre 2025, complété par les textes officiels',categories:result};
const serialized=JSON.stringify(output,null,2)+'\n';
fs.writeFileSync('data/reference.json',serialized);
fs.writeFileSync('app/src/main/assets/reference.json',serialized);
console.log(JSON.stringify({categories:result.length,rows:result.reduce((n,c)=>n+c.rows.length,0),flashcards:ids.size,bytes:Buffer.byteLength(serialized)}));
