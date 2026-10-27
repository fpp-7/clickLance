package br.com.clicklance.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocalMediaControllerTest {
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final String KEY = "replays/quadra_001/evento_001.mp4";
    @TempDir Path directory;

    @Test void mvcServesRangesAndRejectsForgedOrExpiredCapabilities() throws Exception {
        Path file = directory.resolve(KEY);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
        StorageProperties p = new StorageProperties();
        p.setLocalDirectory(directory);
        p.setSigningSecret("segredo-para-teste-de-media-com-32-caracteres");
        LocalVideoStorage storage = new LocalVideoStorage(p, Clock.fixed(NOW, ZoneOffset.UTC));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LocalMediaController(storage)).build();
        String url = storage.playbackUrl(KEY, NOW.plusSeconds(30));
        mvc.perform(get(url).header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-3/8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(new byte[]{0, 1, 2, 3}));
        mvc.perform(get("/api/v1/media/forged.signature")).andExpect(status().isForbidden());
        MockMvc expiredMvc = MockMvcBuilders.standaloneSetup(new LocalMediaController(
                new LocalVideoStorage(p, Clock.fixed(NOW.plusSeconds(30), ZoneOffset.UTC)))).build();
        expiredMvc.perform(get(url)).andExpect(status().isForbidden());
        Files.delete(file);
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }
}
