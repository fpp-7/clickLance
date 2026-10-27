package br.com.clicklance.purge;

import br.com.clicklance.db.Rows;
import br.com.clicklance.db.Rows.Lance;
import br.com.clicklance.storage.VideoStorage;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PurgeService {
    private static final Logger log=LoggerFactory.getLogger(PurgeService.class);
    private final JdbcTemplate db; private final Clock clock; private final VideoStorage storage; private final TransactionTemplate tx;
    private final int batchSize;
    public PurgeService(JdbcTemplate db,Clock clock,VideoStorage storage,TransactionTemplate tx,@Value("${clicklance.purge.batch-size:100}") int batchSize) {
        this.db=db; this.clock=clock; this.storage=storage; this.tx=tx;
        if(batchSize<1 || batchSize>1000) throw new IllegalArgumentException("Lote de purga inválido"); this.batchSize=batchSize;
    }
    public int runBatch() {
        var ids=db.query("SELECT id FROM lances l WHERE (status='PURGA_PENDENTE' AND (purga_proxima_tentativa IS NULL OR purga_proxima_tentativa<=?)) OR (expira_em<=? AND (status='DISPONIVEL' OR (status='EXPIRADO_SALVO' AND NOT EXISTS(SELECT 1 FROM usuario_lances u WHERE u.lance_id=l.id)))) ORDER BY expira_em,id LIMIT ?",
            (r,n)->r.getObject("id",UUID.class),Rows.sql(clock.instant()),Rows.sql(clock.instant()),batchSize);
        int deleted=0; long bytes=0;
        for(UUID id:ids) {
            try {
                Lance candidate=tx.execute(status->{
                    Lance l=db.query("SELECT * FROM lances WHERE id=? FOR UPDATE",Rows.LANCE,id).getFirst();
                    if("EXPIRADO_PURGADO".equals(l.status()) || l.expira_em().isAfter(clock.instant())) return null;
                    if("PURGA_PENDENTE".equals(l.status())) {
                        var next=db.queryForObject("SELECT purga_proxima_tentativa FROM lances WHERE id=?",java.time.OffsetDateTime.class,id);
                        if(next!=null && next.toInstant().isAfter(clock.instant())) return null;
                    }
                    int links=db.queryForObject("SELECT COUNT(*) FROM usuario_lances WHERE lance_id=?",Integer.class,id);
                    if(links>0) {
                        if(!"EXPIRADO_SALVO".equals(l.status())) db.update("UPDATE lances SET status='EXPIRADO_SALVO' WHERE id=?",id);
                        return null;
                    }
                    int attempts=db.queryForObject("SELECT purga_tentativas FROM lances WHERE id=?",Integer.class,id);
                    long delay=Math.min(3600,60L << Math.min(attempts,6));
                    db.update("UPDATE lances SET status='PURGA_PENDENTE',purga_tentativas=purga_tentativas+1,purga_proxima_tentativa=? WHERE id=?",Rows.sql(clock.instant().plusSeconds(delay)),id);
                    return l;
                });
                if(candidate==null) continue;
                // A intenção está confirmada no banco; nenhuma transação permanece aberta durante a chamada externa.
                storage.delete(candidate.s3_key());
                int changed=db.update("UPDATE lances SET status='EXPIRADO_PURGADO',purga_proxima_tentativa=NULL WHERE id=? AND status='PURGA_PENDENTE'",id);
                if(changed==1) { deleted++; bytes+=candidate.tamanho_bytes(); }
            } catch(Exception ex) { log.warn("Purga pendente para lance {}: {}",id,ex.getClass().getSimpleName()); }
        }
        log.info("Purga: {} arquivo(s), {} bytes confirmados",deleted,bytes); return deleted;
    }
}
