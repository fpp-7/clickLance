package br.com.clicklance.controller;

import br.com.clicklance.model.*;
import br.com.clicklance.security.PlayerPrincipal;
import br.com.clicklance.service.PlayerReplayService;
import br.com.clicklance.validation.Input;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jogadores")
public class PlayerReplayController {
    private final PlayerReplayService replays;
    public PlayerReplayController(PlayerReplayService replays) { this.replays=replays; }
    @GetMapping("/arenas")
    Map<String,Object> arenas(@AuthenticationPrincipal PlayerPrincipal player,@RequestParam(defaultValue="0") String page,@RequestParam(defaultValue="20") String size) {
        return replays.arenas(player,number(page,"page"),number(size,"size"));
    }
    @GetMapping("/arenas/{empresa_id}/quadras")
    Map<String,Object> courts(@AuthenticationPrincipal PlayerPrincipal player,@PathVariable("empresa_id") String company,@RequestParam(defaultValue="0") String page,@RequestParam(defaultValue="20") String size) {
        return replays.courts(player,company,number(page,"page"),number(size,"size"));
    }
    @GetMapping("/quadras/{quadra_id}/disponibilidade")
    Map<String,Object> availability(@AuthenticationPrincipal PlayerPrincipal player,@PathVariable("quadra_id") String court,@RequestParam(required=false) String data,@RequestParam(defaultValue="0") String page,@RequestParam(defaultValue="20") String size) {
        return replays.availability(player,court,data,number(page,"page"),number(size,"size"));
    }
    @GetMapping("/quadras/{quadra_id}/lances")
    Map<String,Object> list(@AuthenticationPrincipal PlayerPrincipal player,@PathVariable("quadra_id") String court,@RequestParam(required=false) String data,
            @RequestParam(name="hora_inicio",required=false) String start,@RequestParam(name="hora_fim",required=false) String end,@RequestParam(defaultValue="0") String page,@RequestParam(defaultValue="20") String size) {
        return replays.list(player,court,data,start==null?null:number(start,"hora_inicio"),end==null?null:number(end,"hora_fim"),number(page,"page"),number(size,"size"));
    }
    @GetMapping("/lances/{evento_id}")
    ReplaySummary detail(@AuthenticationPrincipal PlayerPrincipal player,@PathVariable("evento_id") String event) { return replays.detail(player,event); }
    @PostMapping("/lances/{evento_id}/acesso")
    ResponseEntity<MediaAccess> access(@AuthenticationPrincipal PlayerPrincipal player,@PathVariable("evento_id") String event,@RequestBody Map<String,Object> body) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(replays.access(player,event,body));
    }
    static int number(String text,String field) {
        try { return Input.integer(Long.parseLong(text),field,0,Integer.MAX_VALUE); }
        catch(NumberFormatException ignored) { Input.invalid(field,"OUT_OF_RANGE"); return 0; }
    }
}
