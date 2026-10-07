package dev.kidocolors.backend.study;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kidocolors.backend.analysis.AnalysisRepository;
import dev.kidocolors.backend.analysis.SimulationImageService;
import dev.kidocolors.backend.scanner.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fixture scanner + actual services, core, H2/JPA and HTTP contracts. No real study executed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StudyRepository studies;
    @Autowired AnalysisRepository analyses;
    @MockitoBean PageScanner scanner;
    @MockitoBean SimulationImageService images;
    private static final String HEADER = "id,site,url,categoria\n";
    @BeforeEach void resetDatabase() { analyses.deleteAll(); studies.deleteAll(); }
    private MockMultipartFile csv(String content) { return new MockMultipartFile("file", "fixture.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8)); }
    private PageCapture capture(boolean contrastFails) {
        return new PageCapture("https://example.com/", "fixture-browser", 1280, 720, 720, 15000, 500, 0, false,
                List.of(new CollectedText("Fixture text", "p", 0, contrastFails ? "#999999" : "#000000", "#FFFFFF", 16, 400, 1, 2, 30, 10, null)));
    }
    @Test void sequentiallyReusesAnalysisServiceAndPersistsMetricsFailuresAndLinks() throws Exception {
        when(scanner.scan(any(), any())).thenReturn(capture(false)).thenThrow(new ScanException(ScanException.Code.TIMEOUT, "Fixture timeout")).thenReturn(capture(true));
        String dataset = HEADER + "1,A,https://example.com,Ensino\n2,B,http://localhost,Ensino\n3,C,https://example.org,Serviços\n4,D,https://example.net,Serviços\n";
        String body = mvc.perform(multipart("/api/studies").file(csv(dataset)).param("name", "Fixture study"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.environment.scannerProtocol").value("scanner-v2"))
                .andExpect(jsonPath("$.environment.timeoutMs").value(30000))
                .andExpect(jsonPath("$.environment.readinessTimeoutMs").value(5000))
                .andExpect(jsonPath("$.totalRows").value(4)).andExpect(jsonPath("$.metrics.processed").value(4))
                .andExpect(jsonPath("$.metrics.completed").value(2)).andExpect(jsonPath("$.metrics.failed").value(2))
                .andExpect(jsonPath("$.metrics.successRate").value(0.5)).andExpect(jsonPath("$.metrics.meanScore").value(50.0))
                .andExpect(jsonPath("$.metrics.medianScore").value(50.0)).andExpect(jsonPath("$.metrics.minScore").value(0))
                .andExpect(jsonPath("$.metrics.maxScore").value(100)).andExpect(jsonPath("$.metrics.elementsEvaluated").value(2))
                .andExpect(jsonPath("$.metrics.contrastFailures").value(1)).andExpect(jsonPath("$.durationMs").isNumber())
                .andExpect(jsonPath("$.rows[1].errorCode").value("INVALID_URL"))
                .andExpect(jsonPath("$.rows[2].errorCode").value("TIMEOUT"))
                .andExpect(jsonPath("$.rows[0].capture.browserVersion").value("fixture-browser"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(body).get("id").asText());
        assertEquals(3, analyses.count()); assertEquals(1, studies.count());
        for (var analysis : analyses.findAll()) assertEquals(id, analysis.getStudyRun().getId());
        var order = inOrder(scanner); order.verify(scanner).scan(eq("https://example.com/"), any());
        order.verify(scanner).scan(eq("https://example.org/"), any()); order.verify(scanner).scan(eq("https://example.net/"), any());
        mvc.perform(get("/api/studies/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.metrics.scored").value(2));
        mvc.perform(get("/api/studies/" + id + "/dataset.csv")).andExpect(status().isOk()).andExpect(content().string(dataset));
    }
    @Test void rejectsMalformedDatasetBeforeAnyAnalysis() throws Exception {
        mvc.perform(multipart("/api/studies").file(csv(HEADER + "1,A,https://example.com,C\n1,B,https://example.org,C\n")).param("name", "Invalid fixture"))
                .andExpect(status().isBadRequest());
        assertEquals(0, studies.count()); assertEquals(0, analyses.count()); verifyNoInteractions(scanner);
    }
    @Test void zeroCoverageIsQualityFailureAndExcludedFromScoreStatistics() throws Exception {
        when(scanner.scan(any(), any())).thenReturn(new PageCapture("https://example.com/", "fixture", 1280, 720, 720, 15000, 500, 0, false, List.of()));
        mvc.perform(multipart("/api/studies").file(csv(HEADER + "1,A,https://example.com,\n")).param("name", "No coverage fixture"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.metrics.completed").value(0))
                .andExpect(jsonPath("$.metrics.failed").value(1)).andExpect(jsonPath("$.rows[0].errorCode").value("QUALITY"))
                .andExpect(jsonPath("$.metrics.scored").value(0)).andExpect(jsonPath("$.metrics.meanScore").isEmpty())
                .andExpect(jsonPath("$.metrics.medianScore").isEmpty()).andExpect(jsonPath("$.categories[''].processed").value(1));
    }
    @Test void exportsCsvWithQuotedFieldsAndFormulaNeutralization() throws Exception {
        when(scanner.scan(any(), any())).thenReturn(capture(false));
        String body = mvc.perform(multipart("/api/studies").file(csv(HEADER + "1,\"=1+1, teste\",https://example.com,C\n")).param("name", "CSV fixture"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(body).get("id").asText();
        String export = mvc.perform(get("/api/studies/" + id + "/export.csv")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=study-" + id + ".csv"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(export.startsWith("\uFEFF"));
        try (var parser = org.apache.commons.csv.CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get().parse(new java.io.StringReader(export.substring(1)))) {
            var row = parser.getRecords().getFirst();
            assertEquals("'=1+1, teste", row.get("site")); assertEquals("100", row.get("score"));
            assertEquals("COMPLETED", row.get("status")); assertFalse(row.get("engine_version").isBlank());
            assertEquals("fixture-browser", row.get("browser_version")); assertEquals("1280", row.get("viewport_width"));
        }
    }
    @Test void returnsNotFoundAndValidatesNameAndMissingFile() throws Exception {
        mvc.perform(get("/api/studies/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/studies").file(csv(HEADER + "1,A,https://example.com,C\n")).param("name", " ")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/studies").param("name", "Missing file")).andExpect(status().isBadRequest());
    }
}
