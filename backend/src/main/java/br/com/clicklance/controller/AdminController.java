package br.com.clicklance.controller;

import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.service.AdminSessionService;
import br.com.clicklance.validation.Input;

import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/sessoes")
public class AdminController {
    private final AdminSessionService sessions;
    public AdminController(AdminSessionService sessions) { this.sessions = sessions; }

    @PostMapping
    ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        Input.fields(body, Set.of("email", "password"));
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(sessions.login(
                Input.string(body, "email", 254), Input.string(body, "password", 72)));
    }

    @GetMapping("/atual")
    Map<String, Object> current(@AuthenticationPrincipal AdminPrincipal principal) {
        return Map.of("usuario_id", principal.userId().toString(), "email", principal.email(),
                "platform_admin", principal.platformAdmin(), "empresa_ids", principal.empresaIds().stream().sorted().toList());
    }

    @DeleteMapping("/atual")
    ResponseEntity<Void> logout(@AuthenticationPrincipal AdminPrincipal principal) {
        sessions.logout(principal);
        return ResponseEntity.noContent().build();
    }
}
