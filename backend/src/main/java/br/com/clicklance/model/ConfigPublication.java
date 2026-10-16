package br.com.clicklance.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConfigPublication(String dispositivo_id, String version, Map<String, Object> settings,
                                UUID published_by, Instant published_at) {
    public ConfigPublication { settings = Map.copyOf(settings); }
    @Override public String toString() { return "ConfigPublication[version=" + version + ", settings=REDACTED]"; }
}
