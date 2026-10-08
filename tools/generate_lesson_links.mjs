import fs from 'node:fs';
import assert from 'node:assert/strict';
const groups={
 c01:[['radio-regulations'],['exam-rules'],['callsigns'],['bands','itu-regions']],
 c02:[['nato'],['qcodes'],['morse','morse-rhythm'],['operating-rules','reports','abbreviations']],
 c16:[['prefixes','units'],['math-basics','formulas'],['math-basics','formulas'],['ac-circuits','measurements']],
 c03:[['units','prefixes'],['dc-circuits'],['dc-circuits','formulas'],['dc-circuits','formulas']],
 c04:[['dc-circuits','formulas'],['dc-circuits','formulas'],['dc-circuits','measurements'],['dc-circuits']],
 c05:[['resistors'],['ac-circuits'],['ac-circuits'],['ac-circuits','formulas']],
 c06:[['ac-circuits','measurements'],['ac-circuits','formulas'],['ac-circuits'],['ac-circuits','formulas']],
 c07:[['filters','ac-circuits'],['filters'],['transformers-batteries'],['decibels']],
 c17:[['ac-circuits','formulas'],['ac-circuits','formulas'],['ac-circuits','math-basics'],['filters']],
 c08:[['diodes-power'],['transistors-tubes'],['amplifiers-oscillators'],['amplifiers-oscillators','binary-logic']],
 c09:[['modulations','emissions'],['modulations','emissions'],['modulations','emissions','morse'],['digital-signals','modulations','emissions']],
 c20:[['binary-logic'],['digital-signals','modulations'],['digital-signals'],['digital-signals','binary-logic']],
 c22:[['binary-logic'],['binary-logic'],['binary-logic'],['binary-logic']],
 c10:[['radio-blocks','amplifiers-oscillators'],['radio-blocks','amplifiers-oscillators'],['radio-blocks','emc-noise'],['measurements']],
 c18:[['amplifiers-oscillators','emc-noise'],['amplifiers-oscillators','radio-blocks'],['amplifiers-oscillators','emc-noise'],['decibels','radio-blocks','emc-noise']],
 c11:[['antennas','physical-constants','formulas'],['antennas'],['antennas','decibels'],['transmission-lines']],
 c19:[['transmission-lines','measurements'],['transmission-lines','formulas'],['transmission-lines','decibels'],['transmission-lines','measurements']],
 c12:[['propagation'],['propagation','bands','antennas'],['decibels','antennas','propagation'],['bands','propagation','emissions']],
 c13:[['station-safety','diodes-power'],['station-safety','antennas'],['emc-noise'],['operating-rules','callsigns']],
 c21:[['reports'],['measurements','station-safety','emc-noise'],['transmitter-limits','emc-noise','modulations'],['operating-rules','qcodes','reports']],
 c14:[['radio-regulations','bands','callsigns','transmitter-limits'],['formulas','ac-circuits','antennas','radio-blocks'],['exam-rules','math-basics'],['station-safety','antennas','bands','operating-rules']]
};
const anchors={
 nato:'R31',morse:'R32d','morse-rhythm':'R32d',qcodes:'R32a',reports:'R32b',abbreviations:'R32c',emissions:'R12',
 'exam-rules':'Intro5a','itu-regions':'R21','radio-regulations':'R11a',bands:'R21',callsigns:'R46',
 'transmitter-limits':'R22a','operating-rules':'R33a','station-safety':'R55a','math-basics':'T0001a',
 units:'T011a',prefixes:'T0002a',formulas:'T012a',decibels:'T041a','physical-constants':'T091a',
 materials:'T014b','dc-circuits':'T011a',resistors:'T015','ac-circuits':'T021a','transformers-batteries':'T031a',
 measurements:'T034a',filters:'T042a','diodes-power':'T051','transistors-tubes':'T061a',
 'amplifiers-oscillators':'T071a','logic-digital':'T081a','binary-logic':'T084','digital-signals':'T085',
 propagation:'T092a',antennas:'T094a','transmission-lines':'T101a','radio-blocks':'T111a',modulations:'T121a','emc-noise':'R54a'
};
const curriculum=JSON.parse(fs.readFileSync('data/curriculum.json','utf8'));
const references=JSON.parse(fs.readFileSync('data/reference.json','utf8'));
const refIds=new Set(references.categories.map(c=>c.id));
const html=fs.readFileSync('data/sources/f6kgl/COURS.html','latin1');
const htmlAnchors=new Set([...html.matchAll(/(?:name|id)\s*=\s*(?:["']([^"']+)["']|([^\s>]+))/gi)].map(m=>m[1]??m[2]));
const lessonReferences={};
for(const chapter of curriculum.chapters) for(const lesson of chapter.lessons) {
 const number=Number(lesson.id.split('-l')[1]);
 const list=chapter.id==='c15'? ['morse','morse-rhythm',...(number===12?['callsigns']:[])]:
   lesson.id==='c20-l01'?['binary-logic']:groups[chapter.id]?.[number-1];
 assert(list?.length,`No resources for ${lesson.id}`);
 for(const id of list) assert(refIds.has(id),`Unknown reference ${id}`);
 lessonReferences[lesson.id]=list;
}
const courseSources={};
for(const id of refIds) {
 assert(anchors[id] && htmlAnchors.has(anchors[id]),`Invalid course anchor for ${id}`);
 courseSources[id]=`http://f6kgl.free.fr/COURS.html#${anchors[id]}`;
}
const serialized=JSON.stringify({schema:1,lessonReferences,courseSources},null,2)+'\n';
for(const path of ['data/lesson-resources.json','app/src/main/assets/lesson-resources.json']) fs.writeFileSync(path,serialized);
console.log(JSON.stringify({linkedLessons:Object.keys(lessonReferences).length,referenceCourseLinks:Object.keys(courseSources).length,allAnchorsPresent:true}));
