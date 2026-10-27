package br.com.clicklance;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import br.com.clicklance.db.Rows;
import br.com.clicklance.purge.PurgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class Sprint4ApiTest extends ApiTestSupport {
    @Autowired JdbcTemplate db;
    @Autowired PurgeService purge;
    private Map<String,String> player() throws Exception {
        String email="jogador_"+UUID.randomUUID()+"@example.com";
        assertThat(call("POST","/api/v1/auth/cadastro",Map.of("nome","Jogador","email",email,"senha","SenhaForte123!"),Map.of()).status()).isEqualTo(201);
        var login=call("POST","/api/v1/auth/login",Map.of("email",email.toUpperCase(Locale.ROOT),"senha","SenhaForte123!"),Map.of());
        assertThat(login.status()).isEqualTo(200);
        return Map.of("Authorization","Bearer "+login.body().path("access_token").asText());
    }
    private UUID replay() throws Exception {
        var d=setupDevice(); UUID event=UUID.randomUUID(); String key="replays/"+d.path("quadra_id").asText()+"/"+event+".mp4";
        Path path=Path.of("target/test-videos").resolve(key); Files.createDirectories(path.getParent()); Files.write(path,new byte[]{1,2,3});
        var response=call("POST","/api/v1/replays",Map.of("dispositivo_id",d.path("id").asText(),"evento_id",event,"timestamp",Instant.now().toString(),"s3_key",key),Map.of("X-Device-Token",d.path("token_acesso").asText()));
        assertThat(response.status()).isEqualTo(201); return UUID.fromString(response.body().path("id").asText());
    }
    @Test void savedReplaySurvivesExpirationAndIsPrivateToItsPlayer() throws Exception {
        var owner=player(); var other=player(); UUID id=replay();
        assertThat(call("GET","/api/v1/meus-lances",null,Map.of()).status()).isEqualTo(401);
        assertThat(call("POST","/api/v1/empresas",Map.of("nome","Não permitido"),owner).status()).isEqualTo(403);
        assertThat(call("POST","/api/v1/meus-lances",Map.of("lance_id",id),owner).status()).isEqualTo(201);
        assertThat(call("POST","/api/v1/meus-lances",Map.of("lance_id",id),owner).status()).isEqualTo(200);
        db.update("UPDATE lances SET expira_em=? WHERE id=?",Rows.sql(Instant.now().minusSeconds(1)),id);
        purge.runBatch();
        assertThat(db.queryForObject("SELECT status FROM lances WHERE id=?",String.class,id)).isEqualTo("EXPIRADO_SALVO");
        assertThat(call("GET","/api/v1/lances/"+id,null,Map.of()).status()).isEqualTo(404);
        assertThat(call("GET","/api/v1/lances/"+id,null,other).status()).isEqualTo(404);
        assertThat(call("GET","/api/v1/lances/"+id,null,owner).status()).isEqualTo(200);
        var library=call("GET","/api/v1/meus-lances",null,owner);
        assertThat(library.status()).isEqualTo(200); assertThat(library.body().path("total_lances").asInt()).isEqualTo(1);
        assertThat(library.body().path("lances").get(0).path("video_url").asText()).startsWith("/api/v1/media/");
        assertThat(call("GET","/api/v1/meus-lances",null,other).body().path("total_lances").asInt()).isZero();
        assertThat(call("POST","/api/v1/meus-lances",Map.of("lance_id",id),other).status()).isEqualTo(400);
    }
    @Test void unownedExpiredReplayIsDeletedAndMetadataRemains() throws Exception {
        UUID id=replay(); String key=db.queryForObject("SELECT s3_key FROM lances WHERE id=?",String.class,id);
        db.update("UPDATE lances SET expira_em=? WHERE id=?",Rows.sql(Instant.now().minusSeconds(1)),id);
        purge.runBatch(); purge.runBatch();
        assertThat(Files.exists(Path.of("target/test-videos").resolve(key))).isFalse();
        assertThat(db.queryForObject("SELECT status FROM lances WHERE id=?",String.class,id)).isEqualTo("EXPIRADO_PURGADO");
        assertThat(call("GET","/api/v1/lances/"+id,null,Map.of()).status()).isEqualTo(404);
    }
    @Test void passwordAndJwtAreValidatedAndInactiveUsersLoseAccess() throws Exception {
        String email="USER_"+UUID.randomUUID()+"@example.com";
        var body=Map.of("nome","Jogador","email",email,"senha","SenhaForte123!");
        var signup=call("POST","/api/v1/auth/cadastro",body,Map.of()); assertThat(signup.status()).isEqualTo(201);
        String normalized=email.toLowerCase(Locale.ROOT); UUID id=UUID.fromString(signup.body().path("id").asText());
        assertThat(db.queryForObject("SELECT email FROM usuarios WHERE id=?",String.class,id)).isEqualTo(normalized);
        assertThat(db.queryForObject("SELECT senha_hash FROM usuarios WHERE id=?",String.class,id)).startsWith("$2");
        assertThat(signup.body().has("senha_hash")).isFalse();
        assertThat(call("POST","/api/v1/auth/cadastro",Map.of("nome","Outro","email",normalized,"senha","SenhaForte123!"),Map.of()).status()).isEqualTo(409);
        assertThat(call("POST","/api/v1/auth/cadastro",Map.of("nome","Outro","email","outro@example.com","senha","á".repeat(40)),Map.of()).status()).isEqualTo(400);
        assertThat(call("POST","/api/v1/auth/login",Map.of("email",email,"senha","incorreta"),Map.of()).status()).isEqualTo(401);
        var login=call("POST","/api/v1/auth/login",Map.of("email",email,"senha","SenhaForte123!"),Map.of());
        String token=login.body().path("access_token").asText();
        assertThat(call("GET","/api/v1/meus-lances",null,Map.of("Authorization","Bearer "+token+"x")).status()).isEqualTo(401);
        db.update("UPDATE usuarios SET ativo=FALSE WHERE id=?",id);
        assertThat(call("GET","/api/v1/meus-lances",null,Map.of("Authorization","Bearer "+token)).status()).isEqualTo(401);
    }
}
