param([switch]$Clean, [switch]$Pack, [switch]$RecordDependencies)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$version = '9.7.1'
$digest = 'acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a'
# Gradle is external build tooling. No third-party wrapper source/JAR is bundled.
$gradle = $null
$cache = Join-Path $env:USERPROFILE ".gradle/wrapper/dists/gradle-$version-bin"
if (Test-Path -LiteralPath $cache) {
    $gradle = Get-ChildItem -LiteralPath $cache -Recurse -Filter gradle.bat | Select-Object -First 1 -ExpandProperty FullName
}
if (!$gradle) {
    $tools = Join-Path $repo '.build-tools'
    New-Item -ItemType Directory -Force $tools | Out-Null
    $archive = Join-Path $tools "gradle-$version-bin.zip"
    $gradle = Join-Path $tools "gradle-$version/bin/gradle.bat"
    if (!(Test-Path -LiteralPath $gradle)) {
        if (!(Test-Path -LiteralPath $archive)) { Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$version-bin.zip" -OutFile $archive }
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLower() -ne $digest) { throw 'Gradle checksum mismatch; archive not executed.' }
        Expand-Archive -LiteralPath $archive -DestinationPath $tools
    }
}
$oldOptions = $env:JAVA_TOOL_OPTIONS
try {
    if ($env:OS -eq 'Windows_NT') { $env:JAVA_TOOL_OPTIONS = ($oldOptions + ' -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE').Trim() }
    Push-Location $repo
    try {
        $tasks = @('build')
        if ($Clean) { $tasks = @('clean') + $tasks }
        if ($Pack) { $tasks += 'generatePack' }
        if ($RecordDependencies) { $tasks += @('recordDependencyInventory', '--write-locks') }
        & $gradle --no-daemon --console=plain @tasks
        if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
    } finally { Pop-Location }
} finally { $env:JAVA_TOOL_OPTIONS = $oldOptions }
