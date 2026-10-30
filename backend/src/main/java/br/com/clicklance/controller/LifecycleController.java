package br.com.clicklance.controller;

import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.service.LifecycleService;
import br.com.clicklance.service.PurgeWorkerService;
import br.com.clicklance.validation.Input;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class LifecycleController {
    private final LifecycleService lifecycle;
    private final PurgeWorkerService worker;
    public LifecycleController(LifecycleService lifecycle,PurgeWorkerService worker) { this.lifecycle=lifecycle; this.worker=worker; }
    @PatchMapping("/lances/{evento_id}/bloqueio")
    Map<String,Object> block(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable("evento_id") String event,@RequestBody Map<String,Object> body) { return lifecycle.block(actor,event,body); }
    @PostMapping("/empresas/{empresa_id}/purga/reservas")
    Map<String,Object> reserve(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable("empresa_id") String company,@RequestBody Map<String,Object> body) { return Map.of("reserved",lifecycle.reserve(actor,company,limit(body))); }
    @PostMapping("/empresas/{empresa_id}/purga/executar")
    Map<String,Object> run(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable("empresa_id") String company,@RequestBody Map<String,Object> body) { return worker.run(actor,company,limit(body)); }
    @PostMapping("/empresas/{empresa_id}/reconciliacao")
    Map<String,Object> reconcile(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable("empresa_id") String company,@RequestBody Map<String,Object> body) { Input.fields(body,Set.of()); return worker.reconcile(actor,company); }
    @GetMapping("/empresas/{empresa_id}/reconciliacao")
    List<Map<String,Object>> issues(@AuthenticationPrincipal AdminPrincipal actor,@PathVariable("empresa_id") String company) { return lifecycle.issues(actor,company); }
    private static int limit(Map<String,Object> body) { Input.fields(body,Set.of("limit")); return Input.integer(body.get("limit"),"limit",1,100); }
}
