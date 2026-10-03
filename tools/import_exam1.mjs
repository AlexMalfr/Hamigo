#!/usr/bin/env node
/** Offline normalization of the public Exam1 snapshot downloaded by import_exam1.ps1. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const rawDir = path.join(root, 'data/sources/exam1');
const outDir = path.join(root, 'app/src/main/assets/exam1');
const input = JSON.parse(fs.readFileSync(path.join(rawDir, 'questions.raw.json'), 'utf8').replace(/^\uFEFF/, ''));
const downloadFile = path.join(rawDir, 'download.json');
const download = fs.existsSync(downloadFile) ? JSON.parse(fs.readFileSync(downloadFile, 'utf8').replace(/^\uFEFF/, '')) : { downloadedOn: '2026-10-03' };
const themeMap = new Map(input.themes.map(theme => [theme.num, theme.nom]));
const imageDir = path.join(rawDir, 'images');
const outImages = path.join(outDir, 'images');
fs.mkdirSync(outImages, { recursive: true });

const sectionOf = num => num >= 200 && num < 300 ? 'technique' : 'regulation';
const writeJson = (name, data) => fs.writeFileSync(path.join(outDir, name), JSON.stringify(data, null, 2) + '\n', 'utf8');
const sha256 = filename => crypto.createHash('sha256').update(fs.readFileSync(filename)).digest('hex');
const ids = new Set();
const missingImages = [];
const references = [];
const questions = input.questions.map(q => {
  const id = String(q.num);
  if (!/^\d+$/.test(id) || ids.has(id)) throw new Error(`Invalid or duplicate question id: ${id}`);
  ids.add(id);
  if (!themeMap.has(q.themeNum)) throw new Error(`Unknown theme: ${q.themeNum}`);
  if (typeof q.question !== 'string' || !q.question.trim()) throw new Error(`Empty prompt: ${id}`);
  if (!Array.isArray(q.propositions) || q.propositions.length < 2 || q.propositions.some(x => typeof x !== 'string')) throw new Error(`Invalid choices: ${id}`);
  if (!Number.isInteger(q.reponse) || q.reponse < 0 || q.reponse >= q.propositions.length) throw new Error(`Invalid zero-based answer: ${id}`);
  const imageFile = path.join(imageDir, `${id}.png`);
  const hasImage = fs.existsSync(imageFile);
  if (hasImage) {
    const magic = fs.readFileSync(imageFile).subarray(0, 8).toString('hex');
    if (magic !== '89504e470d0a1a0a') throw new Error(`Image is not PNG: ${id}`);
    fs.copyFileSync(imageFile, path.join(outImages, `${id}.png`));
  } else missingImages.push(id);
  const reviewFlags = [];
  if (/hors programme/i.test(q.commentaire || '')) reviewFlags.push('author_notes_out_of_program');
  if (/suppression de cette disposition|aurait.*dû être retirée|ne devrait plus y avoir|résolution a été abrogée/i.test(q.commentaire || '')) reviewFlags.push('author_notes_historical_or_repealed');
  if (id === '23814') reviewFlags.push('source_prompt_possible_transcription_error');
  references.push({ id, course: q.cours ?? null, sourceTopicId: q.themeNum, sourceTopic: themeMap.get(q.themeNum), reviewFlags });
  return {
    id,
    section: sectionOf(q.themeNum),
    topic: themeMap.get(q.themeNum).trim(),
    prompt: q.question,
    choices: q.propositions,
    answer: q.reponse,
    explanation: q.commentaire ?? '',
    image: hasImage ? `exam1/images/${id}.png` : null,
    source: `https://exam1.r-e-f.org/questions-listing?questionId=${id}`,
  };
});
if (questions.length !== input.nbQuestions) throw new Error('Declared question count differs from actual count');
const topics = input.themes.map(t => ({ id: String(t.num), section: sectionOf(t.num), topic: t.nom.trim(), count: questions.filter(q => q.topic === t.nom.trim()).length }));
const extraImages = fs.readdirSync(imageDir).filter(x => x.endsWith('.png') && !ids.has(path.basename(x, '.png'))).sort();
const audit = {
  questionCount: questions.length,
  imageCount: questions.filter(q => q.image).length,
  missingImages,
  extraSourceImages: extraImages,
  emptyExplanationCount: questions.filter(q => !q.explanation.trim()).length,
  flaggedQuestions: references.filter(r => r.reviewFlags.length),
};
const manifest = {
  source: 'https://exam1.r-e-f.org/',
  downloadedOn: download.downloadedOn,
  upstreamVersion: input.version,
  questionCount: questions.length,
  sections: { regulation: questions.filter(q => q.section === 'regulation').length, technique: questions.filter(q => q.section === 'technique').length },
  themeCount: topics.length,
  importedImageCount: audit.imageCount,
  sourceImageCount: audit.imageCount + extraImages.length,
  normalizedSchemaVersion: 1,
  answerIndexBase: 0,
  rawQuestionSha256: sha256(path.join(rawDir, 'questions.raw.json')),
  sourceZipSha256: sha256(path.join(rawDir, 'questions.zip')),
  attribution: ['Jean-Luc Fortin F6GPX et contributeurs : banque de questions et corrigés', 'René F5AXG : logiciel Exam1 historique', 'Valentin Saugnier F4HVV : version web', 'Réseau des Émetteurs Français : hébergement', 'F6KGL/F5KFF Radio-Club de la Haute Île : maintenance et ressources'],
  licenseNote: 'Le dépôt Android Exam1RA porte GPL-3.0 ; aucune licence explicite distincte n’a été trouvée pour le JSON/ZIP de la banque téléchargée. Attribution et originaux conservés pour cet usage personnel privé.',
};
writeJson('questions.json', questions);
writeJson('topics.json', topics);
writeJson('references.json', references);
writeJson('audit.json', audit);
writeJson('manifest.json', manifest);
writeJson('excluded.json', references.filter(r => r.reviewFlags.includes('author_notes_historical_or_repealed') || r.reviewFlags.includes('source_prompt_possible_transcription_error')).map(r => r.id));
for (const [src, dest] of [['series.raw.json', 'series.json'], ['contributeurs.raw.json', 'contributors.json']]) {
  fs.copyFileSync(path.join(rawDir, src), path.join(outDir, dest));
}
console.log(JSON.stringify({ ...manifest, audit: { missingImages: missingImages.length, extraImages: extraImages.length, flaggedQuestions: audit.flaggedQuestions.length, emptyExplanations: audit.emptyExplanationCount } }, null, 2));
