package br.com.clicklance.repository;

import br.com.clicklance.model.PurgeJob;
import br.com.clicklance.model.ReconciliationOrigin;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LifecycleRepository {
    private final JdbcTemplate jdbc;
    public LifecycleRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Optional<String> lockReplay(String id) {
        return jdbc.query("SELECT empresa_id FROM replay WHERE evento_id=? FOR UPDATE",(rs,row)->rs.getString(1),id).stream().findFirst();
    }
    public Optional<String> companyOf(String id) {
        return jdbc.query("SELECT empresa_id FROM replay WHERE evento_id=?",(rs,row)->rs.getString(1),id).stream().findFirst();
    }
    public void block(String id,UUID actor,boolean blocked,String reason,Instant now) {
        jdbc.update("""
            INSERT INTO replay_lifecycle(event_id,blocked_at,blocked_by,block_reason) VALUES (?,?,?,?)
            ON CONFLICT(event_id) DO UPDATE SET blocked_at=EXCLUDED.blocked_at,
                blocked_by=EXCLUDED.blocked_by,block_reason=EXCLUDED.block_reason
            """,id,blocked?time(now):null,blocked?actor:null,blocked?reason:null);
    }
    public List<String> reserve(String company,Instant now,int limit) {
        var ids=jdbc.query("""
            SELECT r.evento_id FROM replay r WHERE r.empresa_id=? AND r.expires_at<=? AND r.purged_at IS NULL
                AND NOT EXISTS(SELECT 1 FROM library_entry b WHERE b.event_id=r.evento_id)
                AND NOT EXISTS(SELECT 1 FROM purge_job j WHERE j.event_id=r.evento_id)
            ORDER BY r.evento_id LIMIT ? FOR UPDATE OF r SKIP LOCKED
            """,(rs,row)->rs.getString(1),company,time(now),limit);
        var reserved=new ArrayList<String>();
        for(String id:ids) {
            // Reavaliar apos o lock de replay, compartilhado com salvar/remover biblioteca.
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM library_entry WHERE event_id=?)",Boolean.class,id))) continue;
            jdbc.update("INSERT INTO replay_lifecycle(event_id,purge_reserved_at) VALUES (?,?) ON CONFLICT(event_id) DO UPDATE SET purge_reserved_at=COALESCE(replay_lifecycle.purge_reserved_at,EXCLUDED.purge_reserved_at)",id,time(now));
            jdbc.update("INSERT INTO purge_job(event_id,state,reserved_at,next_attempt_at) VALUES (?,'PENDING',?,?)",id,time(now),time(now));
            reserved.add(id);
        }
        return reserved;
    }
    public Optional<PurgeJob> claim(String company,Instant now) {
        var jobs=jdbc.query("""
            SELECT r.*,j.attempts FROM purge_job j JOIN replay r ON r.evento_id=j.event_id
            WHERE r.empresa_id=? AND ((j.state='PENDING' AND j.next_attempt_at<=?)
                OR (j.state='RUNNING' AND j.lease_until<=?))
            ORDER BY j.next_attempt_at,j.event_id LIMIT 1 FOR UPDATE OF j SKIP LOCKED
            """,(rs,row)->new PurgeJob(rs.getString("evento_id"),rs.getString("empresa_id"),rs.getString("dispositivo_id"),
                rs.getString("bucket"),rs.getString("object_key"),rs.getString("object_version"),rs.getString("object_etag"),UUID.randomUUID(),rs.getInt("attempts")+1),company,time(now),time(now));
        if(jobs.isEmpty()) return Optional.empty();
        var job=jobs.getFirst();
        jdbc.update("UPDATE purge_job SET state='RUNNING',lease_token=?,lease_until=?,attempts=attempts+1 WHERE event_id=?",job.leaseToken(),time(now.plusSeconds(60)),job.eventId());
        return Optional.of(job);
    }
    public boolean complete(PurgeJob job,Instant now) {
        if(jdbc.update("UPDATE purge_job SET state='DONE',completed_at=?,lease_token=NULL,lease_until=NULL,last_error=NULL WHERE event_id=? AND state='RUNNING' AND lease_token=? AND lease_until>?",time(now),job.eventId(),job.leaseToken(),time(now))!=1) return false;
        jdbc.update("UPDATE replay SET purged_at=? WHERE evento_id=? AND purged_at IS NULL",time(now),job.eventId());
        return true;
    }
    public boolean retry(PurgeJob job,String code,Instant now) {
        long delay=Math.min(3600,5L << Math.min(job.attempts(),9));
        return jdbc.update("UPDATE purge_job SET state='PENDING',lease_token=NULL,lease_until=NULL,next_attempt_at=?,last_error=? WHERE event_id=? AND state='RUNNING' AND lease_token=?",time(now.plusSeconds(delay)),code,job.eventId(),job.leaseToken())==1;
    }
    public List<ReconciliationOrigin> origins(String company) {
        return jdbc.query("SELECT d.id,d.bucket FROM dispositivo d JOIN quadra q ON q.id=d.quadra_id WHERE q.empresa_id=? ORDER BY d.id",(rs,row)->new ReconciliationOrigin(rs.getString(1),rs.getString(2)),company);
    }
    public List<String> registeredKeys(String company,String bucket,String device) {
        return jdbc.query("SELECT object_key FROM replay WHERE empresa_id=? AND bucket=? AND dispositivo_id=?",(rs,row)->rs.getString(1),company,bucket,device);
    }
    public List<String> unpurgedKeys(String company,String bucket,String device) {
        return jdbc.query("SELECT object_key FROM replay WHERE empresa_id=? AND bucket=? AND dispositivo_id=? AND purged_at IS NULL",(rs,row)->rs.getString(1),company,bucket,device);
    }
    public UUID issue(String company,String bucket,String key,String kind,Instant now) {
        return jdbc.queryForObject("""
            INSERT INTO reconciliation_issue(id,empresa_id,bucket,object_key,kind,first_seen_at,last_seen_at) VALUES (?,?,?,?,?,?,?)
            ON CONFLICT(empresa_id,bucket,object_key,kind) DO UPDATE SET last_seen_at=EXCLUDED.last_seen_at RETURNING id
            """,UUID.class,UUID.randomUUID(),company,bucket,key,kind,time(now),time(now));
    }
    public List<Map<String,Object>> issues(String company,int limit) {
        return jdbc.query("SELECT id,kind,first_seen_at,last_seen_at FROM reconciliation_issue WHERE empresa_id=? ORDER BY last_seen_at DESC,id LIMIT ?",(rs,row)->Map.of("id",rs.getObject(1,UUID.class),"kind",rs.getString(2),"first_seen_at",rs.getObject(3,OffsetDateTime.class),"last_seen_at",rs.getObject(4,OffsetDateTime.class)),company,limit);
    }
    private static OffsetDateTime time(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
}
