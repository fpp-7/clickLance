package br.com.clicklance.identity;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("provision-company-admin")
public class ProvisionCompanyAdmin implements CommandLineRunner {
    private final AdminAccounts accounts;
    private final AdminSessions sessions;
    private final Environment env;
    public ProvisionCompanyAdmin(AdminAccounts accounts, AdminSessions sessions, Environment env) {
        this.accounts = accounts; this.sessions = sessions; this.env = env;
    }
    @Override public void run(String... args) {
        String token = env.getProperty("PROVISION_ADMIN_TOKEN", "");
        String email = env.getProperty("PROVISION_ADMIN_EMAIL", "");
        String password = env.getProperty("PROVISION_ADMIN_PASSWORD", "");
        String company = env.getProperty("PROVISION_EMPRESA_ID", "");
        if (token.isBlank() || email.isBlank() || password.isBlank() || company.isBlank()) {
            throw new IllegalStateException("Provisionamento exige PROVISION_ADMIN_TOKEN/EMAIL/PASSWORD e PROVISION_EMPRESA_ID.");
        }
        accounts.provisionCompanyAdmin(sessions.authenticate(token), email, password, company);
    }
}
