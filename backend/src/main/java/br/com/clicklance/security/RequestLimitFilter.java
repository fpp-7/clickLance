package br.com.clicklance.security;

import br.com.clicklance.service.RequestLimitService;
import br.com.clicklance.exception.ApiErrorResponder;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RequestLimitFilter extends OncePerRequestFilter {
    private final RequestLimitService limits;
    private final ApiErrorResponder errors;
    public RequestLimitFilter(RequestLimitService limits,ApiErrorResponder errors) { this.limits=limits; this.errors=errors; }
    @Override protected boolean shouldNotFilter(HttpServletRequest r) { return !r.getServletPath().startsWith("/api/"); }
    @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain) throws IOException,ServletException {
        String category=limits.category(r.getServletPath(),r.getMethod()); long start=System.nanoTime();
        try {
            String credential=r.getHeader(category.equals("ingest") || category.equals("heartbeat")?"X-Device-Token":"Authorization");
            if (!limits.allow(category,r.getRemoteAddr(),credential)) { errors.write(r,s,429,"RATE_LIMITED"); return; }
            chain.doFilter(r,s);
        } finally { limits.record(category,s.getStatus(),System.nanoTime()-start); }
    }
}
