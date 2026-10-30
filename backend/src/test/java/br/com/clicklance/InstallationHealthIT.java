package br.com.clicklance;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import br.com.clicklance.security.DeviceTokens;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminTestSupport.TimeConfiguration.class)
class InstallationHealthIT extends AdminTestSupport {
    String company,court,device,secret,admin;
    @BeforeEach void provision() {
        company=UUID.randomUUID().toString(); court=UUID.randomUUID().toString();
        device=UUID.randomUUID().toString().replace("-",""); companies.add(company);
        jdbc.update("INSERT INTO empresa(id,nome,active) VALUES (?,'Health test',true)",company);
        jdbc.update("""
            INSERT INTO quadra(id,empresa_id,nome,active,timezone,replay_before_seconds,replay_after_seconds,segment_duration_seconds,keyframe_interval)
            VALUES (?,?,'Health test',true,'America/Sao_Paulo',120,0,60,30)
            """,court,company);
        var tokens=context.getBean(DeviceTokens.class); secret=tokens.issue();
        jdbc.update("INSERT INTO dispositivo(id,quadra_id,active,bucket,token_hash) VALUES (?,?,true,'health-fixture',?)",device,court,tokens.hash(secret));
        admin=token(user(true));
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM installation_health WHERE dispositivo_id=?",device);
        jdbc.update("DELETE FROM dispositivo WHERE id=?",device);
        jdbc.update("DELETE FROM quadra WHERE id=?",court);
    }
    Map<String,Object> observation() {
        Map<String,Object> b=new HashMap<>(Map.of("schema_version",1,"observed_at",CLOCK.instant().toString(),
                "gateway_version","0.3.0","capture_state","READY","pending_events",2,"oldest_pending_age_seconds",60));
        b.put("capture_progress_age_seconds",null); return b;
    }
    HttpResponse<String> send(String token,Map<String,Object> data) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+environment.getRequiredProperty("local.server.port")
                +"/api/v1/dispositivos/"+device+"/saude")).header("Content-Type","application/json")
                .header("X-Device-Token",token).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(data))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    String path() { return "/api/v1/admin/dispositivos/"+device+"/saude"; }
    @Test void scopedStateFreshnessDuplicatesAndOutOfOrderObservations() throws Exception {
        assertThat(body(request("GET",path(),admin,null))).containsEntry("contact_state","UNKNOWN");
        var first=observation(); assertThat(send(secret,first).statusCode()).isEqualTo(204);
        var before=jdbc.queryForMap("SELECT * FROM installation_health WHERE dispositivo_id=?",device);
        CLOCK.advance(60);
        assertThat(send(secret,first).statusCode()).isEqualTo(204);
        var older=observation(); older.put("observed_at",CLOCK.instant().minusSeconds(61).toString()); older.put("capture_state","UNAVAILABLE");
        assertThat(send(secret,older).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForMap("SELECT * FROM installation_health WHERE dispositivo_id=?",device)).isEqualTo(before);
        assertThat(body(request("GET",path(),admin,null))).containsEntry("contact_state","CURRENT");
        CLOCK.advance(120);
        assertThat(body(request("GET",path(),admin,null))).containsEntry("contact_state","STALE");
        var latest=observation(); latest.put("capture_state","UNAVAILABLE");
        assertThat(send(secret,latest).statusCode()).isEqualTo(204);
        assertThat(request("GET",path(),admin,null).body()).contains("UNAVAILABLE","CURRENT");
        String other=UUID.randomUUID().toString(); companies.add(other);
        jdbc.update("INSERT INTO empresa(id,nome,active) VALUES (?,'Other tenant',true)",other);
        var tenant=user(false);
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)",tenant,other);
        assertThat(request("GET",path(),token(tenant),null).statusCode()).isEqualTo(403);
        assertThat(request("GET","/api/v1/admin/metricas",token(tenant),null).statusCode()).isEqualTo(403);
        assertThat(request("GET","/api/v1/admin/metricas",admin,null).statusCode()).isEqualTo(200);
    }
    @Test void authenticationClockAndSensitiveFieldsFailWithoutWriting() throws Exception {
        assertThat(send("invalid",observation()).statusCode()).isEqualTo(401);
        for (String extra:List.of("device_token","CAMERA_RTSP_URL","stderr","dispositivo_id")) {
            var invalid=observation(); invalid.put(extra,"sensitive-fixture");
            var result=send(secret,invalid); assertThat(result.statusCode()).isEqualTo(422);
            assertThat(result.body()).doesNotContain("sensitive-fixture");
        }
        for (long offset:List.of(-181L,31L)) {
            var invalid=observation(); invalid.put("observed_at",CLOCK.instant().plusSeconds(offset).toString());
            assertThat(send(secret,invalid).statusCode()).isEqualTo(422);
        }
        jdbc.update("UPDATE empresa SET active=false WHERE id=?",company);
        assertThat(send(secret,observation()).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM installation_health WHERE dispositivo_id=?",Integer.class,device)).isZero();
    }
    @Test void originalGatewayPublishesLocalQueueAndUnavailableCaptureAgainstRealApi() throws Exception {
        var process=new ProcessBuilder(Path.of(".venv/Scripts/python.exe").toAbsolutePath().toString(),
                "-X","utf8","tools/gateway_telemetry.py").redirectErrorStream(true).start();
        try(var input=process.getOutputStream()) {
            input.write(mapper.writeValueAsString(Map.of("base_url","http://127.0.0.1:"+environment.getRequiredProperty("local.server.port"),
                    "device_id",device,"device_token",secret)).getBytes(StandardCharsets.UTF_8));
        }
        boolean ended=process.waitFor(20,TimeUnit.SECONDS);
        if(!ended) process.destroyForcibly();
        assertThat(ended).isTrue();
        String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("GATEWAY_TELEMETRY_OK").doesNotContain(secret);
        var stored=jdbc.queryForMap("SELECT capture_state,pending_events FROM installation_health WHERE dispositivo_id=?",device);
        assertThat(stored.get("capture_state")).isIn("WARMING_UP","UNAVAILABLE");
        assertThat(stored.get("pending_events")).isEqualTo(0);
    }
}
