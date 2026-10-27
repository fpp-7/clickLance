package br.com.clicklance;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class Sprint1ApiTest extends ApiTestSupport {
    @Autowired JdbcTemplate db;
    @Test void administrativeRoutesRequireSecretAndValidateInput() throws Exception {
        assertThat(call("POST","/api/v1/empresas",Map.of("nome","Invasor"),Map.of()).status()).isEqualTo(401);
        assertThat(call("POST","/api/v1/empresas",Map.of("nome","Invasor"),Map.of("X-Admin-Token","errado")).status()).isEqualTo(401);
        assertThat(admin("POST","/api/v1/empresas",Map.of("nome"," ","dias_retencao_padrao",0)).status()).isEqualTo(400);
        assertThat(admin("POST","/api/v1/quadras",Map.of("empresa_id",java.util.UUID.randomUUID(),"nome","Quadra")).status()).isEqualTo(404);
    }
    @Test void deviceTokenIsHashedAndCannotReadAnotherDeviceConfiguration() throws Exception {
        var first=setupDevice(); var second=setupDevice();
        String id=first.path("id").asText(),token=first.path("token_acesso").asText();
        assertThat(db.queryForObject("SELECT token_acesso_hash FROM dispositivos WHERE id=?",String.class,id)).isNotEqualTo(token).startsWith("$2");
        var config=call("GET","/api/v1/dispositivos/"+id+"/config",null,Map.of("X-Device-Token",token));
        assertThat(config.status()).isEqualTo(200);
        assertThat(config.body().has("token_acesso")).isFalse();
        assertThat(config.body().path("segmentos_concatenar").asInt()).isEqualTo(2);
        assertThat(call("GET","/api/v1/dispositivos/"+second.path("id").asText()+"/config",null,Map.of("X-Device-Token",token)).status()).isEqualTo(401);
        assertThat(call("GET","/api/v1/dispositivos/"+id+"/config",null,Map.of()).status()).isEqualTo(401);
        admin("PATCH","/api/v1/dispositivos/"+id,Map.of("ativo",false));
        assertThat(call("GET","/api/v1/dispositivos/"+id+"/config",null,Map.of("X-Device-Token",token)).status()).isEqualTo(401);
    }
    @Test void disablingQuadraOrEmpresaDisablesConfig() throws Exception {
        var device=setupDevice(); String q=device.path("quadra_id").asText();
        var auth=Map.of("X-Device-Token",device.path("token_acesso").asText());
        assertThat(admin("PATCH","/api/v1/quadras/"+q,Map.of("duracao_segmento_segundos",60,"segmentos_concatenar",300)).status()).isEqualTo(400);
        admin("PATCH","/api/v1/quadras/"+q,Map.of("ativo",false));
        assertThat(call("GET","/api/v1/dispositivos/"+device.path("id").asText()+"/config",null,auth).status()).isEqualTo(401);
        admin("PATCH","/api/v1/quadras/"+q,Map.of("ativo",true));
        var empresa=db.queryForObject("SELECT empresa_id FROM quadras WHERE id=?",java.util.UUID.class,java.util.UUID.fromString(q));
        admin("PATCH","/api/v1/empresas/"+empresa,Map.of("ativo",false));
        assertThat(call("GET","/api/v1/dispositivos/"+device.path("id").asText()+"/config",null,auth).status()).isEqualTo(401);
    }
}
