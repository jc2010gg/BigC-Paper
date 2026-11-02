"""One-shot backup/update of the stopped marked Paper demo; never overwrite layouts or worlds."""
from pathlib import Path
import datetime,hashlib,json,shutil,socket,subprocess

ROOT=Path(__file__).resolve().parents[1];WORK=ROOT.parents[1]/'work'
SERVER=(WORK/'tideframe-server').resolve();PROBE=(WORK/'tideframe-quality-server').resolve()
EXPECTED='aeaf00d011610ba2fc0fd5755e8c8dd85cad89f5b3ebd601eee66e62f2e6cec4'
PREVIOUS='dcd6cf91370ce9a52f106f3c3cf0e59b99e438c66fcad6f1ae079d0246f96c38'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def inventory(folder):return {p.relative_to(folder).as_posix():sha(p) for p in folder.rglob('*') if p.is_file()}
def stopped():
    for port in [25586,8136,25588,8138]:
        with socket.socket() as sock:
            sock.settimeout(.5);assert sock.connect_ex(('127.0.0.1',port))!=0,'Test server active; refuse live update'
    lock=(SERVER/'tideframe-test/session.lock').as_posix().replace("'","''")
    cmd="$s=[IO.File]::Open('"+lock+"',[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$s.Dispose()"
    subprocess.run(['powershell.exe','-NoProfile','-Command',cmd],check=True,capture_output=True)
assert SERVER.parent==WORK.resolve() and PROBE.parent==WORK.resolve()
assert (SERVER/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
assert sha(SERVER/'plugins/tideframe.jar')==PREVIOUS,'Unexpected current plugin; do not overwrite'
assert sha(ROOT/'build/libs/tideframe-0.5.0.jar')==EXPECTED
assert sha(PROBE/'plugins/tideframe.jar')==EXPECTED
stopped();before=inventory(SERVER/'tideframe-test');screens=inventory(SERVER/'plugins/Tideframe/screens')
assert 'gallery_demo.json' not in screens,'Preserve an existing user gallery layout'
probeScreens=inventory(PROBE/'plugins/Tideframe/screens')
assert screens=={k:v for k,v in probeScreens.items() if k!='gallery_demo.json'},'Layout drift; need a fresh pack probe first'
backup=WORK/('tideframe-pre-quality-v05-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%SZ'))
assert backup.parent.resolve()==WORK.resolve() and not backup.exists();backup.mkdir()
shutil.copytree(SERVER/'plugins',backup/'plugins');shutil.copytree(SERVER/'tideframe-test',backup/'tideframe-test')
for file in ['server.properties','INICIAR_PRUEBA.ps1','INICIAR_PRUEBA.cmd','LEEME.md']:
    if (SERVER/file).exists():shutil.copy2(SERVER/file,backup/file)
assert inventory(backup/'tideframe-test')==before
assert inventory(backup/'plugins/Tideframe/screens')==screens
stopped();assert inventory(SERVER/'tideframe-test')==before
shutil.copy2(ROOT/'build/libs/tideframe-0.5.0.jar',SERVER/'plugins/tideframe.jar')
shutil.copy2(ROOT/'tools/start-demo.ps1',SERVER/'INICIAR_PRUEBA.ps1')
shutil.copy2(ROOT/'examples/gallery-demo/gallery_demo.json',SERVER/'plugins/Tideframe/screens/gallery_demo.json')
shutil.copy2(PROBE/'plugins/Tideframe/packs/tideframe.zip',SERVER/'plugins/Tideframe/packs/tideframe.zip')
after=inventory(SERVER/'plugins/Tideframe/screens')
assert all(after[k]==v for k,v in screens.items()) and len(after)==8
assert inventory(SERVER/'tideframe-test')==before
result={'backup':str(backup),'sourceWasStopped':True,'previousVersion':'0.4.1','newVersion':'0.5.0',
        'pluginSha256':sha(SERVER/'plugins/tideframe.jar'),'packSha256':sha(SERVER/'plugins/Tideframe/packs/tideframe.zip'),
        'worldFiles':len(before),'worldByteIdenticalAfterStage':True,'previousLayoutsPreserved':len(screens),
        'newLayout':'gallery_demo.json','screens':len(after),'matchesTestedCloneScreens':after==probeScreens,
        'originalServerStartedByThisScript':False,'nativeClientVerified':False}
(ROOT/'evidence/quality-v05-staging.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
