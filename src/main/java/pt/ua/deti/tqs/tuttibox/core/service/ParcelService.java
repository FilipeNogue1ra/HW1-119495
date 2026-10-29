package pt.ua.deti.tqs.tuttibox.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.deti.tqs.tuttibox.core.model.*;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.ParcelDetailResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.CompartmentRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.ParcelRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ParcelService {

    private static final Logger log = LoggerFactory.getLogger(ParcelService.class);
    private final SecureRandom secureRandom = new SecureRandom();

    private final ParcelRepository parcelRepo;
    private final LockerStationRepository stationRepo;
    private final CompartmentRepository compartmentRepo;
    private final ParcelStateMachineService stateMachineService;
    private final Clock clock;

    private final int dropoffDeadlineHours;
    private final boolean allowSToMFallback;

    public ParcelService(
            ParcelRepository parcelRepo,
            LockerStationRepository stationRepo,
            CompartmentRepository compartmentRepo,
            ParcelStateMachineService stateMachineService,
            Clock clock,
            @Value("${tuttibox.rules.dropoff-deadline-hours:48}") int dropoffDeadlineHours,
            @Value("${tuttibox.rules.allow-s-to-m-fallback:true}") boolean allowSToMFallback) {
        this.parcelRepo = parcelRepo;
        this.stationRepo = stationRepo;
        this.compartmentRepo = compartmentRepo;
        this.stateMachineService = stateMachineService;
        this.clock = clock;
        this.dropoffDeadlineHours = dropoffDeadlineHours;
        this.allowSToMFallback = allowSToMFallback;
    }

    @Transactional
    public CreateParcelResponse createParcel(CreateParcelRequest request) {
        LockerStation station = stationRepo.findByStationCode(request.getStationCode())
                .orElseThrow(() -> new DomainRuleException("STATION_NOT_FOUND",
                        "Station with code '" + request.getStationCode() + "' does not exist."));

        Compartment compartment = allocateCompartment(station, request.getDimension());
        compartment.setState(CompartmentState.RESERVED);
        compartmentRepo.save(compartment);

        String rawPin = generateSecurePin();
        String pinHash = hashPin(rawPin);
        String trackingCode = generateUniqueTrackingCode();

        Instant now = clock.instant();
        Instant dropoffDeadline = now.plus(dropoffDeadlineHours, ChronoUnit.HOURS);

        Parcel parcel = new Parcel(
                trackingCode,
                request.getDimension(),
                request.getRecipientPhone(),
                request.getRecipientEmail(),
                pinHash,
                dropoffDeadline,
                station,
                compartment
        );

        // Record initial state creation in immutable audit trail
        parcel.addTransition(ParcelState.CREATED, Actor.PARTNER, now,
                "Shipment registered by partner. Compartment " + compartment.getCompartmentNumber() + " reserved.");

        parcelRepo.save(parcel);
        log.info("Created parcel '{}' for station '{}' in compartment '{}'",
                trackingCode, station.getStationCode(), compartment.getCompartmentNumber());

        return new CreateParcelResponse(
                trackingCode,
                rawPin,
                parcel.getDimension(),
                station.getStationCode(),
                station.getName(),
                compartment.getCompartmentNumber(),
                parcel.getState(),
                parcel.getDropoffDeadline()
        );
    }

    @Transactional(readOnly = true)
    public ParcelDetailResponse getParcelByTrackingCode(String trackingCode) {
        Parcel parcel = parcelRepo.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new DomainRuleException("PARCEL_NOT_FOUND",
                        "Parcel with tracking code '" + trackingCode + "' not found."));

        List<ParcelDetailResponse.TransitionDto> transitions = parcel.getTransitions().stream()
                .map(t -> new ParcelDetailResponse.TransitionDto(
                        t.getFromState(),
                        t.getToState(),
                        t.getActor(),
                        t.getTimestamp(),
                        t.getReason()
                )).toList();

        String compNum = parcel.getCompartment() != null ? parcel.getCompartment().getCompartmentNumber() : null;

        return new ParcelDetailResponse(
                parcel.getTrackingCode(),
                parcel.getDimension(),
                parcel.getState(),
                parcel.getStation().getStationCode(),
                parcel.getStation().getName(),
                compNum,
                parcel.getDropoffDeadline(),
                parcel.getPickupDeadline(),
                transitions
        );
    }

    @Transactional
    public ParcelDetailResponse cancelParcel(String trackingCode) {
        Parcel parcel = parcelRepo.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new DomainRuleException("PARCEL_NOT_FOUND",
                        "Parcel with tracking code '" + trackingCode + "' not found."));

        stateMachineService.transitionToCancelledByPartner(parcel);
        parcelRepo.save(parcel);

        return getParcelByTrackingCode(trackingCode);
    }

    private Compartment allocateCompartment(LockerStation station, Dimension dimension) {
        // 1. Try exact size match with pessimistic lock
        List<Compartment> exactMatches = compartmentRepo.findAvailableForUpdate(station, CompartmentState.FREE, dimension);
        if (!exactMatches.isEmpty()) {
            return exactMatches.get(0);
        }

        // 2. Rule 1: Fallback S -> M if all S compartments are full
        if (dimension == Dimension.S && allowSToMFallback) {
            List<Compartment> fallbackMatches = compartmentRepo.findAvailableForUpdate(station, CompartmentState.FREE, Dimension.M);
            if (!fallbackMatches.isEmpty()) {
                log.info("Applying Rule 1 fallback: allocated Dimension.M compartment for Dimension.S parcel.");
                return fallbackMatches.get(0);
            }
        }

        // Station is full for this size requirement
        throw new DomainRuleException("NO_LOCKERS_AVAILABLE",
                "No compatible compartment available at station " + station.getStationCode() + " for size " + dimension);
    }

    private String generateSecurePin() {
        int pinNumber = secureRandom.nextInt(1_000_000);
        return String.format("%06d", pinNumber);
    }

    public static String hashPin(String rawPin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawPin.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String generateUniqueTrackingCode() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TB-2026-" + uuid;
    }
}
