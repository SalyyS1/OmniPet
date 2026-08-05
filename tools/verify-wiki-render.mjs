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
import { existsSync, statSync } from 'node:fs';
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
const renderMarkdownSource = appSource.match(/function renderMarkdown\([\s\S]*?\n  \}/);
const helpers = ['escapeHtml', 'inline', 'slug', 'renderTable', 'splitSections', 'snippet']
  .map((name) => {
    const match = appSource.match(new RegExp(`function ${name}\\([\\s\\S]*?\\n  \\}`));
    if (!match) throw new Error(`could not extract ${name} from wiki.js`);
    return match[0];
  });

if (!renderMarkdownSource) throw new Error('could not extract renderMarkdown from wiki.js');

const renderMarkdown = new Function(
  `${helpers.join('\n')}\n${renderMarkdownSource[0]}\nreturn renderMarkdown;`,
)();

/** The search helpers, exercised against the same content the page ships. */
const searchHelpers = new Function(
  `${helpers.join('\n')}\nreturn { splitSections: splitSections, snippet: snippet };`,
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

console.log('image weight');
// A documentation page that takes seconds to load is a page people stop reading, and generated art is
// the one asset here that can quietly grow by megabytes.
const MAX_IMAGE_KIB = 400;
let totalKib = 0;
for (const src of referenced) {
  const bytes = statSync(join(docs, src)).size;
  const kib = Math.round(bytes / 1024);
  totalKib += kib;
  check(kib <= MAX_IMAGE_KIB, `${src} is ${kib} KiB (limit ${MAX_IMAGE_KIB})`);
}
check(totalKib <= 1024, `all page images total ${totalKib} KiB (limit 1024)`);

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

console.log('accessibility affordances');
// Every one of these was a real defect: a hardcoded aria-pressed contradicted a stored preference, a
// described decorative banner was read out before the content, and a missing live region left page
// changes and result counts silent.
check(!/data-language="en"[^>]*aria-pressed/.test(html),
  'aria-pressed is left to the script rather than hardcoded to English');
check(/class="wiki-banner"[^>]*alt=""/.test(html), 'the decorative banner has an empty alt');
check(html.includes('id="wiki-announcer"') && html.includes('aria-live'),
  'wiki.html has a live region for announcements');
check(html.includes('id="wiki-results"'), 'wiki.html has a container for search results');

// Results live in the reading column, not the aside: the aside is display:none below 1180px, so results
// placed there would be unreachable on every phone.
const resultsBeforeAside = html.indexOf('id="wiki-results"') < html.indexOf('wiki-aside');
check(resultsBeforeAside, 'search results are outside the aside that collapses on narrow screens');

const css = await readFile(join(docs, 'wiki.css'), 'utf8');
// The contents list must survive the aside collapsing, or phones lose the only skim tool on long pages.
check(css.includes('.wiki-toc-inline'), 'a collapsed contents list exists for narrow screens');
check(/\.wiki-toc a\[aria-current="location"\]/.test(css), 'the contents list styles scroll position');

const tokens = await readFile(join(docs, 'styles.css'), 'utf8');
// The old accent may still be named in a comment explaining why it was replaced; what must not survive
// is a declaration still using it. Text colour at 2.65:1 was the defect.
const oldAccentInUse = tokens
  .split('\n')
  .filter((line) => !line.trim().startsWith('*') && !line.trim().startsWith('/*'))
  .some((line) => line.includes('#e67532') || line.includes('230, 117, 50'));
check(!oldAccentInUse, 'no declaration still uses the old 2.65:1 accent');
check(tokens.includes('--line-strong'),
  'control borders have their own token, separate from decorative hairlines');

console.log('markdown affordances');
const withCode = renderMarkdown('```\n/pet admin reload\n```');
// The copy button reads this attribute, and it has to carry the source rather than the escaped HTML.
check(withCode.html.includes('data-code="/pet admin reload"'),
  'code blocks carry their raw source for the copy button');
const withTable = renderMarkdown('## Commands\n\n| A | B |\n| --- | --- |\n| 1 | 2 |');
check(withTable.html.includes('tabindex="0"'), 'tables are focusable so a keyboard can scroll them');
check(withTable.html.includes('role="region"'), 'tables are announced as a region');
check(/aria-label="Commands/.test(withTable.html), 'a table is labelled by the heading above it');

console.log('search');
// The search reads page bodies, so the section split it relies on has to actually produce sections.
for (const page of wiki.pages) {
  for (const language of LANGUAGES) {
    const sections = searchHelpers.splitSections(page.body[language]);
    check(sections.some((section) => section.id),
      `${page.id}/${language}: splits into titled sections for search`);
  }
}
const sentence = 'Run the reload command to apply it.';
const marked = searchHelpers.snippet(sentence, sentence.indexOf('reload'), 'reload'.length);
check(marked.includes('<mark>reload</mark>'), 'a snippet marks the matched term');
check(!marked.includes('<script'), 'a snippet escapes its surroundings');
// The hit index comes from the raw body, but the snippet collapses whitespace first, so the term has to
// be re-found rather than sliced at the original offset.
const padded = 'First line\n\n   padded   reload   here';
const wrapped = searchHelpers.snippet(padded, padded.indexOf('reload'), 'reload'.length);
check(wrapped.includes('<mark>reload</mark>'), 'a snippet survives collapsing whitespace');

// --- run the real script against a DOM stub ----------------------------------------------------

/*
 * The checks above exercise extracted functions, which cannot catch a throw in the parts that only run
 * on load — the boot sequence, the rail builder, the article renderer. A stub DOM is enough to run the
 * actual IIFE and see whether it produces a page.
 */
console.log('boot');

function stubElement(tag) {
  const node = {
    tagName: tag,
    dataset: {},
    style: {},
    children: [],
    hidden: false,
    textContent: '',
    _html: '',
    classList: { toggle() {}, add() {}, remove() {} },
    setAttribute() {},
    removeAttribute() {},
    getAttribute() { return null; },
    addEventListener() {},
    appendChild(child) { node.children.push(child); },
    focus() {},
    scrollIntoView() {},
    closest() { return null; },
    querySelector() { return null; },
    querySelectorAll() { return []; },
    get innerHTML() { return node._html; },
    set innerHTML(value) { node._html = value; },
  };
  return node;
}

const stubs = {};
for (const id of ['wiki-nav', 'wiki-article', 'wiki-toc', 'wiki-search', 'wiki-results',
  'wiki-announcer', 'brand-tagline', 'wiki-stats']) {
  stubs[`#${id}`] = stubElement('div');
}

globalThis.window = {
  location: { hash: '' },
  localStorage: { getItem() { return null; }, setItem() {} },
  addEventListener() {},
  setTimeout() {},
  // Absent on purpose: both have to degrade rather than throw, since `file://` has neither.
  IntersectionObserver: null,
  fetch: null,
};
// Node defines `navigator` as a getter-only global, so it has to be redefined rather than assigned.
Object.defineProperty(globalThis, 'navigator', {
  value: { language: 'en-US' },
  configurable: true,
  writable: true,
});
globalThis.document = {
  documentElement: {},
  body: stubElement('body'),
  querySelector(selector) { return stubs[selector] || null; },
  querySelectorAll(selector) {
    return selector === '[data-language]' ? [stubElement('button'), stubElement('button')] : [];
  },
  getElementById() { return null; },
  createElement: stubElement,
  addEventListener() {},
};

let booted = true;
try {
  new Function('window', contentSource)(globalThis.window);
  new Function(appSource)();
} catch (failure) {
  booted = false;
  console.error(`  FAIL wiki.js threw on load: ${failure.message}`);
  failures += 1;
}

if (booted) {
  const nav = stubs['#wiki-nav'].innerHTML;
  const article = stubs['#wiki-article'].innerHTML;
  const toc = stubs['#wiki-toc'].innerHTML;

  check(nav.length > 0, 'the rail renders');
  check(nav.includes('<h2>'), 'the rail groups pages under section headings');
  check(nav.includes('<small>'), 'the rail shows summaries rather than hiding them in a tooltip');
  check(!nav.includes('title="'), 'summaries are not tooltips, which touch and screen readers miss');
  check(article.length > 0, 'the article renders');
  check(article.includes('wiki-pager'), 'the article ends with a previous/next pager');
  check(article.includes('wiki-toc-inline'), 'the article carries a collapsed contents list');
  check(toc.includes('#/'), 'contents links keep the page in the hash so a deep link survives a reload');
  check(!/href="#[a-z]/.test(toc), 'no contents link overwrites the hash with a bare heading id');
}

console.log('heading anchors');
/*
 * Anchor ids have to be unique per page and readable in a shared link. Vietnamese headings are the hard
 * case: stripping diacritics rather than folding them turned "Chọn trang cần đọc" into "ch-n-trang-c-n-c"
 * and left two headings differing only in accents mapping to the same id — a deep link that quietly goes
 * to the wrong section.
 */
const slugOnly = new Function(
  `${helpers.join('\n')}\nreturn slug;`,
)();
for (const page of wiki.pages) {
  for (const language of LANGUAGES) {
    const headings = page.body[language]
      .split('\n')
      .filter((line) => line.startsWith('## '))
      .map((line) => line.slice(3).trim());
    const ids = headings.map(slugOnly);
    check(ids.every((id) => id.length > 0), `${page.id}/${language}: every heading produces an id`);
    check(new Set(ids).size === ids.length, `${page.id}/${language}: heading ids are unique`);
  }
}
check(slugOnly('Chọn trang cần đọc') === 'chon-trang-can-doc',
  'Vietnamese diacritics fold to base letters rather than being dropped');
check(slugOnly('Đã có') === 'da-co', 'the đ character folds rather than vanishing');

console.log(failures === 0 ? '\nAll wiki checks passed.' : `\n${failures} check(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
