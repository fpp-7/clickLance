package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.repository.PlayerRepository;
import br.com.clicklance.security.AdminPasswords;
import br.com.clicklance.security.DeviceTokens;
import br.com.clicklance.security.PlayerPrincipal;
import br.com.clicklance.security.CorrelationContext;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerAccountService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PlayerAccountService.class);
    private final PlayerRepository players;
    private final PasswordEncoder passwords;
    private final DeviceTokens tokens;
    private final Clock clock;
    private final RecoveryMailService mail;
    private final LibraryService library;

    public PlayerAccountService(PlayerRepository players, PasswordEncoder passwords, DeviceTokens tokens,
                                Clock clock, RecoveryMailService mail, LibraryService library) {
        this.players = players; this.passwords = passwords; this.tokens = tokens;
        this.clock = clock; this.mail = mail; this.library = library;
    }

    @Transactional
    public Map<String, Object> register(String rawEmail, String password) {
        String email = AdminPasswords.email(rawEmail);
        AdminPasswords.validate(password);
        String passwordHash = passwords.encode(password);
        players.lockProvisioning();
        UUID id = UUID.randomUUID();
        if (!players.create(id, email, passwordHash)) throw new ApiException(409, "IDENTITY_CONFLICT");
        return Map.of("usuario_id", id.toString(), "email", email);
    }

    @Transactional
    public void requestReset(String rawEmail) {
        String email = AdminPasswords.email(rawEmail);
        // Verificação independente da existência da conta; SMTP ausente nunca enumera usuários.
        mail.requireConfigured();
        players.lockProvisioning();
        var player = players.lockByEmail(email).orElse(null);
        if (player == null) return;
        String token = tokens.issue();
        String tokenHash = tokens.hash(token);
        players.createReset(tokenHash, player.id(), clock.instant());
        try { mail.send(player.email(), token); }
        catch (ApiException unavailable) {
            if (unavailable.status() != 503) throw unavailable;
            // Não entregar resposta distinta para contas existentes durante falha SMTP.
            players.deleteReset(tokenHash);
            LOG.warn("player_recovery_delivery_failed correlation_id={} code=DEPENDENCY_UNAVAILABLE", CorrelationContext.currentOrNew());
        }
    }

    @Transactional
    public void resetPassword(String token, String password) {
        AdminPasswords.validate(password);
        if (!tokens.validFormat(token)) throw new ApiException(422, "INVALID_RESET_TOKEN");
        String hash = tokens.hash(token);
        players.lockProvisioning();
        UUID id = players.resetUser(hash).orElseThrow(() -> new ApiException(422, "INVALID_RESET_TOKEN"));
        players.lockById(id).orElseThrow(() -> new ApiException(422, "INVALID_RESET_TOKEN"));
        var now = clock.instant();
        if (!players.consumeReset(hash, id, now)) throw new ApiException(422, "INVALID_RESET_TOKEN");
        players.changePassword(id, passwords.encode(password));
        players.consumeAllResets(id, now);
        players.revokeAll(id, now);
    }

    @Transactional
    public void close(PlayerPrincipal principal) {
        players.lockProvisioning();
        players.lockById(principal.userId()).orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED"));
        if (!players.currentSession(principal.sessionHash(), principal.userId(), clock.instant())) throw new ApiException(401, "UNAUTHENTICATED");
        // Biblioteca usa a mesma ordem user -> replays. Tudo confirma ou reverte junto.
        library.removeAllForAccount(principal.userId());
        players.close(principal.userId(), passwords.encode(tokens.issue()), clock.instant());
    }
}
