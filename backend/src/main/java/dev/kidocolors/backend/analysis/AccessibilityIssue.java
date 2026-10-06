package dev.kidocolors.backend.analysis;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "accessibility_issue", indexes = @Index(name = "idx_issue_analysis", columnList = "analysis_id"))
public class AccessibilityIssue {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;
    @Column(nullable = false, length = 40) private String type;
    @Column(nullable = false, length = 20) private String severity;
    @Column(length = 20) private String simulation;
    @Column(name = "detail_json", nullable = false, columnDefinition = "text") private String detailJson;
    @Column(name = "issue_order", nullable = false) private int position;

    protected AccessibilityIssue() { }
    AccessibilityIssue(Analysis analysis, IssueDetail detail, String json, int position) {
        id = UUID.randomUUID();
        this.analysis = analysis;
        type = detail.type().name();
        severity = detail.severity().name();
        simulation = detail.simulation();
        detailJson = json;
        this.position = position;
    }
    public UUID getId() { return id; }
    public String getDetailJson() { return detailJson; }
}
