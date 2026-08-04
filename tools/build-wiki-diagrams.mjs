#!/usr/bin/env node
/**
 * Generates the wiki's diagrams as SVG into docs/img/.
 *
 * SVG rather than screenshots because these carry meaning that must stay correct: a flow diagram showing
 * the wrong hatch order is worse than no diagram. Being text, they diff in review, scale on any display,
 * and need no binary in the repository.
 *
 * Usage: node tools/build-wiki-diagrams.mjs
 * Writes: docs/img/*.svg
 */

import { mkdir, writeFile } from 'node:fs/promises';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = join(root, 'docs', 'img');

const INK = '#102a2b';
const INK_SOFT = '#2d4a49';
const PAPER = '#f3f0e7';
const PAPER_DEEP = '#e7e0d0';
const SIGNAL = '#e67532';
const MINT = '#85c8b2';

const FONT = 'IBM Plex Mono, ui-monospace, monospace';

/** A titled frame every diagram shares, so they read as one family. */
function frame(width, height, title, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}" role="img" aria-label="${escapeAttr(title)}">
  <rect width="${width}" height="${height}" fill="${PAPER}"/>
  <text x="24" y="34" font-family="${FONT}" font-size="13" font-weight="600" fill="${SIGNAL}" letter-spacing="1.6">${escapeText(title.toUpperCase())}</text>
  <line x1="24" y1="46" x2="${width - 24}" y2="46" stroke="${INK}" stroke-opacity="0.22"/>
${body}
</svg>
`;
}

function escapeText(raw) {
  return String(raw).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeAttr(raw) {
  return escapeText(raw).replace(/"/g, '&quot;');
}

function box(x, y, w, h, label, sublabel, fill) {
  const lines = [
    `  <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="4" fill="${fill || PAPER_DEEP}" stroke="${INK}" stroke-opacity="0.35"/>`,
    `  <text x="${x + w / 2}" y="${y + (sublabel ? h / 2 - 4 : h / 2 + 5)}" text-anchor="middle" font-family="${FONT}" font-size="13" font-weight="600" fill="${INK}">${escapeText(label)}</text>`,
  ];
  if (sublabel) {
    lines.push(`  <text x="${x + w / 2}" y="${y + h / 2 + 14}" text-anchor="middle" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">${escapeText(sublabel)}</text>`);
  }
  return lines.join('\n');
}

function arrow(x1, y1, x2, y2, label) {
  const midX = (x1 + x2) / 2;
  const midY = (y1 + y2) / 2;
  return [
    `  <line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${INK}" stroke-opacity="0.55" stroke-width="1.6" marker-end="url(#arrowhead)"/>`,
    label
      ? `  <text x="${midX}" y="${midY - 7}" text-anchor="middle" font-family="${FONT}" font-size="10" fill="${INK_SOFT}">${escapeText(label)}</text>`
      : '',
  ].filter(Boolean).join('\n');
}

const ARROWHEAD = `  <defs>
    <marker id="arrowhead" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
      <path d="M 0 0 L 10 5 L 0 10 z" fill="${INK}" fill-opacity="0.55"/>
    </marker>
  </defs>`;

// --- diagrams ----------------------------------------------------------------------------------

/**
 * The hatch flow, in the order the code actually performs it.
 *
 * The order is the point: the roll happens before the egg is removed, which is why a failed roll never
 * consumes the egg.
 */
function hatchFlow() {
  const y = 90;
  const h = 62;
  const w = 150;
  const gap = 38;
  const steps = [
    ['Hold egg', '/pet hatch main'],
    ['Escrow PREPARED', 'payment recorded'],
    ['Roll outcome', 'rarity + stats'],
    ['Remove egg', 'only if roll ok'],
    ['COMMITTED', 'countdown starts'],
  ];

  let body = ARROWHEAD;
  steps.forEach((step, index) => {
    const x = 24 + index * (w + gap);
    const fill = index === steps.length - 1 ? MINT : PAPER_DEEP;
    body += '\n' + box(x, y, w, h, step[0], step[1], fill);
    if (index > 0) {
      body += '\n' + arrow(x - gap + 4, y + h / 2, x - 4, y + h / 2);
    }
  });

  body += `\n  <text x="24" y="${y + h + 34}" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">The roll runs before the egg leaves your inventory, so a failed roll never costs you the egg.</text>`;
  body += `\n  <text x="24" y="${y + h + 52}" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">The countdown and the claim button stay locked until escrow reads COMMITTED.</text>`;

  return frame(24 + steps.length * (w + gap) + 24 - gap, y + h + 76, 'Hatching an egg', body);
}

/** Where a placed egg lives, and why breaking it cannot duplicate it. */
function placedEggFlow() {
  let body = ARROWHEAD;
  const w = 176;
  const h = 62;

  body += '\n' + box(24, 90, w, h, 'Place egg', 'beside heat');
  body += '\n' + box(24 + w + 46, 90, w, h, 'Block record', 'data/placed-eggs/');
  body += '\n' + box(24 + 2 * (w + 46), 90, w, h, 'Hologram', 'derived, never saved');
  body += '\n' + box(24 + w + 46, 188, w, h, 'Break block', 'record deleted first', PAPER_DEEP);
  body += '\n' + box(24 + 2 * (w + 46), 188, w, h, 'Egg returned', 'same egg, same id', MINT);

  body += '\n' + arrow(24 + w + 4, 121, 24 + w + 42, 121);
  body += '\n' + arrow(24 + 2 * w + 50, 121, 24 + 2 * w + 88, 121);
  body += '\n' + arrow(24 + w + 46 + w / 2, 152, 24 + w + 46 + w / 2, 184, 'break');
  body += '\n' + arrow(24 + 2 * w + 50, 219, 24 + 2 * w + 88, 219);

  body += `\n  <text x="24" y="284" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">The record is the single owner. Breaking deletes it before the item exists again, so a</text>`;
  body += `\n  <text x="24" y="302" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">break-and-replace cycle cannot produce a second egg.</text>`;
  body += `\n  <text x="24" y="326" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">Holograms are rebuilt from records on chunk load, so a crash leaves nothing to clean up.</text>`;

  return frame(24 + 3 * w + 2 * 46 + 24, 350, 'A placed egg', body);
}

/** Which config section fails closed and which degrades — the distinction operators most need. */
function configStrictness() {
  let body = ARROWHEAD;
  const w = 250;
  const h = 96;

  body += '\n' + box(24, 80, w, h, 'storage:', 'strict', '#f6d5c4');
  body += `\n  <text x="${24 + w / 2}" y="204" text-anchor="middle" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">A bad value stops startup.</text>`;
  body += `\n  <text x="${24 + w / 2}" y="222" text-anchor="middle" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">Capacity and prices are about</text>`;
  body += `\n  <text x="${24 + w / 2}" y="240" text-anchor="middle" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">someone's pets and money.</text>`;

  const x2 = 24 + w + 60;
  body += '\n' + box(x2, 80, w, h, 'everything else', 'lenient', '#d3eadf');
  body += `\n  <text x="${x2 + w / 2}" y="204" text-anchor="middle" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">A bad value warns and falls back</text>`;
  body += `\n  <text x="${x2 + w / 2}" y="222" text-anchor="middle" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">for that one key. A typo in a page</text>`;
  body += `\n  <text x="${x2 + w / 2}" y="240" text-anchor="middle" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">size never costs anyone a pet.</text>`;

  return frame(24 + 2 * w + 60 + 24, 274, 'Two kinds of config section', body);
}

/** The 27-slot hub, so an operator restyling it can see what they are moving. */
function hubLayout() {
  const cell = 44;
  const pad = 3;
  const originX = 24;
  const originY = 70;
  const labels = { 11: 'Vault', 13: 'Hatch', 15: 'Slots', 22: 'Help', 26: 'Studio' };

  let body = '';
  for (let slot = 0; slot < 27; slot += 1) {
    const col = slot % 9;
    const row = Math.floor(slot / 9);
    const x = originX + col * (cell + pad);
    const y = originY + row * (cell + pad);
    const label = labels[slot];
    body += `\n  <rect x="${x}" y="${y}" width="${cell}" height="${cell}" rx="3" fill="${label ? MINT : PAPER_DEEP}" stroke="${INK}" stroke-opacity="0.28"/>`;
    body += `\n  <text x="${x + 4}" y="${y + 13}" font-family="${FONT}" font-size="9" fill="${INK_SOFT}">${slot}</text>`;
    if (label) {
      body += `\n  <text x="${x + cell / 2}" y="${y + cell / 2 + 9}" text-anchor="middle" font-family="${FONT}" font-size="10" font-weight="600" fill="${INK}">${escapeText(label)}</text>`;
    }
  }

  const height = originY + 3 * (cell + pad) + 62;
  body += `\n  <text x="${originX}" y="${height - 34}" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">Slot numbers are what gui.menus.hub.buttons.&lt;name&gt;.slot expects. Buttons are named,</text>`;
  body += `\n  <text x="${originX}" y="${height - 16}" font-family="${FONT}" font-size="11" fill="${INK_SOFT}">so moving one is a slot change rather than a rewrite: vault, hatch, slots, help, studio.</text>`;

  return frame(originX * 2 + 9 * (cell + pad), height, 'Hub layout', body);
}


/**
 * The wiki masthead.
 *
 * Drawn rather than generated by an image model: the environment's Gemini key is on a free tier with zero
 * image quota, and a vector masthead is sharper on any display, diffs in review, and needs no binary in
 * the repository.
 */
function banner() {
  const width = 1200;
  const height = 300;
  let body = `  <defs>
    <linearGradient id="sky" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#0f2a2b"/>
      <stop offset="100%" stop-color="#1c4442"/>
    </linearGradient>
    <radialGradient id="glow" cx="50%" cy="50%">
      <stop offset="0%" stop-color="${MINT}" stop-opacity="0.85"/>
      <stop offset="100%" stop-color="${MINT}" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="ember" cx="50%" cy="50%">
      <stop offset="0%" stop-color="${SIGNAL}" stop-opacity="0.7"/>
      <stop offset="100%" stop-color="${SIGNAL}" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="${width}" height="${height}" fill="url(#sky)"/>
  <circle cx="880" cy="150" r="190" fill="url(#glow)"/>
  <circle cx="300" cy="250" r="150" fill="url(#ember)"/>`;

  // The egg: the one object every page is ultimately about.
  body += `
  <ellipse cx="880" cy="168" rx="52" ry="66" fill="${PAPER}" fill-opacity="0.92"/>
  <ellipse cx="880" cy="168" rx="52" ry="66" fill="none" stroke="${MINT}" stroke-width="2.5"/>
  <path d="M 852 150 q 28 -22 56 0" fill="none" stroke="${MINT}" stroke-width="2.5" stroke-linecap="round"/>
  <path d="M 856 186 q 24 20 48 0" fill="none" stroke="${SIGNAL}" stroke-width="2.5" stroke-linecap="round"/>
  <rect x="820" y="238" width="120" height="14" rx="3" fill="${PAPER}" fill-opacity="0.22"/>`;

  // Two torches, the ordinary heat source a placed egg wants.
  [
    [1010, 205],
    [752, 205],
  ].forEach(([x, y]) => {
    body += `
  <rect x="${x - 4}" y="${y}" width="8" height="42" rx="2" fill="${PAPER}" fill-opacity="0.35"/>
  <circle cx="${x}" cy="${y - 6}" r="9" fill="${SIGNAL}" fill-opacity="0.9"/>
  <circle cx="${x}" cy="${y - 6}" r="20" fill="url(#ember)"/>`;
  });

  body += `
  <text x="72" y="132" font-family="Fraunces, Georgia, serif" font-size="58" font-weight="750" fill="${PAPER}">OmniPet</text>
  <text x="76" y="170" font-family="${FONT}" font-size="15" fill="${MINT}" letter-spacing="3.4">FIELD MANUAL</text>
  <text x="76" y="206" font-family="${FONT}" font-size="13" fill="${PAPER}" fill-opacity="0.62">Install &#183; Play &#183; Configure &#183; Administer</text>
  <text x="76" y="228" font-family="${FONT}" font-size="13" fill="${PAPER}" fill-opacity="0.62">English &#183; Ti&#7871;ng Vi&#7879;t</text>`;

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}" role="img" aria-label="OmniPet field manual">
${body}
</svg>
`;
}

const diagrams = {
  'wiki-banner.svg': banner(),
  'hatch-flow.svg': hatchFlow(),
  'placed-egg.svg': placedEggFlow(),
  'config-strictness.svg': configStrictness(),
  'hub-layout.svg': hubLayout(),
};

await mkdir(outDir, { recursive: true });
for (const [name, svg] of Object.entries(diagrams)) {
  await writeFile(join(outDir, name), svg, 'utf8');
  console.log(`docs/img/${name}`);
}
