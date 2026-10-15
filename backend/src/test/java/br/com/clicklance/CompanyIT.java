package br.com.clicklance;

import br.com.clicklance.exception.ApiException;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminTestSupport.TimeConfiguration.class)
class CompanyIT extends AdminTestSupport {
    static final String PATH = "/api/v1/admin/empresas";

    String company(boolean active) {
        String id = UUID.randomUUID().toString();
        companies.add(id);
        jdbc.update("INSERT INTO empresa(id,nome,active) VALUES (?,?,?)", id, "Arena de teste", active);
        return id;
    }
    void member(UUID user, String company) {
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?)", user, company);
    }

    @Test void platformCreatesDefaultAndBoundaryRetentionsWithStableIdentity() throws Exception {
        String token = token(user(true));
        var response = request("POST", PATH, token, Map.of("nome", "Arena Exemplo", "active", true));
        assertThat(response.statusCode()).isEqualTo(201);
        String id = (String) body(response).get("empresa_id");
        companies.add(id);
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(body(response)).containsEntry("retencao_dias", 2).containsEntry("active", true);
        assertThat(body(request("GET", PATH + "/" + id, token, null))).isEqualTo(body(response));
        for (int retention : new int[]{1, 30}) {
            var boundary = request("POST", PATH, token, Map.of("nome", "Arena", "active", false, "retencao_dias", retention));
            assertThat(boundary.statusCode()).isEqualTo(201);
            companies.add((String) body(boundary).get("empresa_id"));
            assertThat(body(boundary)).containsEntry("active", false).containsEntry("retencao_dias", retention);
        }
    }

    @Test void invalidInputNeverLeavesPartialRowsOrLeakedValues() throws Exception {
        String token = token(user(true));
        Long before = jdbc.queryForObject("SELECT count(*) FROM empresa", Long.class);
        var template = Map.<String, Object>of("nome", "Arena", "active", true);
        for (Object invalid : new Object[]{0, 31, 2.0, "2", true}) {
            var input = new HashMap<>(template);
            input.put("retencao_dias", invalid);
            var response = request("POST", PATH, token, input);
            assertThat(response.statusCode()).as("retention %s", invalid).isEqualTo(422);
            assertThat(body(response)).containsEntry("code", "VALIDATION_FAILED");
            assertThat(UUID.fromString((String) body(response).get("correlation_id"))).isNotNull();
            assertThat(response.body()).doesNotContain("rejected_value", "jdbc:", "Exception");
        }
        for (Map<String, Object> invalid : List.of(
                Map.<String, Object>of("nome", "   ", "active", true),
                Map.<String, Object>of("nome", "Arena"),
                Map.<String, Object>of("nome", "Arena", "active", "true"),
                Map.<String, Object>of("nome", "Arena", "active", true, "platform_admin", true),
                Map.<String, Object>of("nome", "x".repeat(121), "active", true))) {
            assertThat(request("POST", PATH, token, invalid).statusCode()).isEqualTo(422);
        }
        var withNull = new HashMap<>(template);
        withNull.put("retencao_dias", null);
        assertThat(request("POST", PATH, token, withNull).statusCode()).isEqualTo(422);
        assertThat(request("POST", PATH, token, "unexpected scalar JSON").statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM empresa", Long.class)).isEqualTo(before);
    }

    @Test void tenantCannotCreateOrReadOtherCompanyAndRevocationTakesEffect() throws Exception {
        UUID platform = user(true), tenant = user(false);
        String own = company(true), other = company(false);
        member(tenant, own);
        String platformToken = token(platform), tenantToken = token(tenant);
        assertThat(request("POST", PATH, null, Map.of("nome", "Arena", "active", true)).statusCode()).isEqualTo(401);
        assertThat(request("POST", PATH, tenantToken, Map.of("nome", "Arena", "active", true)).statusCode()).isEqualTo(403);
        assertThat(request("GET", PATH + "/" + own, tenantToken, null).statusCode()).isEqualTo(200);
        var denied = request("GET", PATH + "/" + other, tenantToken, null);
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.body()).doesNotContain(other, "Arena de teste");
        assertThat(request("GET", PATH + "/" + other, platformToken, null).statusCode()).isEqualTo(200);
        String missing = UUID.randomUUID().toString();
        assertThat(request("GET", PATH + "/" + missing, platformToken, null).statusCode()).isEqualTo(404);
        assertThat(request("GET", PATH + "/" + missing, tenantToken, null).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE admin_company_membership SET active=false WHERE user_id=? AND empresa_id=?", tenant, own);
        assertThat(request("GET", PATH + "/" + own, tenantToken, null).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE app_user SET platform_admin=false WHERE id=?", platform);
        assertThat(request("GET", PATH, platformToken, null).statusCode()).isEqualTo(403);
    }

    @Test void paginationAndCountsOnlyIncludeCurrentAuthorizedCompanies() throws Exception {
        UUID tenant = user(false);
        String first = company(true), second = company(false);
        company(true); // Sem vinculo: nem contagem nem listagem.
        member(tenant, first);
        member(tenant, second);
        String token = token(tenant);
        var page0 = body(request("GET", PATH + "?size=1&page=0", token, null));
        var page1 = body(request("GET", PATH + "?size=1&page=1", token, null));
        var ordered = new ArrayList<>(List.of(first, second));
        Collections.sort(ordered);
        assertThat(page0).containsEntry("total", 2).containsEntry("size", 1).containsEntry("page", 0);
        assertThat(page1).containsEntry("total", 2);
        assertThat(page0.get("items").toString()).contains(ordered.getFirst()).doesNotContain(ordered.getLast());
        assertThat(page1.get("items").toString()).contains(ordered.getLast());
        assertThat((List<?>) body(request("GET", PATH + "?page=2147483647", token, null)).get("items")).isEmpty();
        for (String query : List.of("page=-1", "size=0", "size=101", "page=abc", "page=1.0")) {
            assertThat(request("GET", PATH + "?" + query, token, null).statusCode()).isEqualTo(422);
        }
        var identity = body(request("GET", "/api/v1/admin/sessoes/atual", token, null));
        assertThat((List<?>) identity.get("empresa_ids")).hasSize(2);
    }

    @Test void postgresEnforcesRetentionAndMembershipIntegrity() {
        String id = company(true);
        UUID account = user(false);
        for (int value : new int[]{0, 31}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE empresa SET retencao_dias=? WHERE id=?", value, id))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(jdbc.queryForObject("SELECT retencao_dias FROM empresa WHERE id=?", Integer.class, id)).isEqualTo(2);
        assertThatThrownBy(() -> member(account, UUID.randomUUID().toString())).isInstanceOf(DataIntegrityViolationException.class);
        member(account, id);
        assertThatThrownBy(() -> member(account, id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE admin_company_membership SET role='PLATFORM_ADMIN' WHERE user_id=?", account))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void operationalProvisioningRequiresPlatformAndKeepsPasswordAndMembershipStable() {
        UUID platform = user(true), tenant = user(false);
        String id = company(true);
        member(tenant, id);
        var operator = sessions.authenticate(token(platform));
        UUID newAccount = UUID.randomUUID();
        String email = email(newAccount);
        UUID created = accounts.provisionCompanyAdmin(operator, email, PASSWORD, id);
        users.add(created);
        assertThat(sessions.authenticate((String) sessions.login(email, PASSWORD).get("access_token")).empresaIds()).containsExactly(id);
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, created);
        assertThat(accounts.provisionCompanyAdmin(operator, email, "a-different-password", id)).isEqualTo(created);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, created)).isEqualTo(hash);
        var otherOperator = sessions.authenticate(token(tenant));
        assertThatThrownBy(() -> accounts.provisionCompanyAdmin(otherOperator, email, PASSWORD, id))
                .isInstanceOf(br.com.clicklance.exception.ApiException.class).extracting("status").isEqualTo(403);
    }
}
