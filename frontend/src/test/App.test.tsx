import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { App } from '../App'
import { analysis, report } from './fixtures'

function mount(path = '/') { return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>) }
function json(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } }) }
function mockCompleted() { const fetcher = vi.fn().mockResolvedValueOnce(json(analysis)).mockResolvedValueOnce(json(report)); vi.stubGlobal('fetch', fetcher); return fetcher }

describe('Fluxo de análise', () => {
  it('rejeita URL inválida sem chamar a API', async () => {
    const fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher); mount()
    await userEvent.type(screen.getByLabelText('Cole a URL que deseja analisar'), 'ftp://fixture.invalid')
    await userEvent.click(screen.getByRole('button', { name: /Analisar site/ }))
    expect(screen.getByRole('alert')).toHaveTextContent('Informe uma URL válida')
    expect(fetcher).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Cole a URL que deseja analisar')).toHaveFocus()
  })
  it('envia a URL uma vez, mantém estado de espera e apresenta o relatório retornado', async () => {
    let resolve!: (value: Response) => void
    const fetcher = vi.fn().mockImplementationOnce(() => new Promise<Response>(done => { resolve = done }))
      .mockResolvedValueOnce(json(analysis)).mockResolvedValueOnce(json(report))
    vi.stubGlobal('fetch', fetcher); mount()
    await userEvent.type(screen.getByLabelText('Cole a URL que deseja analisar'), analysis.url)
    await userEvent.click(screen.getByRole('button', { name: /Analisar site/ }))
    expect(screen.getByRole('button', { name: /Analisando/ })).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('nenhum resultado foi concluído')
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(fetcher).toHaveBeenCalledWith('/api/analyses', expect.objectContaining({ method: 'POST', body: JSON.stringify({ url: analysis.url }) }))
    resolve(json(analysis, 201))
    expect(await screen.findByRole('heading', { name: 'Um novo olhar para suas cores.' })).toBeVisible()
    expect(screen.getByText('50')).toBeVisible()
    expect(screen.getByText(/Duração: 2 s/)).toBeVisible()
    expect(screen.getByText(/1 textos sem avaliação confiável/)).toBeVisible()
  })
  it('mostra validação enviada pela API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ detail: 'URL bloqueada.' }, 400))); mount()
    await userEvent.type(screen.getByLabelText('Cole a URL que deseja analisar'), 'http://localhost')
    await userEvent.click(screen.getByRole('button', { name: /Analisar site/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('URL bloqueada.')
    expect(screen.getByRole('button', { name: /Analisar site/ })).toBeEnabled()
  })
  it('explica indisponibilidade da API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))); mount('/analyses/fixture-id')
    expect(await screen.findByRole('alert')).toHaveTextContent('Verifique se o back-end está em execução')
    expect(screen.getByRole('button', { name: 'Tentar carregar novamente' })).toBeVisible()
  })
  it.each([['TIMEOUT', 'A página excedeu o tempo de espera.'], ['INACCESSIBLE', 'A página não pôde ser acessada.']])('mostra falha persistida %s sem solicitar relatório', async (code, message) => {
    const fetcher = vi.fn().mockResolvedValue(json({ ...analysis, status: 'FAILED', score: null, reportUrl: null, errorCode: code, errorMessage: 'Falha de teste.' }))
    vi.stubGlobal('fetch', fetcher); mount('/analyses/fixture-id')
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByText(/Nenhum score foi produzido/)).toBeVisible()
    expect(fetcher).toHaveBeenCalledTimes(1)
  })
  it('seleciona ocorrência, mostra sugestão e troca a simulação', async () => {
    mockCompleted(); mount('/analyses/fixture-id')
    const issue = await screen.findByRole('button', { name: /Contraste insuficiente/ })
    await userEvent.click(issue)
    expect(issue).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('#767676')).toBeVisible()
    expect(screen.getByText(/4.54:1/)).toBeVisible()
    await userEvent.selectOptions(screen.getByLabelText('Simulação'), 'DEUTERANOPIA')
    expect(screen.getByRole('img', { name: 'Captura da página: Deuteranopia' })).toHaveAttribute('src', report.screenshots.DEUTERANOPIA)
    await userEvent.selectOptions(screen.getByLabelText('Filtrar ocorrências'), 'COLOR_DIFFERENTIATION')
    expect(screen.getByText('Nenhuma ocorrência neste filtro.')).toBeVisible()
  })
  it('informa erro de captura sem substituir a imagem por uma fixture', async () => {
    mockCompleted(); mount('/analyses/fixture-id')
    const image = await screen.findByRole('img', { name: 'Captura da página: Original' })
    fireEvent.error(image)
    expect(screen.getByRole('alert')).toHaveTextContent('A captura não pôde ser carregada')
    expect(screen.queryByRole('img', { name: 'Captura da página: Original' })).not.toBeInTheDocument()
  })
  it('evita declarar aprovação quando não há cobertura', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json(analysis)).mockResolvedValueOnce(json({ ...report, summary: { ...report.summary, score: null, elementsEvaluated: 0, elementsSkipped: 3, contrastFailures: 0, collectionTruncated: true }, issues: [] })))
    mount('/analyses/fixture-id')
    expect(await screen.findByText('Sem textos avaliáveis para calcular o score.')).toBeVisible()
    expect(screen.getByText(/Isso não garante a acessibilidade/)).toBeVisible()
    expect(screen.getByText(/Cobertura parcial/)).toBeVisible()
  })
  it('aborta a leitura ao sair da página', async () => {
    let signal: AbortSignal | undefined
    vi.stubGlobal('fetch', vi.fn().mockImplementation((_url, options: RequestInit) => { signal = options.signal as AbortSignal; return new Promise(() => {}) }))
    const view = mount('/analyses/fixture-id')
    await waitFor(() => expect(signal).toBeDefined())
    view.unmount()
    expect(signal?.aborted).toBe(true)
  })
  it('explica a metodologia e trata rotas desconhecidas', async () => {
    mount('/metodologia')
    expect(screen.getByRole('heading', { name: 'Como olhamos para as cores.' })).toBeVisible()
    expect(screen.getByText(/não é uma nota oficial WCAG/)).toBeVisible()
    await userEvent.click(screen.getByRole('link', { name: /Experimentar uma análise/ }))
    expect(screen.getByLabelText('Cole a URL que deseja analisar')).toBeVisible()
  })
  it('mostra página não encontrada', () => {
    mount('/inexistente')
    expect(screen.getByRole('heading', { name: 'Página não encontrada' })).toBeVisible()
  })
})
