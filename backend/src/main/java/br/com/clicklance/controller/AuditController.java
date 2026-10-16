package br.com.clicklance.controller;

import br.com.clicklance.model.AuditEntry;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.service.AuditQueryService;
import br.com.clicklance.validation.Input;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AuditController {
    private final AuditQueryService audit;
    public AuditController(AuditQueryService audit) { this.audit = audit; }

    @GetMapping("/empresas/{empresa_id}/auditoria")
    Map<String, Object> list(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable("empresa_id") String companyId,
                            @RequestParam(defaultValue = "0") String page, @RequestParam(defaultValue = "20") String size) {
        return audit.list(principal, companyId, parameter(page, "page", 0, Integer.MAX_VALUE), parameter(size, "size", 1, 100));
    }

    @GetMapping("/auditoria/{correlation_id}")
    List<AuditEntry> findCorrelation(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable("correlation_id") UUID id) {
        return audit.findCorrelation(principal, id);
    }

    private static int parameter(String value, String name, int min, int max) {
        try { return Input.integer(Long.parseLong(value), name, min, max); }
        catch (NumberFormatException ignored) { Input.invalid(name, "OUT_OF_RANGE"); return 0; }
    }
}
