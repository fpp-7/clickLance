package br.com.clicklance.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Spring MVC handles byte ranges for Resource responses, including 206 and 416. */
@RestController
@ConditionalOnProperty(prefix = "clicklance.storage", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalMediaController {
    private final LocalVideoStorage storage;

    public LocalMediaController(LocalVideoStorage storage) { this.storage = storage; }

    @GetMapping("/api/v1/media/{token}")
    public ResponseEntity<Resource> media(@PathVariable String token) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(storage.resource(token));
        } catch (StorageException e) {
            HttpStatus status = e.reason() == StorageException.Reason.NOT_FOUND
                    ? HttpStatus.NOT_FOUND : HttpStatus.FORBIDDEN;
            return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).build();
        }
    }
}
