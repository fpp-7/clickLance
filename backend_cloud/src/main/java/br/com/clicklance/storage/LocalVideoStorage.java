package br.com.clicklance.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

public final class LocalVideoStorage implements VideoStorage {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final StorageProperties properties;
    private final Clock clock;
    private final Path root;
    private final byte[] secret;

    public LocalVideoStorage(StorageProperties properties, Clock clock) {
        properties.validate();
        this.properties = properties;
        this.clock = clock;
        this.secret = properties.getSigningSecret().getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(properties.getLocalDirectory());
            this.root = properties.getLocalDirectory().toRealPath();
        } catch (IOException e) {
            throw new StorageException(StorageException.Reason.UNAVAILABLE, "Diretório de vídeos indisponível", e);
        }
    }

    @Override
    public StoredVideo verify(String key) {
        Path file = existingPath(key);
        try {
            long size = Files.size(file);
            if (size <= 0 || size > properties.getMaxBytes()) {
                throw new StorageException(StorageException.Reason.INVALID_VIDEO, "Tamanho do vídeo fora do limite");
            }
            return new StoredVideo(size);
        } catch (IOException e) {
            throw new StorageException(StorageException.Reason.UNAVAILABLE, "Não foi possível verificar o vídeo", e);
        }
    }

    @Override
    public String playbackUrl(String key, Instant notAfter) {
        verify(key);
        Instant expiry = StorageKeys.expiry(clock, properties.getPlaybackTtlSeconds(), notAfter);
        String payload = ENCODER.encodeToString((key + "\n" + expiry.getEpochSecond()).getBytes(StandardCharsets.UTF_8));
        return "/api/v1/media/" + payload + "." + ENCODER.encodeToString(sign(payload));
    }

    Resource resource(String token) {
        return new FileSystemResource(existingPath(keyFromToken(token)));
    }

    String keyFromToken(String token) {
        try {
            if (token == null || token.length() > 1024) throw invalidToken();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), DECODER.decode(parts[1]))) {
                throw invalidToken();
            }
            String[] payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8).split("\n", -1);
            if (payload.length != 2) throw invalidToken();
            String key = StorageKeys.requireSafe(payload[0]);
            Instant expiry = Instant.ofEpochSecond(Long.parseLong(payload[1]));
            if (!expiry.isAfter(clock.instant())) throw invalidToken();
            return key;
        } catch (IllegalArgumentException e) {
            throw invalidToken();
        }
    }

    @Override
    public void delete(String key) {
        StorageKeys.requireSafe(key);
        Path candidate = root.resolve(key).normalize();
        if (!Files.exists(candidate)) return;
        try {
            Files.delete(existingPath(key));
        } catch (IOException e) {
            throw new StorageException(StorageException.Reason.UNAVAILABLE, "Não foi possível excluir o vídeo", e);
        }
    }

    private Path existingPath(String key) {
        StorageKeys.requireSafe(key);
        try {
            Path requested = root.resolve(key).normalize();
            Path component = root;
            for (Path part : root.relativize(requested)) {
                component = component.resolve(part);
                if (Files.isSymbolicLink(component)) {
                    throw new StorageException(StorageException.Reason.INVALID_KEY,
                            "Links simbólicos não são permitidos no diretório de vídeos");
                }
            }
            Path candidate = requested.toRealPath();
            if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
                throw new StorageException(StorageException.Reason.INVALID_KEY, "Caminho do vídeo fora do diretório permitido");
            }
            return candidate;
        } catch (java.nio.file.NoSuchFileException e) {
            throw new StorageException(StorageException.Reason.NOT_FOUND, "Vídeo não encontrado");
        } catch (IOException e) {
            throw new StorageException(StorageException.Reason.UNAVAILABLE, "Vídeo indisponível", e);
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível", e);
        }
    }

    private StorageException invalidToken() {
        return new StorageException(StorageException.Reason.EXPIRED, "Link de reprodução inválido ou expirado");
    }
}
