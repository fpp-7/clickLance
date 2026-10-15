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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
            assertThat(response.statusCode()).as(path).isEqualTo(404);
            assertThat(response.body()).doesNotContain("DB_PASSWORD", "jdbc:postgresql", "stackTrace");
        }
    }
}
