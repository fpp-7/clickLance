package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.validation.Input;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Sem transacao externa: rede acontece entre transacoes curtas de claim/finalizacao. */
@Service
public class PurgeWorkerService {
    private final LifecycleService lifecycle;
    private final PurgeStorageService storage;
    public PurgeWorkerService(LifecycleService lifecycle,PurgeStorageService storage) { this.lifecycle=lifecycle; this.storage=storage; }
    @Transactional(propagation=Propagation.NEVER)
    public Map<String,Object> run(AdminPrincipal actor,String company,int limit) {
        actor.requireCompany(company); Input.integer(limit,"limit",1,100); storage.requireEnabled();
        int completed=0,failed=0;
        for(int i=0;i<limit;i++) {
            var next=lifecycle.claim(company);
            if(next.isEmpty()) break;
            var job=next.get();
            try { storage.deleteAndConfirm(job); if(lifecycle.complete(job)) completed++; }
            catch(ApiException error) { lifecycle.retry(job,error.code()); failed++; }
        }
        return Map.of("completed",completed,"failed",failed);
    }
    @Transactional(propagation=Propagation.NEVER)
    public Map<String,Object> reconcile(AdminPrincipal actor,String company) {
        var origins=lifecycle.origins(actor,company); storage.requireEnabled();
        int orphans=0,missing=0,purged=0;
        for(var origin:origins) {
            var registered=new HashSet<>(lifecycle.registeredKeys(company,origin));
            var unpurged=new HashSet<>(lifecycle.unpurgedKeys(company,origin));
            for(String key:storage.keys(origin)) {
                if(!registered.contains(key)) {
                    // Upload sem POST pode estar pendente por tempo ilimitado: apenas reportar.
                    lifecycle.issue(actor,company,origin,key,"ORPHAN_OBJECT"); orphans++;
                } else if(!unpurged.contains(key)) {
                    lifecycle.issue(actor,company,origin,key,"PURGED_OBJECT_PRESENT"); purged++;
                }
            }
            for(String key:unpurged) if(!storage.exists(origin.bucket(),key)) {
                lifecycle.issue(actor,company,origin,key,"MISSING_OBJECT"); missing++;
            }
        }
        return Map.of("orphan_objects",orphans,"missing_objects",missing,"purged_objects",purged);
    }
}
