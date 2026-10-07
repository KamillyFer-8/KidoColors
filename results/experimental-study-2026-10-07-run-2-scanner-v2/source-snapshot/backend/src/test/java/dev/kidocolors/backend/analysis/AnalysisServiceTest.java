package dev.kidocolors.backend.analysis;

import dev.kidocolors.backend.url.UrlValidator;
import dev.kidocolors.backend.url.InvalidUrlException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kidocolors.backend.scanner.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnalysisServiceTest {
    private final AnalysisRepository repository = mock(AnalysisRepository.class);
    private AnalysisService service;
    private final PageScanner scanner = mock(PageScanner.class);
    private final SimulationImageService images = mock(SimulationImageService.class);

    @BeforeEach void setUp() {
        service = new AnalysisService(repository, new UrlValidator(), scanner, new ObjectMapper(), new AnalysisEngine(), images);
        when(repository.saveAndFlush(any(Analysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void zeroTextPreservesCaptureAndDurationButFailsQualityWithoutInventingResults() {
        when(scanner.scan(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(20);
            return new PageCapture("https://example.com/", "fixture", 1280, 720, 720, 15000, 500, 0, false, List.of());
        });
        Instant before = Instant.now();
        AnalysisResponse result = service.create(new CreateAnalysisRequest("https://example.com", " Educação "));
        assertEquals("https://example.com/", result.url());
        assertEquals("Educação", result.category());
        assertEquals(AnalysisStatus.FAILED, result.status());
        assertEquals("QUALITY", result.errorCode());
        assertFalse(result.createdAt().isBefore(before));
        assertNotNull(result.finishedAt());
        assertTrue(result.durationMs() >= 20);
        assertNull(result.score());
        assertNull(result.elementsAnalyzed());
        assertNull(result.totalIssues());
        assertEquals(0, result.elementsCollected());
        verify(repository, times(3)).saveAndFlush(any(Analysis.class));
        verifyNoInteractions(images);
    }

    @Test void scannerTimeoutIsPersistedWithDurationAndNoCapture() {
        when(scanner.scan(any(), any())).thenThrow(new ScanException(ScanException.Code.TIMEOUT, "Tempo limite."));
        AnalysisResponse result = service.create(new CreateAnalysisRequest("https://example.com", null));
        assertEquals(AnalysisStatus.FAILED, result.status());
        assertEquals("TIMEOUT", result.errorCode());
        assertNotNull(result.durationMs());
        assertNull(result.score());
        assertNull(result.captureUrl());
        verify(repository, times(3)).saveAndFlush(any(Analysis.class));
    }

    @Test void scannerFailureIsPersisted() {
        when(scanner.scan(any(), any())).thenThrow(new ScanException(ScanException.Code.INACCESSIBLE, "Página inacessível."));
        AnalysisResponse result = service.create(new CreateAnalysisRequest("https://example.com", null));
        assertEquals("INACCESSIBLE", result.errorCode());
        assertNotNull(result.finishedAt());
    }

    @Test void qualityFailurePreservesCaptureAndDiagnosticsWithoutCreatingScore() throws Exception {
        var diagnostics = new ScannerDiagnostics("scanner-v2", "https://example.com/", "https://example.com/",
                200, "QUALITY", "QUALITY", "Sem texto", null, null, 100, 720, false,
                List.of(), List.of("ZERO_COLLECTED_ELEMENTS"), 5000);
        var capture = new PageCapture("https://example.com/", "fixture", 1280, 720, 720,
                30000, 500, 0, false, List.of(), diagnostics);
        when(scanner.scan(any(), any())).thenThrow(new ScanException(ScanException.Code.QUALITY, "Sem texto")
                .observed(diagnostics, capture));
        AnalysisResponse result = service.create(new CreateAnalysisRequest("https://example.com", null));
        assertEquals(AnalysisStatus.FAILED, result.status());
        assertNull(result.score());
        assertNotNull(result.captureUrl());
        assertNull(result.reportUrl());
        assertEquals("QUALITY", new ObjectMapper().readTree(result.scannerDiagnosticsJson()).get("phase").asText());
        verifyNoInteractions(images);
    }

    @Test void legacyCaptureWithoutDiagnosticsRemainsReadable() throws Exception {
        var mapper = new ObjectMapper();
        var legacy = mapper.readValue("""
                {"finalUrl":"https://example.com/","browserVersion":"fixture","viewportWidth":1280,
                "viewportHeight":720,"pageHeight":720,"timeoutMs":15000,"settleMs":500,
                "blockedRequests":0,"truncated":false,"elements":[]}
                """, PageCapture.class);
        assertNull(legacy.diagnostics());
        assertEquals(15000, legacy.timeoutMs());
    }

    @Test void simulationFailureDoesNotExposePartialReportOrInventMetrics() {
        var diagnostics = new ScannerDiagnostics("scanner-v2", "https://example.com/", "https://example.com/",
                200, "COMPLETE", null, null, null, null, 100, 720, false, List.of(), List.of(), 5000);
        when(scanner.scan(any(), any())).thenReturn(new PageCapture("https://example.com/", "fixture", 1280, 720,
                720, 30000, 0, 0, false, List.of(new CollectedText("Fixture", "p", 0,
                        "#000000", "#FFFFFF", 16, 400, 0, 0, 10, 10, null)), diagnostics));
        doThrow(new ScanException(ScanException.Code.STORAGE, "Falha de imagem.", new IllegalStateException("Fixture")))
                .when(images).generate(any());
        AnalysisResponse result = service.create(new CreateAnalysisRequest("https://example.com", null));
        assertEquals(AnalysisStatus.FAILED, result.status());
        assertEquals("STORAGE", result.errorCode());
        assertNull(result.score());
        assertNull(result.reportUrl());
        assertNotNull(result.captureUrl());
        assertNotNull(result.durationMs());
        assertTrue(result.scannerDiagnosticsJson().contains("SIMULATION_SCREENSHOT"));
        assertTrue(result.scannerDiagnosticsJson().contains("java.lang.IllegalStateException"));
    }

    @Test void invalidUrlIsNeverPersisted() {
        assertThrows(InvalidUrlException.class,
                () -> service.create(new CreateAnalysisRequest("http://localhost", null)));
        verifyNoInteractions(repository);
    }

    @Test void missingAnalysisIsReported() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(AnalysisNotFoundException.class, () -> service.get(id));
    }

    @Test void returnsPersistedAnalysis() {
        Analysis analysis = Analysis.pending("https://example.com/", null, Instant.now());
        when(repository.findById(analysis.getId())).thenReturn(Optional.of(analysis));
        assertEquals(analysis.getId(), service.get(analysis.getId()).id());
    }

    @Test void invalidPaginationDoesNotQueryDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.history(-1, 20, null));
        assertThrows(IllegalArgumentException.class, () -> service.history(0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> service.history(0, 101, null));
        verifyNoInteractions(repository);
    }
}
