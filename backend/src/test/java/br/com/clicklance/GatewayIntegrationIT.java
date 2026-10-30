package br.com.clicklance;

import br.com.clicklance.model.ConfigPublication;
import br.com.clicklance.model.GatewayConfig;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="clicklance.devices.allowed-buckets=be008-private-test")
@Import({AdminTestSupport.TimeConfiguration.class,GatewayIntegrationIT.Storage.class})
@ExtendWith(OutputCaptureExtension.class)
class GatewayIntegrationIT extends AdminTestSupport {
    static final String BUCKET="be008-private-test";
    static final S3Fixture S3;
    static { try { S3=new S3Fixture(); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @TestConfiguration(proxyBeanMethods=false)
    static class Storage { @Bean @Primary S3Client localS3() { return S3.client(); } }
    record Origin(String company,String court,String device,String secret,String admin,UUID actor) {}
    final List<Origin> origins=new ArrayList<>();
    @AfterAll static void stopStorageFixture() { S3.close(); }
    @AfterEach void cleanupOrigin() {
        S3.failureStatus=0; S3.changeOnGet=false; S3.objects.clear();
        for (var o:origins) {
            jdbc.update("DELETE FROM replay WHERE dispositivo_id=?",o.device());
            jdbc.update("DELETE FROM device_configuration_current WHERE dispositivo_id=?",o.device());
            jdbc.update("DELETE FROM device_configuration WHERE dispositivo_id=?",o.device());
            jdbc.update("DELETE FROM dispositivo WHERE id=?",o.device());
            jdbc.update("DELETE FROM quadra WHERE id=?",o.court());
        }
    }
    Origin origin() throws Exception {
        String company=UUID.randomUUID().toString(), court=UUID.randomUUID().toString(), device=id();
        companies.add(company);
        jdbc.update("INSERT INTO empresa(id,nome,active) VALUES (?,'Gateway teste',true)",company);
        jdbc.update("""
                INSERT INTO quadra(id,empresa_id,nome,active,timezone,replay_before_seconds,replay_after_seconds,segment_duration_seconds,keyframe_interval)
                VALUES (?,?,'Gateway teste',true,'America/Sao_Paulo',120,0,60,30)
                """,court,company);
        UUID actor=user(true); String admin=token(actor);
        var response=request("POST","/api/v1/admin/empresas/"+company+"/quadras/"+court+"/dispositivos",admin,
                Map.of("dispositivo_id",device,"active",true,"bucket",BUCKET));
        assertThat(response.statusCode()).isEqualTo(201);
        var origin=new Origin(company,court,device,(String)body(response).get("device_token"),admin,actor);
        origins.add(origin); return origin;
    }
    static String id() { return UUID.randomUUID().toString().replace("-",""); }
    String configPath(Origin o) { return "/api/v1/dispositivos/"+o.device()+"/config"; }
    String publishPath(Origin o) { return "/api/v1/admin/dispositivos/"+o.device()+"/configuracoes"; }
    Map<String,Object> publishBody(String base,Map<String,Object> settings) {
        var body=new HashMap<String,Object>(); body.put("base_version",base); body.put("settings",settings); return body;
    }
    HttpResponse<String> publish(Origin o,String base,Map<String,Object> settings) throws Exception {
        return request("POST",publishPath(o),o.admin(),publishBody(base,settings));
    }
    HttpResponse<String> gateway(String method,String path,String token,String key,Object body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(baseUrl()+path)).timeout(Duration.ofSeconds(20));
        if(token!=null) builder.header("X-Device-Token",token);
        if(key!=null) builder.header("Idempotency-Key",key);
        if(body!=null) builder.header("Content-Type","application/json");
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        response.headers().firstValue("X-Correlation-ID").ifPresent(c->correlations.add(UUID.fromString(c)));
        return response;
    }
    String baseUrl() { return "http://127.0.0.1:"+environment.getRequiredProperty("local.server.port"); }
    Map<String,Object> replay(Origin o,String event,Instant timestamp,boolean upload) {
        String key="replays/"+o.device()+"/"+event+".mp4";
        byte[] bytes=("synthetic-video-"+event).getBytes(StandardCharsets.UTF_8);
        String hash=upload?S3.put(BUCKET,key,bytes):S3Fixture.hash(bytes);
        return new HashMap<>(Map.of("contrato_versao",1,"evento_id",event,"dispositivo_id",o.device(),"quadra_id",o.court(),
                "timestamp",timestamp.toString(),"objeto",new HashMap<>(Map.of("bucket",BUCKET,"key",key,"sha256",hash))));
    }
    HttpResponse<String> register(Origin o,Map<String,Object> input) throws Exception {
        return gateway("POST","/api/v1/replays",o.secret(),(String)input.get("evento_id"),input);
    }
    @SuppressWarnings("unchecked") Map<String,Object> object(Map<String,Object> input) { return (Map<String,Object>)input.get("objeto"); }

    @Test void publicationIsVersionedScopedDurableAndNeverExposesSecretsInLogs(CapturedOutput output) throws Exception {
        var o=origin(); var other=origin();
        var absent=gateway("GET",configPath(o),o.secret(),null,null);
        assertThat(absent.statusCode()).isEqualTo(503); assertThat(body(absent)).containsEntry("code","CONFIGURATION_NOT_READY");
        assertThat(absent.headers().firstValue("Retry-After")).hasValue("5");
        String url="rtsp://fixture-user:fixture-camera-secret@192.0.2.10/stream";
        var response=publish(o,null,Map.of("CAMERA_RTSP_URL",url));
        assertThat(response.statusCode()).isEqualTo(201);
        var publication=body(response); String version=(String)publication.get("version");
        assertThat(publication).containsOnlyKeys("dispositivo_id","version","settings","published_by","published_at");
        assertThat(publication.get("published_by")).isEqualTo(o.actor().toString());
        assertThat(Instant.parse((String)publication.get("published_at"))).isEqualTo(CLOCK.instant());
        var current=gateway("GET",configPath(o),o.secret(),null,null);
        assertThat(current.statusCode()).isEqualTo(200); assertThat(body(current)).containsOnlyKeys("version","active","settings").containsEntry("active",true);
        assertThat(body(current).get("settings")).isEqualTo(publication.get("settings"));
        assertThat(current.headers().firstValue("Cache-Control")).hasValue("no-store");
        var denied=gateway("GET",configPath(o),other.secret(),null,null);
        assertThat(denied.statusCode()).isEqualTo(401); assertThat(denied.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(gateway("GET",configPath(o),null,null,null).statusCode()).isEqualTo(401);
        var second=publish(o,version,Map.of("MAX_PENDING_EVENTS",20));
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.body()).contains(url);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_configuration WHERE dispositivo_id=?",Integer.class,o.device())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administrative_audit WHERE resource_id=? AND action='CONFIG_PUBLISH' AND outcome='SUCCESS'",Integer.class,o.device())).isEqualTo(2);
        assertThat(request("GET","/api/v1/admin/dispositivos/"+o.device(),o.admin(),null).body()).doesNotContain(url,"fixture-camera-secret");
        UUID tenant=user(false);
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)",tenant,other.company());
        assertThat(request("POST",publishPath(o),token(tenant),publishBody((String)body(second).get("version"),Map.of("CAMERA_RTSP_URL",url))).statusCode()).isEqualTo(403);
        assertThat(new ConfigPublication(o.device(),version,Map.of("CAMERA_RTSP_URL",url),o.actor(),CLOCK.instant()).toString()).doesNotContain(url);
        assertThat(new GatewayConfig(version,true,Map.of("CAMERA_RTSP_URL",url)).toString()).doesNotContain(url);
        assertThat(output.getAll()).doesNotContain(url,o.secret(),"fixture-camera-secret");
        // Somente dados sinteticos sem credenciais exportados.
        var safe=publish(other,null,Map.of("CAMERA_RTSP_URL","rtsp://192.0.2.11/stream"));
        Files.writeString(Path.of("target/be008-config-responses.json"),mapper.writeValueAsString(List.of(
                Map.of("schema","ConfigPublication","body",body(safe)),
                Map.of("schema","GatewayConfig","body",body(gateway("GET",configPath(other),other.secret(),null,null))))));
    }

    @Test void invalidOrStalePublicationNeverReplacesCurrentAndConcurrentPublishHasOneWinner() throws Exception {
        var o=origin();
        assertThat(publish(o,null,Map.of("MAX_PENDING_EVENTS",5)).statusCode()).isEqualTo(422);
        var first=publish(o,null,Map.of("CAMERA_SOURCE","dshow","CAMERA_DEVICE_NAME","Fixture"));
        assertThat(first.statusCode()).isEqualTo(201); String version=(String)body(first).get("version");
        for(var invalid:List.of(Map.<String,Object>of("DEVICE_ID","other"),Map.<String,Object>of("MAX_PENDING_EVENTS",1.0),
                Map.<String,Object>of("MIN_FREE_BYTES",0),Map.<String,Object>of("CAMERA_RTSP_URL","http://192.0.2.1"),
                Map.<String,Object>of("KEYFRAME_INTERVAL",601),Map.<String,Object>of("CAMERA_SOURCE","other"))) {
            assertThat(publish(o,version,invalid).statusCode()).isEqualTo(422);
            assertThat(body(gateway("GET",configPath(o),o.secret(),null,null)).get("version")).isEqualTo(version);
        }
        assertThat(publish(o,null,Map.of("MAX_PENDING_EVENTS",5)).statusCode()).isEqualTo(409);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            Callable<Integer> task=()->{ start.await(); return publish(o,version,Map.of("MAX_PENDING_EVENTS",10)).statusCode(); };
            var a=pool.submit(task); var b=pool.submit(task); start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_configuration WHERE dispositivo_id=?",Integer.class,o.device())).isEqualTo(2);
    }

    @Test void inactiveOriginRefusesConfigurationPublicationAndReplayIncludingRetries() throws Exception {
        var o=origin(); assertThat(publish(o,null,Map.of("CAMERA_SOURCE","dshow")).statusCode()).isEqualTo(201);
        var input=replay(o,id(),CLOCK.instant(),true); assertThat(register(o,input).statusCode()).isEqualTo(201);
        for(var entry:Map.of("dispositivo",o.device(),"quadra",o.court(),"empresa",o.company()).entrySet()) {
            jdbc.update("UPDATE "+entry.getKey()+" SET active=false WHERE id=?",entry.getValue());
            assertThat(gateway("GET",configPath(o),o.secret(),null,null).statusCode()).isEqualTo(403);
            assertThat(publish(o,null,Map.of("CAMERA_SOURCE","dshow")).statusCode()).isEqualTo(403);
            assertThat(register(o,input).statusCode()).isEqualTo(403);
            jdbc.update("UPDATE "+entry.getKey()+" SET active=true WHERE id=?",entry.getValue());
        }
    }

    @Test void replayRetryPreservesPolicyTimeAndPurgedIdentityWithoutTouchingStorage() throws Exception {
        var o=origin(); var input=replay(o,id(),CLOCK.instant().minusSeconds(3600),true);
        var first=register(o,input); assertThat(first.statusCode()).isEqualTo(201);
        var event=(String)input.get("evento_id");
        var before=jdbc.queryForMap("SELECT * FROM replay WHERE evento_id=?",event);
        jdbc.update("UPDATE empresa SET retencao_dias=30 WHERE id=?",o.company());
        jdbc.update("UPDATE replay SET purged_at=? WHERE evento_id=?",CLOCK.instant().atOffset(ZoneOffset.UTC),event);
        S3.objects.clear(); int heads=S3.heads.get();
        // O mesmo instante com outro offset tambem e o mesmo evento.
        input.put("timestamp",Instant.parse((String)input.get("timestamp")).atOffset(ZoneOffset.ofHours(-3)).toString());
        var retry=register(o,input); assertThat(retry.statusCode()).isEqualTo(200); assertThat(body(retry)).isEqualTo(body(first));
        assertThat(S3.heads.get()).isEqualTo(heads);
        var after=jdbc.queryForMap("SELECT * FROM replay WHERE evento_id=?",event);
        for(var key:List.of("event_at","received_at","expires_at","retention_days","sha256","expired_on_arrival")) assertThat(after.get(key)).isEqualTo(before.get(key));
        object(input).put("sha256","a".repeat(64));
        assertThat(register(o,input).statusCode()).isEqualTo(409); assertThat(S3.heads.get()).isEqualTo(heads);
        Files.writeString(Path.of("target/be011-replay-responses.json"),mapper.writeValueAsString(List.of(Map.of("schema","ReplayAck","body",body(first)),Map.of("schema","ReplayAck","body",body(retry)))));
    }

    @Test void expiredArrivalAndFutureBoundaryPreserveOriginalInstants() throws Exception {
        var o=origin(); var expired=replay(o,id(),CLOCK.instant().minusSeconds(2*86400),true);
        assertThat(register(o,expired).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT expired_on_arrival FROM replay WHERE evento_id=?",Boolean.class,expired.get("evento_id"))).isTrue();
        assertThat(register(o,replay(o,id(),CLOCK.instant().plusSeconds(300),true)).statusCode()).isEqualTo(201);
        assertThat(register(o,replay(o,id(),CLOCK.instant().plusSeconds(301),true)).statusCode()).isEqualTo(422);
        var adjacent=replay(o,id(),CLOCK.instant(),true); var adjacent2=replay(o,id(),CLOCK.instant().plusSeconds(1),true);
        assertThat(register(o,adjacent).statusCode()).isEqualTo(201); assertThat(register(o,adjacent2).statusCode()).isEqualTo(201);
    }

    @Test void originAndPayloadChecksPrecedeAllStorageCalls() throws Exception {
        var o=origin(); var other=origin(); var input=replay(o,id(),CLOCK.instant(),true);
        int heads=S3.heads.get();
        assertThat(gateway("POST","/api/v1/replays",null,(String)input.get("evento_id"),input).statusCode()).isEqualTo(401);
        assertThat(gateway("POST","/api/v1/replays",other.secret(),(String)input.get("evento_id"),input).statusCode()).isEqualTo(401);
        input.put("quadra_id",other.court()); assertThat(register(o,input).statusCode()).isEqualTo(403); input.put("quadra_id",o.court());
        object(input).put("bucket","other-private"); assertThat(register(o,input).statusCode()).isEqualTo(403); object(input).put("bucket",BUCKET);
        object(input).put("key","https://192.0.2.1/private"); assertThat(register(o,input).statusCode()).isEqualTo(403);
        input=replay(o,id(),CLOCK.instant(),false);
        assertThat(gateway("POST","/api/v1/replays",o.secret(),id(),input).statusCode()).isEqualTo(400);
        input.put("timestamp","2026-10-16T10:00:00"); assertThat(register(o,input).statusCode()).isEqualTo(422);
        input.put("timestamp",CLOCK.instant().toString()); input.put("empresa_id",other.company()); assertThat(register(o,input).statusCode()).isEqualTo(422);
        assertThat(S3.heads.get()).isEqualTo(heads); assertThat(S3.mutations.get()).isZero();
    }

    @Test void storageFailuresRemainRetryableAndBytesAreActuallyHashed() throws Exception {
        var o=origin(); var input=replay(o,id(),CLOCK.instant(),false);
        assertThat(register(o,input).statusCode()).isEqualTo(503);
        input=replay(o,id(),CLOCK.instant(),true);
        for(int status:List.of(403,429,500,503)) {
            S3.failureStatus=status; assertThat(register(o,input).statusCode()).isEqualTo(503);
        }
        S3.failureStatus=0; S3.changeOnGet=true; assertThat(register(o,input).statusCode()).isEqualTo(503); S3.changeOnGet=false;
        String path="/"+BUCKET+"/"+object(input).get("key"); var stored=S3.objects.get(path);
        S3.objects.put(path,new S3Fixture.ObjectData("altered".getBytes(StandardCharsets.UTF_8),stored.metadata(),null,stored.etag()));
        assertThat(register(o,input).statusCode()).isEqualTo(409);
        S3.objects.put(path,new S3Fixture.ObjectData(new byte[0],stored.metadata(),null,stored.etag()));
        assertThat(register(o,input).statusCode()).isEqualTo(422);
        S3.objects.put(path,new S3Fixture.ObjectData(stored.bytes(),"a".repeat(64),null,stored.etag()));
        assertThat(register(o,input).statusCode()).isEqualTo(409);
        S3.objects.put(path,new S3Fixture.ObjectData(stored.bytes(),stored.metadata(),"fixture-version-1",stored.etag()));
        assertThat(register(o,input).statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT object_version FROM replay WHERE evento_id=?",String.class,input.get("evento_id"))).isEqualTo("fixture-version-1");
        assertThat(S3.mutations.get()).isZero();
    }

    @Test void simultaneousIngestionConvergesAndDatabaseRejectsContradictoryOriginAndMutation() throws Exception {
        var o=origin(); var other=origin(); var input=replay(o,id(),CLOCK.instant(),true);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1); Callable<Integer> task=()->{start.await();return register(o,input).statusCode();};
            var a=pool.submit(task); var b=pool.submit(task); start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,200);
        }
        String event=(String)input.get("evento_id");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM replay WHERE evento_id=?",Integer.class,event)).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("UPDATE replay SET retention_days=30 WHERE evento_id=?",event)).isInstanceOf(DataIntegrityViolationException.class);
        String different=id();
        assertThatThrownBy(()->jdbc.update("""
                INSERT INTO replay SELECT ?,dispositivo_id,quadra_id,?,event_at,received_at,retention_days,expires_at,
                  expired_on_arrival,bucket,?,sha256,object_version,object_etag,object_size,purged_at FROM replay WHERE evento_id=?
                """,different,other.company(),"replays/"+o.device()+"/"+different+".mp4",event)).isInstanceOf(DataIntegrityViolationException.class);
        // Banco sem API: chaves e evento duplicados tambem sao recusados.
        assertThatThrownBy(()->jdbc.update("INSERT INTO replay SELECT * FROM replay WHERE evento_id=?",event)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void failedAuditRollsBackPublicationAndFailedReplayCommitNeverAcknowledges() throws Exception {
        var o=origin();
        String suffix=id(); String function="fail_gateway_"+suffix;
        jdbc.execute("CREATE FUNCTION "+function+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic commit failure' USING ERRCODE='23514'; END; $$");
        String auditTrigger="audit_"+suffix, replayTrigger="replay_"+suffix;
        try {
            jdbc.execute("CREATE TRIGGER "+auditTrigger+" BEFORE INSERT ON administrative_audit FOR EACH ROW WHEN (NEW.actor_id='"+o.actor()+"'::uuid AND NEW.action='CONFIG_PUBLISH' AND NEW.outcome='SUCCESS') EXECUTE FUNCTION "+function+"()");
            assertThat(publish(o,null,Map.of("CAMERA_SOURCE","dshow")).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM device_configuration WHERE dispositivo_id=?",Integer.class,o.device())).isZero();
            jdbc.execute("DROP TRIGGER "+auditTrigger+" ON administrative_audit");
            var input=replay(o,id(),CLOCK.instant(),true);
            // Deferred trigger fails at COMMIT, after the service has built its result.
            jdbc.execute("CREATE CONSTRAINT TRIGGER "+replayTrigger+" AFTER INSERT ON replay DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.dispositivo_id='"+o.device()+"') EXECUTE FUNCTION "+function+"()");
            assertThat(register(o,input).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM replay WHERE evento_id=?",Integer.class,input.get("evento_id"))).isZero();
            jdbc.execute("DROP TRIGGER "+replayTrigger+" ON replay");
            assertThat(register(o,input).statusCode()).isEqualTo(201);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS "+auditTrigger+" ON administrative_audit");
            jdbc.execute("DROP TRIGGER IF EXISTS "+replayTrigger+" ON replay");
            jdbc.execute("DROP FUNCTION IF EXISTS "+function+"()");
        }
    }

    @Test void realPythonGatewayConsumesConfigurationRetriesLostAckAndClassifiesErrors(CapturedOutput output) throws Exception {
        var o=origin(); assertThat(publish(o,null,Map.of("CAMERA_RTSP_URL","rtsp://192.0.2.20/stream")).statusCode()).isEqualTo(201);
        String event=id(); var input=replay(o,event,CLOCK.instant(),true);
        var parameters=Map.of("base_url",baseUrl(),"device_id",o.device(),"device_token",o.secret(),"court_id",o.court(),
                "bucket",BUCKET,"event_id",event,"timestamp",input.get("timestamp"),"sha256",object(input).get("sha256"));
        var python=Path.of(".venv","Scripts","python.exe");
        if(!Files.exists(python)) python=Path.of(".venv","bin","python");
        assertThat(python).exists();
        var process=new ProcessBuilder(python.toAbsolutePath().toString(),"-X","utf8","tools/gateway_integration.py").redirectErrorStream(true).start();
        try(var stdin=process.getOutputStream()) { stdin.write(mapper.writeValueAsBytes(parameters)); }
        assertThat(process.waitFor(45,TimeUnit.SECONDS)).isTrue();
        String result=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertThat(process.exitValue()).withFailMessage("Gateway integration failed: %s",result).isZero();
        assertThat(result).contains("GATEWAY_INTEGRATION_OK").doesNotContain(o.secret());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM replay WHERE evento_id=?",Integer.class,event)).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(o.secret());
        Files.writeString(Path.of("target/be012-gateway-result.txt"),result);
    }

    @Test void operationStatusChangesApplyImmediatelyToConfigAndReplayWithoutChangingHistory() throws Exception {
        var o=origin();
        assertThat(publish(o,null,Map.of("CAMERA_SOURCE","dshow")).statusCode()).isEqualTo(201);
        var input=replay(o,id(),CLOCK.instant(),true);
        assertThat(register(o,input).statusCode()).isEqualTo(201);
        var before=jdbc.queryForMap("SELECT * FROM replay WHERE evento_id=?",input.get("evento_id"));
        String version=(String)body(gateway("GET",configPath(o),o.secret(),null,null)).get("version");
        List<Map<String,Object>> responses=new ArrayList<>();
        var paths=List.of("/api/v1/admin/dispositivos/"+o.device()+"/status",
                "/api/v1/admin/empresas/"+o.company()+"/quadras/"+o.court()+"/status",
                "/api/v1/admin/empresas/"+o.company()+"/status");
        var schemas=List.of("Dispositivo","Quadra","Empresa");
        var actions=List.of("DEVICE_STATUS_CHANGE","COURT_STATUS_CHANGE","COMPANY_STATUS_CHANGE");
        for(int i=0;i<paths.size();i++) {
            var response=request("PATCH",paths.get(i),o.admin(),Map.of("active",false));
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(body(response)).containsEntry("active",false);
            responses.add(Map.of("schema",schemas.get(i),"body",body(response)));
            assertAudit(response,actions.get(i),"SUCCESS");
            assertThat(gateway("GET",configPath(o),o.secret(),null,null).statusCode()).isEqualTo(403);
            assertThat(register(o,input).statusCode()).isEqualTo(403);
            assertThat(request("PATCH",paths.get(i),o.admin(),Map.of("active",false)).statusCode()).isEqualTo(200);
            assertThat(request("PATCH",paths.get(i),o.admin(),Map.of("active",true)).statusCode()).isEqualTo(200);
            assertThat(register(o,input).statusCode()).isEqualTo(200);
        }
        assertThat(jdbc.queryForMap("SELECT * FROM replay WHERE evento_id=?",input.get("evento_id"))).isEqualTo(before);
        assertThat(body(gateway("GET",configPath(o),o.secret(),null,null))).containsEntry("version",version);
        Files.writeString(Path.of("target/be026-operation-responses.json"),mapper.writeValueAsString(responses));
    }

    @Test void rotationRevokesOldCredentialEvenForLostAckRetryAndOnlyReturnsNewSecretOnce(CapturedOutput output) throws Exception {
        var o=origin();
        assertThat(publish(o,null,Map.of("CAMERA_SOURCE","dshow")).statusCode()).isEqualTo(201);
        var input=replay(o,id(),CLOCK.instant(),true);
        assertThat(register(o,input).statusCode()).isEqualTo(201);
        var before=jdbc.queryForMap("SELECT * FROM replay WHERE evento_id=?",input.get("evento_id"));
        var response=request("POST",rotationPath(o),o.admin(),Map.of());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(body(response)).containsOnlyKeys("dispositivo","device_token");
        String secret=(String)body(response).get("device_token");
        assertThat(secret).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(o.secret());
        String hash=jdbc.queryForObject("SELECT token_hash FROM dispositivo WHERE id=?",String.class,o.device());
        assertThat(hash).isEqualTo(S3Fixture.hash(secret.getBytes(StandardCharsets.UTF_8)));
        assertAudit(response,"DEVICE_TOKEN_ROTATE","SUCCESS");
        assertThat(gateway("GET",configPath(o),o.secret(),null,null).statusCode()).isEqualTo(401);
        assertThat(register(o,input).statusCode()).isEqualTo(401);
        assertThat(gateway("GET",configPath(o),secret,null,null).statusCode()).isEqualTo(200);
        int heads=S3.heads.get();
        assertThat(gateway("POST","/api/v1/replays",secret,(String)input.get("evento_id"),input).statusCode()).isEqualTo(200);
        assertThat(S3.heads.get()).isEqualTo(heads);
        assertThat(jdbc.queryForMap("SELECT * FROM replay WHERE evento_id=?",input.get("evento_id"))).isEqualTo(before);
        var detail=request("GET","/api/v1/admin/dispositivos/"+o.device(),o.admin(),null);
        assertThat(detail.body()).doesNotContain(secret,hash,"device_token","token_hash");
        var second=request("POST",rotationPath(o),o.admin(),Map.of());
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(body(second).get("device_token")).isNotEqualTo(secret);
        assertThat(gateway("GET",configPath(o),secret,null,null).statusCode()).isEqualTo(401);
        assertThat(output.getAll()).doesNotContain(secret,hash,o.secret());
    }

    @Test void operationalChangesRequireCorrectTenantAndStrictBodyAndCompanyStatusIsPlatformOnly() throws Exception {
        var o=origin(); var other=origin(); UUID actor=user(false);
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)",actor,o.company());
        String tenant=token(actor);
        var ownDevice="/api/v1/admin/dispositivos/"+o.device()+"/status";
        var otherDevice="/api/v1/admin/dispositivos/"+other.device()+"/status";
        assertThat(request("PATCH",ownDevice,tenant,Map.of("active",false)).statusCode()).isEqualTo(200);
        assertThat(request("PATCH",ownDevice,tenant,Map.of("active",true)).statusCode()).isEqualTo(200);
        assertThat(request("POST",rotationPath(o),tenant,Map.of()).statusCode()).isEqualTo(200);
        var denied=request("PATCH",otherDevice,tenant,Map.of("active",false));
        assertThat(denied.statusCode()).isEqualTo(403); assertAudit(denied,"DEVICE_STATUS_CHANGE","REFUSED");
        denied=request("POST",rotationPath(other),tenant,Map.of());
        assertThat(denied.statusCode()).isEqualTo(403); assertAudit(denied,"DEVICE_TOKEN_ROTATE","REFUSED");
        denied=request("PATCH","/api/v1/admin/empresas/"+other.company()+"/quadras/"+other.court()+"/status",tenant,Map.of("active",false));
        assertThat(denied.statusCode()).isEqualTo(403); assertAudit(denied,"COURT_STATUS_CHANGE","REFUSED");
        denied=request("PATCH","/api/v1/admin/empresas/"+o.company()+"/status",tenant,Map.of("active",false));
        assertThat(denied.statusCode()).isEqualTo(403); assertAudit(denied,"COMPANY_STATUS_CHANGE","REFUSED");
        assertThat(request("PATCH",ownDevice,null,Map.of("active",false)).statusCode()).isEqualTo(401);
        for(var invalid:List.of(Map.of(),Map.of("active","false"),Map.of("active",false,"quadra_id",other.court()))) {
            assertThat(request("PATCH",ownDevice,tenant,invalid).statusCode()).isEqualTo(422);
        }
        assertThat(request("POST",rotationPath(o),tenant,Map.of("device_token","caller-chosen-token")).statusCode()).isEqualTo(422);
        assertThat(request("POST",rotationPath(o),tenant,null).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT active FROM dispositivo WHERE id=?",Boolean.class,o.device())).isTrue();
        assertThat(jdbc.queryForObject("SELECT active FROM dispositivo WHERE id=?",Boolean.class,other.device())).isTrue();
        assertThat(request("PATCH","/api/v1/admin/empresas/"+o.company()+"/quadras/"+other.court()+"/status",tenant,Map.of("active",false)).statusCode()).isEqualTo(404);
    }

    @Test void auditFailureRollsBackStatusAndRotationWithoutRevealingAnUncommittedCredential(CapturedOutput output) throws Exception {
        var o=origin();
        String before=jdbc.queryForObject("SELECT token_hash FROM dispositivo WHERE id=?",String.class,o.device());
        String function="be026_fail_"+id();
        jdbc.execute("CREATE FUNCTION "+function+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id='"+o.actor()+"'::uuid AND NEW.outcome='SUCCESS' THEN RAISE EXCEPTION 'synthetic audit failure' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER "+function+" BEFORE INSERT ON administrative_audit FOR EACH ROW EXECUTE FUNCTION "+function+"()");
        try {
            var responses=List.of(request("POST",rotationPath(o),o.admin(),Map.of()),
                    request("PATCH","/api/v1/admin/dispositivos/"+o.device()+"/status",o.admin(),Map.of("active",false)),
                    request("PATCH","/api/v1/admin/empresas/"+o.company()+"/quadras/"+o.court()+"/status",o.admin(),Map.of("active",false)),
                    request("PATCH","/api/v1/admin/empresas/"+o.company()+"/status",o.admin(),Map.of("active",false)));
            for(var response:responses) {
                assertThat(response.statusCode()).isEqualTo(503);
                assertThat(response.body()).doesNotContain("device_token",o.secret(),before);
                String correlation=response.headers().firstValue("X-Correlation-ID").orElseThrow();
                assertThat(jdbc.queryForObject("SELECT outcome FROM administrative_audit WHERE correlation_id=?",String.class,UUID.fromString(correlation))).isEqualTo("FAILED");
                assertThat(output.getAll()).doesNotContain("correlation_id="+correlation+" action=DEVICE_TOKEN_ROTATE outcome=SUCCESS");
            }
            assertThat(jdbc.queryForObject("SELECT token_hash FROM dispositivo WHERE id=?",String.class,o.device())).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT active FROM dispositivo WHERE id=?",Boolean.class,o.device())).isTrue();
            assertThat(jdbc.queryForObject("SELECT active FROM quadra WHERE id=?",Boolean.class,o.court())).isTrue();
            assertThat(jdbc.queryForObject("SELECT active FROM empresa WHERE id=?",Boolean.class,o.company())).isTrue();
        } finally {
            jdbc.execute("DROP TRIGGER "+function+" ON administrative_audit");
            jdbc.execute("DROP FUNCTION "+function+"()");
        }
    }

    @Test void rotationWaitsForAuthenticatedReplayCommitThenRejectsTheOldToken() throws Exception {
        var o=origin(); var input=replay(o,id(),CLOCK.instant(),true);
        var transactions=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var registered=new CountDownLatch(1); var allowCommit=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var ingest=pool.submit(()->transactions.execute(status->{
                var result=context.getBean(br.com.clicklance.service.ReplayService.class).register(o.secret(),(String)input.get("evento_id"),input);
                registered.countDown();
                try { if(!allowCommit.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("commit release timed out"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return result;
            }));
            try {
                assertThat(registered.await(10,TimeUnit.SECONDS)).isTrue();
                var rotation=pool.submit(()->request("POST",rotationPath(o),o.admin(),Map.of()));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                boolean blocked=false;
                while(System.nanoTime()<deadline) {
                    blocked=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE 'UPDATE dispositivo d SET token_hash=%')",Boolean.class);
                    if(blocked) break;
                    Thread.sleep(10);
                }
                assertThat(blocked).as("rotation waits on the in-flight replay transaction").isTrue();
                assertThat(rotation.isDone()).isFalse();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM replay WHERE evento_id=?",Integer.class,input.get("evento_id"))).isZero();
                allowCommit.countDown();
                assertThat(ingest.get(10,TimeUnit.SECONDS).created()).isTrue();
                var response=rotation.get(10,TimeUnit.SECONDS);
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(register(o,input).statusCode()).isEqualTo(401);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM replay WHERE evento_id=?",Integer.class,input.get("evento_id"))).isEqualTo(1);
            } finally { allowCommit.countDown(); }
        }
    }

    @Test void pythonGatewayPersistsOperationalInactivationAcrossOfflineRestartAndRecoversAfterRotation(CapturedOutput output) throws Exception {
        var o=origin(); assertThat(publish(o,null,Map.of("CAMERA_RTSP_URL","rtsp://192.0.2.20/stream")).statusCode()).isEqualTo(201);
        String event=id(); var input=replay(o,event,CLOCK.instant(),true);
        var parameters=new HashMap<String,Object>(Map.of("base_url",baseUrl(),"device_id",o.device(),"device_token",o.secret(),"court_id",o.court(),
                "bucket",BUCKET,"event_id",event,"timestamp",input.get("timestamp"),"sha256",object(input).get("sha256"),"company_id",o.company(),"admin_token",o.admin()));
        var python=Path.of(".venv","Scripts","python.exe");
        if(!Files.exists(python)) python=Path.of(".venv","bin","python");
        assertThat(python).exists();
        var process=new ProcessBuilder(python.toAbsolutePath().toString(),"-X","utf8","tools/gateway_operations.py").redirectErrorStream(true).start();
        try(var stdin=process.getOutputStream()) { stdin.write(mapper.writeValueAsBytes(parameters)); }
        assertThat(process.waitFor(45,TimeUnit.SECONDS)).isTrue();
        String result=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertThat(process.exitValue()).withFailMessage("Gateway operations failed: %s",result).isZero();
        assertThat(result).contains("GATEWAY_OPERATIONS_OK").doesNotContain(o.secret(),o.admin());
        assertThat(output.getAll()).doesNotContain(o.secret(),o.admin());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM replay WHERE evento_id=?",Integer.class,event)).isEqualTo(1);
        Files.writeString(Path.of("target/be026-gateway-result.txt"),result);
    }

    String rotationPath(Origin o) { return "/api/v1/admin/dispositivos/"+o.device()+"/credenciais/rotacao"; }
    void assertAudit(HttpResponse<String> response,String action,String outcome) {
        UUID correlation=UUID.fromString(response.headers().firstValue("X-Correlation-ID").orElseThrow());
        var entry=jdbc.queryForMap("SELECT action,outcome FROM administrative_audit WHERE correlation_id=?",correlation);
        assertThat(entry).containsEntry("action",action).containsEntry("outcome",outcome);
    }
}
