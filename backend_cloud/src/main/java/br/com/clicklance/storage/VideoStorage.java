package br.com.clicklance.storage;

import java.time.Instant;

/** Private objects. Callers must authorize the resource before asking for playback. */
public interface VideoStorage {
    record StoredVideo(long sizeBytes) {}

    StoredVideo verify(String key);
    String playbackUrl(String key, Instant notAfter);
    void delete(String key);
}
