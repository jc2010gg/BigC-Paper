# Tideframe Embedded Module

## Contract

Compile the original Tideframe Java sources and resources into BigCasares. There is one Bukkit plugin descriptor,
one JavaPlugin (BigCasares), and no external Tideframe JAR or binary API dependency. Keep the editor's authored
sources in the repository. Preserve the engine API, screen IDs, pack namespace and licensed assets.

## Implementation

- [x] Import original engine, tests, namespaced resources, editor and license notices, excluding build caches.
- [x] Replace Tideframe's standalone JavaPlugin entry point with a BigCasares-owned runtime.
- [x] Scope commands, listeners, scheduled tasks, watchers, HTTP servers and executors to the module lifecycle.
- [x] Start the embedded runtime from TideframeUiModule and bind the existing mission/shop presentation to it.
- [x] Remove external plugin lookup, softdepend and binary API; adapt regression tests to embedded ownership.
- [x] Run engine/editor tests and BigCasares clean build; inspect the single final JAR.
- [x] Review and update PR #19 without assistant attribution; keep live-client acceptance explicitly pending.
- [ ] Live acceptance on a Paper server with Java and Bedrock clients (not done; see docs/tideframe.md).

## Evidence (2026-11-02)

- Standalone leftovers: after the data-folder move the Ultimate UI importer looked in
  `plugins/BigCasares/tideframe/UltimateUI`; it reads the server's `plugins/UltimateUI/contents` again.
  Operator messages, MENUS.md text, config comments and Javadoc name `plugins/BigCasares/tideframe/engine`.
- JDK 25.0.3, `gradlew.bat --offline --no-daemon clean build`: BUILD SUCCESSFUL. `test`: 1323 passed, 0 failed,
  0 errors, 0 skipped. `tideframeTest`: 591 tests, 0 failed, 0 errors, 1 skipped (the symbolic-link test aborts
  on a Windows account without the symlink privilege). `verifyEmbeddedTideframe` passed.
- Editor: 322 Node tests passed.
- Final JAR: 299 engine classes, one plugin.yml, no nested JARs, license and asset provenance included.

## Decisions

The engine retains its dev.tideframe package to preserve source and API compatibility. Mutable engine data is under
plugins/BigCasares/tideframe/engine. Bundled resources are under tideframe/engine to avoid replacing BigCasares config.
No arena, gameplay modules, platform versions or economy rules change. Clients still need the generated pack;
embedding source does not remove that Minecraft requirement. Java/Bedrock rendering is not inferred from tests.

The BigCasares item suite keeps its existing Spigot fixture API. The imported UI suite uses a separate
tideframeTest source set against the same Paper API as production; check/clean build includes both suites.
This avoids replacing the item factory or downgrading the engine's audience methods. Adventure artifacts are
reused from the existing Paper dependency graph without choosing a new version.
