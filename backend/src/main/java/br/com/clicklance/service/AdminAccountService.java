package br.com.clicklance.service;

import br.com.clicklance.model.AuditAction;
import br.com.clicklance.repository.AdminMembershipRepository;
import br.com.clicklance.security.AdminPasswords;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.repository.AdminUserRepository;
import br.com.clicklance.exception.ApiException;
import br.com.clicklance.repository.CompanyRepository;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provisionamento operacional; nunca exposto como cadastro publico. */
@Service
public class AdminAccountService {
    private final AdminUserRepository users;
    private final AdminMembershipRepository memberships;
    private final CompanyRepository companies;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    public AdminAccountService(AdminUserRepository users, AdminMembershipRepository memberships,
                         CompanyRepository companies, PasswordEncoder passwords, AuditService audit) {
        this.users = users;
        this.memberships = memberships;
        this.companies = companies;
        this.passwords = passwords;
        this.audit = audit;
    }

    @Transactional
    public UUID bootstrap(String rawEmail, String password) {
        String email = AdminPasswords.email(rawEmail);
        AdminPasswords.validate(password);
        // Serializa inclusive chamadas concorrentes em processos distintos.
        users.lockProvisioning();
        var existing = users.findByEmailForUpdate(email);
        if (existing.isPresent()) {
            var user = existing.get();
            if (!user.platformAdmin() || !user.active()) throw new ApiException(409, "IDENTITY_CONFLICT");
            audit.success(AuditAction.ADMIN_BOOTSTRAP, null, null, user.id().toString());
            return user.id(); // Nao troca senha, reativa ou promove uma identidade existente.
        }
        UUID id = UUID.randomUUID();
        users.insertPlatformAdmin(id, email, passwords.encode(password));
        audit.success(AuditAction.ADMIN_BOOTSTRAP, null, null, id.toString());
        return id;
    }

    @Transactional
    public UUID provisionCompanyAdmin(AdminPrincipal operator, String rawEmail, String password, String empresaId) {
        operator.requirePlatform();
        String email = AdminPasswords.email(rawEmail);
        AdminPasswords.validate(password);
        if (!companies.existsById(empresaId)) {
            throw new ApiException(404, "RESOURCE_NOT_FOUND");
        }
        users.lockProvisioning();
        UUID candidate = UUID.randomUUID();
        users.insertCompanyAdminIfAbsent(candidate, email, passwords.encode(password));
        var user = users.findByEmailForUpdate(email).orElseThrow(() -> new ApiException(409, "IDENTITY_CONFLICT"));
        if (!user.active()) throw new ApiException(409, "IDENTITY_CONFLICT");
        // Repetir mantem senha e estado do vinculo; reativacao exige operacao propria.
        memberships.insertIfAbsent(user.id(), empresaId);
        audit.success(AuditAction.COMPANY_ADMIN_PROVISION, operator.userId(), empresaId, user.id().toString());
        return user.id();
    }

}
