// Drift gate: every chromatik MCP tool name referenced anywhere under agent-plugin/**
// (the SKILL.md prose, its references/, the bundled manifests) must actually exist in
// src/data/tools.json — the authoritative, source-generated tool catalog. This is what
// stops a tool rename in the Java server from silently invalidating the plugin's docs:
// a rename shows up here as a failure instead of a stale skill nobody notices.
//
// "Tool-name-shaped" is defined relative to tools.json itself, not a hardcoded verb
// list: a token qualifies if its first snake_case segment matches the first segment of
// some real tool name (get_, list_, add_, set_, wire_, ...). That keeps this checker
// from going stale if the naming convention ever grows a new verb. A `foo_bar_*`
// wildcard (shorthand for "the list_available_ family", used in prose) is checked as a
// prefix instead of an exact name.
import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));
const pluginDir = here('../../agent-plugin');

async function listFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await listFiles(full)));
    } else if (entry.isFile()) {
      files.push(full);
    }
  }
  return files;
}

const catalog = JSON.parse(await readFile(here('../src/data/tools.json'), 'utf8'));
const toolNames = new Set(catalog.map((t) => t.name));
const verbs = new Set(catalog.map((t) => t.name.split('_')[0]));

// A snake_case token (>=1 underscore), optionally followed by a literal "_*" wildcard
// suffix used in prose to mean "the whole <prefix>_ family".
const TOKEN = /\b([a-z][a-z0-9]*(?:_[a-z0-9]+)+)(_\*)?/g;

const files = await listFiles(pluginDir);
const violations = [];

for (const file of files) {
  const text = await readFile(file, 'utf8');
  for (const match of text.matchAll(TOKEN)) {
    const [, token, wildcard] = match;
    const verb = token.split('_')[0];
    if (!verbs.has(verb)) {
      continue; // not tool-shaped by this catalog's own naming convention — e.g. an
      // argument name (include_image) or error code (invalid_argument, not_found).
    }
    const ok = wildcard ? [...toolNames].some((name) => name.startsWith(`${token}_`)) : toolNames.has(token);
    if (!ok) {
      violations.push({ file: path.relative(here('../..'), file), token: token + (wildcard ?? '') });
    }
  }
}

if (violations.length) {
  console.error('check-plugin-tool-names.mjs: tool names referenced in agent-plugin/ that do not exist in tools.json:');
  for (const { file, token } of violations) {
    console.error(`  ${file}: ${token}`);
  }
  console.error('Update the reference (or regenerate tools.json if the tool was renamed) before merging.');
  process.exit(1);
}

console.log(`check-plugin-tool-names.mjs: all tool-shaped references in agent-plugin/ (${files.length} files) match tools.json`);
