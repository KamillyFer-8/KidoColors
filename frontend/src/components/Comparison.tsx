import { useState } from 'react'
import type { CollectedText, IssueDetail, Report, Simulation } from '../types'

const labels: Record<Simulation, string> = { PROTANOPIA: 'Protanopia', DEUTERANOPIA: 'Deuteranopia', TRITANOPIA: 'Tritanopia (aproximação)' }

function Capture({ src, label, regions = [] }: { src: string; label: string; regions?: CollectedText[] }) {
  const [size, setSize] = useState<{ width: number; height: number } | null>(null)
  const [failed, setFailed] = useState(false)
  return <figure className="capture"><figcaption>{label}</figcaption>
    {failed ? <p className="notice error" role="alert">A captura não pôde ser carregada. Verifique a disponibilidade da API e dos arquivos de captura.</p> : <>
      {!size && <p role="status" className="hint">Carregando captura…</p>}
      <div className="capture-image"><img src={src} alt={`Captura da página: ${label}`} onLoad={event => setSize({ width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight })} onError={() => setFailed(true)} />
        {size && regions.map((region, index) => <span key={index} className="region" aria-hidden="true" style={{ left: `${100 * region.x / size.width}%`, top: `${100 * region.y / size.height}%`, width: `${100 * region.width / size.width}%`, height: `${100 * region.height / size.height}%` }} />)}
      </div><a href={src} target="_blank" rel="noreferrer">Abrir captura completa <span className="sr-only">({label}, nova aba)</span>↗</a>
    </>}
  </figure>
}

export function Comparison({ screenshots, selected }: { screenshots: Report['screenshots']; selected?: IssueDetail }) {
  const [simulation, setSimulation] = useState<Simulation>('PROTANOPIA')
  const regions = selected ? [selected.element, ...(selected.relatedElement ? [selected.relatedElement] : [])] : []
  return <section className="panel" aria-labelledby="compare-title">
    <div className="section-heading"><div><p className="eyebrow">02 / Perspectivas</p><h2 id="compare-title">Compare as cores</h2></div>
      <div><label htmlFor="simulation">Simulação</label><select id="simulation" value={simulation} onChange={event => setSimulation(event.target.value as Simulation)}>{Object.entries(labels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
    </div>
    <p className="hint">Imagens geradas a partir da mesma captura. São aproximações; a percepção varia entre pessoas. O contorno indica a ocorrência selecionada.</p>
    <div className="comparison-grid"><Capture key={screenshots.ORIGINAL} src={screenshots.ORIGINAL} label="Original" regions={regions} /><Capture key={screenshots[simulation]} src={screenshots[simulation]} label={labels[simulation]} regions={regions} /></div>
  </section>
}
