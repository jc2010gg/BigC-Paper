"""One-shot backup and screen-HUD upgrade of the two existing, stopped test servers."""
from pathlib import Path
import datetime, hashlib, json, shutil, socket, subprocess

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT.parents[1]/'work'
JAR=ROOT/'build/libs/tideframe-0.4.0.jar'
EXPECTED='84e66a928c58e42c58e61793eb3a18a482589cb1dd9318a4fb7927afa941a479'
assert hashlib.sha256(JAR.read_bytes()).hexdigest()==EXPECTED
for port in [25586,25587,8136,8137]:
    with socket.socket() as sock:assert sock.connect_ex(('127.0.0.1',port))!=0,'A test server is running; do not replace live files'
# The JVM holds session.lock open. Check exclusive read access before copying saves.
for name,world in [('tideframe-server','tideframe-test'),('tideframe-folia-server','tideframe-folia-test')]:
    target=(WORK/name).resolve();assert target.parent==WORK.resolve()
    assert (target/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
    assert hashlib.sha256((target/'plugins/tideframe.jar').read_bytes()).hexdigest()!=EXPECTED,'Already staged; preserve current changes'
    lock=(target/world/'session.lock').as_posix().replace("'","''")
    check="$s=[System.IO.File]::Open('"+lock+"',[System.IO.FileMode]::Open,[System.IO.FileAccess]::Read,[System.IO.FileShare]::None);$s.Dispose()"
    subprocess.run(['powershell.exe','-NoProfile','-Command',check],check=True,capture_output=True)
backup=WORK/('tideframe-pre-hud-v4-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
assert backup.parent.resolve()==WORK.resolve() and not backup.exists();backup.mkdir()
result=[]
for name,world in [('tideframe-server','tideframe-test'),('tideframe-folia-server','tideframe-folia-test')]:
    server=WORK/name;snapshot=backup/name;snapshot.mkdir()
    shutil.copytree(server/'plugins',snapshot/'plugins')
    shutil.copytree(server/world,snapshot/world)
    shutil.copy2(server/'server.properties',snapshot/'server.properties')
    for file in ['INICIAR_PRUEBA.ps1','INICIAR_PRUEBA.cmd','LEEME.md']:
        if (server/file).exists():shutil.copy2(server/file,snapshot/file)
    shutil.copy2(JAR,server/'plugins/tideframe.jar')
    result.append({'server':name,'pluginSha256':EXPECTED,'screensPreserved':len(list((server/'plugins/Tideframe/screens').glob('*.json'))),'worldBackedUp':True})
shutil.copy2(ROOT/'tools/start-demo.ps1',WORK/'tideframe-server/INICIAR_PRUEBA.ps1')
(ROOT/'evidence/hud-v4-staging.json').write_text(json.dumps({'backup':str(backup),'staged':result},indent=2)+'\n')
print(json.dumps({'backup':str(backup),'staged':result},indent=2))
