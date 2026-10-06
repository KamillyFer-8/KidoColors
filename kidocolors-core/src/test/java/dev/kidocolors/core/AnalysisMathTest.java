package dev.kidocolors.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisMathTest {
    private final RgbColor black = RgbColor.fromHex("#000000"), white = RgbColor.fromHex("#FFFFFF");

    // Expected linear primary outputs come from the published severity-1 matrix columns,
    // independently encoded to 8-bit sRGB. Tests catch use of encoded RGB instead of linear RGB.
    @ParameterizedTest
    @CsvSource({"PROTANOPIA,#FF0000,#6D5F00", "DEUTERANOPIA,#FF0000,#A39000", "TRITANOPIA,#FF0000,#FF000F",
            "PROTANOPIA,#00FF00,#FFE500", "DEUTERANOPIA,#0000FF,#003DFB", "TRITANOPIA,#0000FF,#006B96"})
    void matchesReferencePrimaryVectors(Simulation simulation, String input, String expected) {
        assertEquals(expected, simulation.apply(RgbColor.fromHex(input)).toHex());
    }

    @Test void preservesAllNeutralColorsAndAlpha() {
        for (Simulation simulation : Simulation.values()) {
            for (int value = 0; value < 256; value++) {
                RgbColor gray = new RgbColor(value, value, value);
                assertEquals(gray, simulation.apply(gray));
            }
            assertEquals(0x80000000, simulation.applyPixel(0x80FF0000) & 0xff000000);
            assertThrows(NullPointerException.class, () -> simulation.apply(null));
        }
    }

    @Test void distanceAndSimilarityHaveKnownBoundsAndAreSymmetric() {
        assertEquals(0, ColorDistance.between(black, black));
        assertEquals(1, ColorDistance.between(black, white));
        assertFalse(ColorDistance.potentiallyConfused(ColorDistance.between(black, white), ColorDistance.between(black, white)));
        assertEquals(1 / Math.sqrt(3), ColorDistance.between(black, RgbColor.fromHex("#FF0000")), 1e-12);
        assertEquals(ColorDistance.between(black, white), ColorDistance.between(white, black));
        assertEquals(1, ColorDistance.similarity(black, black));
        assertEquals(0, ColorDistance.similarity(black, white), 1e-12);
    }

    @Test void heuristicHasExplicitThresholds() {
        assertTrue(ColorDistance.potentiallyConfused(.20, .08));
        assertFalse(ColorDistance.potentiallyConfused(.19, .01));
        assertFalse(ColorDistance.potentiallyConfused(.25, .081));
        assertFalse(ColorDistance.potentiallyConfused(0, 0));
        assertThrows(IllegalArgumentException.class, () -> ColorDistance.potentiallyConfused(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> ColorDistance.potentiallyConfused(.5, 1.1));
    }

    @Test void suggestsSmallestValidGrayChangeAndKeepsPassingColor() {
        var suggestion = ContrastSuggestion.suggest(RgbColor.fromHex("#777777"), white, 4.5).orElseThrow();
        assertEquals("#767676", suggestion.foreground().toHex());
        assertTrue(suggestion.ratio() >= 4.5);
        assertEquals(Contrast.ratio(suggestion.foreground(), white), suggestion.ratio());
        assertEquals(black, ContrastSuggestion.suggest(black, white, 4.5).orElseThrow().foreground());
        assertTrue(ContrastSuggestion.suggest(black, RgbColor.fromHex("#777777"), 21).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ContrastSuggestion.suggest(black, white, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ContrastSuggestion.suggest(black, white, 22));
    }

    @Test void suggestionsForBothDirectionsReachThreshold() {
        for (String background : List.of("#FFFFFF", "#000000", "#777777", "#B72D38")) {
            RgbColor color = RgbColor.fromHex(background);
            var result = ContrastSuggestion.suggest(color, color, 4.5).orElseThrow();
            assertTrue(Contrast.ratio(result.foreground(), color) >= 4.5);
        }
    }

    @Test void scoreIsOwnPercentageWithNoResultForEmptyCoverage() {
        assertNull(AccessibilityScore.calculate(0, 0));
        assertEquals(100, AccessibilityScore.calculate(4, 0));
        assertEquals(0, AccessibilityScore.calculate(4, 4));
        assertEquals(67, AccessibilityScore.calculate(3, 1));
        assertEquals(100, AccessibilityScore.calculate(Integer.MAX_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> AccessibilityScore.calculate(0, 1));
        assertThrows(IllegalArgumentException.class, () -> AccessibilityScore.calculate(-1, 0));
    }

    @Test void evaluatesContrastAndExcludesUnsupportedSamplesFromScore() {
        List<TextSample> samples = List.of(sample("#000000", "#FFFFFF", 0), sample("#777777", "#FFFFFF", 20),
                new TextSample(null, null, 16, 400, new TextSample.Bounds(0, 40, 10, 10), "GRADIENT"));
        AnalysisResult result = new ColorAnalyzer().analyze(samples);
        assertEquals(50, result.score());
        assertEquals(2, result.elementsEvaluated());
        assertEquals(1, result.elementsSkipped());
        assertEquals(1, result.contrastFailures());
        assertEquals(1, result.findings().size());
        Finding finding = result.findings().getFirst();
        assertEquals(1, finding.elementIndex());
        assertEquals(Finding.Type.CONTRAST, finding.type());
        assertEquals(4.5, finding.requiredRatio());
        assertTrue(finding.suggestedContrast() >= finding.requiredRatio());
        assertEquals(result, new ColorAnalyzer().analyze(samples));
    }

    @Test void reportsNearbyColorPairsOncePerSimulationAndRole() {
        AnalysisResult result = new ColorAnalyzer().analyze(List.of(sample("#FF0000", "#FFFFFF", 0),
                sample("#006000", "#FFFFFF", 10), sample("#FF0000", "#FFFFFF", 20)));
        assertEquals(1, result.protanopiaWarnings());
        Finding warning = result.findings().stream().filter(finding -> finding.simulation() == Simulation.PROTANOPIA).findFirst().orElseThrow();
        assertEquals(Finding.Type.COLOR_DIFFERENTIATION, warning.type());
        assertEquals(Finding.Severity.WARNING, warning.severity());
        assertEquals(0, warning.elementIndex());
        assertEquals(1, warning.relatedElementIndex());
        assertNull(warning.requiredRatio());
        assertNull(warning.suggestedForeground());
        assertEquals(0, new ColorAnalyzer().analyze(List.of(sample("#FF0000", "#FFFFFF", 0),
                sample("#006000", "#FFFFFF", 500))).protanopiaWarnings());
    }

    @Test void recordsHeuristicBudgetWithoutReducingContrastCoverage() {
        List<TextSample> samples = new ArrayList<>();
        for (int index = 0; index < 210; index++) samples.add(sample("#000000", "#FFFFFF", 0));
        AnalysisResult result = new ColorAnalyzer().analyze(samples);
        assertTrue(result.differentiationTruncated());
        assertEquals(20_000, result.nearbyPairsCompared());
        assertEquals(210, result.elementsEvaluated());
        assertEquals(100, result.score());
        assertNull(new ColorAnalyzer().analyze(List.of()).score());
    }

    private TextSample sample(String foreground, String background, double x) {
        return new TextSample(RgbColor.fromHex(foreground), RgbColor.fromHex(background), 16, 400,
                new TextSample.Bounds(x, 0, 10, 10), null);
    }
}
