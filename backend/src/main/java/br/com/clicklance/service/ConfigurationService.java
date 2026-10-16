package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.*;
import br.com.clicklance.repository.ConfigurationRepository;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.validation.ConfigSettings;
import br.com.clicklance.validation.Input;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigurationService {
    private final ConfigurationRepository configurations;
    private final DeviceService devices;
    private final CourtService courts;
    private final CompanyService companies;
    private final DeviceAuthenticationService authentication;
    private final AuditService audit;
    private final Clock clock;
    public ConfigurationService(ConfigurationRepository configurations, DeviceService devices, CourtService courts,
                                CompanyService companies, DeviceAuthenticationService authentication, AuditService audit, Clock clock) {
        this.configurations=configurations; this.devices=devices; this.courts=courts; this.companies=companies;
        this.authentication=authentication; this.audit=audit; this.clock=clock;
    }
    @Transactional
    public ConfigPublication publish(AdminPrincipal principal, String id, Map<String, Object> body) {
        var device = devices.find(principal, id);
        configurations.lock(id);
        device = devices.find(principal, id);
        var court = courts.find(principal, device.empresa_id(), device.quadra_id());
        if (!device.active() || !court.active() || !companies.find(principal, device.empresa_id()).active()) throw new ApiException(403, "ORIGIN_INACTIVE");
        Input.fields(body, Set.of("base_version", "settings"));
        if (!body.containsKey("base_version")) Input.invalid("base_version", "INVALID_FORMAT");
        Object base = body.get("base_version");
        if (base != null && (!(base instanceof String version) || version.isBlank() || version.length()>64)) Input.invalid("base_version", "INVALID_FORMAT");
        var previous = configurations.current(id);
        if (!Objects.equals(base, previous.map(ConfigPublication::version).orElse(null))) throw new ApiException(409, "CONFIG_VERSION_CONFLICT");
        var input = Input.object(body, "settings");
        if (input.isEmpty()) Input.invalid("settings", "INVALID_FORMAT");
        ConfigSettings.validate(input);
        var profile = court.perfil_captura();
        var effective = new HashMap<String,Object>(Map.of("REPLAY_BEFORE_SECONDS", profile.REPLAY_BEFORE_SECONDS(),
                "REPLAY_AFTER_SECONDS", profile.REPLAY_AFTER_SECONDS(), "SEGMENT_DURATION_SECONDS", profile.SEGMENT_DURATION_SECONDS(),
                "KEYFRAME_INTERVAL", profile.KEYFRAME_INTERVAL(), "CAMERA_SOURCE", "rtsp"));
        previous.ifPresent(p -> effective.putAll(p.settings()));
        effective.putAll(input);
        ConfigSettings.validate(effective);
        if (effective.get("CAMERA_SOURCE").equals("rtsp") && !ConfigSettings.rtsp(effective.get("CAMERA_RTSP_URL"))) Input.invalid("settings.CAMERA_RTSP_URL", "INVALID_FORMAT");
        var publication = new ConfigPublication(id, UUID.randomUUID().toString(), effective, principal.userId(), clock.instant());
        configurations.insert(publication);
        audit.success(AuditAction.CONFIG_PUBLISH, principal.userId(), device.empresa_id(), id);
        return publication;
    }
    @Transactional(readOnly=true)
    public GatewayConfig current(String id, String token) {
        authentication.authenticate(id, token);
        var publication = configurations.current(id).orElseThrow(() -> new ApiException(503, "CONFIGURATION_NOT_READY"));
        return new GatewayConfig(publication.version(), true, publication.settings());
    }
}
