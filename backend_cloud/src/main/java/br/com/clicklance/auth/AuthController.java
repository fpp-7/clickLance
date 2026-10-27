package br.com.clicklance.auth;

import br.com.clicklance.api.ApiException;
import br.com.clicklance.db.Rows;
import br.com.clicklance.db.Rows.User;
import br.com.clicklance.security.JwtService;
import br.com.clicklance.security.SecurityProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record Signup(@NotBlank @Size(max=100) String nome,@NotBlank @Email @Size(max=150) String email,
                         @NotBlank @Size(min=10,max=72) String senha) { }
    public record Login(@NotBlank @Email @Size(max=150) String email,@NotBlank @Size(max=256) String senha) { }
    private final JdbcTemplate db; private final PasswordEncoder encoder; private final JwtService jwt;
    private final SecurityProperties config; private final Clock clock; private final String dummy;
    public AuthController(JdbcTemplate db,PasswordEncoder encoder,JwtService jwt,SecurityProperties config,Clock clock) {
        this.db=db; this.encoder=encoder; this.jwt=jwt; this.config=config; this.clock=clock;
        dummy=encoder.encode(UUID.randomUUID().toString());
    }
    @PostMapping("/cadastro")
    ResponseEntity<?> signup(@Valid @RequestBody Signup body) {
        if(body.senha().getBytes(StandardCharsets.UTF_8).length>72) throw ApiException.invalid("A senha não pode exceder 72 bytes UTF-8.");
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO usuarios(id,nome,email,senha_hash,criado_em) VALUES(?,?,?,?,?)",id,body.nome().strip(),email(body.email()),encoder.encode(body.senha()),Rows.sql(clock.instant()));
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(publicUser(user(id)));
    }
    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody Login body) {
        var users=db.query("SELECT * FROM usuarios WHERE email=?",Rows.USER,email(body.email()));
        User user=users.isEmpty()?null:users.getFirst();
        boolean valid=body.senha().getBytes(StandardCharsets.UTF_8).length<=72 && encoder.matches(body.senha(),user==null?dummy:user.senhaHash());
        if(!valid || user==null || !user.ativo()) throw ApiException.unauthorized();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token_tipo","Bearer","access_token",jwt.issue(user.id()),"expira_em_segundos",config.jwtTtlSeconds(),"usuario",publicUser(user)));
    }
    private User user(UUID id) { return db.query("SELECT * FROM usuarios WHERE id=?",Rows.USER,id).getFirst(); }
    private static String email(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private static Map<String,Object> publicUser(User user) { return Map.of("id",user.id(),"nome",user.nome(),"email",user.email(),"criado_em",user.criado_em()); }
}
