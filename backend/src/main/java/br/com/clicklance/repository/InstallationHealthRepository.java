package br.com.clicklance.repository;

import br.com.clicklance.model.InstallationHealth;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InstallationHealthRepository {
    private final JdbcTemplate jdbc;
    public InstallationHealthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void update(InstallationHealth h) {
        jdbc.update("""
            INSERT INTO installation_health VALUES (?,?,?,?,?,?,?,?)
            ON CONFLICT(dispositivo_id) DO UPDATE SET observed_at=EXCLUDED.observed_at,
            received_at=EXCLUDED.received_at,gateway_version=EXCLUDED.gateway_version,
            capture_state=EXCLUDED.capture_state,pending_events=EXCLUDED.pending_events,
            oldest_pending_age_seconds=EXCLUDED.oldest_pending_age_seconds,
            capture_progress_age_seconds=EXCLUDED.capture_progress_age_seconds
            WHERE EXCLUDED.observed_at > installation_health.observed_at
            """, h.dispositivo_id(), h.observed_at().atOffset(ZoneOffset.UTC), h.received_at().atOffset(ZoneOffset.UTC),
                h.gateway_version(), h.capture_state(), h.pending_events(), h.oldest_pending_age_seconds(), h.capture_progress_age_seconds());
    }
    public Optional<InstallationHealth> find(String id) {
        return jdbc.query("SELECT * FROM installation_health WHERE dispositivo_id=?", (r,n) -> new InstallationHealth(
                r.getString("dispositivo_id"), r.getObject("observed_at", OffsetDateTime.class).toInstant(),
                r.getObject("received_at", OffsetDateTime.class).toInstant(), r.getString("gateway_version"),
                r.getString("capture_state"), r.getInt("pending_events"), r.getLong("oldest_pending_age_seconds"),
                r.getObject("capture_progress_age_seconds", Long.class)), id).stream().findFirst();
    }
}
