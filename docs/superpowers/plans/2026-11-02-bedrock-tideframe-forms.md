# Bedrock Forms With The Tideframe Look

## Contract

Bedrock players keep Floodgate forms (Bedrock cannot draw the Java HUD), but the shop and missions forms look like
their Tideframe screens: same panel, row and text colours, item icons and progress bars. No other form changes.
Purchases and claims keep their services; nothing pays twice.

## Implementation

- [x] Phase 1, shop: `ui/server_form.json` adds a second factory; `ui/bigc_forms.json` draws a tagged long form
  with textures generated from `bigc_tienda`. Titles carry `BedrockFormStyle.TAG`. Rows get vanilla Bedrock icons
  (`BedrockItemIcons`, only paths listed from Mojang's bedrock-samples).
- [x] Phase 2, missions: `MissionBedrockForm` builds the Diarias/Semanales form with bars from `font/glyph_E5.png`;
  "Reclamar" opens the existing claims inventory. Answers after a reload are ignored.
- [x] Phase 3: tests for the form texts, icons, pack contract (tag, textures, colours, glyphs) and the full build.
- [ ] Acceptance on Bedrock clients (PC and phone) with Geyser/Floodgate. Not available on the development PC.

## Decisions

- The stock form is left untouched and covered, not hidden: modifying stock elements risks every form on the
  server, while a covering panel that fails to draw leaves the stock buttons working.
- The Bedrock pack version went to 2.1.0 so clients refresh their cached copy.
- Glyph page E5 is used only by these bars. Another pack with `font/glyph_E5.png` would replace it.
- Reference for the stock UI: Mojang/bedrock-samples `resource_pack/ui/server_form.json` and
  `ui_template_buttons.json` (variables `$default_button_texture`, `$hover_button_texture`, `$pressed_button_texture`).
