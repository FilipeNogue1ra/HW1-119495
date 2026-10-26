package pt.ua.deti.tqs.tuttibox.core.service;

import org.springframework.stereotype.Service;
import pt.ua.deti.tqs.tuttibox.core.model.*;

import java.time.Clock;
import java.time.Instant;

@Service
public class ParcelStateMachineService {

    private final Clock clock;

    public ParcelStateMachineService(Clock clock) {
        this.clock = clock;
    }

    public void transitionToReadyForPickup(Parcel parcel, boolean doorWasOpened) {
        if (parcel.getState() != ParcelState.CREATED) {
            throw new DomainRuleException("INVALID_STATE", "Parcel must be in CREATED state to transition to READY_FOR_PICKUP, current: " + parcel.getState());
        }
        Instant now = clock.instant();
        if (now.isAfter(parcel.getDropoffDeadline())) {
            throw new DomainRuleException("DROPOFF_EXPIRED", "Drop-off deadline has elapsed (48h limit). Parcel cannot be deposited.");
        }
        if (!doorWasOpened) {
            throw new DomainRuleException("DOOR_NOT_OPENED", "Cannot deposit parcel without door having been unlocked and opened.");
        }

        parcel.addTransition(ParcelState.READY_FOR_PICKUP, Actor.COURIER, now, "Courier deposited parcel in compartment.");
        if (parcel.getCompartment() != null) {
            parcel.getCompartment().setState(CompartmentState.OCCUPIED);
        }
        // Pickup deadline set to 72h from now
        parcel.setPickupDeadline(now.plusSeconds(72 * 3600));
    }

    public void transitionToCancelledByPartner(Parcel parcel) {
        if (parcel.getState() != ParcelState.CREATED) {
            throw new DomainRuleException("INVALID_STATE", "Only parcels in CREATED state can be cancelled by Partner.");
        }
        Instant now = clock.instant();
        parcel.addTransition(ParcelState.CANCELLED, Actor.PARTNER, now, "Shipment cancelled by partner before courier deposit.");
        if (parcel.getCompartment() != null) {
            parcel.getCompartment().setState(CompartmentState.FREE);
            parcel.setCompartment(null);
        }
    }

    public void transitionToCancelledByTimeout(Parcel parcel) {
        if (parcel.getState() != ParcelState.CREATED) {
            throw new DomainRuleException("INVALID_STATE", "Only parcels in CREATED state can expire drop-off timeout.");
        }
        Instant now = clock.instant();
        if (!now.isAfter(parcel.getDropoffDeadline())) {
            throw new DomainRuleException("TIMEOUT_NOT_REACHED", "Drop-off deadline has not elapsed yet.");
        }
        parcel.addTransition(ParcelState.CANCELLED, Actor.SYSTEM, now, "Drop-off deadline (48h) elapsed without courier deposit.");
        if (parcel.getCompartment() != null) {
            parcel.getCompartment().setState(CompartmentState.FREE);
            parcel.setCompartment(null);
        }
    }

    public void transitionToPickedUp(Parcel parcel, boolean doorClosed) {
        if (parcel.getState() != ParcelState.READY_FOR_PICKUP) {
            throw new DomainRuleException("INVALID_STATE", "Parcel must be READY_FOR_PICKUP to be picked up.");
        }
        Instant now = clock.instant();
        if (now.isAfter(parcel.getPickupDeadline())) {
            throw new DomainRuleException("PICKUP_EXPIRED", "Pickup deadline has elapsed (72h limit).");
        }
        if (!doorClosed) {
            throw new DomainRuleException("DOOR_STILL_OPEN", "Compartment door must be closed to complete pickup.");
        }
        parcel.addTransition(ParcelState.PICKED_UP, Actor.RECIPIENT, now, "Recipient collected parcel and closed compartment door.");
        if (parcel.getCompartment() != null) {
            parcel.getCompartment().setState(CompartmentState.FREE);
        }
    }

    public void transitionToWaitingForOperatorOnJammed(Parcel parcel, String hardwareErrorNote) {
        if (parcel.getState() != ParcelState.READY_FOR_PICKUP) {
            throw new DomainRuleException("INVALID_STATE", "Only READY_FOR_PICKUP parcels can enter WAITING_FOR_OPERATOR on hardware failure.");
        }
        Instant now = clock.instant();
        parcel.addTransition(ParcelState.WAITING_FOR_OPERATOR, Actor.RECIPIENT, now, "Hardware failure: " + hardwareErrorNote);
        if (parcel.getCompartment() != null) {
            parcel.getCompartment().setState(CompartmentState.OUT_OF_SERVICE);
            parcel.getCompartment().setMaintenanceNote("Hardware fault during pickup: " + hardwareErrorNote);
        }
    }

    public void transitionToExpiredByTimeout(Parcel parcel) {
        if (parcel.getState() != ParcelState.READY_FOR_PICKUP) {
            throw new DomainRuleException("INVALID_STATE", "Only READY_FOR_PICKUP parcels can transition to EXPIRED.");
        }
        Instant now = clock.instant();
        if (!now.isAfter(parcel.getPickupDeadline())) {
            throw new DomainRuleException("TIMEOUT_NOT_REACHED", "Pickup deadline has not elapsed yet.");
        }
        parcel.addTransition(ParcelState.EXPIRED, Actor.SYSTEM, now, "72h pickup window elapsed without recipient collection.");
        // Compartment remains OCCUPIED until operator removes package
    }

    public void transitionToReadyForPickupRelocated(Parcel parcel, Compartment newCompartment) {
        if (parcel.getState() != ParcelState.WAITING_FOR_OPERATOR) {
            throw new DomainRuleException("INVALID_STATE", "Only WAITING_FOR_OPERATOR parcels can be relocated.");
        }
        if (newCompartment.getState() != CompartmentState.FREE) {
            throw new DomainRuleException("COMPARTMENT_NOT_FREE", "Target relocation compartment must be FREE.");
        }
        if (!parcel.getDimension().canFitIn(newCompartment.getDimension(), true)) {
            throw new DomainRuleException("INCOMPATIBLE_SIZE", "Target compartment size is too small for parcel.");
        }

        Instant now = clock.instant();
        newCompartment.setState(CompartmentState.OCCUPIED);
        parcel.setCompartment(newCompartment);
        parcel.addTransition(ParcelState.READY_FOR_PICKUP, Actor.OPERATOR, now, "Operator relocated parcel to working compartment " + newCompartment.getCompartmentNumber());
    }

    public void transitionToReturnedFromJammed(Parcel parcel) {
        if (parcel.getState() != ParcelState.WAITING_FOR_OPERATOR) {
            throw new DomainRuleException("INVALID_STATE", "Parcel must be in WAITING_FOR_OPERATOR state.");
        }
        Instant now = clock.instant();
        parcel.addTransition(ParcelState.RETURNED, Actor.OPERATOR, now, "Operator removed parcel from jammed compartment for warehouse return.");
        // Faulty compartment remains OUT_OF_SERVICE
    }

    public void transitionToReturnedFromExpired(Parcel parcel) {
        if (parcel.getState() != ParcelState.EXPIRED) {
            throw new DomainRuleException("INVALID_STATE", "Parcel must be in EXPIRED state to be returned.");
        }
        Instant now = clock.instant();
        parcel.addTransition(ParcelState.RETURNED, Actor.OPERATOR, now, "Operator removed expired parcel for return.");
        if (parcel.getCompartment() != null) {
            parcel.getCompartment().setState(CompartmentState.FREE);
        }
    }
}
