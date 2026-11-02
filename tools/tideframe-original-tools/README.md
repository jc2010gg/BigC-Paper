# Original Tideframe Tools

This directory preserves the 54 authored helper sources found in the authorized
Tideframe `tools/` directory. The imported Java, Python, PowerShell, JavaScript,
and command scripts are retained unchanged as historical source reference.

The standalone build, launcher, staging, portable packaging, release, and deploy
workflows are not supported entry points for the embedded BigCasares module.
They may assume the original directory layout, standalone plugin metadata,
local staging folders, external dependencies, and generated artifacts. Do not
execute these legacy workflows against this repository or a live server.

## Supported Entry Points

Build BigCasares from the repository root using its own Gradle wrapper:

```powershell
.\gradlew.bat build
```

On platforms using the shell wrapper, the equivalent is `./gradlew build`.
There is no separate Tideframe plugin build or compiled API JAR to install.

The supported editor is `tools/tideframe-editor`, not the legacy staging copies:

```sh
node tools/tideframe-editor/serve.mjs --server "<server root>"
```

It selects `<server root>/plugins/BigCasares/tideframe/engine/screens` and uses
the adjacent engine data for the game bridge, images, and sounds. The maintained
editor's `test-kit.mjs` has adapted imports; this directory's original copy is
retained unchanged for source completeness.

Original examples and artwork are preserved in `docs/tideframe-examples` with
their original hierarchy. They are reference assets, not automatically staged
server data. No legacy helper, release script, or launcher was executed as part
of this import. Caches and compiled binaries are not included here.

## License

The imported helpers remain covered by the original Tideframe MIT license at
`src/main/resources/META-INF/tideframe/LICENSE`. Asset provenance is retained in
`src/main/resources/META-INF/tideframe/ASSETS.md`. These are repository-root paths.
