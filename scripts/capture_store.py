#!/usr/bin/env python3
"""Capture genuine UI on the attached Samsungs, restoring display/demo settings."""
import argparse
import json
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
PHONE = 'R5CT601C8DA'
TABLET = 'R8YW810E3FF'


def adb(serial, *args, **kwargs):
    return subprocess.run(['adb', '-s', serial, *args], check=True, **kwargs)


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--languages', nargs='+', default=['en'], help='BCP 47 codes, or all')
args = parser.parse_args()
supported = json.loads((ROOT / 'localization/languages.json').read_text())
store_locales = json.loads((ROOT / 'localization/store-locales.json').read_text())
languages = list(supported) if args.languages == ['all'] else args.languages
if not set(languages) <= set(supported):
    parser.error('Unsupported language')
for language in languages:
    for serial, category, other in [(PHONE, 'phoneScreenshots', TABLET), (TABLET, 'tenInchScreenshots', PHONE)]:
        adb(other, 'shell', 'am', 'force-stop', 'com.turntouch.android')
        running = subprocess.run(['adb', '-s', serial, 'shell', 'pidof', 'com.turntouch.android'], capture_output=True, text=True)
        print(f'{serial}: previous app process {running.stdout.strip() or "not running"}', flush=True)
        adb(serial, 'install', '-r', str(ROOT / 'app/build/outputs/apk/debug/app-debug.apk'))
        adb(serial, 'install', '-r', str(ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
        demo = adb(serial, 'shell', 'settings', 'get', 'global', 'sysui_demo_allowed', capture_output=True, text=True).stdout.strip()
        original_size = adb(serial, 'shell', 'wm', 'size', capture_output=True, text=True).stdout
        override = next((line.split(': ', 1)[1] for line in original_size.splitlines() if line.startswith('Override size:')), 'reset')
        try:
            # Render at a Play-supported aspect ratio on the real phone, rather than crop its UI.
            if serial == PHONE:
                adb(serial, 'shell', 'wm', 'size', '1080x1920')
            adb(serial, 'shell', 'settings', 'put', 'global', 'sysui_demo_allowed', '1')
            for args in [('enter',), ('notifications', '--es', 'visible', 'false')]:
                adb(serial, 'shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '--es', 'command', *args)
            result = adb(serial, 'shell', 'am', 'instrument', '-w', '-e', 'class', 'com.turntouch.android.StoreScreenshotTest', '-e', 'storeScreenshots', 'true', '-e', 'language', language, 'com.turntouch.android.test/androidx.test.runner.AndroidJUnitRunner', capture_output=True, text=True)
            print(result.stdout)
            if 'OK (1 test)' not in result.stdout:
                raise RuntimeError('Screenshot instrumentation failed')
            destination = ROOT / 'store' / store_locales[language] / 'images' / category
            destination.mkdir(parents=True, exist_ok=True)
            for name in ['01-actions', '02-button-actions', '03-action-picker', '04-settings', '05-tutorial', '06-buttons', '07-apps']:
                adb(serial, 'pull', f'/sdcard/Android/data/com.turntouch.android/files/store-1.0/{language}/{name}.png', str(destination / f'{name}.png'))
        finally:
            adb(serial, 'shell', 'am', 'broadcast', '-a', 'com.android.systemui.demo', '--es', 'command', 'exit')
            if demo == 'null':
                adb(serial, 'shell', 'settings', 'delete', 'global', 'sysui_demo_allowed')
            else:
                adb(serial, 'shell', 'settings', 'put', 'global', 'sysui_demo_allowed', demo)
            if serial == PHONE:
                adb(serial, 'shell', 'wm', 'size', override)
