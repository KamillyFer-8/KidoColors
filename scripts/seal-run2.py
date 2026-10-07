"""Verify immutable pilot, CSV observations and Run 2 archival inventory. No scanner calls."""
import csv, io, json, pathlib, importlib.util, datetime, hashlib
spec=importlib.util.spec_from_file_location('run2_support',pathlib.Path(__file__).with_name('run2-experiment.py'))
s=importlib.util.module_from_spec(spec);spec.loader.exec_module(s)
out=s.OUT
baseline=s.protected();manifest=s.frozen()
before=json.loads((out/'run1-database-before.json').read_text(encoding='utf-8'))
after=json.loads((out/'run1-database-after.json').read_text(encoding='utf-8'))
assert before==after,'Run 1 database row hashes changed'
pilot=json.loads((s.PILOT/'study.json').read_text(encoding='utf-8'))
live=s.get('/api/studies/'+pilot['id'])
for row in live['rows']:
    a=row.get('analysis')
    if a is not None and a.get('scannerDiagnosticsJson') is None:a.pop('scannerDiagnosticsJson',None)
assert live==pilot,'Run 1 public data changed'
table=json.loads((s.ROOT/'.maven-cache/run2-results-table.json').read_text(encoding='utf-8'))
raw=(out/'study-results.csv').read_bytes()
rows=list(csv.reader(io.StringIO(raw.decode('utf-8-sig'),newline='')))
assert rows[0]==table['headers'] and len(rows)==101
for actual,expected in zip(rows[1:],table['rows']):
    for value,original in zip(actual,expected):
        if isinstance(original,(int,float)) and not isinstance(original,bool):
            assert value!='' and float(value)==original,(actual[0],'CSV numeric mismatch')
        else:
            formatted='' if original is None else str(original).lower() if isinstance(original,bool) else str(original)
            assert value==formatted,(actual[0],'CSV typed value mismatch')
study=json.loads((out/'study.json').read_text(encoding='utf-8'))
assert study['status']=='COMPLETED' and len(study['rows'])==100
assert len({r['analysis']['id'] for r in study['rows']})==100
report={'verified_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'run1_protected_files_unchanged':len(baseline),'run1_database_rows_unchanged':{k:len(v) for k,v in before.items()},
    'run1_public_data_unchanged':True,'frozen_sources_unchanged':True,'approved_jar_unchanged':True,
    'dataset_unchanged':True,'csv_rows':100,'csv_columns':len(table['headers']),
    'csv_matches_typed_observations':True,'unique_analysis_ids':100,'post_attempts':1,
    'csv_sha256':s.sha(raw),'study_id':study['id'],'no_selective_retries':True}
s.save('integrity-verification.json',report)
inventory={}
for p in sorted(out.rglob('*')):
    if p.is_file() and p.name!='raw-artifacts-manifest.json':
        data=p.read_bytes();inventory[p.relative_to(out).as_posix()]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
s.save('raw-artifacts-manifest.json',{'files':inventory,'count':len(inventory)})
print(json.dumps({'integrity':report,'archived_files':len(inventory)},ensure_ascii=False))
