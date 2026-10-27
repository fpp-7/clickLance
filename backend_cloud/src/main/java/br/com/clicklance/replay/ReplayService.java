package br.com.clicklance.replay;

import br.com.clicklance.admin.AdminService;
import br.com.clicklance.api.ApiException;
import br.com.clicklance.db.Rows;
import br.com.clicklance.db.Rows.*;
import br.com.clicklance.storage.VideoStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReplayService {
    public record IngestResult(Lance lance,boolean created) { }
    private final JdbcTemplate db; private final AdminService admin; private final Clock clock;
    private final VideoStorage storage; private final TransactionTemplate tx;
    public ReplayService(JdbcTemplate db,AdminService admin,Clock clock,VideoStorage storage,TransactionTemplate tx) {
        this.db=db; this.admin=admin; this.clock=clock; this.storage=storage; this.tx=tx;
    }
    public IngestResult ingest(String deviceId,String token,UUID eventId,String timestamp,String key) {
        Device device=admin.authenticateDevice(deviceId,token);
        Instant eventTime;
        try { eventTime=OffsetDateTime.parse(timestamp).toInstant().truncatedTo(ChronoUnit.MICROS); }
        catch(Exception ex) { throw ApiException.invalid("timestamp deve conter offset, por exemplo 2026-10-26T22:14:50-03:00."); }
        String expected="replays/"+device.quadra_id()+"/"+eventId+".mp4";
        if(!expected.equals(key)) throw ApiException.invalid("s3_key não corresponde à quadra e ao evento autenticados.");
        Lance previous=existing(deviceId,eventId);
        if(previous!=null) return repeated(previous,eventTime,key);
        Instant now=clock.instant();
        if(eventTime.isAfter(now.plusSeconds(120))) throw ApiException.invalid("timestamp está mais de dois minutos no futuro.");
        var q=admin.activeQuadra(device.quadra_id());
        Instant expiry=eventTime.plus(admin.empresa(q.empresa_id()).dias_retencao_padrao(),ChronoUnit.DAYS);
        if(!expiry.isAfter(now)) throw new ApiException(422,"EVENTO_EXPIRADO","Evento chegou após a sua janela de retenção.");
        long size=storage.verify(key).sizeBytes();
        return tx.execute(status->{
            // Serializa ingestão por dispositivo; a constraint mantém a identidade também no banco.
            db.queryForObject("SELECT id FROM dispositivos WHERE id=? FOR UPDATE",String.class,deviceId);
            admin.authenticateDevice(deviceId,token);
            Lance duplicate=existing(deviceId,eventId);
            if(duplicate!=null) return repeated(duplicate,eventTime,key);
            if(db.queryForObject("SELECT COUNT(*) FROM lances WHERE s3_key=?",Integer.class,key)>0)
                throw ApiException.conflict("Objeto já associado a outro evento.");
            UUID id=UUID.randomUUID();
            db.update("INSERT INTO lances(id,quadra_id,dispositivo_id,evento_id,timestamp_evento,s3_key,tamanho_bytes,expira_em,criado_em) VALUES(?,?,?,?,?,?,?,?,?)",
                id,device.quadra_id(),deviceId,eventId,Rows.sql(eventTime),key,size,Rows.sql(expiry),Rows.sql(now));
            return new IngestResult(lance(id),true);
        });
    }
    public Lance lance(UUID id) { return db.query("SELECT * FROM lances WHERE id=?",Rows.LANCE,id).stream().findFirst().orElseThrow(ApiException::notFound); }
    private Lance existing(String device,UUID event) {
        List<Lance> rows=db.query("SELECT * FROM lances WHERE dispositivo_id=? AND evento_id=?",Rows.LANCE,device,event);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private IngestResult repeated(Lance old,Instant time,String key) {
        if(!old.timestamp_evento().equals(time) || !old.s3_key().equals(key)) throw ApiException.conflict("evento_id já utilizado com conteúdo diferente.");
        return new IngestResult(old,false);
    }
}
