package dev.kidocolors.core;

import java.util.List;

public record AnalysisResult(String engineVersion, Integer score, int elementsEvaluated, int elementsSkipped,
        int contrastFailures, int protanopiaWarnings, int deuteranopiaWarnings, int tritanopiaWarnings,
        int nearbyPairsCompared, boolean differentiationTruncated, List<Finding> findings) {
    public AnalysisResult { findings = List.copyOf(findings); }
}
