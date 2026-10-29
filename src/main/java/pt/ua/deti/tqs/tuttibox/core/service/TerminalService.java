package pt.ua.deti.tqs.tuttibox.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.model.Parcel;
import pt.ua.deti.tqs.tuttibox.core.model.ParcelState;
import pt.ua.deti.tqs.tuttibox.core.ports.out.LockerHardwarePort;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.*;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.CompartmentRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.ParcelRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class TerminalService {

    private static final Logger log = LoggerFactory.getLogger(TerminalService.class);
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final int LOCKOUT_SECONDS = 60;

    private final LockerStationRepository stationRepo;
    private final ParcelRepository parcelRepo;
    private final CompartmentRepository compartmentRepo;
    private final ParcelStateMachineService stateMachineService;
    private final LockerHardwarePort lockerHardwarePort;
    private final Clock clock;

    public TerminalService(
            LockerStationRepository stationRepo,
            ParcelRepository parcelRepo,
            CompartmentRepository compartmentRepo,
            ParcelStateMachineService stateMachineService,
            LockerHardwarePort lockerHardwarePort,
            Clock clock) {
        this.stationRepo = stationRepo;
        this.parcelRepo = parcelRepo;
        this.compartmentRepo = compartmentRepo;
        this.stateMachineService = stateMachineService;
        this.lockerHardwarePort = lockerHardwarePort;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = DomainRuleException.class)
    public DropoffResponse dropoffParcel(String stationCode, DropoffRequest request) {
        LockerStation station = findStationOrThrow(stationCode);

        Parcel parcel = parcelRepo.findByTrackingCode(request.trackingCode())
                .orElseThrow(() -> new DomainRuleException("PARCEL_NOT_FOUND",
                        "Parcel with tracking code '" + request.trackingCode() + "' not found."));

        if (!parcel.getStation().getStationCode().equals(station.getStationCode())) {
            throw new DomainRuleException("INVALID_STATION",
                    "Parcel '" + request.trackingCode() + "' is assigned to station '"
                            + parcel.getStation().getStationCode() + "', not '" + stationCode + "'.");
        }

        if (parcel.getState() != ParcelState.CREATED) {
            throw new DomainRuleException("INVALID_STATE",
                    "Parcel must be in CREATED state to be deposited. Current state: " + parcel.getState());
        }

        String compartmentNumber = parcel.getCompartment().getCompartmentNumber();

        // Hardware command: pop open compartment door for courier
        boolean unlocked = lockerHardwarePort.unlockDoor(stationCode, compartmentNumber);
        if (!unlocked) {
            log.error("Hardware fault: failed to unlock compartment {} at station {}", compartmentNumber, stationCode);
            throw new DomainRuleException("DOOR_UNLOCK_FAILED",
                    "Hardware failure: unable to unlock compartment " + compartmentNumber + " for deposit.");
        }

        // Transition domain state: CREATED -> READY_FOR_PICKUP (sets compartment to OCCUPIED and 72h deadline)
        stateMachineService.transitionToReadyForPickup(parcel, true);

        // Courier closes door
        lockerHardwarePort.closeDoor(stationCode, compartmentNumber);

        parcelRepo.save(parcel);
        compartmentRepo.save(parcel.getCompartment());

        log.info("Courier dropped off parcel '{}' in compartment '{}' at station '{}'",
                parcel.getTrackingCode(), compartmentNumber, stationCode);

        return new DropoffResponse(
                parcel.getTrackingCode(),
                stationCode,
                compartmentNumber,
                parcel.getState(),
                parcel.getPickupDeadline(),
                "Parcel successfully deposited in compartment " + compartmentNumber + ". Ready for customer pickup."
        );
    }

    @Transactional(noRollbackFor = DomainRuleException.class)
    public PickupResponse pickupParcel(String stationCode, PickupRequest request) {
        LockerStation station = findStationOrThrow(stationCode);
        Instant now = clock.instant();

        // 1. Check if terminal is currently locked
        if (station.isLocked(now)) {
            long remaining = Duration.between(now, station.getLockedUntil()).toSeconds();
            log.warn("Pickup attempt rejected: terminal '{}' is locked for {} more seconds", stationCode, remaining);
            throw new DomainRuleException("TERMINAL_LOCKED",
                    "Terminal is locked due to consecutive failed attempts. Please wait " + remaining + " seconds.");
        }

        String inputPinHash = ParcelService.hashPin(request.pin());

        // 2. Identify the target parcel
        Optional<Parcel> targetParcelOpt;
        if (request.trackingCode() != null && !request.trackingCode().isBlank()) {
            targetParcelOpt = parcelRepo.findByTrackingCodeAndStation(request.trackingCode().trim(), station);
        } else {
            targetParcelOpt = parcelRepo.findByStationAndPickupPinHashAndState(station, inputPinHash, ParcelState.READY_FOR_PICKUP);
        }

        if (targetParcelOpt.isEmpty()) {
            handleFailedAttempt(station, now);
            return null; // unreachable as handleFailedAttempt throws
        }

        Parcel parcel = targetParcelOpt.get();

        if (parcel.getState() != ParcelState.READY_FOR_PICKUP) {
            throw new DomainRuleException("INVALID_STATE",
                    "Parcel is not available for pickup. Current state: " + parcel.getState());
        }

        // 3. Constant-time PIN hash comparison to prevent timing side-channel attacks
        boolean pinValid = MessageDigest.isEqual(
                inputPinHash.getBytes(StandardCharsets.UTF_8),
                parcel.getPickupPinHash().getBytes(StandardCharsets.UTF_8)
        );

        if (!pinValid) {
            handleFailedAttempt(station, now);
            return null; // unreachable
        }

        // Reset failed attempt counter on successful PIN match
        station.resetFailedPins();
        stationRepo.save(station);

        String compartmentNumber = parcel.getCompartment().getCompartmentNumber();

        // 4. Hardware command: unlock door for recipient
        boolean unlocked = lockerHardwarePort.unlockDoor(stationCode, compartmentNumber);
        if (!unlocked) {
            log.error("Hardware fault: compartment door {} jammed on pickup at station {}", compartmentNumber, stationCode);
            stateMachineService.transitionToWaitingForOperatorOnJammed(parcel, "Compartment door mechanical jam on customer pickup.");
            parcelRepo.save(parcel);
            compartmentRepo.save(parcel.getCompartment());
            throw new DomainRuleException("DOOR_JAMMED",
                    "Compartment door is jammed. Operator alert dispatched. We apologize for the inconvenience.");
        }

        // Customer retrieves parcel and closes door
        lockerHardwarePort.closeDoor(stationCode, compartmentNumber);

        // Transition domain state: READY_FOR_PICKUP -> PICKED_UP (frees compartment)
        stateMachineService.transitionToPickedUp(parcel, true);

        parcelRepo.save(parcel);
        compartmentRepo.save(parcel.getCompartment());

        log.info("Recipient picked up parcel '{}' from compartment '{}' at station '{}'",
                parcel.getTrackingCode(), compartmentNumber, stationCode);

        return new PickupResponse(
                parcel.getTrackingCode(),
                stationCode,
                compartmentNumber,
                parcel.getState(),
                "Parcel collected successfully from compartment " + compartmentNumber + ". Thank you for using TuttiBox!"
        );
    }

    @Transactional(readOnly = true)
    public TerminalStatusResponse getTerminalStatus(String stationCode) {
        LockerStation station = findStationOrThrow(stationCode);
        Instant now = clock.instant();
        boolean locked = station.isLocked(now);
        long remaining = locked ? Duration.between(now, station.getLockedUntil()).toSeconds() : 0L;

        return new TerminalStatusResponse(
                stationCode,
                locked,
                remaining,
                station.getConsecutiveFailedPins()
        );
    }

    private void handleFailedAttempt(LockerStation station, Instant now) {
        station.recordFailedPinAttempt(now, MAX_FAILED_ATTEMPTS, LOCKOUT_SECONDS);
        stationRepo.save(station);

        if (station.isLocked(now)) {
            long remaining = Duration.between(now, station.getLockedUntil()).toSeconds();
            log.warn("Terminal '{}' locked out for {} seconds after {} failed attempts",
                    station.getStationCode(), remaining, MAX_FAILED_ATTEMPTS);
            throw new DomainRuleException("TERMINAL_LOCKED",
                    "3 invalid PIN attempts. Terminal is locked for " + remaining + " seconds.");
        } else {
            int remainingAttempts = MAX_FAILED_ATTEMPTS - station.getConsecutiveFailedPins();
            log.warn("Invalid PIN attempt at station '{}'. Remaining attempts before lockout: {}",
                    station.getStationCode(), remainingAttempts);
            throw new DomainRuleException("INVALID_PIN",
                    "Invalid PIN. Remaining attempts before lockout: " + remainingAttempts);
        }
    }

    private LockerStation findStationOrThrow(String stationCode) {
        return stationRepo.findByStationCode(stationCode)
                .orElseThrow(() -> new DomainRuleException("STATION_NOT_FOUND",
                        "Station with code '" + stationCode + "' does not exist."));
    }
}
