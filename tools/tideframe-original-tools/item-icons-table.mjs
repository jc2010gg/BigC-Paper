#!/usr/bin/env node
// Builds src/main/resources/item-icons.tsv, the table behind the element "item" (item icons drawn from the player's
// own client textures; the pack only names them). For every item whose inventory icon is one plain texture it records
// the texture path, its size, its animation rows and how many columns its first frame paints: the client derives a
// glyph's advance from that, and the plugin must cancel the advance exactly. Names and numbers only, no pixels.
//
// Input: the item -> texture list written by tools/extract-item-icons.mjs from the same client jar.
// Usage: node tools/item-icons-table.mjs <icons.json> [client.jar] [out.tsv]
import { readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { openJar, defaultClientJar, decodePng, inkWidth } from '../editor/client-jar.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const [listPath, jarPath = defaultClientJar(), outPath = path.join(root, 'src', 'main', 'resources', 'item-icons.tsv')] = process.argv.slice(2);
if (!listPath) { console.error('Usage: node tools/item-icons-table.mjs <icons.json> [client.jar] [out.tsv]'); process.exit(2); }

const list = JSON.parse(readFileSync(listPath, 'utf8'));
const jar = openJar(readFileSync(jarPath));
const lines = [
  `# Item icons for the element "item" (Tideframe). Measured from the ${list.source ?? 'client jar'}: the texture each item's inventory icon uses,`,
  '# its size, animation rows (frames stacked vertically; the first is drawn) and painted columns of that frame. Nothing is copied:',
  '# the pack names minecraft:<texture>.png and the client draws its own file. Flags: b = the game shows a 3D block (drawn as one face),',
  '# t = the game tints it (drawn without that colour). Rebuild: tools/extract-item-icons.mjs, then tools/item-icons-table.mjs.',
  '# item\ttexture\twidth\theight\trows\tink\tflags'];
let skipped = 0;
for (const [item, entry] of Object.entries(list.items).sort(([a], [b]) => a.localeCompare(b))) {
  const texture = String(entry.texture ?? '').replace(/^minecraft:/, '');
  // Special models (beds, chests...) and in-hand variants would show the wrong picture; wide textures are block parts.
  if (!entry.exists || entry.complex && entry.kind !== 'flat' || texture.includes('_in_hand') || !/^[a-z0-9_/]+$/.test(texture)) { skipped++; continue; }
  const png = jar.read(`assets/minecraft/textures/${texture}.png`);
  if (!png) { skipped++; continue; }
  const image = decodePng(png);
  if (image.width !== 16 || image.height % image.width) { skipped++; continue; }
  const rows = image.height / image.width, ink = inkWidth(image, image.width, image.width);
  if (ink === 0) { skipped++; continue; }
  const flags = (entry.kind === 'block-face' ? 'b' : '') + (entry.tinted ? 't' : '');
  lines.push([item, texture, image.width, image.height, rows, ink, flags].join('\t'));
}
writeFileSync(outPath, lines.join('\n') + '\n', 'utf8');
console.log(`${lines.length - 5} items written to ${outPath}; ${skipped} skipped`);
