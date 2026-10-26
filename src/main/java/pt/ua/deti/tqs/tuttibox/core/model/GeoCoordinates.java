package pt.ua.deti.tqs.tuttibox.core.model;

/**
 * Value Object representing geographic GPS coordinates and a resolved location name.
 */
public record GeoCoordinates(double latitude, double longitude, String displayName) {
}
