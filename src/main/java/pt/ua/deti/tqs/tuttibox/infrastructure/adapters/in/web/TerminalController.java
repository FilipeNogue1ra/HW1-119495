package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.ua.deti.tqs.tuttibox.core.service.TerminalService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.DropoffRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.DropoffResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.PickupRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.PickupResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.TerminalStatusResponse;

@RestController
@RequestMapping("/api/v1/terminals")
public class TerminalController {

    private final TerminalService terminalService;

    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    @PostMapping("/{stationCode}/dropoff")
    public ResponseEntity<DropoffResponse> courierDropoff(
            @PathVariable String stationCode,
            @Valid @RequestBody DropoffRequest request
    ) {
        DropoffResponse response = terminalService.dropoffParcel(stationCode, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{stationCode}/pickup")
    public ResponseEntity<PickupResponse> recipientPickup(
            @PathVariable String stationCode,
            @Valid @RequestBody PickupRequest request
    ) {
        PickupResponse response = terminalService.pickupParcel(stationCode, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{stationCode}/status")
    public ResponseEntity<TerminalStatusResponse> getStatus(@PathVariable String stationCode) {
        TerminalStatusResponse response = terminalService.getTerminalStatus(stationCode);
        return ResponseEntity.ok(response);
    }
}
