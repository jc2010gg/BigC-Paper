"""Prepare isolated probes or back up/update the stopped marked local demo. Never start a server."""
from pathlib import Path
import datetime,hashlib,json,re,shutil,socket,subprocess,sys
ROOT=Path(__file__).resolve().parents[1];WORK=ROOT.parents[1]/'work'
SERVER=WORK/'tideframe-server';PROBE=WORK/'tideframe-quality-server';FOLIA=WORK/'tideframe-quality-folia-server'
PREVIOUS='aeaf00d011610ba2fc0fd5755e8c8dd85cad89f5b3ebd601eee66e62f2e6cec4'
JAR=ROOT/'build/libs/tideframe-0.6.0.jar'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def inventory(folder):return {p.relative_to(folder).as_posix():sha(p) for p in sorted(folder.rglob('*')) if p.is_file()}
def check_server(folder):
    assert folder.resolve().parent==WORK.resolve()
    assert (folder/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
def stopped():
    for port in [25586,8136,25588,8138,25589,8139]:
        with socket.socket() as s:
            s.settimeout(.5);assert s.connect_ex(('127.0.0.1',port))!=0,'Test server active; refuse live update'
    lock=(SERVER/'tideframe-test/session.lock').as_posix().replace("'","''")
    cmd="$s=[IO.File]::Open('"+lock+"',[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$s.Dispose()"
    subprocess.run(['powershell.exe','-NoProfile','-Command',cmd],check=True,capture_output=True)
def config128(folder):
    p=folder/'plugins/Tideframe/config.yml';old=p.read_text()
    changed,n=re.subn(r'(?m)^(\s*elements:)\s*\d+\s*$',r'\g<1> 128',old)
    assert n==1,'Unexpected configuration element limit'
    p.write_text(changed)
def backup(folder,label,world=False):
    target=WORK/(label+'-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%SZ'))
    assert target.resolve().parent==WORK.resolve() and not target.exists();target.mkdir()
    shutil.copytree(folder/'plugins',target/'plugins')
    if world:shutil.copytree(folder/'tideframe-test',target/'tideframe-test')
    for name in ['server.properties','INICIAR_PRUEBA.ps1','INICIAR_PRUEBA.cmd','LEEME.md']:
        if (folder/name).exists():shutil.copy2(folder/name,target/name)
    return target
for folder in [SERVER,PROBE,FOLIA]:check_server(folder)
assert sys.argv[1:] in [['prepare'],['install']]
assert sha(SERVER/'plugins/tideframe.jar')==PREVIOUS,'Unexpected current plugin; preserve user changes'
stopped();screens=inventory(SERVER/'plugins/Tideframe/screens')
own=ROOT/'examples/advanced-demo/advanced_low.json'
assert 'advanced_low.json' not in screens,'Preserve an existing advanced_low layout'
expected={**screens,'advanced_low.json':sha(own)}
if sys.argv[1]=='prepare':
    backups=[]
    for folder in [PROBE,FOLIA]:
        backups.append(str(backup(folder,'tideframe-probe-pre-v06-'+folder.name)))
        screen_dir=folder/'plugins/Tideframe/screens'
        assert inventory(screen_dir)==screens,'Probe layout drift: manual reconcile before replacing'
        shutil.copy2(own,screen_dir/'advanced_low.json');config128(folder)
    result={'preparedProbes':True,'originalServerModified':False,'sourceLayouts':screens,'expectedProbeLayouts':expected,'backups':backups,'nativeClientVerified':False}
    (ROOT/'evidence/advanced-v06-preparation.json').write_text(json.dumps(result,indent=2)+'\n')
else:
    digest=sha(JAR)
    assert sha(PROBE/'plugins/tideframe.jar')==digest and sha(FOLIA/'plugins/tideframe.jar')==digest
    for distribution in ['paper','folia']:
        result=json.loads((ROOT/f'evidence/advanced-v06-{distribution}-server.json').read_text())
        assert result['pluginSha256']==digest and result['ready'] and result['saveObserved'] and result['exitCode']==0 and not result['forcedTermination']
    assert inventory(PROBE/'plugins/Tideframe/screens')==expected==inventory(FOLIA/'plugins/Tideframe/screens')
    assert sha(PROBE/'plugins/Tideframe/packs/tideframe.zip')==sha(FOLIA/'plugins/Tideframe/packs/tideframe.zip')
    before=inventory(SERVER/'tideframe-test');saved=backup(SERVER,'tideframe-pre-advanced-v06',True)
    assert inventory(saved/'tideframe-test')==before and inventory(saved/'plugins/Tideframe/screens')==screens
    stopped();assert inventory(SERVER/'tideframe-test')==before
    shutil.copy2(JAR,SERVER/'plugins/tideframe.jar');shutil.copy2(own,SERVER/'plugins/Tideframe/screens/advanced_low.json')
    shutil.copy2(PROBE/'plugins/Tideframe/packs/tideframe.zip',SERVER/'plugins/Tideframe/packs/tideframe.zip')
    shutil.copy2(ROOT/'tools/start-demo.ps1',SERVER/'INICIAR_PRUEBA.ps1');config128(SERVER)
    assert inventory(SERVER/'tideframe-test')==before and inventory(SERVER/'plugins/Tideframe/screens')==expected
    result={'backup':str(saved),'sourceWasStopped':True,'previousVersion':'0.5.0','newVersion':'0.6.0','pluginSha256':digest,
        'packSha256':sha(SERVER/'plugins/Tideframe/packs/tideframe.zip'),'worldFiles':len(before),'worldByteIdenticalAfterStage':True,
        'previousLayoutsPreserved':len(screens),'newLayout':'advanced_low.json','screens':len(expected),'limitElements':128,
        'matchesTestedCloneScreens':True,'originalServerStartedByThisScript':False,'nativeClientVerified':False}
    (ROOT/'evidence/advanced-v06-staging.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
