package br.com.clicklance.exception;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletResponse;

public final class ApiErrors {
    private ApiErrors() {}

    public static Map<String, Object> body(ApiException error) {
        var body = new LinkedHashMap<String, Object>();
        body.put("code", error.code());
        body.put("message", message(error.code()));
        body.put("correlation_id", UUID.randomUUID().toString());
        if (!error.violations().isEmpty()) body.put("violations", error.violations());
        return body;
    }

    private static String message(String code) {
        return switch (code) {
            case "UNAUTHENTICATED" -> "Autenticacao necessaria ou invalida.";
            case "FORBIDDEN" -> "Operacao nao autorizada.";
            case "RESOURCE_NOT_FOUND" -> "Recurso nao encontrado.";
            case "VALIDATION_FAILED" -> "Campos fora do dominio permitido.";
            case "INVALID_REQUEST" -> "Requisicao invalida.";
            case "IDENTITY_CONFLICT" -> "Identidade em conflito.";
            default -> "Dependencia temporariamente indisponivel.";
        };
    }

    /** Apenas constantes internas e UUID gerado; nunca serializa exception/header/payload. */
    public static void write(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        if (status == 503) response.setHeader("Retry-After", "5");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message(code)
                + "\",\"correlation_id\":\"" + UUID.randomUUID() + "\"}");
    }
}
