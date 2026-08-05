#!/usr/bin/env node
/**
 * Drives the published wiki in a real browser.
 *
 * Complements verify-wiki-render.mjs rather than replacing it. That script runs anywhere and checks the
 * logic — content, the Markdown subset, translations. This one needs Chromium and a local server, and
 * checks the things only a layout engine can answer: how many characters actually fit on a line, whether
 * a deep link survives a reload, whether the contents list is reachable on a phone.
 *
 * Usage:
 *   python -m http.server 8899 --directory docs &
 *   node tools/verify-wiki-browser.mjs [http://localhost:8899]
 *
 * Playwright is not a dependency of this repository, which has no Node package manifest at all. It is
 * resolved wherever it happens to be installed — locally, globally, or in the npx cache — and the script
 * explains how to get it rather than failing with a module-resolution stack trace.
 */

import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);

/** Finds Playwright without making it a dependency of a repository that has no manifest. */
async function loadChromium() {
  const candidates = [
    'playwright',
    'playwright-core',
    `${process.env.HOME || process.env.USERPROFILE}/node_modules/playwright/index.js`,
    `${process.env.APPDATA || ''}/npm/node_modules/playwright/index.js`,
  ];
  for (const candidate of candidates) {
    try {
      const resolved = require.resolve(candidate);
      const module = require(resolved);
      if (module && module.chromium) return module.chromium;
    } catch (ignored) {
      // Try the next location.
    }
  }
  console.error('Playwright is not available. Install it, then re-run:');
  console.error('  npm install --no-save playwright && npx playwright install chromium');
  console.error('\nThis check is optional; tools/verify-wiki-render.mjs runs everywhere and gates the deploy.');
  process.exit(2);
}

const chromium = await loadChromium();

const base = process.argv[2] || 'http://localhost:8899';

let failures = 0;
function check(condition, message) {
  console.log(`${condition ? '  ok  ' : '  FAIL'} ${message}`);
  if (!condition) failures += 1;
}

/**
 * Characters per line for the widest paragraph on the page.
 *
 * Measured with a probe span in the paragraph's own computed font rather than assumed from the CSS,
 * because the whole point of the `ch` cap is what the font actually does at that size.
 */
const MEASURE = () => {
  const paragraph = [...document.querySelectorAll('.wiki-article p')]
    .find((node) => node.textContent.length > 200);
  if (!paragraph) return null;
  const style = getComputedStyle(paragraph);
  const probe = document.createElement('span');
  probe.style.font = style.font;
  probe.style.visibility = 'hidden';
  probe.style.whiteSpace = 'pre';
  probe.textContent = '0'.repeat(100);
  paragraph.appendChild(probe);
  const perCharacter = probe.getBoundingClientRect().width / 100;
  probe.remove();
  return {
    characters: paragraph.getBoundingClientRect().width / perCharacter,
    family: style.fontFamily,
  };
};

const browser = await chromium.launch();

// --- desktop ------------------------------------------------------------------------------------

console.log('desktop');
const errors = [];
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()); });
page.on('pageerror', (error) => errors.push(String(error)));

await page.goto(`${base}/wiki.html`, { waitUntil: 'networkidle' });
check(errors.length === 0, `no console errors (${errors.join(' | ') || 'none'})`);

// The stats panel is fetched, so it only fills in over HTTP — this is the check `file://` cannot make.
const stats = await page.textContent('#wiki-stats');
check(/\d{3}/.test(stats), `build statistics loaded from the generated file (${stats.replace(/\s+/g, ' ').trim()})`);

const measured = await page.evaluate(MEASURE);
check(measured !== null && measured.characters <= 80,
  `prose measures ${measured ? Math.round(measured.characters) : '?'} characters per line (target <= 80)`);
check(measured !== null && !/Plex Mono/.test(measured.family),
  `prose is set in a proportional face (${measured?.family})`);

// A contents click must not replace the hash with a bare heading id, or a shared link loses its page.
await page.click('#wiki-toc a');
await page.waitForTimeout(250);
const deepLink = page.url();
check(/#\/[a-z-]+#/.test(deepLink),
  `a contents click keeps the page in the url (#${deepLink.split('#').slice(1).join('#')})`);
await page.reload({ waitUntil: 'networkidle' });
check(page.url() === deepLink, 'reloading a deep link returns to the same page and heading');
check((await page.$$('.wiki-nav a[aria-current="page"]')).length === 1,
  'exactly one rail entry is marked as the open page');

// Finishing one page and opening the next used to drop the reader into the middle of it.
await page.goto(`${base}/wiki.html#/commands`, { waitUntil: 'networkidle' });
await page.evaluate(() => window.scrollTo(0, 1200));
await page.click('.wiki-nav a[data-page="config"]');
// Waited for rather than slept through: a fixed delay passes locally and flakes on a slower runner,
// which says nothing about whether the page scrolled.
await page.waitForFunction(() => window.scrollY < 50, null, { timeout: 5000 }).catch(() => {});
check(await page.evaluate(() => window.scrollY) < 50, 'changing page scrolls back to the top');
check(await page.evaluate(() => document.activeElement.id) === 'wiki-article',
  'focus moves into the article, so a screen reader starts reading the new page');

console.log('search');
await page.fill('#wiki-search', 'reload');
await page.waitForTimeout(300);
check((await page.$$('#wiki-results a')).length > 0, 'a query returns results');
check((await page.$$('#wiki-results mark')).length > 0, 'results highlight the matched term');
check(/\d+/.test(await page.textContent('#wiki-results .result-count')), 'the result count is shown');
await page.press('#wiki-search', 'ArrowDown');
await page.waitForTimeout(150);
check((await page.$$('#wiki-results a.selected')).length === 1, 'arrow keys move the selection');
await page.press('#wiki-search', 'Enter');
await page.waitForTimeout(400);
check(await page.evaluate(() => document.querySelector('#wiki-results').hidden),
  'Enter opens the selected result and puts the search away');

// An English reader searching a Vietnamese term used to get nothing at all.
await page.fill('#wiki-search', 'dung lượng');
await page.waitForTimeout(300);
check((await page.$$('#wiki-results a')).length > 0, 'a search crosses languages');
await page.fill('#wiki-search', '');
await page.waitForTimeout(150);

console.log('affordances');
await page.goto(`${base}/wiki.html#/config`, { waitUntil: 'networkidle' });
check((await page.$$('.wiki-code button')).length > 0, 'code blocks carry a copy button');
check((await page.$$('.table-scroll[tabindex="0"]')).length > 0,
  'tables are focusable, so a keyboard can scroll them');
check((await page.$$('.wiki-pager a')).length > 0, 'the article ends with a prev/next pager');

/*
 * Checked at several depths rather than one. The first implementation marked a heading only while that
 * heading was inside a narrow band, so the marker blinked out whenever a section was longer than the
 * viewport — which is precisely where a contents list earns its place. A single spot-check passed.
 */
const spy = [];
for (const offset of [0, 800, 1600, 2400, 3200]) {
  await page.evaluate((y) => window.scrollTo(0, y), offset);
  await page.waitForTimeout(300);
  const marked = await page.$$('.wiki-toc a[aria-current="location"]');
  spy.push(`${offset}:${marked.length}`);
}
check(spy.every((entry) => entry.endsWith(':1')),
  `the contents list marks exactly one heading at every scroll depth (${spy.join(' ')})`);

// --- phone --------------------------------------------------------------------------------------

console.log('Vietnamese typography');
/*
 * Half of this site is Vietnamese, and a font without precomposed Vietnamese glyphs does not fail
 * visibly — the browser synthesises them by stacking a combining accent on a base letter, so "ấp trứng"
 * renders as "â ́p tr ứng". Georgia does exactly this, which is how it got shipped here in the first
 * place: it looks correct in English and is broken on every Vietnamese page.
 *
 * Measured per rendered element in its own computed font rather than against a guessed list, so a font
 * added later is covered too. A synthesised character measures roughly twice its base letter.
 */
await page.goto(`${base}/wiki.html#/overview`, { waitUntil: 'networkidle' });
await page.click('[data-language="vi"]');
await page.waitForTimeout(300);
const brokenFonts = await page.evaluate(() => {
  const canvas = document.createElement('canvas').getContext('2d');
  const suspect = [];
  const seen = new Set();
  const selector = '.wiki-article h1, .wiki-article h2, .wiki-article p, .wiki-article td,'
    + ' .wiki-article code, .wiki-nav a, .wiki-nav h2, .wiki-toc a, .brand strong, .brand small';
  for (const node of document.querySelectorAll(selector)) {
    const font = getComputedStyle(node).font;
    if (!font || seen.has(font)) continue;
    seen.add(font);
    canvas.font = font;
    const base = canvas.measureText('a').width;
    const worst = Math.max(...['ấ', 'ư', 'ộ', 'ề', 'ỹ']
      .map((character) => canvas.measureText(character).width / base));
    if (worst > 1.4) suspect.push(`${font} (${worst.toFixed(2)}x)`);
  }
  return suspect;
});
check(brokenFonts.length === 0,
  `every rendered font carries Vietnamese as single glyphs (${brokenFonts.join('; ') || 'all clear'})`);

/*
 * A glyph the font has can still be sheared off by its own line box. The rail heading inherited the
 * global 1.02 line-height for headings, which left the line box a fraction of a pixel taller than the
 * font — and the rail scrolls, so `overflow-y: auto` clipped the top of every accent. "BẮT ĐẦU" rendered
 * as "BĂT ĐÂU": a different word, and invisible in English where there is nothing above the cap height.
 */
const clipped = await page.evaluate(() => {
  const tight = [];
  const selector = '.wiki-nav h2, .wiki-toc h3, .wiki-article .eyebrow, .wiki-nav a,'
    + ' .wiki-toc a, .wiki-results .result-page, .wiki-pager small';
  for (const node of document.querySelectorAll(selector)) {
    const style = getComputedStyle(node);
    const size = parseFloat(style.fontSize);
    const height = parseFloat(style.lineHeight);
    // Accents need roughly a fifth of the font size above the cap height to survive.
    if (Number.isFinite(height) && height < size * 1.2) {
      tight.push(`${node.className || node.tagName} ${size}px/${height}px`);
    }
  }
  return tight;
});
check(clipped.length === 0,
  `no label has a line box too tight for a stacked tone mark (${clipped.join('; ') || 'all clear'})`);

await page.click('[data-language="en"]');
await page.waitForTimeout(200);

console.log('375px');
const phoneErrors = [];
const phone = await browser.newPage({ viewport: { width: 375, height: 780 } });
phone.on('pageerror', (error) => phoneErrors.push(String(error)));
await phone.goto(`${base}/wiki.html#/config`, { waitUntil: 'networkidle' });
check(phoneErrors.length === 0, `no errors (${phoneErrors.join(' | ') || 'none'})`);

// The aside is display:none here, so these are the checks that the fallbacks actually work.
check(await phone.isVisible('.wiki-toc-inline'), 'the collapsed contents list is reachable');
check(await phone.isVisible('#wiki-search'), 'search is reachable');

const phoneMeasured = await phone.evaluate(MEASURE);
check(phoneMeasured !== null && phoneMeasured.characters <= 80,
  `prose measures ${phoneMeasured ? Math.round(phoneMeasured.characters) : '?'} characters per line`);

await phone.fill('#wiki-search', 'reload');
await phone.waitForTimeout(300);
check(await phone.isVisible('#wiki-results a'), 'search results are reachable');

// --- landing page -------------------------------------------------------------------------------

console.log('landing page');
const landingErrors = [];
const landing = await browser.newPage({ viewport: { width: 1400, height: 900 } });
landing.on('pageerror', (error) => landingErrors.push(String(error)));
await landing.goto(`${base}/index.html`, { waitUntil: 'networkidle' });
check(landingErrors.length === 0, `no errors (${landingErrors.join(' | ') || 'none'})`);

// The wiki is the reference, so no card may send a reader to the repository copy instead.
const rawLinks = await landing.$$eval('a[href]', (anchors) => anchors
  .map((anchor) => anchor.getAttribute('href'))
  .filter((href) => /githubusercontent|blob\/main\/docs|tree\/main\/docs/.test(href)));
check(rawLinks.length === 0, `no link points at raw repository markdown (${rawLinks.join(', ') || 'none'})`);

await browser.close();

console.log(failures === 0 ? '\nAll browser checks passed.' : `\n${failures} browser check(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
