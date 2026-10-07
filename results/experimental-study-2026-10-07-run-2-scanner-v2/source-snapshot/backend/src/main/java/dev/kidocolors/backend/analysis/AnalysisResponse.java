package dev.kidocolors.backend.analysis;

import java.time.Instant;
import java.util.UUID;

public record AnalysisResponse(UUID id, String url, String category, Instant createdAt,
        Instant finishedAt, Long durationMs, AnalysisStatus status, Integer score,
        Integer elementsAnalyzed, Integer totalIssues, String errorMessage, String errorCode,
        Integer elementsCollected, String screenshotUrl, String captureUrl, String reportUrl,
        Integer contrastFailures, Integer protanopiaWarnings, Integer deuteranopiaWarnings, Integer tritanopiaWarnings, String scannerDiagnosticsJson) {
    public static AnalysisResponse from(Analysis analysis) {
        return new AnalysisResponse(analysis.getId(), analysis.getUrl(), analysis.getCategory(),
                analysis.getCreatedAt(), analysis.getFinishedAt(), analysis.getDurationMs(),
                analysis.getStatus(), analysis.getScore(), analysis.getElementsAnalyzed(),
                analysis.getTotalIssues(), analysis.getErrorMessage(), analysis.getErrorCode(),
                analysis.getElementsCollected(), analysis.getCaptureJson() == null ? null
                        : "/api/analyses/" + analysis.getId() + "/screenshot",
                analysis.getCaptureJson() == null ? null : "/api/analyses/" + analysis.getId() + "/capture",
                analysis.getReportJson() == null ? null : "/api/analyses/" + analysis.getId() + "/report",
                analysis.getContrastFailures(), analysis.getProtanopiaWarnings(), analysis.getDeuteranopiaWarnings(), analysis.getTritanopiaWarnings(), analysis.getScannerDiagnosticsJson());
    }
}
