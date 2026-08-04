#!/usr/bin/env node
/**
 * Generates the wiki's decorative artwork through an OpenAI-compatible image endpoint.
 *
 * Separate from build-wiki-diagrams.mjs on purpose. The diagrams there carry meaning — a flow diagram
 * showing the wrong hatch order is worse than no diagram — so they are drawn deterministically from repo
 * facts. The images here are decoration: a masthead and page headers. If this script cannot run, the site
 * is still complete and correct, which is why the SVG banner remains the committed fallback.
 *
 * Credentials come from the environment and are never written to the repository:
 *   IMAGE_API_BASE   e.g. https://router.example/v1
 *   IMAGE_API_KEY    the bearer token
 *   IMAGE_API_MODEL  e.g. cx/gpt-5.5-image
 *
 * Usage:
 *   node tools/build-wiki-art.mjs            # only generate what is missing
 *   node tools/build-wiki-art.mjs --force    # regenerate everything
 */

import { mkdir, writeFile, stat, unlink } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = join(root, 'docs', 'img');
const force = process.argv.includes('--force');
const run = promisify(execFile);

/**
 * Compresses a generated PNG to WebP and drops the original.
 *
 * <p>The endpoint returns 1.5-2 MB per image, which is a 7 MB page for four of them. WebP at quality 82
 * holds up on flat vector art — the linework and the flat fills are exactly what it compresses well — and
 * brings the set under 500 KB. ImageMagick is a build-time tool here, not a plugin dependency.
 *
 * @returns the bytes written, or null when ImageMagick is unavailable and the PNG was kept
 */
async function compress(pngPath, webpPath) {
  try {
    await run('magick', [pngPath, '-resize', '1600x1600>', '-quality', '82',
      '-define', 'webp:method=6', webpPath]);
  } catch {
    console.warn('  magick not found; keeping the PNG uncompressed');
    return null;
  }
  const compressed = await stat(webpPath);
  await unlink(pngPath);
  return compressed.size;
}

const base = process.env.IMAGE_API_BASE;
const key = process.env.IMAGE_API_KEY;
const model = process.env.IMAGE_API_MODEL;

if (!base || !key || !model) {
  console.error('Set IMAGE_API_BASE, IMAGE_API_KEY, and IMAGE_API_MODEL first.');
  console.error('The committed SVG artwork stays valid, so skipping this step is safe.');
  process.exit(1);
}

/**
 * A shared style clause.
 *
 * <p>Repeated into every prompt so the set reads as one family rather than four unrelated pictures, and
 * kept text-free because any text an image model renders is unreadable at banner scale and cannot be
 * translated — the page supplies the words in both languages instead.
 */
const STYLE = 'Flat editorial vector illustration, muted cream #f3f0e7 background, deep teal #102a2b '
  + 'linework, soft mint #85c8b2 and warm orange #e67532 accents, generous negative space, calm and '
  + 'premium, no text, no letters, no words, no watermark, no user interface';

/**
 * The endpoint ignores the `size` parameter, so orientation has to be stated in the prompt itself.
 *
 * <p>Found by probing: a request for 1792x768 came back 1024x1536 — portrait, useless as a masthead —
 * until the shape was described in words. `size` is still sent, since honouring it would do no harm.
 */
const WIDE = 'WIDE PANORAMIC BANNER, 21:9 ultrawide letterbox composition, much wider than tall. ';

const ART = [
  {
    file: 'wiki-hero.png',
    size: '1536x1024',
    prompt: `${WIDE}A glowing teal dragon egg on a mossy stone pedestal at the centre-right, two lit `
      + `torches flanking it, wide empty cream space on the left for a title, distant soft mint `
      + `mountains along the horizon. ${STYLE}`,
  },
  {
    file: 'art-player.png',
    size: '1024x1024',
    prompt: `A small friendly companion creature sitting beside an open treasure chest that glows softly `
      + `from within, cosy and inviting. ${STYLE}`,
  },
  {
    file: 'art-operator.png',
    size: '1024x1024',
    prompt: `An open technical ledger beside neat stacks of labelled crates and a brass dial, orderly and `
      + `deliberate, suggesting careful record keeping. ${STYLE}`,
  },
  {
    file: 'art-placed-egg.png',
    size: '1024x1024',
    prompt: `A single egg placed on the ground encircled by glowing lava, heat shimmer rising around it, `
      + `dramatic but calm. ${STYLE}`,
  },
];

async function exists(path) {
  try {
    await stat(path);
    return true;
  } catch {
    return false;
  }
}

/** One request. Returns the PNG bytes, or throws with the endpoint's own message. */
async function generate(entry) {
  const response = await fetch(`${base.replace(/\/$/, '')}/images/generations`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ model, prompt: entry.prompt, n: 1, size: entry.size }),
  });

  const text = await response.text();
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${text.slice(0, 300)}`);

  let payload;
  try {
    payload = JSON.parse(text);
  } catch {
    throw new Error(`response was not JSON: ${text.slice(0, 200)}`);
  }

  const encoded = payload?.data?.[0]?.b64_json;
  if (!encoded) throw new Error(`no image in response: ${text.slice(0, 300)}`);

  const bytes = Buffer.from(encoded, 'base64');
  // Verified rather than trusted: a truncated or error-page body would otherwise land on disk as a
  // .png that no browser can open, and the failure would only surface when someone opened the page.
  const isPng = bytes.length > 8 && bytes.subarray(0, 8).equals(
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  );
  if (!isPng) throw new Error('response decoded to something that is not a PNG');
  return bytes;
}

await mkdir(outDir, { recursive: true });

let generated = 0;
let skipped = 0;
let failed = 0;

for (const entry of ART) {
  const target = join(outDir, entry.file);
  // Checked against the compressed name, since that is what survives a run.
  if (!force && await exists(target.replace(/\.png$/, '.webp'))) {
    console.log(`skip  docs/img/${entry.file} (already present; --force to redo)`);
    skipped += 1;
    continue;
  }
  try {
    const bytes = await generate(entry);
    await writeFile(target, bytes);
    const webp = target.replace(/\.png$/, '.webp');
    const compressed = await compress(target, webp);
    console.log(compressed
      ? `write docs/img/${entry.file.replace(/\.png$/, '.webp')} `
        + `(${Math.round(compressed / 1024)} KiB, from ${Math.round(bytes.length / 1024)} KiB)`
      : `write docs/img/${entry.file} (${Math.round(bytes.length / 1024)} KiB)`);
    generated += 1;
  } catch (error) {
    console.error(`FAIL  docs/img/${entry.file}: ${error.message}`);
    failed += 1;
  }
}

console.log(`\n${generated} generated, ${skipped} skipped, ${failed} failed.`);
// A failure is reported but does not fail the run: the artwork is decoration, and the SVG banner the
// site actually depends on is committed.
process.exit(0);
