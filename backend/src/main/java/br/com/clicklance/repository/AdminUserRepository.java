package br.com.clicklance.repository;

import br.com.clicklance.model.AdminUser;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserRepository {
    private final JdbcTemplate jdbc;
    public AdminUserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockProvisioning() {
        // Lock transacional compartilhado inclusive entre processos distintos.
        jdbc.execute("SELECT pg_advisory_xact_lock(1947101503)");
    }

    public Optional<AdminUser> findByEmailForUpdate(String email) {
        return jdbc.query("SELECT id,email,password_hash,active,platform_admin FROM app_user WHERE email=? FOR UPDATE",
                AdminUserRepository::map, email).stream().findFirst();
    }

    public void insertPlatformAdmin(UUID id, String email, String passwordHash) {
        jdbc.update("INSERT INTO app_user(id,email,password_hash,platform_admin) VALUES (?,?,?,true)", id, email, passwordHash);
    }

    public void insertCompanyAdminIfAbsent(UUID id, String email, String passwordHash) {
        jdbc.update("INSERT INTO app_user(id,email,password_hash) VALUES (?,?,?) ON CONFLICT(email) DO NOTHING",
                id, email, passwordHash);
    }

    static AdminUser map(ResultSet rs, int row) throws SQLException {
        return new AdminUser(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"),
                rs.getBoolean("active"), rs.getBoolean("platform_admin"));
    }
}
