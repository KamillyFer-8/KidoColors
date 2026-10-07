package dev.kidocolors.backend.validation;

import org.apache.commons.csv.CSVFormat;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;

/** Offline evaluation of supplied human labels; never creates or infers reference labels. */
public final class ValidationEvaluator {
    public enum Reference { FAIL, PASS, EXCLUDED }
    public enum Detector { FAIL, PASS, MISSING, SKIPPED }
    public record Counts(int tp, int fp, int fn, int tn, Double precision, Double recall, Double f1) {
        static Counts from(int tp, int fp, int fn, int tn) {
            return new Counts(tp, fp, fn, tn, tp + fp == 0 ? null : tp / (double) (tp + fp),
                    tp + fn == 0 ? null : tp / (double) (tp + fn),
                    2 * tp + fp + fn == 0 ? null : 2.0 * tp / (2 * tp + fp + fn));
        }
    }
    public record Result(String labelFileSha256, String scope, int rows, int analyses, int excluded,
            int missing, int skipped, Double classifiedCoverage, Counts endToEnd, Counts classifiedOnly) { }
    private ValidationEvaluator() { }

    public static Result evaluate(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > 1_048_576) throw new IllegalArgumentException("Use um CSV de rótulos com até 1 MiB.");
        try {
            String csv = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (csv.startsWith("\uFEFF")) csv = csv.substring(1);
            Set<String> units = new HashSet<>(), analyses = new HashSet<>();
            int rows = 0, excluded = 0, missing = 0, skipped = 0, classified = 0;
            int[] all = new int[4], resolved = new int[4];
            try (var parser = CSVFormat.RFC4180.parse(new StringReader(csv))) {
                var iterator = parser.iterator();
                if (!iterator.hasNext()) throw new IllegalArgumentException("Cabeçalho obrigatório.");
                List<String> columns = iterator.next().stream().map(String::strip).toList();
                Set<String> expected = Set.of("analysis_id", "unit_id", "reference", "detector", "reviewer", "evidence");
                if (columns.size() != expected.size() || !new HashSet<>(columns).equals(expected)) throw new IllegalArgumentException("Cabeçalho esperado: analysis_id,unit_id,reference,detector,reviewer,evidence.");
                while (iterator.hasNext()) {
                    var row = iterator.next();
                    if (row.size() != columns.size() || ++rows > 10000) throw new IllegalArgumentException("Colunas inválidas ou mais de 10.000 unidades.");
                    List<String> values = row.stream().map(String::strip).toList();
                    if (values.stream().anyMatch(value -> value.isBlank() || value.length() > 4000)) throw new IllegalArgumentException("Preencha todos os campos, com até 4.000 caracteres cada.");
                    String analysis = UUID.fromString(values.get(columns.indexOf("analysis_id"))).toString();
                    String unit = values.get(columns.indexOf("unit_id"));
                    if (!units.add(analysis + ":" + unit)) throw new IllegalArgumentException("Unidade duplicada na mesma análise: " + unit);
                    analyses.add(analysis);
                    Reference reference = Reference.valueOf(values.get(columns.indexOf("reference")));
                    Detector detector = Detector.valueOf(values.get(columns.indexOf("detector")));
                    if (reference == Reference.EXCLUDED) { excluded++; continue; }
                    if (detector == Detector.MISSING) missing++;
                    if (detector == Detector.SKIPPED) skipped++;
                    boolean positive = reference == Reference.FAIL;
                    boolean predicted = detector == Detector.FAIL;
                    int index = positive ? (predicted ? 0 : 2) : (predicted ? 1 : 3);
                    all[index]++;
                    if (detector == Detector.FAIL || detector == Detector.PASS) { classified++; resolved[index]++; }
                }
            }
            if (rows == 0) throw new IllegalArgumentException("O template está vazio. Preencha rótulos reais antes de calcular métricas.");
            int eligible = rows - excluded;
            return new Result(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    "Contraste AA: detecção de falhas nos rótulos fornecidos; autenticidade e completude exigem revisão humana.",
                    rows, analyses.size(), excluded, missing, skipped, eligible == 0 ? null : classified / (double) eligible,
                    Counts.from(all[0], all[1], all[2], all[3]), Counts.from(resolved[0], resolved[1], resolved[2], resolved[3]));
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("Não foi possível ler os rótulos. Use CSV UTF-8 válido.", exception); }
    }
}
