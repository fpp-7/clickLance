package br.com.clicklance;

import br.com.clicklance.service.RequestLimitService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RequestLimitServiceTest {
    @Test void recoveryAndIngestionLimitsResetAtNextMinuteAndNeverExposeIdentities() {
        var clock=new AdminTestSupport.MutableClock();
        var limits=new RequestLimitService(clock,true);
        for(int n=0;n<3;n++) assertThat(limits.allow("recovery","fixture-network",null)).isTrue();
        assertThat(limits.allow("recovery","fixture-network",null)).isFalse();
        for(int n=0;n<120;n++) assertThat(limits.allow("ingest","fixture-network","fixture-token")).isTrue();
        assertThat(limits.allow("ingest","fixture-network","fixture-token")).isFalse();
        assertThat(limits.allow("ingest","fixture-network","another-installation")).isTrue();
        clock.advance(60); assertThat(limits.allow("recovery","fixture-network",null)).isTrue();
        limits.record("ingest",429,123000000);
        assertThat(limits.metrics()).containsEntry("ingest.status_429",1L).containsEntry("ingest.duration_ms",123L);
        assertThat(limits.metrics().toString()).doesNotContain("fixture-network","fixture-token");
    }
}
