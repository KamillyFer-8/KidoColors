package dev.kidocolors.backend.scanner;

public class ScanException extends RuntimeException {
    public enum Code { TIMEOUT, INACCESSIBLE, BLOCKED, LIMIT_EXCEEDED, STORAGE, BROWSER }
    private final Code code;

    public ScanException(Code code, String message) { super(message); this.code = code; }
    public ScanException(Code code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public Code code() { return code; }
}
