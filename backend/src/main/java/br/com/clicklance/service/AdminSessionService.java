package br.com.clicklance.service;

import br.com.clicklance.model.AuditAction;
import br.com.clicklance.security.AdminJwt;
import br.com.clicklance.repository.AdminMembershipRepository;
import br.com.clicklance.security.AdminPasswords;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.repository.AdminSessionRepository;
import br.com.clicklance.repository.AdminUserRepository;
import br.com.clicklance.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSessionService {
    private final AdminUserRepository users;
    private final AdminSessionRepository sessions;
    private final AdminMembershipRepository memberships;
    private final AdminJwt jwt;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final String dummyHash;
    private final AuditService audit;
    public AdminSessionService(AdminUserRepository users, AdminSessionRepository sessions, AdminMembershipRepository memberships,
                         AdminJwt jwt, PasswordEncoder passwords, Clock clock, AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.memberships = memberships;
        this.jwt = jwt;
        this.passwords = passwords;
        this.clock = clock;
        this.audit = audit;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Map<String, Object> login(String rawEmail, String password) {
        jwt.requireConfigured();
        String email = AdminPasswords.email(rawEmail);
        AdminPasswords.validate(password);
        var user = users.findByEmailForUpdate(email).orElse(null);
        boolean matches = passwords.matches(password, user == null ? dummyHash : user.passwordHash());
        if (user == null || !matches || !user.active()) throw new ApiException(401, "UNAUTHENTICATED");
        if (!user.platformAdmin() && memberships.findActiveCompanyIds(user.id()).isEmpty()) throw new ApiException(403, "FORBIDDEN");
        String jti = UUID.randomUUID().toString();
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String token = jwt.issue(user.id().toString(), jti, now);
        sessions.insert(hash(jti), user.id(), now, now.plusSeconds(3600));
        audit.success(AuditAction.ADMIN_LOGIN, user.id(), null, user.id().toString());
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
        var user = sessions.findValidUser(jtiHash, id, clock.instant(), claims.getIssuedAt(), claims.getExpiresAt())
                .orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED"));
        if (!user.active()) throw new ApiException(401, "UNAUTHENTICATED");
        var companies = new HashSet<>(memberships.findActiveCompanyIds(user.id()));
        if (!user.platformAdmin() && companies.isEmpty()) throw new ApiException(403, "FORBIDDEN");
        return new AdminPrincipal(user.id(), user.email(), user.platformAdmin(), companies, jtiHash);
    }

    @Transactional
    public void logout(AdminPrincipal principal) {
        sessions.revoke(principal.sessionHash(), principal.userId(), clock.instant());
        audit.success(AuditAction.ADMIN_LOGOUT, principal.userId(), null, principal.userId().toString());
    }

    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

}
