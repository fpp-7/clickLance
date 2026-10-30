package br.com.clicklance.service;

import br.com.clicklance.model.AuditAction;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.CaptureProfile;
import br.com.clicklance.model.Court;
import br.com.clicklance.repository.CourtRepository;
import br.com.clicklance.validation.Input;

import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourtService {
    private final CourtRepository courts;
    private final CompanyService companies;
    private final AuditService audit;
    public CourtService(CourtRepository courts, CompanyService companies, AuditService audit) { this.courts = courts; this.companies = companies; this.audit = audit; }

    @Transactional
    public Court create(AdminPrincipal principal, String companyId, Map<String, Object> body) {
        companies.find(principal, companyId);
        Input.fields(body, Set.of("nome", "active", "timezone", "perfil_captura"));
        String name = Input.string(body, "nome", 120).strip();
        boolean active = Input.bool(body, "active");
        String timezone = Input.string(body, "timezone", 64);
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) Input.invalid("timezone", "INVALID_FORMAT");
        var profile = CaptureProfile.parse(Input.object(body, "perfil_captura"));
        String id = UUID.randomUUID().toString();
        var court = new Court(id, companyId, name, active, timezone, profile);
        courts.insert(court);
        audit.success(AuditAction.COURT_CREATE, principal.userId(), companyId, id);
        return court;
    }

    @Transactional(readOnly = true)
    public Court find(AdminPrincipal principal, String companyId, String id) {
        companies.find(principal, companyId);
        if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new ApiException(400, "INVALID_REQUEST");
        return courts.findByCompanyAndId(companyId, id).orElseThrow(() -> new ApiException(404, "RESOURCE_NOT_FOUND"));
    }

    @Transactional
    public Court setActive(AdminPrincipal principal, String companyId, String id, Map<String, Object> body) {
        find(principal, companyId, id);
        Input.fields(body, Set.of("active"));
        courts.setActive(companyId, id, Input.bool(body, "active"));
        audit.success(AuditAction.COURT_STATUS_CHANGE, principal.userId(), companyId, id);
        return courts.findByCompanyAndId(companyId, id).orElseThrow(() -> new ApiException(404, "RESOURCE_NOT_FOUND"));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> list(AdminPrincipal principal, String companyId, int page, int size) {
        companies.find(principal, companyId);
        Input.integer(page, "page", 0, Integer.MAX_VALUE);
        Input.integer(size, "size", 1, 100);
        var items = courts.findByCompany(companyId, size, (long) page * size);
        var total = courts.countByCompany(companyId);
        return Map.of("items", items, "page", page, "size", size, "total", total);
    }
}
