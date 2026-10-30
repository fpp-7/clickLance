package br.com.clicklance.repository;

import br.com.clicklance.model.Company;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CompanyRepository {
    private static final String MEMBER_SCOPE = " WHERE EXISTS (SELECT 1 FROM admin_company_membership m WHERE m.empresa_id=e.id AND m.user_id=? AND m.active AND m.role='COMPANY_ADMIN')";
    private final JdbcTemplate jdbc;

    public CompanyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(Company company) {
        jdbc.update("INSERT INTO empresa(id,nome,active,retencao_dias) VALUES (?,?,?,?)",
                company.empresa_id(), company.nome(), company.active(), company.retencao_dias());
    }

    public Optional<Company> findById(String id) {
        return jdbc.query("SELECT id,nome,active,retencao_dias FROM empresa WHERE id=?", CompanyRepository::map, id)
                .stream().findFirst();
    }

    public boolean existsById(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM empresa WHERE id=?)", Boolean.class, id));
    }

    public void setActive(String id, boolean active) {
        jdbc.update("UPDATE empresa SET active=? WHERE id=?", active, id);
    }

    public List<Company> findAll(int size, long offset) {
        return jdbc.query("SELECT id,nome,active,retencao_dias FROM empresa ORDER BY id LIMIT ? OFFSET ?",
                CompanyRepository::map, size, offset);
    }

    public long countAll() { return jdbc.queryForObject("SELECT count(*) FROM empresa", Long.class); }

    public List<Company> findByActiveMembership(UUID userId, int size, long offset) {
        return jdbc.query("SELECT id,nome,active,retencao_dias FROM empresa e" + MEMBER_SCOPE + " ORDER BY id LIMIT ? OFFSET ?",
                CompanyRepository::map, userId, size, offset);
    }

    public long countByActiveMembership(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM empresa e" + MEMBER_SCOPE, Long.class, userId);
    }

    private static Company map(ResultSet rs, int row) throws SQLException {
        return new Company(rs.getString("id"), rs.getString("nome"), rs.getBoolean("active"), rs.getInt("retencao_dias"));
    }
}
