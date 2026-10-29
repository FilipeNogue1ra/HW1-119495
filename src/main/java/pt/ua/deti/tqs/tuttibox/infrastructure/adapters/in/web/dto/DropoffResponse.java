package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto;

import pt.ua.deti.tqs.tuttibox.core.model.ParcelState;

import java.time.Instant;

public record DropoffResponse(
        String trackingCode,
        String stationCode,
        String compartmentNumber,
        ParcelState state,
        Instant pickupDeadline,
        String message
) {
}
