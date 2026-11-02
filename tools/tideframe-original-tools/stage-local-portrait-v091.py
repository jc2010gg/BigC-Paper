"""Update only the stopped, marked localhost test copy; never its previous server."""
from pathlib import Path
import datetime, hashlib, json, shutil, socket

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT.parents[1]/'work'
TARGET=WORK/'tideframe-local-offline'
JAR=ROOT/'build/libs/tideframe-0.9.1.jar'
PREVIOUS='803e41fd9900701c228dcc1b65148a3e6f06b78b36cfb899816e10a4b5f3acf3'

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def inventory(folder):
    paths=sorted(p for p in folder.rglob('*') if p.is_file())
    assert not any(p.is_symlink() for p in paths)
    return {p.relative_to(folder).as_posix():sha(p) for p in paths}

assert TARGET.resolve().parent==WORK.resolve()
assert (TARGET/'.tideframe-local-only').read_text().strip()=='TIDEFRAME_LOCAL_OFFLINE_AUTH_TEST_V1'
properties=(TARGET/'server.properties').read_text()
for value in ['server-ip=127.0.0.1','server-port=25565','online-mode=false','white-list=true','enforce-whitelist=true']:
    assert value in properties
for port in [25565,8140]:
    with socket.socket() as connection:assert connection.connect_ex(('127.0.0.1',port))!=0,'Save and stop the local test first'
assert sha(TARGET/'plugins/tideframe.jar')==PREVIOUS,'Unexpected current plugin; do not overwrite'
digest=sha(JAR)
for distro in ['paper','folia']:
    proof=json.loads((ROOT/f'evidence/portrait-v091-{distro}-server.json').read_text())
    assert proof['pluginSha256']==digest and proof['ready'] and proof['consoleStatus'] and proof['saveObserved']
    assert proof['exitCode']==0 and not proof['forcedTermination'] and 'error' not in proof

stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%SZ')
backup=WORK/('backup-local-portrait-v091-'+stamp)
assert not backup.exists()
backup.mkdir()
world=inventory(TARGET/'tideframe-test')
shutil.copytree(TARGET/'tideframe-test',backup/'tideframe-test')
assert inventory(backup/'tideframe-test')==world
for name in ['plugins/tideframe.jar','plugins/Tideframe/boss-face.png','plugins/Tideframe/packs/tideframe.zip','INICIAR_LOCAL.ps1','LEEME_LOCAL.md','LOCAL_TEST_SETUP.json','server.properties','whitelist.json','ops.json']:
    source=TARGET/name
    if source.is_file():
        dest=backup/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,dest);assert sha(source)==sha(dest)
shutil.copy2(JAR,TARGET/'plugins/tideframe.jar')
launcher=TARGET/'INICIAR_LOCAL.ps1'
text=launcher.read_text(encoding='utf-8-sig')
assert text.count(PREVIOUS.upper())==1
launcher.write_text(text.replace(PREVIOUS.upper(),digest.upper()),encoding='utf-8')
guide=TARGET/'LEEME_LOCAL.md'
guide.write_text('''# Tideframe 0.9 - prueba local

Ejecutar INICIAR_LOCAL.cmd, esperar Done y TIDEFRAME_CORE_READY, y conectar Java26.2 a localhost.
Aceptar el pack nuevo. /tideframe abre el menu con cuatro imagenes propias.
/tideframe boss muestra la barra con cara PNG propia; no bloquea movimiento ni camara. Cambiar plugins/Tideframe/boss-face.png por otro PNG requiere reinicio y aceptar pack nuevo.
/tideframe close cierra las vistas propias. X o agacharse cierra el menu.

Si el tamano no coincide: /tideframe fit 1920 1080 3 usando las dimensiones reales de la ventana y la escala GUI de Minecraft. 0 estima Auto. Calibrar de nuevo al reconectar/cambiar ventana. No se detecta la resolucion automaticamente. Los layouts del operador conservan su escala.

Fuente/preview y pruebas en docs/BOSS_FACE_V091.md. La preview no es capturaMinecraft.
Para guardar: save-all flush y luego stop en la consola. Solo loopback y whitelist, sin cambiar el servidor anterior.
''',encoding='utf-8')
docs=TARGET/'docs';docs.mkdir(exist_ok=True)
for name in ['BOSS_FACE_V091.md','ASSETS.md']:shutil.copy2(ROOT/'docs'/name,docs/name)
for name in ['boss-face-v091-preview.png','boss-face-v091-preview.png']:shutil.copy2(ROOT/'evidence'/name,docs/name)
receipt=json.loads((TARGET/'LOCAL_TEST_SETUP.json').read_text(encoding='utf-8-sig'))
receipt.update(version='0.9.1',pluginSha256=digest,javaPid=None,managedExecSessionId=None,backup=str(backup),nativeClientVerified=False)
(TARGET/'LOCAL_TEST_SETUP.json').write_text(json.dumps(receipt,indent=2)+'\n')
assert inventory(TARGET/'tideframe-test')==world,'World changed during staging'
assert sha(TARGET/'server.properties')==sha(backup/'server.properties')
result={'version':'0.9.1','pluginSha256':digest,'backup':str(backup),'worldFiles':len(world),'worldPreservedByteForByte':True,
        'address':'127.0.0.1:25565','originalServerModified':False,'nativeClientVerified':False}
(ROOT/'evidence/portrait-v091-local-stage.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
