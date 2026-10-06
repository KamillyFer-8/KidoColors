package dev.kidocolors.backend.study;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "study_run")
public class StudyRun {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 64) private String datasetSha256;
    @Column(nullable = false, columnDefinition = "text") private String datasetCsv;
    @Column(nullable = false, columnDefinition = "text") private String rowsJson;
    @Column(nullable = false, columnDefinition = "text") private String environmentJson;
    @Column(nullable = false) private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int totalRows;
    @Column(length = 2000) private String errorMessage;
    protected StudyRun() { }
    static StudyRun start(String name, DatasetCsv.Dataset dataset, String environmentJson) {
        StudyRun run = new StudyRun();
        run.id = UUID.randomUUID(); run.name = name; run.datasetSha256 = dataset.sha256(); run.datasetCsv = dataset.csv();
        run.rowsJson = "[]"; run.environmentJson = environmentJson; run.startedAt = Instant.now(); run.status = "RUNNING"; run.totalRows = dataset.rows().size();
        return run;
    }
    void checkpoint(String rowsJson) { this.rowsJson = rowsJson; }
    void complete() { status = "COMPLETED"; }
    void fail() { status = "FAILED"; errorMessage = "O lote foi interrompido por uma falha interna. Consulte os registros processados e os logs."; }
    void finish(long durationMs) { this.durationMs = durationMs; finishedAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDatasetSha256() { return datasetSha256; }
    public String getDatasetCsv() { return datasetCsv; }
    public String getRowsJson() { return rowsJson; }
    public String getEnvironmentJson() { return environmentJson; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public String getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public String getErrorMessage() { return errorMessage; }
}
