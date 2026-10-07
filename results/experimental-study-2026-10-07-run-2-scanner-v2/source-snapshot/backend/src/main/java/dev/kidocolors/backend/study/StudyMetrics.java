package dev.kidocolors.backend.study;

import java.util.*;

public record StudyMetrics(int processed, int completed, int failed, Double successRate,
        int scored, Double meanScore, Double medianScore, Integer minScore, Integer maxScore,
        Map<String, Long> scoreDistribution, long elementsEvaluated, long totalIssues,
        long contrastFailures, long protanopiaWarnings, long deuteranopiaWarnings, long tritanopiaWarnings,
        Double meanIssuesPerCompletedPage, Double meanDurationMs, Long minDurationMs, Long maxDurationMs) {
    public static StudyMetrics calculate(List<StudyRow> rows) {
        var completed = rows.stream().filter(StudyRow::completed).toList();
        var scores = completed.stream().map(row -> row.analysis().score()).filter(Objects::nonNull).sorted().toList();
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (String bucket : List.of("0-24", "25-49", "50-74", "75-100")) distribution.put(bucket, 0L);
        for (int score : scores) {
            String bucket = score < 25 ? "0-24" : score < 50 ? "25-49" : score < 75 ? "50-74" : "75-100";
            distribution.compute(bucket, (key, value) -> value + 1);
        }
        int n = scores.size();
        Double median = n == 0 ? null : (scores.get((n - 1) / 2).doubleValue() + scores.get(n / 2)) / 2;
        long elements = 0, issues = 0, contrast = 0, protan = 0, deutan = 0, tritan = 0;
        for (var row : completed) {
            var a = row.analysis(); elements += number(a.elementsAnalyzed()); issues += number(a.totalIssues());
            contrast += number(a.contrastFailures()); protan += number(a.protanopiaWarnings());
            deutan += number(a.deuteranopiaWarnings()); tritan += number(a.tritanopiaWarnings());
        }
        return new StudyMetrics(rows.size(), completed.size(), rows.size() - completed.size(),
                rows.isEmpty() ? null : completed.size() / (double) rows.size(), n,
                n == 0 ? null : scores.stream().mapToInt(Integer::intValue).average().orElseThrow(), median,
                n == 0 ? null : scores.getFirst(), n == 0 ? null : scores.getLast(), distribution,
                elements, issues, contrast, protan, deutan, tritan,
                completed.isEmpty() ? null : issues / (double) completed.size(),
                rows.isEmpty() ? null : rows.stream().mapToLong(StudyRow::durationMs).average().orElseThrow(),
                rows.isEmpty() ? null : rows.stream().mapToLong(StudyRow::durationMs).min().orElseThrow(),
                rows.isEmpty() ? null : rows.stream().mapToLong(StudyRow::durationMs).max().orElseThrow());
    }
    private static long number(Integer value) { return value == null ? 0 : value; }
    public static Map<String, StudyMetrics> byCategory(List<StudyRow> rows) {
        Map<String, List<StudyRow>> groups = new TreeMap<>();
        for (var row : rows) groups.computeIfAbsent(row.category() == null ? "" : row.category(), key -> new ArrayList<>()).add(row);
        Map<String, StudyMetrics> result = new LinkedHashMap<>(); groups.forEach((category, group) -> result.put(category, calculate(group)));
        return result;
    }
}
