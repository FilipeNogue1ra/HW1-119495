package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pt.ua.deti.tqs.tuttibox.core.service.ParcelService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.ParcelDetailResponse;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/parcels")
@Tag(name = "Parcels", description = "Endpoints for partner e-commerce stores to register and track shipments")
public class ParcelController {

    private final ParcelService parcelService;

    public ParcelController(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    @PostMapping
    @Operation(summary = "Register a new parcel and reserve a compatible locker compartment")
    @ApiResponse(responseCode = "201", description = "Parcel registered and locker compartment reserved")
    @ApiResponse(responseCode = "400", description = "Validation failed for request body")
    @ApiResponse(responseCode = "404", description = "Target station not found")
    @ApiResponse(responseCode = "409", description = "No compatible lockers available at target station")
    public ResponseEntity<CreateParcelResponse> createParcel(@Valid @RequestBody CreateParcelRequest request) {
        CreateParcelResponse response = parcelService.createParcel(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{trackingCode}")
                .buildAndExpand(response.getTrackingCode())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{trackingCode}")
    @Operation(summary = "Get parcel status, assigned compartment, and full audit trail")
    @ApiResponse(responseCode = "200", description = "Parcel details retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Parcel tracking code not found")
    public ResponseEntity<ParcelDetailResponse> getParcel(@PathVariable String trackingCode) {
        ParcelDetailResponse response = parcelService.getParcelByTrackingCode(trackingCode);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{trackingCode}/cancel")
    @Operation(summary = "Partner cancels shipment before courier deposit, releasing the reserved compartment")
    @ApiResponse(responseCode = "200", description = "Shipment cancelled and locker compartment released")
    @ApiResponse(responseCode = "404", description = "Parcel not found")
    @ApiResponse(responseCode = "409", description = "Parcel cannot be cancelled (already deposited or collected)")
    public ResponseEntity<ParcelDetailResponse> cancelParcel(@PathVariable String trackingCode) {
        ParcelDetailResponse response = parcelService.cancelParcel(trackingCode);
        return ResponseEntity.ok(response);
    }
}
