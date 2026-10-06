import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getReport } from '../api'
import type { Analysis, Report } from '../types'

type ComparisonState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; reports: Report[] }

export function Evolution({ analyses }: { analyses: Analysis[] }) {
  const [state, setState] = useState<ComparisonState>({ status: 'loading' })
  const ordered = [...analyses].sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id.localeCompare(b.id))
  const [older, newer] = ordered
  const olderId = older.id
  const newerId = newer.id
  useEffect(() => {
    const abort = new AbortController()
    const timer = window.setTimeout(() => abort.abort('timeout'), 30000)
    async function load() {
      try {
        const reports = await Promise.all([getReport(olderId, abort.signal), getReport(newerId, abort.signal)])
        if (!abort.signal.aborted) setState({ status: 'ready', reports })
      } catch (error) {
        if (abort.signal.reason === 'timeout') setState({ status: 'error', message: 'Os relatórios demoraram para responder. Selecione as análises novamente para tentar outra vez.' })
        else if (!abort.signal.aborted) setState({ status: 'error', message: error instanceof Error ? error.message : 'Não foi possível comparar os relatórios.' })
      } finally { window.clearTimeout(timer) }
    }
    void load()
    return () => { window.clearTimeout(timer); abort.abort() }
  }, [olderId, newerId])
  const summaries = state.status === 'ready' ? state.reports.map(item => item.summary) : []
  const comparable = summaries.length === 2 && summaries[0].engineVersion === summaries[1].engineVersion && summaries.every(item => item.score !== null) && older.url === newer.url && Date.parse(newer.createdAt) > Date.parse(older.createdAt)
  const delta = comparable ? summaries[1].score! - summaries[0].score! : null
  return <section className="panel" aria-labelledby="evolution-title"><p className="eyebrow">Dois momentos da mesma página</p><h2 id="evolution-title">Evolução do contraste</h2>
    {state.status === 'loading' && <p role="status">Carregando os relatórios selecionados…</p>}
    {state.status === 'error' && <p className="notice error" role="alert">{state.message}</p>}
    {state.status === 'ready' && <>
      {delta === null ? <p className="notice">Não calculamos a variação: URLs ou versões do motor diferentes, datas iguais ou score indisponível. Consulte os relatórios separadamente.</p> : <p className="notice">Variação do score: <strong>{delta > 0 ? '+' : ''}{delta} pontos</strong> (mais recente menos anterior). Essa diferença não comprova uma mudança de acessibilidade.</p>}
      <div className="table-scroll" role="region" aria-label="Comparação dos relatórios" tabIndex={0}><table className="history-table"><thead><tr><th scope="col">Métrica</th><th scope="col">Anterior</th><th scope="col">Mais recente</th></tr></thead><tbody>
        <tr><th scope="row">Data</th>{ordered.map(item => <td key={item.id}>{new Date(item.createdAt).toLocaleString('pt-BR')}</td>)}</tr>
        <tr><th scope="row">Versão do motor</th>{summaries.map((item, index) => <td key={index}><code>{item.engineVersion}</code></td>)}</tr>
        <tr><th scope="row">Score AA</th>{summaries.map((item, index) => <td key={index}>{item.score ?? '—'}</td>)}</tr>
        <tr><th scope="row">Textos avaliados</th>{summaries.map((item, index) => <td key={index}>{item.elementsEvaluated}</td>)}</tr>
        <tr><th scope="row">Textos ignorados</th>{summaries.map((item, index) => <td key={index}>{item.elementsSkipped}</td>)}</tr>
        <tr><th scope="row">Falhas de contraste</th>{summaries.map((item, index) => <td key={index}>{item.contrastFailures}</td>)}</tr>
        <tr><th scope="row">Avisos protanopia / deuteranopia / tritanopia</th>{summaries.map((item, index) => <td key={index}>{item.protanopiaWarnings} / {item.deuteranopiaWarnings} / {item.tritanopiaWarnings}</td>)}</tr>
        <tr><th scope="row">Duração</th>{ordered.map(item => <td key={item.id}>{item.durationMs === null ? '—' : `${(item.durationMs / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} s`}</td>)}</tr>
        <tr><th scope="row">Relatório completo</th>{ordered.map(item => <td key={item.id}><Link to={`/analyses/${item.id}`}>Abrir {item.id === older.id ? 'anterior' : 'mais recente'}</Link></td>)}</tr>
      </tbody></table></div>
      <p className="hint">A cobertura e o conteúdo podem mudar entre capturas, mesmo com a mesma versão do motor. Considere textos avaliados, ignorados e as condições de coleta antes de interpretar a variação.</p>
      {summaries.some(item => item.collectionTruncated || item.differentiationTruncated || item.blockedRequests > 0) && <p className="notice">Uma das capturas tem coleta parcial, comparação limitada ou recursos bloqueados. Revise as limitações no relatório completo.</p>}
    </>}
  </section>
}
