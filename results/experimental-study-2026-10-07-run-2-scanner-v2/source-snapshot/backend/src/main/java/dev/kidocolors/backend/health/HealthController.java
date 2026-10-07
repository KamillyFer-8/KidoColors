package dev.kidocolors.backend.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Readiness of the API and database connection, not a test of browser availability. */
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping("/api/health")
    public Health health() {
        if (!Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class))) {
            throw new IllegalStateException("Database readiness check failed");
        }
        return new Health("UP");
    }
    public record Health(String status) { }
}
