package dev.kidocolors.backend.study;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/studies")
public class StudyController {
    private final StudyService service;
    public StudyController(StudyService service) { this.service = service; }
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<StudyResponse> create(@RequestParam String name, @RequestParam MultipartFile file) throws IOException {
        if (file.getSize() > DatasetCsv.MAX_BYTES) throw new IllegalArgumentException("O CSV deve ter no máximo 1 MiB.");
        StudyResponse response = service.create(name, file.getBytes());
        return ResponseEntity.created(URI.create("/api/studies/" + response.id())).body(response);
    }
    @GetMapping("/{id}") public StudyResponse get(@PathVariable UUID id) { return service.get(id); }
    @GetMapping(value = "/{id}/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> export(@PathVariable UUID id) {
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=study-" + id + ".csv")
                .body(StudyCsv.export(service.get(id)));
    }
    @GetMapping(value = "/{id}/dataset.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> dataset(@PathVariable UUID id) {
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=dataset-" + id + ".csv")
                .body(service.dataset(id));
    }
}
