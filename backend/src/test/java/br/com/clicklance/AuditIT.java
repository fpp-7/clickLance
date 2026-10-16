package br.com.clicklance;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "clicklance.devices.allowed-buckets=be006-private-test,be006-second-test")
@Import(AdminTestSupport.TimeConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AuditIT extends AdminTestSupport {
    final List<String> courts = new ArrayList<>();
    final List<String> devices = new ArrayList<>();

    @AfterEach void removeResources() {
        for (String id : devices) jdbc.update("DELETE FROM dispositivo WHERE id=?", id);
        for (String id : courts) jdbc.update("DELETE FROM quadra WHERE id=?", id);
    }

    String company() {
        String id = UUID.randomUUID().toString(); companies.add(id);
        jdbc.update("INSERT INTO empresa(id,nome,active) VALUES (?, 'Audit fixture', true)", id);
        return id;
    }
    String court(String company) {
        String id = UUID.randomUUID().toString(); courts.add(id);
        jdbc.update("INSERT INTO quadra(id,empresa_id,nome,active,timezone,replay_before_seconds,replay_after_seconds,segment_duration_seconds,keyframe_interval) VALUES (?,?,'Audit court',true,'UTC',120,0,60,30)", id, company);
        return id;
    }
    String courtPath(String company) { return "/api/v1/admin/empresas/" + company + "/quadras"; }
    String auditPath(String company) { return "/api/v1/admin/empresas/" + company + "/auditoria"; }
    String correlationPath(String id) { return "/api/v1/admin/auditoria/" + id; }
    Map<String, Object> courtBody() {
        return Map.of("nome", "Court HTTP fixture", "active", true, "timezone", "UTC", "perfil_captura",
                Map.of("REPLAY_BEFORE_SECONDS",120,"REPLAY_AFTER_SECONDS",0,"SEGMENT_DURATION_SECONDS",60,"KEYFRAME_INTERVAL",30));
    }
    String correlation(HttpResponse<String> response) {
        String id = response.headers().firstValue("X-Correlation-ID").orElseThrow();
        assertThat(UUID.fromString(id)).isNotNull(); return id;
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries(HttpResponse<String> response) { return mapper.readValue(response.body(), List.class); }
    Map<String, Object> one(String id, String admin) throws Exception {
        var response = request("GET", correlationPath(id), admin, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(entries(response)).hasSize(1); return entries(response).getFirst();
    }
    String tenant(String company) {
        UUID id = user(false);
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)", id, company);
        return token(id);
    }

    @Test void creationsShareResponseCorrelationAndNeverPersistBusinessPayloads(CapturedOutput output) throws Exception {
        UUID actor = user(true); String admin = token(actor);
        String sensitiveName = "not-audit-payload-" + UUID.randomUUID();
        var companyResponse = request("POST", "/api/v1/admin/empresas", admin, Map.of("nome",sensitiveName,"active",true));
        assertThat(companyResponse.statusCode()).isEqualTo(201);
        String company = (String)body(companyResponse).get("empresa_id"); companies.add(company);
        var courtResponse = request("POST", courtPath(company), admin, courtBody());
        assertThat(courtResponse.statusCode()).isEqualTo(201);
        String court = (String)body(courtResponse).get("quadra_id"); courts.add(court);
        String device = UUID.randomUUID().toString().replace("-",""); devices.add(device);
        var deviceResponse = request("POST", courtPath(company) + "/" + court + "/dispositivos", admin,
                Map.of("dispositivo_id",device,"active",true,"bucket","be006-private-test"));
        assertThat(deviceResponse.statusCode()).isEqualTo(201);
        String secret = (String)body(deviceResponse).get("device_token");
        String hash = jdbc.queryForObject("SELECT token_hash FROM dispositivo WHERE id=?",String.class,device);
        var responses = List.of(companyResponse,courtResponse,deviceResponse);
        var actions = List.of("COMPANY_CREATE","COURT_CREATE","DEVICE_CREATE");
        var ids = List.of(company,court,device);
        for (int i=0;i<responses.size();i++) {
            var entry = one(correlation(responses.get(i)),admin);
            assertThat(entry).containsEntry("action",actions.get(i)).containsEntry("outcome","SUCCESS")
                    .containsEntry("actor_id",actor.toString()).containsEntry("resource_id",ids.get(i))
                    .containsEntry("empresa_id",company).containsEntry("code","OK").containsEntry("http_status",201);
            assertThat(java.time.Instant.parse((String)entry.get("occurred_at"))).isEqualTo(CLOCK.instant());
            assertThat(entry.toString()).doesNotContain(sensitiveName,secret,hash,PASSWORD,PASSWORD_HASH,admin);
        }
        var page = request("GET",auditPath(company),admin,null);
        assertThat(body(page)).containsEntry("total",3);
        assertThat(output.getAll()).contains("administrative_audit",correlation(deviceResponse))
                .doesNotContain(sensitiveName,secret,hash,PASSWORD,PASSWORD_HASH,admin);
        Files.writeString(Path.of("target/be007-audit-responses.json"),mapper.writeValueAsString(List.of(
                Map.of("schema","AuditPage","body",body(page)),
                Map.of("schema","AuditEntry","body",one(correlation(companyResponse),admin)))));
    }

    @Test void refusalSurvivesRollbackAndAuditOfOtherCompanyIsDenied() throws Exception {
        String own=company(), other=company(), admin=token(user(true)), tenant=tenant(own);
        var denied=request("POST",courtPath(other),tenant,courtBody());
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(body(denied)).containsEntry("correlation_id",correlation(denied));
        var entry=one(correlation(denied),admin);
        assertThat(entry).containsEntry("action","COURT_CREATE").containsEntry("outcome","REFUSED")
                .containsEntry("code","FORBIDDEN").containsEntry("empresa_id",other).containsEntry("resource_id",null);
        assertThat(entry.get("actor_id")).isNotNull();
        assertThat(request("GET",auditPath(other),tenant,null).statusCode()).isEqualTo(403);
        assertThat(request("GET",correlationPath(correlation(denied)),tenant,null).statusCode()).isEqualTo(403);
        assertThat((List<?>)body(request("GET",auditPath(own),tenant,null)).get("items")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quadra WHERE empresa_id=?",Integer.class,other)).isZero();
    }

    @Test void frameworkSecurityAndValidationErrorsHaveOneSafeEnvelopeAndServerCorrelation(CapturedOutput output) throws Exception {
        String company=company(), admin=token(user(true)), marker="secret-path-"+UUID.randomUUID();
        String supplied=UUID.randomUUID().toString();
        String cameraSecret="rtsp://camera-user:"+marker+"@192.0.2.10/stream";
        var sensitiveBody=new HashMap<>(courtBody()); sensitiveBody.put("CAMERA_RTSP_URL",cameraSecret);
        var responses=List.of(
                raw("GET",auditPath(company),null,null,null,supplied),
                raw("POST",courtPath(company),admin,"{\"password\":\""+marker+"\",", "application/json",supplied),
                raw("DELETE","/api/v1/admin/empresas",admin,null,null,supplied),
                raw("POST",courtPath(company),admin,marker,"text/plain",supplied),
                raw("GET","/api/v1/admin/unknown/"+marker,admin,null,null,supplied),
                request("POST",courtPath(company),admin,sensitiveBody));
        assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactly(401,400,405,415,404,422);
        var ids=new HashSet<String>();
        for (var response:responses) {
            assertThat(body(response)).containsKeys("code","message","correlation_id");
            assertThat(body(response).keySet()).isSubsetOf(Set.of("code","message","correlation_id","violations"));
            assertThat(body(response)).containsEntry("correlation_id",correlation(response));
            assertThat(correlation(response)).isNotEqualTo(supplied);
            ids.add(correlation(response));
            var entry=one(correlation(response),admin);
            assertThat(entry.get("outcome")).isEqualTo("REFUSED");
            assertThat(entry.toString()).doesNotContain(marker,admin,"password",cameraSecret);
            assertThat(response.body()).doesNotContain(marker,admin,"stackTrace",cameraSecret);
        }
        assertThat(ids).hasSize(responses.size());
        assertThat(output.getAll()).doesNotContain(marker,admin,cameraSecret);
    }

    @Test void failedCriticalAuditRollsBackCompanyCourtAndDeviceAndNeverLogsSuccess(CapturedOutput output) throws Exception {
        UUID actor=user(true); String admin=token(actor), company=company(), court=court(company);
        String marker="private-database-detail-"+UUID.randomUUID();
        String trigger=installFailure(actor,"SUCCESS",marker);
        try {
            int before=jdbc.queryForObject("SELECT count(*) FROM empresa",Integer.class);
            var a=request("POST","/api/v1/admin/empresas",admin,Map.of("nome","Rollback fixture","active",true));
            var b=request("POST",courtPath(company),admin,courtBody());
            String id=UUID.randomUUID().toString().replace("-","");
            var c=request("POST",courtPath(company)+"/"+court+"/dispositivos",admin,
                    Map.of("dispositivo_id",id,"active",true,"bucket","be006-private-test"));
            for (var response:List.of(a,b,c)) {
                assertThat(response.statusCode()).isEqualTo(503);
                assertThat(body(response)).containsEntry("code","DEPENDENCY_UNAVAILABLE").containsEntry("correlation_id",correlation(response));
                assertThat(response.headers().firstValue("Retry-After")).hasValue("5");
                assertThat(one(correlation(response),admin)).containsEntry("outcome","FAILED");
                assertThat(output.getAll()).doesNotContain("correlation_id="+correlation(response)+" action=COMPANY_CREATE outcome=SUCCESS",
                        "correlation_id="+correlation(response)+" action=COURT_CREATE outcome=SUCCESS",
                        "correlation_id="+correlation(response)+" action=DEVICE_CREATE outcome=SUCCESS");
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM empresa",Integer.class)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM quadra WHERE empresa_id=?",Integer.class,company)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositivo WHERE id=?",Integer.class,id)).isZero();
            assertThat(output.getAll()).doesNotContain(marker,admin);
        } finally { removeFailure(trigger); }
    }

    @Test void failureToPersistARefusalReturns503AndEmitsOnlyCorrelation(CapturedOutput output) throws Exception {
        String own=company(), other=company(); UUID actor=user(false);
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)",actor,own);
        String admin=token(actor), marker="must-not-log-"+UUID.randomUUID(), trigger=installFailure(actor,"REFUSED",marker);
        try {
            var response=request("GET",auditPath(other),admin,null);
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(body(response)).containsEntry("code","DEPENDENCY_UNAVAILABLE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM administrative_audit WHERE correlation_id=?",Integer.class,UUID.fromString(correlation(response)))).isZero();
            assertThat(output.getAll()).contains("administrative_audit_unavailable correlation_id="+correlation(response)).doesNotContain(marker,admin);
        } finally { removeFailure(trigger); }
    }

    @Test void paginationHasStableOrderingAndNeverIncludesOtherCompany() throws Exception {
        String company=company(), other=company(), admin=token(user(true)), tenant=tenant(company);
        for (String scope:List.of(company,company,company,other)) {
            var response=request("POST",courtPath(scope),admin,courtBody());
            assertThat(response.statusCode()).isEqualTo(201); courts.add((String)body(response).get("quadra_id"));
        }
        var first=body(request("GET",auditPath(company)+"?size=1",tenant,null));
        var second=body(request("GET",auditPath(company)+"?size=1&page=1",tenant,null));
        assertThat(first).containsEntry("total",3).containsEntry("size",1).containsEntry("page",0);
        assertThat(first.get("items")).isNotEqualTo(second.get("items"));
        assertThat(first.get("items").toString()).contains(company).doesNotContain(other);
        assertThat((List<?>)body(request("GET",auditPath(company)+"?page=2147483647",tenant,null)).get("items")).isEmpty();
        for(String query:List.of("size=0","size=101","page=-1","page=1.0")) {
            assertThat(request("GET",auditPath(company)+"?"+query,tenant,null).statusCode()).isEqualTo(422);
        }
    }

    @Test void auditSnapshotsSurviveResourceRemovalAndPostgresRejectsInvalidMetadata() throws Exception {
        String company=company(), admin=token(user(true));
        var response=request("POST",courtPath(company),admin,courtBody());
        assertThat(response.statusCode()).isEqualTo(201);
        String court=(String)body(response).get("quadra_id"); courts.add(court);
        jdbc.update("DELETE FROM quadra WHERE id=?",court);
        assertThat(one(correlation(response),admin)).containsEntry("resource_id",court).containsEntry("empresa_id",company);
        assertThatThrownBy(()->jdbc.update("""
                INSERT INTO administrative_audit(id,correlation_id,occurred_at,actor_type,action,resource_type,outcome,code)
                VALUES (?, ?,CURRENT_TIMESTAMP,'ADMIN','COURT_CREATE','COURT','SUCCESS','OK')
                """,UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("""
                INSERT INTO administrative_audit(id,correlation_id,occurred_at,actor_type,action,resource_type,outcome,code)
                VALUES (?, ?,CURRENT_TIMESTAMP,'SYSTEM','raw request body','COURT','SUCCESS','OK')
                """,UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void sessionsBootstrapAndCompanyAdminProvisionAreTraceableWithoutCredentials(CapturedOutput output) throws Exception {
        UUID platform=user(true); String admin=token(platform), company=company();
        var login=request("POST","/api/v1/admin/sessoes",null,Map.of("email",email(platform),"password",PASSWORD));
        String issued=(String)body(login).get("access_token");
        assertThat(one(correlation(login),admin)).containsEntry("action","ADMIN_LOGIN").containsEntry("resource_id",platform.toString());
        var logout=request("DELETE","/api/v1/admin/sessoes/atual",issued,null);
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(one(correlation(logout),admin)).containsEntry("action","ADMIN_LOGOUT");
        UUID bootstrap=accounts.bootstrap(UUID.randomUUID()+"@example.invalid",PASSWORD); users.add(bootstrap);
        assertThat(jdbc.queryForObject("SELECT actor_type FROM administrative_audit WHERE action='ADMIN_BOOTSTRAP' AND resource_id=?",String.class,bootstrap.toString())).isEqualTo("SYSTEM");
        UUID provisioned=accounts.provisionCompanyAdmin(sessions.authenticate(admin),UUID.randomUUID()+"@example.invalid",PASSWORD,company); users.add(provisioned);
        assertThat(jdbc.queryForObject("SELECT actor_id FROM administrative_audit WHERE action='COMPANY_ADMIN_PROVISION' AND resource_id=?",UUID.class,provisioned.toString())).isEqualTo(platform);
        assertThat(output.getAll()).doesNotContain(PASSWORD,PASSWORD_HASH,admin,issued,email(platform));
    }

    HttpResponse<String> raw(String method,String path,String token,String content,String type,String suppliedCorrelation) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+environment.getRequiredProperty("local.server.port")+path));
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(type!=null) builder.header("Content-Type",type);
        builder.header("X-Correlation-ID",suppliedCorrelation).method(method,content==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(content));
        var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        correlations.add(UUID.fromString(correlation(response))); return response;
    }

    String installFailure(UUID actor,String outcome,String marker) {
        String name="audit_test_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id='"+actor+"'::uuid AND NEW.outcome='"+outcome+"' THEN RAISE EXCEPTION '"+marker+"' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER "+name+" BEFORE INSERT ON administrative_audit FOR EACH ROW EXECUTE FUNCTION "+name+"()");
        return name;
    }
    void removeFailure(String name) {
        jdbc.execute("DROP TRIGGER "+name+" ON administrative_audit");
        jdbc.execute("DROP FUNCTION "+name+"()");
    }
}
