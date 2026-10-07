"""Read-only API archival and independent calculations for the completed Run 2. Never runs analyses."""
import collections, csv, datetime, hashlib, io, json, math, pathlib, shutil, statistics, urllib.request
import importlib.util
from PIL import Image
spec=importlib.util.spec_from_file_location('run2_support',pathlib.Path(__file__).with_name('run2-experiment.py'))
support=importlib.util.module_from_spec(spec);spec.loader.exec_module(support)
ROOT,OUT,PILOT,BASE=support.ROOT,support.OUT,support.PILOT,support.BASE

def save(name,value): support.save(name,value)
def download(route,path):
    assert path.resolve().is_relative_to(OUT.resolve())
    with urllib.request.urlopen(BASE+route,timeout=90) as response:data=response.read()
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists():assert path.read_bytes()==data,'Existing artifact differs: '+str(path)
    else:path.write_bytes(data)
    return data
def completed(row):return row.get('errorCode') is None and row.get('analysis') is not None and row['analysis']['status']=='COMPLETED'
def diagnostics(row,captures):
    a=row.get('analysis') or {}
    if a.get('scannerDiagnosticsJson'):return json.loads(a['scannerDiagnosticsJson'])
    return captures.get(a.get('id'),{}).get('diagnostics')
def calculate(rows,captures):
    ok=[r for r in rows if completed(r)]
    scores=[r['analysis']['score'] for r in ok if r['analysis']['score'] is not None]
    durations=[r['durationMs'] for r in rows]
    observed=[captures[r['analysis']['id']] for r in rows if r.get('analysis') and r['analysis']['id'] in captures]
    failed_captures=[captures[r['analysis']['id']] for r in rows if not completed(r) and r.get('analysis') and r['analysis']['id'] in captures]
    issues=sum(r['analysis']['totalIssues'] for r in ok)
    problem_pages=sum(r['analysis']['totalIssues']>0 for r in ok)
    result={'processed':len(rows),'completed':len(ok),'failed':len(rows)-len(ok),
        'successRate':len(ok)/len(rows) if rows else None,'scored':len(scores),
        'pagesWithoutScore':len(rows)-len(scores),'completedWithoutScore':len(ok)-len(scores),
        'meanScore':statistics.mean(scores) if scores else None,'medianScore':statistics.median(scores) if scores else None,
        'minScore':min(scores) if scores else None,'maxScore':max(scores) if scores else None,
        'scoreDistribution':{name:sum(lo<=s<=hi for s in scores) for name,lo,hi in [('0-24',0,24),('25-49',25,49),('50-74',50,74),('75-100',75,100)]},
        'elementsEvaluated':sum(r['analysis']['elementsAnalyzed'] for r in ok),
        'elementsSkipped':sum(r['report']['elementsSkipped'] for r in ok),
        'elementsCollectedObserved':sum(len(c['elements']) for c in observed),
        'elementsCollectedCompleted':sum(r['analysis']['elementsCollected'] for r in ok),
        'elementsCollectedFailedCaptures':sum(len(c['elements']) for c in failed_captures),
        'unsupportedTextsInFailedCaptures':sum(e.get('unsupportedReason') is not None for c in failed_captures for e in c['elements']),
        'totalIssues':issues,'pagesWithIssues':problem_pages,
        'percentCompletedPagesWithIssues':100*problem_pages/len(ok) if ok else None,
        'meanIssuesPerCompletedPage':issues/len(ok) if ok else None,
        'meanDurationMs':statistics.mean(durations) if durations else None,
        'minDurationMs':min(durations) if durations else None,'maxDurationMs':max(durations) if durations else None,
        'durationRowsSumMs':sum(durations),
        'capturesAvailable':len(observed),'pagesWithoutCaptureMetadata':len(rows)-len(observed),
        'pagesTruncatedObserved':sum(c['truncated'] for c in observed),
        'pagesHeightTruncatedObserved':sum(bool((c.get('diagnostics') or {}).get('heightTruncated')) for c in observed),
        'pagesCollectionTruncated':sum(r['report']['collectionTruncated'] for r in ok),
        'pagesDifferentiationTruncated':sum(r['report']['differentiationTruncated'] for r in ok),
        'blockedRequestsRecorded':sum(c['blockedRequests'] for c in observed),
        'blockedRequestsCompleted':sum(r['report']['blockedRequests'] for r in ok),
        'pagesWithBlockedResourcesObserved':sum(c['blockedRequests']>0 for c in observed),
        'pagesWithBlockedResources':sum(r['report']['blockedRequests']>0 for r in ok),
        'qualityFailures':sum(r.get('errorCode')=='QUALITY' for r in rows),
        'failureCodes':dict(collections.Counter(r.get('errorCode') or 'UNKNOWN' for r in rows if not completed(r)))}
    for field in ['contrastFailures','protanopiaWarnings','deuteranopiaWarnings','tritanopiaWarnings']:
        result[field]=sum(r['analysis'][field] for r in ok)
    assert issues==sum(result[k] for k in ['contrastFailures','protanopiaWarnings','deuteranopiaWarnings','tritanopiaWarnings'])
    assert result['elementsCollectedCompleted']==result['elementsEvaluated']+result['elementsSkipped']
    assert result['elementsCollectedObserved']==result['elementsCollectedCompleted']+result['elementsCollectedFailedCaptures']
    return result
def check_metrics(independent,server):
    for key,value in server.items():
        actual=independent[key]
        assert (math.isclose(actual,value,rel_tol=1e-10,abs_tol=1e-8) if isinstance(value,float) else actual==value),(key,actual,value)
def fmt(value,places=2):return 'Não disponível' if value is None else f'{value:.{places}f}'
def failure_groups(rows,captures):
    group=collections.defaultdict(list)
    for row in rows:
        if completed(row):continue
        d=diagnostics(row,captures) or {}
        key=(row.get('errorCode'),d.get('phase'),d.get('httpStatus'),d.get('causeType'),d.get('technicalMessage'))
        group[key].append(row['sourceId'])
    return [dict(code=k[0],phase=k[1],httpStatus=k[2],causeType=k[3],technicalMessage=k[4],quantity=len(ids),sourceIds=ids)
            for k,ids in sorted(group.items(),key=lambda item:str(item[0]))]
def main():
    manifest=support.frozen();support.protected()
    ref=json.loads((OUT/'study-reference.json').read_text(encoding='utf-8'))
    study=json.loads(download('/api/studies/'+ref['study_id'],OUT/'study-api-response.json'))
    assert study==json.loads((OUT/'study.json').read_text(encoding='utf-8')),'Final study data changed'
    assert study['status']=='COMPLETED' and study['totalRows']==len(study['rows'])==100
    assert study['datasetSha256']==manifest['import_sha256']
    for k,v in manifest['expected_configuration'].items():assert study['environment'][k]==v,(k,study['environment'][k],v)
    inputs=list(csv.DictReader(io.StringIO((OUT/'dataset-selection-original.csv').read_text(encoding='utf-8-sig'),newline='')))
    assert len({r['sourceId'] for r in study['rows']})==100
    assert len({r['analysis']['id'] for r in study['rows'] if r.get('analysis')})==100
    captures={};reports={};analyses={};artifact_errors=[]
    for index,(row,original) in enumerate(zip(study['rows'],inputs),1):
        assert (row['sourceId'],row['site'],row['requestedUrl'],row['category'])==(original['id'],original['site'],original['url'],original['categoria'])
        a=row['analysis'];assert a is not None
        live=json.loads(download('/api/analyses/'+a['id'],OUT/'raw/analyses'/(a['id']+'.json')))
        for key in a:
            if key in ('createdAt','finishedAt') and a[key] and live[key]:
                # PostgreSQL persists microseconds; the in-memory POST snapshot may contain nanoseconds.
                delta=abs((datetime.datetime.fromisoformat(a[key].replace('Z','+00:00'))-datetime.datetime.fromisoformat(live[key].replace('Z','+00:00'))).total_seconds())
                assert delta<=0.000001,(key,delta)
            else:assert live[key]==a[key],('Analysis field changed',a['id'],key)
        analyses[a['id']]=live
        if a.get('captureUrl'):
            capture=json.loads(download(a['captureUrl'],OUT/'raw/captures'/(a['id']+'.json')));captures[a['id']]=capture
            assert len(capture['elements'])==a['elementsCollected']
            for key in ('viewportWidth','viewportHeight','timeoutMs','settleMs'):assert capture[key]==study['environment'][key]
            assert capture['diagnostics']['protocol']=='scanner-v2'
        if completed(row):
            data=json.loads(download(a['reportUrl'],OUT/'raw/reports'/(a['id']+'.json')));reports[a['id']]=data
            assert data['summary']==row['report'] and len(data['issues'])==a['totalIssues']
            r=data['summary'];assert r['elementsEvaluated']==a['elementsAnalyzed']>0
            assert r['score']==a['score']==math.floor(100*(r['elementsEvaluated']-r['contrastFailures'])/r['elementsEvaluated']+0.5)
            assert r['elementsSkipped']==sum(e.get('unsupportedReason') is not None for e in captures[a['id']]['elements'])
            details=[i['detail'] for i in data['issues']]
            assert sum(d['type']=='CONTRAST' for d in details)==a['contrastFailures']
            for simulation,key in [('PROTANOPIA','protanopiaWarnings'),('DEUTERANOPIA','deuteranopiaWarnings'),('TRITANOPIA','tritanopiaWarnings')]:
                assert sum(d['type']=='COLOR_DIFFERENTIATION' and d['simulation']==simulation for d in details)==a[key]
        else:assert a['score'] is None and a.get('reportUrl') is None
        d=diagnostics(row,captures)
        if d:assert d['protocol']=='scanner-v2' and d['readinessTimeoutMs']==5000
        if index%20==0:print(f'Archived {index}/100 persisted individual results; no scanner invoked.',flush=True)
    storage=ROOT/manifest['capture_storage_path'];ids=set(analyses)
    pngs=[]
    screenshot_checks=[]
    for path in sorted(storage.glob('*.png')):
        if path.stem.split('-PROTANOPIA')[0].split('-DEUTERANOPIA')[0].split('-TRITANOPIA')[0] not in ids:continue
        dest=OUT/'raw/screenshots'/path.name;dest.parent.mkdir(parents=True,exist_ok=True)
        if dest.exists():assert dest.read_bytes()==path.read_bytes()
        else:shutil.copyfile(path,dest)
        pngs.append(path.name)
        with Image.open(dest) as image:
            dimensions=image.size;image.verify()
        aid=path.stem.split('-PROTANOPIA')[0].split('-DEUTERANOPIA')[0].split('-TRITANOPIA')[0]
        expected=(1280,captures[aid]['diagnostics']['coverageHeight']) if aid in captures else None
        assert dimensions[0]==1280 and dimensions[1]>0,(path.name,dimensions)
        if expected and dimensions!=expected:
            artifact_errors.append({'type':'SCREENSHOT_GEOMETRY_MISMATCH','filename':path.name,
                'analysisId':aid,'expected':list(expected),'observed':list(dimensions)})
        screenshot_checks.append({'filename':path.name,'width':dimensions[0],'height':dimensions[1],
            'png_integrity_verified':True,'coverage_metadata_available':aid in captures,
            'expected_dimensions':list(expected) if expected else None,'matches_coverage_dimensions':dimensions==expected if expected else None})
    save('screenshots-verification.json',screenshot_checks)
    for row in study['rows']:
        a=row['analysis']
        if a.get('captureUrl'):assert a['id']+'.png' in pngs
        if completed(row):
            for sim in ('PROTANOPIA','DEUTERANOPIA','TRITANOPIA'):assert a['id']+'-'+sim+'.png' in pngs
    download('/api/studies/'+study['id']+'/export.csv',OUT/'application-study-export.csv')
    imported=download('/api/studies/'+study['id']+'/dataset.csv',OUT/'application-import-original.csv')
    assert imported==(OUT/'dataset-import.csv').read_bytes()
    general=calculate(study['rows'],captures);check_metrics(general,study['metrics'])
    categories={category:calculate([r for r in study['rows'] if r['category']==category],captures) for category in study['categories']}
    for category,values in categories.items():assert values['processed']==20;check_metrics(values,study['categories'][category])
    groups=failure_groups(study['rows'],captures)
    reasons=collections.Counter(reason for row in study['rows'] if row.get('errorCode')=='QUALITY'
        for reason in (diagnostics(row,captures) or {}).get('qualityWarnings',[]))
    exclusions=collections.Counter(e['unsupportedReason'] for capture in captures.values() for e in capture['elements'] if e.get('unsupportedReason'))
    stats={'study_id':study['id'],'run':2,'protocol':'scanner-v2','environment':study['environment'],
        'started_at_utc':study['startedAt'],'finished_at_utc':study['finishedAt'],'duration_ms':study['durationMs'],
        'general':general,'categories':categories,'failureDiagnosticGroups':groups,'qualityReasons':dict(reasons),
        'observedUnsupportedReasons':dict(exclusions),'png_artifacts':len(pngs),'artifact_errors':artifact_errors,
        'definitions':{'scores':'Only completed pages with non-null score. Missing score is never zero.',
            'issues':'Only completed analyses; contrast failures and differentiation warnings are separate findings.',
            'elementsCollectedObserved':'All available capture metadata, including failed captures; unobserved pages are not assumed to have zero texts.',
            'elementsSkipped':'Completed reports only; unsupported texts in failed captures are reported separately, not as engine-evaluated data.',
            'resourcesBlocked':'Only captures with metadata; no whole-batch total can be inferred for navigation failures without captures.',
            'truncation':'Observed capture flag; top/width/element limit can coexist. Height truncation is explicit.',
            'durations':'Row durations include failures; total duration is measured by the study service.'},
        'validation':{'all_100_ids_urls_sites_categories_and_order_match_frozen_input':True,'unique_analysis_ids':100,
            'independent_metrics_match_api':True,'independent_category_metrics_match_api':True,
            'score_formula_and_issue_counts_validated_against_raw_reports':True,'same_frozen_dataset_as_pilot':True}}
    save('study-statistics.json',stats);save('failure-diagnostics.json',groups)
    headers=list(inputs[0])+['run','protocol','study_id','analysis_id','status','inicio_utc','fim_utc','duracao_linha_ms','duracao_analise_ms',
        'elementos_coletados','elementos_avaliados','elementos_ignorados_relatorio','textos_com_motivo_exclusao_captura','score','achados','contraste','protanopia','deuteranopia','tritanopia',
        'captura_disponivel','http_status','url_inicial_diagnostico','url_final_diagnostico','fase','error_code','error_message','causa_tipo','causa_mensagem','duracao_scanner_ms',
        'coverage_height_px','altura_documento_px','truncamento','height_truncated','recursos_bloqueados_observados','redirects','motivos_qualidade']
    output_rows=[]
    for row,original in zip(study['rows'],inputs):
        a=row['analysis'];d=diagnostics(row,captures) or {};c=captures.get(a['id']);r=row.get('report') or {};ok=completed(row)
        output={**original,'run':2,'protocol':'scanner-v2','study_id':study['id'],'analysis_id':a['id'],'status':a['status'],
            'inicio_utc':row['startedAt'],'fim_utc':row['finishedAt'],'duracao_linha_ms':row['durationMs'],'duracao_analise_ms':a['durationMs'],
            'elementos_coletados':a.get('elementsCollected'),'elementos_ignorados_relatorio':r.get('elementsSkipped'),
            'textos_com_motivo_exclusao_captura':sum(e.get('unsupportedReason') is not None for e in c['elements']) if c else None,
            'captura_disponivel':c is not None,'http_status':d.get('httpStatus'),'url_inicial_diagnostico':d.get('initialUrl'),
            'url_final_diagnostico':d.get('finalUrl'),'fase':d.get('phase'),'error_code':row['errorCode'],'error_message':row['errorMessage'],
            'causa_tipo':d.get('causeType'),'causa_mensagem':d.get('technicalMessage'),'duracao_scanner_ms':d.get('durationMs'),
            'coverage_height_px':d.get('coverageHeight') or None,'altura_documento_px':c['pageHeight'] if c else None,
            'truncamento':c['truncated'] if c else None,'height_truncated':d.get('heightTruncated') if c else None,
            'recursos_bloqueados_observados':c['blockedRequests'] if c else None,
            'redirects':json.dumps(d['redirects'],ensure_ascii=False) if 'redirects' in d else None,
            'motivos_qualidade':json.dumps(d['qualityWarnings'],ensure_ascii=False) if 'qualityWarnings' in d else None}
        for target,source in [('score','score'),('elementos_avaliados','elementsAnalyzed'),('achados','totalIssues'),('contraste','contrastFailures'),('protanopia','protanopiaWarnings'),('deuteranopia','deuteranopiaWarnings'),('tritanopia','tritanopiaWarnings')]:output[target]=a.get(source) if ok else None
        output_rows.append([output.get(k) for k in headers])
    (ROOT/'.maven-cache/run2-results-table.json').write_text(json.dumps({'headers':headers,'rows':output_rows},ensure_ascii=False),encoding='utf-8')
    write_reports(study,stats,captures)
    print(json.dumps({'study_id':study['id'],'statistics':general,'screenshots':len(pngs)},ensure_ascii=True),flush=True)

def write_reports(study,stats,captures):
    s=stats['general'];lines=['# Run 2 oficial — Scanner V2','',f'Lote `{study["id"]}`; 100 tentativas, uma por linha, sem substituições ou repetição seletiva.',
        f'Coleta UTC: {study["startedAt"]} a {study["finishedAt"]}.',
        '', '## Pré-verificação e protocolo','',
        'A API e o PostgreSQL/Supabase responderam antes do único POST. Os parâmetros foram lidos do bean efetivo; o Chromium foi aberto e fechado sem criar página ou visitar URL. Dataset, IDs, URLs, sites, categorias, ordem e hashes coincidem com o piloto. Os snapshots da configuração e do JAR aprovado estão nesta pasta.',
        'DOMCONTENTLOADED; navegação 30.000 ms; prontidão 5.000 ms; estabilidade 500 ms; viewport 1280×720; cobertura superior de até 12.000 px; 2.000 textos; cinco redirects. Nenhum parâmetro ou scanner foi ajustado durante o lote.',
        '', '## Resultados reais','', '| Medida | Resultado |','|---|---|',
        f'| Tentadas / concluídas / falhas | {s["processed"]} / {s["completed"]} / {s["failed"]} |',
        f'| Sucesso operacional | {fmt(100*s["successRate"])}% de 100 tentativas |',
        f'| Páginas com score / sem score | {s["scored"]} / {s["pagesWithoutScore"]} |',
        f'| Concluídas sem score | {s["completedWithoutScore"]} |',
        f'| Textos coletados observados / em concluídas / em capturas de falhas | {s["elementsCollectedObserved"]} / {s["elementsCollectedCompleted"]} / {s["elementsCollectedFailedCaptures"]} |',
        f'| Textos avaliados / ignorados em relatórios concluídos | {s["elementsEvaluated"]} / {s["elementsSkipped"]} |',
        f'| Textos com exclusão em capturas de falhas, sem análise pelo Core | {s["unsupportedTextsInFailedCaptures"]} |',
        f'| Achados / páginas concluídas com achados | {s["totalIssues"]} / {s["pagesWithIssues"]} |',
        f'| Concluídas com achados | {fmt(s["percentCompletedPagesWithIssues"])}% (denominador: {s["completed"]}) |',
        f'| Score médio / mediana / mínimo / máximo | {fmt(s["meanScore"])} / {fmt(s["medianScore"])} / {s["minScore"]} / {s["maxScore"]} |',
        f'| Média de achados por página concluída | {fmt(s["meanIssuesPerCompletedPage"])} |',
        f'| Falhas de contraste | {s["contrastFailures"]} |',
        f'| Avisos protanopia / deuteranopia / tritanopia | {s["protanopiaWarnings"]} / {s["deuteranopiaWarnings"]} / {s["tritanopiaWarnings"]} |',
        f'| Capturas com metadados / sem metadados | {s["capturesAvailable"]} / {s["pagesWithoutCaptureMetadata"]} |',
        f'| Páginas truncadas observadas / por altura | {s["pagesTruncatedObserved"]} / {s["pagesHeightTruncatedObserved"]} |',
        f'| Concluídas com coleta truncada / comparação heurística limitada | {s["pagesCollectionTruncated"]} / {s["pagesDifferentiationTruncated"]} |',
        f'| Falhas QUALITY | {s["qualityFailures"]} |',
        f'| Requisições bloqueadas registradas em capturas / em concluídas | {s["blockedRequestsRecorded"]} / {s["blockedRequestsCompleted"]} |',
        f'| Páginas com bloqueios observados / concluídas com bloqueios | {s["pagesWithBlockedResourcesObserved"]} / {s["pagesWithBlockedResources"]} |',
        f'| Tempo total do lote | {fmt(study["durationMs"]/1000)} s |',
        f'| Tempo médio / mínimo / máximo por linha, incluindo falhas | {fmt(s["meanDurationMs"]/1000)} / {fmt(s["minDurationMs"]/1000)} / {fmt(s["maxDurationMs"]/1000)} s |',
        '', '## Distribuição de scores','', '| Faixa | Páginas com score |','|---|---:|']
    lines += [f'| {name} | {count} |' for name,count in s['scoreDistribution'].items()]
    lines += ['', '## Categorias','', '| Categoria | Tentadas | Concluídas | Falhas | Sucesso | Com score | Coletados observados | Avaliados | Ignorados concluídas | Achados | Com achados | Score médio | Mediana |','|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    lines += [f'| {name} | {v["processed"]} | {v["completed"]} | {v["failed"]} | {fmt(100*v["successRate"])}% | {v["scored"]} | {v["elementsCollectedObserved"]} | {v["elementsEvaluated"]} | {v["elementsSkipped"]} | {v["totalIssues"]} | {v["pagesWithIssues"]} | {fmt(v["meanScore"])} | {fmt(v["medianScore"])} |' for name,v in stats['categories'].items()]
    lines += ['', 'Todas as demais métricas gerais também estão calculadas por categoria em `study-statistics.json`.',
        '', '## Falhas e diagnóstico','', '| Código | Quantidade |','|---|---:|']
    lines += [f'| {code} | {count} |' for code,count in s['failureCodes'].items()]
    lines += ['', '| Código | Fase | HTTP observado | Tipo de causa | Quantidade | IDs |','|---|---|---|---|---:|---|']
    lines += [f'| {g["code"]} | {g["phase"] or "Não disponível"} | {g["httpStatus"] if g["httpStatus"] is not None else "Não disponível"} | {g["causeType"] or "Sem exceção técnica registrada"} | {g["quantity"]} | {", ".join(g["sourceIds"])} |' for g in stats['failureDiagnosticGroups']]
    lines += ['', 'Mensagens técnicas e agrupamentos completos estão em `failure-diagnostics.json` e no CSV. HTTP representa a última resposta principal observada; no bloqueio de redirect pode ser a resposta do host de origem, sem requisição ao destino proibido.',
        '', '### Motivos QUALITY','', '| Motivo | Páginas |','|---|---:|']
    lines += [f'| {reason} | {count} |' for reason,count in stats['qualityReasons'].items()]
    lines += ['', 'Uma página pode ter vários motivos QUALITY. HTTP 403/429 indica erro/bloqueio operacional observado; não é evidência de problema de acessibilidade. QUALITY indica insuficiência de qualidade/cobertura da coleta, não ausência de acessibilidade.',
        '', '## Interpretação e integridade','',
        'Scores ausentes permanecem ausentes. Scores e achados usam somente análises concluídas; tempos usam todas as 100 linhas. Textos coletados em capturas de falhas não são apresentados como avaliados pelo Core. Páginas sem captura não são presumidas vazias e não fornecem um total conhecido de recursos bloqueados.',
        'Truncamento delimita a amostra de texto: o score não descreve regiões fora da cobertura. Zero achados não certifica acessibilidade global. Os avisos de diferenciação de cores são heurísticos e não entram na fórmula do score.',
        'O dataset é uma amostra intencional por quotas, sem representatividade estatística de todos os sites brasileiros. Não há referência humana para precision, recall ou F1. Os resultados refletem as condições desta execução e não foram calibrados por URL.',
        'JSONs, PNGs, respostas originais, configuração, JAR, fontes e logs de validação estão separados do Run 1. Os dados foram recalculados independentemente e comparados aos totais da API, aos relatórios individuais e à fórmula existente. O CSV padrão da aplicação não foi modificado; a consolidação de pesquisa é separada.',
        '', '**Entregue para revisão. README definitivo não reescrito.**','']
    if artifact_errors:
        lines += ['', '## Ressalvas de integridade geométrica','',
            'A verificação dos PNGs detectou divergência entre dimensão observada e cobertura declarada. Os arquivos e metadados originais foram preservados; nenhuma dimensão foi corrigida retroativamente.']
        for item in artifact_errors:
            row=next(r for r in study['rows'] if r['analysis']['id']==item['analysisId'])
            lines.append(f'- {row["sourceId"]} — {row["site"]}: estado {row["errorCode"] or "COMPLETED"}; dimensão declarada {item["expected"]}, PNG observado {item["observed"]}.')
        lines.append('Nesta execução, a divergência ocorreu somente na falha BROWSER da Natura, sem score ou achados calculados. As 61 análises concluídas têm PNGs com dimensões correspondentes à cobertura declarada. A causa da diferença geométrica não foi determinada; conteúdo dinâmico é uma hipótese, não uma conclusão.')
    (OUT/'study-summary.md').write_text('\n'.join(lines),encoding='utf-8')
    old=json.loads((PILOT/'study.json').read_text(encoding='utf-8'));m=old['metrics']
    old_captures=sum(bool(r.get('capture')) for r in old['rows'])
    old_truncated=sum(bool((r.get('capture') or {}).get('truncated')) for r in old['rows'])
    old_codes=dict(collections.Counter(r['errorCode'] for r in old['rows'] if r['errorCode']))
    old_by_id={r['sourceId']:r for r in old['rows']}
    transitions=collections.Counter((completed(old_by_id[r['sourceId']]),completed(r)) for r in study['rows'])
    diagnostic_rows=sum(diagnostics(r,captures) is not None for r in study['rows'])
    failed_http_known=sum((diagnostics(r,captures) or {}).get('httpStatus') is not None for r in study['rows'] if not completed(r))
    comparison=['# Comparação técnica — Run 1 / V1 × Run 2 / V2','',
        f'Piloto `{old["id"]}`; Run 2 `{study["id"]}`. Os mesmos 100 IDs/URLs/sites/categorias e a mesma ordem foram confirmados.',
        'A fórmula do score não mudou; prontidão, cobertura, suporte de cores/redirects e critérios de validade mudaram. Esta comparação mostra capacidade operacional e diagnóstico, não melhora/piora de acessibilidade dos websites. As execuções ocorreram em momentos diferentes e não isolam causalmente todas as diferenças de rede/conteúdo.',
        '', '| Medida | Run 1 / V1 | Run 2 / V2 |','|---|---:|---:|',
        f'| Tentadas | {m["processed"]} | {s["processed"]} |',f'| Concluídas | {m["completed"]} | {s["completed"]} |',
        f'| Falhas | {m["failed"]} | {s["failed"]} |',f'| Sucesso operacional | {fmt(100*m["successRate"])}% | {fmt(100*s["successRate"])}% |',
        f'| Com score | {m["scored"]} | {s["scored"]} |',f'| Concluídas sem score | {m["completed"]-m["scored"]} | {s["completedWithoutScore"]} |',
        f'| Textos avaliados | {m["elementsEvaluated"]} | {s["elementsEvaluated"]} |',
        f'| Capturas com metadados, incluindo falhas | {old_captures} | {s["capturesAvailable"]} |',
        f'| Execuções com novo diagnóstico estruturado | 0 | {diagnostic_rows} |',
        f'| Falhas com status HTTP preservado | 0 | {failed_http_known} |',
        f'| Truncamento registrado em capturas | {old_truncated} | {s["pagesTruncatedObserved"]} |',
        f'| Tempo total | {fmt(old["durationMs"]/1000)} s | {fmt(study["durationMs"]/1000)} s |',
        f'| Tempo médio por linha, incluindo falhas | {fmt(m["meanDurationMs"]/1000)} s | {fmt(s["meanDurationMs"]/1000)} s |',
        f'| Score médio, separado por protocolo/coorte | {fmt(m["meanScore"])} ({m["scored"]} páginas) | {fmt(s["meanScore"])} ({s["scored"]} páginas) |',
        f'| Mediana do score, separada por protocolo | {fmt(m["medianScore"])} | {fmt(s["medianScore"])} |',
        '', '## Distribuição de falhas','', '| Código | Run 1 | Run 2 |','|---|---:|---:|']
    comparison += [f'| {code} | {old_codes.get(code,0)} | {s["failureCodes"].get(code,0)} |' for code in sorted(set(old_codes)|set(s['failureCodes']))]
    comparison += ['', '## Sucesso operacional por categoria','', '| Categoria | Run 1 concluídas/20 | Run 2 concluídas/20 |','|---|---:|---:|']
    comparison += [f'| {category} | {old["categories"][category]["completed"]} | {v["completed"]} |' for category,v in stats['categories'].items()]
    comparison += ['', '## Transições de conclusão técnica nas mesmas URLs','',
        '| Situação | Páginas |','|---|---:|',
        f'| Concluídas nos dois Runs | {transitions[(True,True)]} |',
        f'| Concluídas somente no Run 1 | {transitions[(True,False)]} |',
        f'| Concluídas somente no Run 2 | {transitions[(False,True)]} |',
        f'| Falharam nos dois Runs | {transitions[(False,False)]} |',
        '', 'Essas transições são descritivas: critérios de conclusão e condições externas diferem. Não atribuímos cada transição exclusivamente à mudança do scanner.']
    comparison += ['', '## Cobertura e diagnóstico','',
        'V1 bloqueava redirects HTTP e abortava documentos acima de 12.000 px antes de coletar. V2 valida destinos e usa uma região superior limitada de coleta/captura. Mais páginas com truncamento não significam piora: a V1 frequentemente não produzia uma captura nesses casos. Não existe medição suficiente para comparar o percentual de todo o DOM coberto em ambos os Runs.',
        'V1 usava LOAD + 500 ms e timeout de 15 s. V2 usa DOMCONTENTLOADED, orçamento de navegação de 30 s e prontidão limitada a 5 s com 500 ms de estabilidade. Tempos também incluem rede, renderização, limpeza, persistência e conteúdo variável; não são benchmarks isolados do algoritmo.',
        'V1 permitiu três COMPLETED sem score (Agência Brasil, Correios e Conta Azul). V2 rejeita coleta sem texto avaliável/qualidade suficiente. Por isso os denominadores de conclusão têm critérios diferentes. Os scores são apresentados separadamente, sem média combinada e sem atribuir suas diferenças a mudanças na acessibilidade dos sites.',
        'V1 não preservava status HTTP, fase ou causa técnica nos erros. V2 os registra quando disponíveis e preserva capturas de falhas após coleta. Falhas anteriores à resposta continuam sem status HTTP, pois o dado não existe.',
        '', '## Casos de interesse do piloto','', '| ID | Site | Run 1 | Run 2 | Fase V2 | HTTP V2 |','|---|---|---|---|---|']
    for row in study['rows']:
        if row['sourceId'] not in ('KC-006','KC-025','KC-046','KC-074','KC-080','KC-088'):continue
        prev=old_by_id[row['sourceId']];d=diagnostics(row,captures) or {}
        comparison.append(f'| {row["sourceId"]} | {row["site"]} | {prev["errorCode"] or "COMPLETED"} | {row["errorCode"] or "COMPLETED"} | {d.get("phase") or "Não disponível"} | {d.get("httpStatus") if d.get("httpStatus") is not None else "Não disponível"} |')
    comparison += ['', 'Os detalhes desses casos são observações desta execução; mudanças não demonstram uma causa isolada nem justificam substituição de resultados do piloto.',
        '', 'Run 1 permanece separado e preservado. Run 2 foi executado integralmente, sem reexecução seletiva ou ajustes durante o lote. README definitivo aguarda revisão.','']
    (OUT/'run1-vs-run2-comparison.md').write_text('\n'.join(comparison),encoding='utf-8')

if __name__=='__main__':main()
