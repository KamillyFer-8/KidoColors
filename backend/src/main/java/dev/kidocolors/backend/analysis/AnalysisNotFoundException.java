package dev.kidocolors.backend.analysis;

import java.util.UUID;

public class AnalysisNotFoundException extends RuntimeException {
    public AnalysisNotFoundException(UUID id) {
        super("Análise não encontrada: " + id);
    }
}
