package br.com.clicklance.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminMembershipRepository {
    private final JdbcTemplate jdbc;
    public AdminMembershipRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<String> findActiveCompanyIds(UUID userId) {
        return jdbc.queryForList("SELECT empresa_id FROM admin_company_membership WHERE user_id=? AND active AND role='COMPANY_ADMIN' ORDER BY empresa_id",
                String.class, userId);
    }

    public void insertIfAbsent(UUID userId, String companyId) {
        jdbc.update("INSERT INTO admin_company_membership(user_id,empresa_id) VALUES (?,?) ON CONFLICT DO NOTHING", userId, companyId);
    }
}
