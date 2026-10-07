"""Read-only view of the exact persisted experimental study checkpoint."""
import datetime,json,pathlib,urllib.request
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'results/experimental-study-2026-10-07'
reference=json.loads((out/'study-reference.json').read_text(encoding='utf-8'))
with urllib.request.urlopen('http://127.0.0.1:8080/api/studies/'+reference['study_id'],timeout=30) as response:
    raw=response.read()
study=json.loads(raw)
manifest=json.loads((out/'manifest.json').read_text(encoding='utf-8'))
assert study['datasetSha256']==manifest['import_sha256']
(out/'checkpoint-latest.json').write_bytes(raw)
result={'observed_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'study_id':study['id'],'status':study['status'],'planned':study['totalRows'],
    'processed':study['metrics']['processed'],'completed':study['metrics']['completed'],
    'failed':study['metrics']['failed'],
    'last_processed_source_id':study['rows'][-1]['sourceId'] if study['rows'] else None,
    'failure_codes':{code:sum(r['errorCode']==code for r in study['rows']) for code in {r['errorCode'] for r in study['rows'] if r['errorCode']}}}
(out/'progress-snapshot.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result),flush=True)
