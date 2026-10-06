import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getAnalysis, getReport } from '../api'
import { Comparison } from '../components/Comparison'
import { Issues } from '../components/Issues'
import type { Analysis, Report } from '../types'

type State = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; analysis: Analysis; report: Report | null }
const errors: Record<string, string> = {
  TIMEOUT: 'A página excedeu o tempo de espera.', INACCESSIBLE: 'A página não pôde ser acessada.',
  BLOCKED: 'O endereço ou um redirecionamento foi bloqueado por segurança.', LIMIT_EXCEEDED: 'A página excedeu os limites de captura.',
  STORAGE: 'Não foi possível armazenar ou ler as capturas.', BROWSER: 'O navegador de captura não pôde concluir a operação.',
}

export function Result() {
  const { id = '' } = useParams()
  return <ResultContent key={id} id={id} />
}

function ResultContent({ id }: { id: string }) {
  const [state, setState] = useState<State>({ status: 'loading' })
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const abort = new AbortController()
    const timer = window.setTimeout(() => abort.abort('timeout'), 30000)
    async function load() {
      try {
        const analysis = await getAnalysis(id, abort.signal)
        const report = analysis.status === 'COMPLETED' && analysis.reportUrl ? await getReport(id, abort.signal) : null
        if (!abort.signal.aborted) setState({ status: 'ready', analysis, report })
      } catch (failure) {
        if (abort.signal.reason === 'timeout') setState({ status: 'error', message: 'A API demorou para responder. Tente carregar o relatório novamente.' })
        else if (!abort.signal.aborted) setState({ status: 'error', message: failure instanceof Error ? failure.message : 'Não foi possível carregar a análise.' })
      } finally { window.clearTimeout(timer) }
    }
    void load()
    return () => { window.clearTimeout(timer); abort.abort() }
  }, [id, attempt])

  if (state.status === 'loading') return <section className="page-heading"><h1>Seu relatório</h1><p role="status">Carregando a análise…</p></section>
  if (state.status === 'error') return <section className="page-heading"><h1>Não foi possível carregar</h1><p className="notice error" role="alert">{state.message}</p><button className="button primary" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>Tentar carregar novamente</button> <Link to="/">Voltar para o início</Link></section>
  const { analysis, report } = state
  const selected = report?.issues.find(issue => issue.id === selectedId)?.detail
  return <>
    <section className="page-heading"><Link className="text-link" to="/">← Nova análise</Link><p className="eyebrow">Relatório da página</p><h1>Um novo olhar para suas cores.</h1><p className="report-url">{analysis.url}</p><p className="hint">{new Date(analysis.createdAt).toLocaleString('pt-BR')} · Duração: {analysis.durationMs === null ? 'indisponível' : `${(analysis.durationMs / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} s`} · ID: <code>{analysis.id}</code></p></section>
    {analysis.status === 'FAILED' ? <section className="panel"><h2>A análise não foi concluída</h2><p className="notice error" role="alert">{errors[analysis.errorCode ?? ''] ?? 'O processamento da página falhou.'}</p><p>{analysis.errorMessage}</p><p>Nenhum score foi produzido. Verifique o endereço final e a disponibilidade da página antes de iniciar outra análise.</p><Link className="button primary" to="/">Analisar outra página</Link></section> : !report ? <section className="panel"><h2>Relatório ainda indisponível</h2><p>Status registrado: <strong>{analysis.status}</strong>. A captura ou o processamento não gerou um relatório completo.</p><button className="button secondary" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>Atualizar status</button></section> : <>
      <section className="summary-grid" aria-label="Resumo da análise"><article className="score-panel"><p className="eyebrow">Score de contraste AA</p><p className="score">{report.summary.score ?? '—'}<span>/100</span></p><p>{report.summary.score === null ? 'Sem textos avaliáveis para calcular o score.' : 'Proporção dos textos avaliáveis que passaram no contraste AA.'}</p><Link to="/metodologia">Como calculamos →</Link></article><div className="metrics"><Metric label="Textos avaliados" value={report.summary.elementsEvaluated} /><Metric label="Textos não avaliados" value={report.summary.elementsSkipped} /><Metric label="Falhas de contraste" value={report.summary.contrastFailures} /><Metric label="Avisos · protanopia" value={report.summary.protanopiaWarnings} /><Metric label="Avisos · deuteranopia" value={report.summary.deuteranopiaWarnings} /><Metric label="Avisos · tritanopia" value={report.summary.tritanopiaWarnings} /></div></section>
      <div className="coverage"><p>Motor: <code>{report.summary.engineVersion}</code> · Pares próximos comparados: {report.summary.nearbyPairsCompared}.</p><p>Score próprio da ferramenta; os avisos de diferenciação não entram na nota.</p>
        {report.summary.elementsSkipped > 0 && <p className="notice">Há {report.summary.elementsSkipped} textos sem avaliação confiável, por limitações de coleta ou composição de cores.</p>}
        {(report.summary.collectionTruncated || report.summary.differentiationTruncated) && <p className="notice">Cobertura parcial: {report.summary.collectionTruncated && 'a coleta atingiu o limite de textos; '}{report.summary.differentiationTruncated && 'a comparação atingiu o limite de pares; '}revise a página manualmente.</p>}
        {report.summary.blockedRequests > 0 && <p className="notice">{report.summary.blockedRequests} requisições foram bloqueadas durante a captura. Isso pode alterar a aparência da página.</p>}
      </div>
      <Comparison screenshots={report.screenshots} selected={selected} />
      <Issues issues={report.issues} selectedId={selectedId} onSelect={value => setSelectedId(previous => previous === value ? null : value)} />
    </>}
  </>
}

function Metric({ label, value }: { label: string; value: number }) { return <article className="metric"><p>{value}</p><h2>{label}</h2></article> }
