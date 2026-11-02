"""Snapshot only the stopped marked test world, own plugin/editor and optional own layouts."""
from pathlib import Path
import hashlib,json,re,shutil,socket
ROOT=Path(__file__).resolve().parents[1];WORK=ROOT.parents[1]/'work';SERVER=WORK/'tideframe-server'
DEMO=ROOT.parent/'Tideframe-demo-0.6.0';OLD=ROOT.parent/'Tideframe-demo-0.5.0'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def inventory(folder):return {p.relative_to(folder).as_posix():sha(p) for p in sorted(folder.rglob('*')) if p.is_file()}
def stopped():
    for port in [25586,8136,25588,8138,25589,8139]:
        with socket.socket() as s:
            s.settimeout(.5);assert s.connect_ex(('127.0.0.1',port))!=0,'Do not copy a live world'
assert SERVER.resolve().parent==WORK.resolve() and DEMO.resolve().parent==ROOT.parent.resolve()
assert (SERVER/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
assert not DEMO.exists(),'Refuse to overwrite an existing delivery'
assert not any(p.is_symlink() for p in (SERVER/'tideframe-test').rglob('*'))
stopped();before=inventory(SERVER/'tideframe-test')
assert sha(SERVER/'plugins/tideframe.jar')==sha(ROOT/'build/libs/tideframe-0.6.0.jar')
DEMO.mkdir();shutil.copytree(SERVER/'tideframe-test',DEMO/'tideframe-test')
assert inventory(DEMO/'tideframe-test')==before==inventory(SERVER/'tideframe-test');stopped()
for name in ['.tideframe-disposable','server.properties']:
    shutil.copy2(OLD/name,DEMO/name)
props=(DEMO/'server.properties').read_text()
assert 'management-server-secret=\n' in props and 'online-mode=true' in props and 'server-ip=127.0.0.1' in props
(DEMO/'plugins/Tideframe').mkdir(parents=True)
for name in ['screens','packs']:shutil.copytree(SERVER/'plugins/Tideframe'/name,DEMO/'plugins/Tideframe'/name)
shutil.copy2(SERVER/'plugins/Tideframe/config.yml',DEMO/'plugins/Tideframe/config.yml')
shutil.copy2(SERVER/'plugins/tideframe.jar',DEMO/'plugins/tideframe.jar')
shutil.copy2(ROOT/'LICENSE',DEMO/'LICENSE')
shutil.copy2(ROOT/'tools/start-demo.ps1',DEMO/'INICIAR_PRUEBA.ps1');shutil.copy2(ROOT/'tools/start-demo.cmd',DEMO/'INICIAR_PRUEBA.cmd')
guide=(ROOT/'docs/PLAY_DEMO_ES.md').read_text(encoding='utf-8').replace('../evidence/','evidence/')
for destination in [SERVER,DEMO]:
    optional=destination/'EJEMPLOS_CALIDAD';assert not optional.exists();optional.mkdir()
    for name in ['advanced_medium.json','advanced_high.json','advanced_4k.json']:
        shutil.copy2(ROOT/'examples/advanced-demo'/name,optional/name)
    (optional/'LEEME.txt').write_text('Copy only the desired JSON into plugins/Tideframe/screens after backup and stop; restart and accept the updated pack. All installed screens share one pack.\n')
    assert not (destination/'editor').exists();shutil.copytree(ROOT/'editor',destination/'editor')
    (destination/'ABRIR_EDITOR.cmd').write_bytes(b'@echo off\r\ncd /d "%~dp0"\r\nnode editor/serve.mjs\r\npause\r\n')
    (destination/'LEEME.md').write_text(guide,encoding='utf-8')
    (destination/'evidence').mkdir(exist_ok=True)
    for name in re.findall(r'\]\(evidence/([^)]+)\)',guide):shutil.copy2(ROOT/'evidence'/name,destination/'evidence'/name)
assert inventory(SERVER/'tideframe-test')==before==inventory(DEMO/'tideframe-test');stopped()
result={'version':'0.6.0','worldFiles':len(before),'worldByteIdenticalToStoppedSource':True,'nativeClientVerified':False,
        'pluginSha256':sha(DEMO/'plugins/tideframe.jar'),'packSha256':sha(DEMO/'plugins/Tideframe/packs/tideframe.zip'),
        'excluded':['Paper','Minecraft','eula.txt','account caches','ops/whitelist','server software caches'],
        'optionalHeavyLayouts':3,'editorIncluded':True}
(ROOT/'evidence/advanced-v06-portable.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
