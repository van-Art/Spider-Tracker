"""Verify delivery identity, test-report success and the APK's asset/permission boundary."""
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
apk = root / 'dist' / 'spider-tracker-phase2-debug.apk'
build = root / 'app' / 'build' / 'outputs' / 'apk' / 'debug' / 'app-debug.apk'
assert apk.read_bytes() == build.read_bytes(), 'Delivery differs from tested build output'
badging = (root / 'docs' / 'evidence' / 'apk-badging.txt').read_text(encoding='utf-8-sig')
assert "name='com.spidertracker.app'" in badging
assert "minSdkVersion:'26'" in badging and "targetSdkVersion:'36'" in badging
assert 'ACCESS_FINE_LOCATION' not in badging and 'ACCESS_COARSE_LOCATION' not in badging
assert 'ACCESS_BACKGROUND_LOCATION' not in badging
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    assert not any(name.startswith(('analysis/', 'assets/analysis/', 'assets/source/')) for name in names)
    assert not any(name.endswith(('.woff', '.woff2')) for name in names)
    assets = sorted(name for name in names if name.startswith('assets/') and not name.startswith('assets/dexopt/'))
    assert 'assets/NOTICE.txt' in names and 'assets/MapLibre-LICENSE.txt' in names
    assert 'assets/PixelifySans-OFL.txt' in names and 'res/font/tracker_pixel.ttf' in names
    style = json.loads(archive.read('assets/tracker-style.json'))
    assert style['sources']['osm']['url'] == 'https://tiles.openfreemap.org/planet'
    assert not any('google' in str(value).lower() for value in style['sources'].values())
reports = {}
for name, expected in [('unit-tests', 6), ('device-tests', 8)]:
    tree = ET.parse(root / 'docs' / 'evidence' / f'{name}.xml').getroot()
    assert int(tree.get('tests')) == expected
    assert int(tree.get('failures')) == 0 and int(tree.get('errors')) == 0
    reports[name] = dict(tree.attrib)
lint = (root / 'docs' / 'evidence' / 'lint-results.txt').read_text(encoding='utf-8-sig')
assert '0 errors, 8 warnings' in lint
signing = (root / 'docs' / 'evidence' / 'apk-signing.txt').read_text(encoding='utf-8-sig')
assert 'Verifies' in signing and 'Verified using v2 scheme (APK Signature Scheme v2): true' in signing
value = {'apk': str(apk.relative_to(root)), 'bytes': apk.stat().st_size,
         'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'testReports': reports,
         'lint': {'errors': 0, 'warnings': 8}, 'assets': assets,
         'permissions': [line for line in badging.splitlines() if line.startswith('uses-permission:')],
         'signing': 'debug certificate; verified APK Signature Scheme v2',
         'checks': 'identical build/delivery bytes; no location permissions; no reference archive/commercial web fonts; required notices and original map style packaged'}
(root / 'docs' / 'evidence' / 'delivery.json').write_text(json.dumps(value, indent=2), encoding='utf-8')
print(json.dumps(value, indent=2))
