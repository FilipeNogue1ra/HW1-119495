package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record DropoffRequest(
        @NotBlank(message = "Tracking code is mandatory")
        String trackingCode
) {
}
