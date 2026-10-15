package br.com.clicklance.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.clicklance.api.ApiException;

@Service
public class AdminSessions {
    private final JdbcTemplate jdbc;
    private final AdminJwt jwt;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final String dummyHash;
    public AdminSessions(JdbcTemplate jdbc, AdminJwt jwt, PasswordEncoder passwords, Clock clock) {
        this.jdbc = jdbc;
        this.jwt = jwt;
        this.passwords = passwords;
        this.clock = clock;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Map<String, Object> login(String rawEmail, String password) {
        jwt.requireConfigured();
        String email = AdminPasswords.email(rawEmail);
        AdminPasswords.validate(password);
        var users = jdbc.query("SELECT id,email,password_hash,active,platform_admin FROM app_user WHERE email=? FOR UPDATE",
                (rs, row) -> new User(rs.getObject("id", UUID.class), rs.getString("email"),
                        rs.getString("password_hash"), rs.getBoolean("active"), rs.getBoolean("platform_admin")), email);
        var user = users.isEmpty() ? null : users.getFirst();
        boolean matches = passwords.matches(password, user == null ? dummyHash : user.hash());
        if (user == null || !matches || !user.active()) throw new ApiException(401, "UNAUTHENTICATED");
        if (!user.platformAdmin() && memberships(user.id()).isEmpty()) throw new ApiException(403, "FORBIDDEN");
        String jti = UUID.randomUUID().toString();
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String token = jwt.issue(user.id().toString(), jti, now);
        jdbc.update("INSERT INTO admin_session(jti_hash,user_id,created_at,expires_at) VALUES (?,?,?,?)",
                hash(jti), user.id(), Timestamp.from(now), Timestamp.from(now.plusSeconds(3600)));
        // O proxy transacional confirma commit antes de entregar o resultado ao controller.
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", 3600);
    }

    @Transactional(readOnly = true)
    public AdminPrincipal authenticate(String token) {
        var claims = jwt.decode(token);
        UUID id;
        try { id = UUID.fromString(claims.getSubject()); }
        catch (IllegalArgumentException ignored) { throw new ApiException(401, "UNAUTHENTICATED"); }
        String jtiHash = hash(claims.getId());
        var users = jdbc.query("""
                SELECT u.id,u.email,u.password_hash,u.active,u.platform_admin
                FROM admin_session s JOIN app_user u ON u.id=s.user_id
                WHERE s.jti_hash=? AND s.user_id=? AND s.revoked_at IS NULL AND s.expires_at>?
                AND s.created_at=? AND s.expires_at=?
                """, (rs, row) -> new User(rs.getObject("id", UUID.class), rs.getString("email"),
                rs.getString("password_hash"), rs.getBoolean("active"), rs.getBoolean("platform_admin")),
                jtiHash, id, Timestamp.from(clock.instant()), Timestamp.from(claims.getIssuedAt()), Timestamp.from(claims.getExpiresAt()));
        if (users.isEmpty() || !users.getFirst().active()) throw new ApiException(401, "UNAUTHENTICATED");
        var user = users.getFirst();
        var companies = new HashSet<>(memberships(user.id()));
        if (!user.platformAdmin() && companies.isEmpty()) throw new ApiException(403, "FORBIDDEN");
        return new AdminPrincipal(user.id(), user.email(), user.platformAdmin(), companies, jtiHash);
    }

    @Transactional
    public void logout(AdminPrincipal principal) {
        jdbc.update("UPDATE admin_session SET revoked_at=COALESCE(revoked_at,?) WHERE jti_hash=? AND user_id=?",
                Timestamp.from(clock.instant()), principal.sessionHash(), principal.userId());
    }

    private java.util.List<String> memberships(UUID id) {
        return jdbc.queryForList("SELECT empresa_id FROM admin_company_membership WHERE user_id=? AND active AND role='COMPANY_ADMIN' ORDER BY empresa_id", String.class, id);
    }

    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record User(UUID id, String email, String hash, boolean active, boolean platformAdmin) {}
}
