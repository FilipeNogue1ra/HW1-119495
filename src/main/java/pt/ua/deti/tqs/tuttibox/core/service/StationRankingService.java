package pt.ua.deti.tqs.tuttibox.core.service;

import org.springframework.stereotype.Service;
import pt.ua.deti.tqs.tuttibox.core.model.GeoCoordinates;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.ports.out.GeocodingPort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class StationRankingService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private final GeocodingPort geocodingPort;

    public record StationDistance(LockerStation station, double distanceKm) {
    }

    public StationRankingService(GeocodingPort geocodingPort) {
        this.geocodingPort = geocodingPort;
    }

    /**
     * Calculates the great-circle distance between two GPS coordinates using the Haversine formula.
     * Strictly implemented in domain code and verified with unit tests (Guidelines P46).
     *
     * @param lat1 latitude of point 1 in degrees
     * @param lon1 longitude of point 1 in degrees
     * @param lat2 latitude of point 2 in degrees
     * @param lon2 longitude of point 2 in degrees
     * @return distance in kilometers rounded to two decimal places
     */
    public double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = EARTH_RADIUS_KM * c;
        return Math.round(distance * 100.0) / 100.0;
    }

    /**
     * Resolves user address coordinates via the GeocodingPort and ranks locker stations by proximity.
     *
     * @param address user-specified address string
     * @param stations list of candidate locker stations
     * @return list of StationDistance sorted in ascending order (nearest station first)
     */
    public List<StationDistance> rankStationsByAddress(String address, List<LockerStation> stations) {
        Optional<GeoCoordinates> coords = geocodingPort.geocodeAddress(address);
        if (coords.isEmpty()) {
            throw new DomainRuleException("GEOCODING_NOT_FOUND", "Could not resolve geographic location for: " + address);
        }

        double userLat = coords.get().latitude();
        double userLon = coords.get().longitude();

        List<StationDistance> rankedList = new ArrayList<>();
        for (LockerStation station : stations) {
            double dist = calculateDistanceKm(userLat, userLon, station.getLatitude(), station.getLongitude());
            rankedList.add(new StationDistance(station, dist));
        }

        rankedList.sort(Comparator.comparingDouble(StationDistance::distanceKm));
        return rankedList;
    }

    /**
     * Ranks locker stations by proximity given direct GPS coordinates.
     *
     * @param userLat user latitude in degrees
     * @param userLon user longitude in degrees
     * @param stations list of candidate locker stations
     * @return list of StationDistance sorted in ascending order (nearest station first)
     */
    public List<StationDistance> rankStationsByCoordinates(double userLat, double userLon, List<LockerStation> stations) {
        List<StationDistance> rankedList = new ArrayList<>();
        for (LockerStation station : stations) {
            double dist = calculateDistanceKm(userLat, userLon, station.getLatitude(), station.getLongitude());
            rankedList.add(new StationDistance(station, dist));
        }
        rankedList.sort(Comparator.comparingDouble(StationDistance::distanceKm));
        return rankedList;
    }
}
