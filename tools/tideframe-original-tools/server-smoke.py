"""Boot/status/HTTP/save integration: only marked, pinned, loopback MC26.2 servers."""
from pathlib import Path
import argparse, hashlib, json, os, re, socket, struct, subprocess, threading, time, urllib.request
p=argparse.ArgumentParser()
for name in ['server','jar','java','stage']:p.add_argument('--'+name,required=True)
p.add_argument('--distribution',choices=['paper','folia'],default='paper')
p.add_argument('--screens',type=int,default=4)
p.add_argument('--menu-elements',type=int,default=5)
p.add_argument('--minecraft-port',type=int)
p.add_argument('--pack-port',type=int)
a=p.parse_args();assert re.fullmatch('[a-z0-9_-]{1,32}',a.stage)
server=Path(a.server).resolve();plugin=Path(a.jar).resolve();folia=a.distribution=='folia'
assert (server/'.tideframe-disposable').read_text().strip()=='TIDEFRAME_TEST_ONLY'
port,http_port=(25587,8137) if folia else (25586,8136);jar='folia.jar' if folia else 'paper.jar'
port=a.minecraft_port or port;http_port=a.pack_port or http_port
assert (port,http_port) in [(25586,8136),(25587,8137),(25588,8138),(25589,8139)],'Only reserved loopback test ports are allowed'
expected=('128a634192261cd38bb4a5dc54075018a0f896fd6c6f529e37dca6e99e32b3b3' if folia else
          '5ab560a769c1ab413cb7f637dd0dc697974571f2db0a667cfbac511422e51b26')
props=(server/'server.properties').read_text()
for value in ['server-ip=127.0.0.1',f'server-port={port}','online-mode=true','enable-rcon=false','enable-query=false']:assert value in props,value
config=(server/'plugins/Tideframe/config.yml').read_text()
assert 'bind: 127.0.0.1' in config and f'port: {http_port}' in config and f'public-url: http://127.0.0.1:{http_port}/packs/' in config
assert hashlib.sha256((server/jar).read_bytes()).hexdigest()==expected
for checked in [port,http_port]:
    with socket.socket() as sock:assert sock.connect_ex(('127.0.0.1',checked))!=0,'Test port occupied'
(server/'plugins/tideframe.jar').write_bytes(plugin.read_bytes())
out=Path(__file__).resolve().parents[1]/'evidence';out.mkdir(exist_ok=True);lines=[]
trust=['-Djavax.net.ssl.trustStoreType=Windows-ROOT','-Djavax.net.ssl.trustStore=NONE'] if os.name=='nt' else []
proc=subprocess.Popen([a.java,*trust,'-Xms512M','-Xmx1G','-jar',jar,'nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',bufsize=1)
def collect():
    for line in proc.stdout:lines.append(line)
reader=threading.Thread(target=collect,daemon=True);reader.start();started=time.monotonic()
result={'stage':a.stage,'distribution':a.distribution,'minecraft':'26.2','build':7 if folia else 132,'channel':'BETA' if folia else 'STABLE','java':'25.0.3','serverSha256':expected,'pluginSha256':hashlib.sha256(plugin.read_bytes()).hexdigest(),'ready':False,'consoleStatus':False,'nativeClientConnected':False,'visualUiVerified':False,'forcedTermination':False}
def vi(n):
    result=bytearray()
    while True:
        b=n&127;n>>=7;result.append(b|(128 if n else 0))
        if not n:return bytes(result)
def readint(sock):
    n=0
    for i in range(5):
        b=sock.recv(1)
        if not b:raise EOFError('status socket closed')
        n|=(b[0]&127)<<(7*i)
        if not b[0]&128:return n
    raise ValueError('invalid VarInt')
def status():
    host=b'127.0.0.1';payload=b'\x00'+vi(776)+vi(len(host))+host+struct.pack('>H',port)+b'\x01'
    with socket.create_connection(('127.0.0.1',port),timeout=4) as sock:
        sock.sendall(vi(len(payload))+payload+b'\x01\x00');assert 0<readint(sock)<65536 and readint(sock)==0
        size=readint(sock);assert 0<size<65536;data=bytearray()
        while len(data)<size:
            chunk=sock.recv(size-len(data))
            if not chunk:raise EOFError('truncated status JSON')
            data.extend(chunk)
        return json.loads(data)
try:
    while time.monotonic()-started<180 and proc.poll() is None:
        result['ready']=any('Done (' in line for line in lines) and any('TIDEFRAME_CORE_READY' in line and f'folia={str(folia).lower()}' in line for line in lines)
        if result['ready']:break
        time.sleep(.2)
    assert result['ready'],'Server did not become ready'
    result['cameraBindingsVerified']=any('TIDEFRAME_CAMERA_BINDINGS_READY protocol=776 playerProbe=false' in line for line in lines)
    assert result['cameraBindingsVerified'],'Native 26.2 camera constructor self-test did not pass'
    result['readyAfterSeconds']=round(time.monotonic()-started,3);result['status']=status();assert result['status']['version']['protocol']==776
    command=f'Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object {{ $_.OwningProcess -eq {proc.pid} }} | Select-Object LocalAddress,LocalPort,OwningProcess | ConvertTo-Json -Compress'
    snapshot=subprocess.run(['powershell.exe','-NoProfile','-Command',command],capture_output=True,text=True,timeout=30,check=True).stdout.strip()
    listeners=json.loads(snapshot);listeners=listeners if isinstance(listeners,list) else [listeners];result['listeners']=listeners
    assert {e['LocalPort'] for e in listeners}=={port,http_port} and all(e['LocalAddress'] in ['127.0.0.1','::1'] for e in listeners)
    proc.stdin.write('tideframe\n');proc.stdin.flush();deadline=time.monotonic()+10
    while time.monotonic()<deadline:
        result['consoleStatus']=any('TIDEFRAME_STATUS' in line and f'screens={a.screens} ' in line and f'menuElements={a.menu_elements} ' in line and 'pack=tideframe.zip' in line for line in lines)
        if result['consoleStatus']:break
        time.sleep(.1)
    assert result['consoleStatus'],'Actual console screen/pack state not observed'
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(f'http://127.0.0.1:{http_port}/packs/tideframe.zip',timeout=5) as response:
        data=response.read(64*1024*1024+1);assert 0<len(data)<=64*1024*1024
        result['packHttp']={'status':response.status,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
        assert data==(server/'plugins/Tideframe/packs/tideframe.zip').read_bytes()
    # Folia removes save-all; its region-aware graceful shutdown saves all worlds.
    result['saveMethod']='region-aware graceful stop' if folia else 'save-all flush then stop'
    if not folia:
        proc.stdin.write('save-all flush\n');proc.stdin.flush();deadline=time.monotonic()+15
        while time.monotonic()<deadline and not any('Saved the game' in line for line in lines):time.sleep(.1)
        assert any('Saved the game' in line for line in lines),'Explicit world save not observed'
except Exception as exc:result['error']=f'{type(exc).__name__}: {exc}'
finally:
    if proc.poll() is None:
        try:proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=45)
        except (subprocess.TimeoutExpired,BrokenPipeError):result['forcedTermination']=True;proc.terminate();proc.wait(timeout=10)
    reader.join(timeout=3);result['exitCode']=proc.returncode;result['seconds']=round(time.monotonic()-started,3);result['portsReleased']={}
    for checked in [port,http_port]:
        with socket.socket() as sock:result['portsReleased'][str(checked)]=sock.connect_ex(('127.0.0.1',checked))!=0
    result['stopObserved']=any('Stopping server' in line for line in lines)
    result['saveObserved']=any(('Saved all worlds' if folia else 'Saved the game') in line for line in lines)
    (out/f'{a.stage}-server.log').write_text(''.join(lines),encoding='utf-8');(out/f'{a.stage}-server.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
assert 'error' not in result and result['ready'] and result['consoleStatus'] and result['saveObserved']
assert result['exitCode']==0 and all(result['portsReleased'].values()) and not result['forcedTermination']
