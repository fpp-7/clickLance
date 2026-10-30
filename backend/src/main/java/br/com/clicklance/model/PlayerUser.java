package br.com.clicklance.model;

import java.util.UUID;

/** Registro interno; nunca serializado pela API. */
public record PlayerUser(UUID id, String email, String passwordHash) {
    @Override public String toString() { return "PlayerUser[id=" + id + "]"; }
}
