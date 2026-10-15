package br.com.clicklance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Requer PostgreSQL real dedicado. Nao limpa nem recria dados existentes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"clicklance.admin.private-key-path=", "clicklance.admin.public-key-path="})
class FoundationIT {
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void migrationIsDurableAndDoesNotReapply() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM backend_installation", Integer.class)).isEqualTo(1);
        var installedAt = jdbc.queryForObject("SELECT installed_at FROM backend_installation WHERE id = 1", Object.class);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT installed_at FROM backend_installation WHERE id = 1", Object.class))
                .isEqualTo(installedAt);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class)).isEqualTo(1);
    }

    @Test
    void healthIsMinimalAndConfigurationAndBusinessRoutesAreNotExposed() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String base = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        var health = client.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health"))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).isEqualTo("{\"status\":\"UP\"}");
        for (String path : new String[]{"/actuator/env", "/actuator/configprops", "/actuator", "/api/v1/replays"}) {
            var response = client.send(HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(path).isEqualTo(401);
            assertThat(response.body()).doesNotContain("DB_PASSWORD", "jdbc:postgresql", "stackTrace");
        }
    }

    @Test
    void missingKeysNeverCreateAnImplicitAdministratorOrSession() throws Exception {
        Long users = jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class);
        Long sessions = jdbc.queryForObject("SELECT count(*) FROM admin_session", Long.class);
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                + environment.getRequiredProperty("local.server.port") + "/api/v1/admin/sessoes"))
                .header("Content-Type", "application/json").timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"test@example.invalid\",\"password\":\"test-only-password\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Retry-After")).hasValue("5");
        assertThat(response.body()).doesNotContain("test-only-password");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class)).isEqualTo(users);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_session", Long.class)).isEqualTo(sessions);
    }
}
