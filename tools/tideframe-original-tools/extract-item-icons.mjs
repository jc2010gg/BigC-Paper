#!/usr/bin/env node
// Maps every item id of a Minecraft client jar to the flat texture its inventory icon uses.
// The jar is read into memory only; nothing inside it is extracted or written.
// Built-in modules only.
// Usage: node tools/extract-item-icons.mjs [jarPath] [outDir]  (writes icons.json for tools/item-icons-table.mjs)
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const DEFAULT_JAR = 'C:\\Users\\LEGION\\AppData\\Roaming\\.minecraft\\versions\\26.2\\26.2.jar';
const SOURCE_LABEL = '26.2 client jar';
const ITEM_PREFIX = 'assets/minecraft/items/';
const ITEM_FILE_RE = /^assets\/minecraft\/items\/[^/]+\.json$/;
const BLOCK_FACE_KEYS = ['all', 'side', 'front', 'top', 'particle', 'texture', 'cross', 'end'];
const PNG_SIGNATURE = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

const SIG_LOCAL = 0x04034b50;
const SIG_CENTRAL = 0x02014b50;
const SIG_EOCD = 0x06054b50;
const SIG_ZIP64_EOCD = 0x06064b50;
const SIG_ZIP64_LOCATOR = 0x07064b50;

const scriptDir = path.dirname(path.resolve(process.argv[1]));
const jarPath = path.resolve(process.argv[2] ?? DEFAULT_JAR);
const outDir = path.resolve(process.argv[3] ?? scriptDir);

const jar = readFileSync(jarPath);

// ---------- zip reading (central directory, stored and deflated entries) ----------

function findEocd(buf) {
  const lowest = Math.max(0, buf.length - 22 - 0xffff);
  for (let i = buf.length - 22; i >= lowest; i--) {
    if (buf.readUInt32LE(i) === SIG_EOCD && i + 22 + buf.readUInt16LE(i + 20) === buf.length) return i;
  }
  throw new Error('end of central directory not found (not a zip file)');
}

function readCentralDirectory(buf) {
  const eocd = findEocd(buf);
  let count = buf.readUInt16LE(eocd + 10);
  let cdOffset = buf.readUInt32LE(eocd + 16);
  if (count === 0xffff || cdOffset === 0xffffffff) {
    const locator = eocd - 20;
    if (locator < 0 || buf.readUInt32LE(locator) !== SIG_ZIP64_LOCATOR) throw new Error('zip64 locator missing');
    const record = Number(buf.readBigUInt64LE(locator + 8));
    if (buf.readUInt32LE(record) !== SIG_ZIP64_EOCD) throw new Error('zip64 end record missing');
    count = Number(buf.readBigUInt64LE(record + 32));
    cdOffset = Number(buf.readBigUInt64LE(record + 48));
  }

  const entries = new Map();
  let duplicates = 0;
  let p = cdOffset;
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(p) !== SIG_CENTRAL) throw new Error(`bad central directory header #${i} at ${p}`);
    const method = buf.readUInt16LE(p + 10);
    const crc = buf.readUInt32LE(p + 16);
    let compSize = buf.readUInt32LE(p + 20);
    let size = buf.readUInt32LE(p + 24);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    let localOffset = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nameLen);

    if (size === 0xffffffff || compSize === 0xffffffff || localOffset === 0xffffffff) {
      // zip64 extra field: the 64-bit values appear in this order, only for the fields set to 0xffffffff
      for (let q = p + 46 + nameLen, end = q + extraLen; q + 4 <= end; ) {
        const id = buf.readUInt16LE(q);
        const len = buf.readUInt16LE(q + 2);
        if (id === 0x0001) {
          let r = q + 4;
          if (size === 0xffffffff) { size = Number(buf.readBigUInt64LE(r)); r += 8; }
          if (compSize === 0xffffffff) { compSize = Number(buf.readBigUInt64LE(r)); r += 8; }
          if (localOffset === 0xffffffff) localOffset = Number(buf.readBigUInt64LE(r));
          break;
        }
        q += 4 + len;
      }
    }

    if (entries.has(name)) duplicates++;
    else entries.set(name, { name, method, crc, size, compSize, localOffset });
    p += 46 + nameLen + extraLen + commentLen;
  }
  return { entries, duplicates };
}

function entryBytes(entry) {
  const lh = entry.localOffset;
  if (jar.readUInt32LE(lh) !== SIG_LOCAL) throw new Error(`bad local header for ${entry.name}`);
  const start = lh + 30 + jar.readUInt16LE(lh + 26) + jar.readUInt16LE(lh + 28);
  if (start + entry.compSize > jar.length) throw new Error(`truncated data for ${entry.name}`);
  return jar.subarray(start, start + entry.compSize);
}

// Whole entry content, verified against the stored size and CRC-32.
function readFull(entry) {
  const raw = entryBytes(entry);
  let data;
  if (entry.method === 0) data = raw;
  else if (entry.method === 8) data = zlib.inflateRawSync(raw);
  else throw new Error(`unsupported compression method ${entry.method} for ${entry.name}`);
  if (data.length !== entry.size) throw new Error(`size mismatch for ${entry.name}`);
  if (zlib.crc32(data) !== entry.crc) throw new Error(`CRC mismatch for ${entry.name}`);
  return data;
}

function readText(entry) {
  return readFull(entry).toString('utf8').replace(/^\uFEFF/, '');
}

// First n bytes of an entry, inflating only as much of a deflate stream as is needed.
function readHead(entry, n) {
  const raw = entryBytes(entry);
  if (entry.method === 0) return raw.subarray(0, n);
  if (entry.method !== 8) throw new Error(`unsupported compression method ${entry.method} for ${entry.name}`);
  try {
    const part = zlib.inflateRawSync(raw.subarray(0, 4096), { finishFlush: zlib.constants.Z_SYNC_FLUSH });
    if (part.length >= n) return part.subarray(0, n);
  } catch {
    // fall through to a full inflate
  }
  return zlib.inflateRawSync(raw).subarray(0, n);
}

// Width and height from the PNG IHDR chunk, or null when the entry is not a readable PNG.
function pngSize(entry) {
  const head = readHead(entry, 24);
  if (head.length < 24 || !PNG_SIGNATURE.every((b, i) => head[i] === b) || head.toString('latin1', 12, 16) !== 'IHDR') {
    return null;
  }
  return { width: head.readUInt32BE(16), height: head.readUInt32BE(20) };
}

// ---------- model and item-definition helpers ----------

function splitRef(ref) {
  const colon = ref.indexOf(':');
  return colon < 0 ? { ns: 'minecraft', rel: ref } : { ns: ref.slice(0, colon), rel: ref.slice(colon + 1) };
}

function isModelNode(node) {
  return node !== null && typeof node === 'object' && (node.type === 'minecraft:model' || node.type === 'model');
}

// Depth-first search for the first node that pick() accepts; a "fallback" key is searched before the other keys.
function findInTree(node, pick, depth = 0) {
  if (node === null || typeof node !== 'object' || depth > 64) return null;
  const direct = pick(node);
  if (direct) return direct;
  if (Array.isArray(node)) {
    for (const child of node) {
      const found = findInTree(child, pick, depth + 1);
      if (found) return found;
    }
    return null;
  }
  const keys = Object.keys(node).sort((a, b) => (b === 'fallback') - (a === 'fallback'));
  for (const key of keys) {
    const found = findInTree(node[key], pick, depth + 1);
    if (found) return found;
  }
  return null;
}

const pickModel = (node) => (isModelNode(node) && typeof node.model === 'string' ? node.model : null);
// Special item models (chest, bed, shield, ...) name their base model in "base".
const pickBase = (node) => (typeof node.base === 'string' ? node.base : null);

function containsType(node, type, depth = 0) {
  if (node === null || typeof node !== 'object' || depth > 64) return false;
  if (node.type === type) return true;
  return Object.values(node).some((child) => containsType(child, type, depth + 1));
}

function hasTintsArray(node, depth = 0) {
  if (node === null || typeof node !== 'object' || depth > 64) return false;
  return Object.entries(node).some(
    ([key, value]) => (key === 'tints' && Array.isArray(value)) || hasTintsArray(value, depth + 1),
  );
}

function hasTintindex(model) {
  if (!Array.isArray(model.elements)) return false;
  return model.elements.some(
    (element) =>
      element !== null &&
      typeof element === 'object' &&
      element.faces !== null &&
      typeof element.faces === 'object' &&
      Object.values(element.faces).some((face) => face !== null && typeof face === 'object' && 'tintindex' in face),
  );
}

// ---------- main ----------

function main() {
  const { entries, duplicates } = readCentralDirectory(jar);
  const itemFiles = [...entries.values()]
    .filter((entry) => ITEM_FILE_RE.test(entry.name))
    .sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));

  const modelCache = new Map();
  const pngCache = new Map();
  const animCache = new Map();
  const warnings = [];
  const mcmetaOnly = [];

  // A texture is animated when its .mcmeta has an "animation" section. Some textures (e.g. block/glass)
  // only have a .mcmeta for other settings, so the file's existence alone is not enough.
  const isAnimated = (texFile) => {
    if (!animCache.has(texFile)) {
      const meta = entries.get(`${texFile}.mcmeta`);
      let animated = false;
      if (meta) {
        try {
          const json = JSON.parse(readText(meta));
          animated = json !== null && typeof json.animation === 'object' && json.animation !== null;
          if (!animated) mcmetaOnly.push(texFile);
        } catch (err) {
          warnings.push(`${texFile}.mcmeta: ${err.message}`);
        }
      }
      animCache.set(texFile, animated);
    }
    return animCache.get(texFile);
  };

  const loadModel = (ns, rel) => {
    const key = `${ns}:${rel}`;
    if (!modelCache.has(key)) {
      const entry = entries.get(`assets/${ns}/models/${rel}.json`);
      modelCache.set(key, entry ? JSON.parse(readText(entry)) : null);
    }
    return modelCache.get(key);
  };

  // Follows "parent" links starting at the item model (child first), at most 10 levels above it.
  const modelChain = (ref) => {
    const chain = [];
    const seen = new Set();
    let current = ref;
    let missing = null;
    while (current) {
      const { ns, rel } = splitRef(current);
      if (ns === 'minecraft' && rel.startsWith('builtin/')) break;
      const key = `${ns}:${rel}`;
      if (seen.has(key) || chain.length > 10) {
        missing = `model chain loops or is deeper than 10 levels at ${key}`;
        break;
      }
      seen.add(key);
      const model = loadModel(ns, rel);
      if (!model) {
        missing = `model file not found: ${key}`;
        break;
      }
      chain.push(model);
      current = typeof model.parent === 'string' ? model.parent : null;
    }
    return { chain, missing };
  };

  // Resolves a texture value to "namespace:path". A value may be a string, a "#name" reference, or an
  // object such as {"sprite": "minecraft:block/glass", "force_translucent": true}.
  const resolveTexture = (value, textures) => {
    let current = value;
    for (let hops = 0; hops <= 10; hops++) {
      if (current !== null && typeof current === 'object' && typeof current.sprite === 'string') {
        current = current.sprite;
      }
      if (typeof current !== 'string' || current === '') return null;
      if (!current.startsWith('#')) {
        const { ns, rel } = splitRef(current);
        return `${ns}:${rel}`;
      }
      current = textures.get(current.slice(1));
    }
    return null;
  };

  const findIcon = (chain) => {
    const textures = new Map();
    for (const model of chain) {
      if (model.textures !== null && typeof model.textures === 'object') {
        for (const [key, value] of Object.entries(model.textures)) {
          if (!textures.has(key)) textures.set(key, value);
        }
      }
    }
    if (textures.has('layer0')) {
      const tex = resolveTexture(textures.get('layer0'), textures);
      return tex ? { tex, kind: 'flat', key: 'layer0' } : { reason: 'layer0 does not resolve to a texture' };
    }
    for (const key of BLOCK_FACE_KEYS) {
      const tex = textures.has(key) ? resolveTexture(textures.get(key), textures) : null;
      if (tex) return { tex, kind: 'block-face', key };
    }
    return { reason: 'no layer0 or block face texture in model chain' };
  };

  const items = {};
  const unresolved = [];
  const reasons = {};
  const keyCounts = {};
  const specialKept = [];
  const particleKept = [];

  for (const entry of itemFiles) {
    const id = entry.name.slice(ITEM_PREFIX.length, -'.json'.length);
    try {
      const def = JSON.parse(readText(entry));
      const root = def.model;
      let modelRef = null;
      let complex = false;
      if (typeof root === 'string') {
        modelRef = root;
      } else if (isModelNode(root) && typeof root.model === 'string') {
        modelRef = root.model;
      } else if (root !== null && typeof root === 'object') {
        complex = true;
        modelRef = findInTree(root, pickModel) ?? findInTree(root, pickBase);
      }
      if (!modelRef) {
        throw new Error(root?.type ? `no model reference (type ${root.type})` : 'no model reference');
      }

      const { chain, missing } = modelChain(modelRef);
      if (chain.length === 0) throw new Error(missing ?? `${modelRef} is engine-built and has no texture file`);
      if (missing) warnings.push(`${id}: ${missing}`);

      const icon = findIcon(chain);
      if (!icon.tex) throw new Error(icon.reason);
      const special = containsType(def, 'minecraft:special');
      if (special && icon.key === 'particle') {
        throw new Error(`3D special model: only the particle texture ${icon.tex}, no flat icon`);
      }

      const { ns, rel } = splitRef(icon.tex);
      const texFile = `assets/${ns}/textures/${rel}.png`;
      if (!entries.has(texFile)) throw new Error(`texture ${icon.tex} has no PNG in the jar`);
      if (!pngCache.has(texFile)) pngCache.set(texFile, pngSize(entries.get(texFile)));
      const size = pngCache.get(texFile);
      if (!size) throw new Error(`texture ${icon.tex} is not a readable PNG`);

      items[id] = {
        texture: icon.tex,
        kind: icon.kind,
        width: size.width,
        height: size.height,
        animated: isAnimated(texFile),
        tinted: hasTintsArray(def) || chain.some(hasTintindex),
        complex,
        exists: true,
      };
      keyCounts[icon.key] = (keyCounts[icon.key] ?? 0) + 1;
      if (special) specialKept.push(`${id} (${icon.key})`);
      if (icon.key === 'particle') particleKept.push(id);
    } catch (err) {
      unresolved.push(id);
      reasons[id] = err.message;
    }
  }

  const sortedIds = Object.keys(items).sort();
  const sortedItems = {};
  for (const id of sortedIds) sortedItems[id] = items[id];
  unresolved.sort();
  const sortedReasons = {};
  for (const id of unresolved) sortedReasons[id] = reasons[id];

  const output = {
    source: SOURCE_LABEL,
    count: sortedIds.length,
    items: sortedItems,
    unresolved,
    reasons: sortedReasons,
  };
  mkdirSync(outDir, { recursive: true });
  const outFile = path.join(outDir, 'icons.json');
  writeFileSync(outFile, `${JSON.stringify(output, null, 2)}\n`, 'utf8');

  const all = Object.values(sortedItems);
  const countWhere = (pred) => all.filter(pred).length;
  console.log(`item definitions: ${itemFiles.length}`);
  console.log(`resolved: ${all.length}, unresolved: ${unresolved.length}`);
  console.log(`flat: ${countWhere((v) => v.kind === 'flat')}, block-face: ${countWhere((v) => v.kind === 'block-face')}`);
  console.log(
    `animated: ${countWhere((v) => v.animated)}, tinted: ${countWhere((v) => v.tinted)}, ` +
      `complex: ${countWhere((v) => v.complex)}, missing png: ${countWhere((v) => !v.exists)}`,
  );
  console.log(`icon texture key used: ${JSON.stringify(keyCounts)}`);
  console.log(`special models kept: ${specialKept.length ? specialKept.join(', ') : 'none'}`);
  console.log(`particle-keyed items kept: ${particleKept.length ? particleKept.join(', ') : 'none'}`);
  console.log(`mcmeta without animation section (not animated): ${mcmetaOnly.length ? mcmetaOnly.join(', ') : 'none'}`);
  console.log(`duplicate zip entry names skipped: ${duplicates}`);
  for (const w of warnings.slice(0, 20)) console.log(`warning: ${w}`);
  if (warnings.length > 20) console.log(`warning: ${warnings.length - 20} more not shown`);
  console.log(`wrote ${outFile}`);
}

main();
