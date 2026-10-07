"""Preserve real API outputs and independently validate the study statistics."""
import collections, csv, hashlib, io, json, math, pathlib, shutil, statistics, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'results' / 'experimental-study-2026-10-07'
BASE = 'http://127.0.0.1:8080'

def download(route, destination):
    with urllib.request.urlopen(BASE + route, timeout=90) as response:
        raw = response.read()
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(raw)
    return raw

def completed(row):
    return row['errorCode'] is None and row['analysis'] is not None and row['analysis']['status'] == 'COMPLETED'

def calculate(rows):
    ok = [r for r in rows if completed(r)]
    scores = [r['analysis']['score'] for r in ok if r['analysis']['score'] is not None]
    issues = sum(r['analysis']['totalIssues'] for r in ok)
    durations = [r['durationMs'] for r in rows]
    problem_pages = sum(r['analysis']['totalIssues'] > 0 for r in ok)
    result = dict(processed=len(rows), completed=len(ok), failed=len(rows)-len(ok),
        successRate=len(ok)/len(rows) if rows else None, scored=len(scores),
        meanScore=statistics.mean(scores) if scores else None,
        medianScore=statistics.median(scores) if scores else None,
        minScore=min(scores) if scores else None, maxScore=max(scores) if scores else None,
        scoreDistribution={name: sum(low <= s <= high for s in scores) for name,low,high in [('0-24',0,24),('25-49',25,49),('50-74',50,74),('75-100',75,100)]},
        elementsEvaluated=sum(r['analysis']['elementsAnalyzed'] for r in ok),totalIssues=issues,
        meanIssuesPerCompletedPage=issues/len(ok) if ok else None,
        meanDurationMs=statistics.mean(durations) if durations else None,
        minDurationMs=min(durations) if durations else None,maxDurationMs=max(durations) if durations else None,
        pagesWithIssues=problem_pages,percentCompletedPagesWithIssues=100*problem_pages/len(ok) if ok else None,
        completedWithoutScore=len(ok)-len(scores),
        elementsSkipped=sum(r['report']['elementsSkipped'] for r in ok),
        pagesCollectionTruncated=sum(r['report']['collectionTruncated'] for r in ok),
        pagesDifferentiationTruncated=sum(r['report']['differentiationTruncated'] for r in ok),
        pagesWithBlockedResources=sum(r['report']['blockedRequests'] > 0 for r in ok),
        failureCodes=dict(collections.Counter(r['errorCode'] or 'UNKNOWN' for r in rows if not completed(r))))
    for field in ['contrastFailures','protanopiaWarnings','deuteranopiaWarnings','tritanopiaWarnings']:
        result[field] = sum(r['analysis'][field] for r in ok)
    assert result['totalIssues']==sum(result[k] for k in ['contrastFailures','protanopiaWarnings','deuteranopiaWarnings','tritanopiaWarnings'])
    return result

def check_metrics(independent, original):
    for key,value in original.items():
        actual=independent[key]
        if isinstance(value,float):
            assert math.isclose(actual,value,rel_tol=1e-10,abs_tol=1e-8),(key,actual,value)
        else:
            assert actual==value,(key,actual,value)

def main():
    manifest=json.loads((OUT/'manifest.json').read_text(encoding='utf-8'))
    cached=json.loads((OUT/'study.json').read_text(encoding='utf-8'))
    reference=json.loads((OUT/'study-reference.json').read_text(encoding='utf-8'))
    assert cached['id']==reference['study_id']
    study=json.loads(download('/api/studies/'+cached['id'],OUT/'study.json'))
    assert study['status']=='COMPLETED' and study['totalRows']==100 and len(study['rows'])==100
    assert study['datasetSha256']==manifest['import_sha256']
    selection=list(csv.DictReader(io.StringIO((OUT/'dataset-selection-original.csv').read_text(encoding='utf-8-sig'),newline='')))
    assert hashlib.sha256((ROOT/'datasets/websites.csv').read_bytes()).hexdigest()==manifest['selection_sha256']
    assert len({r['sourceId'] for r in study['rows']})==100
    by_id={r['id']:r for r in selection}
    for row in study['rows']:
        original=by_id[row['sourceId']]
        assert (row['site'],row['requestedUrl'],row['category'])==(original['site'],original['url'],original['categoria'])
        if completed(row):
            assert row['report']['engineVersion']==study['environment']['engineVersion']
        if row['capture']:
            for key in ['viewportWidth','viewportHeight','timeoutMs','settleMs']:
                assert row['capture'][key]==study['environment'][key]
    independent=calculate(study['rows']);check_metrics(independent,study['metrics'])
    categories={category:calculate([r for r in study['rows'] if r['category']==category]) for category in study['categories']}
    for category,stats in categories.items():
        assert stats['processed']==20;check_metrics(stats,study['categories'][category])
    raw_dir=OUT/'raw';raw_dir.mkdir(exist_ok=True)
    download('/api/studies/'+study['id']+'/export.csv',OUT/'application-study-export.csv')
    server_input=download('/api/studies/'+study['id']+'/dataset.csv',OUT/'application-import-original.csv')
    assert hashlib.sha256(server_input).hexdigest()==manifest['import_sha256']
    artifact_errors=[]
    for index,row in enumerate(study['rows'],1):
        a=row['analysis']
        if not a:continue
        for endpoint in ['','/report' if a.get('reportUrl') else None,'/capture' if a.get('captureUrl') else None]:
            if endpoint is None:continue
            name='analysis' if not endpoint else endpoint[1:]
            try:
                data=json.loads(download('/api/analyses/'+a['id']+endpoint,raw_dir/(a['id']+'-'+name+'.json')))
                if name=='report' and completed(row):
                    assert data['summary']==row['report']
                    assert len(data['issues'])==a['totalIssues']
                    details=[issue['detail'] for issue in data['issues']]
                    assert sum(d['type']=='CONTRAST' for d in details)==a['contrastFailures']
                    for simulation,key in [('PROTANOPIA','protanopiaWarnings'),('DEUTERANOPIA','deuteranopiaWarnings'),('TRITANOPIA','tritanopiaWarnings')]:
                        assert sum(d['type']=='COLOR_DIFFERENTIATION' and d['simulation']==simulation for d in details)==a[key]
                    report=data['summary']
                    assert report['elementsEvaluated']==a['elementsAnalyzed']
                    if report['elementsEvaluated']:
                        expected=math.floor(100*(report['elementsEvaluated']-report['contrastFailures'])/report['elementsEvaluated']+0.5)
                        assert expected==a['score'],('score formula',row['sourceId'],expected,a['score'])
            except AssertionError:
                raise
            except Exception as error:
                artifact_errors.append({'sourceId':row['sourceId'],'analysisId':a['id'],'artifact':name,'error':str(error)})
        for filename in (ROOT/'storage/captures').glob(a['id']+'*.png'):
            target=raw_dir/'captures'/filename.name;target.parent.mkdir(exist_ok=True)
            if not target.exists():shutil.copy2(filename,target)
        if index%10==0:print(f'Preserved persisted artifacts for {index}/100 dataset rows.',flush=True)
    summary={'study_id':study['id'],'started_at_utc':study['startedAt'],'finished_at_utc':study['finishedAt'],
        'duration_ms':study['durationMs'],'selection_sha256':manifest['selection_sha256'],
        'import_sha256':study['datasetSha256'],'code_revision':manifest['code_revision'],
        'environment':study['environment'],'overall':independent,'categories':categories,'artifact_download_errors':artifact_errors,
        'validation':'Independently recalculated metrics match persisted study metrics; source IDs/URLs/categories and original/import hashes match.'}
    (ROOT/'results/study-statistics.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    fields=list(selection[0])+['study_id','analysis_id','status','score','elementos_avaliados','problemas','contraste','protanopia','deuteranopia','tritanopia','inicio_utc','fim_utc','duracao_linha_ms','duracao_analise_ms','textos_ignorados','coleta_parcial','pares_limitados','recursos_bloqueados','url_final','error_code','error_message']
    with (ROOT/'results/study-results.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=fields);writer.writeheader()
        for row in study['rows']:
            a=row['analysis'] or {};r=row['report'] or {};c=row['capture'] or {};ok=completed(row)
            output={**by_id[row['sourceId']],'study_id':study['id'],'analysis_id':a.get('id'),'status':'COMPLETED' if ok else 'FAILED',
                'inicio_utc':row['startedAt'],'fim_utc':row['finishedAt'],'duracao_linha_ms':row['durationMs'],'duracao_analise_ms':a.get('durationMs'),
                'textos_ignorados':r.get('elementsSkipped'),'coleta_parcial':r.get('collectionTruncated'),'pares_limitados':r.get('differentiationTruncated'),
                'recursos_bloqueados':r.get('blockedRequests'),'url_final':c.get('finalUrl'),'error_code':row['errorCode'],'error_message':row['errorMessage']}
            for target,source in [('score','score'),('elementos_avaliados','elementsAnalyzed'),('problemas','totalIssues'),('contraste','contrastFailures'),('protanopia','protanopiaWarnings'),('deuteranopia','deuteranopiaWarnings'),('tritanopia','tritanopiaWarnings')]:output[target]=a.get(source) if ok else None
            writer.writerow(output)
    def fmt(value,places=2):return 'Não disponível' if value is None else f'{value:.{places}f}'
    s=independent
    lines=['# Estudo experimental real do KidoColors','',f'Lote: `{study["id"]}`. Estado persistido: `{study["status"]}`.',
        f'Coleta UTC: {study["startedAt"]} a {study["finishedAt"]}.',f'Revisão do código: `{manifest["code_revision"]}`.',
        '', '## Verificação antes da coleta','',
        'Passaram 27 testes do Core, 73 do backend e 24 do frontend, além de lint e build do frontend. Maven verify gerou o JAR usado. API e PostgreSQL/Supabase responderam UP antes do único POST do lote. Os logs estão no diretório preflight.',
        '', '## Resultados gerais','', '| Medida | Resultado |','|---|---|',
        '| URLs previstas e processadas | 100 / 100 |',f'| Concluídas / falhas | {s["completed"]} / {s["failed"]} |',
        f'| Taxa de sucesso operacional | {fmt(100*s["successRate"])}% (denominador: 100 tentadas) |',
        f'| Páginas com score / concluídas sem score | {s["scored"]} / {s["completedWithoutScore"]} |',
        f'| Elementos de texto avaliados | {s["elementsEvaluated"]} |',f'| Achados totais | {s["totalIssues"]} |',
        f'| Páginas concluídas com pelo menos um achado | {s["pagesWithIssues"]} / {s["completed"]} ({fmt(s["percentCompletedPagesWithIssues"])}%) |',
        f'| Score médio / mediana | {fmt(s["meanScore"])} / {fmt(s["medianScore"])} |',f'| Menor / maior score | {s["minScore"]} / {s["maxScore"]} |',
        f'| Média de achados por página concluída | {fmt(s["meanIssuesPerCompletedPage"])} |',
        f'| Falhas de contraste | {s["contrastFailures"]} |',f'| Avisos de protanopia / deuteranopia / tritanopia | {s["protanopiaWarnings"]} / {s["deuteranopiaWarnings"]} / {s["tritanopiaWarnings"]} |',
        f'| Tempo total do lote | {fmt(study["durationMs"]/1000)} s |',f'| Tempo médio / mínimo / máximo por linha | {fmt(s["meanDurationMs"]/1000)} / {fmt(s["minDurationMs"]/1000)} / {fmt(s["maxDurationMs"]/1000)} s |',
        '', '## Distribuição dos scores','', '| Faixa | Páginas |','|---|---|']
    lines += [f'| {bucket} | {count} |' for bucket,count in s['scoreDistribution'].items()]
    lines += ['', '## Resultados por categoria','', '| Categoria | Tentadas | Concluídas | Falhas | Sucesso | Com achados | Elementos | Achados | Score médio | Mediana |','|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|']
    lines += [f'| {category} | {stats["processed"]} | {stats["completed"]} | {stats["failed"]} | {fmt(100*stats["successRate"])}% | {stats["pagesWithIssues"]} | {stats["elementsEvaluated"]} | {stats["totalIssues"]} | {fmt(stats["meanScore"])} | {fmt(stats["medianScore"])} |' for category,stats in categories.items()]
    lines += ['', '## Falhas','', '| Código | Quantidade |','|---|---:|']+[f'| {code} | {count} |' for code,count in s['failureCodes'].items()]
    lines += ['', 'Os IDs, URLs, mensagens de erro e durações de todas as falhas estão em `study-results.csv` e no JSON bruto. Nenhuma URL foi substituída ou seletivamente repetida.',
        '', '## Cobertura e interpretação','',f'Textos ignorados: {s["elementsSkipped"]}. Páginas concluídas com coleta truncada: {s["pagesCollectionTruncated"]}; comparação heurística limitada: {s["pagesDifferentiationTruncated"]}; recursos bloqueados: {s["pagesWithBlockedResources"]}.',
        'COMPLETED indica conclusão técnica, não certificação de acessibilidade. Score nulo não foi convertido em zero. Estatísticas de score usam apenas páginas concluídas com score; achados usam páginas concluídas; tempos por linha incluem falhas.',
        'Agência Brasil (KC-046), Correios (KC-074) e Conta Azul (KC-080) terminaram COMPLETED com zero elementos avaliáveis e score nulo. Esses três casos não comprovam ausência de problemas. Não houve preenchimento dos valores ausentes.',
        'O score mede contraste AA de textos avaliáveis. Os avisos de diferenciação de cores são heurísticos e não entram na fórmula do score. Achados totais somam falhas de contraste e avisos; não equivalem a elementos únicos.',
        'Redirecionamentos HTTP são bloqueados pelo scanner existente. Recursos bloqueados, telas intermediárias, conteúdo dinâmico e páginas truncadas podem afetar a cobertura. Nenhuma medida disponível identifica automaticamente que a captura corresponde a todo o conteúdo principal.',
        'Esta amostra intencional por quotas não representa todos os websites brasileiros. Não foram calculadas precision, recall ou F1: não há rotulagem humana de referência. Tempos são os intervalos medidos pelo sistema, não estimativas.',
        '', '## Integridade e arquivos','',f'SHA-256 da seleção original de oito colunas: `{manifest["selection_sha256"]}`.',f'SHA-256 da projeção importada de quatro colunas: `{study["datasetSha256"]}`.',
        'O dataset original CSV/XLSX e a projeção importada estão preservados em `experimental-study-2026-10-07/`. A exportação original da API está em `application-study-export.csv`, sem mudança de contrato. `study-results.csv` é uma consolidação separada para pesquisa.',
        'Conferência do contrato existente: quatro colunas correspondem à entrada do importador e ao endpoint dataset.csv. O export.csv de resultados do lote já tem 33 colunas no código original; nenhuma coluna ou implementação dessa exportação foi alterada.',
        'O JSON persistido do lote, respostas individuais, relatórios, capturas e PNGs disponíveis estão preservados nesse diretório. `study-statistics.json` contém as métricas gerais e por categoria, ambiente e validações independentes.',
        f'Falhas no download de artefatos: {len(artifact_errors)}; detalhes preservados em `study-statistics.json`.',
        'As estatísticas foram recalculadas independentemente e comparadas às métricas persistidas. IDs/URLs/categorias e hashes de entrada foram conferidos. Testes e verificação técnica anteriores não integram o lote experimental.',
        '', '**Resultados entregues para revisão. O README definitivo não foi reescrito.**','']
    (ROOT/'results/study-summary.md').write_text('\n'.join(lines),encoding='utf-8')
    with (ROOT/'results/study-results.csv').open(encoding='utf-8-sig',newline='') as f:assert len(list(csv.DictReader(f)))==100
    artifact_manifest=[{'path':str(p.relative_to(OUT)).replace('\\','/'),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(OUT.rglob('*')) if p.is_file() and p.name!='raw-artifacts-manifest.json']
    (OUT/'raw-artifacts-manifest.json').write_text(json.dumps(artifact_manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'study_id':study['id'],'validated_metrics':independent,'artifact_errors':len(artifact_errors)},ensure_ascii=True),flush=True)

if __name__=='__main__':main()
