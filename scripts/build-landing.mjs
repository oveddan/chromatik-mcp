// Builds landing/dist/ — the single-page site published to GitHub Pages.
//
// The setup instructions are NOT authored here. They are rendered from the section of
// README.md between the `landing:start` / `landing:end` markers, so the page and the
// repo's own install guide cannot drift: there is one source, two renderings. Everything
// else on the page (hero, links out) lives in landing/template.html.
//
// Deliberately dependency-free — it renders the small markdown subset the README's setup
// section actually uses (headings, paragraphs, lists, fenced code, GitHub alerts, inline
// formatting, and raw <details> blocks) rather than pulling in a markdown library.
//
//   node scripts/build-landing.mjs            # write landing/dist/
//   node scripts/build-landing.mjs --check    # fail if the committed page is stale
import { readFile, writeFile, mkdir, copyFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));

const START = '<!-- landing:start -->';
const END = '<!-- landing:end -->';

const escapeHtml = (s) =>
  s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');

// GitHub's heading slugger, so in-page links written as GitHub anchors in README.md
// (e.g. "#3-configure-the-port-and-host-optional") still resolve on this page.
const slug = (text) =>
  text
    .toLowerCase()
    .replaceAll(/`|\*\*|\*/g, '')
    .replaceAll(/[^\w\s-]/g, '')
    .trim()
    .replaceAll(/\s+/g, '-');

// Inline formatting. Code spans are extracted first so their contents are never treated
// as bold/link markup.
function inline(text) {
  const codes = [];
  let out = text.replaceAll(/`([^`]+)`/g, (_, code) => {
    codes.push(code);
    return `\u0000${codes.length - 1}\u0000`;
  });
  out = escapeHtml(out);
  out = out.replaceAll(/\[([^\]]+)\]\(([^)]+)\)/g, (_, label, href) => {
    const external = /^https?:/.test(href);
    const rel = external ? ' target="_blank" rel="noopener"' : '';
    return `<a href="${href}"${rel}>${label}</a>`;
  });
  out = out.replaceAll(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  out = out.replaceAll(/(^|[\s(])\*([^*]+)\*/g, '$1<em>$2</em>');
  return out.replaceAll(/\u0000(\d+)\u0000/g, (_, i) => `<code>${escapeHtml(codes[Number(i)])}</code>`);
}

const ALERT_LABELS = { NOTE: 'Note', TIP: 'Tip', IMPORTANT: 'Important', WARNING: 'Warning', CAUTION: 'Caution' };

function renderMarkdown(md) {
  const lines = md.split('\n');
  const html = [];
  let i = 0;

  const paragraph = (buf) => {
    if (buf.length) html.push(`<p>${inline(buf.join(' '))}</p>`);
    buf.length = 0;
  };
  const buf = [];

  while (i < lines.length) {
    const line = lines[i];

    // fenced code
    if (line.startsWith('```')) {
      paragraph(buf);
      const lang = line.slice(3).trim();
      const body = [];
      i += 1;
      while (i < lines.length && !lines[i].startsWith('```')) body.push(lines[i++]);
      i += 1;
      const cls = lang ? ` class="language-${lang}"` : '';
      html.push(`<pre><code${cls}>${escapeHtml(body.join('\n'))}</code></pre>`);
      continue;
    }

    // GitHub alert: "> [!WARNING]" followed by quoted body
    const alert = line.match(/^>\s*\[!(\w+)\]\s*$/);
    if (alert) {
      paragraph(buf);
      const kind = alert[1].toUpperCase();
      const body = [];
      i += 1;
      while (i < lines.length && lines[i].startsWith('>')) {
        body.push(lines[i].replace(/^>\s?/, ''));
        i += 1;
      }
      const label = ALERT_LABELS[kind] ?? kind;
      html.push(
        `<div class="alert alert-${kind.toLowerCase()}"><p class="alert-label">${label}</p>` +
          renderMarkdown(body.join('\n')) +
          '</div>',
      );
      continue;
    }

    // raw HTML block passthrough (<details>, <summary>, </details>)
    if (/^<\/?(details|summary)/.test(line.trim())) {
      paragraph(buf);
      html.push(line);
      i += 1;
      continue;
    }

    const heading = line.match(/^(#{1,4})\s+(.*)$/);
    if (heading) {
      paragraph(buf);
      const level = heading[1].length;
      html.push(`<h${level} id="${slug(heading[2])}">${inline(heading[2])}</h${level}>`);
      i += 1;
      continue;
    }

    if (/^[-*]\s+/.test(line)) {
      paragraph(buf);
      const items = [];
      while (i < lines.length && (/^[-*]\s+/.test(lines[i]) || (items.length && /^\s+\S/.test(lines[i])))) {
        if (/^[-*]\s+/.test(lines[i])) items.push(lines[i].replace(/^[-*]\s+/, ''));
        else items[items.length - 1] += ' ' + lines[i].trim();
        i += 1;
      }
      html.push('<ul>' + items.map((it) => `<li>${inline(it)}</li>`).join('') + '</ul>');
      continue;
    }

    if (line.trim() === '') {
      paragraph(buf);
      i += 1;
      continue;
    }

    buf.push(line.trim());
    i += 1;
  }
  paragraph(buf);
  return html.join('\n');
}

const readme = await readFile(here('../README.md'), 'utf8');
const startIdx = readme.indexOf(START);
const endIdx = readme.indexOf(END);
if (startIdx === -1 || endIdx === -1) {
  console.error(`build-landing.mjs: README.md is missing ${START} / ${END} markers`);
  process.exit(1);
}
const setupMd = readme.slice(startIdx + START.length, endIdx).trim();
const setupHtml = renderMarkdown(setupMd);

const template = await readFile(here('../landing/template.html'), 'utf8');
if (!template.includes('{{setup}}')) {
  console.error('build-landing.mjs: landing/template.html has no {{setup}} slot');
  process.exit(1);
}
const page = template.replace('{{setup}}', setupHtml);

const distDir = here('../landing/dist');
const outPath = here('../landing/dist/index.html');

if (process.argv.includes('--check')) {
  const current = await readFile(outPath, 'utf8').catch(() => null);
  if (current !== page) {
    console.error(
      'build-landing.mjs: landing/dist/index.html is stale relative to README.md or ' +
        'template.html — run `node scripts/build-landing.mjs`.',
    );
    process.exit(1);
  }
  console.log('landing page is up to date with README.md');
  process.exit(0);
}

await mkdir(distDir, { recursive: true });
await writeFile(outPath, page);

// static assets sit alongside the template and are copied verbatim
const assets = (await readdir(here('../landing/public'))).filter((f) => !f.startsWith('.'));
for (const asset of assets) {
  await copyFile(here(`../landing/public/${asset}`), here(`../landing/dist/${asset}`));
}
console.log(`wrote landing/dist/index.html + ${assets.length} assets`);
