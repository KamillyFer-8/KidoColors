"""Run one real, sequential KidoColors study. Never retries the POST."""
import csv, datetime, hashlib, io, json, pathlib, subprocess, urllib.request, urllib.error

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'results' / 'experimental-study-2026-10-07'
BASE = 'http://127.0.0.1:8080'

def digest(data):
    return hashlib.sha256(data).hexdigest()

def save_json(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

def get_json(route):
    with urllib.request.urlopen(BASE + route, timeout=60) as response:
        return json.load(response)

def main():
    if OUT.exists():
        raise SystemExit('Output already exists. Inspect the existing run; do not resend automatically.')
    original = (ROOT / 'datasets/websites.csv').read_bytes()
    reader = csv.DictReader(io.StringIO(original.decode('utf-8-sig'), newline=''))
    expected = ['id', 'site', 'url', 'categoria', 'criterio_selecao', 'data_selecao', 'status_verificacao', 'observacao']
    assert reader.fieldnames == expected
    rows = list(reader)
    assert len(rows) == 100
    for column in ('id', 'site', 'url'):
        assert len({r[column].strip().casefold().rstrip('/') for r in rows}) == 100
    categories = {c: sum(r['categoria'] == c for r in rows) for c in {r['categoria'] for r in rows}}
    assert len(categories) == 5 and set(categories.values()) == {20}
    assert get_json('/api/health')['status'] == 'UP'
    revision = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    changes = subprocess.check_output(['git', 'diff', '--name-only', '--', 'backend', 'kidocolors-core', 'frontend'], cwd=ROOT, text=True).strip()
    assert not changes, 'Analysis application sources have uncommitted changes.'
    projected = io.StringIO(newline='')
    writer = csv.DictWriter(projected, fieldnames=['id', 'site', 'url', 'categoria'], lineterminator='\r\n')
    writer.writeheader()
    writer.writerows({k: r[k] for k in writer.fieldnames} for r in rows)
    imported = projected.getvalue().encode('utf-8-sig')
    preflight_files = ['experimental-preflight-java.log', 'experimental-preflight-vitest.log', 'experimental-preflight-lint.log', 'experimental-preflight-frontend-build.log']
    preflight = {name: (ROOT / '.maven-cache' / name).read_bytes() for name in preflight_files}
    assert b'BUILD SUCCESS' in preflight['experimental-preflight-java.log']
    assert b'24 passed' in preflight['experimental-preflight-vitest.log']
    assert b'built in' in preflight['experimental-preflight-frontend-build.log']
    OUT.mkdir(parents=True)
    (OUT / 'preflight').mkdir()
    for name,data in preflight.items():
        (OUT / 'preflight' / name).write_bytes(data)
    (OUT / 'dataset-selection-original.csv').write_bytes(original)
    (OUT / 'dataset-selection-original.xlsx').write_bytes((ROOT / 'datasets/kidocolors-dataset.xlsx').read_bytes())
    (OUT / 'dataset-import.csv').write_bytes(imported)
    manifest = {'code_revision': revision, 'selection_sha256': digest(original), 'import_sha256': digest(imported),
                'jar_sha256': digest((ROOT / 'backend/target/backend-0.1.0-SNAPSHOT.jar').read_bytes()),
                'category_counts': categories, 'dataset_rows': len(rows), 'post_attempts': 1,
                'api_base': BASE, 'created_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'note': 'Original eight-column selection preserved; only four columns projected for the existing importer. No export implementation changed.'}
    save_json('manifest.json', manifest)
    boundary = 'KidoColorsExperimentalStudy20261007'
    name = 'Estudo experimental real - 100 sites - 2026-10-07'
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="name"\r\n\r\n{name}\r\n'
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="dataset-import.csv"\r\n'
            'Content-Type: text/csv; charset=UTF-8\r\n\r\n').encode('utf-8') + imported + f'\r\n--{boundary}--\r\n'.encode()
    request = urllib.request.Request(BASE + '/api/studies', data=body,
        headers={'Content-Type': f'multipart/form-data; boundary={boundary}'}, method='POST')
    print('Validated 100 distinct input rows, 20 per category. Sending ONE POST to the existing real study engine.', flush=True)
    try:
        with urllib.request.urlopen(request, timeout=14400) as response:
            raw = response.read()
            (OUT / 'study-post-response.json').write_bytes(raw)
            save_json('post-response-metadata.json', {'status': response.status, 'location': response.headers.get('Location')})
        study = json.loads(raw)
        save_json('study.json', get_json('/api/studies/' + study['id']))
        assert digest((ROOT / 'datasets/websites.csv').read_bytes()) == manifest['selection_sha256']
        print(json.dumps({'study_id': study['id'], 'status': study['status'], 'totalRows': study['totalRows'], 'metrics': study['metrics']}, ensure_ascii=True), flush=True)
    except Exception as error:
        save_json('client-error.json', {'type': type(error).__name__, 'message': str(error), 'automatic_retry': False})
        raise

if __name__ == '__main__':
    main()
