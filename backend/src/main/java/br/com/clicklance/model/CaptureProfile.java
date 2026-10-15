package br.com.clicklance.model;

import br.com.clicklance.validation.Input;

import java.util.Map;
import java.util.Set;

public record CaptureProfile(double REPLAY_BEFORE_SECONDS, double REPLAY_AFTER_SECONDS,
                             int SEGMENT_DURATION_SECONDS, int KEYFRAME_INTERVAL) {
    public static CaptureProfile parse(Map<String, Object> values) {
        Input.fields(values, Set.of("REPLAY_BEFORE_SECONDS", "REPLAY_AFTER_SECONDS", "SEGMENT_DURATION_SECONDS", "KEYFRAME_INTERVAL"));
        return new CaptureProfile(
                Input.number(values.get("REPLAY_BEFORE_SECONDS"), "perfil_captura.REPLAY_BEFORE_SECONDS", 1, 600),
                Input.number(values.get("REPLAY_AFTER_SECONDS"), "perfil_captura.REPLAY_AFTER_SECONDS", 0, 60),
                Input.integer(values.get("SEGMENT_DURATION_SECONDS"), "perfil_captura.SEGMENT_DURATION_SECONDS", 1, 120),
                Input.integer(values.get("KEYFRAME_INTERVAL"), "perfil_captura.KEYFRAME_INTERVAL", 1, 600));
    }
}
