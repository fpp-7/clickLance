package br.com.clicklance.validation;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/** Limites do cliente remote_config v1. Nunca copia nomes arbitrarios para erros. */
public final class ConfigSettings {
    private ConfigSettings() {}
    private static final Map<String, double[]> RANGES = Map.ofEntries(
            Map.entry("REPLAY_BEFORE_SECONDS", new double[]{1,600}), Map.entry("REPLAY_AFTER_SECONDS", new double[]{0,60}),
            Map.entry("SEGMENT_DURATION_SECONDS", new double[]{1,120}), Map.entry("KEYFRAME_INTERVAL", new double[]{1,600}),
            Map.entry("CAMERA_TIMEOUT_SECONDS", new double[]{1,60}), Map.entry("CAMERA_LATENCY_SECONDS", new double[]{0,60}),
            Map.entry("MAX_PENDING_EVENTS", new double[]{1,1000}), Map.entry("MIN_FREE_BYTES", new double[]{1048576,1099511627776d}),
            Map.entry("BUFFER_QUOTA_BYTES", new double[]{1048576,10995116277760d}), Map.entry("CLIPS_QUOTA_BYTES", new double[]{1048576,10995116277760d}));
    private static final Set<String> INTEGERS = Set.of("SEGMENT_DURATION_SECONDS", "KEYFRAME_INTERVAL", "MAX_PENDING_EVENTS");
    public static void validate(Map<String, Object> settings) {
        for (var entry : settings.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (RANGES.containsKey(key)) {
                var range = RANGES.get(key);
                Input.number(value, "settings." + key, range[0], range[1]);
                if (INTEGERS.contains(key)) Input.integer(value, "settings." + key, (int)range[0], (int)range[1]);
            } else switch (key) {
                case "CAMERA_SOURCE" -> choice(value, key, Set.of("rtsp", "dshow"));
                case "CAMERA_RTSP_TRANSPORT" -> choice(value, key, Set.of("tcp", "udp"));
                case "CAMERA_RTSP_URL" -> {
                    if (!(value instanceof String text) || text.length() > 2048 || !rtsp(text)) Input.invalid("settings.CAMERA_RTSP_URL", "INVALID_FORMAT");
                }
                case "CAMERA_DEVICE_NAME" -> {
                    if (!(value instanceof String text) || text.isEmpty() || text.codePointCount(0, text.length()) > 128) Input.invalid("settings.CAMERA_DEVICE_NAME", "INVALID_FORMAT");
                }
                default -> Input.invalid("settings", "UNKNOWN_FIELD");
            }
        }
    }
    private static void choice(Object value, String key, Set<String> allowed) {
        if (!(value instanceof String text) || !allowed.contains(text)) Input.invalid("settings." + key, "INVALID_FORMAT");
    }
    public static boolean rtsp(Object value) {
        if (!(value instanceof String text)) return false;
        try {
            var uri = URI.create(text);
            // getHost also rejects malformed credentials/authority without echoing the URL.
            return Set.of("rtsp", "rtsps").contains(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException ignored) { return false; }
    }
}
