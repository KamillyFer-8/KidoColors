package dev.kidocolors.core;

public record Finding(Type type, Severity severity, String message, int elementIndex,
        Integer relatedElementIndex, Simulation simulation, ColorRole colorRole,
        RgbColor firstColor, RgbColor secondColor, RgbColor simulatedFirstColor, RgbColor simulatedSecondColor,
        Double contrastRatio, Double requiredRatio, Double originalDistance, Double simulatedDistance,
        RgbColor suggestedForeground, Double suggestedContrast) {
    public enum Type { CONTRAST, COLOR_DIFFERENTIATION }
    public enum Severity { HIGH, MEDIUM, WARNING }
    public enum ColorRole { TEXT_BACKGROUND, FOREGROUND, BACKGROUND }
}
