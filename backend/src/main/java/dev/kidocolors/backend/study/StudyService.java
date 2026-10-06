package dev.kidocolors.backend.study;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kidocolors.backend.analysis.*;
import dev.kidocolors.backend.scanner.ScannerSettings;
import dev.kidocolors.backend.url.InvalidUrlException;
import dev.kidocolors.core.ColorAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class StudyService {
    private static final Logger LOG = LoggerFactory.getLogger(StudyService.class);
    private final StudyRepository repository;
    private final AnalysisService analyses;
    private final ObjectMapper mapper;
    private final ScannerSettings settings;
    public StudyService(StudyRepository repository, AnalysisService analyses, ObjectMapper mapper, ScannerSettings settings) {
        this.repository = repository; this.analyses = analyses; this.mapper = mapper; this.settings = settings;
    }
    public StudyResponse create(String name, byte[] csv) {
        if (name == null || name.isBlank() || name.strip().length() > 120) throw new IllegalArgumentException("Informe um nome de lote com até 120 caracteres.");
        DatasetCsv.Dataset dataset = DatasetCsv.parse(csv); // Validate the entire structure before starting any browser.
        long started = System.nanoTime();
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("engineVersion", ColorAnalyzer.VERSION); environment.put("javaVersion", System.getProperty("java.version"));
        environment.put("os", System.getProperty("os.name")); environment.put("playwrightVersion", "1.63.0");
        environment.put("viewportWidth", 1280); environment.put("viewportHeight", 720);
        environment.put("timeoutMs", settings.getTimeoutMs()); environment.put("settleMs", settings.getSettleMs());
        environment.put("maxElements", settings.getMaxElements()); environment.put("maxPageHeight", settings.getMaxPageHeight());
        StudyRun run = StudyRun.start(name.strip(), dataset, json(environment));
        repository.saveAndFlush(run);
        List<StudyRow> rows = new ArrayList<>();
        try {
            for (var input : dataset.rows()) {
                long rowStarted = System.nanoTime(); Instant rowDate = Instant.now();
                AnalysisResponse analysis = null; ReportSummary report = null; String code = null, message = null;
                StudyRow.CaptureMetadata capture = null;
                try {
                    analysis = analyses.create(new CreateAnalysisRequest(input.url(), input.category()), run);
                    if (analysis.captureUrl() != null) capture = StudyRow.CaptureMetadata.from(analyses.capture(analysis.id()));
                    if (analysis.status() == AnalysisStatus.COMPLETED) report = analyses.report(analysis.id()).summary();
                    else { code = analysis.errorCode() == null ? "INCOMPLETE" : analysis.errorCode(); message = analysis.errorMessage(); }
                } catch (InvalidUrlException exception) {
                    code = "INVALID_URL"; message = exception.getMessage();
                } catch (RuntimeException exception) {
                    LOG.error("Falha no lote {}, linha {}", run.getId(), input.id(), exception);
                    code = "UNEXPECTED"; message = "Falha interna ao processar esta linha. Consulte os logs da API.";
                }
                rows.add(new StudyRow(input.id(), input.site(), input.url(), input.category(), rowDate, Instant.now(),
                        (System.nanoTime() - rowStarted) / 1_000_000, analysis, report, code, message, capture));
                run.checkpoint(json(rows)); repository.saveAndFlush(run);
            }
            run.complete();
        } catch (RuntimeException exception) {
            LOG.error("Lote {} interrompido", run.getId(), exception); run.fail();
        } finally {
            repository.saveAndFlush(run);
            run.finish((System.nanoTime() - started) / 1_000_000);
            repository.saveAndFlush(run); // Final duration update is excluded from measured interval.
        }
        return response(run);
    }
    public StudyResponse get(UUID id) { return response(find(id)); }
    public String dataset(UUID id) { return find(id).getDatasetCsv(); }
    private StudyRun find(UUID id) { return repository.findById(id).orElseThrow(StudyNotFoundException::new); }
    private StudyResponse response(StudyRun run) {
        try {
            List<StudyRow> rows = mapper.readValue(run.getRowsJson(), new TypeReference<>() { });
            Map<String, Object> environment = mapper.readValue(run.getEnvironmentJson(), new TypeReference<>() { });
            return new StudyResponse(run.getId(), run.getName(), run.getDatasetSha256(), run.getStatus(), run.getStartedAt(),
                    run.getFinishedAt(), run.getDurationMs(), run.getTotalRows(), run.getErrorMessage(), environment,
                    StudyMetrics.calculate(rows), StudyMetrics.byCategory(rows), rows);
        } catch (Exception exception) { throw new IllegalStateException("Invalid persisted study", exception); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Cannot serialize study", exception); }
    }
}
