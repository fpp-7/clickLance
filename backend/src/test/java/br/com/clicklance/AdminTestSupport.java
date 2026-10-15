package br.com.clicklance;

import br.com.clicklance.service.AdminAccountService;
import br.com.clicklance.security.AdminJwt;
import br.com.clicklance.service.AdminSessionService;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

abstract class AdminTestSupport {
    static final String PASSWORD = "test-fixture-only-" + UUID.randomUUID();
    static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(PASSWORD);
    static final MutableClock CLOCK = new MutableClock();
    static final Path KEYS;
    static final KeyPair KEY_PAIR;
    static {
        try {
            KEYS = Files.createTempDirectory("clicklance-admin-it-");
            KEYS.toFile().deleteOnExit();
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEY_PAIR = generator.generateKeyPair();
            writePem(KEYS.resolve("private.pem"), "PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded());
            writePem(KEYS.resolve("public.pem"), "PUBLIC KEY", KEY_PAIR.getPublic().getEncoded());
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }

    private static void writePem(Path path, String kind, byte[] bytes) throws Exception {
        Files.writeString(path, "-----BEGIN " + kind + "-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                .encodeToString(bytes) + "\n-----END " + kind + "-----\n");
        path.toFile().deleteOnExit();
    }

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) {
        registry.add("clicklance.admin.private-key-path", () -> KEYS.resolve("private.pem").toString());
        registry.add("clicklance.admin.public-key-path", () -> KEYS.resolve("public.pem").toString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary Clock testClock() { return CLOCK; }
    }

    static class MutableClock extends Clock {
        private Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        void reset() { now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS); }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @Autowired AdminAccountService accounts;
    @Autowired AdminSessionService sessions;
    @Autowired AdminJwt jwt;
    @Autowired org.springframework.context.ApplicationContext context;
    final List<UUID> users = new ArrayList<>();
    final List<String> companies = new ArrayList<>();
    final JsonMapper mapper = JsonMapper.builder().build();
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach void resetClock() { CLOCK.reset(); }

    @AfterEach void removeOnlyOwnFixtures() {
        for (UUID id : users) {
            jdbc.update("DELETE FROM admin_session WHERE user_id=?", id);
            jdbc.update("DELETE FROM admin_company_membership WHERE user_id=?", id);
            jdbc.update("DELETE FROM app_user WHERE id=?", id);
        }
        for (String id : companies) jdbc.update("DELETE FROM empresa WHERE id=?", id);
    }

    UUID user(boolean platform) {
        UUID id = UUID.randomUUID();
        users.add(id);
        jdbc.update("INSERT INTO app_user(id,email,password_hash,platform_admin) VALUES (?,?,?,?)",
                id, id + "@example.invalid", PASSWORD_HASH, platform);
        return id;
    }

    String email(UUID id) { return id + "@example.invalid"; }
    String token(UUID id) { return (String) sessions.login(email(id), PASSWORD).get("access_token"); }

    HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getRequiredProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> body(HttpResponse<String> response) { return mapper.readValue(response.body(), Map.class); }
}
