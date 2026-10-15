package br.com.clicklance.validation;

import br.com.clicklance.exception.ApiException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validacao estrita: impede coercao de booleanos, strings e floats para inteiros. */
public final class Input {
    private Input() {}

    public static void fields(Map<String, Object> body, Set<String> allowed) {
        if (body == null) throw new ApiException(400, "INVALID_REQUEST");
        if (!allowed.containsAll(body.keySet())) invalid("body", "UNKNOWN_FIELD");
    }

    public static String string(Map<String, Object> body, String name, int maxLength) {
        Object value = body.get(name);
        if (!(value instanceof String text) || text.isBlank()
                || text.codePointCount(0, text.length()) > maxLength) invalid(name, "INVALID_FORMAT");
        return (String) value;
    }

    public static boolean bool(Map<String, Object> body, String name) {
        if (!(body.get(name) instanceof Boolean)) invalid(name, "INVALID_FORMAT");
        return (Boolean) body.get(name);
    }

    public static int integer(Object value, String name, int min, int max) {
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < min
                || ((Number) value).longValue() > max) invalid(name, "OUT_OF_RANGE");
        return ((Number) value).intValue();
    }

    public static void invalid(String field, String code) {
        throw new ApiException(422, "VALIDATION_FAILED", List.of(Map.of("field", field, "code", code)));
    }

    public static double number(Object value, String name, double min, double max) {
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())
                || ((Number) value).doubleValue() < min || ((Number) value).doubleValue() > max) {
            invalid(name, "OUT_OF_RANGE");
        }
        return ((Number) value).doubleValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> body, String name) {
        if (!(body.get(name) instanceof Map<?, ?> map) || !map.keySet().stream().allMatch(String.class::isInstance)) {
            invalid(name, "INVALID_FORMAT");
        }
        return (Map<String, Object>) body.get(name);
    }
}
