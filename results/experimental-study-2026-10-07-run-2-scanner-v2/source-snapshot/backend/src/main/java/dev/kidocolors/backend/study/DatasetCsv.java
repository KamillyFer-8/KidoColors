package dev.kidocolors.backend.study;

import org.apache.commons.csv.CSVFormat;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Strict UTF-8, comma-separated dataset; never performs network requests. */
public final class DatasetCsv {
    public static final int MAX_BYTES = 1_048_576;
    public static final int MAX_ROWS = 500;
    public record Row(String id, String site, String url, String category) { }
    public record Dataset(String sha256, String csv, List<Row> rows) { }
    private DatasetCsv() { }

    public static Dataset parse(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("O CSV deve ter entre 1 byte e 1 MiB.");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            String input = text.startsWith("\uFEFF") ? text.substring(1) : text;
            List<Row> rows = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            try (var parser = CSVFormat.RFC4180.parse(new StringReader(input))) {
                var iterator = parser.iterator();
                if (!iterator.hasNext()) throw new IllegalArgumentException("O CSV precisa de cabeçalho e dados.");
                var header = iterator.next();
                List<String> columns = header.stream().map(String::strip).toList();
                if (columns.size() != 4 || !new HashSet<>(columns).equals(Set.of("id", "site", "url", "categoria"))) {
                    throw new IllegalArgumentException("Use exatamente os cabeçalhos id,site,url,categoria, separados por vírgula.");
                }
                while (iterator.hasNext()) {
                    var record = iterator.next();
                    if (record.size() != 4) throw new IllegalArgumentException("Registro " + record.getRecordNumber() + ": quantidade de colunas inválida.");
                    if (rows.size() >= MAX_ROWS) throw new IllegalArgumentException("O CSV deve ter no máximo " + MAX_ROWS + " linhas de dados.");
                    String id = record.get(columns.indexOf("id")).strip();
                    String site = record.get(columns.indexOf("site")).strip();
                    String url = record.get(columns.indexOf("url")).strip();
                    String category = record.get(columns.indexOf("categoria")).strip();
                    if (id.isBlank() || id.length() > 100 || site.isBlank() || site.length() > 200 || url.isBlank() || url.length() > 2048 || category.length() > 120) {
                        throw new IllegalArgumentException("Registro " + record.getRecordNumber() + ": id/site/url obrigatórios; limites 100/200/2048 caracteres e categoria até 120.");
                    }
                    if (!ids.add(id)) throw new IllegalArgumentException("ID duplicado no dataset: " + id);
                    rows.add(new Row(id, site, url, category.isBlank() ? null : category));
                }
            }
            if (rows.isEmpty()) throw new IllegalArgumentException("O CSV precisa de pelo menos uma linha de dados.");
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new Dataset(hash, text, List.copyOf(rows));
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("Não foi possível ler o CSV. Use UTF-8 e aspas válidas para campos com vírgulas ou quebras de linha.", exception); }
    }
}
