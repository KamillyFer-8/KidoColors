import { useState } from 'react'
import type { IssueDetail, Report } from '../types'

function Color({ value, label }: { value: string | null; label: string }) {
  return <span className="color-value">{value && /^#[0-9a-f]{6}$/i.test(value) && <span className="swatch" style={{ backgroundColor: value }} aria-hidden="true" />}{label}: <code>{value ?? 'Indisponível'}</code></span>
}

function Details({ detail }: { detail: IssueDetail }) {
  const roles: Record<string, string> = { TEXT_BACKGROUND: 'texto e fundo', FOREGROUND: 'cores de texto', BACKGROUND: 'cores de fundo' }
  return <div className="issue-detail">
    <p>{detail.message}</p><blockquote>{detail.element.text}</blockquote>
    {detail.relatedElement && <><p>Texto relacionado:</p><blockquote>{detail.relatedElement.text}</blockquote></>}
    <p className="hint">Elemento: <code>{detail.element.selector}</code> · Posição: {Math.round(detail.element.x)}, {Math.round(detail.element.y)} px · {detail.element.fontSizePx}px / peso {detail.element.fontWeight}</p>
    <div className="color-pair"><Color value={detail.firstColor} label="Cor 1" /><Color value={detail.secondColor} label="Cor 2" /></div>
    {detail.type === 'CONTRAST' ? <>
      <p>Contraste medido: <strong>{detail.contrastRatio?.toFixed(2)}:1</strong> · mínimo AA: <strong>{detail.requiredRatio}:1</strong>.</p>
      {detail.suggestedForeground ? <div className="suggestion"><h3>Sugestão para a cor do texto</h3><Color value={detail.suggestedForeground} label="Texto sugerido" /><p>Contraste estimado: {detail.suggestedContrast?.toFixed(2)}:1, mantendo o fundo. Revise no contexto do seu design antes de aplicar.</p></div> : <p>Não há sugestão automática disponível para esta ocorrência.</p>}
    </> : <>
      <p>Simulação: {detail.simulation?.toLowerCase()} · comparação de {roles[detail.colorRole] ?? detail.colorRole}.</p>
      <div className="color-pair"><Color value={detail.simulatedFirstColor} label="Cor 1 simulada" /><Color value={detail.simulatedSecondColor} label="Cor 2 simulada" /></div>
      <p>Distância RGB normalizada: {detail.originalDistance?.toFixed(3)} → {detail.simulatedDistance?.toFixed(3)} após simulação.</p>
      <p className="suggestion">Revise as cores e acrescente rótulos, ícones ou padrões quando a cor comunicar uma informação. Este aviso é heurístico e exige revisão humana.</p>
    </>}
  </div>
}

export function Issues({ issues, selectedId, onSelect }: { issues: Report['issues']; selectedId: string | null; onSelect: (id: string) => void }) {
  const [filter, setFilter] = useState('ALL')
  const [limit, setLimit] = useState(20)
  const filtered = issues.filter(issue => filter === 'ALL' || issue.detail.type === filter)
  return <section className="panel" aria-labelledby="issues-title"><div className="section-heading"><div><p className="eyebrow">03 / Revisar</p><h2 id="issues-title">Ocorrências <span className="count">{issues.length}</span></h2></div><div><label htmlFor="filter">Filtrar ocorrências</label><select id="filter" value={filter} onChange={event => { setFilter(event.target.value); setLimit(20) }}><option value="ALL">Todas</option><option value="CONTRAST">Contraste</option><option value="COLOR_DIFFERENTIATION">Diferenciação de cores</option></select></div></div>
    {!issues.length ? <p className="notice">Nenhuma ocorrência encontrada nos textos e pares avaliados. Isso não garante a acessibilidade de toda a página.</p> : !filtered.length ? <p>Nenhuma ocorrência neste filtro.</p> : <>
      <p className="hint">Selecione uma ocorrência para ver os detalhes e destacar sua posição nas capturas.</p>
      <ul className="issue-list">{filtered.slice(0, limit).map((issue, index) => <li key={issue.id}>
        <button className="issue-button" aria-expanded={selectedId === issue.id} aria-controls={`issue-${issue.id}`} onClick={() => onSelect(issue.id)}><span><span className="issue-number">{index + 1}.</span> {issue.detail.type === 'CONTRAST' ? 'Contraste insuficiente' : 'Possível perda de diferenciação'} <span className="issue-preview">{issue.detail.element.text.slice(0, 100)}</span></span><span className="badge">{issue.detail.severity === 'HIGH' ? 'Alta' : issue.detail.severity === 'MEDIUM' ? 'Média' : 'Aviso'}</span></button>
        <div id={`issue-${issue.id}`} hidden={selectedId !== issue.id}>{selectedId === issue.id && <Details detail={issue.detail} />}</div>
      </li>)}</ul>
      {filtered.length > limit && <button className="button secondary" onClick={() => setLimit(value => value + 20)}>Mostrar mais 20 ocorrências</button>}
      <p className="hint">Exibindo {Math.min(limit, filtered.length)} de {filtered.length} ocorrências neste filtro.</p>
    </>}
  </section>
}
