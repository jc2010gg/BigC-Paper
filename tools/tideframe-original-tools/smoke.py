"""Run only an explicitly marked loopback test server; never accepts a production directory."""
from pathlib import Path
import argparse, hashlib, json, socket, subprocess, threading, time

p=argparse.ArgumentParser()
p.add_argument('--server',type=Path,required=True)
p.add_argument('--jar',type=Path,required=True)
p.add_argument('--java',required=True)
p.add_argument('--stage',required=True)
a=p.parse_args()
server=a.server.resolve()
assert (server/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
props=(server/'server.properties').read_text()
assert 'server-ip=127.0.0.1' in props and 'server-port=25586' in props and 'online-mode=true' in props
expected='5ab560a769c1ab413cb7f637dd0dc697974571f2db0a667cfbac511422e51b26'
assert hashlib.sha256((server/'paper.jar').read_bytes()).hexdigest()==expected
with socket.socket() as s: assert s.connect_ex(('127.0.0.1',25586))!=0, 'Test port occupied'
(server/'plugins').mkdir(exist_ok=True)
(server/'plugins/tideframe.jar').write_bytes(a.jar.read_bytes())
out=Path(__file__).resolve().parents[1]/'evidence'
lines=[]
proc=subprocess.Popen([a.java,'-Xms512M','-Xmx1G','-jar','paper.jar','nogui'],cwd=server,
    stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',bufsize=1)
def collect():
    for line in proc.stdout: lines.append(line)
t=threading.Thread(target=collect,daemon=True);t.start()
start=time.monotonic();ready=False
try:
    while time.monotonic()-start<100:
        ready=any('Done (' in line for line in lines) and any('TIDEFRAME_' in line and 'READY' in line for line in lines)
        if ready or proc.poll() is not None: break
        time.sleep(.2)
    if ready:
        proc.stdin.write('tideframe\n');proc.stdin.flush();time.sleep(1)
        with socket.create_connection(('127.0.0.1',25586),timeout=2): pass
finally:
    if proc.poll() is None:
        proc.stdin.write('stop\n');proc.stdin.flush()
        try: proc.wait(timeout=40)
        except subprocess.TimeoutExpired: proc.terminate();proc.wait(timeout=10)
    t.join(timeout=3)
    (out/f'{a.stage}-server.log').write_text(''.join(lines),encoding='utf-8')
with socket.socket() as s: released=s.connect_ex(('127.0.0.1',25586))!=0
result={'stage':a.stage,'ready':ready,'exitCode':proc.returncode,'portReleased':released,
        'paper':'26.2 build132','paperSha256':expected,'pluginSha256':hashlib.sha256(a.jar.read_bytes()).hexdigest(),
        'nativeClientConnected':False,'seconds':round(time.monotonic()-start,2)}
(out/f'{a.stage}-server.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
assert ready and proc.returncode==0 and released
