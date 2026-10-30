package br.com.clicklance.security;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Reutiliza assinatura/validação existentes com audiência exclusiva de jogadores. */
@Component
public class PlayerJwt {
    private final AdminJwt delegate;

    public PlayerJwt(@Value("${clicklance.admin.private-key-path}") String privatePath,
                     @Value("${clicklance.admin.public-key-path}") String publicPath,
                     @Value("${clicklance.admin.issuer}") String issuer,
                     @Value("${clicklance.admin.audience}") String adminAudience, Clock clock) {
        if ("clicklance-player".equals(adminAudience)) throw new IllegalStateException("Audiencias de jogadores e administradores devem ser distintas.");
        delegate = new AdminJwt(privatePath, publicPath, issuer, "clicklance-player", clock);
    }

    public void requireConfigured() { delegate.requireConfigured(); }
    public String issue(String userId, String jti, Instant now) { return delegate.issue(userId, jti, now); }
    public Jwt decode(String token) { return delegate.decode(token); }
}
