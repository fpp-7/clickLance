package br.com.clicklance.model;

import java.util.UUID;

/** Registro interno de persistencia; nunca retornado por controllers. */
public record AdminUser(UUID id, String email, String passwordHash, boolean active, boolean platformAdmin) {
    @Override public String toString() { return "AdminUser[id=" + id + "]"; }
}
