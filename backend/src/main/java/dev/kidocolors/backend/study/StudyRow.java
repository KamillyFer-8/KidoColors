package dev.kidocolors.backend.study;

import dev.kidocolors.backend.analysis.AnalysisResponse;
import dev.kidocolors.backend.analysis.ReportSummary;
import java.time.Instant;

public record StudyRow(String sourceId, String site, String requestedUrl, String category,
        Instant startedAt, Instant finishedAt, long durationMs, AnalysisResponse analysis,
        ReportSummary report, String errorCode, String errorMessage, CaptureMetadata capture) {
    public boolean completed() { return errorCode == null && analysis != null && analysis.status() == dev.kidocolors.backend.analysis.AnalysisStatus.COMPLETED; }
    public record CaptureMetadata(String finalUrl, String browserVersion, int viewportWidth, int viewportHeight,
            int pageHeight, int timeoutMs, int settleMs, int blockedRequests, boolean truncated) {
        static CaptureMetadata from(dev.kidocolors.backend.scanner.PageCapture capture) {
            return new CaptureMetadata(capture.finalUrl(), capture.browserVersion(), capture.viewportWidth(), capture.viewportHeight(),
                    capture.pageHeight(), capture.timeoutMs(), capture.settleMs(), capture.blockedRequests(), capture.truncated());
        }
    }
}
