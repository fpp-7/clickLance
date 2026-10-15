package br.com.clicklance.service;

import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.Company;
import br.com.clicklance.repository.CompanyRepository;
import br.com.clicklance.validation.Input;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {
    private final CompanyRepository companies;
    public CompanyService(CompanyRepository companies) { this.companies = companies; }

    @Transactional
    public Company create(AdminPrincipal principal, Map<String, Object> body) {
        principal.requirePlatform();
        Input.fields(body, Set.of("nome", "active", "retencao_dias"));
        String nome = Input.string(body, "nome", 120).strip();
        boolean active = Input.bool(body, "active");
        int retention = Input.integer(body.containsKey("retencao_dias") ? body.get("retencao_dias") : 2,
                "retencao_dias", 1, 30);
        var company = new Company(UUID.randomUUID().toString(), nome, active, retention);
        companies.insert(company);
        return company;
    }

    @Transactional(readOnly = true)
    public Company find(AdminPrincipal principal, String id) {
        // Autorizacao anterior a consulta evita confirmar a existencia em outro tenant.
        principal.requireCompany(id);
        if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw new ApiException(400, "INVALID_REQUEST");
        return companies.findById(id).orElseThrow(() -> new ApiException(404, "RESOURCE_NOT_FOUND"));
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> list(AdminPrincipal principal, int page, int size) {
        if (!principal.platformAdmin() && principal.empresaIds().isEmpty()) throw new ApiException(403, "FORBIDDEN");
        Input.integer(page, "page", 0, Integer.MAX_VALUE);
        Input.integer(size, "size", 1, 100);
        long offset = (long) page * size;
        var items = principal.platformAdmin() ? companies.findAll(size, offset)
                : companies.findByActiveMembership(principal.userId(), size, offset);
        long total = principal.platformAdmin() ? companies.countAll() : companies.countByActiveMembership(principal.userId());
        return Map.of("items", items, "page", page, "size", size, "total", total);
    }
}
