package dev.kidocolors.backend.study;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import java.io.StringWriter;
import java.io.IOException;

public final class StudyCsv {
    private StudyCsv() { }
    public static String export(StudyResponse study) {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.RFC4180)) {
            printer.printRecord("study_id", "dataset_sha256", "id", "site", "url_solicitada", "url_normalizada", "categoria", "inicio", "fim", "status", "analysis_id", "score", "elementos_avaliados", "problemas", "contraste", "protanopia", "deuteranopia", "tritanopia", "duracao_linha_ms", "duracao_analise_ms", "engine_version", "textos_ignorados", "coleta_parcial", "pares_limitados", "recursos_bloqueados", "error_code", "error_message", "url_final", "browser_version", "viewport_width", "viewport_height", "timeout_ms", "settle_ms");
            for (var row : study.rows()) {
                var a = row.analysis(); var r = row.report(); var c = row.capture();
                printer.printRecord(study.id(), study.datasetSha256(), safe(row.sourceId()), safe(row.site()), safe(row.requestedUrl()),
                        a == null ? null : safe(a.url()), safe(row.category()), row.startedAt(), row.finishedAt(), row.completed() ? "COMPLETED" : "FAILED",
                        a == null ? null : a.id(), row.completed() ? a.score() : null, row.completed() ? a.elementsAnalyzed() : null,
                        row.completed() ? a.totalIssues() : null, row.completed() ? a.contrastFailures() : null,
                        row.completed() ? a.protanopiaWarnings() : null, row.completed() ? a.deuteranopiaWarnings() : null,
                        row.completed() ? a.tritanopiaWarnings() : null, row.durationMs(), a == null ? null : a.durationMs(),
                        r == null ? null : safe(r.engineVersion()), r == null ? null : r.elementsSkipped(), r == null ? null : r.collectionTruncated(),
                        r == null ? null : r.differentiationTruncated(), r == null ? null : r.blockedRequests(), safe(row.errorCode()), safe(row.errorMessage()),
                        c == null ? null : safe(c.finalUrl()), c == null ? null : safe(c.browserVersion()), c == null ? null : c.viewportWidth(),
                        c == null ? null : c.viewportHeight(), c == null ? null : c.timeoutMs(), c == null ? null : c.settleMs());
            }
        } catch (IOException exception) { throw new IllegalStateException("Cannot export study CSV", exception); }
        return "\uFEFF" + out; // UTF-8 BOM helps spreadsheet applications recognize accents.
    }
    static String safe(String value) {
        if (value == null || value.isEmpty()) return value;
        String stripped = value.stripLeading();
        boolean control = value.chars().anyMatch(c -> c < 32 || c == 127);
        return control || (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) ? "'" + value : value;
    }
}
