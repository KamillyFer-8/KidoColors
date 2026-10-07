"""Preserve a redacted copy of the local runtime log; never emit environment values."""
import pathlib, re, urllib.parse, shutil
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'results/experimental-study-2026-10-07-run-2-scanner-v2'
log=(root/'.maven-cache/run2-runtime.log').read_text(encoding='utf-8-sig',errors='replace')
for line in (root/'.env.local').read_text(encoding='utf-8-sig').splitlines():
    if '=' not in line or line.lstrip().startswith('#'):continue
    key,value=line.split('=',1)
    if key.strip() in ('DB_PASSWORD','DB_USERNAME','DB_URL'):
        value=value.strip().strip('"').strip("'")
        if value:log=log.replace(value,'[REDACTED]')
def safe_url(match):
    raw=match.group(0)
    try:
        u=urllib.parse.urlsplit(raw)
        return urllib.parse.urlunsplit((u.scheme,u.hostname or '',u.path,'',''))
    except ValueError:return '[REDACTED_URL]'
log=re.sub(r'https?://[^\s<>"\]]+',safe_url,log)
log=re.sub(r'(?i)((?:password|authorization|cookie|token|secret)\s*[:=]\s*)[^\s,;]+',r'\1[REDACTED]',log)
(out/'runtime-sanitized.log').write_text('Runtime log snapshot. Database values, URL queries/userinfo/fragments and credential fields redacted.\n'+log,encoding='utf-8')
for name in ('Run2Launcher.java','start-run2.ps1'):
    target=out/'execution-support'/name;target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(root/'.maven-cache/run2-launcher'/name,target)
for name in ('run2-experiment.py','archive-run2.py','seal-run2.py','archive-run2-log.py'):
    shutil.copyfile(root/'scripts'/name,out/'execution-support'/name)
shutil.copyfile(root/'.maven-cache/run2-csv-builder.mjs',out/'execution-support/run2-csv-builder.mjs')
print('Sanitized runtime log and execution support preserved separately for Run 2.')
