package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.repository.DeviceRepository;
import br.com.clicklance.security.DevicePrincipal;
import br.com.clicklance.security.DeviceTokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Base para os adaptadores HTTP de configuracao/ingestao das historias seguintes. */
@Service
public class DeviceAuthenticationService {
    private final DeviceRepository devices;
    private final DeviceTokens tokens;

    public DeviceAuthenticationService(DeviceRepository devices, DeviceTokens tokens) {
        this.devices = devices;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public DevicePrincipal authenticate(String deviceId, String token) {
        if (deviceId == null || !deviceId.matches("[A-Za-z0-9_-]{1,32}") || !tokens.validFormat(token)) {
            throw new ApiException(401, "UNAUTHENTICATED");
        }
        var credentials = devices.findCredentials(deviceId).orElse(null);
        // Executa a mesma comparacao de hash para identidade ausente, sem revelar existencia.
        boolean matches = tokens.matches(token, credentials == null ? "0".repeat(64) : credentials.tokenHash());
        if (credentials == null || !matches) throw new ApiException(401, "UNAUTHENTICATED");
        var device = credentials.device();
        if (!device.active() || !credentials.courtActive() || !credentials.companyActive()) {
            throw new ApiException(403, "ORIGIN_INACTIVE");
        }
        return new DevicePrincipal(device.dispositivo_id(), device.quadra_id(), device.empresa_id(), device.bucket());
    }
}
