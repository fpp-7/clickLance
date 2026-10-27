package br.com.clicklance.library;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meus-lances")
public class LibraryController {
    public record Save(@NotNull UUID lance_id) { }
    private final LibraryService service;
    public LibraryController(LibraryService service) { this.service=service; }
    @PostMapping
    ResponseEntity<?> save(@Valid @RequestBody Save body,Authentication auth) {
        var saved=service.save((UUID)auth.getPrincipal(),body.lance_id());
        return ResponseEntity.status(saved.created()?201:200).cacheControl(CacheControl.noStore()).body(saved.link());
    }
    @GetMapping
    ResponseEntity<?> list(Authentication auth,@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int pagina,
        @RequestParam(defaultValue="25") @Min(1) @Max(100) int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list((UUID)auth.getPrincipal(),pagina,tamanho));
    }
}
