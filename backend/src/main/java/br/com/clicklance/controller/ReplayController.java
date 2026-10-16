package br.com.clicklance.controller;

import br.com.clicklance.model.ReplayAck;
import br.com.clicklance.service.ReplayService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReplayController {
    private final ReplayService replays;
    public ReplayController(ReplayService replays) { this.replays=replays; }
    @PostMapping("/api/v1/replays")
    ResponseEntity<ReplayAck> register(@RequestHeader(value="X-Device-Token",required=false) String token,
                                      @RequestHeader(value="Idempotency-Key",required=false) String key,
                                      @RequestBody Map<String,Object> body) {
        var result=replays.register(token,key,body);
        return ResponseEntity.status(result.created()?201:200).header("Cache-Control","no-store").body(result.ack());
    }
}
