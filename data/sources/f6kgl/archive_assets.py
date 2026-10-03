"""Fetch the illustrations referenced by the requested F6KGL course.

The original course retains its CC BY-NC-SA 4.0 license and attribution.
Files are preserved for offline analysis; the Android lessons are original.
"""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin
from urllib.request import urlopen
import hashlib
import json
import re

root = Path(__file__).resolve().parent
page = (root / 'COURS.html').read_bytes()
html = page.decode('cp1252')
assets = sorted(set(re.findall(r'src\s*=\s*"([^"\s>]+)"', html, re.I)))

def fetch(asset):
    target = (root / asset).resolve()
    if not target.is_relative_to(root):
        return {'path': asset, 'error': 'outside archive root'}
    url = urljoin('http://f6kgl.free.fr/COURS.html', asset)
    try:
        if not target.exists():
            with urlopen(url, timeout=30) as response:
                content = response.read()
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
        content = target.read_bytes()
        return {'path': asset, 'url': url, 'bytes': len(content), 'sha256': hashlib.sha256(content).hexdigest()}
    except Exception as exc:
        return {'path': asset, 'url': url, 'error': str(exc)}

with ThreadPoolExecutor(max_workers=8) as pool:
    entries = list(pool.map(fetch, assets))
manifest = {'source': 'http://f6kgl.free.fr/COURS.html', 'edition': 'novembre 2025',
            'downloadedAt': datetime.now(timezone.utc).isoformat(),
            'license': 'CC BY-NC-SA 4.0', 'pageSha256': hashlib.sha256(page).hexdigest(), 'assets': entries}
(root / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'assets': len(entries), 'downloaded': sum('bytes' in e for e in entries),
                  'errors': [e for e in entries if 'error' in e]}, ensure_ascii=False))
