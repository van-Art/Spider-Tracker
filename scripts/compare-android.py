"""Normalize real emulator captures and retain auditable, region-specific comparisons."""
import base64
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageStat

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs' / 'comparisons'
OUT.mkdir(parents=True, exist_ok=True)
SIZES = {'phone': (390, 844), 'tablet': (768, 1024), 'landscape': (844, 390)}
REFERENCES = {
    'phone-map': 'phone-map-ready.png', 'phone-menu': 'phone-log-menu-settled.png',
    'phone-log': 'phone-log-content.png', 'phone-detail': 'detail-phone-message-open.png',
    'phone-events': 'phone-events-content.png', 'phone-report': 'phone-report-loaded.png',
    'phone-webwatch': 'phone-webwatch-content.png', 'phone-help': 'phone-help-content.png',
    'phone-videos': 'detail-phone-trailer-result.png', 'phone-media': 'detail-phone-sighting-media-open.png',
    'phone-gallery': 'videos-phone-gallery-list-visible.png',
    'tablet-help': 'tablet-help-content.png', 'landscape-map': 'landscape-map-ready.png',
}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def mask(rectangles, size=(390, 844)):
    image = Image.new('L', size, 0)
    draw = ImageDraw.Draw(image)
    for x0, y0, x1, y1 in rectangles:
        draw.rectangle((x0, y0, x1-1, y1-1), fill=255)
    return image

def metric(reference, actual, region=None):
    difference = ImageChops.difference(reference, actual)
    channels = difference.split()
    maximum = ImageChops.lighter(ImageChops.lighter(channels[0], channels[1]), channels[2])
    changed = maximum.point(lambda value: 255 if value > 16 else 0)
    area = reference.width * reference.height
    if region:
        changed = ImageChops.multiply(changed, region)
        area = region.histogram()[255]
    count = changed.histogram()[255]
    return {'pixels': area, 'changedPixelsThreshold16': count,
            'changedPercent': round(count / area * 100, 3),
            'meanAbsoluteChannelError': [round(v, 3) for v in ImageStat.Stat(difference, region).mean]}

bands_rects = [(6, 210, 15, 600), (16, 210, 25, 600),
               (375, 210, 384, 600), (365, 210, 374, 600),
               (61, 6, 105, 15), (285, 6, 328, 15)]
bands = mask(bands_rects)
bands.save(OUT / 'phone-frame-bands-mask.png')
chrome = mask([(0, 0, 390, 674)])
exclusions = [(29, 29, 361, 615), (10, 11, 60, 61), (105, 16, 285, 47),
              (330, 14, 378, 62), (0, 140, 38, 170), (0, 174, 38, 204),
              (6, 615, 46, 674), (54, 633, 336, 666), (338, 633, 381, 666)]
chrome = ImageChops.subtract(chrome, mask(exclusions))
chrome.save(OUT / 'phone-frame-chrome-mask.png')
records = []
for file in sorted((ROOT / 'docs' / 'screenshots').glob('*-final.png')):
    name = file.stem.removesuffix('-final')
    viewport = name.split('-')[0]
    logical = SIZES[viewport]
    raw = Image.open(file).convert('RGB')
    assert raw.size == (logical[0]*2, logical[1]*2), (file, raw.size)
    actual = raw.resize(logical, Image.Resampling.BOX)
    normalized = OUT / f'{name}-android.png'
    actual.save(normalized)
    record = {'state': name, 'raw': str(file.relative_to(ROOT)), 'rawSha256': digest(file),
              'rawPixels': raw.size, 'logicalViewport': logical, 'densityDpi': 320,
              'fontScale': 1.0, 'normalization': 'RGB; exact 2:1 BOX downsample; no crop',
              'normalized': str(normalized.relative_to(ROOT)), 'normalizedSha256': digest(normalized),
              'capturedAtUtc': datetime.fromtimestamp(file.stat().st_mtime, timezone.utc).isoformat(),
              'debugCapture': True, 'decorativeClockSeconds': 0.7,
              'fixture': 'MockSightings v1: 23 confirmed, 29 rumored, 8 event, 1 trailer',
              'engine': 'MapLibre Native 13.6.1; API 37 x86_64; SwiftShader',
              'cameraRequest': {'lat': 15.973899714245507, 'lon': -49.9007427311529, 'zoom': 1.0}}
    device_file = file.with_suffix('.png.json')
    device = json.loads(device_file.read_text(encoding='utf-8-sig'))
    assert device['avd'] == 'Medium_Phone_API_37.0' and device['api'] == 37
    assert float(device['fontScale']) == 1.0
    record['deviceEvidence'] = device
    if name.endswith(('-detail', '-card', '-media')):
        record['cameraRequest'] = {'lat': 38.72, 'lon': -9.14, 'zoom': 12.597060635860297}
    if name not in REFERENCES:
        record['comparison'] = 'Additional Android state; no same-viewport canonical pair selected.'
        records.append(record)
        continue
    source = ROOT / 'analysis' / 'references' / REFERENCES[name]
    reference = Image.open(source).convert('RGB')
    assert reference.size == actual.size
    record.update(reference=str(source.relative_to(ROOT)), referenceSha256=digest(source),
                  fullScreenDiagnostic=metric(reference, actual))
    if name == 'phone-map':
        record['uninterruptedFrameBands'] = metric(reference, actual, bands)
        record['frameChromeExcludingReplacementEnvelopes'] = metric(reference, actual, chrome)
    board = Image.new('RGB', (logical[0]*2+12, logical[1]+26), '#dce4e7')
    draw = ImageDraw.Draw(board)
    draw.text((5, 7), 'PHASE 1 REFERENCE', fill='black')
    draw.text((logical[0]+17, 7), 'NATIVE ANDROID / ORIGINAL ASSETS', fill='black')
    board.paste(reference, (0, 26)); board.paste(actual, (logical[0]+12, 26))
    board.save(OUT / f'{name}-side-by-side.png')
    Image.blend(reference, actual, .5).save(OUT / f'{name}-overlay.png')
    ImageChops.difference(reference, actual).save(OUT / f'{name}-difference.png')
    records.append(record)

phone = next(r for r in records if r['state'] == 'phone-map')
reference = Image.open(ROOT / phone['reference']).convert('RGB')
iterations = []
for iteration in ('iteration1', 'iteration2', 'final'):
    file = ROOT / 'docs' / 'screenshots' / f'phone-map-{iteration}.png'
    actual = Image.open(file).convert('RGB').resize((390, 844), Image.Resampling.BOX)
    iterations.append({'iteration': iteration, 'sha256': digest(file),
                       'frameBands': metric(reference, actual, bands),
                       'frameChrome': metric(reference, actual, chrome)})
    actual.save(OUT / f'phone-map-{iteration}-normalized.png')

# Self-verifying artifacts for the independent REA pixel comparator.
for name, file in [('reference', ROOT / phone['reference']),
                   ('android', OUT / 'phone-map-android.png')]:
    data = file.read_bytes()
    value = {'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data),
             'media_type': 'image/png', 'data_base64': base64.b64encode(data).decode()}
    (OUT / f'rea-phone-{name}.artifact.json').write_text(json.dumps(value), encoding='utf-8')
summary = {'generatedAtUtc': datetime.now(timezone.utc).isoformat(), 'channelThreshold': 16,
           'warning': 'Whole-screen difference is diagnostic, not a fidelity score: mock stories, original artwork/font and OSM cartography differ.',
           'masks': {'frameBands': bands_rects, 'frameChromeBase': [0,0,390,674],
                     'frameChromeExclusions': exclusions,
                     'reason': 'Map content and licensed/original replacement asset envelopes. Misplaced paint outside envelopes remains counted.'},
           'iterations': iterations, 'captures': records}
delivery = json.loads((ROOT / 'docs' / 'evidence' / 'delivery.json').read_text(encoding='utf-8'))
summary['apk'] = {'file': delivery['apk'], 'sha256': delivery['sha256'], 'bytes': delivery['bytes']}
(OUT / 'comparison-metrics.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
print(json.dumps({'captures': len(records), 'pairedReferences': sum('reference' in r for r in records),
                  'iterations': iterations}, indent=2))
