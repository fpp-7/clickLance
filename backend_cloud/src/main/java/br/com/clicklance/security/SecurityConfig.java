package br.com.clicklance.security;

import br.com.clicklance.api.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean org.springframework.security.core.userdetails.UserDetailsService noDefaultUsers() {
        return username -> { throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Use autenticação da API."); };
    }
    @Bean SecurityFilterChain security(HttpSecurity http,SecurityProperties properties,JwtService jwt,JdbcTemplate db) throws Exception {
        return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers(HttpMethod.POST,"/api/v1/empresas","/api/v1/quadras","/api/v1/dispositivos").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,"/api/v1/quadras/**","/api/v1/dispositivos/**","/api/v1/empresas/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/meus-lances/**").hasRole("PLAYER")
                .anyRequest().permitAll())
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->error(s,401))
                .accessDeniedHandler((r,s,x)->error(s,403)))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                    try {
                        String admin=req.getHeader("X-Admin-Token"), bearer=req.getHeader("Authorization");
                        if(admin!=null) {
                            if(!MessageDigest.isEqual(admin.getBytes(StandardCharsets.UTF_8),properties.adminToken().getBytes(StandardCharsets.UTF_8))) throw ApiException.unauthorized();
                            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                        } else if(bearer!=null) {
                            if(!bearer.startsWith("Bearer ")) throw ApiException.unauthorized();
                            UUID id=jwt.verify(bearer.substring(7));
                            if(db.queryForObject("SELECT COUNT(*) FROM usuarios WHERE id=? AND ativo=TRUE",Integer.class,id)!=1) throw ApiException.unauthorized();
                            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority("ROLE_PLAYER"))));
                        }
                    } catch(ApiException ex) { error(res,401); return; }
                    chain.doFilter(req,res);
                }
            },UsernamePasswordAuthenticationFilter.class).build();
    }
    private static void error(HttpServletResponse res,int status) throws IOException {
        res.setStatus(status); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"codigo\":\"ACESSO_NEGADO\",\"erro\":\"Autenticação necessária ou permissão insuficiente.\"}");
    }
}
