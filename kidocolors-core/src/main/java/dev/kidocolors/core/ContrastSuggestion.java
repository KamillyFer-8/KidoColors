package dev.kidocolors.core;

import java.util.Optional;

public final class ContrastSuggestion {
    private ContrastSuggestion() { }
    public record Correction(RgbColor foreground, double ratio) { }

    /** Searches discrete blends toward black/white; closest valid candidate among these two paths. */
    public static Optional<Correction> suggest(RgbColor foreground, RgbColor background, double threshold) {
        if (!Double.isFinite(threshold) || threshold < 1 || threshold > 21) throw new IllegalArgumentException("Invalid contrast threshold");
        double current = Contrast.ratio(foreground, background);
        if (current >= threshold) return Optional.of(new Correction(foreground, current));
        Correction best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int destination : new int[]{0, 255}) {
            for (int step = 1; step <= 255; step++) {
                double weight = step / 255.0;
                RgbColor candidate = new RgbColor(blend(foreground.red(), destination, weight),
                        blend(foreground.green(), destination, weight), blend(foreground.blue(), destination, weight));
                double ratio = Contrast.ratio(candidate, background);
                double distance = ColorDistance.between(foreground, candidate);
                if (ratio >= threshold && distance < bestDistance) {
                    best = new Correction(candidate, ratio);
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static int blend(int channel, int destination, double weight) {
        return (int) Math.round(channel + (destination - channel) * weight);
    }
}
