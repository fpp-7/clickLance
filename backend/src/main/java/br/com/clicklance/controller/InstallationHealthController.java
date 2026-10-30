package br.com.clicklance.controller;

import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.service.InstallationHealthService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class InstallationHealthController {
    private final InstallationHealthService health;
    public InstallationHealthController(InstallationHealthService health) { this.health=health; }
    @PostMapping("/api/v1/dispositivos/{id}/saude")
    ResponseEntity<Void> receive(@PathVariable String id, @RequestHeader(value="X-Device-Token",required=false) String token,
                                 @RequestBody Map<String,Object> body) {
        health.receive(id,token,body);
        return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
    @GetMapping("/api/v1/admin/dispositivos/{id}/saude")
    ResponseEntity<Map<String,Object>> find(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable String id) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(health.find(principal,id));
    }
}
