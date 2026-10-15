package br.com.clicklance.identity;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.clicklance.api.ApiException;

/** Provisionamento operacional; nunca exposto como cadastro publico. */
@Service
public class AdminAccounts {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AdminAccounts(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @Transactional
    public UUID bootstrap(String rawEmail, String password) {
        String email = AdminPasswords.email(rawEmail);
        AdminPasswords.validate(password);
        // Serializa inclusive chamadas concorrentes em processos distintos.
        jdbc.execute("SELECT pg_advisory_xact_lock(1947101503)");
        var users = jdbc.query("SELECT id, platform_admin, active FROM app_user WHERE email = ? FOR UPDATE",
                (rs, row) -> new Existing(rs.getObject("id", UUID.class), rs.getBoolean("platform_admin"), rs.getBoolean("active")), email);
        if (!users.isEmpty()) {
            var user = users.getFirst();
            if (!user.platformAdmin() || !user.active()) throw new ApiException(409, "IDENTITY_CONFLICT");
            return user.id(); // Nao troca senha, reativa ou promove uma identidade existente.
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,email,password_hash,platform_admin) VALUES (?,?,?,true)",
                id, email, passwords.encode(password));
        return id;
    }

    @Transactional
    public UUID provisionCompanyAdmin(AdminPrincipal operator, String rawEmail, String password, String empresaId) {
        operator.requirePlatform();
        String email = AdminPasswords.email(rawEmail);
        AdminPasswords.validate(password);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM empresa WHERE id=?)", Boolean.class, empresaId))) {
            throw new ApiException(404, "RESOURCE_NOT_FOUND");
        }
        jdbc.execute("SELECT pg_advisory_xact_lock(1947101503)");
        UUID candidate = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,email,password_hash) VALUES (?,?,?) ON CONFLICT(email) DO NOTHING",
                candidate, email, passwords.encode(password));
        var user = jdbc.queryForObject("SELECT id, platform_admin, active FROM app_user WHERE email=? FOR UPDATE",
                (rs, row) -> new Existing(rs.getObject("id", UUID.class), rs.getBoolean("platform_admin"), rs.getBoolean("active")), email);
        if (user == null || !user.active()) throw new ApiException(409, "IDENTITY_CONFLICT");
        // Repetir mantem senha e estado do vinculo; reativacao exige operacao propria.
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?) ON CONFLICT DO NOTHING", user.id(), empresaId);
        return user.id();
    }

    private record Existing(UUID id, boolean platformAdmin, boolean active) {}
}
