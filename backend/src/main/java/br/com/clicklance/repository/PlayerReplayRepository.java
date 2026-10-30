package br.com.clicklance.repository;

import br.com.clicklance.model.PlayerReplay;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerReplayRepository {
    private final JdbcTemplate jdbc;
    public PlayerReplayRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String JOIN = " FROM replay r JOIN quadra q ON q.id=r.quadra_id JOIN empresa e ON e.id=r.empresa_id JOIN dispositivo d ON d.id=r.dispositivo_id LEFT JOIN replay_lifecycle lc ON lc.event_id=r.evento_id ";
    private static final String PUBLIC = "r.expires_at>? AND r.purged_at IS NULL AND lc.blocked_at IS NULL AND lc.purge_reserved_at IS NULL AND e.active AND q.active AND d.active";
    private static final String COLUMNS = "r.*, (e.active AND q.active AND d.active) AS active_origin, lc.blocked_at, lc.purge_reserved_at, EXISTS(SELECT 1 FROM library_entry le WHERE le.event_id=r.evento_id AND le.user_id=?) AS saved";
    public List<Map<String,Object>> arenas(int size,long offset) {
        return jdbc.query("SELECT id,nome FROM empresa WHERE active ORDER BY nome,id LIMIT ? OFFSET ?", (rs,n)->Map.of("empresa_id",rs.getString("id"),"nome",rs.getString("nome")),size,offset);
    }
    public long arenaCount() { return jdbc.queryForObject("SELECT count(*) FROM empresa WHERE active",Long.class); }
    public boolean activeCompany(String id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM empresa WHERE id=? AND active)",Boolean.class,id)); }
    public List<Map<String,Object>> courts(String company,int size,long offset) {
        return jdbc.query("SELECT id,nome,timezone FROM quadra WHERE empresa_id=? AND active ORDER BY nome,id LIMIT ? OFFSET ?",(rs,n)->Map.of("quadra_id",rs.getString("id"),"nome",rs.getString("nome"),"timezone",rs.getString("timezone")),company,size,offset);
    }
    public long courtCount(String company) { return jdbc.queryForObject("SELECT count(*) FROM quadra WHERE empresa_id=? AND active",Long.class,company); }
    public Optional<String> courtZone(String court) {
        return jdbc.query("SELECT q.timezone FROM quadra q JOIN empresa e ON e.id=q.empresa_id WHERE q.id=? AND q.active AND e.active",(rs,n)->rs.getString(1),court).stream().findFirst();
    }
    public List<LocalDate> dates(String court,String zone,Instant now,int size,long offset) {
        return jdbc.query("SELECT DISTINCT (r.event_at AT TIME ZONE ?)::date AS day"+JOIN+" WHERE r.quadra_id=? AND "+PUBLIC+" ORDER BY day DESC LIMIT ? OFFSET ?",(rs,n)->rs.getObject("day",LocalDate.class),zone,court,utc(now),size,offset);
    }
    public List<PlayerReplay> byCourt(UUID user,String court,Instant start,Instant end,Instant now,int size,long offset) {
        return jdbc.query("SELECT "+COLUMNS+JOIN+" WHERE r.quadra_id=? AND r.event_at>=? AND r.event_at<? AND "+PUBLIC+" ORDER BY r.event_at,r.evento_id LIMIT ? OFFSET ?",PlayerReplayRepository::map,user,court,utc(start),utc(end),utc(now),size,offset);
    }
    public long courtReplayCount(String court,Instant start,Instant end,Instant now) {
        return jdbc.queryForObject("SELECT count(*)"+JOIN+" WHERE r.quadra_id=? AND r.event_at>=? AND r.event_at<? AND "+PUBLIC,Long.class,court,utc(start),utc(end),utc(now));
    }
    public Optional<PlayerReplay> find(UUID user,String id,boolean lock) {
        // A separate statement after waiting for the row lock observes committed lifecycle changes.
        if (lock) jdbc.query("SELECT evento_id FROM replay WHERE evento_id=? FOR UPDATE",(rs,n)->rs.getString(1),id);
        return jdbc.query("SELECT "+COLUMNS+JOIN+" WHERE r.evento_id=?",PlayerReplayRepository::map,user,id).stream().findFirst();
    }
    public List<PlayerReplay> library(UUID user,int size,long offset) {
        return jdbc.query("SELECT "+COLUMNS+JOIN+" JOIN library_entry own ON own.event_id=r.evento_id AND own.user_id=? WHERE r.purged_at IS NULL AND lc.blocked_at IS NULL AND lc.purge_reserved_at IS NULL ORDER BY own.saved_at,own.event_id LIMIT ? OFFSET ?",PlayerReplayRepository::map,user,user,size,offset);
    }
    public long libraryVisibleCount(UUID user) {
        return jdbc.queryForObject("SELECT count(*)"+JOIN+" JOIN library_entry own ON own.event_id=r.evento_id AND own.user_id=? WHERE r.purged_at IS NULL AND lc.blocked_at IS NULL AND lc.purge_reserved_at IS NULL",Long.class,user);
    }
    private static PlayerReplay map(ResultSet rs,int n) throws SQLException {
        return new PlayerReplay(rs.getString("evento_id"),rs.getString("empresa_id"),rs.getString("quadra_id"),
                rs.getTimestamp("event_at").toInstant(),rs.getTimestamp("expires_at").toInstant(),rs.getString("bucket"),rs.getString("object_key"),rs.getString("object_version"),
                rs.getBoolean("active_origin"),rs.getTimestamp("blocked_at")!=null,rs.getTimestamp("purge_reserved_at")!=null,rs.getTimestamp("purged_at")!=null,rs.getBoolean("saved"));
    }
    private static Object utc(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
}
