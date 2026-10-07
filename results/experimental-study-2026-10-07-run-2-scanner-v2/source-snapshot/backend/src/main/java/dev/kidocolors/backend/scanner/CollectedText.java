package dev.kidocolors.backend.scanner;

public record CollectedText(String text, String selector, int textNodeIndex, String foreground,
        String background, double fontSizePx, int fontWeight, double x, double y,
        double width, double height, String unsupportedReason) { }
