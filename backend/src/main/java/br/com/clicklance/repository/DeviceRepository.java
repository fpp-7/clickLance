package br.com.clicklance.repository;

import br.com.clicklance.model.Device;
import br.com.clicklance.model.DeviceCredentials;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceRepository {
    private static final String COLUMNS = "d.id,d.quadra_id,q.empresa_id,d.active,d.bucket";
    private static final String ORIGIN = " FROM dispositivo d JOIN quadra q ON q.id=d.quadra_id";
    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean insert(Device device, String tokenHash) {
        return jdbc.update("INSERT INTO dispositivo(id,quadra_id,active,bucket,token_hash) VALUES (?,?,?,?,?) ON CONFLICT(id) DO NOTHING",
                device.dispositivo_id(), device.quadra_id(), device.active(), device.bucket(), tokenHash) == 1;
    }

    public Optional<Device> findForAdmin(String id, UUID userId, boolean platformAdmin) {
        return jdbc.query("SELECT " + COLUMNS + ORIGIN + """
                 WHERE d.id=? AND (? OR EXISTS (SELECT 1 FROM admin_company_membership m
                 WHERE m.empresa_id=q.empresa_id AND m.user_id=? AND m.active AND m.role='COMPANY_ADMIN'))
                """, DeviceRepository::map, id, platformAdmin, userId).stream().findFirst();
    }

    public List<Device> findByCourt(String courtId, int size, long offset) {
        return jdbc.query("SELECT " + COLUMNS + ORIGIN + " WHERE d.quadra_id=? ORDER BY d.id LIMIT ? OFFSET ?",
                DeviceRepository::map, courtId, size, offset);
    }

    public long countByCourt(String courtId) {
        return jdbc.queryForObject("SELECT count(*) FROM dispositivo WHERE quadra_id=?", Long.class, courtId);
    }

    public Optional<DeviceCredentials> findCredentials(String id) {
        return jdbc.query("SELECT " + COLUMNS + ",d.token_hash,q.active AS court_active,e.active AS company_active"
                + ORIGIN + " JOIN empresa e ON e.id=q.empresa_id WHERE d.id=?",
                (rs, row) -> new DeviceCredentials(map(rs, row), rs.getString("token_hash"),
                        rs.getBoolean("court_active"), rs.getBoolean("company_active")), id).stream().findFirst();
    }

    public Optional<String> findCompanyId(String id) {
        return jdbc.query("SELECT q.empresa_id" + ORIGIN + " WHERE d.id=?", (rs, row) -> rs.getString("empresa_id"), id)
                .stream().findFirst();
    }

    private static Device map(ResultSet rs, int row) throws SQLException {
        return new Device(rs.getString("id"), rs.getString("quadra_id"), rs.getString("empresa_id"),
                rs.getBoolean("active"), rs.getString("bucket"));
    }
}
