# Test refusal paths only: no Java process, server download or client launch.
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$workspace=Split-Path (Split-Path $repo -Parent) -Parent
$root=Join-Path $workspace ('work/demo-preflight-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
$starter=Join-Path $PSScriptRoot 'start-demo.ps1'
$cases=@()
function Expect-Refusal([string]$Name,[string]$Directory,[string]$Pattern){
    try { & $starter -DemoDirectory $Directory -MinecraftPort 25588 -PackPort 8138 | Out-Null; throw 'Expected refusal did not occur' }
    catch { if($_.Exception.Message -notmatch $Pattern){throw}; $script:cases+=@{case=$Name;passed=$true;message=$_.Exception.Message} }
}
$missing=Join-Path $root 'missing-marker';New-Item -ItemType Directory -Path $missing | Out-Null
Expect-Refusal 'missing-marker' $missing 'marcador exacto'
$unaccepted=Join-Path $root 'eula-not-accepted';New-Item -ItemType Directory -Path $unaccepted | Out-Null
'TIDEFRAME_TEST_ONLY' | Set-Content -LiteralPath (Join-Path $unaccepted '.tideframe-disposable')
& $starter -DemoDirectory $unaccepted | Out-Null
if((Get-Content -Raw -LiteralPath (Join-Path $unaccepted 'eula.txt')) -notmatch 'eula=false'){throw 'EULA unexpectedly accepted'}
if(Test-Path -LiteralPath (Join-Path $unaccepted 'paper.jar')){throw 'Unexpected download'}
$cases+=@{case='missing-eula-remains-false';passed=$true}
# Accepted value below is a parser-test fixture, never used to start/download software.
foreach($name in @('nonlocal-properties','modified-plugin')){
    $folder=Join-Path $root $name;New-Item -ItemType Directory -Force (Join-Path $folder 'plugins/Tideframe') | Out-Null
    'TIDEFRAME_TEST_ONLY' | Set-Content -LiteralPath (Join-Path $folder '.tideframe-disposable')
    'eula=true' | Set-Content -LiteralPath (Join-Path $folder 'eula.txt')
    $props="server-ip=127.0.0.1`nserver-port=25588`nonline-mode=true`nlevel-name=tideframe-test`n"
    if($name -eq 'nonlocal-properties'){$props=$props.Replace('127.0.0.1','0.0.0.0')}
    $props | Set-Content -LiteralPath (Join-Path $folder 'server.properties')
    if($name -eq 'modified-plugin'){
        New-Item -ItemType Directory -Path (Join-Path $folder 'tideframe-test') | Out-Null
        'parser fixture only' | Set-Content -LiteralPath (Join-Path $folder 'tideframe-test/level.dat')
        (Get-Content -Raw -LiteralPath (Join-Path $repo 'src/main/resources/config.yml')).Replace('8136','8138') | Set-Content -LiteralPath (Join-Path $folder 'plugins/Tideframe/config.yml')
        'invalid plugin bytes' | Set-Content -LiteralPath (Join-Path $folder 'plugins/tideframe.jar')
        Expect-Refusal $name $folder 'SHA-256 incorrecto de Tideframe'
    }else{Expect-Refusal $name $folder 'Configuracion rechazada'}
}
$releaseVersion = [regex]::Match((Get-Content -Raw -LiteralPath (Join-Path $repo 'build.gradle.kts')), 'version = "([0-9.]+)"').Groups[1].Value.Split('.')
$evidenceTag = 'v' + $releaseVersion[1]
if ($releaseVersion.Length -gt 2 -and $releaseVersion[2] -ne '0') { $evidenceTag += '-' + $releaseVersion[2] }
$cases | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $repo ('evidence/' + $evidenceTag + '-demo-preflight.json'))
$cases | ConvertTo-Json -Depth 4
