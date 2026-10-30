package br.com.clicklance.exception;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.transaction.TransactionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiAdvice {
    private final ApiErrorResponder errors;
    public ApiAdvice(ApiErrorResponder errors) { this.errors = errors; }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException error, HttpServletRequest request) {
        error = errors.record(request, error);
        var builder = ResponseEntity.status(error.status()).header("Cache-Control", "no-store");
        if (error.status() == 401 && request.getServletPath().startsWith("/api/v1/admin/")) builder.header("WWW-Authenticate", "Bearer");
        if (error.status() == 503 || error.status() == 429) builder.header("Retry-After", error.status() == 429 ? "60" : "5");
        var body = ApiErrors.body(error);
        return builder.header("X-Correlation-ID", body.get("correlation_id").toString()).body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, Object>> malformed(Exception ignored, HttpServletRequest request) {
        return api(new ApiException(400, "INVALID_REQUEST"), request);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Map<String, Object>> database(Exception ignored, HttpServletRequest request) {
        return api(new ApiException(503, "DEPENDENCY_UNAVAILABLE"), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> method(Exception ignored, HttpServletRequest request) { return api(new ApiException(405, "INVALID_REQUEST"), request); }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> media(Exception ignored, HttpServletRequest request) { return api(new ApiException(415, "INVALID_REQUEST"), request); }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> missing(Exception ignored, HttpServletRequest request) { return api(new ApiException(404, "RESOURCE_NOT_FOUND"), request); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ignored, HttpServletRequest request) { return api(new ApiException(500, "INTERNAL_ERROR"), request); }
}
