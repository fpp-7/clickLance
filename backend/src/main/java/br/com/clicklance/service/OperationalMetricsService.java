package br.com.clicklance.service;

import br.com.clicklance.security.AdminPrincipal;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OperationalMetricsService {
    private final RequestLimitService limits;
    public OperationalMetricsService(RequestLimitService limits) { this.limits=limits; }
    public Map<String,Long> find(AdminPrincipal principal) { principal.requirePlatform(); return limits.metrics(); }
}
