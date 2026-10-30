package br.com.clicklance.controller;

import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.model.Court;
import br.com.clicklance.service.CourtService;
import br.com.clicklance.validation.Input;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/empresas/{empresa_id}/quadras")
public class CourtController {
    private final CourtService courts;
    public CourtController(CourtService courts) { this.courts = courts; }

    @PostMapping
    ResponseEntity<Court> create(@AuthenticationPrincipal AdminPrincipal principal,
                                @PathVariable("empresa_id") String company, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).body(courts.create(principal, company, body));
    }

    @GetMapping("/{quadra_id}")
    Court find(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable("empresa_id") String company,
               @PathVariable("quadra_id") String id) {
        return courts.find(principal, company, id);
    }

    @PatchMapping("/{quadra_id}/status")
    Court setActive(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable("empresa_id") String company,
                    @PathVariable("quadra_id") String id, @RequestBody Map<String, Object> body) {
        return courts.setActive(principal, company, id, body);
    }

    @GetMapping
    Map<String, Object> list(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable("empresa_id") String company,
                            @RequestParam(defaultValue = "0") String page, @RequestParam(defaultValue = "20") String size) {
        return courts.list(principal, company, parameter(page, "page", 0, Integer.MAX_VALUE), parameter(size, "size", 1, 100));
    }

    private static int parameter(String value, String name, int min, int max) {
        try { return Input.integer(Long.parseLong(value), name, min, max); }
        catch (NumberFormatException ignored) { Input.invalid(name, "OUT_OF_RANGE"); return 0; }
    }
}
