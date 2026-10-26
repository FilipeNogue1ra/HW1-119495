package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pt.ua.deti.tqs.tuttibox.core.model.GeoCoordinates;
import pt.ua.deti.tqs.tuttibox.core.ports.out.GeocodingPort;

import java.util.Optional;

/**
 * Production Geocoding Adapter calling OpenStreetMap Nominatim REST API.
 * Adheres to OSM Acceptable Use Policy:
 * 1. Uses explicit identifying User-Agent.
 * 2. Caches responses in local memory via @Cacheable("geocodingCache").
 * 3. Handles network failures gracefully.
 */
@Component
@Profile("!test")
@Primary
public class NominatimGeocodingAdapter implements GeocodingPort {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingAdapter.class);

    private final RestClient restClient;
    private final String nominatimUrl;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NominatimResult(
            @JsonProperty("lat") String lat,
            @JsonProperty("lon") String lon,
            @JsonProperty("display_name") String displayName
    ) {}

    public NominatimGeocodingAdapter(
            @Value("${tuttibox.geocoding.nominatim-url:https://nominatim.openstreetmap.org}") String nominatimUrl,
            @Value("${tuttibox.geocoding.user-agent:TuttiBox-UniversityOfAveiro-TQS-119495}") String userAgent) {
        this.nominatimUrl = nominatimUrl;
        this.restClient = RestClient.builder()
                .baseUrl(nominatimUrl)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    @Override
    @Cacheable(value = "geocodingCache", key = "#address", unless = "#result == null")
    public Optional<GeoCoordinates> geocodeAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            log.info("Querying OpenStreetMap Nominatim for address: {}", address);
            NominatimResult[] results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", address)
                            .queryParam("format", "json")
                            .queryParam("limit", "1")
                            .build())
                    .retrieve()
                    .body(NominatimResult[].class);

            if (results != null && results.length > 0) {
                double lat = Double.parseDouble(results[0].lat());
                double lon = Double.parseDouble(results[0].lon());
                return Optional.of(new GeoCoordinates(lat, lon, results[0].displayName()));
            }
        } catch (Exception e) {
            log.warn("Geocoding service unavailable or failed for address '{}': {}", address, e.getMessage());
        }

        return Optional.empty();
    }
}
