package dev.kidocolors.backend.analysis;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
import dev.kidocolors.backend.scanner.PageCapture;
import dev.kidocolors.backend.scanner.CaptureStore;
import org.springframework.http.MediaType;
import java.io.IOException;
import dev.kidocolors.core.Simulation;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {
    private final AnalysisService service;
    private final CaptureStore captureStore;

    public AnalysisController(AnalysisService service, CaptureStore captureStore) {
        this.service = service;
        this.captureStore = captureStore;
    }

    @PostMapping
    public ResponseEntity<AnalysisResponse> create(@Valid @RequestBody CreateAnalysisRequest request) {
        AnalysisResponse analysis = service.create(request);
        return ResponseEntity.created(URI.create("/api/analyses/" + analysis.id())).body(analysis);
    }

    @GetMapping("/{id}")
    public AnalysisResponse get(@PathVariable UUID id) { return service.get(id); }

    @GetMapping("/{id}/capture")
    public PageCapture capture(@PathVariable UUID id) { return service.capture(id); }

    @GetMapping(value = "/{id}/screenshot", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> screenshot(@PathVariable UUID id, @RequestParam(required = false) Simulation simulation) {
        service.capture(id);
        if (simulation != null) service.report(id);
        try { return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(simulation == null ? captureStore.read(id) : captureStore.read(id, simulation)); }
        catch (IOException exception) { throw new CaptureNotFoundException(); }
    }

    @GetMapping("/{id}/report")
    public AnalysisReportResponse report(@PathVariable UUID id) { return service.report(id); }

    @GetMapping
    public HistoryResponse history(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return service.history(page, size, null);
    }

    @GetMapping("/by-url")
    public HistoryResponse byUrl(@RequestParam String url, @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return service.history(page, size, url);
    }
}
