package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PickupRequest(
        String trackingCode,

        @NotBlank(message = "PIN code is mandatory")
        @Pattern(regexp = "^\\d{6}$", message = "PIN must be exactly 6 numeric digits")
        String pin
) {
}
