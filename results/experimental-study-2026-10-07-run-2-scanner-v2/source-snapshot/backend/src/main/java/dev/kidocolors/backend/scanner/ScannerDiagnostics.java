package dev.kidocolors.backend.scanner;

import java.net.URI;
import java.util.List;

/** Deliberately excludes headers, cookies, query values, fragments and stack traces. */
public record ScannerDiagnostics(String protocol, String initialUrl, String finalUrl,
        Integer httpStatus, String phase, String code, String message, String causeType,
        String technicalMessage, long durationMs, int coverageHeight, boolean heightTruncated,
        List<String> redirects, List<String> qualityWarnings, int readinessTimeoutMs) {
    public ScannerDiagnostics failure(String stage, String errorCode, String errorMessage, Throwable cause, long elapsed) {
        return new ScannerDiagnostics(protocol, initialUrl, finalUrl, httpStatus, stage, errorCode,
                errorMessage, cause == null ? null : cause.getClass().getName(), safeMessage(cause), elapsed,
                coverageHeight, heightTruncated, redirects, qualityWarnings, readinessTimeoutMs);
    }
    public static String safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (Exception ignored) { return "[URL indisponível]"; }
    }
    public static String safeMessage(Throwable cause) {
        if (cause == null || cause.getMessage() == null) return null;
        // Playwright call logs can include URL queries, headers and credentials: retain only the first line.
        String first = cause.getMessage().split("\\R", 2)[0];
        var detail = java.util.regex.Pattern.compile("(?m)^\\s*message=['\"](.*)").matcher(cause.getMessage());
        if (detail.find()) first = detail.group(1);
        first = first.replaceAll("https?://[^\\s<>]+", "[URL omitida]")
                .replaceAll("(?i)(password|token|authorization|cookie|secret)\\s*[:=].*", "$1=[omitido]");
        return first.substring(0, Math.min(first.length(), 1000));
    }
}
