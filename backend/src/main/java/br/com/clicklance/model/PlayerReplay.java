package br.com.clicklance.model;

import java.time.Instant;

/** Private storage reference is internal; HTTP exposes only summary(). */
public record PlayerReplay(String eventId, String companyId, String courtId, Instant eventAt,
                           Instant expiresAt, String bucket, String objectKey, String objectVersion,
                           boolean activeOrigin, boolean blocked, boolean purgeReserved, boolean purged,
                           boolean saved) {
    public ReplaySummary summary() { return new ReplaySummary(eventId, companyId, courtId, eventAt, expiresAt, saved); }
}
