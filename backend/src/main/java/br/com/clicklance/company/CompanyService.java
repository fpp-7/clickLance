package br.com.clicklance.company;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import br.com.clicklance.api.*;
import br.com.clicklance.identity.AdminPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {
    private final JdbcTemplate jdbc;
    public CompanyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public Company create(AdminPrincipal principal, Map<String, Object> body) {
        principal.requirePlatform();
        Input.fields(body, Set.of("nome", "active", "retencao_dias"));
        String nome = Input.string(body, "nome", 120).strip();
        boolean active = Input.bool(body, "active");
        int retention = Input.integer(body.containsKey("retencao_dias") ? body.get("retencao_dias") : 2,
                "retencao_dias", 1, 30);
        var company = new Company(UUID.randomUUID().toString(), nome, active, retention);
        jdbc.update("INSERT INTO empresa(id,nome,active,retencao_dias) VALUES (?,?,?,?)",
                company.empresa_id(), company.nome(), company.active(), company.retencao_dias());
        return company;
    }

    @Transactional(readOnly = true)
    public Company find(AdminPrincipal principal, String id) {
        // Autorizacao anterior a consulta evita confirmar a existencia em outro tenant.
        principal.requireCompany(id);
        if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new ApiException(400, "INVALID_REQUEST");
        var rows = jdbc.query("SELECT id,nome,active,retencao_dias FROM empresa WHERE id=?", CompanyService::map, id);
        if (rows.isEmpty()) throw new ApiException(404, "RESOURCE_NOT_FOUND");
        return rows.getFirst();
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> list(AdminPrincipal principal, int page, int size) {
        if (!principal.platformAdmin() && principal.empresaIds().isEmpty()) throw new ApiException(403, "FORBIDDEN");
        Input.integer(page, "page", 0, Integer.MAX_VALUE);
        Input.integer(size, "size", 1, 100);
        String scope = principal.platformAdmin() ? "" : " WHERE EXISTS (SELECT 1 FROM admin_company_membership m WHERE m.empresa_id=e.id AND m.user_id=? AND m.active AND m.role='COMPANY_ADMIN')";
        List<Company> items;
        Long total;
        long offset = (long) page * size;
        if (principal.platformAdmin()) {
            items = jdbc.query("SELECT id,nome,active,retencao_dias FROM empresa e ORDER BY id LIMIT ? OFFSET ?", CompanyService::map, size, offset);
            total = jdbc.queryForObject("SELECT count(*) FROM empresa", Long.class);
        } else {
            items = jdbc.query("SELECT id,nome,active,retencao_dias FROM empresa e" + scope + " ORDER BY id LIMIT ? OFFSET ?",
                    CompanyService::map, principal.userId(), size, offset);
            total = jdbc.queryForObject("SELECT count(*) FROM empresa e" + scope, Long.class, principal.userId());
        }
        return Map.of("items", items, "page", page, "size", size, "total", total);
    }

    private static Company map(ResultSet rs, int row) throws SQLException {
        return new Company(rs.getString("id"), rs.getString("nome"), rs.getBoolean("active"), rs.getInt("retencao_dias"));
    }
}
