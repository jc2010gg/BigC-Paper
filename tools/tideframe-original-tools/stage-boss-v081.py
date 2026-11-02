"""Back up, stage, or package only the stopped marked 0.8.1 boss-HUD demo."""
from pathlib import Path
import argparse
import datetime
import hashlib
import importlib.util
import json
import re
import shutil
import zipfile

SPEC = importlib.util.spec_from_file_location('stage_runtime_v08', Path(__file__).with_name('stage-runtime-v08.py'))
BASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BASE)
ROOT, WORK, SERVER, PROBE, FOLIA = BASE.ROOT, BASE.WORK, BASE.SERVER, BASE.PROBE, BASE.FOLIA
require, sha, inventory, stopped = BASE.require, BASE.sha, BASE.inventory, BASE.stopped
OLD = ROOT.parent / 'Tideframe-demo-0.8.0'
DEMO = ROOT.parent / 'Tideframe-demo-0.8.1'
JAR = ROOT / 'build/libs/tideframe-0.8.1.jar'
PREVIOUS = '9da9390d744749b26b9f878fc71ddb48ebabb4261ab2bf99b74654357dde4a7d'
PACK = '7b21f04843b6c1527c51718a967c1e9a7f9f88db6a31fafe23ab254b71d2c048'

def receipt_path(mode):
    return ROOT / f'evidence/boss-v081-{mode}.json'

def record(mode, result):
    target = receipt_path(mode)
    require(not target.exists(), f'Refuse to overwrite receipt: {target.name}')
    target.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))

def inventory_digest(folder):
    return hashlib.sha256(json.dumps(inventory(folder), sort_keys=True).encode()).hexdigest()

def backup(folder):
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S-%fZ')
    target = WORK / f'tideframe-pre-boss-v081-{folder.name}-{stamp}'
    require(target.resolve().parent == WORK.resolve() and not target.exists(), 'Unsafe or existing backup')
    before = inventory(folder)
    shutil.copytree(folder, target)
    require(inventory(target) == before == inventory(folder), 'Full backup mismatch or source changed')
    return target, before

def verify_smoke(distribution, folder, digest, screens):
    stem = ROOT / f'evidence/boss-v081-{distribution}-server'
    data = json.loads(stem.with_suffix('.json').read_text(encoding='utf-8'))
    require(data.get('stage') == f'boss-v081-{distribution}' and data.get('distribution') == distribution
            and data.get('pluginSha256') == digest, 'Smoke does not identify this exact 0.8.1 JAR')
    require(all(data.get(key) is True for key in ('ready', 'consoleStatus', 'cameraBindingsVerified',
            'saveObserved', 'stopObserved')) and data.get('exitCode') == 0
            and data.get('forcedTermination') is False and 'error' not in data, 'Smoke did not finish cleanly')
    ports = ('25588', '8138') if distribution == 'paper' else ('25589', '8139')
    require(data.get('portsReleased') == dict.fromkeys(ports, True)
            and data.get('status', {}).get('version', {}).get('protocol') == 776, 'Smoke ports/protocol mismatch')
    log = stem.with_suffix('.log').read_text(encoding='utf-8')
    for text in ('Done (', 'Stopping server', 'TIDEFRAME_CAMERA_BINDINGS_READY protocol=776 playerProbe=false',
                 'TIDEFRAME_RUNTIME_READY demos=48 schema=6 api=true passive=true selfInput=true',
                 'Saved all worlds' if distribution == 'folia' else 'Saved the game'):
        require(text in log, f'Missing actual smoke log observation: {text}')
    require(re.search(r'TIDEFRAME_STATUS.*screens=58 .*menuElements=6 .*pack=tideframe\.zip', log),
            'Smoke did not observe 58 screens and six menu elements')
    pack = folder / 'plugins/Tideframe/packs/tideframe.zip'
    require(sha(folder / 'plugins/tideframe.jar') == digest and BASE.screen_inventory(folder) == screens,
            'Tested probe JAR/layouts changed')
    require(sha(pack) == PACK and data.get('packHttp') == {'status': 200, 'bytes': pack.stat().st_size,
            'sha256': PACK}, 'Saved/served pack differs from unchanged 0.8 pack')
    return data

def support_inputs(digest, mode):
    require(f"$pluginSha256 = '{digest}'" in (ROOT / 'tools/start-demo.ps1').read_text(encoding='utf-8'),
            'Launcher must pin the exact tested 0.8.1 JAR')
    BASE.no_links(ROOT / 'docs')
    guide = (ROOT / 'docs/PLAY_DEMO_ES.md').read_text(encoding='utf-8').replace('../evidence/', 'evidence/')
    guide = re.sub(r'\]\(([^/()]+\.md)\)', r'](docs/\1)', guide)
    require('0.8.1' in guide, 'The current guide must describe 0.8.1')
    evidence = []
    for name in re.findall(r'\]\(evidence/([^)]+)\)', guide):
        require(Path(name).name == name, 'Unsafe guide evidence path')
        if name == 'boss-v081-portable.json' or (mode == 'install' and name == 'boss-v081-staging.json'):
            continue
        source = ROOT / 'evidence' / name
        require(source.is_file() and not source.is_symlink(), f'Missing guide evidence: {name}')
        evidence.append(source)
    return guide, evidence

def copy_support(target, guide, evidence):
    for suffix in ('ps1', 'cmd'):
        shutil.copy2(ROOT / f'tools/start-demo.{suffix}', target / f'INICIAR_PRUEBA.{suffix}')
    shutil.copytree(ROOT / 'docs', target / 'docs', dirs_exist_ok=True)
    (target / 'LEEME.md').write_text(guide, encoding='utf-8')
    (target / 'evidence').mkdir(exist_ok=True)
    for source in evidence:
        shutil.copy2(source, target / 'evidence' / source.name)

def properties(path):
    result = {}
    for line in path.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if not line or line.startswith(('#', '!')):
            continue
        match = re.fullmatch(r'([A-Za-z0-9_.-]+)=(.*)', line)
        require(match and not line.endswith('\\') and match[1] not in result, 'Ambiguous/duplicate properties key')
        result[match[1]] = match[2]
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('prepare', 'install', 'portable'))
    mode = parser.parse_args().mode
    output = 'preparation' if mode == 'prepare' else 'staging' if mode == 'install' else 'portable'
    require(not receipt_path(output).exists(), 'Existing receipt must be inspected, never overwritten')
    for folder in (SERVER, PROBE, FOLIA):
        BASE.validate_folder(folder)
    stopped()
    screens = BASE.screen_inventory(SERVER)
    require(sha(SERVER / 'plugins/Tideframe/packs/tideframe.zip') == PACK, 'Original 0.8 pack changed')
    if mode == 'prepare':
        for folder in (SERVER, PROBE, FOLIA):
            require(sha(folder / 'plugins/tideframe.jar') == PREVIOUS, 'Unexpected 0.8 plugin; retain changes')
            require(BASE.screen_inventory(folder) == screens, 'Probe layout drift; no JSON will be overwritten')
        backups = [backup(folder)[0] for folder in (PROBE, FOLIA)]
        stopped()
        record(output, {'preparedProbes': True, 'previousVersion': '0.8.0', 'previousPluginSha256': PREVIOUS,
                'expectedScreens': screens, 'backups': {str(path): inventory_digest(path) for path in backups},
                'originalServerModified': False, 'nativeClientVerified': False})
        return
    digest = sha(JAR)
    guide, evidence = support_inputs(digest, mode)
    world = inventory(SERVER / 'tideframe-test')
    config = sha(SERVER / 'plugins/Tideframe/config.yml')
    props = sha(SERVER / 'server.properties')
    editor = inventory(SERVER / 'editor')
    if mode == 'install':
        require(sha(SERVER / 'plugins/tideframe.jar') == PREVIOUS, 'Original is not the expected 0.8 JAR')
        prep = json.loads(receipt_path('preparation').read_text(encoding='utf-8'))
        require(prep.get('preparedProbes') is True and prep.get('expectedScreens') == screens
                and len(prep.get('backups', {})) == 2, 'Missing preparation or operator layout drift')
        for name, fingerprint in prep['backups'].items():
            path = Path(name)
            require(path.resolve().parent == WORK.resolve() and path.name.startswith('tideframe-pre-boss-v081-')
                    and inventory_digest(path) == fingerprint, 'Prepared backup changed or escaped work')
        verified = {name: verify_smoke(name, folder, digest, screens)
                    for name, folder in (('paper', PROBE), ('folia', FOLIA))}
        saved, before = backup(SERVER)
        stopped()
        require(inventory(SERVER) == before, 'Original changed after current full backup')
        shutil.copy2(JAR, SERVER / 'plugins/tideframe.jar')
        copy_support(SERVER, guide, evidence)
        target = SERVER
    else:
        require(DEMO.resolve().parent == ROOT.parent.resolve() and not DEMO.exists(), 'Unsafe/existing portable')
        BASE.no_links(OLD)
        stage = json.loads(receipt_path('staging').read_text(encoding='utf-8'))
        require(stage.get('pluginSha256') == digest and stage.get('worldByteIdenticalAfterStage') is True
                and sha(SERVER / 'plugins/tideframe.jar') == digest, 'Verified current 0.8.1 staging is required')
        portable_props, current = properties(OLD / 'server.properties'), properties(SERVER / 'server.properties')
        for key, value in {'management-server-enabled': 'false', 'online-mode': 'true', 'server-ip': '127.0.0.1',
                           'server-port': '25586', 'enable-rcon': 'false', 'enable-query': 'false', 'level-name': 'tideframe-test'}.items():
            require(portable_props.get(key) == current.get(key) == value, 'Loopback configuration drift')
        require(portable_props.get('management-server-secret') == portable_props.get('rcon.password', '') == '',
                'Prior portable properties are not sanitized')
        require(sha(OLD / 'plugins/Tideframe/packs/tideframe.zip') == PACK and inventory(OLD / 'editor') == editor,
                'Prior portable pack/editor changed')
        settings = (SERVER / 'plugins/Tideframe/config.yml').read_text(encoding='utf-8')
        for key, line in {'bind': '  bind: 127.0.0.1', 'port': '  port: 8136', 'public-url': '  public-url: http://127.0.0.1:8136/packs/'}.items():
            require(line in settings.splitlines() and len(re.findall(r'^\s*'+key+r'\s*:', settings, re.M)) == 1,
                    'Portable config must match unique original loopback settings')
        DEMO.mkdir()
        for name in ('editor', 'EJEMPLOS_CALIDAD', 'EJEMPLOS_RUNTIME', 'PROYECTOS_EDITOR', 'evidence'):
            shutil.copytree(OLD / name, DEMO / name)
        for name in ('.tideframe-disposable', 'ABRIR_EDITOR.cmd', 'LICENSE', 'server.properties'):
            shutil.copy2(OLD / name, DEMO / name)
        shutil.copytree(SERVER / 'tideframe-test', DEMO / 'tideframe-test')
        shutil.copytree(SERVER / 'plugins/Tideframe/screens', DEMO / 'plugins/Tideframe/screens')
        (DEMO / 'plugins/Tideframe/packs').mkdir()
        shutil.copy2(SERVER / 'plugins/Tideframe/packs/tideframe.zip', DEMO / 'plugins/Tideframe/packs/tideframe.zip')
        shutil.copy2(SERVER / 'plugins/Tideframe/config.yml', DEMO / 'plugins/Tideframe/config.yml')
        shutil.copy2(JAR, DEMO / 'plugins/tideframe.jar')
        copy_support(DEMO, guide, evidence)
        (DEMO / 'WORLD_SNAPSHOT_V081.json').write_text(json.dumps({'files': world,
                'source': 'Current stopped original world at 0.8.1 portable creation.'}, indent=2) + '\n', encoding='utf-8')
        require(all(path.relative_to(DEMO).as_posix() == 'plugins/tideframe.jar' for path in DEMO.rglob('*.jar')),
                'Foreign executable JAR in portable')
        with zipfile.ZipFile(DEMO / 'plugins/tideframe.jar') as archive:
            require(archive.testzip() is None and 'LICENSE' in archive.namelist(), 'JAR CRC/license mismatch')
        target = DEMO
    require(inventory(SERVER / 'tideframe-test') == world == inventory(target / 'tideframe-test')
            and BASE.screen_inventory(target) == screens, 'Current world/layout bytes changed')
    require(sha(SERVER / 'server.properties') == props and sha(SERVER / 'plugins/Tideframe/config.yml') == config
            and sha(target / 'plugins/Tideframe/config.yml') == config, 'Original configuration bytes changed')
    require(inventory(SERVER / 'editor') == editor == inventory(target / 'editor')
            and sha(target / 'plugins/Tideframe/packs/tideframe.zip') == PACK, 'Editor/pack changed')
    stopped()
    result = {'version': '0.8.1', 'previousVersion': '0.8.0', 'pluginSha256': digest, 'packSha256': PACK,
              'worldFiles': len(world), 'worldBytes': sum(path.stat().st_size for path in (SERVER / 'tideframe-test').rglob('*') if path.is_file()),
              'sourceWasStopped': True, 'worldByteIdenticalAfterStage': True, 'currentOperatorProgressIncluded': True,
              'configurationByteIdenticalAfterStage': True, 'installedJsonScreens': 10, 'compiledScreens': 58,
              'packAndEditorUnchanged': True, 'originalServerStartedByThisScript': False, 'nativeClientVerified': False}
    if mode == 'install':
        after = inventory(SERVER)
        permitted = lambda name: name in ('plugins/tideframe.jar', 'INICIAR_PRUEBA.ps1', 'INICIAR_PRUEBA.cmd', 'LEEME.md') or name.startswith(('docs/', 'evidence/'))
        require({key: value for key, value in before.items() if not permitted(key)} ==
                {key: value for key, value in after.items() if not permitted(key)}, 'Unrelated original files changed')
        result.update(backup=str(saved), fullOriginalBackupVerified=True, probeStages={key: value['stage'] for key, value in verified.items()})
    else:
        result.update(worldByteIdenticalToStoppedSource=True, portablePath=str(DEMO), excluded=['Paper/Minecraft', 'EULA', 'account lists/caches', 'server software caches'])
    record(output, result)
    shutil.copy2(receipt_path(output), target / 'evidence' / receipt_path(output).name)

if __name__ == '__main__':
    main()
