package br.com.clicklance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import br.com.clicklance.db.Rows;
import br.com.clicklance.purge.PurgeService;
import br.com.clicklance.storage.VideoStorage;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PurgeRetryTest extends ApiTestSupport {
    @Autowired JdbcTemplate db; @Autowired Clock clock; @Autowired TransactionTemplate tx;
    @Test void deletionFailureStaysDurableAndCanBeRetried() throws Exception {
        var d=setupDevice(); UUID q=UUID.fromString(d.path("quadra_id").asText()),id=UUID.randomUUID(),event=UUID.randomUUID();
        String key="replays/"+q+"/"+event+".mp4";
        db.update("INSERT INTO lances(id,quadra_id,dispositivo_id,evento_id,timestamp_evento,s3_key,tamanho_bytes,expira_em,criado_em) VALUES(?,?,?,?,?,?,3,?,?)",id,q,d.path("id").asText(),event,Rows.sql(Instant.now().minusSeconds(120)),key,Rows.sql(Instant.now().minusSeconds(60)),Rows.sql(Instant.now()));
        VideoStorage storage=mock(VideoStorage.class); doThrow(new RuntimeException("offline")).doNothing().when(storage).delete(key);
        var purge=new PurgeService(db,clock,storage,tx,100);
        purge.runBatch();
        assertThat(db.queryForObject("SELECT status FROM lances WHERE id=?",String.class,id)).isEqualTo("PURGA_PENDENTE");
        assertThat(db.queryForObject("SELECT purga_tentativas FROM lances WHERE id=?",Integer.class,id)).isEqualTo(1);
        db.update("UPDATE lances SET purga_proxima_tentativa=? WHERE id=?",Rows.sql(Instant.now().minusSeconds(1)),id);
        purge.runBatch();
        assertThat(db.queryForObject("SELECT status FROM lances WHERE id=?",String.class,id)).isEqualTo("EXPIRADO_PURGADO");
        verify(storage,times(2)).delete(key);
    }
}
