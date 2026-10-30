package br.com.clicklance;

import br.com.clicklance.model.PurgeJob;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.service.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AdminTestSupport.TimeConfiguration.class,LifecycleIT.Storage.class})
class LifecycleIT extends AdminTestSupport {
    static final S3Fixture S3;
    static { try { S3=new S3Fixture(); S3.lifecycleOperations=true; } catch(Exception e) { throw new ExceptionInInitializerError(e); } }
    @TestConfiguration(proxyBeanMethods=false)
    static class Storage {
        @Bean @Primary S3Client lifecycleS3() { return S3.client(); }
        @Bean @Primary PurgeStorageService testPurgeStorage() { return new PurgeStorageService(S3.client()); }
    }
    record Origin(String company,String court,String device,String admin) {}
    final List<Origin> origins=new ArrayList<>();
    @Autowired LifecycleService lifecycle;
    @Autowired PurgeWorkerService worker;
    @Autowired PurgeStorageService storage;
    @AfterAll static void stopS3() { S3.close(); }
    @AfterEach void cleanup() {
        S3.objects.clear(); S3.failureStatus=0; S3.deleteFailure=0; S3.versioningStatus=null;
        for(var o:origins) {
            jdbc.update("DELETE FROM library_entry WHERE event_id IN(SELECT evento_id FROM replay WHERE dispositivo_id=?)",o.device());
            jdbc.update("DELETE FROM replay_lifecycle WHERE event_id IN(SELECT evento_id FROM replay WHERE dispositivo_id=?)",o.device());
            jdbc.update("DELETE FROM replay WHERE dispositivo_id=?",o.device());
            jdbc.update("DELETE FROM dispositivo WHERE id=?",o.device());
            jdbc.update("DELETE FROM quadra WHERE id=?",o.court());
        }
    }
    Origin origin() {
        String company=UUID.randomUUID().toString(),court=UUID.randomUUID().toString(),device=id();
        companies.add(company);
        jdbc.update("INSERT INTO empresa(id,nome,active) VALUES (?,'Lifecycle fixture',true)",company);
        jdbc.update("INSERT INTO quadra(id,empresa_id,nome,active,timezone,replay_before_seconds,replay_after_seconds,segment_duration_seconds,keyframe_interval) VALUES (?,?,'Fixture',true,'America/Sao_Paulo',120,0,60,30)",court,company);
        jdbc.update("INSERT INTO dispositivo(id,quadra_id,active,bucket,token_hash) VALUES (?,?,true,'lifecycle-private',?)",device,court,id()+id());
        var o=new Origin(company,court,device,token(user(true))); origins.add(o); return o;
    }
    static String id() { return UUID.randomUUID().toString().replace("-",""); }
    static String key(Origin o,String id) { return "replays/"+o.device()+"/"+id+".mp4"; }
    String replay(Origin o,long secondsUntilExpiry) {
        String id=id(),key=key(o,id); Instant expiry=CLOCK.instant().plusSeconds(secondsUntilExpiry),event=expiry.minusSeconds(2*86400L);
        String hash=S3.put("lifecycle-private",key,("fixture-"+id).getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
            INSERT INTO replay(evento_id,dispositivo_id,quadra_id,empresa_id,event_at,received_at,retention_days,expires_at,expired_on_arrival,bucket,object_key,sha256,object_etag,object_size)
            VALUES (?,?,?,?,?,?,2,?,?,'lifecycle-private',?,?,?,1)
            """,id,o.device(),o.court(),o.company(),event.atOffset(ZoneOffset.UTC),CLOCK.instant().atOffset(ZoneOffset.UTC),expiry.atOffset(ZoneOffset.UTC),secondsUntilExpiry<=0,key,hash,"\"fixture-etag\"");
        return id;
    }
    AdminPrincipal admin(Origin o) { return sessions.authenticate(o.admin()); }
    String companyPath(Origin o) { return "/api/v1/admin/empresas/"+o.company(); }
    String blockPath(String id) { return "/api/v1/admin/lances/"+id+"/bloqueio"; }

    @Test void blockIsScopedAuditedAndCannotChangeImmutableReplay() throws Exception {
        var o=origin(); var other=origin(); String event=replay(o,100);
        var before=jdbc.queryForMap("SELECT * FROM replay WHERE evento_id=?",event);
        UUID tenant=user(false); jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)",tenant,other.company());
        assertThat(request("PATCH",blockPath(event),token(tenant),Map.of("blocked",true,"reason","PRIVACY")).statusCode()).isEqualTo(403);
        assertThat(request("PATCH",blockPath(id()),o.admin(),Map.of("blocked",true,"reason","PRIVACY")).statusCode()).isEqualTo(404);
        var response=request("PATCH",blockPath(event),o.admin(),Map.of("blocked",true,"reason","PRIVACY"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response)).containsEntry("evento_id",event).containsEntry("blocked",true);
        assertThat(jdbc.queryForObject("SELECT block_reason FROM replay_lifecycle WHERE event_id=?",String.class,event)).isEqualTo("PRIVACY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administrative_audit WHERE resource_id=? AND action='REPLAY_BLOCK' AND outcome='SUCCESS'",Integer.class,event)).isEqualTo(1);
        assertThat(request("PATCH",blockPath(event),o.admin(),Map.of("blocked",true,"reason","raw private detail")).statusCode()).isEqualTo(422);
        assertThat(request("PATCH",blockPath(event),o.admin(),Map.of("blocked",false,"reason","OPERATIONAL")).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT blocked_at FROM replay_lifecycle WHERE event_id=?",OffsetDateTime.class,event)).isNull();
        assertThat(jdbc.queryForMap("SELECT * FROM replay WHERE evento_id=?",event)).isEqualTo(before);
    }

    @Test void concurrentReservationsDoNotDuplicateAndNeverReserveSavedOrUnexpiredReplays() throws Exception {
        var o=origin(); String expired=replay(o,-1),saved=replay(o,-1); replay(o,100);
        UUID owner=user(false);
        jdbc.update("INSERT INTO library_entry(user_id,event_id,saved_at) VALUES (?,?,?)",owner,saved,CLOCK.instant().minusSeconds(100).atOffset(ZoneOffset.UTC));
        var actor=admin(o);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> reserve=()->{ start.await(); return lifecycle.reserve(actor,o.company(),100); };
            var a=pool.submit(reserve); var b=pool.submit(reserve); start.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(jdbc.queryForList("SELECT event_id FROM purge_job",String.class)).contains(expired).doesNotContain(saved);
        assertThat(worker.run(actor,o.company(),100)).containsEntry("completed",1).containsEntry("failed",0);
        assertThat(jdbc.queryForObject("SELECT state FROM purge_job WHERE event_id=?",String.class,expired)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT purged_at FROM replay WHERE evento_id=?",OffsetDateTime.class,expired)).isNotNull();
        assertThat(S3.objects).containsKey("/lifecycle-private/"+key(o,saved)).doesNotContainKey("/lifecycle-private/"+key(o,expired));
    }

    @Test void crashAfterDeleteIsRecoveredAndStaleLeaseCannotFinalize() {
        var o=origin(); String event=replay(o,-1); var actor=admin(o);
        assertThat(lifecycle.reserve(actor,o.company(),10)).isEqualTo(1);
        PurgeJob original=lifecycle.claim(o.company()).orElseThrow();
        storage.deleteAndConfirm(original); // Processo cairia aqui, antes do update do banco.
        assertThat(jdbc.queryForObject("SELECT purged_at FROM replay WHERE evento_id=?",OffsetDateTime.class,event)).isNull();
        CLOCK.advance(61);
        PurgeJob recovery=lifecycle.claim(o.company()).orElseThrow();
        assertThat(recovery.leaseToken()).isNotEqualTo(original.leaseToken());
        assertThat(lifecycle.complete(original)).isFalse();
        storage.deleteAndConfirm(recovery);
        assertThat(lifecycle.complete(recovery)).isTrue();
        assertThat(lifecycle.claim(o.company())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT attempts FROM purge_job WHERE event_id=?",Integer.class,event)).isEqualTo(2);
    }

    @Test void versioningPermissionAndDeletionFailuresRemainRetryableAnd403NeverMeansAbsent() {
        var o=origin(); String event=replay(o,-1); var actor=admin(o);
        lifecycle.reserve(actor,o.company(),10);
        int deletes=S3.deletes.get();
        for(String status:List.of("Enabled","Suspended")) {
            S3.versioningStatus=status;
            assertThat(worker.run(actor,o.company(),1)).containsEntry("completed",0).containsEntry("failed",1);
            assertThat(jdbc.queryForObject("SELECT last_error FROM purge_job WHERE event_id=?",String.class,event)).isEqualTo("STORAGE_POLICY_MISMATCH");
            assertThat(S3.deletes.get()).isEqualTo(deletes); CLOCK.advance(3601);
        }
        S3.versioningStatus=null; S3.deleteFailure=403;
        assertThat(worker.run(actor,o.company(),1)).containsEntry("failed",1);
        assertThat(jdbc.queryForObject("SELECT purged_at FROM replay WHERE evento_id=?",OffsetDateTime.class,event)).isNull();
        CLOCK.advance(3601); S3.deleteFailure=0; S3.failureStatus=403;
        assertThatThrownBy(()->storage.exists("lifecycle-private",key(o,event))).isInstanceOf(br.com.clicklance.exception.ApiException.class);
        assertThat(worker.run(actor,o.company(),1)).containsEntry("failed",1);
        S3.failureStatus=0; CLOCK.advance(3601);
        assertThat(worker.run(actor,o.company(),1)).containsEntry("completed",1);
    }

    @Test void reconciliationOnlyReportsProvisionedPrefixesAndIsRepeatableWithoutDeletingUploads() throws Exception {
        var o=origin(); var other=origin(); String missing=replay(o,-1),otherEvent=replay(other,-1),purged=replay(o,-1);
        jdbc.update("UPDATE replay SET purged_at=? WHERE evento_id=?",CLOCK.instant().atOffset(ZoneOffset.UTC),purged);
        S3.objects.remove("/lifecycle-private/"+key(o,missing));
        String orphan=key(o,id()); S3.put("lifecycle-private",orphan,new byte[]{1,2,3});
        int deletes=S3.deletes.get(); var actor=admin(o);
        for(int repeat=0;repeat<2;repeat++) assertThat(worker.reconcile(actor,o.company())).containsEntry("orphan_objects",1).containsEntry("missing_objects",1).containsEntry("purged_objects",1);
        assertThat(S3.deletes.get()).isEqualTo(deletes);
        assertThat(S3.objects).containsKey("/lifecycle-private/"+orphan).containsKey("/lifecycle-private/"+key(other,otherEvent));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_issue WHERE empresa_id=?",Integer.class,o.company())).isEqualTo(3);
        UUID tenant=user(false); jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)",tenant,other.company());
        assertThat(request("GET",companyPath(o)+"/reconciliacao",token(tenant),null).statusCode()).isEqualTo(403);
        assertThat(request("POST",companyPath(o)+"/purga/reservas",token(tenant),Map.of("limit",10)).statusCode()).isEqualTo(403);
        assertThat(request("GET",companyPath(o)+"/reconciliacao",o.admin(),null).body()).doesNotContain(orphan,missing,otherEvent);
    }

    @Test void reservationSkipsReplayWithInFlightSaveAndRechecksCommittedLibrary() throws Exception {
        var o=origin(); String event=replay(o,10); UUID owner=user(false);
        var tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var saved=new CountDownLatch(1); var release=new CountDownLatch(1); var actor=admin(o);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var save=pool.submit(()->tx.execute(status->{
                jdbc.queryForObject("SELECT evento_id FROM replay WHERE evento_id=? FOR UPDATE",String.class,event);
                jdbc.update("INSERT INTO library_entry(user_id,event_id,saved_at) VALUES (?,?,?)",owner,event,CLOCK.instant().atOffset(ZoneOffset.UTC));
                saved.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("save timeout"); }
                catch(InterruptedException e) { throw new IllegalStateException(e); }
                return true;
            }));
            try {
                assertThat(saved.await(10,TimeUnit.SECONDS)).isTrue(); CLOCK.advance(11);
                assertThat(lifecycle.reserve(actor,o.company(),10)).isZero(); release.countDown();
                assertThat(save.get(10,TimeUnit.SECONDS)).isTrue();
                assertThat(lifecycle.reserve(actor,o.company(),10)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM purge_job WHERE event_id=?",Integer.class,event)).isZero();
            } finally { release.countDown(); }
        }
    }
}
