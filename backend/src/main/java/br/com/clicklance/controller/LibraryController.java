package br.com.clicklance.controller;

import br.com.clicklance.model.ReplaySummary;
import br.com.clicklance.security.PlayerPrincipal;
import br.com.clicklance.service.LibraryService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jogadores/biblioteca")
public class LibraryController {
    private final LibraryService library;
    public LibraryController(LibraryService library) { this.library=library; }
    @GetMapping
    Map<String,Object> list(@AuthenticationPrincipal PlayerPrincipal player,@RequestParam(defaultValue="0") String page,@RequestParam(defaultValue="20") String size) {
        return library.list(player,PlayerReplayController.number(page,"page"),PlayerReplayController.number(size,"size"));
    }
    @PutMapping("/{evento_id}")
    ResponseEntity<ReplaySummary> save(@AuthenticationPrincipal PlayerPrincipal player,@PathVariable("evento_id") String event) {
        var saved=library.save(player,event); return ResponseEntity.status(saved.created()?201:200).body(saved.replay());
    }
    @DeleteMapping("/{evento_id}")
    ResponseEntity<Void> remove(@AuthenticationPrincipal PlayerPrincipal player,@PathVariable("evento_id") String event) {
        library.remove(player,event); return ResponseEntity.noContent().build();
    }
}
