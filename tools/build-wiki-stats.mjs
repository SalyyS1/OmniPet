#!/usr/bin/env node
/**
 * Generates docs/wiki-stats.json from the Gradle test results.
 *
 * The old site claimed "54 suites / 203 tests" long after the real count passed 700, because the number
 * was typed into the HTML by hand. Deriving it from the build output means it is either current or absent,
 * and never confidently wrong.
 *
 * Usage: node tools/build-wiki-stats.mjs
 * Reads:  omnipet-core/build/test-results/test/*.xml, omnipet-paper/build/test-results/test/*.xml
 * Writes: docs/wiki-stats.json
 */

import { readdir, readFile, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const resultDirs = [
  join(root, 'omnipet-core', 'build', 'test-results', 'test'),
  join(root, 'omnipet-paper', 'build', 'test-results', 'test'),
];

/** Sums the counts JUnit writes onto each suite's root element. */
async function collect() {
  let tests = 0;
  let suites = 0;
  let skipped = 0;
  let failures = 0;

  for (const dir of resultDirs) {
    if (!existsSync(dir)) continue;
    const files = (await readdir(dir)).filter((name) => name.endsWith('.xml'));
    for (const name of files) {
      const xml = await readFile(join(dir, name), 'utf8');
      const attribute = (key) => {
        const match = xml.match(new RegExp(`${key}="(\\d+)"`));
        return match ? Number(match[1]) : 0;
      };
      suites += 1;
      tests += attribute('tests');
      skipped += attribute('skipped');
      failures += attribute('failures') + attribute('errors');
    }
  }

  return { tests, suites, skipped, failures };
}

const stats = await collect();

if (stats.tests === 0) {
  console.error('No test results found. Run `gradlew build` first, then re-run this script.');
  process.exit(1);
}

// A date rather than a timestamp: the reader wants to know how fresh the number is, not the minute it
// was produced, and a minute-level value would churn the file on every run.
const generatedAt = new Date().toISOString().slice(0, 10);

await writeFile(
  join(root, 'docs', 'wiki-stats.json'),
  `${JSON.stringify({ ...stats, generatedAt }, null, 2)}\n`,
  'utf8',
);

console.log(
  `docs/wiki-stats.json: ${stats.tests} tests across ${stats.suites} suites `
  + `(${stats.skipped} skipped, ${stats.failures} failing)`,
);
