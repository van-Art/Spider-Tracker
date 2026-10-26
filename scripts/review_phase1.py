"""Inventory every Phase 1 evidence file and create visual contact sheets for review."""
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

root = Path(__file__).resolve().parents[1]
analysis = root / 'analysis'
out = root / 'docs' / 'evidence' / 'phase1-review'
out.mkdir(parents=True, exist_ok=True)
records = []
for file in sorted(analysis.rglob('*')):
    if not file.is_file():
        continue
    data = file.read_bytes()
    record = {'file': str(file.relative_to(root)), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
    if file.suffix == '.json':
        parsed = json.loads(data)
        record['shape'] = list(parsed) if isinstance(parsed, dict) else {'entries': len(parsed)}
        if isinstance(parsed, dict) and 'normalized_result' in parsed:
            result = parsed['normalized_result']
            record['predicate'] = parsed.get('predicate_type')
            record['completeness'] = result.get('completeness')
            record['step_states'] = {s['step_id']: s['status'] for s in result.get('steps', [])}
    records.append(record)
images = sorted((analysis / 'references').glob('*.png')) + sorted((root / 'Screenshots').glob('*.png'))
for start in range(0, len(images), 20):
    sheet = Image.new('RGB', (1000, 1450), '#d8e1e6')
    draw = ImageDraw.Draw(sheet)
    for index, file in enumerate(images[start:start+20]):
        original = Image.open(file).convert('RGB')
        thumb = ImageOps.contain(original, (242, 260))
        x, y = (index % 4)*250, (index//4)*290
        sheet.paste(thumb, (x+(250-thumb.width)//2, y))
        label = file.stem
        draw.text((x+4,y+262), label[:35], fill='black')
        draw.text((x+4,y+275), label[35:70], fill='black')
    sheet.save(out / f'contact-{start//20+1:02}.jpg', quality=88)
summary = {'files': len(records), 'images': len(images), 'records': records}
(out / 'inventory.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print({'read_files': len(records), 'review_images': len(images), 'contact_sheets': (len(images)+19)//20})
