"""One-shot backed-up upgrade of the existing, stopped Tideframe test servers."""
from pathlib import Path
import datetime, hashlib, json, shutil, socket, subprocess

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT.parents[1]/'work'
JAR=ROOT/'build/libs/tideframe-0.4.1.jar'
EXPECTED='dcd6cf91370ce9a52f106f3c3cf0e59b99e438c66fcad6f1ae079d0246f96c38'
PREVIOUS='84e66a928c58e42c58e61793eb3a18a482589cb1dd9318a4fb7927afa941a479'
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def inventory(folder):
    assert not any(p.is_symlink() for p in folder.rglob('*'))
    return {p.relative_to(folder).as_posix():digest(p) for p in folder.rglob('*') if p.is_file()}
assert digest(JAR)==EXPECTED
for port in [25586,25587,8136,8137]:
    with socket.socket() as sock:assert sock.connect_ex(('127.0.0.1',port))!=0,'Test port occupied; do not replace live files'
targets=[('tideframe-server','tideframe-test'),('tideframe-folia-server','tideframe-folia-test')]
before={}
for name,world in targets:
    server=(WORK/name).resolve();assert server.parent==WORK.resolve()
    assert (server/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
    assert digest(server/'plugins/tideframe.jar')==PREVIOUS,'Unexpected plugin; refuse overwrite'
    lock=(server/world/'session.lock').as_posix().replace("'","''")
    check="$s=[System.IO.File]::Open('"+lock+"',[System.IO.FileMode]::Open,[System.IO.FileAccess]::Read,[System.IO.FileShare]::None);$s.Dispose()"
    subprocess.run(['powershell.exe','-NoProfile','-Command',check],check=True,capture_output=True)
    before[name]={'world':inventory(server/world),'screens':inventory(server/'plugins/Tideframe/screens')}
backup=WORK/('tideframe-pre-movement-v041-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
assert backup.parent.resolve()==WORK.resolve() and not backup.exists();backup.mkdir()
result=[]
for name,world in targets:
    server=WORK/name;snapshot=backup/name;snapshot.mkdir()
    shutil.copytree(server/'plugins',snapshot/'plugins');shutil.copytree(server/world,snapshot/world)
    shutil.copy2(server/'server.properties',snapshot/'server.properties')
    for file in ['INICIAR_PRUEBA.ps1','INICIAR_PRUEBA.cmd','LEEME.md']:
        if (server/file).exists():shutil.copy2(server/file,snapshot/file)
    assert inventory(snapshot/world)==before[name]['world']
    assert inventory(snapshot/'plugins/Tideframe/screens')==before[name]['screens']
    shutil.copy2(JAR,server/'plugins/tideframe.jar')
    assert inventory(server/world)==before[name]['world']
    assert inventory(server/'plugins/Tideframe/screens')==before[name]['screens']
    result.append({'server':name,'pluginSha256':digest(server/'plugins/tideframe.jar'),
                   'worldFilesPreserved':len(before[name]['world']),'screensPreserved':len(before[name]['screens']),
                   'worldAndScreensByteIdenticalBeforeAfterStage':True,'backupVerified':True})
shutil.copy2(ROOT/'tools/start-demo.ps1',WORK/'tideframe-server/INICIAR_PRUEBA.ps1')
(ROOT/'evidence/movement-v041-staging.json').write_text(json.dumps({'backup':str(backup),'staged':result},indent=2)+'\n')
print(json.dumps({'backup':str(backup),'staged':result},indent=2))
