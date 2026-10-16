package br.com.clicklance;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.DeviceCredentials;
import br.com.clicklance.model.DeviceIssued;
import br.com.clicklance.security.DevicePrincipal;
import br.com.clicklance.service.DeviceAuthenticationService;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
class DeviceIT extends AdminTestSupport {
    private static final String BUCKET = "be006-private-test";
    final Set<String> deviceIds = new LinkedHashSet<>();
    final List<String> courtIds = new ArrayList<>();
    @Autowired DeviceAuthenticationService deviceAuthentication;

    @AfterEach void removeDevicesAndCourts() {
        for (String id : deviceIds) jdbc.update("DELETE FROM dispositivo WHERE id=?", id);
        for (String id : courtIds) jdbc.update("DELETE FROM quadra WHERE id=?", id);
    }

    String company() {
        String id = UUID.randomUUID().toString();
        companies.add(id);
        jdbc.update("INSERT INTO empresa(id,nome,active) VALUES (?,?,true)", id, "Empresa dispositivo teste");
        return id;
    }

    String court(String company) {
        String id = UUID.randomUUID().toString();
        courtIds.add(id);
        jdbc.update("""
                INSERT INTO quadra(id,empresa_id,nome,active,timezone,replay_before_seconds,
                    replay_after_seconds,segment_duration_seconds,keyframe_interval)
                VALUES (?,?,'Quadra dispositivo teste',true,'America/Sao_Paulo',120,0,60,30)
                """, id, company);
        return id;
    }

    String identifier() { return UUID.randomUUID().toString().replace("-", ""); }
    String path(String company, String court) { return "/api/v1/admin/empresas/" + company + "/quadras/" + court + "/dispositivos"; }
    String detail(String id) { return "/api/v1/admin/dispositivos/" + id; }
    Map<String, Object> input(String id) { return new HashMap<>(Map.of("dispositivo_id", id, "active", true, "bucket", BUCKET)); }

    Map<String, Object> issue(String company, String court, String id, String admin) throws Exception {
        deviceIds.add(id);
        var response = request("POST", path(company, court), admin, input(id));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        return body(response);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> metadata(Map<String, Object> issued) { return (Map<String, Object>) issued.get("dispositivo"); }

    @Test void issuanceCommitsOnlyHashAndQueriesNeverExposeCredentials(CapturedOutput output) throws Exception {
        String company = company(), court = court(company), admin = token(user(true)), id = identifier();
        var issued = issue(company, court, id, admin);
        assertThat(issued).containsOnlyKeys("dispositivo", "device_token");
        var device = metadata(issued);
        assertThat(device).containsOnlyKeys("dispositivo_id", "quadra_id", "empresa_id", "active", "bucket");
        assertThat(device).containsEntry("dispositivo_id", id).containsEntry("quadra_id", court)
                .containsEntry("empresa_id", company).containsEntry("bucket", BUCKET).containsEntry("active", true);
        String secret = (String) issued.get("device_token");
        assertThat(secret).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(secret)).hasSize(32);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject("SELECT token_hash FROM dispositivo WHERE id=?", String.class, id)).isEqualTo(hash).isNotEqualTo(secret);
        var found = request("GET", detail(id), admin, null);
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(body(found)).isEqualTo(device);
        var listed = request("GET", path(company, court), admin, null);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).doesNotContain(secret, hash, "token_hash", "device_token");
        assertThat(found.body()).doesNotContain(secret, hash, "token_hash", "device_token");
        var duplicate = request("POST", path(company, court), admin, input(id));
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(body(duplicate)).containsEntry("code", "IDENTITY_CONFLICT");
        assertThat(duplicate.body()).doesNotContain(secret, hash, "device_token");
        assertThat(deviceAuthentication.authenticate(id, secret)).isEqualTo(new DevicePrincipal(id, court, company, BUCKET));
        var credentials = context.getBean(br.com.clicklance.repository.DeviceRepository.class).findCredentials(id).orElseThrow();
        assertThat(new DeviceIssued(credentials.device(), secret).toString()).doesNotContain(secret);
        assertThat(credentials.toString()).doesNotContain(hash);
        assertThat(output.getAll()).doesNotContain(secret, hash);
        // Apenas respostas sem segredo sao exportadas para validar o contrato real.
        Files.writeString(Path.of("target/be006-device-responses.json"), mapper.writeValueAsString(List.of(
                Map.of("schema", "Dispositivo", "body", body(found)),
                Map.of("schema", "DispositivoPage", "body", body(listed)),
                Map.of("schema", "ApiError", "body", body(duplicate)))));
    }

    @Test void tenantAuthorizationAndParentValidationPrecedeDeviceAccess() throws Exception {
        String own = company(), other = company(), ownCourt = court(own), otherCourt = court(other);
        UUID tenant = user(false);
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)", tenant, own);
        String admin = token(tenant), platform = token(user(true));
        String ownId = identifier(), otherId = identifier();
        issue(own, ownCourt, ownId, admin);
        issue(other, otherCourt, otherId, platform);
        assertThat(request("GET", detail(ownId), admin, null).statusCode()).isEqualTo(200);
        assertThat(request("GET", detail(otherId), admin, null).statusCode()).isEqualTo(403);
        assertThat(request("GET", detail(identifier()), admin, null).statusCode()).isEqualTo(403);
        assertThat(request("GET", detail(identifier()), platform, null).statusCode()).isEqualTo(404);
        assertThat(request("GET", detail("x".repeat(33)), platform, null).statusCode()).isEqualTo(400);
        Long before = jdbc.queryForObject("SELECT count(*) FROM dispositivo", Long.class);
        assertThat(request("POST", path(other, otherCourt), admin, input(identifier())).statusCode()).isEqualTo(403);
        assertThat(request("GET", path(other, otherCourt), admin, null).statusCode()).isEqualTo(403);
        assertThat(request("POST", path(own, otherCourt), admin, input(identifier())).statusCode()).isEqualTo(404);
        assertThat(request("POST", path(own, UUID.randomUUID().toString()), platform, input(identifier())).statusCode()).isEqualTo(404);
        assertThat(request("POST", path(UUID.randomUUID().toString(), ownCourt), platform, input(identifier())).statusCode()).isEqualTo(404);
        assertThat(request("POST", path(own, ownCourt), null, input(identifier())).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositivo", Long.class)).isEqualTo(before);
        jdbc.update("UPDATE admin_company_membership SET active=false WHERE user_id=?", tenant);
        assertThat(request("GET", detail(ownId), admin, null).statusCode()).isEqualTo(403);
    }

    @Test void invalidBodiesCannotChooseOriginCredentialsOrUnapprovedBuckets(CapturedOutput output) throws Exception {
        String company = company(), court = court(company), admin = token(user(true));
        for (String id : List.of("", "x".repeat(33), "mega 01", "../mega", "megá", " mega01", "mega|01")) {
            assertThat(request("POST", path(company, court), admin, input(id)).statusCode()).isEqualTo(422);
        }
        for (Object bucket : Arrays.asList(null, true, "xy", "x".repeat(64), "UPPER-bucket", "s3://bucket", "not-approved-bucket")) {
            var payload = input(identifier()); payload.put("bucket", bucket);
            assertThat(request("POST", path(company, court), admin, payload).statusCode()).isEqualTo(422);
        }
        for (Object active : Arrays.asList(null, "true", 1)) {
            var payload = input(identifier()); payload.put("active", active);
            assertThat(request("POST", path(company, court), admin, payload).statusCode()).isEqualTo(422);
        }
        for (String missing : List.of("dispositivo_id", "active", "bucket")) {
            var payload = input(identifier()); payload.remove(missing);
            assertThat(request("POST", path(company, court), admin, payload).statusCode()).isEqualTo(422);
        }
        String syntheticSecret = "test-extra-secret-" + UUID.randomUUID();
        for (String field : List.of("empresa_id", "quadra_id", "device_token", "token_hash", "role", "CAMERA_RTSP_URL")) {
            var payload = input(identifier()); payload.put(field, syntheticSecret);
            var response = request("POST", path(company, court), admin, payload);
            assertThat(response.statusCode()).isEqualTo(422);
            assertThat(response.body()).doesNotContain(syntheticSecret);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositivo WHERE quadra_id=?", Integer.class, court)).isZero();
        assertThat(output.getAll()).doesNotContain(syntheticSecret);
    }

    @Test void concurrentAndCrossCompanyDuplicatesNeverReissueOrReplaceCredential() throws Exception {
        String company = company(), other = company(), court = court(company), otherCourt = court(other);
        String admin = token(user(true)), id = identifier();
        deviceIds.add(id);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<HttpResponse<String>> first = () -> { start.await(); return request("POST", path(company, court), admin, input(id)); };
            Callable<HttpResponse<String>> second = () -> { start.await(); return request("POST", path(other, otherCourt), admin, input(id)); };
            var a = pool.submit(first); var b = pool.submit(second); start.countDown();
            var responses = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(201, 409);
            var winner = responses.stream().filter(response -> response.statusCode() == 201).findFirst().orElseThrow();
            var loser = responses.stream().filter(response -> response.statusCode() == 409).findFirst().orElseThrow();
            String secret = (String) body(winner).get("device_token");
            assertThat(loser.body()).doesNotContain(secret, "device_token", "token_hash");
            assertThat(deviceAuthentication.authenticate(id, secret).courtId()).isEqualTo(metadata(body(winner)).get("quadra_id"));
            String originalHash = jdbc.queryForObject("SELECT token_hash FROM dispositivo WHERE id=?", String.class, id);
            var changed = input(id); changed.put("active", false); changed.put("bucket", "be006-second-test");
            assertThat(request("POST", path(company, court), admin, changed).statusCode()).isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT token_hash FROM dispositivo WHERE id=?", String.class, id)).isEqualTo(originalHash);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dispositivo WHERE id=?", Integer.class, id)).isEqualTo(1);
        }
    }

    @Test void paginationIsStableScopedAndIncludesInactiveDevices() throws Exception {
        String company = company(), ownCourt = court(company), secondCourt = court(company), other = company(), otherCourt = court(other);
        String admin = token(user(true));
        String a = identifier(), b = identifier();
        issue(company, ownCourt, a, admin); issue(company, ownCourt, b, admin);
        jdbc.update("UPDATE dispositivo SET active=false WHERE id=?", b);
        issue(company, secondCourt, identifier(), admin); issue(other, otherCourt, identifier(), admin);
        var ids = new ArrayList<>(List.of(a,b)); Collections.sort(ids);
        var first = body(request("GET", path(company, ownCourt) + "?size=1", admin, null));
        assertThat(first).containsEntry("total", 2).containsEntry("page", 0).containsEntry("size", 1);
        assertThat(first.get("items").toString()).contains(ids.getFirst()).doesNotContain(ids.getLast(), "token_hash", "device_token");
        assertThat(body(request("GET", path(company, ownCourt) + "?size=1&page=1", admin, null)).get("items").toString()).contains(ids.getLast());
        assertThat((List<?>) body(request("GET", path(company, ownCourt) + "?page=2147483647", admin, null)).get("items")).isEmpty();
        for (String query : List.of("size=0", "size=101", "page=-1", "page=1.0")) {
            assertThat(request("GET", path(company, ownCourt) + "?" + query, admin, null).statusCode()).isEqualTo(422);
        }
    }

    @Test void credentialsAreDeviceSpecificAndCannotAuthenticateAdministration() throws Exception {
        String company = company(), court = court(company), admin = token(user(true)), a = identifier(), b = identifier();
        String secretA = (String) issue(company, court, a, admin).get("device_token");
        String secretB = (String) issue(company, court, b, admin).get("device_token");
        assertThat(secretA).isNotEqualTo(secretB);
        for (String token : Arrays.asList(null, "", "wrong", secretB, admin, secretA + "=", " " + secretA)) {
            assertFailure(a, token, 401, "UNAUTHENTICATED");
        }
        assertFailure(identifier(), secretA, 401, "UNAUTHENTICATED");
        assertFailure(null, secretA, 401, "UNAUTHENTICATED");
        assertFailure("../mega", secretA, 401, "UNAUTHENTICATED");
        assertThat(deviceAuthentication.authenticate(a, secretA)).isEqualTo(new DevicePrincipal(a, court, company, BUCKET));
        assertThat(deviceAuthentication.authenticate(b, secretB).deviceId()).isEqualTo(b);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getRequiredProperty("local.server.port") + detail(a)))
                .header("X-Device-Token", secretA).GET().build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(request("GET", detail(a), secretA, null).statusCode()).isEqualTo(401);
        // BE-008/011 nao sao antecipadas por uma credencial valida.
        assertThat(request("GET", "/api/v1/dispositivos/" + a + "/config", null, null).statusCode()).isEqualTo(401);
    }

    @Test void inactiveDeviceCourtOrCompanyIsRecheckedAfterValidatingSecret() throws Exception {
        String company = company(), court = court(company), admin = token(user(true)), id = identifier();
        String secret = (String) issue(company, court, id, admin).get("device_token");
        for (String table : List.of("dispositivo", "quadra", "empresa")) {
            String resourceId = table.equals("dispositivo") ? id : table.equals("quadra") ? court : company;
            jdbc.update("UPDATE " + table + " SET active=false WHERE id=?", resourceId);
            assertFailure(id, secret, 403, "ORIGIN_INACTIVE");
            assertFailure(id, "x".repeat(43), 401, "UNAUTHENTICATED");
            // Inativo continua visivel ao administrador autorizado, sem revelar o segredo.
            assertThat(request("GET", detail(id), admin, null).statusCode()).isEqualTo(200);
            jdbc.update("UPDATE " + table + " SET active=true WHERE id=?", resourceId);
            assertThat(deviceAuthentication.authenticate(id, secret).deviceId()).isEqualTo(id);
        }
        jdbc.update("UPDATE dispositivo SET token_hash=? WHERE id=?", "b".repeat(64), id);
        assertFailure(id, secret, 401, "UNAUTHENTICATED");
        jdbc.update("UPDATE empresa SET active=false WHERE id=?", company);
        String inactiveId = identifier();
        String inactiveSecret = (String) issue(company, court, inactiveId, admin).get("device_token");
        assertFailure(inactiveId, inactiveSecret, 403, "ORIGIN_INACTIVE");
    }

    @Test void postgresEnforcesGlobalIdentityOriginHashAndBucketConstraints() throws Exception {
        String company = company(), court = court(company), anotherCourt = court(company), admin = token(user(true)), id = identifier();
        issue(company, court, id, admin);
        String hash = jdbc.queryForObject("SELECT token_hash FROM dispositivo WHERE id=?", String.class, id);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dispositivo(id,quadra_id,active,bucket,token_hash) VALUES (?,?,true,?,?)",
                identifier(), UUID.randomUUID().toString(), BUCKET, "a".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO dispositivo(id,quadra_id,active,bucket,token_hash) VALUES (?,?,true,?,?)",
                identifier(), court, BUCKET, hash)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE dispositivo SET quadra_id=? WHERE id=?", anotherCourt, id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE dispositivo SET id=? WHERE id=?", identifier(), id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE dispositivo SET token_hash='plaintext-not-a-hash' WHERE id=?", id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE dispositivo SET bucket='s3://invalid' WHERE id=?", id)).isInstanceOf(DataIntegrityViolationException.class);
        for (String invalid : List.of("", "x".repeat(33), "bad|id")) {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO dispositivo(id,quadra_id,active,bucket,token_hash) VALUES (?,?,true,?,?)",
                    invalid, court, BUCKET, "a".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
        }
        String shortest = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-".chars().mapToObj(value -> Character.toString((char)value))
                .filter(value -> jdbc.queryForObject("SELECT count(*) FROM dispositivo WHERE id=?", Integer.class, value) == 0).findFirst().orElseThrow();
        issue(company, court, shortest, admin);
        assertThat(jdbc.queryForObject("SELECT quadra_id FROM dispositivo WHERE id=?", String.class, id)).isEqualTo(court);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM dispositivo WHERE id=?", String.class, id)).isEqualTo(hash);
    }

    void assertFailure(String id, String token, int status, String code) {
        assertThatThrownBy(() -> deviceAuthentication.authenticate(id, token)).isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    assertThat(((ApiException)error).status()).isEqualTo(status);
                    assertThat(((ApiException)error).code()).isEqualTo(code);
                });
    }
}
