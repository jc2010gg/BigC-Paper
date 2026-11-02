"""Back up and update only the stopped, marked Tideframe test directories."""
from pathlib import Path
import datetime, hashlib, json, shutil, socket, subprocess, sys
ROOT=Path(__file__).resolve().parents[1]; WORK=ROOT.parents[1]/'work'
SERVER=WORK/'tideframe-server'; PROBE=WORK/'tideframe-quality-server'; FOLIA=WORK/'tideframe-quality-folia-server'
PREVIOUS='c179ffc940253f8f0f6ce8af0d6d1c71ff4eef9b26d6d73e2598027c2fbcffe1'
JAR=ROOT/'build/libs/tideframe-0.7.0.jar'; OWN=ROOT/'examples/creation-demo/creation_demo.json'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def inventory(folder):return {p.relative_to(folder).as_posix():sha(p) for p in sorted(folder.rglob('*')) if p.is_file()}
def stopped():
    for port in [25586,8136,25588,8138,25589,8139]:
        with socket.socket() as s:
            s.settimeout(.5);assert s.connect_ex(('127.0.0.1',port))!=0,'Refuse a live update'
    lock=(SERVER/'tideframe-test/session.lock').as_posix().replace("'","''")
    command="$s=[IO.File]::Open('"+lock+"',[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$s.Dispose()"
    subprocess.run(['powershell.exe','-NoProfile','-Command',command],check=True,capture_output=True)
def backup(folder,world=False):
    target=WORK/('tideframe-pre-creation-v07-'+folder.name+'-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%SZ'))
    assert target.resolve().parent==WORK.resolve() and not target.exists();target.mkdir()
    for name in ['plugins','editor']:
        if (folder/name).exists():shutil.copytree(folder/name,target/name)
    if world:shutil.copytree(folder/'tideframe-test',target/'tideframe-test')
    for name in ['server.properties','INICIAR_PRUEBA.ps1','INICIAR_PRUEBA.cmd','LEEME.md']:
        if (folder/name).exists():shutil.copy2(folder/name,target/name)
    return target
assert sys.argv[1:] in [['prepare'],['install']]
for folder in [SERVER,PROBE,FOLIA]:
    assert folder.resolve().parent==WORK.resolve()
    assert (folder/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
assert sha(SERVER/'plugins/tideframe.jar')==PREVIOUS,'Unexpected source version; preserve operator changes'
stopped();before_screens=inventory(SERVER/'plugins/Tideframe/screens')
assert 'creation_demo.json' not in before_screens,'Preserve an existing operator layout'
expected={**before_screens,'creation_demo.json':sha(OWN)}
if sys.argv[1]=='prepare':
    backups=[]
    for folder in [PROBE,FOLIA]:
        assert sha(folder/'plugins/tideframe.jar')==PREVIOUS
        assert inventory(folder/'plugins/Tideframe/screens')==before_screens,'Probe screen drift'
        backups.append(str(backup(folder)))
        shutil.copy2(OWN,folder/'plugins/Tideframe/screens/creation_demo.json')
    result={'preparedProbes':True,'originalServerModified':False,'expectedScreens':expected,'backups':backups,'nativeClientVerified':False}
    evidence='creation-v07-preparation.json'
else:
    digest=sha(JAR)
    for distribution,folder in [('paper',PROBE),('folia',FOLIA)]:
        result=json.loads((ROOT/f'evidence/creation-v07-{distribution}-server.json').read_text())
        assert result['pluginSha256']==digest and result['ready'] and result['saveObserved'] and result['exitCode']==0 and not result['forcedTermination']
        assert inventory(folder/'plugins/Tideframe/screens')==expected and sha(folder/'plugins/tideframe.jar')==digest
    assert sha(PROBE/'plugins/Tideframe/packs/tideframe.zip')==sha(FOLIA/'plugins/Tideframe/packs/tideframe.zip')
    world=inventory(SERVER/'tideframe-test');saved=backup(SERVER,True)
    assert inventory(saved/'tideframe-test')==world and inventory(saved/'plugins/Tideframe/screens')==before_screens
    stopped();assert inventory(SERVER/'tideframe-test')==world
    shutil.copy2(JAR,SERVER/'plugins/tideframe.jar');shutil.copy2(OWN,SERVER/'plugins/Tideframe/screens/creation_demo.json')
    shutil.copy2(PROBE/'plugins/Tideframe/packs/tideframe.zip',SERVER/'plugins/Tideframe/packs/tideframe.zip')
    shutil.copy2(ROOT/'tools/start-demo.ps1',SERVER/'INICIAR_PRUEBA.ps1')
    shutil.copytree(ROOT/'editor',SERVER/'editor',dirs_exist_ok=True)
    assert inventory(SERVER/'tideframe-test')==world and inventory(SERVER/'plugins/Tideframe/screens')==expected
    result={'backup':str(saved),'sourceWasStopped':True,'previousVersion':'0.6.0','newVersion':'0.7.0','pluginSha256':digest,
        'packSha256':sha(SERVER/'plugins/Tideframe/packs/tideframe.zip'),'worldFiles':len(world),'worldByteIdenticalAfterStage':True,
        'previousLayoutsPreserved':len(before_screens),'newLayout':'creation_demo.json','screens':len(expected),
        'matchesTestedCloneScreens':True,'originalServerStartedByThisScript':False,'nativeClientVerified':False}
    evidence='creation-v07-staging.json'
(ROOT/'evidence'/evidence).write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
