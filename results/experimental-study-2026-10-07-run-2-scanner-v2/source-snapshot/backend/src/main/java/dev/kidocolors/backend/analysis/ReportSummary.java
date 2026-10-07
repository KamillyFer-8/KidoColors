package dev.kidocolors.backend.analysis;

import dev.kidocolors.core.AnalysisResult;
import dev.kidocolors.backend.scanner.PageCapture;

public record ReportSummary(String engineVersion, String scoreFormula, Integer score, int elementsEvaluated,
        int elementsSkipped, int contrastFailures, int protanopiaWarnings, int deuteranopiaWarnings,
        int tritanopiaWarnings, int nearbyPairsCompared, boolean differentiationTruncated,
        boolean collectionTruncated, int blockedRequests) {
    static ReportSummary from(AnalysisResult result, PageCapture capture) {
        return new ReportSummary(result.engineVersion(), "round(100 * textos com contraste AA / textos avaliáveis)",
                result.score(), result.elementsEvaluated(), result.elementsSkipped(), result.contrastFailures(),
                result.protanopiaWarnings(), result.deuteranopiaWarnings(), result.tritanopiaWarnings(),
                result.nearbyPairsCompared(), result.differentiationTruncated(), capture.truncated(), capture.blockedRequests());
    }
}
