package br.com.clicklance.repository;

import br.com.clicklance.model.PlayerUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerRepository {
    private static final String ELIGIBLE = """
            u.player_account AND u.active AND u.closed_at IS NULL AND NOT u.platform_admin
            AND NOT EXISTS (SELECT 1 FROM admin_company_membership m WHERE m.user_id=u.id)
            """;
    private final JdbcTemplate jdbc;
    public PlayerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockProvisioning() { jdbc.execute("SELECT pg_advisory_xact_lock(1947101503)"); }

    public boolean create(UUID id, String email, String hash) {
        return jdbc.update("INSERT INTO app_user(id,email,password_hash,player_account) VALUES (?,?,?,true) ON CONFLICT(email) DO NOTHING",
                id, email, hash) == 1;
    }

    public Optional<PlayerUser> lockByEmail(String email) {
        return jdbc.query("SELECT u.id,u.email,u.password_hash FROM app_user u WHERE u.email=? AND " + ELIGIBLE + " FOR UPDATE OF u",
                PlayerRepository::map, email).stream().findFirst();
    }

    public Optional<PlayerUser> lockById(UUID id) {
        return jdbc.query("SELECT u.id,u.email,u.password_hash FROM app_user u WHERE u.id=? AND " + ELIGIBLE + " FOR UPDATE OF u",
                PlayerRepository::map, id).stream().findFirst();
    }

    public void insertSession(String hash, UUID id, Instant now) {
        jdbc.update("INSERT INTO player_session(jti_hash,user_id,created_at,expires_at) VALUES (?,?,?,?)",
                hash, id, Timestamp.from(now), Timestamp.from(now.plusSeconds(3600)));
    }

    public Optional<PlayerUser> authenticated(String hash, UUID id, Instant now, Instant issued, Instant expires) {
        return jdbc.query("""
                SELECT u.id,u.email,u.password_hash FROM player_session s JOIN app_user u ON u.id=s.user_id
                WHERE s.jti_hash=? AND s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>?
                AND s.created_at=? AND s.expires_at=? AND
                """ + ELIGIBLE, PlayerRepository::map, hash, id, Timestamp.from(now), Timestamp.from(issued), Timestamp.from(expires))
                .stream().findFirst();
    }

    public void revoke(String hash, UUID id, Instant now) {
        jdbc.update("UPDATE player_session SET revoked_at=COALESCE(revoked_at,?) WHERE jti_hash=? AND user_id=?",
                Timestamp.from(now), hash, id);
    }

    public boolean currentSession(String hash, UUID id, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM player_session WHERE jti_hash=? AND user_id=? AND revoked_at IS NULL AND expires_at>?)",
                Boolean.class, hash, id, Timestamp.from(now)));
    }

    public void revokeAll(UUID id, Instant now) {
        jdbc.update("UPDATE player_session SET revoked_at=COALESCE(revoked_at,?) WHERE user_id=?", Timestamp.from(now), id);
    }

    public void createReset(String hash, UUID id, Instant now) {
        jdbc.update("INSERT INTO player_password_reset(token_hash,user_id,created_at,expires_at) VALUES (?,?,?,?)",
                hash, id, Timestamp.from(now), Timestamp.from(now.plusSeconds(900)));
    }

    public void deleteReset(String hash) { jdbc.update("DELETE FROM player_password_reset WHERE token_hash=?", hash); }

    public Optional<UUID> resetUser(String hash) {
        return jdbc.query("SELECT user_id FROM player_password_reset WHERE token_hash=?", (rs, row) -> rs.getObject(1, UUID.class), hash)
                .stream().findFirst();
    }

    public boolean consumeReset(String hash, UUID id, Instant now) {
        return jdbc.update("UPDATE player_password_reset SET consumed_at=? WHERE token_hash=? AND user_id=? AND consumed_at IS NULL AND expires_at>?",
                Timestamp.from(now), hash, id, Timestamp.from(now)) == 1;
    }

    public void consumeAllResets(UUID id, Instant now) {
        jdbc.update("UPDATE player_password_reset SET consumed_at=COALESCE(consumed_at,?) WHERE user_id=?", Timestamp.from(now), id);
    }

    public void changePassword(UUID id, String hash) { jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?", hash, id); }

    public void close(UUID id, String randomHash, Instant now) {
        jdbc.update("UPDATE app_user SET active=false,email=?,password_hash=?,closed_at=? WHERE id=?",
                "#closed:" + id, randomHash, Timestamp.from(now), id);
        jdbc.update("DELETE FROM player_password_reset WHERE user_id=?", id);
        jdbc.update("DELETE FROM player_session WHERE user_id=?", id);
    }

    private static PlayerUser map(ResultSet rs, int row) throws SQLException {
        return new PlayerUser(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"));
    }
}
