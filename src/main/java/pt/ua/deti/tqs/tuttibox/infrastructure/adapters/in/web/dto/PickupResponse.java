package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto;

import pt.ua.deti.tqs.tuttibox.core.model.ParcelState;

public record PickupResponse(
        String trackingCode,
        String stationCode,
        String compartmentNumber,
        ParcelState state,
        String message
) {
}
