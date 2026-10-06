package dev.kidocolors.backend.study;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface StudyRepository extends JpaRepository<StudyRun, UUID> { }
