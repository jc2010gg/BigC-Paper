"""Back up and stage only the two existing marked, stopped local test servers."""
from pathlib import Path
import datetime,hashlib,json,shutil,socket
ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT.parents[1]/'work'
PLUGIN=ROOT/'build/libs/tideframe-0.3.0.jar'
EXPECTED='16b1d43cc25f0f9eba47caaad4f58b98b661b0ddbe5dd5ff9c9a31b11b26cf48'
assert hashlib.sha256(PLUGIN.read_bytes()).hexdigest()==EXPECTED
for port in [25586,25587,8136,8137]:
    with socket.socket() as sock:assert sock.connect_ex(('127.0.0.1',port))!=0,'A test server is running; do not replace live files'
backup=WORK/('tideframe-pre-v3-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
assert not backup.exists();backup.mkdir()
result=[]
for name in ['tideframe-server','tideframe-folia-server']:
    server=(WORK/name).resolve();assert server.parent==WORK.resolve()
    assert (server/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
    assert 'server-ip=127.0.0.1' in (server/'server.properties').read_text()
    snapshot=backup/name;snapshot.mkdir()
    shutil.copytree(server/'plugins',snapshot/'plugins')
    shutil.copy2(server/'server.properties',snapshot/'server.properties')
    world=server/('tideframe-test' if name=='tideframe-server' else 'tideframe-folia-test')
    assert world.is_dir();shutil.copytree(world,snapshot/world.name)
    shutil.copy2(PLUGIN,server/'plugins/tideframe.jar')
    screens=server/'plugins/Tideframe/screens'
    for id in ['image_demo','editor_v3_demo']:
        assert not (screens/(id+'.json')).exists(),'Preserve an existing custom demo rather than overwrite it'
        shutil.copy2(ROOT/'build/demos'/(id+'.json'),screens/(id+'.json'))
    # Keep each server's own defaults/configuration. Copy the old editor demo only if absent.
    if not (screens/'editor_demo.json').exists():
        shutil.copy2(WORK/'tideframe-server/plugins/Tideframe/screens/editor_demo.json',screens/'editor_demo.json')
    result.append({'server':name,'jsonFiles':len(list(screens.glob('*.json'))),'pluginSha256':EXPECTED,'worldBackedUp':True})
server=WORK/'tideframe-server'
shutil.copy2(ROOT/'tools/start-demo.ps1',server/'INICIAR_PRUEBA.ps1')
shutil.copy2(ROOT/'docs/PLAY_DEMO_ES.md',server/'LEEME.md')
(ROOT/'evidence/v3-staging.json').write_text(json.dumps({'backup':str(backup),'staged':result},indent=2)+'\n')
print(json.dumps({'backup':str(backup),'staged':result},indent=2))
