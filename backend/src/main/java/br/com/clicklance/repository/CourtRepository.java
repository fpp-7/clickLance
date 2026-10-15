package br.com.clicklance.repository;

import br.com.clicklance.model.CaptureProfile;
import br.com.clicklance.model.Court;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourtRepository {
    private final JdbcTemplate jdbc;
    public CourtRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(Court court) {
        var profile = court.perfil_captura();
        jdbc.update("""
                INSERT INTO quadra(id,empresa_id,nome,active,timezone,replay_before_seconds,
                  replay_after_seconds,segment_duration_seconds,keyframe_interval) VALUES (?,?,?,?,?,?,?,?,?)
                """, court.quadra_id(), court.empresa_id(), court.nome(), court.active(), court.timezone(),
                profile.REPLAY_BEFORE_SECONDS(), profile.REPLAY_AFTER_SECONDS(),
                profile.SEGMENT_DURATION_SECONDS(), profile.KEYFRAME_INTERVAL());
    }

    public Optional<Court> findByCompanyAndId(String companyId, String id) {
        return jdbc.query("SELECT * FROM quadra WHERE empresa_id=? AND id=?", CourtRepository::map, companyId, id)
                .stream().findFirst();
    }

    public List<Court> findByCompany(String companyId, int size, long offset) {
        return jdbc.query("SELECT * FROM quadra WHERE empresa_id=? ORDER BY id LIMIT ? OFFSET ?",
                CourtRepository::map, companyId, size, offset);
    }

    public long countByCompany(String companyId) {
        return jdbc.queryForObject("SELECT count(*) FROM quadra WHERE empresa_id=?", Long.class, companyId);
    }

    private static Court map(ResultSet rs, int row) throws SQLException {
        return new Court(rs.getString("id"), rs.getString("empresa_id"), rs.getString("nome"), rs.getBoolean("active"),
                rs.getString("timezone"), new CaptureProfile(rs.getDouble("replay_before_seconds"), rs.getDouble("replay_after_seconds"),
                rs.getInt("segment_duration_seconds"), rs.getInt("keyframe_interval")));
    }
}
