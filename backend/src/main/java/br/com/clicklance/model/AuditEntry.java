package br.com.clicklance.model;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(UUID audit_id, UUID correlation_id, Instant occurred_at, UUID actor_id, String actor_type,
                         String action, String resource_type, String resource_id, String empresa_id,
                         String outcome, String code, Integer http_status) {}
