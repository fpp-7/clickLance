package br.com.clicklance.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service=service; }
    public record EmpresaBody(@NotBlank @Size(max=100) String nome,@Min(1) @Max(30) Integer dias_retencao_padrao) { }
    public record QuadraBody(@NotNull UUID empresa_id,@NotBlank @Size(max=100) String nome,@Min(1) @Max(60) Integer duracao_segmento_segundos,@Min(1) @Max(300) Integer segmentos_concatenar,@Size(max=80) String fuso_horario) { }
    public record DeviceBody(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,50}") String id,@NotNull UUID quadra_id,@Size(max=150) String descricao) { }
    public record ConfigBody(@Min(1) @Max(60) Integer duracao_segmento_segundos,@Min(1) @Max(300) Integer segmentos_concatenar,Boolean ativo) { }
    public record ActiveBody(@NotNull Boolean ativo) { }
    @PostMapping("/empresas") @ResponseStatus(HttpStatus.CREATED)
    Object empresa(@Valid @RequestBody EmpresaBody b) { return service.createEmpresa(b.nome(),b.dias_retencao_padrao()); }
    @PostMapping("/quadras") @ResponseStatus(HttpStatus.CREATED)
    Object quadra(@Valid @RequestBody QuadraBody b) { return service.createQuadra(b.empresa_id(),b.nome(),b.duracao_segmento_segundos(),b.segmentos_concatenar(),b.fuso_horario()); }
    @PostMapping("/dispositivos") @ResponseStatus(HttpStatus.CREATED)
    Object device(@Valid @RequestBody DeviceBody b) { return service.createDevice(b.id(),b.quadra_id(),b.descricao()); }
    @GetMapping("/dispositivos/{id}/config")
    Object config(@PathVariable String id,@RequestHeader(value="X-Device-Token",required=false) String token) { return service.deviceConfig(id,token); }
    @PatchMapping("/quadras/{id}")
    Object configure(@PathVariable UUID id,@Valid @RequestBody ConfigBody b) { return service.configureQuadra(id,b.duracao_segmento_segundos(),b.segmentos_concatenar(),b.ativo()); }
    @PatchMapping("/dispositivos/{id}")
    Object deviceActive(@PathVariable String id,@Valid @RequestBody ActiveBody b) { return service.setDeviceActive(id,b.ativo()); }
    @PatchMapping("/empresas/{id}")
    Object empresaActive(@PathVariable UUID id,@Valid @RequestBody ActiveBody b) { return service.setEmpresaActive(id,b.ativo()); }
}
