package dev.kidocolors.backend.analysis;

import dev.kidocolors.backend.scanner.CollectedText;
import dev.kidocolors.backend.scanner.PageCapture;
import dev.kidocolors.core.Finding;
import dev.kidocolors.core.RgbColor;

public record IssueDetail(Finding.Type type, Finding.Severity severity, String message,
        CollectedText element, CollectedText relatedElement, String simulation, String colorRole,
        String firstColor, String secondColor, String simulatedFirstColor, String simulatedSecondColor,
        Double contrastRatio, Double requiredRatio, Double originalDistance, Double simulatedDistance,
        String suggestedForeground, Double suggestedContrast) {
    public static IssueDetail from(Finding finding, PageCapture capture) {
        return new IssueDetail(finding.type(), finding.severity(), finding.message(), capture.elements().get(finding.elementIndex()),
                finding.relatedElementIndex() == null ? null : capture.elements().get(finding.relatedElementIndex()),
                finding.simulation() == null ? null : finding.simulation().name(), finding.colorRole().name(),
                hex(finding.firstColor()), hex(finding.secondColor()), hex(finding.simulatedFirstColor()), hex(finding.simulatedSecondColor()),
                finding.contrastRatio(), finding.requiredRatio(), finding.originalDistance(), finding.simulatedDistance(),
                hex(finding.suggestedForeground()), finding.suggestedContrast());
    }
    private static String hex(RgbColor color) { return color == null ? null : color.toHex(); }
}
