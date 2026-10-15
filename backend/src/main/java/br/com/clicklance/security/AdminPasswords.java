package br.com.clicklance.security;

import br.com.clicklance.validation.Input;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class AdminPasswords {
    private AdminPasswords() {}
    public static String email(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            Input.invalid("email", "INVALID_FORMAT");
        }
        return normalized;
    }
    public static void validate(String password) {
        int length = password.getBytes(StandardCharsets.UTF_8).length;
        if (length < 12 || length > 72) Input.invalid("password", "OUT_OF_RANGE");
    }
}
