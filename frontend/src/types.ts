export type Simulation = 'PROTANOPIA' | 'DEUTERANOPIA' | 'TRITANOPIA'
export interface Analysis {
  id: string; url: string; category: string | null; createdAt: string
  finishedAt: string | null; durationMs: number | null
  status: 'PENDING' | 'RUNNING' | 'SCANNED' | 'COMPLETED' | 'FAILED'
  score: number | null; elementsAnalyzed: number | null; totalIssues: number | null
  errorMessage: string | null; errorCode: string | null; elementsCollected: number | null
  screenshotUrl: string | null; captureUrl: string | null; reportUrl: string | null
  contrastFailures: number | null; protanopiaWarnings: number | null
  deuteranopiaWarnings: number | null; tritanopiaWarnings: number | null
}
export interface CollectedText {
  text: string; selector: string; textNodeIndex: number
  foreground: string | null; background: string | null; fontSizePx: number; fontWeight: number
  x: number; y: number; width: number; height: number; unsupportedReason: string | null
}
export interface HistoryPage {
  items: Analysis[]; page: number; size: number; totalItems: number; totalPages: number
}
export interface IssueDetail {
  type: 'CONTRAST' | 'COLOR_DIFFERENTIATION'; severity: 'HIGH' | 'MEDIUM' | 'WARNING'
  message: string; element: CollectedText; relatedElement: CollectedText | null
  simulation: Simulation | null; colorRole: string; firstColor: string | null; secondColor: string | null
  simulatedFirstColor: string | null; simulatedSecondColor: string | null
  contrastRatio: number | null; requiredRatio: number | null
  originalDistance: number | null; simulatedDistance: number | null
  suggestedForeground: string | null; suggestedContrast: number | null
}
export interface Report {
  analysisId: string
  summary: {
    engineVersion: string; scoreFormula: string; score: number | null
    elementsEvaluated: number; elementsSkipped: number; contrastFailures: number
    protanopiaWarnings: number; deuteranopiaWarnings: number; tritanopiaWarnings: number
    nearbyPairsCompared: number; differentiationTruncated: boolean
    collectionTruncated: boolean; blockedRequests: number
  }
  issues: { id: string; detail: IssueDetail }[]
  screenshots: Record<'ORIGINAL' | Simulation, string>
}
