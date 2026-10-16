package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.*;
import br.com.clicklance.repository.ReplayRepository;
import br.com.clicklance.validation.ReplayPayload;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReplayService {
    private final ReplayRepository replays;
    private final DeviceAuthenticationService authentication;
    private final ObjectStorageService storage;
    private final Clock clock;
    public ReplayService(ReplayRepository replays, DeviceAuthenticationService authentication, ObjectStorageService storage, Clock clock) {
        this.replays=replays; this.authentication=authentication; this.storage=storage; this.clock=clock;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ReplayRegistration register(String token, String key, Map<String,Object> body) {
        // Autenticar antes de validar campos sensiveis ou procurar registros existentes.
        String id=body!=null && body.get("dispositivo_id") instanceof String value ? value : null;
        var principal=authentication.authenticate(id,token);
        var input=ReplayPayload.parse(body,key);
        storage.authorize(principal,input);
        var previous=replays.find(input.eventId());
        if (previous.isPresent()) return repeat(previous.get(),input);
        var now=clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (input.eventAt().isAfter(now.plusSeconds(300))) throw new ApiException(422,"EVENT_IN_FUTURE");
        var object=storage.verify(principal,input);
        int days=replays.retentionDays(principal.companyId());
        var expiry=input.eventAt().plusSeconds(days*86400L);
        var replay=new Replay(input,principal.companyId(),now,days,expiry,!expiry.isAfter(now),object,null);
        if (!replays.insert(replay)) return repeat(replays.find(input.eventId()).orElseThrow(() -> new ApiException(409,"CONTENT_CONFLICT")),input);
        return new ReplayRegistration(true,new ReplayAck(input.eventId(),input.sha256()));
    }
    private static ReplayRegistration repeat(Replay existing, ReplayInput input) {
        if (!existing.input().equals(input)) throw new ApiException(409,"CONTENT_CONFLICT");
        return new ReplayRegistration(false,new ReplayAck(input.eventId(),input.sha256()));
    }
}
