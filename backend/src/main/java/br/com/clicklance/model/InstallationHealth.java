package br.com.clicklance.model;

import java.time.Instant;

public record InstallationHealth(String dispositivo_id, Instant observed_at, Instant received_at,
        String gateway_version, String capture_state, int pending_events,
        long oldest_pending_age_seconds, Long capture_progress_age_seconds) {}
