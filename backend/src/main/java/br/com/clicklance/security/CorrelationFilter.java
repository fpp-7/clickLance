package br.com.clicklance.security;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public final class CorrelationFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getServletPath().startsWith("/api/"); }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID id = UUID.randomUUID(); // Nunca confiar no header enviado pelo cliente.
        CorrelationContext.set(id);
        response.setHeader("X-Correlation-ID", id.toString());
        try (var ignored = MDC.putCloseable("correlation_id", id.toString())) {
            chain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
        }
    }
}
