package br.com.clicklance.purge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="clicklance.purge",name="enabled",havingValue="true",matchIfMissing=true)
public class PurgeJob {
    private final PurgeService service;
    public PurgeJob(PurgeService service) { this.service=service; }
    @Scheduled(fixedDelayString="${clicklance.purge.delay-ms:60000}")
    public void run() { service.runBatch(); }
}
