package br.com.clicklance.identity;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap-admin")
public class BootstrapAdmin implements CommandLineRunner {
    private final AdminAccounts accounts;
    private final Environment env;
    public BootstrapAdmin(AdminAccounts accounts, Environment env) { this.accounts = accounts; this.env = env; }
    @Override public void run(String... args) {
        String email = env.getProperty("BOOTSTRAP_ADMIN_EMAIL", "");
        String password = env.getProperty("BOOTSTRAP_ADMIN_PASSWORD", "");
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Bootstrap exige BOOTSTRAP_ADMIN_EMAIL e BOOTSTRAP_ADMIN_PASSWORD; nenhuma identidade foi criada.");
        }
        accounts.bootstrap(email, password);
    }
}
