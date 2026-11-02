// Draws every icon of editor/icon-library.mjs on one PNG, over a dark and a light band, to look at the set together.
// Run: node tools/icon-sheet.mjs out.png [scale]
import { writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { ICONS, iconRgba } from '../editor/icon-library.mjs';

const target = process.argv[2] ?? 'icon-sheet.png', scale = Number(process.argv[3] ?? 6), columns = 7, cell = 16 * scale + 16;
const rows = Math.ceil(ICONS.length / columns), width = columns * cell + 16, height = rows * cell * 2 + 32;
const image = new Uint8Array(width * height * 4);
for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
  const light = y >= height / 2, i = (y * width + x) * 4;
  image.set(light ? [214, 222, 230, 255] : [16, 29, 37, 255], i);
}
ICONS.forEach((icon, index) => {
  const { width: side, data } = iconRgba(icon.id, scale);
  for (const band of [0, 1]) {
    const left = 16 + (index % columns) * cell, top = 16 + Math.floor(index / columns) * cell + band * (rows * cell + 16);
    for (let y = 0; y < side; y++) for (let x = 0; x < side; x++) {
      const s = (y * side + x) * 4;
      if (data[s + 3]) image.set([data[s], data[s + 1], data[s + 2], 255], ((top + y) * width + left + x) * 4);
    }
  }
});
const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = bytes => { let c = 0xffffffff; for (const b of bytes) c = crcTable[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const out = Buffer.alloc(12 + data.length), body = Buffer.concat([Buffer.from(type, 'ascii'), Buffer.from(data)]);
  out.writeUInt32BE(data.length, 0); body.copy(out, 4); out.writeUInt32BE(crc(body), 8 + data.length); return out;
}
const header = Buffer.alloc(13);
header.writeUInt32BE(width, 0); header.writeUInt32BE(height, 4); header[8] = 8; header[9] = 6;
const raw = Buffer.alloc((width * 4 + 1) * height);
for (let y = 0; y < height; y++) { raw[y * (width * 4 + 1)] = 0; Buffer.from(image.buffer, y * width * 4, width * 4).copy(raw, y * (width * 4 + 1) + 1); }
writeFileSync(target, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
console.log(`wrote ${ICONS.length} icons to ${target}`);
