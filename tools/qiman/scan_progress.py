import hashlib
import html
import pathlib
import re

base = 'https://www.qimanwu.app'
root = pathlib.Path(__file__).resolve().parents[2] / 'build' / 'qiman-scan'
def path(url):
    return root / (hashlib.sha256(url.encode()).hexdigest() + '.html')
category = path(base + '/category').read_text(encoding='utf-8')
roots = {base + '/category', base + '/category/order/addtime', base + '/category/order/hits'}
roots.update(base + html.unescape(p) for p in re.findall(r'href=["\'](/category/(?:tags|list|status)/[A-Za-z0-9]+)["\']', category))
urls = {base + '/'} | roots
for url in roots:
    if not path(url).exists():
        continue
    text = path(url).read_text(encoding='utf-8')
    for p in re.findall(r'href=["\']([^"\']+)["\']', text):
        match = re.fullmatch(re.escape(url.removeprefix(base)) + r'/page/(\d+)', html.unescape(p))
        if match:
            urls.update(url + '/page/' + str(i) for i in range(2, int(match[1]) + 1))
done = [url for url in sorted(urls) if path(url).exists()]
(root / 'manifest-progress.tsv').write_text(''.join(url + '\t' + str(path(url)) + '\n' for url in done), encoding='utf-8')
print(f'Cached {len(done)}/{len(urls)} pages')
