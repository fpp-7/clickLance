package br.com.clicklance.service;

import br.com.clicklance.model.InstallationHealth;
import br.com.clicklance.repository.InstallationHealthRepository;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.validation.Input;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstallationHealthService {
    private final InstallationHealthRepository health;
    private final DeviceAuthenticationService authentication;
    private final DeviceService devices;
    private final Clock clock;
    public InstallationHealthService(InstallationHealthRepository health, DeviceAuthenticationService authentication,
                                     DeviceService devices, Clock clock) {
        this.health=health; this.authentication=authentication; this.devices=devices; this.clock=clock;
    }
    @Transactional
    public void receive(String id, String token, Map<String,Object> body) {
        authentication.authenticate(id, token);
        Input.fields(body, Set.of("schema_version","observed_at","gateway_version","capture_state","pending_events",
                "oldest_pending_age_seconds","capture_progress_age_seconds"));
        Input.integer(body.get("schema_version"),"schema_version",1,1);
        Instant observed;
        try { observed=OffsetDateTime.parse(Input.string(body,"observed_at",40)).toInstant(); }
        catch (java.time.DateTimeException invalid) { Input.invalid("observed_at","INVALID_FORMAT"); return; }
        var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        // Atrasos antigos sao recusados sem atualizar contato nem estado; nao implicam saude atual.
        if (observed.isAfter(now.plusSeconds(30)) || observed.isBefore(now.minusSeconds(180))
                || observed.getNano()%1000!=0) Input.invalid("observed_at","OUT_OF_RANGE");
        String version=Input.string(body,"gateway_version",32);
        if (!version.matches("[A-Za-z0-9._-]{1,32}")) Input.invalid("gateway_version","INVALID_FORMAT");
        String state=Input.string(body,"capture_state",16);
        if (!Set.of("READY","WARMING_UP","UNAVAILABLE").contains(state)) Input.invalid("capture_state","INVALID_FORMAT");
        int pending=Input.integer(body.get("pending_events"),"pending_events",0,1000000);
        long oldest=Input.integer(body.get("oldest_pending_age_seconds"),"oldest_pending_age_seconds",0,315360000);
        Long progress=body.get("capture_progress_age_seconds")==null ? null :
                (long)Input.integer(body.get("capture_progress_age_seconds"),"capture_progress_age_seconds",0,315360000);
        if (!body.containsKey("capture_progress_age_seconds")) Input.invalid("capture_progress_age_seconds","INVALID_FORMAT");
        health.update(new InstallationHealth(id,observed,now,version,state,pending,oldest,progress));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> find(AdminPrincipal principal, String id) {
        devices.find(principal,id);
        return health.find(id).<Map<String,Object>>map(h -> Map.of("dispositivo_id",id,"contact_state",
                !h.received_at().plusSeconds(180).isAfter(clock.instant()) || !h.observed_at().plusSeconds(180).isAfter(clock.instant())
                        ? "STALE" : "CURRENT", "health",h))
                .orElseGet(() -> Map.of("dispositivo_id",id,"contact_state","UNKNOWN"));
    }
}
