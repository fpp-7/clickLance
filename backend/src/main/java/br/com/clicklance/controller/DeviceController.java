package br.com.clicklance.controller;

import br.com.clicklance.model.Device;
import br.com.clicklance.model.DeviceIssued;
import br.com.clicklance.security.AdminPrincipal;
import br.com.clicklance.service.DeviceService;
import br.com.clicklance.validation.Input;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class DeviceController {
    private final DeviceService devices;
    public DeviceController(DeviceService devices) { this.devices = devices; }

    @PostMapping("/empresas/{empresa_id}/quadras/{quadra_id}/dispositivos")
    ResponseEntity<DeviceIssued> create(@AuthenticationPrincipal AdminPrincipal principal,
                                       @PathVariable("empresa_id") String companyId, @PathVariable("quadra_id") String courtId,
                                       @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(devices.create(principal, companyId, courtId, body));
    }

    @GetMapping("/empresas/{empresa_id}/quadras/{quadra_id}/dispositivos")
    Map<String, Object> list(@AuthenticationPrincipal AdminPrincipal principal,
                            @PathVariable("empresa_id") String companyId, @PathVariable("quadra_id") String courtId,
                            @RequestParam(defaultValue = "0") String page, @RequestParam(defaultValue = "20") String size) {
        return devices.list(principal, companyId, courtId, parameter(page, "page", 0, Integer.MAX_VALUE), parameter(size, "size", 1, 100));
    }

    @GetMapping("/dispositivos/{dispositivo_id}")
    Device find(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable("dispositivo_id") String id) {
        return devices.find(principal, id);
    }

    private static int parameter(String value, String name, int min, int max) {
        try { return Input.integer(Long.parseLong(value), name, min, max); }
        catch (NumberFormatException ignored) { Input.invalid(name, "OUT_OF_RANGE"); return 0; }
    }
}
