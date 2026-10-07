package dev.kidocolors.backend.analysis;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AnalysisReportResponse(UUID analysisId, ReportSummary summary, List<IssueResponse> issues,
                                    Map<String, String> screenshots) {
    public record IssueResponse(UUID id, IssueDetail detail) { }
}
