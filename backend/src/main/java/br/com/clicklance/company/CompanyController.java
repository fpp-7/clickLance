package br.com.clicklance.company;

import java.util.Map;
import br.com.clicklance.api.Input;
import br.com.clicklance.identity.AdminPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/empresas")
public class CompanyController {
    private final CompanyService companies;
    public CompanyController(CompanyService companies) { this.companies = companies; }

    @PostMapping
    ResponseEntity<Company> create(@AuthenticationPrincipal AdminPrincipal principal, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(companies.create(principal, body));
    }

    @GetMapping("/{empresa_id}")
    Company find(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable("empresa_id") String id) {
        return companies.find(principal, id);
    }

    @GetMapping
    Map<String, Object> list(@AuthenticationPrincipal AdminPrincipal principal,
                            @RequestParam(defaultValue = "0") String page,
                            @RequestParam(defaultValue = "20") String size) {
        return companies.list(principal, parameter(page, "page", 0, Integer.MAX_VALUE), parameter(size, "size", 1, 100));
    }

    private static int parameter(String text, String name, int min, int max) {
        try { return Input.integer(Long.parseLong(text), name, min, max); }
        catch (NumberFormatException ignored) { Input.invalid(name, "OUT_OF_RANGE"); return 0; }
    }
}
