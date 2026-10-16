package br.com.clicklance.controller;

import br.com.clicklance.model.ConfigPublication;
import br.com.clicklance.model.GatewayConfig;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.service.ConfigurationService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConfigurationController {
    private final ConfigurationService configurations;
    public ConfigurationController(ConfigurationService configurations) { this.configurations=configurations; }
    @PostMapping("/api/v1/admin/dispositivos/{id}/configuracoes")
    ResponseEntity<ConfigPublication> publish(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable String id,
                                            @RequestBody Map<String,Object> body) {
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(configurations.publish(principal, id, body));
    }
    @GetMapping("/api/v1/dispositivos/{id}/config")
    ResponseEntity<GatewayConfig> current(@PathVariable String id, @RequestHeader(value="X-Device-Token", required=false) String token) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(configurations.current(id, token));
    }
}
