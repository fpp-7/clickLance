package br.com.clicklance.exception;

import br.com.clicklance.security.CorrelationContext;
import br.com.clicklance.service.AuditService;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiAdviceTest {
    @AfterEach void clearContext() { CorrelationContext.clear(); }

    @Test void unexpectedFailureUsesSafe500AndSameCorrelation() {
        UUID id=UUID.randomUUID(); CorrelationContext.set(id);
        var request=new MockHttpServletRequest("GET","/api/v1/admin/empresas"); request.setServletPath("/api/v1/admin/empresas");
        var advice=new ApiAdvice(new ApiErrorResponder(mock(AuditService.class)));
        var result=advice.unexpected(new IllegalStateException("private-password-and-rtsp-url"),request);
        assertThat(result.getStatusCode().value()).isEqualTo(500);
        assertThat(result.getBody()).containsEntry("code","INTERNAL_ERROR").containsEntry("correlation_id",id.toString());
        assertThat(result.getHeaders().getFirst("X-Correlation-ID")).isEqualTo(id.toString());
        assertThat(result.getBody().toString()).doesNotContain("private-password-and-rtsp-url","IllegalStateException");
    }

    @Test void auditDatabaseFailureReplacesRefusalWithSafeRetryable503() {
        var audit=mock(AuditService.class);
        doThrow(new CannotGetJdbcConnectionException("private database credentials")).when(audit).failure(any(),any(),any(),any(),anyInt(),any());
        var request=new MockHttpServletRequest("GET","/api/v1/admin/empresas"); request.setServletPath("/api/v1/admin/empresas");
        var result=new ApiAdvice(new ApiErrorResponder(audit)).api(new ApiException(403,"FORBIDDEN"),request);
        assertThat(result.getStatusCode().value()).isEqualTo(503);
        assertThat(result.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(result.getBody()).containsEntry("code","DEPENDENCY_UNAVAILABLE");
        assertThat(result.getBody().toString()).doesNotContain("private database credentials");
    }
}
