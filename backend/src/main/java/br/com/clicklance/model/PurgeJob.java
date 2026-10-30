package br.com.clicklance.model;

import java.util.UUID;

public record PurgeJob(String eventId, String companyId, String deviceId, String bucket, String key,
                       String version, String etag, UUID leaseToken, int attempts) {}
