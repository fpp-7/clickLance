package br.com.clicklance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminTestSupport.TimeConfiguration.class)
class CourtIT extends AdminTestSupport {
    final List<String> courts = new ArrayList<>();
    @AfterEach void removeCourts() {
        for (String id : courts) jdbc.update("DELETE FROM quadra WHERE id=?", id);
    }
    String company() {
        String id = UUID.randomUUID().toString();
        companies.add(id);
        jdbc.update("INSERT INTO empresa(id,nome,active) VALUES (?,?,true)", id, "Empresa teste");
        return id;
    }
    String path(String company) { return "/api/v1/admin/empresas/" + company + "/quadras"; }
    Map<String, Object> profile() {
        return new HashMap<>(Map.of("REPLAY_BEFORE_SECONDS", 120, "REPLAY_AFTER_SECONDS", 0,
                "SEGMENT_DURATION_SECONDS", 60, "KEYFRAME_INTERVAL", 30));
    }
    Map<String, Object> input(Map<String, Object> profile) {
        return new HashMap<>(Map.of("nome", "Quadra teste", "active", true, "timezone", "America/Sao_Paulo", "perfil_captura", profile));
    }
    Map<String, Object> create(String company, String token, Map<String, Object> input) throws Exception {
        var response = request("POST", path(company), token, input);
        assertThat(response.statusCode()).isEqualTo(201);
        var result = body(response);
        courts.add((String) result.get("quadra_id"));
        return result;
    }

    @Test void tenantCreatesAndReadsOwnCourtWithExplicitTimezoneAndProfile() throws Exception {
        String company = company();
        UUID tenant = user(false);
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)", tenant, company);
        String token = token(tenant);
        var result = create(company, token, input(profile()));
        assertThat(result).containsOnlyKeys("quadra_id", "empresa_id", "nome", "active", "timezone", "perfil_captura");
        assertThat(result).containsEntry("empresa_id", company).containsEntry("timezone", "America/Sao_Paulo");
        String id = (String) result.get("quadra_id");
        assertThat(UUID.fromString(id)).isNotNull();
        var response = request("GET", path(company) + "/" + id, token, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body(response)).isEqualTo(result);
        assertThat(response.body()).doesNotContain("CAMERA_RTSP_URL", "password", "device_token", "token_hash");
    }

    @Test void crossTenantAndContradictoryParentAreRejectedWithoutOrphans() throws Exception {
        String own = company(), other = company();
        UUID tenant = user(false), platform = user(true);
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)", tenant, own);
        String token = token(tenant), global = token(platform);
        var court = create(other, global, input(profile()));
        Long before = jdbc.queryForObject("SELECT count(*) FROM quadra", Long.class);
        assertThat(request("POST", path(own), null, input(profile())).statusCode()).isEqualTo(401);
        assertThat(request("POST", path(other), token, input(profile())).statusCode()).isEqualTo(403);
        assertThat(request("GET", path(other), token, null).statusCode()).isEqualTo(403);
        assertThat(request("GET", path(other) + "/" + court.get("quadra_id"), token, null).statusCode()).isEqualTo(403);
        assertThat(request("GET", path(own) + "/" + court.get("quadra_id"), token, null).statusCode()).isEqualTo(404);
        assertThat(request("GET", path(own) + "/" + court.get("quadra_id"), global, null).statusCode()).isEqualTo(404);
        String missing = UUID.randomUUID().toString();
        assertThat(request("POST", path(missing), global, input(profile())).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quadra", Long.class)).isEqualTo(before);
    }

    @Test void invalidTimezoneShapeAndSensitiveExtraFieldsLeaveNoRows() throws Exception {
        String company = company(), token = token(user(true));
        for (String timezone : List.of("America/Invalid", "+03:00", "local", "America/Sao_Paulo ")) {
            var payload = input(profile());
            payload.put("timezone", timezone);
            assertThat(request("POST", path(company), token, payload).statusCode()).isEqualTo(422);
        }
        for (String missing : List.of("timezone", "active", "perfil_captura", "nome")) {
            var payload = input(profile()); payload.remove(missing);
            assertThat(request("POST", path(company), token, payload).statusCode()).isEqualTo(422);
        }
        var extra = input(profile());
        String syntheticSecret = "rtsp://example-user:example-only-password@192.0.2.10/stream";
        extra.put("CAMERA_RTSP_URL", syntheticSecret);
        var rejected = request("POST", path(company), token, extra);
        assertThat(rejected.statusCode()).isEqualTo(422);
        assertThat(rejected.body()).doesNotContain(syntheticSecret, "example-only-password");
        var nested = profile(); nested.put("CAMERA_RTSP_URL", syntheticSecret);
        assertThat(request("POST", path(company), token, input(nested)).statusCode()).isEqualTo(422);
        var incomplete = profile(); incomplete.remove("KEYFRAME_INTERVAL");
        assertThat(request("POST", path(company), token, input(incomplete)).statusCode()).isEqualTo(422);
        var scalar = input(profile()); scalar.put("perfil_captura", "not-an-object");
        assertThat(request("POST", path(company), token, scalar).statusCode()).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quadra WHERE empresa_id=?", Integer.class, company)).isZero();
    }

    @Test void profileBoundariesAndTypesProduceFixturesForRealGatewayValidation() throws Exception {
        String company = company(), token = token(user(true));
        var fixtures = new ArrayList<Map<String, Object>>();
        var bounds = Map.of("REPLAY_BEFORE_SECONDS", new int[]{1,600}, "REPLAY_AFTER_SECONDS", new int[]{0,60},
                "SEGMENT_DURATION_SECONDS", new int[]{1,120}, "KEYFRAME_INTERVAL", new int[]{1,600});
        for (var entry : bounds.entrySet()) {
            for (int boundary : entry.getValue()) {
                var values = profile(); values.put(entry.getKey(), boundary);
                var result = create(company, token, input(values));
                fixtures.add(Map.of("settings", result.get("perfil_captura"), "accepted", true));
            }
            for (Object invalid : Arrays.asList(entry.getValue()[0]-1, entry.getValue()[1]+1, true, "2", null)) {
                var values = profile(); values.put(entry.getKey(), invalid);
                assertThat(request("POST", path(company), token, input(values)).statusCode()).isEqualTo(422);
                fixtures.add(Map.of("settings", values, "accepted", false));
            }
        }
        for (String field : List.of("SEGMENT_DURATION_SECONDS", "KEYFRAME_INTERVAL")) {
            var values = profile(); values.put(field, 2.0);
            assertThat(request("POST", path(company), token, input(values)).statusCode()).isEqualTo(422);
            fixtures.add(Map.of("settings", values, "accepted", false));
        }
        var fractional = profile(); fractional.put("REPLAY_BEFORE_SECONDS", 2.75); fractional.put("REPLAY_AFTER_SECONDS", 0.25);
        var result = create(company, token, input(fractional));
        fixtures.add(Map.of("settings", result.get("perfil_captura"), "accepted", true));
        Files.writeString(Path.of("target/be005-capture-profiles.json"), mapper.writeValueAsString(fixtures));
    }

    @Test void paginationIsStableAndCompanyScopedIncludingInactiveCourts() throws Exception {
        String company = company(), other = company(), token = token(user(true));
        var a = create(company, token, input(profile()));
        var inactive = input(profile()); inactive.put("active", false);
        var b = create(company, token, inactive);
        create(other, token, input(profile()));
        var ids = new ArrayList<>(List.of((String)a.get("quadra_id"), (String)b.get("quadra_id")));
        Collections.sort(ids);
        var page = body(request("GET", path(company) + "?size=1", token, null));
        assertThat(page).containsEntry("total", 2).containsEntry("page", 0).containsEntry("size", 1);
        assertThat(page.get("items").toString()).contains(ids.getFirst()).doesNotContain(ids.getLast());
        assertThat(body(request("GET", path(company) + "?size=1&page=1", token, null)).get("items").toString()).contains(ids.getLast());
        assertThat((List<?>)body(request("GET", path(company) + "?page=2147483647", token, null)).get("items")).isEmpty();
        for (String query : List.of("size=0", "size=101", "page=-1", "page=1.0")) {
            assertThat(request("GET", path(company) + "?" + query, token, null).statusCode()).isEqualTo(422);
        }
    }

    @Test void postgresRejectsOrphansOutOfRangeValuesAndCompanyTransfer() throws Exception {
        String company = company(), other = company(), token = token(user(true));
        String id = (String) create(company, token, input(profile())).get("quadra_id");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO quadra(id,empresa_id,nome,active,timezone,replay_before_seconds,replay_after_seconds,segment_duration_seconds,keyframe_interval) VALUES (?,?,?,true,'UTC',120,0,60,30)",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "Orphan"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE quadra SET empresa_id=? WHERE id=?", other, id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE quadra SET keyframe_interval=0 WHERE id=?", id)).isInstanceOf(DataIntegrityViolationException.class);
        for (double invalid : new double[]{-1, 601, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE quadra SET replay_before_seconds=? WHERE id=?", invalid, id)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(jdbc.queryForObject("SELECT empresa_id FROM quadra WHERE id=?", String.class, id)).isEqualTo(company);
    }
}
