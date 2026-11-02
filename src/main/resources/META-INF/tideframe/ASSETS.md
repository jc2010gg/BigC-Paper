# Original asset inventory

Version 0.9.1 includes `src/main/resources/boss-face.png`, an original fantasy portrait generated with the built-in imagegen tool for this project on 2026-10-17, without an external reference image. The complete prompt and provenance are in `docs/BOSS_FACE_V091.md`. Its generated alpha is preserved. Operators can replace `plugins/Tideframe/boss-face.png`; replacement image rights remain with their respective holders and are not automatically relicensed by the project MIT notice. No existing game boss face or commercial plugin texture was downloaded or incorporated.

Version 0.9 adds `core/VisualMenuCatalog.java`: four original procedural images (coastal vault hero and three challenge cards), original layout and PNG examples. `pack/BossBarAssets.java` authors a rose crystal health fill, angular gold vines and guardian medallion in 21 states. The supplied reference informed only the broad concept; no reference pixels, character, branding or typography are embedded. Source and built-in art remain MIT. `tools/MakeBossPreview.java` and `tools/MakeVisualMenuPreview.java` export software previews, not Minecraft screenshots. Scaled HUD font variants reuse our existing PNG bytes and compensate native integer advances; they do not introduce shaders or proprietary resources.

All authored runtime classes are in `dev.tideframe`. No LibreUI, VoidRP, commercial plugin or third-party runtime implementation is included.

| Asset | Source | Ownership / generation |
|---|---|---|
| Game ASCII bitmap font | `pack/PackGenerator.java:pattern` | Handwritten 5x7 pixel rows; in the `ui` atlas lowercase repeats our uppercase. No system font rasterization. |
| Lower case, accented letters and symbols (0.21) | `pack/Glyphs.java` | Handwritten 5x7 rows for a-z, 48 accented letters (accent in the top two rows over a five-row body), ¡ ¿ º ª « » € × ÷ ± ² ³ and game symbols (★ ❤ ✔ ✖ arrows • …). Used by HUD canvases and live-text grids. No system or third-party font was read. |
| Screen HUD canvases | `pack/HudRaster.java`, `pack/HudAssets.java` | Original 256x144 raster, own text/geometric shapes, normal/hover/entry frames. Authorized PNG pixels retain their supplying rights. |
| Screen HUD cursor and spacing | `HudAssets` | Own 9x144 clipped cursor row textures and private-use quarter-unit positive/negative spacing. TITLE bitmap height36/ascent15; native glyphs, no shader override. |
| Legacy world cursor | `pack/PackGenerator.java` | Original geometric cross, U+E000. |
| Rectangle, rounded rectangle and ellipse fills/outlines | `PackGenerator` and `pack/ShapeGlyphs.java` | Own 16x16 geometric masks; rounded radius4, outlines from erosion. Glyphs U+E001 through U+E006. |
| Orbit PNG example | `tools/MakeImageDemo.java`, `examples/images/orbit.png` | Original mathematical ring/cross/bars, 48x32 RGBA, no external input. MIT. |
| Operator PNG glyphs | `core/ImageAsset.java`, `pack/ImageGlyphs.java` | Supplied by operator, bounded and canonicalized to metadata-free RGBA; SHA-256 identifies each resource. Rights remain with their original author. |
| Font atlas, cursor PNG, font JSON, metadata | `PackGenerator.generate` | Generated deterministically; original namespace `tideframe`. |
| Web TTF/WOFF | `tools/GlyphSheet.java`, `tools/make-web-font.py` | Contours derived only from our own glyph rows (PackGenerator and Glyphs). FontTools is external build tooling, not bundled in the plugin. Hashes in `editor/FONT_PROVENANCE.json`. |
| Layouts, labels, palette and UI name | `core/Screen.java`, `editor/*` | Authored for this project; no existing interface was traced or matched pixel by pixel. |

MIT license covers this project's authored plugin source, built-in assets and geometric examples. Loading a PNG does not automatically license it under MIT: operators must use their own authorized artwork and retain its applicable notices. Platform APIs, JDK, Gradle, test libraries, FontTools/Pillow and the Minecraft client/server are external dependencies and retain their own licenses. They are not claims of our authorship.

Native chat, action bars, boss bars and Bedrock fallback refer to base-game client resources. These are not redistributed. Installed ItemsAdder content is referenced through its API only; administrators must use their own authorized content. No provider texture/font/model is extracted, included in our pack or included in the deliverable. This distinction is necessary for C1/C2; it is not a claim to own Minecraft's native widgets.

The optional saved demo world is vanilla procedural save data generated locally with seed54321 by the official Paper26.2 server, without imported builds or packs. It is not a custom Minecraft client, texture collection or claim of authorship over Minecraft. Server/client software remains external and is not included in the plugin or source bundle.

Version0.4 uses native titles for menus/cursor; the legacy DisplayRenderer source
remains for audit history and is not instantiated by UiSession. No world entity is
created by the menu path. ItemsAdder ItemDisplay rendering is unavailable in this
HUD backend. Native title shadow/accessibility background and client alignment
still require visual acceptance. Metric inspection of the installed official26.2
client informed the font contract; no client classes, font pixels or shaders were
copied into project code/assets or redistributed.

Version0.5 adds the original angular coast example, authored as polygons/gradients
in tools/MakeImageDemo.java (PNG1024x576 and JPEG). Native HUD canvases are now
1024x576 at the same logical256x144 size, split into atlas-safe256x240 padded
tiles; curves use own subpixel coverage and restrained8% radii. Operators' photos
use original premultiplied-alpha bilinear filtering when fitted into HUD elements.
Input images retain their dimensions when already below the import cap. Photos
are decoded by their browser, resized and converted to PNG; those pixels retain
their original rights and are not automatically MIT. No Minecraft texture,
shader, font or commercial plugin asset is incorporated.

CameraPackets is original reflection/protocol glue. Inspection of already
installed official Minecraft/Paper binaries informed method/packet metadata;
no Minecraft or Paper implementation/classes are bundled. Camera and carrier
entities exist only in client packets and are never added to a server world.

Version0.6 adds original procedural Aurora Ridge art3840x2160 and a640x96
button strip, authored solely as gradients/polygons in tools/MakeAdvancedDemo.java.
The four advanced example layouts demonstrate36elements, translucent decoration,
shadows, stable layers and independent button action areas. No outside assets
or system font pixels are used. All newly authored artwork remains MIT.

Source raster quality is selectable per screen: Low512x288, Medium1920x1080,
High2560x1440, or bounded16:9 Custom up to3840x2160. These numbers describe
worker-generated source textures, not physical display pixel coverage. Fonts
split the raster into atlas-safe pieces; native title placement still requires
Minecraft acceptance. Legacy0.5 layouts retain1024x576. Low omits entrance bake
and delay; the pack still includes every installed layout and is limited64MiB.
Operator photos remain subject to their own rights and are never relabeled MIT.
