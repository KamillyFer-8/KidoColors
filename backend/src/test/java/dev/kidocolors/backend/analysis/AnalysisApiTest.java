package dev.kidocolors.backend.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.List;
import dev.kidocolors.backend.scanner.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** API + real JPA persistence using H2 only as a test database, not real study data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalysisApiTest {
    @Autowired MockMvc mvc;
    @Autowired AnalysisRepository repository;
    @Autowired ObjectMapper mapper;
    @MockitoBean PageScanner scanner;
    @MockitoBean SimulationImageService images;

    @BeforeEach void cleanDatabase() {
        repository.deleteAll();
        when(scanner.scan(any(), any())).thenReturn(new PageCapture("https://example.com/", "test-fixture",
                1280, 720, 720, 15000, 500, 0, false, List.of()));
    }

    @Test void createsAndRetrievesPersistedRequest() throws Exception {
        String response = mvc.perform(post("/api/analyses").contentType("application/json")
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.score").value(nullValue()))
                .andExpect(jsonPath("$.durationMs").isNumber())
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(response).get("id").asText();
        assertTrue(repository.existsById(UUID.fromString(id)));
        mvc.perform(get("/api/analyses/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com/"));
        mvc.perform(get("/api/analyses/" + id + "/capture")).andExpect(status().isOk())
                .andExpect(jsonPath("$.browserVersion").value("test-fixture"));
        mvc.perform(get("/api/analyses/" + id + "/report")).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.score").value(nullValue()))
                .andExpect(jsonPath("$.summary.elementsEvaluated").value(0));
    }

    @Test void readinessChecksApiAndActualTestDatabase() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test void persistsMathReportWithIssuePositionAndSuggestion() throws Exception {
        when(scanner.scan(any(), any())).thenReturn(new PageCapture("https://example.com/", "test-fixture",
                1280, 720, 720, 15000, 500, 0, false, List.of(
                new CollectedText("Fixture de baixo contraste", "#bad", 0, "#777777", "#FFFFFF", 16, 400, 20, 30, 100, 20, null),
                new CollectedText("Fixture adequada", "#good", 0, "#000000", "#FFFFFF", 16, 400, 20, 60, 100, 20, null))));
        String response = mvc.perform(post("/api/analyses").contentType("application/json")
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.score").value(50))
                .andExpect(jsonPath("$.contrastFailures").value(1))
                .andExpect(jsonPath("$.totalIssues").value(1)).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(response).get("id").asText();
        mvc.perform(get("/api/analyses/" + id + "/report")).andExpect(status().isOk())
                .andExpect(jsonPath("$.issues[0].detail.element.selector").value("#bad"))
                .andExpect(jsonPath("$.issues[0].detail.element.x").value(20))
                .andExpect(jsonPath("$.issues[0].detail.requiredRatio").value(4.5))
                .andExpect(jsonPath("$.issues[0].detail.suggestedForeground").value("#767676"))
                .andExpect(jsonPath("$.screenshots.PROTANOPIA").exists());
        mvc.perform(get("/api/analyses/" + id + "/screenshot").param("simulation", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test void persistsFailedScanAndReportsUnavailableCapture() throws Exception {
        when(scanner.scan(any(), any())).thenThrow(new ScanException(ScanException.Code.TIMEOUT, "Tempo limite."));
        String response = mvc.perform(post("/api/analyses").contentType("application/json")
                        .content("{\"url\":\"https://example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("TIMEOUT"))
                .andExpect(jsonPath("$.score").value(nullValue())).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(response).get("id").asText();
        mvc.perform(get("/api/analyses/" + id + "/capture")).andExpect(status().isNotFound());
        mvc.perform(get("/api/analyses/" + id + "/screenshot")).andExpect(status().isNotFound());
    }

    @Test void supportsEmptyHistoryAndExactUrlFiltering() throws Exception {
        mvc.perform(get("/api/analyses")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalItems").value(0));
        create("https://example.com");
        create("https://other.example.com");
        mvc.perform(get("/api/analyses").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/analyses/by-url").param("url", "https://example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].url").value("https://example.com/"));
    }

    @Test void rejectsDtoInvalidUrlAndMalformedJsonWithoutPersistence() throws Exception {
        for (String body : new String[]{"{}", "{\"url\":\" \"}", "{\"url\":\"http://127.0.0.1\"}",
                "{\"url\":\"https://example.com\",\"category\":\"" + "x".repeat(121) + "\"}",
                "{\"url\":\"https://example.com\",\"score\":100}", "{"}) {
            mvc.perform(post("/api/analyses").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        assertEquals(0, repository.count());
    }

    @Test void reportsNotFoundAndInvalidQueryParameters() throws Exception {
        mvc.perform(get("/api/analyses/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/analyses/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analyses").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analyses").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analyses").param("size", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analyses/by-url")).andExpect(status().isBadRequest());
    }

    private void create(String url) throws Exception {
        mvc.perform(post("/api/analyses").contentType("application/json")
                .content("{\"url\":\"" + url + "\"}")).andExpect(status().isCreated());
    }
}
