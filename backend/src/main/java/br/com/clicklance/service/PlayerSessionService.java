package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.repository.PlayerRepository;
import br.com.clicklance.security.AdminPasswords;
import br.com.clicklance.security.PlayerJwt;
import br.com.clicklance.security.PlayerPrincipal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class PlayerSessionService {
    private final PlayerRepository players;
    private final PlayerJwt jwt;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final String dummyHash;

    public PlayerSessionService(PlayerRepository players, PlayerJwt jwt, PasswordEncoder passwords, Clock clock) {
        this.players = players; this.jwt = jwt; this.passwords = passwords; this.clock = clock;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Map<String, Object> login(String rawEmail, String password) {
        jwt.requireConfigured();
        String email = AdminPasswords.email(rawEmail);
        AdminPasswords.validate(password);
        var player = players.lockByEmail(email).orElse(null);
        boolean matches = passwords.matches(password, player == null ? dummyHash : player.passwordHash());
        if (player == null || !matches) throw new ApiException(401, "UNAUTHENTICATED");
        var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String jti = UUID.randomUUID().toString();
        String token = jwt.issue(player.id().toString(), jti, now);
        players.insertSession(AdminSessionService.hash(jti), player.id(), now);
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", 3600);
    }

    @Transactional(readOnly = true)
    public PlayerPrincipal authenticate(String token) {
        var claims = jwt.decode(token);
        UUID id;
        try { id = UUID.fromString(claims.getSubject()); }
        catch (IllegalArgumentException ignored) { throw new ApiException(401, "UNAUTHENTICATED"); }
        String hash = AdminSessionService.hash(claims.getId());
        var player = players.authenticated(hash, id, clock.instant(), claims.getIssuedAt(), claims.getExpiresAt())
                .orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED"));
        return new PlayerPrincipal(player.id(), player.email(), hash);
    }

    @Transactional
    public void logout(PlayerPrincipal principal) {
        requireCurrent(principal);
        players.revoke(principal.sessionHash(), principal.userId(), clock.instant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCurrent(PlayerPrincipal principal) {
        if (principal == null) throw new ApiException(401, "UNAUTHENTICATED");
        players.lockById(principal.userId()).orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED"));
        if (!players.currentSession(principal.sessionHash(), principal.userId(), clock.instant())) {
            throw new ApiException(401, "UNAUTHENTICATED");
        }
    }

    @Transactional
    public void requireActive(UUID userId) {
        players.lockById(userId).orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED"));
    }
}
