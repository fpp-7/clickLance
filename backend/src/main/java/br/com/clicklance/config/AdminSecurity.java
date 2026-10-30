package br.com.clicklance.config;

import br.com.clicklance.security.AdminBearerFilter;
import br.com.clicklance.service.AdminSessionService;
import br.com.clicklance.service.PlayerSessionService;
import br.com.clicklance.security.PlayerBearerFilter;
import br.com.clicklance.security.RequestLimitFilter;
import br.com.clicklance.service.RequestLimitService;
import br.com.clicklance.exception.ApiErrorResponder;
import br.com.clicklance.security.CorrelationFilter;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AdminSecurity {
    @Bean
    SecurityFilterChain administrativeSecurity(HttpSecurity http, AdminSessionService sessions,
                                                PlayerSessionService players, RequestLimitService limits,
                                                ApiErrorResponder errors) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/sessoes").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/jogadores", "/api/v1/jogadores/sessoes",
                                "/api/v1/jogadores/recuperacao", "/api/v1/jogadores/redefinicao").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/dispositivos/*/config").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/dispositivos/*/saude").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/replays").permitAll()
                        .requestMatchers("/api/v1/admin/**").authenticated()
                        .requestMatchers("/api/v1/jogadores/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401, "UNAUTHENTICATED"))
                        .accessDeniedHandler((req, res, ex) -> errors.write(req, res, 403, "FORBIDDEN")))
                .addFilterBefore(new AdminBearerFilter(sessions, errors), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new PlayerBearerFilter(players, errors), AdminBearerFilter.class)
                .addFilterBefore(new RequestLimitFilter(limits, errors), PlayerBearerFilter.class)
                .addFilterBefore(new CorrelationFilter(), RequestLimitFilter.class)
                .build();
    }
}
