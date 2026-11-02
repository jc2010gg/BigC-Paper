# Original Tideframe local demo starter. MIT License; see LICENSE.
# Copy this script to demo/INICIAR_PRUEBA.ps1 before distributing the demo.
[CmdletBinding()]
param(
    [string]$DemoDirectory = '',
    [string]$JavaExecutable = '',
    [ValidateSet(25586,25588)][int]$MinecraftPort = 25586,
    [ValidateSet(8136,8138)][int]$PackPort = 8136
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 can evaluate $PSScriptRoot as empty in parameter defaults.
# Resolve it in the script body; retain an explicitly supplied directory.
if (-not $PSBoundParameters.ContainsKey('DemoDirectory')) { $DemoDirectory = $PSScriptRoot }
$paperSha256 = '5ab560a769c1ab413cb7f637dd0dc697974571f2db0a667cfbac511422e51b26'
$pluginSha256 = '1d586e05a1ff21565ab2ce3e292e897070bed5e84f1fb0418e9b0b30a414aebf'
$paperUrl = 'https://fill-data.papermc.io/v1/objects/5ab560a769c1ab413cb7f637dd0dc697974571f2db0a667cfbac511422e51b26/paper-26.2-132.jar'

function Read-LocalProperties([string]$Path) {
    $values = @{}
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        $text = $line.Trim()
        if ($text.Length -eq 0 -or $text.StartsWith('#') -or $text.StartsWith('!')) { continue }
        # Only the ordinary key=value format shipped with this demo is supported.
        # Refuse escaped keys/continuations rather than guessing Java properties semantics.
        if ($text -notmatch '^([^=:\s\\]+)\s*=\s*(.*)$') { throw "Formato de propiedades no admitido en $Path." }
        $key = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value -match '(?<!\\)(?:\\\\)*\\$') { throw "No se admiten continuaciones en $Path." }
        if ($values.ContainsKey($key)) { throw "Propiedad duplicada '$key' en $Path." }
        $values[$key] = $value
    }
    return $values
}

function Assert-Hash([string]$Path, [string]$Expected, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Falta $Label`: $Path" }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $Expected) { throw "SHA-256 incorrecto de $Label. No se ejecutara ni reemplazara ese archivo. Esperado: $Expected; recibido: $actual" }
}

function Assert-LocalPack([string]$Path) {
    $pack = @{}
    $rootKeys = @{}
    $inside = $false
    $sections = 0
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        if ($line.Trim().Length -eq 0 -or $line.TrimStart().StartsWith('#')) { continue }
        if ($line -match '^\S') {
            if ($line -notmatch '^(pack|cursor|limits):\s*$') { throw 'La demo exige las secciones YAML simples pack, cursor y limits incluidas.' }
            $rootKey = $Matches[1]
            if ($rootKeys.ContainsKey($rootKey)) { throw "Seccion YAML duplicada: $rootKey." }
            $rootKeys[$rootKey] = $true
            $inside = $rootKey -eq 'pack'
            if ($inside) { $sections++ }
            continue
        }
        if (-not $inside) { continue }
        if ($line -notmatch '^  (bind|port|public-url|required):\s*(\S+)\s*$') { throw 'La seccion pack debe conservar el formato simple de la demo.' }
        $key = $Matches[1]
        if ($pack.ContainsKey($key)) { throw "Clave pack duplicada: $key." }
        $pack[$key] = $Matches[2]
    }
    if ($sections -ne 1 -or $pack.Count -ne 4 -or $pack['bind'] -ne '127.0.0.1' -or $pack['port'] -ne [string]$PackPort -or
        $pack['public-url'] -ne "http://127.0.0.1:$PackPort/packs/" -or $pack['required'] -notin @('true', 'false')) {
        throw "Esta demo exige pack.bind=127.0.0.1, pack.port=$PackPort y la URL local incluida."
    }
}

$demoRoot = (Resolve-Path -LiteralPath $DemoDirectory).ProviderPath
if (-not (Test-Path -LiteralPath $demoRoot -PathType Container)) { throw 'La carpeta demo no existe.' }
$marker = Join-Path $demoRoot '.tideframe-disposable'
if (-not (Test-Path -LiteralPath $marker -PathType Leaf) -or [System.IO.File]::ReadAllText($marker).Trim() -ne 'TIDEFRAME_TEST_ONLY') {
    throw 'Inicio rechazado: falta el marcador exacto TIDEFRAME_TEST_ONLY de la carpeta de prueba.'
}
$eulaPath = Join-Path $demoRoot 'eula.txt'
if (-not (Test-Path -LiteralPath $eulaPath -PathType Leaf)) {
    [System.IO.File]::WriteAllText($eulaPath, "# Lee https://www.minecraft.net/eula antes de decidir.`r`neula=false`r`n", [System.Text.UTF8Encoding]::new($false))
    Write-Host 'Se creo eula.txt con eula=false. No se descargo ni inicio el servidor.'
    Write-Host 'Lee la EULA de Minecraft. Solo si la aceptas, cambia manualmente a eula=true y vuelve a iniciar.'
    return
}
$eula = Read-LocalProperties $eulaPath
if ($eula['eula'] -ne 'true') {
    Write-Host 'EULA sin aceptar: no se descargara ni iniciara el servidor.'
    Write-Host 'Lee https://www.minecraft.net/eula y cambia eula.txt a eula=true solo si la aceptas.'
    return
}

$propertiesPath = Join-Path $demoRoot 'server.properties'
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) { throw 'Falta server.properties de la demo.' }
$properties = Read-LocalProperties $propertiesPath
if ($properties['server-ip'] -ne '127.0.0.1' -or $properties['server-port'] -ne [string]$MinecraftPort -or
    $properties['online-mode'] -ne 'true' -or $properties['level-name'] -ne 'tideframe-test') {
    throw "Configuracion rechazada: la demo exige 127.0.0.1:$MinecraftPort, online-mode=true y level-name=tideframe-test."
}
foreach ($key in @('enable-query', 'enable-rcon', 'enable-jmx-monitoring', 'management-server-enabled')) {
    if ($properties.ContainsKey($key) -and $properties[$key] -ne 'false') { throw "La demo exige $key=false si se configura esa opcion." }
}
$worldPath = Join-Path $demoRoot 'tideframe-test'
if (-not (Test-Path -LiteralPath (Join-Path $worldPath 'level.dat') -PathType Leaf)) { throw 'Falta el mundo guardado tideframe-test/level.dat. No se generara otro mundo.' }
foreach ($directory in @($demoRoot, $worldPath, (Join-Path $demoRoot 'plugins'))) {
    if ((Get-Item -LiteralPath $directory).Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
        throw "La demo no admite enlaces de carpeta: $directory"
    }
}
$configPath = Join-Path $demoRoot 'plugins/Tideframe/config.yml'
if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) { throw 'Falta plugins/Tideframe/config.yml de la demo.' }
Assert-LocalPack $configPath
$occupied = @([System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object { $_.Port -in @($MinecraftPort, $PackPort) })
if ($occupied.Count -gt 0) { throw "El puerto $MinecraftPort o $PackPort ya esta ocupado. No se detendra ningun proceso existente." }
$pluginPath = Join-Path $demoRoot 'plugins/tideframe.jar'
Assert-Hash $pluginPath $pluginSha256 'Tideframe'
$extraPlugins = @(Get-ChildItem -LiteralPath (Join-Path $demoRoot 'plugins') -Filter '*.jar' -File | Where-Object { $_.Name -ne 'tideframe.jar' })
if ($extraPlugins.Count -gt 0) { throw 'La demo aislada solo admite el JAR propio tideframe.jar incluido.' }

if ([string]::IsNullOrWhiteSpace($JavaExecutable)) {
    $defaultJava = 'C:\Program Files\Java\jdk-25.0.3\bin\java.exe'
    if (Test-Path -LiteralPath $defaultJava -PathType Leaf) { $JavaExecutable = $defaultJava }
    else {
        $javaCommand = Get-Command java -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -eq $javaCommand) { throw 'No se encontro Java. Instala Java 25 o indica -JavaExecutable C:\ruta\bin\java.exe.' }
        $JavaExecutable = $javaCommand.Source
    }
}
if (-not (Test-Path -LiteralPath $JavaExecutable -PathType Leaf)) { throw 'JavaExecutable debe apuntar al archivo java.exe de Java 25.' }
$javaPath = (Resolve-Path -LiteralPath $JavaExecutable).ProviderPath
$probe = [System.Diagnostics.Process]::new()
try {
    $probe.StartInfo.FileName = $javaPath
    $probe.StartInfo.Arguments = '-version'
    $probe.StartInfo.UseShellExecute = $false
    $probe.StartInfo.CreateNoWindow = $true
    $probe.StartInfo.RedirectStandardOutput = $true
    $probe.StartInfo.RedirectStandardError = $true
    if (-not $probe.Start()) { throw 'No se pudo comprobar Java.' }
    $javaOutput = $probe.StandardOutput.ReadToEnd() + $probe.StandardError.ReadToEnd()
    $probe.WaitForExit()
    if ($probe.ExitCode -ne 0 -or $javaOutput -notmatch '(?:java|openjdk)(?: version)?\s+"?25(?:[. +"-]|$)') { throw 'Se requiere Java 25. La comprobacion de version fallo.' }
} finally { $probe.Dispose() }

$paperPath = Join-Path $demoRoot 'paper.jar'
if (Test-Path -LiteralPath $paperPath) {
    Assert-Hash $paperPath $paperSha256 'Paper 26.2 build 132'
} else {
    $temporary = Join-Path $demoRoot ('paper-download-' + [System.Guid]::NewGuid().ToString('N') + '.tmp')
    try {
        Write-Host 'Descargando Paper 26.2 build 132 desde el servidor oficial de PaperMC...'
        Invoke-WebRequest -Uri $paperUrl -OutFile $temporary -UseBasicParsing
        Assert-Hash $temporary $paperSha256 'descarga oficial Paper 26.2 build 132'
        Move-Item -LiteralPath $temporary -Destination $paperPath
    } finally {
        if (Test-Path -LiteralPath $temporary -PathType Leaf) { Remove-Item -LiteralPath $temporary }
    }
}
Write-Host "Demo verificada. Minecraft Java 26.2: conecta a 127.0.0.1:$MinecraftPort."
Write-Host 'Esta consola queda interactiva. Para guardar y cerrar: save-all flush, y despues stop.'
Push-Location -LiteralPath $demoRoot
try {
    & $javaPath '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' '-Xms512M' '-Xmx1G' '-jar' 'paper.jar' 'nogui'
    if ($LASTEXITCODE -ne 0) { throw "Paper termino con codigo $LASTEXITCODE. Revisa logs/latest.log." }
} finally { Pop-Location }
