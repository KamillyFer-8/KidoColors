package dev.kidocolors.backend.analysis;

import dev.kidocolors.backend.url.UrlValidator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kidocolors.backend.scanner.PageScanner;
import dev.kidocolors.backend.scanner.PageCapture;
import dev.kidocolors.backend.scanner.ScanException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.LinkedHashMap;
import dev.kidocolors.core.Simulation;

@Service
public class AnalysisService {
    private static final Logger LOG = LoggerFactory.getLogger(AnalysisService.class);
    private final AnalysisRepository repository;
    private final UrlValidator urlValidator;
    private final PageScanner scanner;
    private final ObjectMapper mapper;
    private final AnalysisEngine engine;
    private final SimulationImageService simulationImages;

    public AnalysisService(AnalysisRepository repository, UrlValidator urlValidator,
                           PageScanner scanner, ObjectMapper mapper, AnalysisEngine engine,
                           SimulationImageService simulationImages) {
        this.repository = repository;
        this.urlValidator = urlValidator;
        this.scanner = scanner;
        this.mapper = mapper;
        this.engine = engine;
        this.simulationImages = simulationImages;
    }

    public AnalysisResponse create(CreateAnalysisRequest request) {
        return create(request, null);
    }

    public AnalysisResponse create(CreateAnalysisRequest request, dev.kidocolors.backend.study.StudyRun run) {
        String url = urlValidator.validate(request.url());
        String category = request.category() == null || request.category().isBlank()
                ? null : request.category().strip();
        Analysis analysis = Analysis.pending(url, category, Instant.now());
        analysis.associateStudy(run);
        analysis.start();
        repository.saveAndFlush(analysis);
        long started = System.nanoTime();
        try {
            PageCapture capture = scanner.scan(url, analysis.getId());
            analysis.scanned(mapper.writeValueAsString(capture), capture.elements().size());
            var result = engine.analyze(capture);
            simulationImages.generate(analysis.getId());
            ReportSummary summary = ReportSummary.from(result, capture);
            List<AccessibilityIssue> issues = new java.util.ArrayList<>();
            for (var finding : result.findings()) {
                IssueDetail detail = IssueDetail.from(finding, capture);
                issues.add(new AccessibilityIssue(analysis, detail, mapper.writeValueAsString(detail), issues.size()));
            }
            analysis.complete(summary, mapper.writeValueAsString(summary), issues);
        } catch (ScanException exception) {
            analysis.fail(exception.code().name(), exception.getMessage());
        } catch (IOException exception) {
            analysis.fail("STORAGE", "Não foi possível salvar os dados da captura.");
        } catch (RuntimeException exception) {
            LOG.error("Falha inesperada na coleta {}", analysis.getId(), exception);
            analysis.fail("UNEXPECTED", "Ocorreu uma falha inesperada durante a coleta.");
        } finally {
            // Includes browser cleanup and persisted capture/status; final duration update is outside the interval.
            repository.saveAndFlush(analysis);
            analysis.finish((System.nanoTime() - started) / 1_000_000, Instant.now());
            repository.saveAndFlush(analysis);
        }
        return AnalysisResponse.from(analysis);
    }

    @Transactional(readOnly = true)
    public AnalysisResponse get(UUID id) {
        return AnalysisResponse.from(repository.findById(id)
                .orElseThrow(() -> new AnalysisNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public PageCapture capture(UUID id) {
        Analysis analysis = repository.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
        if (analysis.getCaptureJson() == null) throw new CaptureNotFoundException();
        try { return mapper.readValue(analysis.getCaptureJson(), PageCapture.class); }
        catch (IOException exception) { throw new IllegalStateException("Invalid persisted capture", exception); }
    }

    @Transactional(readOnly = true)
    public AnalysisReportResponse report(UUID id) {
        Analysis analysis = repository.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
        if (analysis.getReportJson() == null) throw new ReportNotFoundException();
        try {
            List<AnalysisReportResponse.IssueResponse> issues = new java.util.ArrayList<>();
            for (AccessibilityIssue issue : analysis.getIssues()) {
                issues.add(new AnalysisReportResponse.IssueResponse(issue.getId(), mapper.readValue(issue.getDetailJson(), IssueDetail.class)));
            }
            var screenshots = new LinkedHashMap<String, String>();
            screenshots.put("ORIGINAL", "/api/analyses/" + id + "/screenshot");
            for (Simulation simulation : Simulation.values()) screenshots.put(simulation.name(),
                    "/api/analyses/" + id + "/screenshot?simulation=" + simulation.name());
            return new AnalysisReportResponse(id, mapper.readValue(analysis.getReportJson(), ReportSummary.class), issues, screenshots);
        } catch (IOException exception) { throw new IllegalStateException("Invalid persisted report", exception); }
    }

    @Transactional(readOnly = true)
    public HistoryResponse history(int page, int size, String url) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Use page >= 0 e size entre 1 e 100.");
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return HistoryResponse.from(url == null ? repository.findAll(pageable)
                : repository.findByUrl(urlValidator.validate(url), pageable));
    }
}
