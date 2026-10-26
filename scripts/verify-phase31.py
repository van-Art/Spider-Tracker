"""Assert the Phase 3.1 delivery and preserve historical APK evidence."""
from pathlib import Path
from PIL import Image
import hashlib
import io
import json
import xml.etree.ElementTree as ET
import zipfile

root=Path(__file__).resolve().parents[1]
evidence=root/'docs/phase-3-1-evidence'
apk=root/'dist/spider-tracker-phase3-1-debug.apk'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
assert apk.read_bytes()==(root/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()
assert sha(root/'dist/spider-tracker-phase3-debug.apk')=='08ad408efa2acbe363231e51d3003b2daa898b8c60f570fccd4400f18fd0a638'
assert sha(root/'dist/spider-tracker-phase2-debug.apk')=='26e0f79fcc5963c95cb737b641cea7d58500e69aeb9b7579660ab3a45c611aa1'
assert 'BUILD SUCCESSFUL' in (evidence/'final-validation.log').read_text()
assert '0 errors, 12 warnings' in (evidence/'lint-results.txt').read_text()
counts={}
for label,files,expected in [('unit',list(evidence.glob('TEST-*.xml')),18),('android',[evidence/'android-test-results.xml'],25)]:
    total=0
    for path in files:
        report=ET.parse(path).getroot()
        assert int(report.get('failures',0))==0 and int(report.get('errors',0))==0 and int(report.get('skipped',0))==0
        total+=int(report.get('tests',0))
    assert total==expected,(label,total)
    counts[label]=total

new_assets=json.loads((evidence/'asset-inventory.json').read_text())
old_assets=json.loads((root/'docs/phase-3-evidence/asset-inventory.json').read_text())['assets']
expected_hashes={a.get('name',a.get('file')):a['sha256'] for a in new_assets+old_assets}
assets=[]
with zipfile.ZipFile(apk) as package:
    for name,expected in expected_hashes.items():
        source=root/'app/src/main/res/drawable-nodpi'/name
        assert sha(source)==expected,name
        entry=next(n for n in package.namelist() if n.startswith('res/drawable') and n.endswith('/'+name))
        original=Image.open(source).convert('RGBA')
        packaged=Image.open(io.BytesIO(package.read(entry))).convert('RGBA')
        assert original.size==packaged.size and original.tobytes()==packaged.tobytes(),name
        assets.append(dict(file=name,pixels=list(original.size),sha256=expected))
assert len(assets)==14
badging=(evidence/'apk-badging.txt').read_text()
assert "versionName='0.3.1'" in badging and "versionCode='3'" in badging
assert 'ACCESS_BACKGROUND_LOCATION' not in badging
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in (evidence/'apk-signature.txt').read_text()
for name in ['ui-refinement','map-layers','marker-animation','pixel-rendering','test-results']:
    assert (root/f'docs/phase-3-1-{name}.md').read_text(encoding='utf-8').strip()
real=json.loads((evidence/'real-osm-places.json').read_text())
assert len(real)==30 and all('OpenStreetMap' in x['source'] and -90<=x['lat']<=90 and -180<=x['lon']<=180 for x in real)
load=json.loads((evidence/'density-test.json').read_text())
assert load['inputReports']==1000 and load['visibleClusters']==1
sources=[p for p in (root/'app/src').rglob('*') if p.is_file() and p.suffix in ['.kt','.xml','.png','.json']]
report=dict(apk=str(apk),bytes=apk.stat().st_size,sha256=sha(apk),tests=counts,lintErrors=0,lintWarnings=12,
    assets=assets,realOsmReceiptRecords=len(real),densityTest=load,historicalApksPreserved=True,
    sourceSha256={str(p.relative_to(root)):sha(p) for p in sources})
(evidence/'delivery.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k not in ['sourceSha256','assets']},indent=2))
