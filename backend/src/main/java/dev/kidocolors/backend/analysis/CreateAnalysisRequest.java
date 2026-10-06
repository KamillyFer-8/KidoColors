package dev.kidocolors.backend.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAnalysisRequest(
        @NotBlank(message = "Informe uma URL.")
        @Size(max = 2048, message = "A URL deve ter no máximo 2048 caracteres.") String url,
        @Size(max = 120, message = "A categoria deve ter no máximo 120 caracteres.") String category) { }
