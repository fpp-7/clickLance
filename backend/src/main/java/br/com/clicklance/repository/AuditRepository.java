package br.com.clicklance.repository;

import br.com.clicklance.model.AuditEntry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {
    private final JdbcTemplate jdbc;
    public AuditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(AuditEntry entry) {
        jdbc.update("""
                INSERT INTO administrative_audit(id,correlation_id,occurred_at,actor_id,actor_type,action,
                  resource_type,resource_id,empresa_id,outcome,code,http_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, entry.audit_id(), entry.correlation_id(), Timestamp.from(entry.occurred_at()), entry.actor_id(),
                entry.actor_type(), entry.action(), entry.resource_type(), entry.resource_id(), entry.empresa_id(),
                entry.outcome(), entry.code(), entry.http_status());
    }

    public List<AuditEntry> findByCompany(String companyId, int size, long offset) {
        return jdbc.query("SELECT * FROM administrative_audit WHERE empresa_id=? ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
                AuditRepository::map, companyId, size, offset);
    }

    public long countByCompany(String companyId) {
        return jdbc.queryForObject("SELECT count(*) FROM administrative_audit WHERE empresa_id=?", Long.class, companyId);
    }

    public List<AuditEntry> findByCorrelation(UUID correlationId) {
        return jdbc.query("SELECT * FROM administrative_audit WHERE correlation_id=? ORDER BY occurred_at,id",
                AuditRepository::map, correlationId);
    }

    private static AuditEntry map(ResultSet rs, int row) throws SQLException {
        return new AuditEntry(rs.getObject("id", UUID.class), rs.getObject("correlation_id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_id", UUID.class), rs.getString("actor_type"), rs.getString("action"), rs.getString("resource_type"),
                rs.getString("resource_id"), rs.getString("empresa_id"), rs.getString("outcome"), rs.getString("code"), rs.getObject("http_status", Integer.class));
    }
}
