package dev.kidocolors.backend.study;

import dev.kidocolors.backend.analysis.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudyMetricsTest {
    private StudyRow row(Integer score, boolean success, String category, long duration) {
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var analysis = success ? new AnalysisResponse(UUID.randomUUID(), "https://fixture.invalid", category, now, now, duration,
                AnalysisStatus.COMPLETED, score, 2, 3, null, null, 2, null, null, null, 1, 0, 1, 1, null) : null;
        return new StudyRow("fixture", "Fixture", "https://fixture.invalid", category, now, now, duration, analysis, null,
                success ? null : "TIMEOUT", success ? null : "Fixture timeout", null);
    }
    @Test void computesScoresDurationsCountsAndCategoryDenominators() {
        var rows = List.of(row(0, true, "A", 10), row(50, true, "A", 30), row(100, true, "B", 50), row(null, true, "B", 70), row(null, false, "B", 90));
        var result = StudyMetrics.calculate(rows);
        assertEquals(5, result.processed()); assertEquals(4, result.completed()); assertEquals(1, result.failed());
        assertEquals(0.8, result.successRate()); assertEquals(3, result.scored());
        assertEquals(50, result.meanScore()); assertEquals(50, result.medianScore());
        assertEquals(0, result.minScore()); assertEquals(100, result.maxScore());
        assertEquals(50, result.meanDurationMs()); assertEquals(10, result.minDurationMs()); assertEquals(90, result.maxDurationMs());
        assertEquals(8, result.elementsEvaluated()); assertEquals(12, result.totalIssues());
        assertEquals(3, result.meanIssuesPerCompletedPage()); assertEquals(1L, result.scoreDistribution().get("0-24"));
        var categories = StudyMetrics.byCategory(rows);
        assertEquals(25, categories.get("A").meanScore()); assertEquals(2, categories.get("B").completed());
    }
    @Test void keepsMissingStatisticsNullRatherThanZero() {
        var empty = StudyMetrics.calculate(List.of());
        assertNull(empty.successRate()); assertNull(empty.meanScore()); assertNull(empty.meanDurationMs());
        var failures = StudyMetrics.calculate(List.of(row(null, false, null, 10)));
        assertEquals(0, failures.successRate()); assertNull(failures.medianScore()); assertNull(failures.meanIssuesPerCompletedPage());
    }
    @Test void computesEvenMedianAndKeepsUncategorizedSeparateFromLabels() {
        var rows = List.of(row(0, true, null, 1), row(100, true, "(sem categoria)", 2));
        assertEquals(50, StudyMetrics.calculate(rows).medianScore());
        assertEquals(2, StudyMetrics.byCategory(rows).size()); assertTrue(StudyMetrics.byCategory(rows).containsKey(""));
    }
}
