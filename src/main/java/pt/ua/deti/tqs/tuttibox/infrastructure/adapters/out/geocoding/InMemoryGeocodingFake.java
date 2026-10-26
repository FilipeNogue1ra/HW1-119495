package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.geocoding;

import org.springframework.stereotype.Component;
import pt.ua.deti.tqs.tuttibox.core.model.GeoCoordinates;
import pt.ua.deti.tqs.tuttibox.core.ports.out.GeocodingPort;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Fake implementation of GeocodingPort (Second project Fake).
 * Complies with Guidelines P40-P46:
 * 1. Guarantees default test builds run 100% offline without hitting public APIs.
 * 2. Allows simulating network timeouts and unavailable provider responses.
 */
@Component
public class InMemoryGeocodingFake implements GeocodingPort {

    private final Map<String, GeoCoordinates> addressDatabase = new ConcurrentHashMap<>();
    private boolean simulateUnavailable = false;

    public InMemoryGeocodingFake() {
        seedDefaultAveiroLocations();
    }

    private static String normalizeText(String input) {
        if (input == null) return "";
        String n = Normalizer.normalize(input, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private void seedDefaultAveiroLocations() {
        addressDatabase.put("estacao", new GeoCoordinates(40.6433, -8.6406, "Largo da Estação, Aveiro"));
        addressDatabase.put("universidade", new GeoCoordinates(40.6306, -8.6575, "Campus Universitário de Santiago, Aveiro"));
        addressDatabase.put("santiago", new GeoCoordinates(40.6306, -8.6575, "Campus Universitário de Santiago, Aveiro"));
        addressDatabase.put("forum", new GeoCoordinates(40.6412, -8.6515, "Fórum Aveiro, Centro"));
        addressDatabase.put("glicinias", new GeoCoordinates(40.6272, -8.6455, "Glicínias Plaza, Aveiro"));
        addressDatabase.put("hospital", new GeoCoordinates(40.6358, -8.6472, "Hospital Infante D. Pedro, Aveiro"));
        addressDatabase.put("fonte nova", new GeoCoordinates(40.6402, -8.6441, "Cais da Fonte Nova, Aveiro"));
        addressDatabase.put("mario sacramento", new GeoCoordinates(40.6380, -8.6480, "Rua Dr. Mário Sacramento, Aveiro"));
    }

    @Override
    public Optional<GeoCoordinates> geocodeAddress(String address) {
        if (simulateUnavailable) {
            return Optional.empty();
        }
        if (address == null || address.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalizedQuery = normalizeText(address);
        for (Map.Entry<String, GeoCoordinates> entry : addressDatabase.entrySet()) {
            if (normalizedQuery.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    // =========================================================
    // Test & Failure Injection Controls
    // =========================================================

    public void addCustomAddress(String queryToken, GeoCoordinates coords) {
        addressDatabase.put(normalizeText(queryToken), coords);
    }

    public void setSimulateUnavailable(boolean simulateUnavailable) {
        this.simulateUnavailable = simulateUnavailable;
    }

    public void reset() {
        this.simulateUnavailable = false;
        addressDatabase.clear();
        seedDefaultAveiroLocations();
    }
}
