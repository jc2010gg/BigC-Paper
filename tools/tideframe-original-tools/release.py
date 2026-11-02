"""Inspect authored artifacts and create source+demo ZIPs without external server/client JARs."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT.parent
VERSION=re.search(r'version = "([0-9.]+)"',(ROOT/'build.gradle.kts').read_text()).group(1)
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('mode',choices=['inspect','package'])
parser.add_argument('--revision',default='')
parser.add_argument('--saved-server',type=Path,default=ROOT.parents[1]/'work/tideframe-server')
args=parser.parse_args()
REVISION=args.revision
assert not REVISION or re.fullmatch('[a-z][a-z0-9-]{0,31}',REVISION),'Invalid revision name'
LABEL=VERSION+('-'+REVISION if REVISION else '')
parts=VERSION.split('.')
EVIDENCE='v'+parts[1]+('-'+parts[2] if len(parts)>2 and parts[2]!='0' else '')+('-'+REVISION if REVISION else '')
DEMO=OUT/f'Tideframe-demo-{LABEL}'
JAR=ROOT/f'build/libs/tideframe-{VERSION}.jar'
PACK=ROOT/'build/packs/tideframe.zip'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write_json(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
def files(folder):return sorted(p for p in folder.rglob('*') if p.is_file())
def inspect():
    assert (DEMO/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
    assert sha(DEMO/'plugins/tideframe.jar')==sha(JAR)
    assert not (DEMO/'paper.jar').exists() and not (DEMO/'eula.txt').exists()
    assert not any(p.suffix=='.jar' and p.relative_to(DEMO).as_posix()!='plugins/tideframe.jar' for p in files(DEMO))
    assert not any(p.is_symlink() for p in DEMO.rglob('*'))
    world=DEMO/'tideframe-test'
    saved=args.saved_server.resolve()
    assert saved.parent==(ROOT.parents[1]/'work').resolve()
    assert (saved/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
    source=saved/'tideframe-test'
    world_files=files(world)
    assert {p.relative_to(world).as_posix():sha(p) for p in world_files}=={p.relative_to(source).as_posix():sha(p) for p in files(source)}
    level=gzip.decompress((world/'level.dat').read_bytes());assert level[0]==10 and b'DataVersion' in level
    # Actual 26.2 save layout: generation settings live in dimension data, not level.dat.
    worldgen=gzip.decompress((world/'dimensions/minecraft/overworld/data/minecraft/world_gen_settings.dat').read_bytes())
    assert worldgen[0]==10 and b'seed' in worldgen
    props=(DEMO/'server.properties').read_text()
    assert 'online-mode=true' in props and 'server-ip=127.0.0.1' in props
    assert 'management-server-secret=\n' in props and 'enable-rcon=false' in props
    with zipfile.ZipFile(JAR) as z:
        assert z.testzip() is None and 'LICENSE' in z.namelist()
        classes=[name for name in z.namelist() if name.endswith('.class')]
        assert classes and all(name.startswith('dev/tideframe/') for name in classes)
        assert not any(name.endswith('.jar') for name in z.namelist())
        majors=sorted({int.from_bytes(z.read(name)[6:8],'big') for name in classes});assert majors==[65]
        jar_info={'sha256':sha(JAR),'bytes':JAR.stat().st_size,'classCount':len(classes),'bytecodeMajors':majors,'onlyAuthoredRuntimeNamespace':True,'embeddedJars':0,'zipCrc':'passed'}
    with zipfile.ZipFile(PACK) as z:
        assert z.testzip() is None
        assert all(name in ['pack.mcmeta','LICENSE.txt','ASSET_PROVENANCE.txt'] or name.startswith('assets/tideframe/') for name in z.namelist())
        font=json.loads(z.read('assets/tideframe/font/ui.json'))
        pack_info={'sha256':sha(PACK),'bytes':PACK.stat().st_size,'entries':z.namelist(),'fontProviders':len(font['providers']),'metadata':json.loads(z.read('pack.mcmeta')),'zipCrc':'passed'}
    runtime_pack=DEMO/'plugins/Tideframe/packs/tideframe.zip'
    with zipfile.ZipFile(runtime_pack) as z:
        assert z.testzip() is None
        pictures=[name for name in z.namelist() if '/textures/font/images/' in name]
        assert not pictures,'Native HUD must bake photos into atlas-safe tiles'
        hud_font=json.loads(z.read('assets/tideframe/font/hud.json'))
        providers=[p for p in hud_font['providers'] if p['type']=='bitmap']
        for p in providers:
            png=z.read('assets/'+p['file'].replace(':','/textures/',1))
            assert 0<int.from_bytes(png[16:20],'big')<=256 and 0<int.from_bytes(png[20:24],'big')<=256
            assert p['ascent']<=p['height']
        demo_pack={'sha256':sha(runtime_pack),'bytes':runtime_pack.stat().st_size,'standalonePhotoGlyphs':len(pictures),'hudBitmapProviders':len(providers),'allHudTilesFitVanilla256Atlas':True,'entries':z.namelist(),'zipCrc':'passed'}
    write_json(ROOT/f'evidence/{EVIDENCE}-artifacts.json',{'version':VERSION,'revision':REVISION,'plugin':jar_info,'basePack':pack_info,'demoPack':demo_pack,'demoWorld':{'files':len(world_files),'bytes':sum(p.stat().st_size for p in world_files),'levelDatSha256':sha(world/'level.dat'),'gzipNbtHeader':'passed','matchesSavedServerWorld':True,'nativeClientVisualAcceptance':False}})
    own_sources=[p for folder in ['src','editor','tools','examples'] for p in files(ROOT/folder) if '__pycache__' not in p.parts]
    own_sources += [ROOT/p for p in ['build.gradle.kts','settings.gradle.kts','gradle.lockfile','LICENSE','docs/ASSETS.md']]
    write_json(ROOT/f'evidence/source-{EVIDENCE}-sha256.json',{p.relative_to(ROOT).as_posix():sha(p) for p in sorted(own_sources)})
    write_json(DEMO/'WORLD_INVENTORY.json',{p.relative_to(world).as_posix():sha(p) for p in world_files})
    print(json.dumps({'inspection':'passed','jar':jar_info,'worldFiles':len(world_files),'worldBytes':sum(p.stat().st_size for p in world_files)},indent=2))
def package():
    dirty=subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True)
    assert not dirty,'Commit the reviewed source/evidence before packaging'
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    tracked=subprocess.check_output(['git','ls-files','-z'],cwd=ROOT).decode().split('\0')
    inputs=[(ROOT/name,name) for name in tracked if name and (ROOT/name).is_file()]
    inputs += [(JAR,'build/libs/'+JAR.name),(PACK,'build/packs/tideframe.zip')]
    # World data and own plugin only; no official Minecraft/Paper binary, cache, token or account data.
    demo_inputs=[(p,'demo/'+p.relative_to(DEMO).as_posix()) for p in files(DEMO)]
    outputs=[]
    for name,selected in [(f'Tideframe-original-{LABEL}.zip',inputs+demo_inputs),(f'Tideframe-demo-{LABEL}.zip',demo_inputs)]:
        target=OUT/name;assert not target.exists(),'Refuse to overwrite an existing delivery'
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for path,arcname in selected:
                assert not path.is_symlink() and not arcname.startswith('/') and '..' not in Path(arcname).parts
                z.write(path,f'Tideframe-{LABEL}/'+arcname)
            z.writestr(f'Tideframe-{LABEL}/RELEASE.json',json.dumps({'version':VERSION,'revision':REVISION,'sourceCommit':commit,'pluginSha256':sha(JAR),'basePackSha256':sha(PACK),'demoPackSha256':sha(DEMO/'plugins/Tideframe/packs/tideframe.zip'),'minecraftVisualTest':'NOT_VERIFIED','capsLockHardwareTest':'NOT_VERIFIED','claudeAuditPerformed':False},indent=2)+'\n')
        with zipfile.ZipFile(target) as z:assert z.testzip() is None
        digest=sha(target);target.with_suffix('.zip.sha256').write_text(digest+'  '+target.name+'\n',encoding='ascii')
        outputs.append({'file':name,'bytes':target.stat().st_size,'sha256':digest,'zipCrc':'passed'})
    write_json(OUT/f'Tideframe-release-{LABEL}.json',{'sourceCommit':commit,'deliveries':outputs})
    print(json.dumps(outputs,indent=2))
if args.mode == 'inspect':inspect()
else:package()
