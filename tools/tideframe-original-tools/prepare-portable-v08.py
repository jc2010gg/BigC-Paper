"""Snapshot the CURRENT stopped test world into a new original-only 0.8 demo.

No Paper/Minecraft executable, accepted EULA, account cache, operator/whitelist
file or software cache is copied. The 48 authored runtime frame JSONs are optional
editor examples, not extra installed screens (the plugin supplies them itself).
"""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT.parents[1] / 'work'
SERVER = WORK / 'tideframe-server'
DEMO = ROOT.parent / 'Tideframe-demo-0.8.0'
OLD = ROOT.parent / 'Tideframe-demo-0.7.0'
JAR = ROOT / 'build/libs/tideframe-0.8.0.jar'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def no_links(folder):
    for path in [folder, *folder.rglob('*')]:
        info = path.lstat()
        require(not path.is_symlink() and not (getattr(info, 'st_file_attributes', 0) & 0x400),
                f'Reparse point is not admitted: {path}')


def inventory(folder):
    require(folder.is_dir(), f'Missing inventory directory: {folder}')
    no_links(folder)
    return {path.relative_to(folder).as_posix(): sha(path)
            for path in sorted(folder.rglob('*')) if path.is_file()}


def stopped():
    for port in (25586, 8136, 25588, 8138, 25589, 8139):
        with socket.socket() as sock:
            sock.settimeout(.5)
            require(sock.connect_ex(('127.0.0.1', port)) != 0, 'Do not copy a live test world')
    require(os.name == 'nt', 'This tool verifies the Windows session lock and must run on Windows')
    lock = SERVER / 'tideframe-test/session.lock'
    require(lock.is_file(), 'Missing existing saved-world lock')
    escaped = lock.as_posix().replace("'", "''")
    command = ("$ErrorActionPreference='Stop';$s=[IO.File]::Open('" + escaped +
               "',[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$s.Dispose()")
    subprocess.run(['powershell.exe', '-NoProfile', '-Command', command], check=True,
                   capture_output=True, text=True, timeout=30)


def main():
    require(SERVER.resolve().parent == WORK.resolve() and DEMO.resolve().parent == ROOT.parent.resolve(),
            'Portable paths escaped the named workspace')
    require((SERVER / '.tideframe-disposable').read_text().strip() == 'TIDEFRAME_TEST_ONLY', 'Not a disposable source')
    require(not DEMO.exists(), 'Refuse to overwrite an existing delivery')
    receipt = ROOT / 'evidence/runtime-v08-portable.json'
    require(not receipt.exists(), 'Refuse to overwrite an existing portable receipt')
    no_links(SERVER)
    no_links(OLD)
    stopped()
    source_world = SERVER / 'tideframe-test'
    before = inventory(source_world)
    layouts = inventory(SERVER / 'plugins/Tideframe/screens')
    require(sum(name.endswith('.json') for name in layouts) == 10, 'Expected ten installed operator layouts')
    require(sha(SERVER / 'plugins/tideframe.jar') == sha(JAR), 'Original test server is not staged with this 0.8 JAR')
    stage = json.loads((ROOT / 'evidence/runtime-v08-staging.json').read_text(encoding='utf-8'))
    require(stage.get('pluginSha256') == sha(JAR) and stage.get('worldByteIdenticalAfterStage') is True,
            'A verified 0.8 original-server staging receipt is required')
    require(stage.get('packSha256') == sha(SERVER / 'plugins/Tideframe/packs/tideframe.zip'), 'Staged pack changed')
    properties = (OLD / 'server.properties').read_text(encoding='utf-8')
    for required in ('management-server-secret=\n', 'management-server-enabled=false', 'online-mode=true',
                     'server-ip=127.0.0.1', 'server-port=25586', 'enable-rcon=false', 'enable-query=false',
                     'level-name=tideframe-test'):
        require(required in properties, f'Unsanitized prior portable configuration: {required.strip()}')
    starter = (ROOT / 'tools/start-demo.ps1').read_text(encoding='utf-8')
    require(f"$pluginSha256 = '{sha(JAR)}'" in starter, 'Starter does not pin the exact staged runtime')
    guide = (ROOT / 'docs/PLAY_DEMO_ES.md').read_text(encoding='utf-8').replace('../evidence/', 'evidence/').replace('](RUNTIME_V08_REPORT.md)', '](docs/RUNTIME_V08_REPORT.md)')
    require('0.8.0' in guide, 'The guide must describe 0.8.0')
    evidence = []
    for name in re.findall(r'\]\(evidence/([^)]+)\)', guide):
        require(Path(name).name == name, f'Unsafe guide evidence path: {name}')
        if name == 'runtime-v08-portable.json':
            continue  # Copy this script's own receipt only after verification.
        require((ROOT / 'evidence' / name).is_file(), f'Missing guide evidence: {name}')
        evidence.append(ROOT / 'evidence' / name)
    runtime_examples = sorted((ROOT / 'examples/runtime-demo').glob('*.json'))
    require(len(runtime_examples) == 48, 'Expected exactly 48 authored runtime editor examples')
    for source in runtime_examples:
        screen = json.loads(source.read_text(encoding='utf-8'))
        require(screen.get('version') == 6 and screen.get('id') == source.stem, 'Runtime example is not schema 6')

    DEMO.mkdir()
    shutil.copytree(source_world, DEMO / 'tideframe-test')
    require(inventory(DEMO / 'tideframe-test') == before == inventory(source_world), 'World snapshot byte mismatch')
    stopped()
    # Reuse only reviewed, account-free local properties, never operator settings.
    shutil.copy2(OLD / '.tideframe-disposable', DEMO / '.tideframe-disposable')
    (DEMO / 'server.properties').write_text(properties, encoding='utf-8')
    (DEMO / 'plugins/Tideframe').mkdir(parents=True)
    for name in ('screens', 'packs'):
        shutil.copytree(SERVER / 'plugins/Tideframe' / name, DEMO / 'plugins/Tideframe' / name)
    shutil.copy2(SERVER / 'plugins/Tideframe/config.yml', DEMO / 'plugins/Tideframe/config.yml')
    shutil.copy2(JAR, DEMO / 'plugins/tideframe.jar')
    shutil.copy2(ROOT / 'LICENSE', DEMO / 'LICENSE')
    shutil.copy2(ROOT / 'tools/start-demo.ps1', DEMO / 'INICIAR_PRUEBA.ps1')
    shutil.copy2(ROOT / 'tools/start-demo.cmd', DEMO / 'INICIAR_PRUEBA.cmd')
    shutil.copytree(ROOT / 'editor', DEMO / 'editor')
    shutil.copytree(ROOT / 'docs', DEMO / 'docs')
    optional = DEMO / 'EJEMPLOS_CALIDAD'
    optional.mkdir()
    for name in ('advanced_medium.json', 'advanced_high.json', 'advanced_4k.json'):
        shutil.copy2(ROOT / 'examples/advanced-demo' / name, optional / name)
    (optional / 'LEEME.txt').write_text(
        'Ejemplos pesados opcionales. Respaldar y detener antes de instalar solo el deseado.\n'
        'Todas las pantallas instaladas comparten el mismo resource pack.\n', encoding='utf-8')
    runtime = DEMO / 'EJEMPLOS_RUNTIME'
    runtime.mkdir()
    for source in runtime_examples:
        shutil.copy2(source, runtime / source.name)
    (runtime / 'LEEME.txt').write_text(
        '48 pantallas/frames originales ya incluidos en el plugin. Para abrirlos o editarlos en el editor web.\n'
        'No copiar todos a screens: no hace falta instalarlos. Un JSON con el mismo ID reemplaza solo ese frame\n'
        'al reiniciar y volver a aceptar el pack. Conservar IDs y acciones para mantener las demos.\n'
        'Modo, escala, cursor y brillo son schema6; no existe deteccion automatica de resolucion del cliente.\n', encoding='utf-8')
    projects = DEMO / 'PROYECTOS_EDITOR'
    projects.mkdir()
    for source in (ROOT / 'examples/creation-demo').glob('*.tideframe-project.json'):
        shutil.copy2(source, projects / source.name)
    for source in (ROOT / 'examples/runtime-project').glob('*.tideframe-project.json'):
        shutil.copy2(source, projects / source.name)
    (DEMO / 'ABRIR_EDITOR.cmd').write_bytes(b'@echo off\r\ncd /d "%~dp0"\r\nnode editor/serve.mjs\r\npause\r\n')
    (DEMO / 'LEEME.md').write_text(guide, encoding='utf-8')
    (DEMO / 'evidence').mkdir()
    for source in evidence:
        shutil.copy2(source, DEMO / 'evidence' / source.name)
    world_manifest = {'files': before, 'nativeClientVerified': False,
                      'source': 'Current stopped disposable source world at portable creation; includes operator progress.'}
    (DEMO / 'WORLD_SNAPSHOT_V08.json').write_text(json.dumps(world_manifest, indent=2) + '\n', encoding='utf-8')
    require(inventory(source_world) == before == inventory(DEMO / 'tideframe-test'), 'Source world changed while packaging')
    require(inventory(DEMO / 'plugins/Tideframe/screens') == layouts, 'Installed layout bytes changed while packaging')
    for forbidden in ('paper.jar', 'folia.jar', 'eula.txt', 'ops.json', 'whitelist.json', 'usercache.json',
                      'banned-ips.json', 'banned-players.json', 'libraries', '.paper', 'cache', 'versions'):
        require(not (DEMO / forbidden).exists(), f'Excluded software/account file was packaged: {forbidden}')
    for path in DEMO.rglob('*.jar'):
        require(path.relative_to(DEMO).as_posix() == 'plugins/tideframe.jar', 'Foreign executable JAR in portable')
    with zipfile.ZipFile(DEMO / 'plugins/tideframe.jar') as archive:
        require(archive.testzip() is None and 'LICENSE' in archive.namelist(), 'Authored JAR CRC/license check failed')
    stopped()
    result = {
        'version': '0.8.0', 'worldFiles': len(before),
        'worldBytes': sum(path.stat().st_size for path in source_world.rglob('*') if path.is_file()),
        'worldByteIdenticalToStoppedSource': True, 'currentOperatorProgressIncluded': True,
        'nativeClientVerified': False, 'pluginSha256': sha(DEMO / 'plugins/tideframe.jar'),
        'packSha256': sha(DEMO / 'plugins/Tideframe/packs/tideframe.zip'),
        'excluded': ['Paper', 'Minecraft', 'eula.txt', 'account caches', 'ops/whitelist', 'server software caches'],
        'installedJsonScreens': 10, 'builtinRuntimeScreens': 48, 'compiledScreens': 58,
        'optionalRuntimeEditorExamples': len(runtime_examples), 'runtimeExamplesInstalledAsJson': False,
        'optionalHeavyLayouts': 3, 'editorIncluded': True, 'originalProjectExampleIncluded': True}
    receipt.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    shutil.copy2(receipt, DEMO / 'evidence' / receipt.name)
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
