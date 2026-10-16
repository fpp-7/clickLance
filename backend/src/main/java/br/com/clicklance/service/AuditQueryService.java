package br.com.clicklance.service;

import br.com.clicklance.model.AuditEntry;
import br.com.clicklance.repository.AuditRepository;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.validation.Input;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private final AuditRepository audit;
    private final CompanyService companies;
    public AuditQueryService(AuditRepository audit, CompanyService companies) { this.audit = audit; this.companies = companies; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> list(AdminPrincipal principal, String companyId, int page, int size) {
        companies.find(principal, companyId);
        Input.integer(page, "page", 0, Integer.MAX_VALUE);
        Input.integer(size, "size", 1, 100);
        return Map.of("items", audit.findByCompany(companyId, size, (long) page * size), "page", page, "size", size,
                "total", audit.countByCompany(companyId));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> findCorrelation(AdminPrincipal principal, UUID correlationId) {
        principal.requirePlatform();
        return audit.findByCorrelation(correlationId);
    }
}
