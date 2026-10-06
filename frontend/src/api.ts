import type { Analysis, HistoryPage, Report } from './types'

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) { super(message) }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try { response = await fetch(path, options) }
  catch (error) {
    if (options.signal?.aborted) throw error
    throw new ApiError('Não foi possível conectar à API. Verifique se o back-end está em execução.', 0)
  }
  if (!response.ok) {
    let detail: string | undefined
    try {
      const problem: { detail?: string; errors?: { message: string }[] } = await response.json()
      detail = problem.errors?.map(item => item.message).join(' ') || problem.detail
    } catch { /* Um proxy indisponível pode retornar texto em vez de JSON. */ }
    throw new ApiError(detail || (response.status === 404 ? 'Análise ou relatório não encontrado.' : 'A API não conseguiu atender à solicitação. Tente novamente mais tarde.'), response.status)
  }
  try { return await response.json() as T }
  catch { throw new ApiError('A API retornou uma resposta que não pôde ser lida.', response.status) }
}

export const createAnalysis = (url: string, signal: AbortSignal) => request<Analysis>('/api/analyses', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url }), signal,
})
export const getAnalysis = (id: string, signal: AbortSignal) => request<Analysis>(`/api/analyses/${encodeURIComponent(id)}`, { signal })
export const getReport = (id: string, signal: AbortSignal) => request<Report>(`/api/analyses/${encodeURIComponent(id)}/report`, { signal })
export const getHistory = (page: number, url: string, signal: AbortSignal) => {
  const query = new URLSearchParams({ page: String(page), size: '20' })
  if (url) query.set('url', url)
  return request<HistoryPage>(`/api/analyses${url ? '/by-url' : ''}?${query}`, { signal })
}
