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

    @Test void savesCaptureAndMeasuresDurationWithoutInventingAnalysisResults() {
        when(scanner.scan(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(20);
            return new PageCapture("https://example.com/", "fixture", 1280, 720, 720, 15000, 500, 0, false, List.of());
        });
        Instant before = Instant.now();
        AnalysisResponse result = service.create(new CreateAnalysisRequest("https://example.com", " Educação "));
        assertEquals("https://example.com/", result.url());
        assertEquals("Educação", result.category());
        assertEquals(AnalysisStatus.COMPLETED, result.status());
        assertFalse(result.createdAt().isBefore(before));
        assertNotNull(result.finishedAt());
        assertTrue(result.durationMs() >= 20);
        assertNull(result.score());
        assertEquals(0, result.elementsAnalyzed());
        assertEquals(0, result.totalIssues());
        assertEquals(0, result.elementsCollected());
        verify(repository, times(3)).saveAndFlush(any(Analysis.class));
        verify(images).generate(result.id());
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

    @Test void simulationFailureDoesNotExposePartialReportOrInventMetrics() {
        when(scanner.scan(any(), any())).thenReturn(new PageCapture("https://example.com/", "fixture", 1280, 720,
                720, 15000, 0, 0, false, List.of()));
        doThrow(new ScanException(ScanException.Code.STORAGE, "Falha de imagem.")).when(images).generate(any());
        AnalysisResponse result = service.create(new CreateAnalysisRequest("https://example.com", null));
        assertEquals(AnalysisStatus.FAILED, result.status());
        assertEquals("STORAGE", result.errorCode());
        assertNull(result.score());
        assertNull(result.reportUrl());
        assertNotNull(result.captureUrl());
        assertNotNull(result.durationMs());
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
