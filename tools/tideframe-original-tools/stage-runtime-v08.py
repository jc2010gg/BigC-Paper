"""Back up and update only the stopped, marked Tideframe 0.8 test directories.

prepare verifies that both disposable probes have the operator's current ten
layouts and backs up each entire probe before server-smoke.py replaces its JAR.
install requires both fresh 0.8 smoke receipts and then preserves every current
world/layout byte while updating only the authored runtime and its support files.
Neither mode starts a server, downloads anything, or changes a layout JSON.
"""
from pathlib import Path
import argparse
import datetime
import hashlib
import json
import os
import re
import shutil
import socket
import subprocess

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT.parents[1] / 'work'
SERVER = WORK / 'tideframe-server'
PROBE = WORK / 'tideframe-quality-server'
FOLIA = WORK / 'tideframe-quality-folia-server'
PREVIOUS = 'd5c73c749e03922efafefe2fce453b59ec89931f0341ecea947cbed9917f8c95'
JAR = ROOT / 'build/libs/tideframe-0.8.0.jar'
PREPARATION = ROOT / 'evidence/runtime-v08-preparation.json'
PORTS = (25586, 8136, 25588, 8138, 25589, 8139)


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
    """Reject symlinks and Windows junctions before recursive read/copy."""
    for path in [folder, *folder.rglob('*')]:
        info = path.lstat()
        require(not path.is_symlink() and not (getattr(info, 'st_file_attributes', 0) & 0x400),
                f'Reparse point is not admitted in a saved test directory: {path}')


def inventory(folder):
    require(folder.is_dir(), f'Missing inventory directory: {folder}')
    no_links(folder)
    return {path.relative_to(folder).as_posix(): sha(path)
            for path in sorted(folder.rglob('*')) if path.is_file()}


def validate_folder(folder):
    require(folder.resolve().parent == WORK.resolve(), 'Test directory escaped the named work directory')
    require((folder / '.tideframe-disposable').read_text().strip() == 'TIDEFRAME_TEST_ONLY',
            f'Missing exact disposable marker: {folder}')
    no_links(folder)


def stopped():
    for port in PORTS:
        with socket.socket() as sock:
            sock.settimeout(.5)
            require(sock.connect_ex(('127.0.0.1', port)) != 0,
                    f'Refuse a live test update: port {port} is occupied')
    # A free listener alone does not prove that a world is unlocked.
    require(os.name == 'nt', 'This handoff tool verifies the Windows session lock and must run on Windows')
    for folder in (SERVER, PROBE, FOLIA):
        lock = folder / 'tideframe-test/session.lock'
        require(lock.is_file(), f'Missing existing world session lock: {lock}')
        escaped = lock.as_posix().replace("'", "''")
        command = ("$ErrorActionPreference='Stop';$s=[IO.File]::Open('" + escaped +
                   "',[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$s.Dispose()")
        subprocess.run(['powershell.exe', '-NoProfile', '-Command', command], check=True,
                       capture_output=True, text=True, timeout=30)


def backup(folder):
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S-%fZ')
    target = WORK / f'tideframe-pre-runtime-v08-{folder.name}-{stamp}'
    require(target.resolve().parent == WORK.resolve() and not target.exists(), 'Unsafe or existing backup target')
    before = inventory(folder)
    shutil.copytree(folder, target)
    require(inventory(target) == before == inventory(folder), 'Backup verification failed; source has changed')
    return target, before


def screen_inventory(folder):
    result = inventory(folder / 'plugins/Tideframe/screens')
    require(sum(name.endswith('.json') for name in result) == 10,
            'Expected ten existing layout JSONs; preserve operator drift and review the 58-screen smoke expectation')
    return result


def write_receipt(name, result):
    target = ROOT / 'evidence' / name
    require(not target.exists(), f'Refuse to overwrite an existing verification receipt: {target.name}')
    target.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


def verify_smoke(distribution, folder, digest, screens):
    stem = ROOT / f'evidence/runtime-v08-{distribution}-server'
    result = json.loads(stem.with_suffix('.json').read_text(encoding='utf-8'))
    require(result.get('distribution') == distribution and result.get('pluginSha256') == digest,
            f'{distribution}: smoke is not for this exact runtime JAR')
    for key in ('ready', 'consoleStatus', 'cameraBindingsVerified', 'saveObserved', 'stopObserved'):
        require(result.get(key) is True, f'{distribution}: smoke requirement did not pass: {key}')
    require(result.get('exitCode') == 0 and result.get('forcedTermination') is False and 'error' not in result,
            f'{distribution}: smoke did not end cleanly')
    require(result.get('portsReleased') and all(value is True for value in result['portsReleased'].values()),
            f'{distribution}: probe listener was not released')
    require(result.get('status', {}).get('version', {}).get('protocol') == 776,
            f'{distribution}: wrong Minecraft status protocol')
    log = stem.with_suffix('.log').read_text(encoding='utf-8')
    require('TIDEFRAME_CAMERA_BINDINGS_READY protocol=776 playerProbe=false' in log,
            f'{distribution}: actual NMS/Netty startup self-test was not observed')
    require('TIDEFRAME_RUNTIME_READY demos=48 schema=6 api=true passive=true selfInput=true' in log,
            f'{distribution}: self-input guarded 0.8 runtime was not observed')
    require(re.search(r'TIDEFRAME_STATUS.*screens=58 .*menuElements=6 .*pack=tideframe\.zip', log),
            f'{distribution}: expected 58 compiled screens and canonical close control were not observed')
    require(screen_inventory(folder) == screens, f'{distribution}: probe layouts changed')
    require(sha(folder / 'plugins/tideframe.jar') == digest, f'{distribution}: tested JAR changed')
    pack = folder / 'plugins/Tideframe/packs/tideframe.zip'
    require(result.get('packHttp', {}).get('status') == 200 and result['packHttp'].get('sha256') == sha(pack)
            and result['packHttp'].get('bytes') == pack.stat().st_size,
            f'{distribution}: served pack differs from saved pack')
    return result


def guide_inputs():
    guide = (ROOT / 'docs/PLAY_DEMO_ES.md').read_text(encoding='utf-8').replace('../evidence/', 'evidence/').replace('](RUNTIME_V08_REPORT.md)', '](docs/RUNTIME_V08_REPORT.md)')
    require('0.8.0' in guide, 'The current play guide must describe version 0.8.0 before staging')
    receipts = []
    for name in re.findall(r'\]\(evidence/([^)]+)\)', guide):
        # Evidence links may name only direct authored evidence files.
        require(Path(name).name == name, f'Unsafe guide evidence path: {name}')
        if name == 'runtime-v08-staging.json':
            continue  # This script writes and copies its own receipt at the end.
        require((ROOT / 'evidence' / name).is_file(), f'Unavailable guide evidence: {name}')
        receipts.append(ROOT / 'evidence' / name)
    return guide, receipts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('prepare', 'install'))
    mode = parser.parse_args().mode
    for folder in (SERVER, PROBE, FOLIA):
        validate_folder(folder)
    require(sha(SERVER / 'plugins/tideframe.jar') == PREVIOUS,
            'Unexpected original plugin version; preserve operator changes')
    stopped()
    screens = screen_inventory(SERVER)
    if mode == 'prepare':
        require(not PREPARATION.exists(), 'An earlier 0.8 preparation exists; inspect it instead of replacing evidence')
        # Validate both probes before the first backup or write.
        for folder in (PROBE, FOLIA):
            require(sha(folder / 'plugins/tideframe.jar') == PREVIOUS, 'Unexpected probe plugin version')
            require(screen_inventory(folder) == screens, 'Probe JSON drift: no layouts will be overwritten')
        backups = []
        for folder in (PROBE, FOLIA):
            saved, _ = backup(folder)
            backups.append(str(saved))
        stopped()
        write_receipt(PREPARATION.name, {
            'preparedProbes': True, 'originalServerModified': False, 'previousVersion': '0.7.0',
            'previousPluginSha256': PREVIOUS, 'expectedScreens': screens, 'diskJsonScreens': 10,
            'builtinRuntimeScreens': 48, 'expectedCompiledScreens': 58, 'backups': backups,
            'probeJsonByteIdenticalToOriginal': True, 'nativeClientVerified': False})
        return

    require(not (ROOT / 'evidence/runtime-v08-staging.json').exists(),
            'An earlier staging receipt exists; inspect it instead of replacing verified evidence')
    preparation = json.loads(PREPARATION.read_text(encoding='utf-8'))
    require(preparation.get('preparedProbes') is True and preparation.get('expectedScreens') == screens,
            'Original layouts changed since probe preparation; retain all changes and repeat reviewed verification')
    for name in preparation.get('backups', []):
        target = Path(name)
        require(target.resolve().parent == WORK.resolve() and target.is_dir(), 'Missing prepared probe backup')
    require(len(preparation.get('backups', [])) == 2, 'Two prepared probe backups are required')
    digest = sha(JAR)
    verified = {distribution: verify_smoke(distribution, folder, digest, screens)
                for distribution, folder in (('paper', PROBE), ('folia', FOLIA))}
    pack = PROBE / 'plugins/Tideframe/packs/tideframe.zip'
    require(sha(pack) == sha(FOLIA / 'plugins/Tideframe/packs/tideframe.zip'), 'Paper/Folia pack mismatch')
    starter = (ROOT / 'tools/start-demo.ps1').read_text(encoding='utf-8')
    require(f"$pluginSha256 = '{digest}'" in starter, 'Starter does not pin this exact tested JAR')
    guide, receipts = guide_inputs()
    # The operator may have played in the original world after prepare. Back up
    # its CURRENT contents here; never restore the older disposable probe world.
    world = inventory(SERVER / 'tideframe-test')
    props = sha(SERVER / 'server.properties')
    config = sha(SERVER / 'plugins/Tideframe/config.yml')
    saved, original = backup(SERVER)
    require(inventory(saved / 'tideframe-test') == world and
            inventory(saved / 'plugins/Tideframe/screens') == screens, 'Current world/layout backup mismatch')
    stopped()
    require(inventory(SERVER) == original, 'Original directory changed after full backup; refuse staging')
    shutil.copy2(JAR, SERVER / 'plugins/tideframe.jar')
    shutil.copy2(pack, SERVER / 'plugins/Tideframe/packs/tideframe.zip')
    shutil.copy2(ROOT / 'tools/start-demo.ps1', SERVER / 'INICIAR_PRUEBA.ps1')
    shutil.copy2(ROOT / 'tools/start-demo.cmd', SERVER / 'INICIAR_PRUEBA.cmd')
    shutil.copytree(ROOT / 'editor', SERVER / 'editor', dirs_exist_ok=True)
    shutil.copytree(ROOT / 'docs', SERVER / 'docs', dirs_exist_ok=True)
    projects = SERVER / 'PROYECTOS_EDITOR'
    projects.mkdir(exist_ok=True)
    for source in (ROOT / 'examples/runtime-project').glob('*.tideframe-project.json'):
        shutil.copy2(source, projects / source.name)
    shutil.copytree(ROOT / 'examples/runtime-demo', SERVER / 'EJEMPLOS_RUNTIME', dirs_exist_ok=True)
    (SERVER / 'LEEME.md').write_text(guide, encoding='utf-8')
    (SERVER / 'evidence').mkdir(exist_ok=True)
    for receipt in receipts:
        shutil.copy2(receipt, SERVER / 'evidence' / receipt.name)
    require(inventory(SERVER / 'tideframe-test') == world, 'World bytes changed during stage')
    require(screen_inventory(SERVER) == screens, 'Operator layout bytes changed during stage')
    require(sha(SERVER / 'server.properties') == props and
            sha(SERVER / 'plugins/Tideframe/config.yml') == config, 'Operator configuration changed during stage')
    stopped()
    write_receipt('runtime-v08-staging.json', {
        'backup': str(saved), 'fullOriginalBackupVerified': True, 'sourceWasStopped': True,
        'previousVersion': '0.7.0', 'newVersion': '0.8.0', 'pluginSha256': digest,
        'packSha256': sha(SERVER / 'plugins/Tideframe/packs/tideframe.zip'),
        'worldFiles': len(world), 'worldBytes': sum(path.stat().st_size for path in (SERVER / 'tideframe-test').rglob('*') if path.is_file()),
        'worldByteIdenticalAfterStage': True, 'previousLayoutsPreserved': 10, 'diskJsonScreens': 10,
        'builtinRuntimeScreens': 48, 'screens': 58, 'matchesTestedCloneScreens': True,
        'configurationByteIdenticalAfterStage': True, 'originalServerStartedByThisScript': False,
        'nativeClientVerified': False, 'paperAndFoliaSelfInputStartupGuardVerified': True,
        'probeStages': {name: data['stage'] for name, data in verified.items()}})
    shutil.copy2(ROOT / 'evidence/runtime-v08-staging.json', SERVER / 'evidence/runtime-v08-staging.json')


if __name__ == '__main__':
    main()
