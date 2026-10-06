import { Link, NavLink, Route, Routes, useLocation } from 'react-router'
import { useEffect, useRef } from 'react'
import { Home } from './pages/Home'
import { Result } from './pages/Result'
import { Methodology } from './pages/Methodology'
import { History } from './pages/History'

export function App() {
  const { pathname } = useLocation()
  const main = useRef<HTMLElement>(null)
  useEffect(() => { main.current?.focus(); window.scrollTo(0, 0) }, [pathname])
  return <>
    <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
    <header className="site-header shell">
      <Link className="brand" to="/" aria-label="KidoColors, início"><span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>KidoColors<span className="brand-dot">.</span></Link>
      <nav aria-label="Navegação principal"><NavLink to="/" end>Analisar</NavLink><NavLink to="/historico">Histórico</NavLink><NavLink to="/metodologia">Metodologia</NavLink></nav>
    </header>
    <main id="conteudo" className="shell" tabIndex={-1} ref={main}>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/analyses/:id" element={<Result />} />
        <Route path="/metodologia" element={<Methodology />} />
        <Route path="/historico" element={<History />} />
        <Route path="*" element={<section className="page-heading"><h1>Página não encontrada</h1><Link to="/">Voltar para uma nova análise</Link></section>} />
      </Routes>
    </main>
    <footer className="site-footer shell"><p>KidoColors · Um olhar mais atento às cores.</p><p>Ferramenta exploratória. <Link to="/metodologia">Conheça os critérios e limites.</Link></p></footer>
  </>
}
