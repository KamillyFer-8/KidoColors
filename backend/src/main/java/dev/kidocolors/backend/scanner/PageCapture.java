package dev.kidocolors.backend.scanner;

import java.util.List;

public record PageCapture(String finalUrl, String browserVersion, int viewportWidth, int viewportHeight,
        int pageHeight, int timeoutMs, int settleMs, int blockedRequests, boolean truncated,
        List<CollectedText> elements) {
    public PageCapture { elements = List.copyOf(elements); }
}
