import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { getHistory } from '../api'
import { Evolution } from '../components/Evolution'
import type { Analysis, HistoryPage } from '../types'

const statusLabels: Record<Analysis['status'], string> = { COMPLETED: 'Concluída', FAILED: 'Falhou', PENDING: 'Pendente', RUNNING: 'Em execução', SCANNED: 'Capturada' }
type State = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; data: HistoryPage }

export function History() {
  const [params, setParams] = useSearchParams()
  const url = params.get('url')?.trim() ?? ''
  const rawPage = params.get('page') ?? '0'
  const page = /^\d{1,6}$/.test(rawPage) ? Number(rawPage) : 0
  function navigate(nextUrl: string, nextPage = 0) {
    setParams(nextUrl ? { url: nextUrl, page: String(nextPage) } : { page: String(nextPage) })
  }
  return <HistoryContent key={`${url}:${page}`} url={url} page={page} navigate={navigate} />
}

function HistoryContent({ url, page, navigate }: { url: string; page: number; navigate: (url: string, page?: number) => void }) {
  const [draft, setDraft] = useState(url)
  const [validation, setValidation] = useState('')
  const [state, setState] = useState<State>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [selected, setSelected] = useState<string[]>([])
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    heading.current?.focus()
    const abort = new AbortController()
    const timer = window.setTimeout(() => abort.abort('timeout'), 30000)
    async function load() {
      try {
        const data = await getHistory(page, url, abort.signal)
        if (!abort.signal.aborted) setState({ status: 'ready', data })
      } catch (error) {
        if (abort.signal.reason === 'timeout') setState({ status: 'error', message: 'O histórico demorou para responder. Tente carregar novamente.' })
        else if (!abort.signal.aborted) setState({ status: 'error', message: error instanceof Error ? error.message : 'Não foi possível carregar o histórico.' })
      } finally { window.clearTimeout(timer) }
    }
    void load()
    return () => { window.clearTimeout(timer); abort.abort() }
  }, [page, url, attempt])

  function filter(event: FormEvent) {
    event.preventDefault()
    const value = draft.trim()
    if (value) {
      try {
        const parsed = new URL(value)
        if (!['http:', 'https:'].includes(parsed.protocol) || parsed.username || parsed.password || value.length > 2048) throw new Error()
      } catch { setValidation('Use uma URL completa com http:// ou https://, sem usuário e senha.'); return }
    }
    setValidation('')
    navigate(value)
  }
  const data = state.status === 'ready' ? state.data : null
  const scored = data?.items.filter(item => item.status === 'COMPLETED' && item.score !== null) ?? []
  const average = scored.length ? scored.reduce((sum, item) => sum + item.score!, 0) / scored.length : null
  const completed = data?.items.filter(item => item.status === 'COMPLETED').length ?? 0
  const failed = data?.items.filter(item => item.status === 'FAILED').length ?? 0
  const analyses = data?.items.filter(item => selected.includes(item.id)) ?? []

  return <>
    <section className="page-heading"><p className="eyebrow">Revisitar e acompanhar</p><h1 ref={heading} tabIndex={-1}>O caminho das suas análises.</h1><p>Consulte os relatórios salvos ou filtre uma URL para comparar duas capturas anteriores.</p></section>
    <section className="panel" aria-label="Busca no histórico"><form onSubmit={filter} noValidate><label htmlFor="history-url">Filtrar pela URL exata</label><div className="input-row"><input id="history-url" type="url" value={draft} onChange={event => setDraft(event.target.value)} placeholder="https://seu-site.com" maxLength={2048} aria-invalid={!!validation} aria-describedby={validation ? 'history-help history-error' : 'history-help'} /><button className="button primary" type="submit">Buscar histórico</button>{url && <button className="button secondary" type="button" onClick={() => navigate('')}>Limpar filtro</button>}</div><p id="history-help" className="hint">A API normaliza o endereço. Caminhos e parâmetros diferentes correspondem a páginas diferentes.</p>{validation && <p id="history-error" className="notice error" role="alert">{validation}</p>}</form></section>
    {state.status === 'loading' && <p className="notice" role="status">Carregando histórico…</p>}
    {state.status === 'error' && <section className="panel"><p className="notice error" role="alert">{state.message}</p><button className="button secondary" onClick={() => { setState({ status: 'loading' }); setAttempt(value => value + 1) }}>Tentar carregar novamente</button></section>}
    {data && <section className="panel" aria-labelledby="history-title"><div className="section-heading"><div><p className="eyebrow">Análises salvas</p><h2 id="history-title">{url ? 'Histórico desta URL' : 'Todas as análises'}</h2></div><p>{data.totalItems} registros{url ? ' para esta URL' : ' no histórico'}</p></div>
      {!data.items.length ? <div className="notice">{data.totalItems > 0 ? <><p>Não há registros nesta página do histórico.</p><button className="button secondary" onClick={() => navigate(url)}>Ir para a primeira página</button></> : <><p>{url ? 'Nenhuma análise salva para esta URL.' : 'Seu histórico ainda está vazio.'}</p><Link to="/">Iniciar uma análise →</Link></>}</div> : <>
        <div className="history-metrics" aria-label="Métricas desta página"><p><strong>{completed}</strong> concluídas · <strong>{failed}</strong> falhas · <strong>{data.items.length - completed - failed}</strong> outros status</p><p>Score médio nesta página: <strong>{average === null ? 'indisponível' : average.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}</strong> ({scored.length} análises com score). Estes números não agregam todo o histórico nem corrigem diferenças entre versões do motor.</p></div>
        {url && <p className="hint">Selecione duas análises concluídas com score, nesta página, para comparar. Falhas e registros sem cobertura ficam fora da comparação.</p>}
        <div className="table-scroll" role="region" aria-label="Tabela de análises, role horizontalmente se necessário" tabIndex={0}><table className="history-table"><caption className="sr-only">Análises em ordem da mais recente para a mais antiga</caption><thead><tr>{url && <th scope="col">Comparar</th>}<th scope="col">URL</th><th scope="col">Data</th><th scope="col">Status</th><th scope="col">Score</th><th scope="col">Problemas</th><th scope="col">Tempo</th><th scope="col">Ações</th></tr></thead><tbody>{data.items.map(item => <tr key={item.id}>
          {url && <td><input type="checkbox" aria-label={`Comparar análise ${item.id}`} checked={selected.includes(item.id)} disabled={item.status !== 'COMPLETED' || item.score === null || !item.reportUrl || (selected.length === 2 && !selected.includes(item.id))} onChange={event => setSelected(previous => event.target.checked ? [...previous, item.id] : previous.filter(value => value !== item.id))} /></td>}
          <td className="table-url">{item.url}</td><td><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString('pt-BR')}</time></td><td>{statusLabels[item.status]}</td><td>{item.score ?? '—'}</td><td>{item.totalIssues ?? '—'}</td><td>{item.durationMs === null ? '—' : `${(item.durationMs / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} s`}</td><td><Link to={`/analyses/${item.id}`} aria-label={`Abrir análise ${item.id}`}>Abrir relatório</Link>{!url && <button className="link-button" onClick={() => navigate(item.url)} aria-label={`Ver evolução de ${item.url}`}>Ver evolução</button>}</td>
        </tr>)}</tbody></table></div>
        <nav className="pagination" aria-label="Paginação do histórico"><button className="button secondary" disabled={data.page === 0} onClick={() => navigate(url, data.page - 1)}>Anterior</button><span>Página {data.page + 1} de {data.totalPages}</span><button className="button secondary" disabled={data.page + 1 >= data.totalPages} onClick={() => navigate(url, data.page + 1)}>Próxima</button></nav>
      </>}
    </section>}
    {analyses.length === 2 && <Evolution key={[...selected].sort().join(':')} analyses={analyses} />}
  </>
}
