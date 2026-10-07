package dev.kidocolors.backend.validation;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ValidationEvaluatorTest {
    private static final String HEADER = "analysis_id,unit_id,reference,detector,reviewer,evidence\n";
    private String row(String unit, String ref, String detector) {
        return "00000000-0000-0000-0000-000000000001," + unit + "," + ref + "," + detector + ",Fixture reviewer,Fixture evidence\n";
    }
    private ValidationEvaluator.Result evaluate(String csv) { return ValidationEvaluator.evaluate(csv.getBytes(StandardCharsets.UTF_8)); }
    @Test void computesConfusionMatrixIncludingUndetectedFailuresAndSeparateCoverage() {
        var result = evaluate(HEADER + row("1", "FAIL", "FAIL") + row("2", "PASS", "FAIL") + row("3", "FAIL", "PASS")
                + row("4", "PASS", "PASS") + row("5", "FAIL", "MISSING") + row("6", "FAIL", "SKIPPED") + row("7", "EXCLUDED", "FAIL"));
        assertEquals(1, result.endToEnd().tp()); assertEquals(1, result.endToEnd().fp());
        assertEquals(3, result.endToEnd().fn()); assertEquals(1, result.endToEnd().tn());
        assertEquals(.5, result.endToEnd().precision()); assertEquals(.25, result.endToEnd().recall());
        assertEquals(1.0 / 3, result.endToEnd().f1()); assertEquals(4.0 / 6, result.classifiedCoverage());
        assertEquals(.5, result.classifiedOnly().recall()); assertEquals(1, result.excluded());
        assertEquals(1, result.missing()); assertEquals(1, result.skipped()); assertEquals(64, result.labelFileSha256().length());
    }
    @Test void keepsUndefinedDenominatorsNull() {
        var result = evaluate(HEADER + row("1", "PASS", "MISSING"));
        assertNull(result.endToEnd().precision()); assertNull(result.endToEnd().recall()); assertNull(result.endToEnd().f1());
        assertEquals(1, result.endToEnd().tn()); assertEquals(0, result.classifiedCoverage());
        assertNull(result.classifiedOnly().f1());
        var excluded = evaluate(HEADER + row("1", "EXCLUDED", "SKIPPED"));
        assertNull(excluded.classifiedCoverage()); assertNull(excluded.endToEnd().recall());
    }
    @Test void returnsZeroF1WhenThereAreFalseNegativesAndNoTruePositive() {
        var result = evaluate(HEADER + row("1", "FAIL", "MISSING"));
        assertNull(result.endToEnd().precision()); assertEquals(0, result.endToEnd().recall()); assertEquals(0, result.endToEnd().f1());
    }
    @Test void rejectsEmptyTemplatesDuplicatesMissingEvidenceAndUnknownLabels() {
        for (String csv : new String[] { HEADER, HEADER + row("1", "FAIL", "FAIL") + row("1", "PASS", "PASS"),
                HEADER + row("1", "UNKNOWN", "FAIL"), HEADER + row("1", "PASS", "UNKNOWN"),
                HEADER + row("1", "PASS", "PASS").replace("Fixture evidence", ""), "wrong,header\n" }) {
            assertThrows(IllegalArgumentException.class, () -> evaluate(csv));
        }
    }
    @Test void handlesBomQuotedEvidenceAndRejectsMalformedUtf8() {
        var result = evaluate("\uFEFF" + HEADER + row("1", "FAIL", "FAIL").replace("Fixture evidence", "\"Fixture, evidence\""));
        assertEquals(1, result.endToEnd().tp());
        assertThrows(IllegalArgumentException.class, () -> ValidationEvaluator.evaluate(new byte[] {(byte) 0xc3, 0x28}));
    }
}
