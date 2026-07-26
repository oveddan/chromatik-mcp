// Drift gate: every chromatik MCP tool name referenced anywhere under agent-plugin/**
// (the SKILL.md prose, its references/, the bundled manifests) must actually exist in
// src/data/tools.json — the authoritative, source-generated tool catalog. This is what
// stops a tool rename in the Java server from silently invalidating the plugin's docs:
// a rename shows up here as a failure instead of a stale skill nobody notices.
//
// Inverted on purpose (see #150): earlier versions of this script *inferred* which
// tokens were tool references (by matching a known verb prefix) and skipped anything
// its inference missed — which let an entire renamed verb family, or an mcp__-prefixed
// name, go unvalidated. Instead, this flags any tool-shaped snake_case token that is
// NOT in tools.json, with a small explicit ignore list for the few non-tool snake_case
// tokens that legitimately appear in the plugin's prose. A drift gate should err toward
// false positives (an ignore-list edit) rather than false negatives (silent exemption).
//
// Scoped on purpose (see #152 review): the plugin's prose is full of ordinary
// snake_case English and JSON (parameter names, error-payload examples, frontmatter
// keys like `allowed_tools`/`max_turns`) that is not a tool reference at all. Scanning
// every snake_case token anywhere in the file makes the three-entry IGNORE list an
// allowlist against an unbounded space, which only grows. Instead this only validates
// tokens inside inline backticks (house style backticks tool names) and tokens on an
// agent's frontmatter `tools:` line (the one place an untick-wrapped MCP-prefixed name
// legitimately appears). Everything else — bare prose, fenced code blocks, other
// frontmatter keys/values — is skipped. Trade-off: a tool name mentioned in bare prose
// without backticks is no longer validated. That's a chosen false negative; the
// alternative is the false-positive flood above.
import { readFile, readdir, realpath, stat } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));
const pluginDir = here('../../agent-plugin');

// Non-tool snake_case tokens that legitimately appear backticked, or on a `tools:`
// line, in the plugin's prose today. Keep this list small and evidence-based — add an
// entry only once this script has actually flagged it as a false positive, with a
// comment saying what it really is.
const IGNORE = new Set([
  'include_image', // set_parameter/get_frame-adjacent argument name, not a tool
  'invalid_argument', // MCP error code
  'not_found', // MCP error code
]);

async function listFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    // Follow symlinks (to files or directories) instead of silently dropping them —
    // entry.isFile()/isDirectory() are both false for a symlink dirent, so the old
    // `entry.isFile()` check dropped a symlinked file with no signal at all.
    const info = entry.isSymbolicLink() ? await stat(await realpath(full)) : entry;
    if (info.isDirectory()) {
      files.push(...(await listFiles(full)));
    } else if (info.isFile()) {
      files.push(full);
    }
  }
  return files;
}

async function readTextOrNull(file) {
  let buf;
  try {
    buf = await readFile(file);
  } catch (err) {
    throw new Error(`check-plugin-tool-names.mjs: could not read ${file}: ${err.message}`);
  }
  // Cheap binary sniff: a NUL byte in the first 8KB means "not text" (JSON/Markdown/
  // YAML never legitimately contain one). Skip rather than decoding as utf8.
  const probe = buf.subarray(0, 8192);
  if (probe.includes(0)) return null;
  return buf.toString('utf8');
}

const catalog = JSON.parse(await readFile(here('../src/data/tools.json'), 'utf8'));
const toolNames = new Set(catalog.map((t) => t.name));

// Tool names/prefixes may appear bare (get_status) or MCP-prefixed
// (mcp__chromatik__get_status). Strip the prefix before tokenizing so the remainder is
// validated as an ordinary tool name. The server key between the two `__` delimiters
// is whatever the client's .mcp.json calls it — hyphens and underscores are both
// plausible (this repo's own name, chromatik-mcp, has a hyphen) — so accept the full
// range rather than [a-z][a-z0-9]* alone, which fails to strip (and so fails open,
// validating nothing) the moment the server key isn't a single lowercase word.
const MCP_PREFIX = /\bmcp__[a-z][a-z0-9_-]*__/g;

// A snake_case token: either a multi-segment word (>=1 underscore, e.g. get_status),
// or a single segment immediately followed by a literal "_*" wildcard suffix meaning
// "the whole <prefix>_ family" (e.g. list_*, bogus_*). Without the second branch a
// verb-only wildcard like list_* never tokenizes at all, because the mandatory
// (?:_[a-z0-9]+)+ group can't match "*" — see #150.
const TOKEN = /\b(?:([a-z][a-z0-9]*(?:_[a-z0-9]+)+)(_\*)?|([a-z][a-z0-9]*)(_\*))/g;

function tokensIn(text) {
  const stripped = text.replace(MCP_PREFIX, '');
  const tokens = [];
  for (const match of stripped.matchAll(TOKEN)) {
    const [, multi, multiWildcard, single, singleWildcard] = match;
    if (multi !== undefined) {
      tokens.push([multi, multiWildcard]);
    } else {
      tokens.push([single, singleWildcard]);
    }
  }
  return tokens;
}

// Split a file into its leading YAML frontmatter (if any) and the remaining body.
function splitFrontmatter(text) {
  if (!text.startsWith('---\n') && text !== '---') return { frontmatter: null, body: text };
  const end = text.indexOf('\n---', 4);
  if (end === -1) return { frontmatter: null, body: text };
  const closeLineEnd = text.indexOf('\n', end + 1);
  return {
    frontmatter: text.slice(0, closeLineEnd === -1 ? text.length : closeLineEnd),
    body: text.slice(closeLineEnd === -1 ? text.length : closeLineEnd + 1),
  };
}

// Only the value of a `tools:` frontmatter line — not every frontmatter key/value.
function toolsFrontmatterLineValues(frontmatter) {
  if (!frontmatter) return [];
  const values = [];
  for (const line of frontmatter.split('\n')) {
    const m = /^tools:\s*(.*)$/.exec(line);
    if (m) values.push(m[1]);
  }
  return values;
}

// Inline backtick spans only — not fenced code blocks (```...```), which this gate
// deliberately does not scan (see file-header comment).
function stripFencedCodeBlocks(text) {
  return text.replace(/```[\s\S]*?```/g, '');
}

function backtickSpans(text) {
  return [...stripFencedCodeBlocks(text).matchAll(/`([^`]+)`/g)].map((m) => m[1]);
}

const files = await listFiles(pluginDir);
const violations = [];
let textFileCount = 0;

for (const file of files) {
  let text;
  try {
    text = await readTextOrNull(file);
  } catch (err) {
    // Distinct from a real violation below — an unreadable file (e.g. EACCES) is an
    // environment problem, not a stale tool reference, and shouldn't be reported as one.
    console.error(err.message);
    process.exit(1);
  }
  if (text === null) continue; // non-text file (e.g. an image) — nothing to tokenize
  textFileCount++;

  const { frontmatter, body } = splitFrontmatter(text);
  const scanned = [...toolsFrontmatterLineValues(frontmatter), ...backtickSpans(body)];

  for (const span of scanned) {
    for (const [token, wildcard] of tokensIn(span)) {
      if (IGNORE.has(token)) continue;
      const ok = wildcard ? [...toolNames].some((name) => name.startsWith(`${token}_`)) : toolNames.has(token);
      if (!ok) {
        violations.push({ file: path.relative(here('../..'), file), token: token + (wildcard ?? '') });
      }
    }
  }
}

if (violations.length) {
  console.error('check-plugin-tool-names.mjs: tool-shaped names referenced in agent-plugin/ that do not exist in tools.json:');
  for (const { file, token } of violations) {
    console.error(`  ${file}: ${token}`);
  }
  console.error(
    'Update the reference (or regenerate tools.json if the tool was renamed) before merging. ' +
      'If this is a genuine non-tool snake_case token, add it to the IGNORE list in check-plugin-tool-names.mjs with a comment.',
  );
  process.exit(1);
}

console.log(`check-plugin-tool-names.mjs: all tool-shaped references in agent-plugin/ (${textFileCount} text files) match tools.json`);
