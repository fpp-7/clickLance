package br.com.clicklance.model;

import java.util.Map;

public record GatewayConfig(String version, boolean active, Map<String, Object> settings) {
    public GatewayConfig { settings = Map.copyOf(settings); }
    @Override public String toString() { return "GatewayConfig[version=" + version + ", active=" + active + ", settings=REDACTED]"; }
}
