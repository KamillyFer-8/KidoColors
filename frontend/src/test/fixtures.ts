// Dados sintéticos usados somente por testes. Não são resultados de websites reais.
import type { Analysis, Report } from '../types'

export const analysis: Analysis = {
  id: 'fixture-id', url: 'https://fixture.invalid', category: null,
  createdAt: '2026-01-01T12:00:00Z', finishedAt: '2026-01-01T12:00:02Z', durationMs: 2000,
  status: 'COMPLETED', score: 50, elementsAnalyzed: 2, totalIssues: 1,
  errorMessage: null, errorCode: null, elementsCollected: 3,
  screenshotUrl: '/api/analyses/fixture-id/screenshot', captureUrl: '/api/analyses/fixture-id/capture', reportUrl: '/api/analyses/fixture-id/report',
  contrastFailures: 1, protanopiaWarnings: 0, deuteranopiaWarnings: 0, tritanopiaWarnings: 0,
}
export const report: Report = {
  analysisId: analysis.id,
  summary: { engineVersion: 'fixture-engine', scoreFormula: 'fixture-formula', score: 50, elementsEvaluated: 2, elementsSkipped: 1,
    contrastFailures: 1, protanopiaWarnings: 0, deuteranopiaWarnings: 0, tritanopiaWarnings: 0,
    nearbyPairsCompared: 1, differentiationTruncated: false, collectionTruncated: false, blockedRequests: 0 },
  screenshots: { ORIGINAL: '/api/analyses/fixture-id/screenshot', PROTANOPIA: '/api/analyses/fixture-id/screenshot?simulation=PROTANOPIA', DEUTERANOPIA: '/api/analyses/fixture-id/screenshot?simulation=DEUTERANOPIA', TRITANOPIA: '/api/analyses/fixture-id/screenshot?simulation=TRITANOPIA' },
  issues: [{ id: 'issue-fixture', detail: { type: 'CONTRAST', severity: 'MEDIUM', message: 'Contraste abaixo do mínimo.',
    element: { text: 'Texto de teste', selector: 'p', textNodeIndex: 0, foreground: '#999999', background: '#FFFFFF', fontSizePx: 16, fontWeight: 400, x: 20, y: 30, width: 100, height: 20, unsupportedReason: null },
    relatedElement: null, simulation: null, colorRole: 'TEXT_BACKGROUND', firstColor: '#999999', secondColor: '#FFFFFF', simulatedFirstColor: null, simulatedSecondColor: null,
    contrastRatio: 2.85, requiredRatio: 4.5, originalDistance: null, simulatedDistance: null, suggestedForeground: '#767676', suggestedContrast: 4.54 } }],
}
