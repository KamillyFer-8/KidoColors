package dev.kidocolors.backend.analysis;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {
    Page<Analysis> findByUrl(String url, Pageable pageable);
}
