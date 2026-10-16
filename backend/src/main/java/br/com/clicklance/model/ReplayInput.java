package br.com.clicklance.model;

import java.time.Instant;

public record ReplayInput(String eventId, String deviceId, String courtId, Instant eventAt,
                          String bucket, String key, String sha256) {}
