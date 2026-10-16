package br.com.clicklance;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.CaptureProfile;
import br.com.clicklance.model.Court;
import br.com.clicklance.repository.DeviceRepository;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.security.DeviceTokens;
import br.com.clicklance.service.CourtService;
import br.com.clicklance.service.DeviceService;
import br.com.clicklance.service.AuditService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceServiceTest {
    @Test void absentPlatformBucketConfigurationNeverIssuesOrPersistsCredentials() {
        var devices = mock(DeviceRepository.class);
        var courts = mock(CourtService.class);
        var tokens = mock(DeviceTokens.class);
        var principal = new AdminPrincipal(UUID.randomUUID(), "operator@example.invalid", true, Set.of(), "hash");
        when(courts.find(principal, "company", "court")).thenReturn(new Court("court", "company", "Quadra", true,
                "UTC", new CaptureProfile(120, 0, 60, 30)));
        var service = new DeviceService(devices, courts, tokens, mock(AuditService.class), "");
        assertThatThrownBy(() -> service.create(principal, "company", "court",
                Map.of("dispositivo_id", "mega01", "active", true, "bucket", "not-implicitly-approved")))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(503);
        verifyNoInteractions(devices, tokens);
    }
}
