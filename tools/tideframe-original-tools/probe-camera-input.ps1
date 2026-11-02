param(
    [string]$ServerRoot = '',
    [string]$Classes = '',
    [string]$JavaHome = 'C:/Program Files/Java/jdk-25.0.3'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$ServerRoot) { $ServerRoot = Join-Path $repo '../../work/tideframe-server' }
if (!$Classes) { $Classes = Join-Path $repo 'build/classes/java/main' }
$server = (Resolve-Path -LiteralPath $ServerRoot).Path
$compiled = (Resolve-Path -LiteralPath $Classes).Path
$officialJar = Join-Path $server 'versions/26.2/paper-26.2.jar'
if (!(Test-Path -LiteralPath $officialJar -PathType Leaf)) { throw 'Install the pinned official Paper 26.2 test server first.' }
# No server process is launched. Minecraft bootstraps only registries; the transport is EmbeddedChannel.
$probe = Join-Path $repo '.build-tools/camera-input-probe'
New-Item -ItemType Directory -Force -Path $probe | Out-Null
$libraries = @(Get-ChildItem -LiteralPath (Join-Path $server 'libraries') -Recurse -File -Filter '*.jar' | ForEach-Object { $_.FullName })
$classPath = (@($compiled, $officialJar) + $libraries) -join [IO.Path]::PathSeparator
& (Join-Path $JavaHome 'bin/javac.exe') -cp $classPath -d $probe (Join-Path $PSScriptRoot 'CameraInputSelfProbe.java') (Join-Path $PSScriptRoot 'CameraInputPendingProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'Camera input probe compilation failed.' }
Push-Location $probe
try {
    & (Join-Path $JavaHome 'bin/java.exe') -Xmx512m -cp ($classPath + [IO.Path]::PathSeparator + $probe) dev.tideframe.CameraInputSelfProbe
    if ($LASTEXITCODE -ne 0) { throw 'Camera input packet/EmbeddedChannel probe failed.' }
    & (Join-Path $JavaHome 'bin/java.exe') -Xmx512m -cp ($classPath + [IO.Path]::PathSeparator + $probe) dev.tideframe.CameraInputPendingProbe
    if ($LASTEXITCODE -ne 0) { throw 'Camera input ADD_PENDING event-loop probe failed.' }
} finally { Pop-Location }
