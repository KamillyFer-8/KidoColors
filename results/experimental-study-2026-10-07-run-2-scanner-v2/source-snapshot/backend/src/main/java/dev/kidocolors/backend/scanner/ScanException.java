package dev.kidocolors.backend.scanner;

public class ScanException extends RuntimeException {
    public enum Code { TIMEOUT, INACCESSIBLE, BLOCKED, LIMIT_EXCEEDED, STORAGE, BROWSER, QUALITY }
    private final Code code;
    private ScannerDiagnostics diagnostics;
    private PageCapture partialCapture;

    public ScanException(Code code, String message) { super(message); this.code = code; }
    public ScanException(Code code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public Code code() { return code; }
    public ScannerDiagnostics diagnostics() { return diagnostics; }
    public PageCapture partialCapture() { return partialCapture; }
    public ScanException observed(ScannerDiagnostics value, PageCapture capture) {
        diagnostics = value;
        partialCapture = capture == null ? null : capture.withDiagnostics(value);
        return this;
    }
}
