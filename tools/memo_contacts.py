from pathlib import Path
from PIL import Image, ImageDraw
import sys

folder = Path(sys.argv[1] if len(sys.argv) > 1 else 'output/memo-upgrade')
out = folder.parent / (folder.name + '-contacts')
out.mkdir(exist_ok=True)
latest = {}
for path in folder.rglob('*.png'):
    if path.stem not in latest or path.stat().st_mtime > latest[path.stem].stat().st_mtime:
        latest[path.stem] = path
for kind in ['top', 'bottom', 'diagram', 'detail-formulas', 'detail', 'question', 'practice', '00-']:
    paths = sorted(p for p in latest.values() if (p.stem.endswith('-' + kind) if kind in ['top', 'bottom', 'diagram'] else p.stem.startswith(kind)))
    for offset in range(0, len(paths), 8):
        selected = paths[offset:offset+8]
        sheet = Image.new('RGB', (4*285, 2*650), '#e7e7e7')
        draw = ImageDraw.Draw(sheet)
        for i, path in enumerate(selected):
            image = Image.open(path).convert('RGB')
            image.thumbnail((275, 610))
            x = (i % 4) * 285 + 5
            y = (i // 4) * 650
            draw.text((x, y+4), path.stem, fill='black')
            sheet.paste(image, (x, y+25))
        sheet.save(out / f'{kind}-{offset//8+1}.jpg', quality=94)
print(out)
