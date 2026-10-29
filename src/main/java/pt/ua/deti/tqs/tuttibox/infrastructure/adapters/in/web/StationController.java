package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pt.ua.deti.tqs.tuttibox.core.model.CompartmentState;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.service.StationRankingService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.StationResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
@Transactional(readOnly = true)
public class StationController {

    private final LockerStationRepository stationRepo;
    private final StationRankingService stationRankingService;

    public StationController(LockerStationRepository stationRepo, StationRankingService stationRankingService) {
        this.stationRepo = stationRepo;
        this.stationRankingService = stationRankingService;
    }

    @GetMapping
    public ResponseEntity<List<StationResponse>> getAllStations() {
        List<StationResponse> list = stationRepo.findAll().stream()
                .map(s -> toResponse(s, null))
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<StationResponse>> getNearbyStations(
            @RequestParam(required = false) String address,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon
    ) {
        List<LockerStation> all = stationRepo.findAll();
        List<StationRankingService.StationDistance> ranked;

        if (address != null && !address.isBlank()) {
            ranked = stationRankingService.rankStationsByAddress(address, all);
        } else if (lat != null && lon != null) {
            ranked = stationRankingService.rankStationsByCoordinates(lat, lon, all);
        } else {
            return getAllStations();
        }

        List<StationResponse> response = ranked.stream()
                .map(sd -> toResponse(sd.station(), sd.distanceKm()))
                .toList();

        return ResponseEntity.ok(response);
    }

    private StationResponse toResponse(LockerStation station, Double distanceKm) {
        int total = station.getCompartments().size();
        int available = (int) station.getCompartments().stream()
                .filter(c -> c.getState() == CompartmentState.FREE)
                .count();

        return new StationResponse(
                station.getStationCode(),
                station.getName(),
                station.getAddress(),
                station.getCity(),
                station.getLatitude(),
                station.getLongitude(),
                distanceKm,
                total,
                available
        );
    }
}
