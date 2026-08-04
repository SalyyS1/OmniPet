/**
 * Verifies the wiki renders without a browser.
 *
 * The agent-browser daemon will not start in this environment, and the parts worth checking are logic
 * rather than layout: does every page render, does the Markdown subset handle what the content actually
 * uses, and is every string present in both languages. A DOM stub is enough for that and runs anywhere.
 *
 * Usage: node tools/verify-wiki-render.mjs
 */

import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const docs = join(root, 'docs');

let failures = 0;
function check(condition, message) {
  if (condition) {
    console.log(`  ok   ${message}`);
  } else {
    console.error(`  FAIL ${message}`);
    failures += 1;
  }
}

// --- load the content module by evaluating it against a window stub ----------------------------

const contentSource = await readFile(join(docs, 'wiki-content.js'), 'utf8');
const window = {};
new Function('window', contentSource)(window);
const wiki = window.OMNIPET_WIKI;

console.log('content');
check(Array.isArray(wiki.pages) && wiki.pages.length > 0, `${wiki.pages.length} pages defined`);

const LANGUAGES = ['en', 'vi'];

for (const page of wiki.pages) {
  for (const language of LANGUAGES) {
    check(
      typeof page.title[language] === 'string' && page.title[language].length > 0,
      `${page.id}: ${language} title present`,
    );
    check(
      typeof page.body[language] === 'string' && page.body[language].length > 200,
      `${page.id}: ${language} body present`,
    );
  }
  // A translation that is byte-identical to the English is almost always an untranslated placeholder.
  check(page.body.en !== page.body.vi, `${page.id}: vi body is actually translated`);
}

console.log('ui strings');
for (const language of LANGUAGES) {
  const strings = wiki.ui[language];
  check(!!strings, `${language} ui block present`);
  for (const key of Object.keys(wiki.ui.en)) {
    check(strings[key] !== undefined, `${language}.${key} present`);
  }
}

// --- exercise the renderer the page uses -------------------------------------------------------

const appSource = await readFile(join(docs, 'wiki.js'), 'utf8');

// The renderer is an IIFE that touches the DOM on load, so the functions are re-created here from the
// same source rather than imported. Keeping the extraction honest: if these regexes stop matching, the
// check fails loudly rather than silently testing nothing.
const renderMarkdownSource = appSource.match(/function renderMarkdown\(source\) \{[\s\S]*?\n  \}/);
const helpers = ['escapeHtml', 'inline', 'slug', 'renderTable'].map((name) => {
  const match = appSource.match(new RegExp(`function ${name}\\([\\s\\S]*?\\n  \\}`));
  if (!match) throw new Error(`could not extract ${name} from wiki.js`);
  return match[0];
});

if (!renderMarkdownSource) throw new Error('could not extract renderMarkdown from wiki.js');

const renderMarkdown = new Function(
  `${helpers.join('\n')}\n${renderMarkdownSource[0]}\nreturn renderMarkdown;`,
)();

console.log('markdown renderer');
for (const page of wiki.pages) {
  for (const language of LANGUAGES) {
    const rendered = renderMarkdown(page.body[language]);
    check(rendered.html.length > 100, `${page.id}/${language}: produced html`);
    check(rendered.headings.length > 0, `${page.id}/${language}: produced a table of contents`);
    check(!rendered.html.includes('undefined'), `${page.id}/${language}: no undefined in output`);
    // Unbalanced tags mean a table or list ran off the end of the parser.
    const openTables = (rendered.html.match(/<table>/g) || []).length;
    const closeTables = (rendered.html.match(/<\/table>/g) || []).length;
    check(openTables === closeTables, `${page.id}/${language}: tables balanced`);
  }
}

console.log('images');
const imagePattern = /!\[[^\]]*\]\(([^)]+)\)/g;
const referenced = new Set();
for (const page of wiki.pages) {
  for (const language of LANGUAGES) {
    let match;
    while ((match = imagePattern.exec(page.body[language])) !== null) referenced.add(match[1]);
  }
}
// A broken image is invisible until someone opens the page, so the file has to exist at build time.
for (const src of referenced) {
  check(existsSync(join(docs, src)), `${src} exists on disk`);
}
const withImage = renderMarkdown('![Alt text](img/hatch-flow.svg)');
check(withImage.html.includes('<img'), 'image syntax renders an img element');
check(withImage.html.includes('alt="Alt text"'), 'image alt text is preserved');
check(!withImage.html.includes('](') , 'image syntax is fully consumed');

console.log('escaping');
const hostile = renderMarkdown('## <script>alert(1)</script>\n\nA <img src=x onerror=y> line.');
check(!hostile.html.includes('<script>'), 'script tags in content are escaped');
check(!hostile.html.includes('<img'), 'raw html in content is escaped');

console.log('assets referenced by the page');
const html = await readFile(join(docs, 'wiki.html'), 'utf8');
for (const asset of ['styles.css', 'wiki.css', 'wiki-content.js', 'wiki.js']) {
  check(html.includes(asset), `wiki.html references ${asset}`);
}
check(html.includes('data-language="en"') && html.includes('data-language="vi"'),
  'wiki.html has both language buttons');

console.log(failures === 0 ? '\nAll wiki checks passed.' : `\n${failures} check(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
