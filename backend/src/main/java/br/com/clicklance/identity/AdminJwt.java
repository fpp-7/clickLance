package br.com.clicklance.identity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import br.com.clicklance.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class AdminJwt {
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final String issuer;
    private final String audience;

    public AdminJwt(@Value("${clicklance.admin.private-key-path}") String privatePath,
                    @Value("${clicklance.admin.public-key-path}") String publicPath,
                    @Value("${clicklance.admin.issuer}") String issuer,
                    @Value("${clicklance.admin.audience}") String audience, Clock clock) {
        this.issuer = issuer;
        this.audience = audience;
        if (privatePath.isBlank() && publicPath.isBlank()) {
            encoder = null;
            decoder = null;
            return;
        }
        try {
            var factory = KeyFactory.getInstance("RSA");
            var privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pem(privatePath)));
            var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(pem(publicPath)));
            if (!publicKey.getModulus().equals(privateKey.getModulus()) || publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException();
            }
            var jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("admin-v1").build();
            encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
            decoder = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(issuer), jwt -> {
                Instant now = clock.instant();
                boolean valid = jwt.getSubject() != null && jwt.getId() != null && jwt.getIssuedAt() != null
                        && jwt.getExpiresAt() != null && jwt.getAudience() != null && jwt.getAudience().contains(audience)
                        && !jwt.getIssuedAt().isAfter(now) && jwt.getExpiresAt().isAfter(now)
                        && jwt.getExpiresAt().equals(jwt.getIssuedAt().plusSeconds(3600))
                        && (jwt.getNotBefore() == null || !jwt.getNotBefore().isAfter(now));
                return valid ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }));
        } catch (Exception ignored) {
            throw new IllegalStateException("ADMIN_JWT: configure o par RSA PEM PKCS8/X509 correspondente, com ao menos 2048 bits.");
        }
    }

    private static byte[] pem(String path) throws Exception {
        return Base64.getDecoder().decode(Files.readString(Path.of(path))
                .replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));
    }

    public void requireConfigured() {
        if (encoder == null) throw new ApiException(503, "DEPENDENCY_UNAVAILABLE");
    }

    public String issue(String userId, String jti, Instant now) {
        requireConfigured();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(userId)
                .id(jti).issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        try {
            return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                    .getTokenValue();
        } catch (JwtEncodingException ignored) {
            throw new ApiException(503, "DEPENDENCY_UNAVAILABLE");
        }
    }

    public Jwt decode(String token) {
        requireConfigured();
        try { return decoder.decode(token); }
        catch (JwtException | IllegalArgumentException ignored) { throw new ApiException(401, "UNAUTHENTICATED"); }
    }
}
