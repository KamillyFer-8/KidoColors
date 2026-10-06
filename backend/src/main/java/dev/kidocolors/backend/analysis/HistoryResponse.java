package dev.kidocolors.backend.analysis;

import org.springframework.data.domain.Page;
import java.util.List;

public record HistoryResponse(List<AnalysisResponse> items, int page, int size,
                              long totalItems, int totalPages) {
    static HistoryResponse from(Page<Analysis> result) {
        return new HistoryResponse(result.getContent().stream().map(AnalysisResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
