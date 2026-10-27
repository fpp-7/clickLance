package br.com.clicklance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limite por instância e IP direto; proxy/produção deve complementar com seu próprio controle. */
@Component
public class AttemptLimiter extends OncePerRequestFilter {
    private record Window(long minute,int count) { }
    private final Map<String,Window> attempts=new HashMap<>();
    private final Clock clock;
    public AttemptLimiter(Clock clock) { this.clock=clock; }
    private synchronized boolean allow(String address) {
        long minute=clock.instant().getEpochSecond()/60;
        attempts.entrySet().removeIf(e->e.getValue().minute()<minute);
        Window current=attempts.get(address);
        if(current==null && attempts.size()>=10000) return false;
        int count=current==null?0:current.count();
        if(count>=30) return false;
        attempts.put(address,new Window(minute,count+1)); return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        if("POST".equals(req.getMethod()) && req.getRequestURI().startsWith("/api/v1/auth/") && !allow(req.getRemoteAddr())) {
            res.setStatus(429); res.setHeader("Retry-After","60"); res.setContentType("application/json");
            res.getWriter().write("{\"codigo\":\"LIMITE_TENTATIVAS\",\"erro\":\"Aguarde antes de tentar novamente.\"}"); return;
        }
        chain.doFilter(req,res);
    }
}
