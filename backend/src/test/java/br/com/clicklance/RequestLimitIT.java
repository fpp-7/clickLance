package br.com.clicklance;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"clicklance.limits.enabled=true","clicklance.admin.private-key-path=","clicklance.admin.public-key-path="})
@Import(RequestLimitIT.Time.class)
class RequestLimitIT {
    @TestConfiguration(proxyBeanMethods=false)
    static class Time { @Bean @Primary Clock fixedTime() { return Clock.fixed(Instant.parse("2026-10-30T12:00:01Z"),ZoneOffset.UTC); } }
    @Autowired Environment env;
    @Test void realHttpHeartbeatLimitHasCorrelationSafeErrorAndRetryAfter() throws Exception {
        var client=HttpClient.newHttpClient();
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+env.getRequiredProperty("local.server.port")
                +"/api/v1/dispositivos/limit-fixture/saude")).header("Content-Type","application/json")
                .header("X-Device-Token","invalid-fixture-token").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        var limited=client.send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).hasValue("60");
        assertThat(limited.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(limited.headers().firstValue("X-Correlation-ID")).isPresent();
        assertThat(limited.body()).contains("RATE_LIMITED").doesNotContain("invalid-fixture-token");
    }
}
