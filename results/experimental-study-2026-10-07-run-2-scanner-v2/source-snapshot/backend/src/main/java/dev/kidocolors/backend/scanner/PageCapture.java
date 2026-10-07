package dev.kidocolors.backend.scanner;

import java.util.List;

public record PageCapture(String finalUrl, String browserVersion, int viewportWidth, int viewportHeight,
        int pageHeight, int timeoutMs, int settleMs, int blockedRequests, boolean truncated,
        List<CollectedText> elements, ScannerDiagnostics diagnostics) {
    public PageCapture { elements = List.copyOf(elements); }
    public PageCapture(String finalUrl, String browserVersion, int viewportWidth, int viewportHeight,
            int pageHeight, int timeoutMs, int settleMs, int blockedRequests, boolean truncated,
            List<CollectedText> elements) {
        this(finalUrl, browserVersion, viewportWidth, viewportHeight, pageHeight, timeoutMs, settleMs,
                blockedRequests, truncated, elements, null);
    }
    public PageCapture withDiagnostics(ScannerDiagnostics value) {
        return new PageCapture(finalUrl, browserVersion, viewportWidth, viewportHeight, pageHeight,
                timeoutMs, settleMs, blockedRequests, truncated, elements, value);
    }
}
