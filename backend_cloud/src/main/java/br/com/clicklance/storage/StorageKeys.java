package br.com.clicklance.storage;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

final class StorageKeys {
    private static final Pattern KEY = Pattern.compile("replays/[A-Za-z0-9_-]{1,80}/[A-Za-z0-9_-]{1,100}\\.mp4");

    private StorageKeys() {}

    static String requireSafe(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new StorageException(StorageException.Reason.INVALID_KEY, "Chave de vídeo inválida");
        }
        return key;
    }

    static Instant expiry(Clock clock, long ttl, Instant notAfter) {
        if (notAfter == null) throw new IllegalArgumentException("Prazo de reprodução obrigatório");
        Instant now = clock.instant();
        Instant ttlLimit = now.plusSeconds(ttl);
        Instant expiry = notAfter.isBefore(ttlLimit) ? notAfter : ttlLimit;
        // Whole seconds avoid a token outliving the public deadline after serialization.
        expiry = Instant.ofEpochSecond(expiry.getEpochSecond());
        if (!expiry.isAfter(now)) {
            throw new StorageException(StorageException.Reason.EXPIRED, "Prazo de reprodução expirado");
        }
        return expiry;
    }
}
