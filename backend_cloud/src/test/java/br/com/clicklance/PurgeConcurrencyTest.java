package br.com.clicklance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.clicklance.admin.AdminService;
import br.com.clicklance.catalog.CatalogService;
import br.com.clicklance.db.Rows;
import br.com.clicklance.library.LibraryService;
import br.com.clicklance.purge.PurgeService;
import br.com.clicklance.storage.VideoStorage;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PurgeConcurrencyTest extends ApiTestSupport {
    @Autowired JdbcTemplate db; @Autowired TransactionTemplate tx;
    @Autowired AdminService admin; @Autowired CatalogService catalog;
    private UUID user() {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO usuarios(id,nome,email,senha_hash,criado_em) VALUES(?,?,?,?,?)",id,"Teste",id+"@example.com","unused-in-this-test",Rows.sql(Instant.now()));
        return id;
    }
    private UUID replay(String device,UUID quadra,Instant expiry,String status) {
        UUID id=UUID.randomUUID(),event=UUID.randomUUID();
        db.update("INSERT INTO lances(id,quadra_id,dispositivo_id,evento_id,timestamp_evento,s3_key,tamanho_bytes,expira_em,criado_em,status) VALUES(?,?,?,?,?,?,3,?,?,?)",
            id,quadra,device,event,Rows.sql(expiry.minusSeconds(100)),"replays/"+quadra+"/"+event+".mp4",Rows.sql(expiry),Rows.sql(Instant.now()),status);
        return id;
    }
    @Test void savingBeforeExpirationAndConcurrentPurgePreserveTheObject() throws Exception {
        var d=setupDevice(); UUID q=UUID.fromString(d.path("quadra_id").asText()),owner=user();
        Instant before=Instant.now(); UUID id=replay(d.path("id").asText(),q,before.plusSeconds(600),"DISPONIVEL");
        String key=db.queryForObject("SELECT s3_key FROM lances WHERE id=?",String.class,id);
        VideoStorage storage=mock(VideoStorage.class);
        var library=new LibraryService(db,Clock.fixed(before,ZoneOffset.UTC),admin,catalog);
        var purge=new PurgeService(db,Clock.fixed(before.plusSeconds(601),ZoneOffset.UTC),storage,tx,100);
        var saved=new CountDownLatch(1); var release=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> save=pool.submit(()->tx.execute(s->{
                library.save(owner,id); saved.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout do teste"); }
                catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
                return null;
            }));
            assertThat(saved.await(10,TimeUnit.SECONDS)).isTrue();
            Future<Integer> deletion=pool.submit(purge::runBatch);
            assertThatThrownBy(()->deletion.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); save.get(10,TimeUnit.SECONDS); deletion.get(10,TimeUnit.SECONDS);
            assertThat(db.queryForObject("SELECT status FROM lances WHERE id=?",String.class,id)).isEqualTo("EXPIRADO_SALVO");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM usuario_lances WHERE lance_id=?",Integer.class,id)).isEqualTo(1);
            verify(storage,never()).delete(key);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void retainedClipsAndDeferredFailuresDoNotStarveNewDeletion() throws Exception {
        var d=setupDevice(); UUID q=UUID.fromString(d.path("quadra_id").asText()),owner=user();
        Instant old=Instant.parse("1960-01-01T00:00:00Z");
        for(int i=0;i<4;i++) {
            UUID saved=replay(d.path("id").asText(),q,old.plusSeconds(i),"EXPIRADO_SALVO");
            db.update("INSERT INTO usuario_lances(id,usuario_id,lance_id,salvo_em) VALUES(?,?,?,?)",UUID.randomUUID(),owner,saved,Rows.sql(old));
            UUID failed=replay(d.path("id").asText(),q,old.plusSeconds(i),"PURGA_PENDENTE");
            db.update("UPDATE lances SET purga_proxima_tentativa=? WHERE id=?",Rows.sql(Instant.now().plusSeconds(3600)),failed);
        }
        UUID orphan=replay(d.path("id").asText(),q,old.plusSeconds(100),"DISPONIVEL");
        VideoStorage storage=mock(VideoStorage.class);
        new PurgeService(db,Clock.systemUTC(),storage,tx,3).runBatch();
        assertThat(db.queryForObject("SELECT status FROM lances WHERE id=?",String.class,orphan)).isEqualTo("EXPIRADO_PURGADO");
    }
}
