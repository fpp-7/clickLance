"""Sonda HTTP autenticada: fixture via stdin, sem credenciais em argv/relatorio.

Entrada JSON: {base_url, access_token, path, requests?, concurrency?}.
Somente GET em caminhos de jogador; nao mede captura/latencia de video.
"""
import concurrent.futures
import json
import statistics
import sys
import time
from urllib.parse import urlsplit
import requests


def run(p):
    base = urlsplit(p['base_url'])
    if base.scheme not in ('http', 'https') or not base.hostname or base.username or base.password:
        raise ValueError('invalid base_url')
    path = p['path']
    if not path.startswith('/api/v1/jogadores/') or '..' in path or '://' in path:
        raise ValueError('read-only player path required')
    count = int(p.get('requests', 60))
    concurrency = int(p.get('concurrency', 4))
    if not 1 <= count <= 10000 or not 1 <= concurrency <= 16:
        raise ValueError('invalid workload')
    def one(_):
        start = time.perf_counter()
        r = requests.get(p['base_url'].rstrip('/') + path,
                         headers={'Authorization': 'Bearer ' + p['access_token']},
                         timeout=(5, 15), allow_redirects=False)
        return r.status_code, (time.perf_counter() - start) * 1000
    start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
        results = list(pool.map(one, range(count)))
    elapsed = time.perf_counter() - start
    latencies = sorted(t for _, t in results)
    statuses = {str(s): sum(1 for code, _ in results if code == s) for s, _ in results}
    def percentile(fraction):
        return round(latencies[min(len(latencies)-1, int((len(latencies)-1) * fraction))], 3)
    print(json.dumps(dict(requests=count, concurrency=concurrency, elapsed_seconds=round(elapsed, 3),
                          statuses=statuses, p50_ms=percentile(.50), p95_ms=percentile(.95), p99_ms=percentile(.99),
                          mean_ms=round(statistics.mean(latencies), 3)), indent=2))
    if any(code != 200 for code, _ in results):
        raise SystemExit(1)


if __name__ == '__main__':
    run(json.load(sys.stdin))
