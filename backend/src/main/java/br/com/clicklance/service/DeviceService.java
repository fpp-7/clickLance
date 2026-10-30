package br.com.clicklance.service;

import br.com.clicklance.model.AuditAction;
import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.Device;
import br.com.clicklance.model.DeviceIssued;
import br.com.clicklance.repository.DeviceRepository;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.security.DeviceTokens;
import br.com.clicklance.validation.Input;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
    private final DeviceRepository devices;
    private final CourtService courts;
    private final DeviceTokens tokens;
    private final Set<String> allowedBuckets;
    private final AuditService audit;

    public DeviceService(DeviceRepository devices, CourtService courts, DeviceTokens tokens, AuditService audit,
                         @Value("${clicklance.devices.allowed-buckets:}") String allowedBuckets) {
        this.devices = devices;
        this.courts = courts;
        this.tokens = tokens;
        this.audit = audit;
        this.allowedBuckets = Arrays.stream(allowedBuckets.split(",")).map(String::strip)
                .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        if (this.allowedBuckets.stream().anyMatch(value -> !validBucket(value))) {
            throw new IllegalStateException("DEVICE_ALLOWED_BUCKETS: configure nomes de buckets validos separados por virgula.");
        }
    }

    @Transactional
    public DeviceIssued create(AdminPrincipal principal, String companyId, String courtId, Map<String, Object> body) {
        var court = courts.find(principal, companyId, courtId);
        Input.fields(body, Set.of("dispositivo_id", "active", "bucket"));
        String id = Input.string(body, "dispositivo_id", 32);
        if (!id.matches("[A-Za-z0-9_-]{1,32}")) Input.invalid("dispositivo_id", "INVALID_FORMAT");
        boolean active = Input.bool(body, "active");
        String bucket = Input.string(body, "bucket", 63);
        if (!validBucket(bucket)) Input.invalid("bucket", "INVALID_FORMAT");
        if (allowedBuckets.isEmpty()) throw new ApiException(503, "DEPENDENCY_UNAVAILABLE");
        if (!allowedBuckets.contains(bucket)) Input.invalid("bucket", "NOT_ALLOWED");
        var device = new Device(id, court.quadra_id(), court.empresa_id(), active, bucket);
        String token = tokens.issue();
        if (!devices.insert(device, tokens.hash(token))) throw new ApiException(409, "IDENTITY_CONFLICT");
        audit.success(AuditAction.DEVICE_CREATE, principal.userId(), device.empresa_id(), id);
        return new DeviceIssued(device, token);
    }

    @Transactional(readOnly = true)
    public Device find(AdminPrincipal principal, String id) {
        if (!id.matches("[A-Za-z0-9_-]{1,32}")) throw new ApiException(400, "INVALID_REQUEST");
        return devices.findForAdmin(id, principal.userId(), principal.platformAdmin())
                .orElseThrow(() -> principal.platformAdmin() ? new ApiException(404, "RESOURCE_NOT_FOUND") : new ApiException(403, "FORBIDDEN"));
    }

    @Transactional
    public Device setActive(AdminPrincipal principal, String id, Map<String, Object> body) {
        var device = find(principal, id);
        Input.fields(body, Set.of("active"));
        devices.setActive(id, device.empresa_id(), Input.bool(body, "active"));
        audit.success(AuditAction.DEVICE_STATUS_CHANGE, principal.userId(), device.empresa_id(), id);
        return find(principal, id);
    }

    @Transactional
    public DeviceIssued rotateToken(AdminPrincipal principal, String id, Map<String, Object> body) {
        var device = find(principal, id);
        Input.fields(body, Set.of());
        String token = tokens.issue();
        devices.rotateToken(id, device.empresa_id(), tokens.hash(token));
        audit.success(AuditAction.DEVICE_TOKEN_ROTATE, principal.userId(), device.empresa_id(), id);
        return new DeviceIssued(find(principal, id), token);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> list(AdminPrincipal principal, String companyId, String courtId, int page, int size) {
        courts.find(principal, companyId, courtId);
        Input.integer(page, "page", 0, Integer.MAX_VALUE);
        Input.integer(size, "size", 1, 100);
        return Map.of("items", devices.findByCourt(courtId, size, (long) page * size), "page", page,
                "size", size, "total", devices.countByCourt(courtId));
    }

    private static boolean validBucket(String bucket) {
        return bucket.length() >= 3 && bucket.length() <= 63 && bucket.matches("[a-z0-9][a-z0-9.-]+[a-z0-9]");
    }
}
