package dev.kidocolors.backend.scanner;

import java.util.UUID;

public interface PageScanner {
    PageCapture scan(String url, UUID analysisId);
}
