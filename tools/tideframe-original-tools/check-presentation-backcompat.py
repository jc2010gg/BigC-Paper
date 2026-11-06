"""Compare own historical font/PNG entries with the new build (no external JARs)."""
from pathlib import Path
import argparse, json, os, subprocess

BASELINE = '8123908b803b364f592210e64686d6557fa06c16'
FILES = ['core/Screen.java', 'edit/ScreenCodec.java', 'pack/HudRaster.java', 'pack/HudAssets.java']

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--current-classes', default='build/classes/java/main')
    parser.add_argument('--baseline', default=BASELINE)
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-25.0.3'))
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    current = (repo / args.current_classes).resolve()
    if not current.is_dir():
        raise SystemExit('Compile the project first; current classes were not found: ' + str(current))
    target = repo / '.build-tools/presentation-v08-backcompat'
    target.mkdir(parents=True, exist_ok=True)
    sources = []
    for file in FILES:
        path = target / 'source/dev/linqfy/bigCasares/modules/tideframe/engine' / file
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(subprocess.check_output(['git', 'show', args.baseline + ':src/main/java/dev/linqfy/bigCasares/modules/tideframe/engine/' + file], cwd=repo))
        sources.append(str(path))
    suffix = '.exe' if os.name == 'nt' else ''
    java = Path(args.java_home) / 'bin'
    classes = target / 'classes'
    classes.mkdir(exist_ok=True)
    helper = repo / 'tools/PresentationFingerprint.java'
    subprocess.run([str(java / ('javac' + suffix)), '-cp', str(current), '-d', str(classes), *sources, str(helper)], check=True)
    old = subprocess.check_output([str(java / ('java' + suffix)), '-cp', str(classes) + os.pathsep + str(current), 'PresentationFingerprint'], text=True)
    new = subprocess.check_output([str(java / ('java' + suffix)), '-cp', str(current), str(helper)], text=True)
    if old != new:
        raise SystemExit('Historical default render entries differ.\nBaseline:\n' + old + '\nCurrent:\n' + new)
    samples = []
    for line in new.strip().splitlines():
        mode, count, digest = line.split('\t')
        samples.append({'resolution': mode, 'entries': int(count), 'sha256': digest})
    record = {'baselineCommit': args.baseline, 'samples': samples, 'allEntriesByteIdentical': True,
              'scope': 'Default scale1 legacy and low own menu font/PNG registry; this is not authenticated Minecraft visual proof.'}
    (repo / 'evidence/presentation-v08-backcompat.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record, indent=2))

if __name__ == '__main__':
    main()
