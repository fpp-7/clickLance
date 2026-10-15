package br.com.clicklance.security;

import br.com.clicklance.service.AdminSessionService;
import br.com.clicklance.exception.ApiErrors;
import br.com.clicklance.exception.ApiException;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.transaction.TransactionException;

public final class AdminBearerFilter extends OncePerRequestFilter {
    private final AdminSessionService sessions;
    public AdminBearerFilter(AdminSessionService sessions) { this.sessions = sessions; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/v1/admin/")
                || (request.getServletPath().equals("/api/v1/admin/sessoes") && request.getMethod().equals("POST"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.matches("(?i)Bearer [A-Za-z0-9_.-]+")) throw new ApiException(401, "UNAUTHENTICATED");
                var principal = sessions.authenticate(header.substring(7));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
                SecurityContextHolder.setContext(context);
            } catch (ApiException error) {
                ApiErrors.write(response, error.status(), error.code());
                return;
            } catch (DataAccessException | TransactionException ignored) {
                ApiErrors.write(response, 503, "DEPENDENCY_UNAVAILABLE");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
