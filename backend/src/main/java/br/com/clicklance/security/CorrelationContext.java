package br.com.clicklance.security;

import java.util.UUID;

/** Apenas a identidade gerada pelo servidor; nenhum dado da requisicao. */
public final class CorrelationContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();
    private CorrelationContext() {}
    public static UUID currentOrNew() { UUID value = CURRENT.get(); return value == null ? UUID.randomUUID() : value; }
    public static void set(UUID value) { CURRENT.set(value); }
    public static void clear() { CURRENT.remove(); }
}
