package br.com.clicklance.replay;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/replays")
public class ReplayController {
    public record Body(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,50}") String dispositivo_id,@NotNull UUID evento_id,
        @NotBlank @Size(max=80) String timestamp,@NotBlank @Size(max=255) String s3_key) { }
    private final ReplayService service;
    public ReplayController(ReplayService service) { this.service=service; }
    @PostMapping
    ResponseEntity<?> ingest(@Valid @RequestBody Body body,@RequestHeader(value="X-Device-Token",required=false) String token) {
        var result=service.ingest(body.dispositivo_id(),token,body.evento_id(),body.timestamp(),body.s3_key());
        return ResponseEntity.status(result.created()?201:200).body(result.lance());
    }
}
