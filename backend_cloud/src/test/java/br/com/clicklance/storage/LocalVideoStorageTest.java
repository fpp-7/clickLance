package br.com.clicklance.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class LocalVideoStorageTest {
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final String KEY = "replays/quadra_001/33333333-3333-4333-8333-333333333333.mp4";
    @TempDir Path directory;

    private StorageProperties properties() {
        StorageProperties p = new StorageProperties();
        p.setLocalDirectory(directory);
        p.setSigningSecret("segredo-de-teste-exclusivo-32-caracteres");
        return p;
    }

    private LocalVideoStorage storage(Instant now) {
        return new LocalVideoStorage(properties(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private Path video() throws Exception {
        Path file = directory.resolve(KEY);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{0, 0, 0, 24, 'f', 't', 'y', 'p'});
        return file;
    }

    @Test void signatureDoesNotOutliveAuthorizationAndTamperingIsRejected() throws Exception {
        video();
        LocalVideoStorage storage = storage(NOW);
        String url = storage.playbackUrl(KEY, NOW.plusSeconds(25));
        String token = url.substring(url.lastIndexOf('/') + 1);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
        assertEquals(KEY + "\n" + NOW.plusSeconds(25).getEpochSecond(), payload);
        assertEquals(KEY, storage.keyFromToken(token));
        assertEquals(8, storage.resource(token).contentLength());
        String forged = "A" + token.substring(1);
        assertThrows(StorageException.class, () -> storage.keyFromToken(forged));
        assertThrows(StorageException.class, () -> storage(NOW.plusSeconds(25)).keyFromToken(token));
    }

    @Test void traversalUnknownEmptyAndOversizedFilesAreRejected() throws Exception {
        LocalVideoStorage storage = storage(NOW);
        assertEquals(StorageException.Reason.INVALID_KEY,
                assertThrows(StorageException.class, () -> storage.verify("../secret.mp4")).reason());
        assertEquals(StorageException.Reason.INVALID_KEY,
                assertThrows(StorageException.class, () -> storage.verify("replays/a/%2e%2e.mp4")).reason());
        assertEquals(StorageException.Reason.NOT_FOUND,
                assertThrows(StorageException.class, () -> storage.verify(KEY)).reason());
        Path file = video();
        Files.write(file, new byte[0]);
        assertEquals(StorageException.Reason.INVALID_VIDEO,
                assertThrows(StorageException.class, () -> storage.verify(KEY)).reason());
        Files.write(file, new byte[20]);
        StorageProperties properties = properties();
        properties.setMaxBytes(10);
        assertThrows(StorageException.class,
                () -> new LocalVideoStorage(properties, Clock.fixed(NOW, ZoneOffset.UTC)).verify(KEY));
    }

    @Test void deletionIsRepeatableAndDoesNotIssueLinksToDeletedObjects() throws Exception {
        Path file = video();
        LocalVideoStorage storage = storage(NOW);
        assertEquals(8, storage.verify(KEY).sizeBytes());
        storage.delete(KEY);
        storage.delete(KEY);
        assertFalse(Files.exists(file));
        assertThrows(StorageException.class, () -> storage.playbackUrl(KEY, NOW.plusSeconds(300)));
    }

    @Test void linksRequireAFutureDeadlineAndAConfiguredSecret() throws Exception {
        video();
        assertEquals(StorageException.Reason.EXPIRED,
                assertThrows(StorageException.class, () -> storage(NOW).playbackUrl(KEY, NOW)).reason());
        StorageProperties p = properties();
        p.setSigningSecret("");
        assertThrows(IllegalArgumentException.class, () -> new LocalVideoStorage(p, Clock.systemUTC()));
    }
}
