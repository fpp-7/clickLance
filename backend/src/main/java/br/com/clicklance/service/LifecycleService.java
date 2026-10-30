package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.AuditAction;
import br.com.clicklance.model.PurgeJob;
import br.com.clicklance.model.ReconciliationOrigin;
import br.com.clicklance.repository.LifecycleRepository;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.validation.Input;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LifecycleService {
    private final LifecycleRepository lifecycle;
    private final AuditService audit;
    private final Clock clock;
    public LifecycleService(LifecycleRepository lifecycle,AuditService audit,Clock clock) { this.lifecycle=lifecycle; this.audit=audit; this.clock=clock; }

    @Transactional
    public Map<String,Object> block(AdminPrincipal actor,String eventId,Map<String,Object> body) {
        if(!eventId.matches("[a-f0-9]{32}")) throw new ApiException(400,"INVALID_REQUEST");
        var company=lifecycle.companyOf(eventId).orElse(null);
        if(company==null) throw new ApiException(actor.platformAdmin()?404:403,actor.platformAdmin()?"RESOURCE_NOT_FOUND":"FORBIDDEN");
        actor.requireCompany(company);
        lifecycle.lockReplay(eventId).orElseThrow(()->new ApiException(404,"RESOURCE_NOT_FOUND"));
        Input.fields(body,Set.of("blocked","reason"));
        boolean blocked=Input.bool(body,"blocked");
        String reason=Input.string(body,"reason",32);
        if(!Set.of("POLICY","PRIVACY","OPERATIONAL").contains(reason)) Input.invalid("reason","INVALID_FORMAT");
        lifecycle.block(eventId,actor.userId(),blocked,reason,clock.instant());
        audit.success(AuditAction.REPLAY_BLOCK,actor.userId(),company,eventId);
        return Map.of("evento_id",eventId,"blocked",blocked);
    }
    @Transactional
    public int reserve(AdminPrincipal actor,String company,int limit) {
        actor.requireCompany(company); Input.integer(limit,"limit",1,100);
        var ids=lifecycle.reserve(company,clock.instant(),limit);
        for(var id:ids) audit.success(AuditAction.PURGE_RESERVE,actor.userId(),company,id);
        return ids.size();
    }
    @Transactional
    public Optional<PurgeJob> claim(String company) {
        var job=lifecycle.claim(company,clock.instant());
        job.ifPresent(value->audit.success(AuditAction.PURGE_CLAIM,null,company,value.eventId()));
        return job;
    }
    @Transactional
    public boolean complete(PurgeJob job) {
        if(!lifecycle.complete(job,clock.instant())) return false;
        audit.success(AuditAction.PURGE_COMPLETE,null,job.companyId(),job.eventId());
        return true;
    }
    @Transactional
    public void retry(PurgeJob job,String code) {
        if(lifecycle.retry(job,code,clock.instant())) audit.success(AuditAction.PURGE_RETRY,null,job.companyId(),job.eventId());
    }
    @Transactional(readOnly=true)
    public List<ReconciliationOrigin> origins(AdminPrincipal actor,String company) { actor.requireCompany(company); return lifecycle.origins(company); }
    @Transactional(readOnly=true)
    public List<String> registeredKeys(String company,ReconciliationOrigin origin) { return lifecycle.registeredKeys(company,origin.bucket(),origin.deviceId()); }
    @Transactional(readOnly=true)
    public List<String> unpurgedKeys(String company,ReconciliationOrigin origin) { return lifecycle.unpurgedKeys(company,origin.bucket(),origin.deviceId()); }
    @Transactional
    public void issue(AdminPrincipal actor,String company,ReconciliationOrigin origin,String key,String kind) {
        actor.requireCompany(company);
        UUID issue=lifecycle.issue(company,origin.bucket(),key,kind,clock.instant());
        audit.success(AuditAction.RECONCILIATION_FIND,actor.userId(),company,issue.toString());
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> issues(AdminPrincipal actor,String company) { actor.requireCompany(company); return lifecycle.issues(company,100); }
}
