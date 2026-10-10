"""Generate native iOS mode selectors with the exact existing Mac action catalog."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
catalog=json.loads((root/'app/src/main/assets/catalog.json').read_text())
output=['import Foundation\n']
for m in catalog:
 if m['id'] not in ['Web','News','Video','Presentation']:continue
 key=m['id'];q=lambda v:json.dumps(v,ensure_ascii=False)
 output += [f'@objcMembers\nclass TTMode{key}: TTMode {{', f'    override class func title() -> String {{ return {q(m["title"])} }}',f'    override class func subtitle() -> String {{ return {q({"Web":"Browse the web","News":"Read your NewsBlur stories","Video":"Control video playback","Presentation":"Control your presentation"}[key])} }}',f'    override class func imageName() -> String {{ return {q(m["icon"]+".png")} }}', '    override class func actions() -> [String] { return ['+', '.join(q(a['id']) for a in m['actions'])+'] }', '    override func shouldUseModeOptionsFor(_ action: String) -> Bool { return true }']
 for direction,aid in zip(['North','East','West','South'],m['defaults']):output.append(f'    override func default{direction}() -> String {{ return {q(aid)} }}')
 for a in m['actions']:
  output += [f'    func title{a["id"]}() -> String {{ return {q(a["title"])} }}',f'    func image{a["id"]}() -> String {{ return {q(a["icon"]+".png")} }}',f'    func run{a["id"]}() {{ TTMobileBrowser.run(mode: {q(key)}, action: {q(a["id"].removeprefix("TTMode"+key))}) }}']
 output.append('}\n')
(root.parent/'ios/Turn Touch iOS/Modes/Portable/TTPortableModes.swift').write_text('\n'.join(output))
