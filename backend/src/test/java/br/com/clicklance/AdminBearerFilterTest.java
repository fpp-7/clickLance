package br.com.clicklance;

import br.com.clicklance.security.AdminBearerFilter;
import br.com.clicklance.service.AdminSessionService;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AdminBearerFilterTest {
    @Test void sessionDatabaseFailureNeverFallsBackToJwtOnly() throws Exception {
        for (RuntimeException failure : java.util.List.of(
                new CannotGetJdbcConnectionException("sensitive database detail"),
                new org.springframework.transaction.CannotCreateTransactionException("sensitive transaction detail"))) {
        var sessions = mock(AdminSessionService.class);
        when(sessions.authenticate("synthetic.token.only")).thenThrow(failure);
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/sessoes/atual");
        request.setServletPath("/api/v1/admin/sessoes/atual");
        request.addHeader("Authorization", "Bearer synthetic.token.only");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new AdminBearerFilter(sessions).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getContentAsString()).doesNotContain("sensitive", "synthetic.token.only");
        verifyNoInteractions(chain);
        }
    }
}
