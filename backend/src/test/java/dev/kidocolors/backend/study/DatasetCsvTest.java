package dev.kidocolors.backend.study;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class DatasetCsvTest {
    private byte[] bytes(String csv) { return csv.getBytes(StandardCharsets.UTF_8); }
    @Test void acceptsBomReorderedHeadersAndQuotedFields() {
        var dataset = DatasetCsv.parse(bytes("\uFEFFurl,categoria,site,id\r\nhttps://fixture.invalid,\"Educação, pesquisa\",\"Site \"\"teste\"\"\nsegunda linha\",1\r\n"));
        assertEquals("Educação, pesquisa", dataset.rows().getFirst().category());
        assertEquals("Site \"teste\"\nsegunda linha", dataset.rows().getFirst().site());
        assertEquals(64, dataset.sha256().length());
        assertTrue(dataset.csv().startsWith("\uFEFF"));
    }
    @Test void permitsBlankCategoryAndRetainsRepeatedUrlsAsSeparateIds() {
        var dataset = DatasetCsv.parse(bytes("id,site,url,categoria\n1,A,https://fixture.invalid,\n2,B,https://fixture.invalid,\n"));
        assertEquals(2, dataset.rows().size()); assertNull(dataset.rows().getFirst().category());
    }
    @Test void rejectsDuplicateIdsAndMalformedStructure() {
        for (String csv : new String[] { "id,site,url,categoria\n1,A,http://x,\n1,B,http://y,\n", "id;site;url;categoria\n1;A;http://x;C", "id,site,url,categoria\n", "id,site,url,categoria\n1,A,http://x,C,extra", "id,site,url,categoria\n1,A,\"inacabado,C", "id,site,url,categoria\n1,,http://x,C" }) {
            assertThrows(IllegalArgumentException.class, () -> DatasetCsv.parse(bytes(csv)));
        }
    }
    @Test void rejectsInvalidUtf8SizeAndRowLimits() {
        assertThrows(IllegalArgumentException.class, () -> DatasetCsv.parse(new byte[] {(byte) 0xc3, 0x28}));
        assertThrows(IllegalArgumentException.class, () -> DatasetCsv.parse(new byte[DatasetCsv.MAX_BYTES + 1]));
        StringBuilder csv = new StringBuilder("id,site,url,categoria\n");
        for (int i = 0; i <= DatasetCsv.MAX_ROWS; i++) csv.append(i).append(",A,https://fixture.invalid,C\n");
        assertThrows(IllegalArgumentException.class, () -> DatasetCsv.parse(bytes(csv.toString())));
    }
    @Test void hashChangesWhenRawDatasetBytesChange() {
        String csv = "id,site,url,categoria\n1,A,https://fixture.invalid,C\n";
        assertEquals(DatasetCsv.parse(bytes(csv)).sha256(), DatasetCsv.parse(bytes(csv)).sha256());
        assertNotEquals(DatasetCsv.parse(bytes(csv)).sha256(), DatasetCsv.parse(bytes(csv.replace("\n", "\r\n"))).sha256());
    }
    @Test void neutralizesSpreadsheetFormulaPrefixesAndControlCharacters() {
        for (String value : new String[] {"=SUM(A1)", "  +1", "-1", "@cmd", "\t=cmd", "line\nnext"}) assertTrue(StudyCsv.safe(value).startsWith("'"));
        assertEquals("https://fixture.invalid", StudyCsv.safe("https://fixture.invalid"));
        assertNull(StudyCsv.safe(null));
    }
}
