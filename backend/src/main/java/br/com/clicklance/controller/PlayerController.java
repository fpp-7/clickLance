package br.com.clicklance.controller;

import br.com.clicklance.security.PlayerPrincipal;
import br.com.clicklance.service.PlayerAccountService;
import br.com.clicklance.service.PlayerSessionService;
import br.com.clicklance.validation.Input;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jogadores")
public class PlayerController {
    private final PlayerAccountService accounts;
    private final PlayerSessionService sessions;
    public PlayerController(PlayerAccountService accounts, PlayerSessionService sessions) {
        this.accounts = accounts; this.sessions = sessions;
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        Input.fields(body, Set.of("email", "password"));
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(accounts.register(
                Input.string(body, "email", 254), Input.string(body, "password", 72)));
    }

    @PostMapping("/sessoes")
    ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        Input.fields(body, Set.of("email", "password"));
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(sessions.login(
                Input.string(body, "email", 254), Input.string(body, "password", 72)));
    }

    @GetMapping("/sessoes/atual")
    ResponseEntity<Map<String, Object>> current(@AuthenticationPrincipal PlayerPrincipal principal) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(
                Map.of("usuario_id", principal.userId().toString(), "email", principal.email()));
    }

    @DeleteMapping("/sessoes/atual")
    ResponseEntity<Void> logout(@AuthenticationPrincipal PlayerPrincipal principal) {
        sessions.logout(principal);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @PostMapping("/recuperacao")
    ResponseEntity<Map<String, Object>> requestReset(@RequestBody Map<String, Object> body) {
        Input.fields(body, Set.of("email"));
        accounts.requestReset(Input.string(body, "email", 254));
        return ResponseEntity.accepted().header("Cache-Control", "no-store").body(
                Map.of("message", "Se a conta estiver elegivel, enviaremos as instrucoes de recuperacao."));
    }

    @PostMapping("/redefinicao")
    ResponseEntity<Void> reset(@RequestBody Map<String, Object> body) {
        Input.fields(body, Set.of("token", "password"));
        accounts.resetPassword(Input.string(body, "token", 43), Input.string(body, "password", 72));
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    @DeleteMapping("/conta")
    ResponseEntity<Void> close(@AuthenticationPrincipal PlayerPrincipal principal) {
        accounts.close(principal);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
}
