package dev.kidocolors.core;

import java.util.Objects;

/** WCAG 2.2 text contrast calculations; does not certify a page's conformance. */
public final class Contrast {
    private Contrast() { }

    public static double ratio(RgbColor foreground, RgbColor background) {
        double first = Objects.requireNonNull(foreground).luminance();
        double second = Objects.requireNonNull(background).luminance();
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    public static Assessment assess(RgbColor foreground, RgbColor background,
                                    double fontSizePx, int fontWeight) {
        if (!Double.isFinite(fontSizePx) || fontSizePx <= 0 || fontWeight < 1 || fontWeight > 1000) {
            throw new IllegalArgumentException("Invalid CSS font size or weight");
        }
        boolean large = fontSizePx >= 24 || (fontSizePx >= 14 * 96.0 / 72 && fontWeight >= 700);
        double actual = ratio(foreground, background);
        return new Assessment(actual, large ? 3 : 4.5, large ? 4.5 : 7);
    }

    public record Assessment(double ratio, double aaThreshold, double aaaThreshold) {
        public boolean passesAA() { return ratio >= aaThreshold; }
        public boolean passesAAA() { return ratio >= aaaThreshold; }
    }
}
