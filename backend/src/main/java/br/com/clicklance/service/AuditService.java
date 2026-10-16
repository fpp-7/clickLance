package br.com.clicklance.service;

import br.com.clicklance.model.AuditAction;
import br.com.clicklance.model.AuditEntry;
import br.com.clicklance.repository.AuditRepository;
import br.com.clicklance.repository.CompanyRepository;
import br.com.clicklance.repository.DeviceRepository;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.security.CorrelationContext;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private final AuditRepository audit;
    private final CompanyRepository companies;
    private final DeviceRepository devices;
    private final Clock clock;

    public AuditService(AuditRepository audit, CompanyRepository companies, DeviceRepository devices, Clock clock) {
        this.audit = audit; this.companies = companies; this.devices = devices; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void success(AuditAction action, UUID actorId, String companyId, String resourceId) {
        save(new AuditEntry(UUID.randomUUID(), CorrelationContext.currentOrNew(), clock.instant(), actorId,
                actorId == null ? "SYSTEM" : "ADMIN", action.name(), action.resourceType(), resourceId, companyId,
                "SUCCESS", "OK", action.successStatus()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(AuditAction action, AdminPrincipal actor, String companyCandidate, String deviceCandidate, int status, String code) {
        // Nao persistir IDs brutos do caminho. So identificar empresa que existe no banco.
        String companyId = null;
        if (companyCandidate != null && companyCandidate.matches("[A-Za-z0-9_-]{1,64}") && companies.existsById(companyCandidate)) {
            companyId = companyCandidate;
        } else if (deviceCandidate != null && deviceCandidate.matches("[A-Za-z0-9_-]{1,32}")) {
            companyId = devices.findCompanyId(deviceCandidate).orElse(null);
        }
        save(new AuditEntry(UUID.randomUUID(), CorrelationContext.currentOrNew(), clock.instant(), actor == null ? null : actor.userId(),
                actor == null ? "ANONYMOUS" : "ADMIN", action.name(), action.resourceType(), null, companyId,
                status >= 500 ? "FAILED" : "REFUSED", code, status));
    }

    private void save(AuditEntry entry) {
        audit.insert(entry); // Falha aborta a transacao; nunca anunciar sucesso antes do commit.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                LOG.info("administrative_audit correlation_id={} action={} outcome={} actor_id={} resource_type={} resource_id={} company_id={} code={}",
                        entry.correlation_id(), entry.action(), entry.outcome(), entry.actor_id(), entry.resource_type(),
                        entry.resource_id(), entry.empresa_id(), entry.code());
            }
        });
    }
}
