# Builds libs/tideframe-api-<version>.jar: only the Tideframe classes BigCasares compiles against (the TideframeApi service
# and the click, open and close events), taken from a compiled Tideframe. compileOnly: it never goes into the BigCasares JAR;
# on the server the classes come from the installed Tideframe plugin.
#
#   .\tools\tideframe-api\make-api-jar.ps1 -Classes <Tideframe>\build\classes\java\main -Version 0.23.0
param(
    [Parameter(Mandatory = $true)][string]$Classes,
    [Parameter(Mandatory = $true)][string]$Version
)
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$api = Join-Path $Classes 'dev\tideframe'
$names = @('TideframeApi.class', 'TideframeClickEvent.class', 'TideframeClickEvent$ClickType.class',
           'TideframeOpenEvent.class', 'TideframeCloseEvent.class', 'TideframeCloseEvent$Reason.class')
foreach ($name in $names) { if (-not (Test-Path -LiteralPath (Join-Path $api $name))) { throw "Missing $name in $api" } }
$staging = Join-Path ([System.IO.Path]::GetTempPath()) ("tideframe-api-" + [guid]::NewGuid())
New-Item -ItemType Directory (Join-Path $staging 'dev\tideframe') | Out-Null
try {
    foreach ($name in $names) { Copy-Item -LiteralPath (Join-Path $api $name) -Destination (Join-Path $staging "dev\tideframe\$name") }
    $libs = Join-Path $root 'libs'
    New-Item -ItemType Directory -Force $libs | Out-Null
    $jar = Join-Path $libs "tideframe-api-$Version.jar"
    if (Test-Path -LiteralPath $jar) { Remove-Item -LiteralPath $jar }
    & jar --create --file $jar --date 2026-01-01T00:00:00Z -C $staging dev
    if ($LASTEXITCODE -ne 0) { throw "jar failed: $LASTEXITCODE" }
    "Wrote $jar"
    foreach ($name in $names) { (Get-FileHash -LiteralPath (Join-Path $staging "dev\tideframe\$name") -Algorithm SHA256).Hash.ToLower() + "  dev/tideframe/$name" }
} finally { Remove-Item -LiteralPath $staging -Recurse -Force }
