package br.com.clicklance;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import br.com.clicklance.api.ApiException;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminTestSupport.TimeConfiguration.class)
class AdministrativeIdentityIT extends AdminTestSupport {
    static final String CURRENT = "/api/v1/admin/sessoes/atual";

    @Test void loginLogoutAndDurableHashedSession() throws Exception {
        UUID id = user(true);
        var response = request("POST", "/api/v1/admin/sessoes", null,
                Map.of("email", "  " + email(id).toUpperCase(Locale.ROOT) + "  ", "password", PASSWORD));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(body(response)).containsEntry("expires_in", 3600).containsEntry("token_type", "Bearer");
        String token = (String) body(response).get("access_token");
        var claims = jwt.decode(token);
        assertThat(claims.getExpiresAt()).isEqualTo(claims.getIssuedAt().plusSeconds(3600));
        assertThat(jdbc.queryForObject("SELECT jti_hash FROM admin_session WHERE user_id=?", String.class, id))
                .isEqualTo(br.com.clicklance.identity.AdminSessions.hash(claims.getId())).doesNotContain(claims.getId());
        var current = request("GET", CURRENT, token, null);
        assertThat(current.statusCode()).isEqualTo(200);
        assertThat(body(current)).containsEntry("usuario_id", id.toString()).containsEntry("platform_admin", true);
        assertThat(current.body()).doesNotContain(PASSWORD, PASSWORD_HASH, "sessionHash", "jti_hash");
        assertThat(request("DELETE", CURRENT, token, null).statusCode()).isEqualTo(204);
        assertThat(request("GET", CURRENT, token, null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM admin_session WHERE user_id=?", Boolean.class, id)).isTrue();
    }

    @Test void expirationIsExactAndUserInactivationIsImmediate() throws Exception {
        UUID id = user(true);
        String token = token(id);
        CLOCK.advance(3599);
        assertThat(request("GET", CURRENT, token, null).statusCode()).isEqualTo(200);
        CLOCK.advance(1);
        assertThat(request("GET", CURRENT, token, null).statusCode()).isEqualTo(401);
        CLOCK.reset();
        token = token(id);
        jdbc.update("UPDATE app_user SET active=false WHERE id=?", id);
        assertThat(request("GET", CURRENT, token, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/v1/admin/sessoes", null,
                Map.of("email", email(id), "password", PASSWORD)).statusCode()).isEqualTo(401);
    }

    @Test void invalidCredentialsSignatureAndPrivilegeInjectionAreRejected() throws Exception {
        UUID id = user(true);
        assertThat(request("GET", CURRENT, null, null).statusCode()).isEqualTo(401);
        var wrong = request("POST", "/api/v1/admin/sessoes", null,
                Map.of("email", email(id), "password", "wrong-password-long"));
        var absent = request("POST", "/api/v1/admin/sessoes", null,
                Map.of("email", "missing@example.invalid", "password", "wrong-password-long"));
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(body(wrong).get("message")).isEqualTo(body(absent).get("message"));
        assertThat(wrong.body()).doesNotContain(email(id), "wrong-password-long", PASSWORD_HASH);
        String token = token(id);
        int start = token.lastIndexOf('.') + 1;
        String modified = token.substring(0, start) + (token.charAt(start) == 'A' ? 'B' : 'A') + token.substring(start + 1);
        assertThat(request("GET", CURRENT, modified, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", CURRENT, "eyJhbGciOiJub25lIn0.e30.", null).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/v1/admin/sessoes", null,
                Map.of("email", email(id), "password", PASSWORD, "role", "PLATFORM_ADMIN")).statusCode()).isEqualTo(422);
        UUID ordinary = user(false);
        assertThat(request("POST", "/api/v1/admin/sessoes", null,
                Map.of("email", email(ordinary), "password", PASSWORD)).statusCode()).isEqualTo(403);
    }

    @Test void passwordByteLimitIsEnforcedBeforeBcrypt() throws Exception {
        UUID id = user(true);
        var response = request("POST", "/api/v1/admin/sessoes", null,
                Map.of("email", email(id), "password", "é".repeat(37)));
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).doesNotContain("é");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_session WHERE user_id=?", Integer.class, id)).isZero();
    }

    @Test void bootstrapIsRepeatableConcurrentAndNeverPromotesExistingAccount() throws Exception {
        String email = UUID.randomUUID() + "@example.invalid";
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<UUID> create = () -> { start.await(); return accounts.bootstrap(email, PASSWORD); };
            var first = pool.submit(create);
            var second = pool.submit(create);
            start.countDown();
            UUID id = first.get(20, TimeUnit.SECONDS);
            users.add(id);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(id);
            String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id);
            assertThat(accounts.bootstrap(email, "a-different-valid-password")).isEqualTo(id);
            assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id)).isEqualTo(hash);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email=?", Integer.class, email)).isEqualTo(1);
        }
        UUID ordinary = user(false);
        assertThatThrownBy(() -> accounts.bootstrap(email(ordinary), PASSWORD)).isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT platform_admin FROM app_user WHERE id=?", Boolean.class, ordinary)).isFalse();
    }

    @Test void normalStartupDoesNotProvisionAnImplicitAdministrator() {
        assertThat(environment.getActiveProfiles()).doesNotContain("bootstrap-admin");
        assertThat(context.getBeansOfType(br.com.clicklance.identity.BootstrapAdmin.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email='admin'", Integer.class)).isZero();
    }

    @Test void signedTokenStillRequiresIssuerAudienceIdentityAndPersistedSession() throws Exception {
        UUID id = user(true);
        String token = token(id);
        var original = com.nimbusds.jwt.SignedJWT.parse(token).getJWTClaimsSet();
        for (var claims : java.util.List.of(
                new com.nimbusds.jwt.JWTClaimsSet.Builder(original).issuer("other-issuer").build(),
                new com.nimbusds.jwt.JWTClaimsSet.Builder(original).audience("other-audience").build(),
                new com.nimbusds.jwt.JWTClaimsSet.Builder(original).claim("aud", null).build(),
                new com.nimbusds.jwt.JWTClaimsSet.Builder(original).jwtID(UUID.randomUUID().toString()).build())) {
            var signed = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256), claims);
            signed.sign(new com.nimbusds.jose.crypto.RSASSASigner(KEY_PAIR.getPrivate()));
            assertThat(request("GET", CURRENT, signed.serialize(), null).statusCode()).isEqualTo(401);
        }
        var hmac = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256), original);
        hmac.sign(new com.nimbusds.jose.crypto.MACSigner(new byte[64]));
        assertThat(request("GET", CURRENT, hmac.serialize(), null).statusCode()).isEqualTo(401);
    }
}
