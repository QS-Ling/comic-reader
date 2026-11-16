"""Scan first-level official catalog filters and their advertised pagination, resumably."""
import concurrent.futures
import hashlib
import html
import json
import pathlib
import re
import time
import urllib.request

BASE = 'https://www.qimanwu.app'
ROOT = pathlib.Path(__file__).resolve().parents[2] / 'build' / 'qiman-scan'
ROOT.mkdir(exist_ok=True)

def file_for(url):
    return ROOT / (hashlib.sha256(url.encode()).hexdigest() + '.html')

def fetch(url):
    path = file_for(url)
    if path.exists():
        return url, path.read_text(encoding='utf-8-sig')
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36'})
            with urllib.request.urlopen(request, timeout=25) as response:
                data = response.read(4_000_001)
            if len(data) > 4_000_000:
                raise ValueError('Oversized page')
            text = data.decode('utf-8-sig')
            if '/category' in url and not re.search(r'class=["\'][^"\']*\bmainForm\b', text):
                raise ValueError('Not an official category listing')
            path.write_text(text, encoding='utf-8')
            time.sleep(.1)
            return url, text
        except Exception:
            if attempt == 2:
                raise
            time.sleep(attempt + 1)

_, category = fetch(BASE + '/category')
roots = {BASE + '/category', BASE + '/category/order/addtime', BASE + '/category/order/hits'}
roots.update(BASE + html.unescape(p) for p in re.findall(r'href=["\'](/category/(?:tags|list|status)/[A-Za-z0-9]+)["\']', category))
pages = {BASE + '/': 'home'}
errors = []
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
    futures = {pool.submit(fetch, url): url for url in sorted(roots)}
    for i, future in enumerate(concurrent.futures.as_completed(futures), 1):
        url = futures[future]
        try:
            _, text = future.result()
            pages[url] = 'catalog'
            # Discover only actual numeric pagination links within this root.
            for path in re.findall(r'href=["\']([^"\']+)["\']', text):
                path = html.unescape(path)
                match = re.fullmatch(re.escape(url.removeprefix(BASE)) + r'/page/(\d+)', path)
                if match:
                    for number in range(2, int(match[1]) + 1):
                        pages[url + '/page/' + str(number)] = 'catalog'
        except Exception as exc:
            errors.append((url, str(exc)))
        if i % 20 == 0 or i == len(roots):
            print(f'Discovered {i}/{len(roots)} roots; {len(pages)} total public pages', flush=True)
    remaining = [url for url in pages if not file_for(url).exists()]
    futures = {pool.submit(fetch, url): url for url in remaining}
    for i, future in enumerate(concurrent.futures.as_completed(futures), 1):
        try:
            future.result()
        except Exception as exc:
            errors.append((futures[future], str(exc)))
            if len(errors) <= 5:
                print('Page error:', futures[future], str(exc), flush=True)
        if i % 100 == 0 or i == len(remaining):
            print(f'Downloaded {i}/{len(remaining)} remaining pages; errors={len(errors)}', flush=True)

manifest = [(url, str(file_for(url))) for url in sorted(pages) if file_for(url).exists()]
(ROOT / 'manifest.tsv').write_text(''.join(url + '\t' + path + '\n' for url, path in manifest), encoding='utf-8')
(ROOT / 'report.json').write_text(json.dumps({'roots': len(roots), 'pages': len(manifest), 'expected_pages': len(pages), 'errors': errors}, ensure_ascii=False, indent=2), encoding='utf-8')
print(f'Scan finished: {len(manifest)} pages, {len(errors)} errors', flush=True)
if errors:
    raise SystemExit(1)
