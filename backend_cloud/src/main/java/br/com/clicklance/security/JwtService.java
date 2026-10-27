package br.com.clicklance.security;

import br.com.clicklance.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class JwtService {
    private static final Base64.Encoder ENCODER=Base64.getUrlEncoder().withoutPadding();
    private final SecurityProperties config;
    private final Clock clock;
    private final JsonMapper json=JsonMapper.builder().build();
    public JwtService(SecurityProperties config,Clock clock) { this.config=config; this.clock=clock; }
    public String issue(UUID userId) {
        long now=clock.instant().getEpochSecond();
        String payload=encode(json.writeValueAsBytes(Map.of("iss","clicklance","aud","clicklance-player","sub",userId.toString(),"iat",now,"exp",now+config.jwtTtlSeconds())));
        String unsigned=encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8))+"."+payload;
        return unsigned+"."+encode(sign(unsigned));
    }
    public UUID verify(String token) {
        try {
            if(token.length()>4096) throw new IllegalArgumentException();
            String[] parts=token.split("\\.",-1);
            if(parts.length!=3 || !MessageDigest.isEqual(sign(parts[0]+"."+parts[1]),Base64.getUrlDecoder().decode(parts[2]))) throw new IllegalArgumentException();
            var header=json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            var claims=json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long now=clock.instant().getEpochSecond();
            if(!"HS256".equals(header.path("alg").asText()) || !"JWT".equals(header.path("typ").asText())
                || !"clicklance".equals(claims.path("iss").asText()) || !"clicklance-player".equals(claims.path("aud").asText())
                || !claims.path("exp").isIntegralNumber() || claims.path("exp").asLong()<=now
                || !claims.path("iat").isIntegralNumber() || claims.path("iat").asLong()>now+30) throw new IllegalArgumentException();
            return UUID.fromString(claims.path("sub").asText());
        } catch(Exception ex) { throw ApiException.unauthorized(); }
    }
    private byte[] sign(String input) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.jwtSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
        } catch(Exception ex) { throw new IllegalStateException("Não foi possível assinar a sessão",ex); }
    }
    private static String encode(byte[] bytes) { return ENCODER.encodeToString(bytes); }
}
