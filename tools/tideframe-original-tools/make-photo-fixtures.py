"""Reproducible original geometric browser-import fixtures; no external photo/font.

Requires Pillow. Generated files belong in build/, not the plugin resource pack.
Run from the project root: python tools/make-photo-fixtures.py
"""
from pathlib import Path
import hashlib
import json
import sys
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build" / "photo-fixtures"
OUT.mkdir(parents=True, exist_ok=True)
width, height = 1920, 1080
art = Image.new("RGB", (width, height))
pixels = art.load()
for y in range(height):
    for x in range(width):
        pixels[x, y] = (20 + 170 * x // width, 45 + 135 * y // height, 95 + 90 * (x + y) // (width + height))
draw = ImageDraw.Draw(art)
draw.polygon([(0, 850), (430, 280), (820, 850), (1250, 170), (1920, 900), (1920, 1080), (0, 1080)], fill=(31, 64, 78))
draw.polygon([(800, 790), (1250, 170), (1430, 365), (1230, 330), (1120, 500)], fill=(187, 218, 215))
draw.rectangle((0, 920, 1919, 1079), fill=(42, 116, 133))
for x, y, length in [(910, 960, 180), (1110, 1000, 260), (570, 1030, 180)]:
    draw.rectangle((x, y, x + length, y + 3), fill=(154, 209, 209))

rows = []
def record(path, mode, *, animated=False):
    with Image.open(path) as decoded:
        rows.append({"file": str(path.relative_to(ROOT)).replace("\\", "/"), "format": decoded.format,
                     "width": decoded.width, "height": decoded.height, "mode": mode,
                     "frames": getattr(decoded, "n_frames", 1), "animated": animated,
                     "bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})

for suffix, fmt, kwargs in [("jpg", "JPEG", {"quality": 94, "progressive": True}),
                            ("webp", "WEBP", {"quality": 94}), ("bmp", "BMP", {}), ("tiff", "TIFF", {"compression": "tiff_deflate"})]:
    path = OUT / f"own-landscape.{suffix}"
    art.save(path, format=fmt, **kwargs)
    record(path, "RGB")

alpha = art.convert("RGBA")
alpha_pixels = alpha.load()
for y in range(height):
    for x in range(width):
        r, g, b, _ = alpha_pixels[x, y]
        alpha_pixels[x, y] = (r, g, b, x * 255 // (width - 1))
path = OUT / "own-alpha.png"
alpha.save(path, "PNG")
record(path, "RGBA")

second_frame = art.copy()
ImageDraw.Draw(second_frame).rectangle((40, 40, 240, 240), fill=(255, 95, 120))
path = OUT / "own-animation.gif"
art.save(path, "GIF", save_all=True, append_images=[second_frame], duration=[150, 150], loop=0)
record(path, "P", animated=True)

Image.init()
avif = "AVIF" in Image.SAVE
if avif:
    path = OUT / "own-landscape.avif"
    art.save(path, "AVIF", quality=94)
    record(path, "RGB")

(OUT / "own-rejected.svg").write_text('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#2a7485"/></svg>', encoding="utf-8")
manifest = {"generator": "tools/make-photo-fixtures.py", "provenance": "Original mathematical gradients and polygons; no external pixels or fonts.",
            "pillow": Image.__version__, "expectedFit": {"width": 1024, "height": 576}, "avifEncoderAvailable": avif, "fixtures": rows}
if "--limits" in sys.argv:
    path = OUT / "own-over-40mp.png"
    giant = Image.new("RGB", (8001, 5000), (31, 64, 78))
    giant.save(path, "PNG")
    del giant
    record(path, "RGB")
    rows[-1]["expectedError"] = "40 megapixels"
    path = OUT / "own-over-40mib.jpg"
    with path.open("wb") as stream:
        stream.write(bytes([255, 216, 255, 224]))
        stream.seek(41943040)
        stream.write(b"\0")
    rows.append({"file": str(path.relative_to(ROOT)).replace("\\", "/"), "bytes": path.stat().st_size,
                 "provenance": "Deliberately invalid oversized test input; size guard must reject before decoding.", "expectedError": "40 MiB"})
target = ROOT / "evidence" / "photos-v05-fixtures.json"
target.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
print(json.dumps(manifest, indent=2))
