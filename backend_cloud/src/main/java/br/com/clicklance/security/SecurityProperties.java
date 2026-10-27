package br.com.clicklance.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("clicklance.security")
public record SecurityProperties(String adminToken,String jwtSecret,long jwtTtlSeconds) {
    public SecurityProperties {
        if(adminToken==null || adminToken.length()<32 || jwtSecret==null || jwtSecret.length()<32)
            throw new IllegalArgumentException("ADMIN_TOKEN e JWT_SECRET devem conter pelo menos 32 caracteres.");
        if(adminToken.equals(jwtSecret)) throw new IllegalArgumentException("Segredos administrativos e de sessão devem ser distintos.");
        if(jwtTtlSeconds<60 || jwtTtlSeconds>86400) throw new IllegalArgumentException("TTL de sessão deve estar entre 60 e 86400 segundos.");
    }
}
