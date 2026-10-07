"""Controlled Run 2 support. One POST only; existing output and submission guards prevent retries."""
import argparse, collections, csv, datetime, hashlib, io, json, pathlib, shutil, subprocess, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
PILOT = ROOT / 'results/experimental-study-2026-10-07'
OUT = ROOT / 'results/experimental-study-2026-10-07-run-2-scanner-v2'
BASE = 'http://127.0.0.1:8080'
NAME = 'Run 2 oficial - Scanner V2 - 100 sites - 2026-10-07'

def sha(data): return hashlib.sha256(data).hexdigest()
def save(name, data):
    path = OUT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
def get(route):
    with urllib.request.urlopen(BASE + route, timeout=60) as response: return json.load(response)
def protected():
    baseline = json.loads((ROOT/'.maven-cache/v2-run1-integrity.json').read_text(encoding='utf-8'))
    for name, digest in baseline.items():
        assert (ROOT/name).is_file() and sha((ROOT/name).read_bytes()) == digest, 'Protected pilot artifact changed: ' + name
    return baseline
def frozen():
    manifest = json.loads((OUT/'manifest.json').read_text(encoding='utf-8'))
    for name,digest in manifest['source_files_sha256'].items(): assert sha((ROOT/name).read_bytes()) == digest, name
    assert sha((OUT/'backend-used.jar').read_bytes()) == manifest['jar_sha256']
    assert sha((ROOT/'datasets/websites.csv').read_bytes()) == manifest['selection_sha256']
    return manifest
def prepare():
    assert not OUT.exists(), 'Existing output: inspect and recover, never submit automatically.'
    baseline = protected()
    original = (ROOT/'datasets/websites.csv').read_bytes()
    assert original == (PILOT/'dataset-selection-original.csv').read_bytes()
    rows = list(csv.DictReader(io.StringIO(original.decode('utf-8-sig'), newline='')))
    old = json.loads((PILOT/'study.json').read_text(encoding='utf-8'))
    assert len(rows) == len(old['rows']) == 100
    assert len({r['id'] for r in rows}) == len({r['url'] for r in rows}) == 100
    for input_row,pilot_row in zip(rows,old['rows']):
        assert (input_row['id'],input_row['site'],input_row['url'],input_row['categoria']) == (
            pilot_row['sourceId'],pilot_row['site'],pilot_row['requestedUrl'],pilot_row['category'])
    categories = dict(collections.Counter(r['categoria'] for r in rows))
    assert len(categories)==5 and set(categories.values())=={20}
    validation = json.loads((ROOT/'docs/scanner-v2-validation.json').read_text(encoding='utf-8'))
    jar = ROOT/validation['v2_build']['path']
    assert sha(jar.read_bytes()) == validation['v2_build']['sha256']
    sources = [p for folder in ('backend/src','kidocolors-core/src') for p in (ROOT/folder).rglob('*') if p.is_file()]
    sources += [ROOT/'pom.xml',ROOT/'backend/pom.xml',ROOT/'kidocolors-core/pom.xml']
    source_hashes = {p.relative_to(ROOT).as_posix():sha(p.read_bytes()) for p in sorted(sources)}
    OUT.mkdir()
    for name in ('dataset-selection-original.csv','dataset-selection-original.xlsx','dataset-import.csv'):
        shutil.copyfile(PILOT/name,OUT/name)
    shutil.copyfile(jar,OUT/'backend-used.jar')
    shutil.copyfile(ROOT/'docs/scanner-v2.md',OUT/'approved-protocol-snapshot.md')
    shutil.copyfile(ROOT/'docs/scanner-v2-validation.json',OUT/'approved-validation-snapshot.json')
    for p in sources:
        dest = OUT/'source-snapshot'/p.relative_to(ROOT); dest.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(p,dest)
    for name in ('v2-verify.log','v2-vitest.log','v2-lint.log','v2-frontend-build.log'):
        dest=OUT/'preflight'/name;dest.parent.mkdir(exist_ok=True);shutil.copyfile(ROOT/'.maven-cache'/name,dest)
    save('run1-protected-files-before.json',baseline)
    save('manifest.json',{'run':2,'protocol':'scanner-v2','study_name':NAME,'pilot_id':old['id'],
        'created_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'code_revision':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'source_files_sha256':source_hashes,'jar_sha256':sha(jar.read_bytes()),
        'selection_sha256':sha(original),'import_sha256':sha((OUT/'dataset-import.csv').read_bytes()),
        'dataset_rows':100,'category_counts':categories,'same_ids_urls_categories_and_order_as_pilot':True,
        'individual_adjustments':False,'automatic_retry':False,'post_attempts':0,
        'expected_configuration':{'scannerProtocol':'scanner-v2','navigationWaitUntil':'DOMCONTENTLOADED',
            'timeoutMs':30000,'readinessTimeoutMs':5000,'settleMs':500,'viewportWidth':1280,'viewportHeight':720,
            'maxPageHeight':12000,'maxElements':2000,'maxRedirects':5},'api_base':BASE,
        'capture_storage_path':'storage/run-2-scanner-v2-2026-10-07'})
    print(json.dumps({'prepared':True,'rows':100,'categories':categories,'dataset_sha256':sha(original),
        'jar_sha256':sha(jar.read_bytes()),'protected_files_verified':len(baseline)}),flush=True)
def submit():
    manifest=frozen(); protected()
    assert get('/api/health')['status']=='UP'
    config=json.loads((OUT/'effective-config-before-run.json').read_text(encoding='utf-8'))
    assert config['configuration']==manifest['expected_configuration']
    assert config['database_product']=='PostgreSQL' and config['supabase_host_verified'] is True
    assert config['existing_run2_count']==0
    assert not (OUT/'submission-started.json').exists(), 'POST already attempted. Never resend.'
    manifest['effective_configuration']=config['configuration'];manifest['post_attempts']=1
    save('manifest.json',manifest)
    save('submission-started.json',{'started_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'post_attempts':1,'automatic_retry':False})
    data=(OUT/'dataset-import.csv').read_bytes(); boundary='KidoColorsRun2ScannerV220261007'
    body=(f'--{boundary}\r\nContent-Disposition: form-data; name="name"\r\n\r\n{NAME}\r\n'
          f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="dataset-import.csv"\r\n'
          'Content-Type: text/csv; charset=UTF-8\r\n\r\n').encode('utf-8')+data+f'\r\n--{boundary}--\r\n'.encode()
    request=urllib.request.Request(BASE+'/api/studies',data=body,headers={'Content-Type':f'multipart/form-data; boundary={boundary}'},method='POST')
    print('Sending the only POST: 100 unchanged rows, fixed Scanner V2 protocol.',flush=True)
    try:
        with urllib.request.urlopen(request,timeout=14400) as response:
            raw=response.read();(OUT/'study-post-response.json').write_bytes(raw)
            save('post-response-metadata.json',{'status':response.status,'location':response.headers.get('Location')})
        study=json.loads(raw);save('study-reference.json',{'study_id':study['id']});save('study.json',get('/api/studies/'+study['id']))
        print(json.dumps({'id':study['id'],'status':study['status'],'metrics':study['metrics']}),flush=True)
    except Exception as error:
        save('client-error.json',{'type':type(error).__name__,'automatic_retry':False})
        raise
def progress():
    frozen(); reference=json.loads((OUT/'study-reference.json').read_text(encoding='utf-8'))
    study=get('/api/studies/'+reference['study_id']);manifest=frozen()
    assert study['datasetSha256']==manifest['import_sha256']
    for key,value in manifest['expected_configuration'].items(): assert study['environment'][key]==value,(key,study['environment'][key],value)
    save('checkpoint-latest.json',study)
    result={'status':study['status'],'id':study['id'],'processed':len(study['rows']),
        'completed':study['metrics']['completed'],'failed':study['metrics']['failed'],
        'failure_codes':dict(collections.Counter(r['errorCode'] for r in study['rows'] if r['errorCode'])),
        'last_id':study['rows'][-1]['sourceId'] if study['rows'] else None}
    save('progress-snapshot.json',result);print(json.dumps(result),flush=True)
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['prepare','submit','progress'])
    {'prepare':prepare,'submit':submit,'progress':progress}[parser.parse_args().mode]()
