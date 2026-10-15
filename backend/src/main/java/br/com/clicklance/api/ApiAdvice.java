package br.com.clicklance.api;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.transaction.TransactionException;

@RestControllerAdvice
public class ApiAdvice {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException error) {
        var builder = ResponseEntity.status(error.status()).header("Cache-Control", "no-store");
        if (error.status() == 401) builder.header("WWW-Authenticate", "Bearer");
        if (error.status() == 503) builder.header("Retry-After", "5");
        return builder.body(ApiErrors.body(error));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> malformed(Exception ignored) {
        return api(new ApiException(400, "INVALID_REQUEST"));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Map<String, Object>> database(Exception ignored) {
        return api(new ApiException(503, "DEPENDENCY_UNAVAILABLE"));
    }
}
