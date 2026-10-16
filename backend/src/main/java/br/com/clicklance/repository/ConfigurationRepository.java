package br.com.clicklance.repository;

import br.com.clicklance.model.ConfigPublication;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ConfigurationRepository {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public ConfigurationRepository(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }

    public void lock(String deviceId) {
        jdbc.queryForObject("SELECT id FROM dispositivo WHERE id=? FOR UPDATE", String.class, deviceId);
    }

    @SuppressWarnings("unchecked")
    public Optional<ConfigPublication> current(String deviceId) {
        return jdbc.query("""
                SELECT c.* FROM device_configuration c JOIN device_configuration_current h
                  ON h.dispositivo_id=c.dispositivo_id AND h.version=c.version WHERE h.dispositivo_id=?
                """, (rs, row) -> new ConfigPublication(rs.getString("dispositivo_id"), rs.getString("version"),
                json.readValue(rs.getString("settings"), Map.class), rs.getObject("published_by", UUID.class),
                rs.getObject("published_at", OffsetDateTime.class).toInstant()), deviceId).stream().findFirst();
    }
    public void insert(ConfigPublication publication) {
        var version = UUID.fromString(publication.version());
        jdbc.update("INSERT INTO device_configuration VALUES (?, ?, ?::jsonb, ?, ?)", version, publication.dispositivo_id(),
                json.writeValueAsString(publication.settings()), publication.published_by(), OffsetDateTime.ofInstant(publication.published_at(), java.time.ZoneOffset.UTC));
        jdbc.update("""
                INSERT INTO device_configuration_current VALUES (?,?) ON CONFLICT(dispositivo_id)
                DO UPDATE SET version=EXCLUDED.version
                """, publication.dispositivo_id(), version);
    }
}
