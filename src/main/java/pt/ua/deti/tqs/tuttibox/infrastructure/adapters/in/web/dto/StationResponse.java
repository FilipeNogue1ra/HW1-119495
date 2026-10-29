package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto;

public record StationResponse(
        String stationCode,
        String name,
        String address,
        String city,
        double latitude,
        double longitude,
        Double distanceKm,
        int totalCompartments,
        int availableCompartments
) {
}
