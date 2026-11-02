"""Build our editor font only from Tideframe's own handwritten 5x7 strokes.

The strokes live in PackGenerator.java (upper-case ASCII, digits, punctuation) and Glyphs.java (real lower case,
accented letters, symbols). tools/GlyphSheet.java dumps them as JSON, so the editor preview draws exactly the
glyphs the game font draws.

Build dependency: fonttools==4.65.0; it is not shipped in the editor.
Run from repository root after compiling the plugin:
    java -cp build/classes/java/main tools/GlyphSheet.java build/glyphs.json
    python tools/make-web-font.py [build/glyphs.json]
"""
from pathlib import Path
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / ".font-tools"))
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

source = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build/glyphs.json"
sheet_bytes = source.read_bytes()
sheet = {int(code, 16): rows for code, rows in json.loads(sheet_bytes).items()}
assert 32 in sheet and len(sheet) >= 95 + 72, "Unexpected glyph sheet"
names = {code: ("space" if code == 32 else f"uni{code:04X}") for code in sorted(sheet)}
glyphs, metrics = {}, {}
for code, name in [(None, ".notdef")] + list(names.items()):
    pen = TTGlyphPen(None)
    if code is not None:
        rows = sheet[code].split("/")
        assert len(rows) == 7 and all(len(row) == 5 for row in rows), f"glyph {code:04x}"
        for row, bits in enumerate(rows):
            for column, bit in enumerate(bits):
                if bit == "1":
                    x, y = column * 100, (6 - row) * 100
                    pen.moveTo((x, y)); pen.lineTo((x + 100, y))
                    pen.lineTo((x + 100, y + 100)); pen.lineTo((x, y + 100)); pen.closePath()
    glyphs[name] = pen.glyph()
    metrics[name] = (400 if code == 32 else 600, 0)
builder = FontBuilder(1000, isTTF=True)
builder.setupGlyphOrder([".notdef"] + list(names.values()))
builder.setupCharacterMap({code: name for code, name in names.items()})
builder.setupGlyf(glyphs)
builder.setupHorizontalMetrics(metrics)
builder.setupHorizontalHeader(ascent=800, descent=-200)
builder.setupNameTable({"familyName": "Tideframe Grid", "styleName": "Regular",
    "uniqueFontIdentifier": "TideframeGrid-Original-2", "fullName": "Tideframe Grid Regular",
    "psName": "TideframeGrid-Regular", "version": "Version 2.0",
    "copyright": "Copyright 2026 Tideframe contributors. Original Tideframe bitmap strokes.",
    "licenseDescription": "MIT License. See the repository LICENSE."})
builder.setupOS2(sTypoAscender=800, sTypoDescender=-200, usWinAscent=800, usWinDescent=200)
builder.setupPost()
builder.setupMaxp()
builder.font["head"].created = builder.font["head"].modified = 2082844800  # 1970-01-01
builder.font.recalcTimestamp = False
target = ROOT / "editor"
target.mkdir(exist_ok=True)
builder.save(target / "tideframe.ttf")
builder.font.flavor = "woff"
builder.font.save(target / "tideframe.woff")
manifest = {"source": "PackGenerator.java and Glyphs.java handwritten strokes -> tools/GlyphSheet.java -> glyphs.json",
    "glyphSheetSha256": hashlib.sha256(sheet_bytes).hexdigest(), "license": "MIT", "glyphs": len(sheet),
    "fontToolsBuildVersion": "4.65.0", "containsSystemFontData": False,
    "files": {name: hashlib.sha256((target / name).read_bytes()).hexdigest()
        for name in ["tideframe.ttf", "tideframe.woff"]}}
(target / "FONT_PROVENANCE.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
print(json.dumps(manifest, indent=2))
