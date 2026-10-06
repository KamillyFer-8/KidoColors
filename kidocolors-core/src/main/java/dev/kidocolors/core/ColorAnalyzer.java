package dev.kidocolors.core;

import java.util.*;

/** Deterministic analysis of one collection; no HTTP, Spring, persistence or screenshot IO. */
public final class ColorAnalyzer {
    public static final String VERSION = "1.0-contrast-aa-machado1-rgb-heuristic";
    private static final int MAX_NEARBY_PAIRS = 20_000;

    public AnalysisResult analyze(List<TextSample> samples) {
        List<Finding> findings = new ArrayList<>();
        List<Integer> eligible = new ArrayList<>();
        int contrastFailures = 0;
        for (int index = 0; index < samples.size(); index++) {
            TextSample sample = samples.get(index);
            if (!sample.evaluable()) continue;
            eligible.add(index);
            Contrast.Assessment contrast = Contrast.assess(sample.foreground(), sample.background(), sample.fontSizePx(), sample.fontWeight());
            if (!contrast.passesAA()) {
                contrastFailures++;
                var correction = ContrastSuggestion.suggest(sample.foreground(), sample.background(), contrast.aaThreshold()).orElseThrow();
                findings.add(new Finding(Finding.Type.CONTRAST, contrast.ratio() < 3 ? Finding.Severity.HIGH : Finding.Severity.MEDIUM,
                        "Contraste de texto abaixo do limiar WCAG 2.2 AA (1.4.3).", index, null, null,
                        Finding.ColorRole.TEXT_BACKGROUND, sample.foreground(), sample.background(), null, null,
                        contrast.ratio(), contrast.aaThreshold(), null, null, correction.foreground(), correction.ratio()));
            }
        }
        Map<RgbColor, EnumMap<Simulation, RgbColor>> cache = new HashMap<>();
        Set<String> reported = new HashSet<>();
        int nearbyPairs = 0;
        boolean truncated = false;
        outer: for (int first = 0; first < eligible.size(); first++) {
            for (int second = first + 1; second < eligible.size(); second++) {
                int left = eligible.get(first), right = eligible.get(second);
                TextSample a = samples.get(left), b = samples.get(right);
                if (!a.bounds().near(b.bounds())) continue;
                if (nearbyPairs == MAX_NEARBY_PAIRS) { truncated = true; break outer; }
                nearbyPairs++;
                for (Finding.ColorRole role : List.of(Finding.ColorRole.FOREGROUND, Finding.ColorRole.BACKGROUND)) {
                    RgbColor colorA = role == Finding.ColorRole.FOREGROUND ? a.foreground() : a.background();
                    RgbColor colorB = role == Finding.ColorRole.FOREGROUND ? b.foreground() : b.background();
                    double originalDistance = ColorDistance.between(colorA, colorB);
                    if (originalDistance < 0.20) continue;
                    for (Simulation simulation : Simulation.values()) {
                        RgbColor simulatedA = simulated(cache, colorA, simulation), simulatedB = simulated(cache, colorB, simulation);
                        double distance = ColorDistance.between(simulatedA, simulatedB);
                        String key = role + ":" + simulation + ":" + (colorA.toHex().compareTo(colorB.toHex()) < 0
                                ? colorA.toHex() + colorB.toHex() : colorB.toHex() + colorA.toHex());
                        if (ColorDistance.potentiallyConfused(originalDistance, distance) && reported.add(key)) {
                            findings.add(new Finding(Finding.Type.COLOR_DIFFERENTIATION, Finding.Severity.WARNING,
                                    "Cores próximas na simulação: aviso heurístico; confirme o significado e ofereça pistas além da cor.",
                                    left, right, simulation, role, colorA, colorB, simulatedA, simulatedB,
                                    null, null, originalDistance, distance, null, null));
                        }
                    }
                }
            }
        }
        return new AnalysisResult(VERSION, AccessibilityScore.calculate(eligible.size(), contrastFailures),
                eligible.size(), samples.size() - eligible.size(), contrastFailures,
                warnings(findings, Simulation.PROTANOPIA), warnings(findings, Simulation.DEUTERANOPIA),
                warnings(findings, Simulation.TRITANOPIA), nearbyPairs, truncated, findings);
    }

    private RgbColor simulated(Map<RgbColor, EnumMap<Simulation, RgbColor>> cache, RgbColor color, Simulation simulation) {
        return cache.computeIfAbsent(color, key -> new EnumMap<>(Simulation.class))
                .computeIfAbsent(simulation, key -> simulation.apply(color));
    }
    private int warnings(List<Finding> findings, Simulation simulation) {
        return (int) findings.stream().filter(finding -> finding.simulation() == simulation).count();
    }
}
