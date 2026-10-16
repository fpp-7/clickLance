package br.com.clicklance.repository;

import br.com.clicklance.model.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReplayRepository {
    private final JdbcTemplate jdbc;
    public ReplayRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<Replay> find(String id) {
        return jdbc.query("SELECT * FROM replay WHERE evento_id=?", (rs,row) -> new Replay(
                new ReplayInput(rs.getString("evento_id"),rs.getString("dispositivo_id"),rs.getString("quadra_id"),
                        rs.getObject("event_at",OffsetDateTime.class).toInstant(),rs.getString("bucket"),rs.getString("object_key"),rs.getString("sha256")),
                rs.getString("empresa_id"),rs.getObject("received_at",OffsetDateTime.class).toInstant(),rs.getInt("retention_days"),
                rs.getObject("expires_at",OffsetDateTime.class).toInstant(),rs.getBoolean("expired_on_arrival"),
                new VerifiedObject(rs.getString("object_version"),rs.getString("object_etag"),rs.getLong("object_size")),
                rs.getObject("purged_at",OffsetDateTime.class)==null ? null : rs.getObject("purged_at",OffsetDateTime.class).toInstant()),id).stream().findFirst();
    }
    /** READ COMMITTED: after losing ON CONFLICT, the next statement sees the committed winner. */
    public boolean insert(Replay replay) {
        var i=replay.input(); var o=replay.object();
        return jdbc.update("""
                INSERT INTO replay(evento_id,dispositivo_id,quadra_id,empresa_id,event_at,received_at,retention_days,
                    expires_at,expired_on_arrival,bucket,object_key,sha256,object_version,object_etag,object_size)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """,i.eventId(),i.deviceId(),i.courtId(),replay.companyId(),time(i.eventAt()),time(replay.receivedAt()),
                replay.retentionDays(),time(replay.expiresAt()),replay.expiredOnArrival(),i.bucket(),i.key(),i.sha256(),o.version(),o.etag(),o.size())==1;
    }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    public int retentionDays(String company) { return jdbc.queryForObject("SELECT retencao_dias FROM empresa WHERE id=?",Integer.class,company); }
}
