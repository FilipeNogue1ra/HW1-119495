package pt.ua.deti.tqs.tuttibox.core.ports.out;

import pt.ua.deti.tqs.tuttibox.core.model.GeoCoordinates;

import java.util.Optional;

/**
 * Outbound Port for resolving textual street addresses into GPS coordinates.
 * Decouples the core domain logic from external third-party geocoding providers.
 */
public interface GeocodingPort {

    /**
     * Resolves a free-text address or landmark into geographic GPS coordinates.
     *
     * @param address textual street address or place name (e.g., "Largo da Estacao, Aveiro")
     * @return Optional containing GeoCoordinates if resolved, or Optional.empty() if not found
     */
    Optional<GeoCoordinates> geocodeAddress(String address);
}
