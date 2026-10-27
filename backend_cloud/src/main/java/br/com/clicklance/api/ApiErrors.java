package br.com.clicklance.api;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import br.com.clicklance.storage.StorageException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log=LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException ex) { return ResponseEntity.status(ex.status).body(Map.of("codigo",ex.code,"erro",ex.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<?> validation(Exception ex) { return api(ApiException.invalid("Campos inválidos. Confira formatos e limites do contrato.")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception ex) { return api(ApiException.conflict("Registro duplicado ou referência inválida.")); }
    @ExceptionHandler(StorageException.class)
    ResponseEntity<?> storage(StorageException ex) {
        int status=switch(ex.reason()) {
            case INVALID_KEY, INVALID_VIDEO -> 400;
            case NOT_FOUND, EXPIRED -> 404;
            case UNAVAILABLE, UNSAFE_CONFIGURATION -> 503;
        };
        return ResponseEntity.status(status).body(Map.of("codigo","STORAGE_"+ex.reason().name(),"erro",status==503?"Vídeo temporariamente indisponível.":ex.getMessage()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ex) {
        log.error("Falha ao atender requisição",ex);
        return ResponseEntity.internalServerError().body(Map.of("codigo","ERRO_INTERNO","erro","Não foi possível concluir a operação."));
    }
}
