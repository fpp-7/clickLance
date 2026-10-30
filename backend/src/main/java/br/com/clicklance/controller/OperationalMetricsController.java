package br.com.clicklance.controller;

import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.service.OperationalMetricsService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class OperationalMetricsController {
    private final OperationalMetricsService metrics;
    public OperationalMetricsController(OperationalMetricsService metrics) { this.metrics=metrics; }
    @GetMapping("/api/v1/admin/metricas")
    ResponseEntity<Map<String,Long>> find(@AuthenticationPrincipal AdminPrincipal principal) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(metrics.find(principal));
    }
}
