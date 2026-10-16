package br.com.clicklance.model;

import java.time.Instant;

public record Replay(ReplayInput input, String companyId, Instant receivedAt, int retentionDays,
                     Instant expiresAt, boolean expiredOnArrival, VerifiedObject object, Instant purgedAt) {}
