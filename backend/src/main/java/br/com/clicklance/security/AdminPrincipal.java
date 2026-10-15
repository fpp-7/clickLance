package br.com.clicklance.security;

import br.com.clicklance.exception.ApiException;

import java.util.Set;
import java.util.UUID;

public record AdminPrincipal(UUID userId, String email, boolean platformAdmin, Set<String> empresaIds, String sessionHash) {
    public AdminPrincipal { empresaIds = Set.copyOf(empresaIds); }
    public void requirePlatform() {
        if (!platformAdmin) throw new ApiException(403, "FORBIDDEN");
    }
    public void requireCompany(String id) {
        if (!platformAdmin && !empresaIds.contains(id)) throw new ApiException(403, "FORBIDDEN");
    }
}
