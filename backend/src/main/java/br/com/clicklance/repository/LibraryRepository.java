package br.com.clicklance.repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LibraryRepository {
    private final JdbcTemplate jdbc;
    public LibraryRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public long count(UUID user) { return jdbc.queryForObject("SELECT count(*) FROM library_entry WHERE user_id=?",Long.class,user); }
    public void insert(UUID user,String event,Instant now) { jdbc.update("INSERT INTO library_entry(user_id,event_id,saved_at) VALUES (?,?,?)",user,event,now.atOffset(ZoneOffset.UTC)); }
    public void remove(UUID user,String event) { jdbc.update("DELETE FROM library_entry WHERE user_id=? AND event_id=?",user,event); }
    public List<String> lockAll(UUID user) {
        return jdbc.query("SELECT r.evento_id FROM replay r JOIN library_entry le ON le.event_id=r.evento_id WHERE le.user_id=? ORDER BY r.evento_id FOR UPDATE OF r",(rs,n)->rs.getString(1),user);
    }
    public void removeAll(UUID user) { jdbc.update("DELETE FROM library_entry WHERE user_id=?",user); }
}
