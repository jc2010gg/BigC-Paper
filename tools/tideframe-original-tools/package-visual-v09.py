"""Package authored source/assets/evidence and our JAR. No server, account or world files."""
from pathlib import Path
import hashlib,json,re,shutil,zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT.parent
VERSION=re.search(r'version = "([0-9.]+)"',(ROOT/'build.gradle.kts').read_text()).group(1)
PREFIX='visual-v09' if VERSION=='0.9.0' else 'portrait-v091'
jar=ROOT/f'build/libs/tideframe-{VERSION}.jar'
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
unit=json.loads((ROOT/f'evidence/{PREFIX}-unit-summary.json').read_text())
assert unit['jarSha256']==digest and unit['tests']>0 and unit['failures']==unit['errors']==0
for name in ['paper','folia']:
    proof=json.loads((ROOT/f'evidence/{PREFIX}-{name}-server.json').read_text())
    assert proof['pluginSha256']==digest and proof['exitCode']==0 and 'error' not in proof
sources=[]
for folder in ['src','editor','tools','docs','examples']:
    sources.extend(p for p in (ROOT/folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
sources.extend(ROOT/name for name in ['LICENSE','README.md','AUDIT_FOR_CLAUDE.md','build.gradle.kts','settings.gradle.kts','gradle.lockfile'])
for pattern in ['visual-v09*','boss-v09*','cursor-v09*','portrait-v091*','boss-face-v091*']:
    for entry in (ROOT/'evidence').glob(pattern):sources.extend([entry] if entry.is_file() else [p for p in entry.rglob('*') if p.is_file()])
sources=sorted(p for p in set(sources) if p.name!=f'{PREFIX}-delivery.json')
assert all(not p.is_symlink() and p.suffix not in ['.jar','.class'] for p in sources)
target=OUT/f'Tideframe-{VERSION}-source.zip'
inventory={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
metadata={'version':VERSION,'jarSha256':digest,'sourceFiles':inventory,'nativeClientVerified':False,'externalClaudeAudit':False,
          'note':'Includes current evidence only; historical evidence remains in the working repository.'}
with zipfile.ZipFile(target,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for p in sources:
        info=zipfile.ZipInfo(p.relative_to(ROOT).as_posix(),(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
        z.writestr(info,p.read_bytes())
    z.writestr('DELIVERY_METADATA.json',json.dumps(metadata,indent=2)+'\n')
with zipfile.ZipFile(target) as z:
    assert z.testzip() is None
    assert all(hashlib.sha256(z.read(name)).hexdigest()==value for name,value in inventory.items())
artifact=OUT/f'Tideframe-{VERSION}.jar';shutil.copy2(jar,artifact)
result={'version':VERSION,'jar':str(artifact),'jarSha256':digest,'sourceZip':str(target),
        'sourceZipSha256':hashlib.sha256(target.read_bytes()).hexdigest(),'sourceFiles':len(inventory),'crcAndSourceBytesVerified':True,
        'externalServerClientBinariesIncluded':False,'nativeClientVerified':False,'externalClaudeAudit':False}
(ROOT/f'evidence/{PREFIX}-delivery.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
