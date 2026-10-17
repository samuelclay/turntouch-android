#!/usr/bin/env python3
"""Import source artwork and declarative action metadata; never executes Apple code."""
from pathlib import Path
import re, json, shutil
root = Path(__file__).resolve().parents[1]
ios = root.parent / 'ios' / 'Turn Touch iOS'
mac = root.parent / 'mac' / 'Turn Touch Mac'
def resource(name): return re.sub(r'[^a-z0-9_]', '_', name.lower().replace('.png',''))
for source in [mac / 'Images', ios / 'Images']:
    for f in source.rglob('*.png'):
        shutil.copyfile(f, root / 'app/src/main/res/drawable' / (resource(f.name)+'.png'))
for source, name in [('Effra_Rg.ttf','effra'),('Dalton Maag - Effra Light.ttf','effra_light'),('Dalton Maag - Effra Std Bold.ttf','effra_bold')]:
    shutil.copyfile(ios / 'Fonts' / source,root / 'app/src/main/res/font' / (name+'.ttf'))
shutil.copyfile(ios/'Assets.xcassets/AppIcon.appiconset/Icon-App-iTunes.png',root/'app/src/main/res/drawable/app_icon.png')
shutil.copyfile(root.parent/'ios/LICENSE', root/'LICENSE')
def swiftbody(text, name):
    m=re.search(r'func '+re.escape(name)+r'\([^)]*\)[^{]*\{(.*?)(?=\n    (?:override |class |@objc |private |public )*func |\n    // MARK:|\n})',text,re.S)
    return m.group(1) if m else ''
def literal(body, fallback=''):
    m=re.search(r'return (?:TTText\()?@?"([^"\\]*)"',body)
    return m.group(1) if m else fallback
catalog=[]
for key in ['Phone','Camera','Music','Hue','Nanoleaf','Wemo','Kasa','Govee','Sonos','Bose','Yoga','Custom','Ifttt','HomeAssistant','HomeKit','Spotify','AlarmClock','Airfoil','Video','News','Web','Presentation','Nest']:
    paths=list((ios/'Modes').glob('*/TTMode'+key+'.swift'))
    if paths and key!='AlarmClock':
        text=paths[0].read_text(); body=lambda n:swiftbody(text,n)
        actbody=body('actions'); ids=re.findall(r'"(TTMode\w+)"',actbody.split(']')[0]); origin='iOS'
    else:
        paths=list((mac/'Modes').glob('*/TTMode'+key+'.m'))
        if not paths: continue
        text=paths[0].read_text()
        def body(n):
            m=re.search(r'[+-] \([^)]*\)'+re.escape(n)+r'\s*\{(.*?)\n}',text,re.S)
            return m.group(1) if m else ''
        ids=re.findall(r'@"(TTMode\w+)"',body('actions'));origin='Mac'
    actions=[]
    for aid in ids:
        actions.append(dict(id=aid,title=literal(body('title'+aid),aid.replace('TTMode'+key,'')),icon=resource(literal(body('image'+aid),'mode_'+key.lower()))))
    for action in actions:
        if action['id']=='TTModeNanoleafSceneCustom': action['title']='Trigger effect'
        if action['id']=='TTModeHueSceneCustom': action['title']='Custom scene'
    catalog.append(dict(id=key,title=literal(body('title'),key),subtitle=literal(body('subtitle')),icon=resource(literal(body('imageName'),'mode_'+key.lower())),origin=origin,actions=actions,defaults=[literal(body('default'+d),ids[0] if ids else '') for d in ['North','East','West','South']]))
(root/'app/src/main/assets/catalog.json').write_text(json.dumps(catalog,indent=2))
print('\n'.join(f"{m['id']}: {len(m['actions'])} actions ({m['origin']})" for m in catalog))
