package br.com.clicklance.security;

import br.com.clicklance.service.AdminSessionService;
import br.com.clicklance.exception.ApiException;
import br.com.clicklance.exception.ApiErrorResponder;

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
    private final ApiErrorResponder errors;
    public AdminBearerFilter(AdminSessionService sessions, ApiErrorResponder errors) { this.sessions = sessions; this.errors = errors; }

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
                errors.write(request, response, error.status(), error.code());
                return;
            } catch (DataAccessException | TransactionException ignored) {
                errors.write(request, response, 503, "DEPENDENCY_UNAVAILABLE");
                return;
            } catch (RuntimeException ignored) {
                errors.write(request, response, 500, "INTERNAL_ERROR");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
