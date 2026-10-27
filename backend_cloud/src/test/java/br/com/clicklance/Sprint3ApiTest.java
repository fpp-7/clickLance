package br.com.clicklance;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import br.com.clicklance.db.Rows;

class Sprint3ApiTest extends ApiTestSupport {
    @Autowired JdbcTemplate db;
    private UUID replay(String device,UUID quadra,Instant event,Instant expiry) throws Exception {
        UUID id=UUID.randomUUID(),eventId=UUID.randomUUID(); String key="replays/"+quadra+"/"+eventId+".mp4";
        Path file=Path.of("target/test-videos").resolve(key); Files.createDirectories(file.getParent()); Files.write(file,new byte[]{1,2,3});
        db.update("INSERT INTO lances(id,quadra_id,dispositivo_id,evento_id,timestamp_evento,s3_key,tamanho_bytes,expira_em,criado_em) VALUES(?,?,?,?,?,?,3,?,?)",id,quadra,device,eventId,Rows.sql(event),key,Rows.sql(expiry),Rows.sql(Instant.now()));
        return id;
    }
    @Test void listUsesQuadraTimezoneHalfOpenIntervalsAndTemporaryUrls() throws Exception {
        var d=setupDevice(); UUID q=UUID.fromString(d.path("quadra_id").asText());
        LocalDate day=LocalDate.of(2026,10,20); ZoneId zone=ZoneId.of("America/Sao_Paulo");
        UUID first=replay(d.path("id").asText(),q,day.atTime(22,0).atZone(zone).toInstant(),Instant.now().plusSeconds(3600));
        UUID boundary=replay(d.path("id").asText(),q,day.atTime(23,0).atZone(zone).toInstant(),Instant.now().plusSeconds(3600));
        var page=call("GET","/api/v1/quadras/"+q+"/lances?data="+day+"&hora_inicio=22:00&hora_fim=23:00",null,Map.of());
        assertThat(page.status()).isEqualTo(200); assertThat(page.body().path("total_lances").asInt()).isEqualTo(1);
        var clip=page.body().path("lances").get(0);
        assertThat(clip.path("id").asText()).isEqualTo(first.toString());
        assertThat(clip.path("horario_formatado").asText()).isEqualTo("22:00");
        assertThat(clip.path("video_url").asText()).startsWith("/api/v1/media/");
        assertThat(clip.has("s3_key")).isFalse();
        var next=call("GET","/api/v1/quadras/"+q+"/lances?data="+day+"&hora_inicio=23:00&hora_fim=00:00",null,Map.of());
        assertThat(next.body().path("lances").get(0).path("id").asText()).isEqualTo(boundary.toString());
        assertThat(call("GET","/api/v1/quadras/"+q+"/lances?data="+day+"&hora_inicio=22:00",null,Map.of()).status()).isEqualTo(400);
        assertThat(call("GET","/api/v1/quadras/"+q+"/lances?data="+day+"&tamanho=101",null,Map.of()).status()).isEqualTo(400);
    }
    @Test void expiredReplayIsHiddenInListAndDetailEvenBeforeJobRuns() throws Exception {
        var d=setupDevice(); UUID q=UUID.fromString(d.path("quadra_id").asText()); Instant now=Instant.now();
        UUID id=replay(d.path("id").asText(),q,now.minusSeconds(60),now.minusSeconds(1));
        LocalDate day=now.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate();
        assertThat(call("GET","/api/v1/lances/"+id,null,Map.of()).status()).isEqualTo(404);
        var page=call("GET","/api/v1/quadras/"+q+"/lances?data="+day,null,Map.of());
        assertThat(page.body().path("total_lances").asInt()).isZero();
        UUID active=replay(d.path("id").asText(),q,now,now.plusSeconds(3600));
        assertThat(call("GET","/api/v1/lances/"+active,null,Map.of()).status()).isEqualTo(200);
        admin("PATCH","/api/v1/quadras/"+q,Map.of("ativo",false));
        assertThat(call("GET","/api/v1/lances/"+active,null,Map.of()).status()).isEqualTo(404);
        assertThat(call("GET","/api/v1/quadras/"+q+"/lances?data="+day,null,Map.of()).status()).isEqualTo(404);
    }
}
