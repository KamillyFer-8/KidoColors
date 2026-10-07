package dev.kidocolors.core;

public final class AccessibilityScore {
    private AccessibilityScore() { }

    /** Own contrast score, not an official WCAG score. Null means no eligible text. */
    public static Integer calculate(int evaluated, int failures) {
        if (evaluated < 0 || failures < 0 || failures > evaluated) throw new IllegalArgumentException("Invalid counts");
        return evaluated == 0 ? null : (int) Math.round(100.0 * (evaluated - failures) / evaluated);
    }
}
