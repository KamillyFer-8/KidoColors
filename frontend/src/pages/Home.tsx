import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { createAnalysis } from '../api'

export function Home() {
  const [url, setUrl] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const controller = useRef<AbortController | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()
  useEffect(() => () => controller.current?.abort(), [])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (controller.current) return
    setError('')
    const value = url.trim()
    try {
      const parsed = new URL(value)
      if (!['http:', 'https:'].includes(parsed.protocol) || !parsed.hostname || parsed.username || parsed.password || value.length > 2048) throw new Error()
    } catch {
      setError('Informe uma URL válida com http:// ou https://, sem usuário e senha.')
      input.current?.focus()
      return
    }
    const abort = new AbortController()
    controller.current = abort
    setLoading(true)
    const timer = window.setTimeout(() => abort.abort('timeout'), 180000)
    try {
      const analysis = await createAnalysis(value, abort.signal)
      if (!abort.signal.aborted) navigate(`/analyses/${analysis.id}`)
    } catch (failure) {
      if (abort.signal.reason === 'timeout') setError('A API demorou mais de 3 minutos. A análise pode ter sido salva; não reenviamos o pedido automaticamente.')
      else if (!abort.signal.aborted) setError(failure instanceof Error ? failure.message : 'Não foi possível iniciar a análise.')
    } finally {
      window.clearTimeout(timer)
      controller.current = null
      if (!abort.signal.aborted || abort.signal.reason === 'timeout') setLoading(false)
    }
  }

  return <>
    <section className="hero">
      <div><p className="eyebrow"><span aria-hidden="true">●</span> Acessibilidade começa com um olhar</p>
        <h1>Cores que fazem<br /><span>sentido para todos.</span></h1>
        <p className="hero-description">Explore o contraste de uma página e veja como suas cores mudam em simulações de visão de cores.</p>
        <a className="text-link" href="#analisar">Comece por uma URL <span aria-hidden="true">↘</span></a>
      </div>
      <div className="color-art" aria-hidden="true"><div className="art-caption">um mundo de perspectivas</div><div className="orb orb-one" /><div className="orb orb-two" /><div className="orb orb-three" /><span className="art-label">COR + CONTRASTE + PERCEPÇÃO</span></div>
    </section>
    <section id="analisar" className="analysis-form" aria-labelledby="form-title">
      <div><p className="eyebrow">01 / Explorar</p><h2 id="form-title">Qual página vamos olhar?</h2><p>Use o endereço final de uma página pública, sem login.</p></div>
      <form onSubmit={submit} noValidate aria-busy={loading}>
        <label htmlFor="url">Cole a URL que deseja analisar</label>
        <div className="input-row"><input ref={input} id="url" name="url" type="url" inputMode="url" autoComplete="url" placeholder="https://seu-site.com" value={url} onChange={event => setUrl(event.target.value)} maxLength={2048} disabled={loading} aria-invalid={!!error} aria-describedby={error ? 'url-help url-error' : 'url-help'} required /><button className="button primary" disabled={loading} type="submit">{loading ? 'Analisando…' : 'Analisar site'}<span aria-hidden="true"> ↗</span></button></div>
        <p id="url-help" className="hint">A captura usa Chromium em 1280 × 720 px. A duração depende da página e do processamento.</p>
        {error && <p id="url-error" className="notice error" role="alert">{error}</p>}
        {loading && <p className="notice" role="status">Estamos coletando os textos, avaliando contraste e gerando as simulações. Aguarde; nenhum resultado foi concluído ainda.</p>}
      </form>
    </section>
    <section className="features" aria-label="O que a análise oferece">
      <article><span className="step-number">01</span><h2>Contraste, com critério</h2><p>Verifique textos avaliáveis usando os limiares de contraste WCAG AA.</p></article>
      <article><span className="step-number">02</span><h2>Outras perspectivas</h2><p>Compare a captura original com três simulações de visão de cores.</p></article>
      <article><span className="step-number">03</span><h2>Um próximo passo</h2><p>Encontre ocorrências e sugestões de cor para revisar a página.</p></article>
    </section>
    <p className="method-note">O resultado é uma análise exploratória, sem certificação de acessibilidade. <Link to="/metodologia">Entenda como funciona →</Link></p>
  </>
}
