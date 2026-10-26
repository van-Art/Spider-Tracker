"""Validate the Phase 3 delivery without changing historical Phase 2 evidence."""
from pathlib import Path
from PIL import Image
import hashlib
import io
import json
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
evidence = root / 'docs/phase-3-evidence'
apk = root / 'dist/spider-tracker-phase3-debug.apk'
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert apk.read_bytes() == (root/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()
assert sha(root/'dist/spider-tracker-phase2-debug.apk') == '26e0f79fcc5963c95cb737b641cea7d58500e69aeb9b7579660ab3a45c611aa1'
assert 'BUILD SUCCESSFUL' in (evidence/'final-build-tests.log').read_text()
assert '0 errors, 11 warnings' in (evidence/'lint-results.txt').read_text()
counts = {}
for name, files in [('unit', list(evidence.glob('TEST-*.xml'))), ('android', [evidence/'android-test-results.xml'])]:
    total = 0
    for file in files:
        report = ET.parse(file).getroot()
        assert int(report.get('failures', 0)) == 0 and int(report.get('errors', 0)) == 0
        total += int(report.get('tests', 0))
    assert total == 12, (name, total)
    counts[name] = total

assets = json.loads((evidence/'asset-inventory.json').read_text())
assert assets['sprite']['frameCount'] == 76
with zipfile.ZipFile(apk) as package:
    for asset in assets['assets']:
        source = root/'app/src/main/res/drawable-nodpi'/asset['file']
        assert sha(source) == asset['sha256']
        entry = next(n for n in package.namelist() if n.startswith('res/drawable') and n.endswith('/'+asset['file']))
        original = Image.open(source).convert('RGBA')
        packaged = Image.open(io.BytesIO(package.read(entry))).convert('RGBA')
        assert original.size == packaged.size and original.tobytes() == packaged.tobytes(), asset['file']

badging = (evidence/'apk-badging.txt').read_text()
assert "versionName='0.3.0'" in badging
for permission in ['ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION']:
    assert permission in badging
assert 'ACCESS_BACKGROUND_LOCATION' not in badging
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in (evidence/'apk-signature.txt').read_text()
for name in ['ui-changes', 'asset-integration', 'search-implementation', 'visual-validation', 'test-results']:
    assert (root/f'docs/phase-3-{name}.md').is_file()
for name, size in [('reference', (780,1386)), ('phone', (780,1688)), ('compact', (640,1280)), ('tablet', (1536,2048)), ('landscape', (1688,780))]:
    assert Image.open(root/f'docs/phase-3-screenshots/{name}-map.png').size == size
receipt = json.loads((evidence/'live-search-receipt.json').read_text())
assert receipt['productionProvider'] and not receipt['hardcodedResults'] and len(receipt['labels']) == 5

sources = [p for p in (root/'app/src/main').rglob('*') if p.is_file() and p.suffix in ['.kt','.xml','.png','.json']]
report = dict(apk=str(apk), apkBytes=apk.stat().st_size, apkSha256=sha(apk),
              tests=counts, lintErrors=0, lintWarnings=11, assetsVerified=6, spriteFrames=76,
              phase2ApkPreserved=True, sourceSha256={str(p.relative_to(root)):sha(p) for p in sources},
              limitations=['No physical-device GPS certification','No routing/backend','Public Photon has no SLA','Visual differences documented'])
(evidence/'delivery.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k != 'sourceSha256'}, indent=2))
