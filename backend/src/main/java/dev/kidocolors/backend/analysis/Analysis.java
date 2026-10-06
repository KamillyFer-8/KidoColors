package dev.kidocolors.backend.analysis;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "analysis", indexes = {
        @Index(name = "idx_analysis_created_at", columnList = "created_at"),
        @Index(name = "idx_analysis_url", columnList = "url")})
public class Analysis {
    @Id
    private UUID id;
    @Column(nullable = false, length = 2048)
    private String url;
    @Column(length = 120)
    private String category;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_run_id")
    private dev.kidocolors.backend.study.StudyRun studyRun;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "duration_ms")
    private Long durationMs;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;
    private Integer score;
    @Column(name = "elements_analyzed")
    private Integer elementsAnalyzed;
    @Column(name = "total_issues")
    private Integer totalIssues;
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
    @Column(name = "error_code", length = 40)
    private String errorCode;
    @Column(name = "capture_json", columnDefinition = "text")
    private String captureJson;
    @Column(name = "elements_collected")
    private Integer elementsCollected;
    @Column(name = "report_json", columnDefinition = "text") private String reportJson;
    @Column(name = "contrast_failures") private Integer contrastFailures;
    @Column(name = "protanopia_warnings") private Integer protanopiaWarnings;
    @Column(name = "deuteranopia_warnings") private Integer deuteranopiaWarnings;
    @Column(name = "tritanopia_warnings") private Integer tritanopiaWarnings;
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<AccessibilityIssue> issues = new ArrayList<>();

    protected Analysis() { }

    static Analysis pending(String url, String category, Instant now) {
        Analysis analysis = new Analysis();
        analysis.id = UUID.randomUUID();
        analysis.url = url;
        analysis.category = category;
        analysis.createdAt = now;
        analysis.status = AnalysisStatus.PENDING;
        return analysis;
    }

    public UUID getId() { return id; }
    public dev.kidocolors.backend.study.StudyRun getStudyRun() { return studyRun; }
    void associateStudy(dev.kidocolors.backend.study.StudyRun run) { studyRun = run; }
    public String getUrl() { return url; }
    public String getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public AnalysisStatus getStatus() { return status; }
    public Integer getScore() { return score; }
    public Integer getElementsAnalyzed() { return elementsAnalyzed; }
    public Integer getTotalIssues() { return totalIssues; }
    public String getErrorMessage() { return errorMessage; }
    public String getErrorCode() { return errorCode; }
    public String getCaptureJson() { return captureJson; }
    public Integer getElementsCollected() { return elementsCollected; }
    public String getReportJson() { return reportJson; }
    public Integer getContrastFailures() { return contrastFailures; }
    public Integer getProtanopiaWarnings() { return protanopiaWarnings; }
    public Integer getDeuteranopiaWarnings() { return deuteranopiaWarnings; }
    public Integer getTritanopiaWarnings() { return tritanopiaWarnings; }
    public List<AccessibilityIssue> getIssues() { return issues; }

    void complete(ReportSummary summary, String summaryJson, List<AccessibilityIssue> findings) {
        reportJson = summaryJson;
        score = summary.score();
        elementsAnalyzed = summary.elementsEvaluated();
        totalIssues = findings.size();
        contrastFailures = summary.contrastFailures();
        protanopiaWarnings = summary.protanopiaWarnings();
        deuteranopiaWarnings = summary.deuteranopiaWarnings();
        tritanopiaWarnings = summary.tritanopiaWarnings();
        issues.addAll(findings);
        status = AnalysisStatus.COMPLETED;
    }

    void start() { status = AnalysisStatus.RUNNING; }
    void scanned(String captureJson, int count) {
        this.captureJson = captureJson;
        elementsCollected = count;
        status = AnalysisStatus.SCANNED;
    }
    void fail(String code, String message) {
        status = AnalysisStatus.FAILED;
        errorCode = code;
        errorMessage = message;
    }
    void finish(long durationMs, Instant finishedAt) {
        this.durationMs = durationMs;
        this.finishedAt = finishedAt;
    }
}
