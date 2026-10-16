package br.com.clicklance.validation;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.model.ReplayInput;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

public final class ReplayPayload {
    private ReplayPayload() {}
    public static ReplayInput parse(Map<String,Object> body, String idempotencyKey) {
        Input.fields(body, Set.of("contrato_versao","evento_id","dispositivo_id","quadra_id","timestamp","objeto"));
        Input.integer(body.get("contrato_versao"), "contrato_versao", 1, 1);
        String id=Input.string(body,"evento_id",32);
        if (!id.matches("[a-f0-9]{32}")) Input.invalid("evento_id", "INVALID_FORMAT");
        if (!id.equals(idempotencyKey)) throw new ApiException(400,"INVALID_REQUEST");
        String device=Input.string(body,"dispositivo_id",32), court=Input.string(body,"quadra_id",64);
        if (!device.matches("[A-Za-z0-9_-]{1,32}")) Input.invalid("dispositivo_id","INVALID_FORMAT");
        if (!court.matches("[A-Za-z0-9_-]{1,64}")) Input.invalid("quadra_id","INVALID_FORMAT");
        String timestamp=Input.string(body,"timestamp",64);
        java.time.Instant instant;
        try {
            if (!timestamp.matches(".*(Z|[+-][0-9]{2}:[0-9]{2})$")) throw new DateTimeParseException("offset", "",0);
            instant=OffsetDateTime.parse(timestamp).toInstant();
            // PostgreSQL conserva microssegundos; nao aceitar truncamento silencioso da identidade.
            if (instant.getNano()%1000!=0) throw new DateTimeParseException("precision", "",0);
            if (instant.atOffset(java.time.ZoneOffset.UTC).getYear()<1 || instant.atOffset(java.time.ZoneOffset.UTC).getYear()>9998) throw new DateTimeParseException("range", "",0);
        } catch (DateTimeParseException ignored) { Input.invalid("timestamp","INVALID_FORMAT"); return null; }
        var object=Input.object(body,"objeto");
        Input.fields(object,Set.of("bucket","key","sha256"));
        String bucket=Input.string(object,"bucket",63), key=Input.string(object,"key",80), hash=Input.string(object,"sha256",64);
        if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) Input.invalid("objeto.bucket","INVALID_FORMAT");
        if (!hash.matches("[a-f0-9]{64}")) Input.invalid("objeto.sha256","INVALID_FORMAT");
        return new ReplayInput(id,device,court,instant,bucket,key,hash);
    }
}
