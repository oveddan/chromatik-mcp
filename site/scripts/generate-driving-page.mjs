// Regenerates the generated portion of src/content/docs/driving.md from
// agent-plugin/skills/driving-chromatik/SKILL.md — so the docs-site "driving well" page
// can never drift from the actual skill an agent loads (see agent-plugin/README.md:
// SKILL.md is the source, this page is the generated mirror).
//
// Hand-written prose in driving.md — its Starlight frontmatter and any page-only framing
// (e.g. "who this page is for") — is untouched; only the content between
// `<!-- generated:start:driving -->` / `<!-- generated:end -->` markers is replaced with
// SKILL.md's body (YAML frontmatter stripped). Pattern copied from
// generate-tool-reference.mjs: marker-delimited replacement, fail loudly on mismatch.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));

const skillPath = here('../../agent-plugin/skills/driving-chromatik/SKILL.md');
const docPath = here('../src/content/docs/driving.md');

const START_MARKER = '<!-- generated:start:driving -->';
const END_MARKER = '<!-- generated:end -->';

function stripFrontmatter(markdown, sourcePath) {
  if (!markdown.startsWith('---\n')) {
    console.error(`generate-driving-page.mjs: ${sourcePath} has no YAML frontmatter to strip`);
    process.exit(1);
  }
  const closeIdx = markdown.indexOf('\n---\n', 4);
  if (closeIdx === -1) {
    console.error(`generate-driving-page.mjs: ${sourcePath}'s frontmatter is never closed with "---"`);
    process.exit(1);
  }
  return markdown.slice(closeIdx + '\n---\n'.length).replace(/^\n+/, '');
}

const skill = await readFile(skillPath, 'utf8');
const body = stripFrontmatter(skill, skillPath);
const generated = body.trimEnd() + '\n';

const doc = await readFile(docPath, 'utf8');

const startIdx = doc.indexOf(START_MARKER);
if (startIdx === -1) {
  console.error(`generate-driving-page.mjs: missing marker ${START_MARKER} in driving.md`);
  process.exit(1);
}
const contentStart = startIdx + START_MARKER.length;
const endIdx = doc.indexOf(END_MARKER, contentStart);
if (endIdx === -1) {
  console.error(`generate-driving-page.mjs: missing ${END_MARKER} after ${START_MARKER} in driving.md`);
  process.exit(1);
}

const nextDoc = doc.slice(0, contentStart) + '\n\n' + generated + '\n' + doc.slice(endIdx);

if (process.argv.includes('--check')) {
  if (nextDoc !== doc) {
    console.error(
      'generate-driving-page.mjs: src/content/docs/driving.md is stale relative to ' +
        'agent-plugin/skills/driving-chromatik/SKILL.md — run `npm run driving-ref`.',
    );
    process.exit(1);
  }
  console.log('driving.md is up to date with SKILL.md');
  process.exit(0);
}

await writeFile(docPath, nextDoc);
console.log('wrote SKILL.md body into src/content/docs/driving.md');
