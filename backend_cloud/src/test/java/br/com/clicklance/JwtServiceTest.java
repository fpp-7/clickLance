package br.com.clicklance;

import static org.assertj.core.api.Assertions.*;
import br.com.clicklance.security.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    @Test void expiredAndTamperedJwtAreRejected() {
        var config=new SecurityProperties("admin-test-01234567890123456789012345","jwt-test-012345678901234567890123456",60);
        Instant now=Instant.parse("2026-10-26T12:00:00Z"); UUID id=UUID.randomUUID();
        var issuer=new JwtService(config,Clock.fixed(now,ZoneOffset.UTC)); String token=issuer.issue(id);
        assertThat(issuer.verify(token)).isEqualTo(id);
        var later=new JwtService(config,Clock.fixed(now.plusSeconds(60),ZoneOffset.UTC));
        assertThatThrownBy(()->later.verify(token)).isInstanceOf(br.com.clicklance.api.ApiException.class);
        assertThatThrownBy(()->issuer.verify(token+"x")).isInstanceOf(br.com.clicklance.api.ApiException.class);
    }
}
