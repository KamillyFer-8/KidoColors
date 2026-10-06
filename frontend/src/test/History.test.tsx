import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { App } from '../App'
import { analysis, report } from './fixtures'
import type { Analysis, HistoryPage } from '../types'

const older: Analysis = { ...analysis, id: 'older', createdAt: '2026-01-01T12:00:00Z' }
const newer: Analysis = { ...analysis, id: 'newer', createdAt: '2026-01-02T12:00:00Z', score: 75 }
const failed: Analysis = { ...analysis, id: 'failed', status: 'FAILED', score: null, totalIssues: null, reportUrl: null }
const unscored: Analysis = { ...analysis, id: 'unscored', score: null }
const filteredPath = `/historico?url=${encodeURIComponent(analysis.url)}`
function json(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status }) }
function page(items: Analysis[], overrides: Partial<HistoryPage> = {}): HistoryPage { return { items, page: 0, size: 20, totalItems: items.length, totalPages: items.length ? 1 : 0, ...overrides } }
function mount(path = '/historico') { return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>) }

describe('Histórico e evolução', () => {
  it('mostra histórico vazio com caminho para nova análise', async () => {
    const fetcher = vi.fn().mockResolvedValue(json(page([]))); vi.stubGlobal('fetch', fetcher); mount()
    expect(await screen.findByText('Seu histórico ainda está vazio.')).toBeVisible()
    expect(screen.getByRole('link', { name: /Iniciar uma análise/ })).toHaveAttribute('href', '/')
    expect(fetcher).toHaveBeenCalledWith('/api/analyses?page=0&size=20', expect.objectContaining({ signal: expect.any(AbortSignal) }))
  })
  it('lista registros, links, falhas e média apenas dos scores válidos da página', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(page([newer, older, failed, unscored])))); mount()
    expect(await screen.findByText('4 registros no histórico')).toBeVisible()
    expect(screen.getByText('62,5')).toBeVisible()
    expect(screen.getByText('Falhou')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Abrir análise older' })).toHaveAttribute('href', '/analyses/older')
    expect(screen.getByText(/Estes números não agregam todo o histórico/)).toBeVisible()
  })
  it('troca página e preserva os parâmetros da URL filtrada', async () => {
    const url = 'https://fixture.invalid/path?a=1&b=2'
    const fetcher = vi.fn().mockResolvedValueOnce(json(page([older], { totalItems: 21, totalPages: 2 }))).mockResolvedValueOnce(json(page([newer], { page: 1, totalItems: 21, totalPages: 2 })))
    vi.stubGlobal('fetch', fetcher); mount(`/historico?url=${encodeURIComponent(url)}`)
    expect(await screen.findByText('Página 1 de 2')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }))
    expect(await screen.findByText('Página 2 de 2')).toBeVisible()
    const called = new URL(fetcher.mock.calls[1][0], 'http://localhost')
    expect(called.pathname).toBe('/api/analyses/by-url'); expect(called.searchParams.get('url')).toBe(url); expect(called.searchParams.get('page')).toBe('1')
    expect(screen.getByRole('button', { name: 'Próxima' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeEnabled()
  })
  it('valida o filtro e distingue uma busca sem registros do histórico vazio', async () => {
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(json(page([])))); vi.stubGlobal('fetch', fetcher); mount()
    await screen.findByText('Seu histórico ainda está vazio.')
    const input = screen.getByLabelText('Filtrar pela URL exata')
    await userEvent.type(input, 'invalida')
    await userEvent.click(screen.getByRole('button', { name: 'Buscar histórico' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Use uma URL completa')
    expect(fetcher).toHaveBeenCalledTimes(1)
    await userEvent.clear(input); await userEvent.type(input, analysis.url)
    await userEvent.click(screen.getByRole('button', { name: 'Buscar histórico' }))
    expect(await screen.findByText('Nenhuma análise salva para esta URL.')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Limpar filtro' }))
    expect(await screen.findByText('Seu histórico ainda está vazio.')).toBeVisible()
  })
  it('mostra erro de rede sem declarar histórico vazio e permite recarregar', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce(json(page([])))); mount()
    expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível conectar')
    expect(screen.queryByText('Seu histórico ainda está vazio.')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Tentar carregar novamente' }))
    expect(await screen.findByText('Seu histórico ainda está vazio.')).toBeVisible()
  })
  it('oferece primeira página quando o endereço aponta para uma página sem registros', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(page([], { page: 9, totalItems: 1, totalPages: 1 })))); mount('/historico?page=9')
    expect(await screen.findByText('Não há registros nesta página do histórico.')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Ir para a primeira página' })).toBeVisible()
  })
  it('compara em ordem cronológica, bloqueia falhas e scores nulos e limita a seleção', async () => {
    const third = { ...analysis, id: 'third' }
    const fetcher = vi.fn().mockResolvedValueOnce(json(page([newer, older, failed, unscored, third])))
      .mockResolvedValueOnce(json({ ...report, analysisId: older.id }))
      .mockResolvedValueOnce(json({ ...report, analysisId: newer.id, summary: { ...report.summary, score: 75, collectionTruncated: true } }))
    vi.stubGlobal('fetch', fetcher); mount(filteredPath)
    expect(await screen.findByRole('checkbox', { name: 'Comparar análise failed' })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: 'Comparar análise unscored' })).toBeDisabled()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Comparar análise newer' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Comparar análise older' }))
    expect(await screen.findByText('+25 pontos')).toBeVisible()
    expect(screen.getByRole('checkbox', { name: 'Comparar análise third' })).toBeDisabled()
    expect(fetcher.mock.calls.slice(1).map(call => call[0])).toEqual(['/api/analyses/older/report', '/api/analyses/newer/report'])
    expect(screen.getByText(/Uma das capturas tem coleta parcial/)).toBeVisible()
  })
  it('não calcula variação entre versões de motor diferentes', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json(page([newer, older])))
      .mockResolvedValueOnce(json(report)).mockResolvedValueOnce(json({ ...report, summary: { ...report.summary, engineVersion: 'outro-motor' } })))
    mount(filteredPath)
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Comparar análise newer' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Comparar análise older' }))
    expect(await screen.findByText(/Não calculamos a variação/)).toBeVisible()
    expect(screen.queryByText(/Variação do score:/)).not.toBeInTheDocument()
  })
  it('aborta a leitura do histórico ao sair da página', async () => {
    let signal: AbortSignal | undefined
    vi.stubGlobal('fetch', vi.fn().mockImplementation((_path, options: RequestInit) => { signal = options.signal as AbortSignal; return new Promise(() => {}) }))
    const view = mount(); await waitFor(() => expect(signal).toBeDefined()); view.unmount(); expect(signal?.aborted).toBe(true)
  })
  it('mantém score zero como resultado válido e não calcula direção com datas iguais', async () => {
    const zero = { ...older, score: 0 }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json(page([zero, { ...newer, createdAt: older.createdAt }])))
      .mockResolvedValueOnce(json({ ...report, summary: { ...report.summary, score: 0 } })).mockResolvedValueOnce(json(report)))
    mount(filteredPath)
    const checkbox = await screen.findByRole('checkbox', { name: 'Comparar análise older' })
    expect(checkbox).toBeEnabled()
    await userEvent.click(checkbox); await userEvent.click(screen.getByRole('checkbox', { name: 'Comparar análise newer' }))
    expect(await screen.findByText(/Não calculamos a variação/)).toBeVisible()
  })
  it('mostra erro se um relatório da comparação falhar', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json(page([older, newer])))
      .mockResolvedValueOnce(json({ detail: 'Relatório indisponível.' }, 404)).mockResolvedValueOnce(json(report)))
    mount(filteredPath)
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Comparar análise older' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Comparar análise newer' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Relatório indisponível.')
    expect(screen.queryByText(/Variação do score:/)).not.toBeInTheDocument()
  })
  it('ordena datas com frações de segundo pela hora real', async () => {
    const fractional = { ...newer, createdAt: '2026-01-01T12:00:00.123Z' }
    const fetcher = vi.fn().mockResolvedValueOnce(json(page([fractional, older])))
      .mockResolvedValueOnce(json(report)).mockResolvedValueOnce(json({ ...report, summary: { ...report.summary, score: 75 } }))
    vi.stubGlobal('fetch', fetcher); mount(filteredPath)
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Comparar análise newer' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Comparar análise older' }))
    expect(await screen.findByText('+25 pontos')).toBeVisible()
    expect(fetcher.mock.calls[1][0]).toBe('/api/analyses/older/report')
  })
})
