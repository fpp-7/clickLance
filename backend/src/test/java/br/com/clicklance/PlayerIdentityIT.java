package br.com.clicklance;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.security.PlayerJwt;
import br.com.clicklance.service.RecoveryMailService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminTestSupport.TimeConfiguration.class)
class PlayerIdentityIT extends AdminTestSupport {
    private static final String BASE = "/api/v1/jogadores";
    private static final String NEW_PASSWORD = "replacement-fixture-only-" + UUID.randomUUID();
    private final List<UUID> playerIds = new CopyOnWriteArrayList<>();
    private final List<String> delivered = new CopyOnWriteArrayList<>();
    @MockitoBean RecoveryMailService mail;
    @Autowired PlayerJwt playerJwt;

    @BeforeEach void captureMail() {
        reset(mail);
        delivered.clear();
        doAnswer(invocation -> { delivered.add(invocation.getArgument(1)); return null; }).when(mail).send(anyString(), anyString());
    }

    @AfterEach void removePlayers() {
        for (UUID id : playerIds) {
            jdbc.update("DELETE FROM player_password_reset WHERE user_id=?", id);
            jdbc.update("DELETE FROM player_session WHERE user_id=?", id);
            jdbc.update("DELETE FROM library_entry WHERE user_id=?", id);
            jdbc.update("DELETE FROM admin_company_membership WHERE user_id=?", id);
            jdbc.update("DELETE FROM app_user WHERE id=?", id);
        }
    }

    String signup() throws Exception {
        String email = UUID.randomUUID() + "@example.invalid";
        var response = request("POST", BASE, null, Map.of("email", email, "password", PASSWORD));
        assertThat(response.statusCode()).isEqualTo(201);
        playerIds.add(UUID.fromString((String) body(response).get("usuario_id")));
        return email;
    }

    String playerToken(String email) throws Exception {
        var response = request("POST", BASE + "/sessoes", null, Map.of("email", email, "password", PASSWORD));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        return (String) body(response).get("access_token");
    }

    @Test void concurrentNormalizedSignupCreatesOneGlobalPlayerWithoutPrivilege() throws Exception {
        String email = UUID.randomUUID() + "@example.invalid";
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> { start.await(); return request("POST", BASE, null,
                    Map.of("email", "  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", "password", PASSWORD)); });
            var b = pool.submit(() -> { start.await(); return request("POST", BASE, null, Map.of("email", email, "password", PASSWORD)); });
            start.countDown();
            var responses = List.of(a.get(), b.get());
            assertThat(responses.stream().map(response -> response.statusCode()).toList()).containsExactlyInAnyOrder(201, 409);
            var success = responses.stream().filter(response -> response.statusCode() == 201).findFirst().orElseThrow();
            var id = UUID.fromString((String) body(success).get("usuario_id"));
            playerIds.add(id);
            assertThat(body(success)).containsExactlyInAnyOrderEntriesOf(Map.of("usuario_id", id.toString(), "email", email));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email=?", Long.class, email)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT platform_admin FROM app_user WHERE id=?", Boolean.class, id)).isFalse();
        }
        var injected = request("POST", BASE, null, Map.of("email", "nobody@example.invalid", "password", PASSWORD, "role", "PLATFORM_ADMIN"));
        assertThat(injected.statusCode()).isEqualTo(422);
        assertThat(request("POST", BASE, null, Map.of("email", "nobody@example.invalid", "password", "é".repeat(37))).statusCode()).isEqualTo(422);
        String player = playerToken(email);
        assertThat(request("GET", "/api/v1/admin/empresas", player, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", "/api/v1/admin/sessoes", null, Map.of("email", email, "password", PASSWORD)).statusCode()).isEqualTo(403);
    }

    @Test void playerSessionsHaveSeparateAudienceLogoutAndExactExpiry() throws Exception {
        String email = signup(), token = playerToken(email);
        var claims = playerJwt.decode(token);
        assertThat(claims.getAudience()).containsExactly("clicklance-player");
        assertThat(claims.getExpiresAt()).isEqualTo(claims.getIssuedAt().plusSeconds(3600));
        var current = request("GET", BASE + "/sessoes/atual", token, null);
        assertThat(current.statusCode()).isEqualTo(200);
        assertThat(body(current)).containsOnlyKeys("usuario_id", "email");
        String admin = token(user(true));
        assertThat(request("GET", BASE + "/sessoes/atual", admin, null).statusCode()).isEqualTo(401);
        assertThat(request("DELETE", BASE + "/sessoes/atual", token, null).statusCode()).isEqualTo(204);
        assertThat(request("GET", BASE + "/sessoes/atual", token, null).statusCode()).isEqualTo(401);
        String expiring = playerToken(email);
        CLOCK.advance(3600);
        assertThat(request("GET", BASE + "/sessoes/atual", expiring, null).statusCode()).isEqualTo(401);
        CLOCK.reset();
        jdbc.update("UPDATE app_user SET active=false WHERE email=?", email);
        assertThat(request("POST", BASE + "/sessoes", null, Map.of("email", email, "password", PASSWORD)).statusCode()).isEqualTo(401);
    }

    @Test void recoveryIsGenericProtectsTokenAndRevokesEveryPreviousSession() throws Exception {
        String email = signup(), token = playerToken(email), another = playerToken(email);
        var known = request("POST", BASE + "/recuperacao", null, Map.of("email", email));
        var absent = request("POST", BASE + "/recuperacao", null, Map.of("email", UUID.randomUUID() + "@example.invalid"));
        assertThat(known.statusCode()).isEqualTo(202);
        assertThat(absent.statusCode()).isEqualTo(202);
        assertThat(body(known)).isEqualTo(body(absent));
        assertThat(delivered).hasSize(1);
        String reset = delivered.getFirst();
        assertThat(reset).matches("[A-Za-z0-9_-]{43}");
        String stored = jdbc.queryForObject("SELECT token_hash FROM player_password_reset WHERE user_id=?", String.class, playerIds.getFirst());
        assertThat(stored).isNotEqualTo(reset).matches("[a-f0-9]{64}");
        assertThat(known.body()).doesNotContain(email, reset);
        assertThat(request("POST", BASE + "/redefinicao", null, Map.of("token", reset, "password", NEW_PASSWORD)).statusCode()).isEqualTo(204);
        assertThat(request("GET", BASE + "/sessoes/atual", token, null).statusCode()).isEqualTo(401);
        assertThat(request("GET", BASE + "/sessoes/atual", another, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", BASE + "/sessoes", null, Map.of("email", email, "password", PASSWORD)).statusCode()).isEqualTo(401);
        assertThat(request("POST", BASE + "/sessoes", null, Map.of("email", email, "password", NEW_PASSWORD)).statusCode()).isEqualTo(201);
        assertThat(request("POST", BASE + "/redefinicao", null, Map.of("token", reset, "password", PASSWORD)).statusCode()).isEqualTo(422);
    }

    @Test void resetExpiresAtFifteenMinutesAndConcurrentConsumptionHasOneWinner() throws Exception {
        String email = signup();
        request("POST", BASE + "/recuperacao", null, Map.of("email", email));
        String expired = delivered.getFirst();
        CLOCK.advance(900);
        assertThat(request("POST", BASE + "/redefinicao", null, Map.of("token", expired, "password", NEW_PASSWORD)).statusCode()).isEqualTo(422);
        request("POST", BASE + "/recuperacao", null, Map.of("email", email));
        String resetToken = delivered.getLast();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> { start.await(); return request("POST", BASE + "/redefinicao", null, Map.of("token", resetToken, "password", NEW_PASSWORD)); });
            var b = pool.submit(() -> { start.await(); return request("POST", BASE + "/redefinicao", null, Map.of("token", resetToken, "password", NEW_PASSWORD)); });
            start.countDown();
            assertThat(List.of(a.get().statusCode(), b.get().statusCode())).containsExactlyInAnyOrder(204, 422);
        }
    }

    @Test void smtpFailureInvalidatesUndeliveredTokenAndKeepsGenericResponse() throws Exception {
        String email = signup();
        doThrow(new ApiException(503, "DEPENDENCY_UNAVAILABLE")).when(mail).send(anyString(), anyString());
        var failed = request("POST", BASE + "/recuperacao", null, Map.of("email", email));
        var absent = request("POST", BASE + "/recuperacao", null, Map.of("email", "absent@example.invalid"));
        assertThat(failed.statusCode()).isEqualTo(202);
        assertThat(absent.statusCode()).isEqualTo(202);
        assertThat(body(failed)).isEqualTo(body(absent));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player_password_reset WHERE user_id=?", Long.class, playerIds.getFirst())).isZero();
        doThrow(new ApiException(503, "DEPENDENCY_UNAVAILABLE")).when(mail).requireConfigured();
        assertThat(request("POST", BASE + "/recuperacao", null, Map.of("email", email)).statusCode()).isEqualTo(503);
        assertThat(request("POST", BASE + "/recuperacao", null, Map.of("email", "absent@example.invalid")).statusCode()).isEqualTo(503);
    }

    @Test void closingAnAccountAnonymizesAndRevokesWithoutAffectingAdministrators() throws Exception {
        String email = signup(), token = playerToken(email);
        UUID id = playerIds.getFirst();
        // Terceiro pode registrar qualquer endereço sintaticamente válido; anonimização
        // precisa usar namespace que o cadastro público não consegue reservar.
        var collision = request("POST", BASE, null, Map.of("email", id + "@closed.invalid", "password", PASSWORD));
        assertThat(collision.statusCode()).isEqualTo(201);
        playerIds.add(UUID.fromString((String) body(collision).get("usuario_id")));
        request("POST", BASE + "/recuperacao", null, Map.of("email", email));
        String resetToken = delivered.getFirst();
        assertThat(request("DELETE", BASE + "/conta", token, null).statusCode()).isEqualTo(204);
        assertThat(request("GET", BASE + "/sessoes/atual", token, null).statusCode()).isEqualTo(401);
        assertThat(request("DELETE", BASE + "/conta", token, null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT active FROM app_user WHERE id=?", Boolean.class, id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT email FROM app_user WHERE id=?", String.class, id)).isEqualTo("#closed:" + id);
        assertThat(jdbc.queryForObject("SELECT closed_at IS NOT NULL FROM app_user WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(request("POST", BASE + "/redefinicao", null, Map.of("token", resetToken, "password", NEW_PASSWORD)).statusCode()).isEqualTo(422);
        UUID admin = user(true);
        assertThat(request("POST", BASE, null, Map.of("email", email(admin), "password", PASSWORD)).statusCode()).isEqualTo(409);
        delivered.clear();
        assertThat(request("POST", BASE + "/recuperacao", null, Map.of("email", email(admin))).statusCode()).isEqualTo(202);
        assertThat(delivered).isEmpty();
        assertThat(request("DELETE", BASE + "/conta", token(admin), null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT email FROM app_user WHERE id=?", String.class, admin)).isEqualTo(email(admin));
    }

    @Test void aPlayerPromotedToAdministrationCannotResetOrCloseThatAdministrativeIdentity() throws Exception {
        String email = signup(), token = playerToken(email);
        UUID id = playerIds.getFirst();
        request("POST", BASE + "/recuperacao", null, Map.of("email", email));
        String resetToken = delivered.getFirst();
        jdbc.update("UPDATE app_user SET platform_admin=true WHERE id=?", id);
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id);
        assertThat(request("GET", BASE + "/sessoes/atual", token, null).statusCode()).isEqualTo(401);
        assertThat(request("DELETE", BASE + "/conta", token, null).statusCode()).isEqualTo(401);
        assertThat(request("POST", BASE + "/redefinicao", null, Map.of("token", resetToken, "password", NEW_PASSWORD)).statusCode()).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("SELECT email FROM app_user WHERE id=?", String.class, id)).isEqualTo(email);
    }
}
