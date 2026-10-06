package dev.kidocolors.core;

public final class ColorDistance {
    private ColorDistance() { }

    /** Normalized Euclidean distance in encoded sRGB, NOT a perceptual Delta E. */
    public static double between(RgbColor first, RgbColor second) {
        double red = first.red() - second.red(), green = first.green() - second.green(), blue = first.blue() - second.blue();
        return Math.min(1, Math.sqrt(red * red + green * green + blue * blue) / (255 * Math.sqrt(3)));
    }

    public static double similarity(RgbColor first, RgbColor second) { return 1 - between(first, second); }

    /** KidoColors heuristic only. These thresholds require empirical validation. */
    public static boolean potentiallyConfused(double originalDistance, double simulatedDistance) {
        if (!Double.isFinite(originalDistance) || !Double.isFinite(simulatedDistance)
                || originalDistance < 0 || originalDistance > 1 || simulatedDistance < 0 || simulatedDistance > 1) {
            throw new IllegalArgumentException("Distances must be finite and between 0 and 1");
        }
        return originalDistance >= 0.20 && simulatedDistance <= 0.08 && simulatedDistance <= originalDistance * 0.40;
    }
}
