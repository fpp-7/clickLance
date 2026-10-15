package br.com.clicklance.exception;

import java.util.List;
import java.util.Map;

public final class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<Map<String, String>> violations;

    public ApiException(int status, String code) {
        this(status, code, List.of());
    }

    public ApiException(int status, String code, List<Map<String, String>> violations) {
        super(code);
        this.status = status;
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    public int status() { return status; }
    public String code() { return code; }
    public List<Map<String, String>> violations() { return violations; }
}
