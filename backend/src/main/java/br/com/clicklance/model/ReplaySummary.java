package br.com.clicklance.model;

import java.time.Instant;

public record ReplaySummary(String evento_id, String empresa_id, String quadra_id,
                            Instant timestamp, Instant expira_em, boolean salvo) {}
