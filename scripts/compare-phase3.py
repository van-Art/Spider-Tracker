"""Measured screenshot comparison; no image is used as application artwork."""
from pathlib import Path
from PIL import Image, ImageChops, ImageStat, ImageDraw
import hashlib
import json

root = Path(__file__).resolve().parents[1]
evidence = root / 'docs/phase-3-evidence'
reference = Image.open(evidence / 'reference/approved.png').convert('RGB')
android = Image.open(root / 'docs/phase-3-screenshots/reference-map.png').convert('RGB')
assert reference.size == (941, 1672) and android.size == (780, 1386)
scaled = reference.resize(android.size, Image.Resampling.LANCZOS)
diff = ImageChops.difference(scaled, android)
mae = sum(ImageStat.Stat(diff).mean) / 3
scaled.save(evidence / 'reference/approved-normalized.png')
diff.point(lambda x: min(x * 3, 255)).save(evidence / 'difference-3x.png')
board = Image.new('RGB', (android.width * 2, android.height + 28), '#071623')
board.paste(scaled, (0, 28))
board.paste(android, (android.width, 28))
draw = ImageDraw.Draw(board)
draw.text((8, 8), 'APPROVED REFERENCE (normalized)', fill='white')
draw.text((android.width + 8, 8), 'ACTUAL ANDROID EMULATOR', fill='white')
board.save(evidence / 'comparison.png')

# Reference bounds manually read from supplied pixels, tolerance approximately 2px.
# Android bounds come from capture geometry and matching native UI bounds at 320dpi.
ref_rects = {
    'map': [63, 67, 876, 1521],
    'header': [242, 39, 703, 111],
    'radar': [589, 1263, 835, 1511],
    'search_visible_outline': [110, 1535, 810, 1628],
    'global_visible_icon': [811, 1346, 878, 1412],
    'location_visible_icon': [758, 1455, 827, 1525],
    'character': [31, 1527, 102, 1648],
}
actual_rects = {
    'map': [54, 56, 726, 1270],
    'header': [199, 32, 581, 96],
    'radar': [491, 1041, 698, 1248],
    'search_visible_outline': [92, 1278, 668, 1358],
    'global_visible_icon': [670, 1117, 726, 1173],
    'location_visible_icon': [634, 1200, 690, 1256],
    'character': [20, 1264, 84, 1366],
}
geometry = {}
for key, rect in ref_rects.items():
    norm = [rect[0] * 780 / 941, rect[1] * 1386 / 1672, rect[2] * 780 / 941, rect[3] * 1386 / 1672]
    actual = actual_rects[key]
    geometry[key] = dict(referencePixels=rect, referenceDp=[round(v/2, 2) for v in norm],
                         androidPixels=actual, androidDp=[v/2 for v in actual],
                         edgeDeltaDp=[round((a-r)/2, 2) for a, r in zip(actual, norm)])
measurements = dict(
    referenceSha256=hashlib.sha256((evidence/'reference/approved.png').read_bytes()).hexdigest(),
    androidSha256=hashlib.sha256((root/'docs/phase-3-screenshots/reference-map.png').read_bytes()).hexdigest(),
    referenceSize=list(reference.size), androidSize=list(android.size), densityDpi=320,
    referenceMeasurementTolerancePixels=2, geometry=geometry,
    samples={
        'referenceFrame': list(reference.getpixel((25, 500))), 'androidFrame': list(android.getpixel((20, 400))),
        'referenceWater': list(reference.getpixel((550, 900))), 'androidWater': list(android.getpixel((460, 740))),
        'referenceInput': list(reference.getpixel((650, 1600))), 'androidInput': list(android.getpixel((550, 1350))),
    },
    wholeImageMeanAbsoluteRgbError=round(mae, 3),
    metricMeaning='Diagnostic RGB error out of 255, not a visual-fidelity percentage. No masking; includes changed artwork, map labels, fixtures and textures.',
)
(evidence / 'visual-measurements.json').write_text(json.dumps(measurements, indent=2), encoding='utf-8')
print(json.dumps(measurements, indent=2))
