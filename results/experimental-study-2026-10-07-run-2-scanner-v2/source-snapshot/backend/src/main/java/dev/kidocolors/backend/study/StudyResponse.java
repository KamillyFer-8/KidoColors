package dev.kidocolors.backend.study;

import java.time.Instant;
import java.util.*;

public record StudyResponse(UUID id, String name, String datasetSha256, String status,
        Instant startedAt, Instant finishedAt, Long durationMs, int totalRows, String errorMessage,
        Map<String, Object> environment, StudyMetrics metrics, Map<String, StudyMetrics> categories, List<StudyRow> rows) { }
