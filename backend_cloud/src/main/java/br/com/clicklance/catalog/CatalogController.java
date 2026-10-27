package br.com.clicklance.catalog;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service=service; }
    @GetMapping("/empresas/{id}/quadras")
    Object quadras(@PathVariable UUID id) { return service.quadras(id); }
    @GetMapping("/quadras/{id}/lances")
    ResponseEntity<?> list(@PathVariable UUID id,@RequestParam LocalDate data,
            @RequestParam(required=false) LocalTime hora_inicio,@RequestParam(required=false) LocalTime hora_fim,
            @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int pagina,
            @RequestParam(defaultValue="25") @Min(1) @Max(100) int tamanho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(id,data,hora_inicio,hora_fim,pagina,tamanho));
    }
    @GetMapping("/lances/{id}")
    ResponseEntity<?> detail(@PathVariable UUID id,Authentication auth) {
        UUID user=auth!=null && auth.getPrincipal() instanceof UUID u?u:null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.detail(id,user));
    }
}
