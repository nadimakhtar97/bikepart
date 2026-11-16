"""Disposable API37 shrunk APK smoke; preserves shop records and restores debug APK/settings."""
from pathlib import Path
import hashlib,json,re,subprocess,time,traceback

ROOT=Path(__file__).resolve().parents[4]
BASE=Path(__file__).resolve().parent
ADB='/home/nadimakhtar97/Android/Sdk/platform-tools/adb'
ANDROID='/home/nadimakhtar97/.local/bin/android'
SERIAL='emulator-5556'
PACKAGE='com.nadim.bikepart'

def command(args):
    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True)
    return result.stdout.decode(errors='replace').strip()
def adb(*args): return command([ADB,'-s',SERIAL,*args])
def nodes(value):
    if isinstance(value,dict):
        yield value
        for child in value.values(): yield from nodes(child)
    elif isinstance(value,list):
        for child in value: yield from nodes(child)
def layout(name):
    target=BASE/'logs'/f'release-{name}.json'
    command([ANDROID,'layout','--device',SERIAL,'--flat','--output',str(target)])
    return list(nodes(json.loads(target.read_text())))
def find(tree,label):
    return next((n for n in tree if (n.get('text')==label or n.get('content-desc')==label)
                 and n.get('center') and not n.get('off-screen')),None)
def await_label(label,name):
    for attempt in range(12):
        tree=layout(name)
        if find(tree,label): return tree
        time.sleep(.2)
    raise AssertionError(f'Missing visible {label}')
def tap(label,name):
    tree=await_label(label,name); x,y=map(int,re.findall(r'\d+',find(tree,label)['center']))
    adb('shell','input','tap',str(x),str(y))
def is_checked(tree,label):
    node=find(tree,label); assert node, label
    x,y=map(int,re.findall(r'\d+',node['center']))
    matches=[]
    for n in tree:
        if 'CHECKABLE' not in n.get('interactions',[]) or not n.get('bounds'): continue
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n['bounds']))
        if x1<=x<=x2 and y1<=y<=y2: matches.append(((x2-x1)*(y2-y1),n))
    assert matches,f'No labelled checked row: {label}'
    return 'CHECKED' in min(matches,key=lambda x:x[0])[1].get('state',[])
def capture(name):
    data=subprocess.check_output([ADB,'-s',SERIAL,'exec-out','screencap','-p'])
    assert data.startswith(b'\x89PNG\r\n\x1a\n')
    (BASE/'screenshots'/f'{name}.png').write_bytes(data)
def home_more(name):
    adb('shell','am','start','-n',f'{PACKAGE}/.MainActivity')
    tap('More',name+'-home')
    return await_label('Accent',name+'-more')
def restore_choices():
    adb('shell','am','force-stop',PACKAGE)
    home_more('restore')
    tap('System','restore-system'); tap('Fluent Brand','restore-fluent')
    tree=layout('restore-accent')
    if not is_checked(tree,'Accent'): tap('Accent','restore-accent-on')

original_font=adb('shell','settings','get','system','font_scale')
original_night=adb('shell','settings','get','secure','ui_night_mode')
try:
    print(adb('install','-r',str(ROOT/'app/build/outputs/apk/release/app-release-smoke.apk')),flush=True)
    path=adb('shell','pm','path',PACKAGE).split('package:',1)[1].strip()
    installed=adb('shell','sha256sum',path).split()[0]
    expected=hashlib.sha256((ROOT/'app/build/outputs/apk/release/app-release-smoke.apk').read_bytes()).hexdigest()
    assert installed==expected,'Installed APK must equal the actual shrunk artifact'
    print('Installed shrunk APK SHA256:',installed,flush=True)
    flags=[line.strip() for line in adb('shell','dumpsys','package',PACKAGE).splitlines()
           if 'flags=[' in line.lower() or 'pkgFlags=[' in line or 'versionCode=' in line]
    assert flags and not any('DEBUGGABLE' in line for line in flags)
    print('\n'.join(flags),flush=True)
    tree=home_more('initial')
    assert is_checked(tree,'System') and is_checked(tree,'Fluent Brand') and is_checked(tree,'Accent')
    tap('Light','select-light');tap('M365','select-m365');tap('Accent','select-neutral')
    tree=layout('before-cold')
    assert is_checked(tree,'Light') and is_checked(tree,'M365') and not is_checked(tree,'Accent')
    capture('release-m365-light-neutral-before-cold')
    adb('shell','am','force-stop',PACKAGE)
    tree=home_more('cold-reopen')
    assert is_checked(tree,'Light') and is_checked(tree,'M365') and not is_checked(tree,'Accent')
    print('PASS cold force-stop/relaunch: M365 + Accent off + Light persisted.',flush=True)
    capture('release-m365-light-neutral-after-cold')
    tap('Dark','select-dark');tap('Accent','select-brand')
    tree=layout('dark-brand')
    assert is_checked(tree,'Dark') and is_checked(tree,'M365') and is_checked(tree,'Accent')
    capture('release-m365-dark-brand')
    tap('Stock','stock');tap('Add product','add-product');await_label('New product','new-product')
    capture('release-m365-dark-header')
    adb('shell','input','keyevent','4')
    print('PASS shrunk runtime: brightness, Accent, native switch and product navigation.',flush=True)
finally:
    try: restore_choices()
    finally:
        print(adb('install','-r',str(ROOT/'app/build/outputs/apk/debug/app-debug.apk')),flush=True)
        tree=home_more('debug-restored')
        assert is_checked(tree,'System') and is_checked(tree,'Fluent Brand') and is_checked(tree,'Accent')
        assert adb('shell','settings','get','system','font_scale')==original_font
        assert adb('shell','settings','get','secure','ui_night_mode')==original_night
        print('RESTORED debug APK, System, Fluent Brand, Accent on, original font and night settings.',flush=True)
