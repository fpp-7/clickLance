package br.com.clicklance.exception;

import br.com.clicklance.model.AuditAction;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.security.CorrelationContext;
import br.com.clicklance.service.AuditService;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

@Component
public class ApiErrorResponder {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrorResponder.class);
    private static final String RECORDED = ApiErrorResponder.class.getName() + ".recorded";
    private final AuditService audit;
    public ApiErrorResponder(AuditService audit) { this.audit = audit; }

    public ApiException record(HttpServletRequest request, ApiException error) {
        if (!request.getServletPath().startsWith("/api/v1/admin/") || request.getAttribute(RECORDED) != null) return error;
        request.setAttribute(RECORDED, Boolean.TRUE);
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        AdminPrincipal actor = authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal ? principal : null;
        var route = route(request.getMethod(), request.getServletPath());
        try {
            audit.failure(route.action(), actor, route.companyId(), route.deviceId(), error.status(), error.code());
            return error;
        } catch (DataAccessException | TransactionException unavailable) {
            // Nunca imprimir exception, parametros SQL, headers, corpo ou caminho recebido.
            LOG.error("administrative_audit_unavailable correlation_id={} action={}", CorrelationContext.currentOrNew(), route.action());
            return new ApiException(503, "DEPENDENCY_UNAVAILABLE");
        }
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code) throws IOException {
        ApiException error = record(request, new ApiException(status, code));
        ApiErrors.write(response, error.status(), error.code());
    }

    private record Route(AuditAction action, String companyId, String deviceId) {}

    private static Route route(String method, String path) {
        String[] p = path.split("/");
        boolean post = method.equals("POST");
        if (p.length >= 5 && p[4].equals("empresas")) {
            String company = p.length >= 6 ? p[5] : null;
            AuditAction action = AuditAction.ADMIN_REQUEST;
            if (p.length == 5) action = post ? AuditAction.COMPANY_CREATE : AuditAction.COMPANY_LIST;
            else if (p.length == 6) action = AuditAction.COMPANY_READ;
            else if (p.length == 7 && p[6].equals("status")) action = AuditAction.COMPANY_STATUS_CHANGE;
            else if (p.length == 8 && p[6].equals("purga")) action = p[7].equals("reservas") ? AuditAction.PURGE_RESERVE : AuditAction.PURGE_RUN;
            else if (p.length == 7 && p[6].equals("reconciliacao")) action = post ? AuditAction.RECONCILIATION_RUN : AuditAction.RECONCILIATION_READ;
            else if (p.length == 7 && p[6].equals("auditoria")) action = AuditAction.AUDIT_LIST;
            else if (p.length >= 7 && p[6].equals("quadras")) {
                if (p.length == 7) action = post ? AuditAction.COURT_CREATE : AuditAction.COURT_LIST;
                else if (p.length == 8) action = AuditAction.COURT_READ;
                else if (p.length == 9 && p[8].equals("status")) action = AuditAction.COURT_STATUS_CHANGE;
                else if (p.length == 9 && p[8].equals("dispositivos")) action = post ? AuditAction.DEVICE_CREATE : AuditAction.DEVICE_LIST;
            }
            return new Route(action, company, null);
        }
        if (p.length == 7 && p[4].equals("dispositivos") && p[6].equals("configuracoes")) return new Route(AuditAction.CONFIG_PUBLISH, null, p[5]);
        if (p.length == 7 && p[4].equals("lances") && p[6].equals("bloqueio")) return new Route(AuditAction.REPLAY_BLOCK, null, null);
        if (p.length == 7 && p[4].equals("dispositivos") && p[6].equals("status")) return new Route(AuditAction.DEVICE_STATUS_CHANGE, null, p[5]);
        if (p.length == 8 && p[4].equals("dispositivos") && p[6].equals("credenciais") && p[7].equals("rotacao")) return new Route(AuditAction.DEVICE_TOKEN_ROTATE, null, p[5]);
        if (p.length == 6 && p[4].equals("dispositivos")) return new Route(AuditAction.DEVICE_READ, null, p[5]);
        if (p.length == 6 && p[4].equals("auditoria")) return new Route(AuditAction.AUDIT_READ, null, null);
        if (p.length >= 5 && p[4].equals("sessoes")) {
            return new Route(p.length == 5 ? AuditAction.ADMIN_LOGIN : method.equals("DELETE") ? AuditAction.ADMIN_LOGOUT : AuditAction.ADMIN_SESSION_READ, null, null);
        }
        return new Route(AuditAction.ADMIN_REQUEST, null, null);
    }
}
