package br.com.clicklance.repository;

import br.com.clicklance.model.AdminUser;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminSessionRepository {
    private final JdbcTemplate jdbc;
    public AdminSessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(String jtiHash, UUID userId, Instant createdAt, Instant expiresAt) {
        jdbc.update("INSERT INTO admin_session(jti_hash,user_id,created_at,expires_at) VALUES (?,?,?,?)",
                jtiHash, userId, Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    public Optional<AdminUser> findValidUser(String jtiHash, UUID userId, Instant now, Instant issuedAt, Instant expiresAt) {
        return jdbc.query("""
                SELECT u.id,u.email,u.password_hash,u.active,u.platform_admin
                FROM admin_session s JOIN app_user u ON u.id=s.user_id
                WHERE s.jti_hash=? AND s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>?
                AND s.created_at=? AND s.expires_at=?
                """, AdminUserRepository::map, jtiHash, userId, Timestamp.from(now), Timestamp.from(issuedAt), Timestamp.from(expiresAt))
                .stream().findFirst();
    }

    public void revoke(String jtiHash, UUID userId, Instant now) {
        jdbc.update("UPDATE admin_session SET revoked_at=COALESCE(revoked_at,?) WHERE jti_hash=? AND user_id=?",
                Timestamp.from(now), jtiHash, userId);
    }
}
