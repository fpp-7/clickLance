package br.com.clicklance.security;

import java.util.UUID;

public record PlayerPrincipal(UUID userId, String email, String sessionHash) {
    @Override public String toString() { return "PlayerPrincipal[userId=" + userId + "]"; }
}
