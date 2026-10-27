package br.com.clicklance;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class Sprint2ApiTest extends ApiTestSupport {
    @Autowired JdbcTemplate db;
    @Test void ingestAuthenticatesValidatesOriginAndIsStableAfterPurge() throws Exception {
        var d=setupDevice(); UUID event=UUID.randomUUID();
        String key="replays/"+d.path("quadra_id").asText()+"/"+event+".mp4";
        var body=Map.of("dispositivo_id",d.path("id").asText(),"evento_id",event,"timestamp",Instant.now().truncatedTo(ChronoUnit.MICROS).toString(),"s3_key",key);
        var auth=Map.of("X-Device-Token",d.path("token_acesso").asText());
        assertThat(call("POST","/api/v1/replays",body,Map.of()).status()).isEqualTo(401);
        assertThat(call("POST","/api/v1/replays",body,auth).status()).isEqualTo(404);
        Path file=Path.of("target/test-videos").resolve(key); Files.createDirectories(file.getParent()); Files.write(file,new byte[]{0,1,2,3});
        var first=call("POST","/api/v1/replays",body,auth); assertThat(first.status()).isEqualTo(201);
        assertThat(first.body().path("quadra_id").asText()).isEqualTo(d.path("quadra_id").asText());
        Files.delete(file);
        var repeated=call("POST","/api/v1/replays",body,auth); assertThat(repeated.status()).isEqualTo(200);
        assertThat(repeated.body().path("id").asText()).isEqualTo(first.body().path("id").asText());
        var conflict=new java.util.HashMap<String,Object>(body); conflict.put("timestamp",Instant.now().minusSeconds(30).toString());
        assertThat(call("POST","/api/v1/replays",conflict,auth).status()).isEqualTo(409);
        conflict.put("evento_id",UUID.randomUUID()); conflict.put("s3_key","replays/outra-quadra/qualquer.mp4");
        assertThat(call("POST","/api/v1/replays",conflict,auth).status()).isEqualTo(400);
    }
    @Test void concurrentRetriesCreateExactlyOneReplay() throws Exception {
        var d=setupDevice(); UUID event=UUID.randomUUID();
        String key="replays/"+d.path("quadra_id").asText()+"/"+event+".mp4";
        Path file=Path.of("target/test-videos").resolve(key); Files.createDirectories(file.getParent()); Files.write(file,new byte[]{0,1,2});
        var body=Map.of("dispositivo_id",d.path("id").asText(),"evento_id",event,"timestamp",Instant.now().toString(),"s3_key",key);
        var auth=Map.of("X-Device-Token",d.path("token_acesso").asText());
        var first=CompletableFuture.supplyAsync(()->{try{return call("POST","/api/v1/replays",body,auth);}catch(Exception ex){throw new RuntimeException(ex);}});
        var second=CompletableFuture.supplyAsync(()->{try{return call("POST","/api/v1/replays",body,auth);}catch(Exception ex){throw new RuntimeException(ex);}});
        assertThat(java.util.List.of(first.get().status(),second.get().status())).containsExactlyInAnyOrder(201,200);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM lances WHERE dispositivo_id=? AND evento_id=?",Integer.class,d.path("id").asText(),event)).isEqualTo(1);
    }
    @Test void timestampMustHaveOffsetAndBeWithinRetention() throws Exception {
        var d=setupDevice(); UUID event=UUID.randomUUID(); String key="replays/"+d.path("quadra_id").asText()+"/"+event+".mp4";
        var body=new java.util.HashMap<String,Object>(Map.of("dispositivo_id",d.path("id").asText(),"evento_id",event,"timestamp","2026-10-26T22:00:00","s3_key",key));
        var auth=Map.of("X-Device-Token",d.path("token_acesso").asText());
        assertThat(call("POST","/api/v1/replays",body,auth).status()).isEqualTo(400);
        body.put("timestamp",Instant.now().plusSeconds(300).toString());
        assertThat(call("POST","/api/v1/replays",body,auth).status()).isEqualTo(400);
        body.put("timestamp",Instant.now().minus(3,ChronoUnit.DAYS).toString());
        assertThat(call("POST","/api/v1/replays",body,auth).status()).isEqualTo(422);
    }
    @Test void equivalentOffsetsAndNanosecondsRemainIdempotent() throws Exception {
        var d=setupDevice(); UUID event=UUID.randomUUID(); String key="replays/"+d.path("quadra_id").asText()+"/"+event+".mp4";
        Instant time=Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.SECONDS).plusNanos(123456789);
        Path file=Path.of("target/test-videos").resolve(key); Files.createDirectories(file.getParent()); Files.write(file,new byte[]{1,2,3});
        var body=new java.util.HashMap<String,Object>(Map.of("dispositivo_id",d.path("id").asText(),"evento_id",event,"timestamp",time.toString(),"s3_key",key));
        var auth=Map.of("X-Device-Token",d.path("token_acesso").asText());
        var first=call("POST","/api/v1/replays",body,auth); assertThat(first.status()).isEqualTo(201);
        body.put("timestamp",time.atOffset(java.time.ZoneOffset.ofHours(-3)).toString());
        var second=call("POST","/api/v1/replays",body,auth); assertThat(second.status()).isEqualTo(200);
        assertThat(second.body().path("id").asText()).isEqualTo(first.body().path("id").asText());
    }
}
