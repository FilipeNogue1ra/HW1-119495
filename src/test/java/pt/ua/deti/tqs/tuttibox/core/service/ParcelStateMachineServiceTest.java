package pt.ua.deti.tqs.tuttibox.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pt.ua.deti.tqs.tuttibox.core.model.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class ParcelStateMachineServiceTest {

    private ParcelStateMachineService stateMachineService;
    private Instant fixedInstant;
    private LockerStation station;
    private Compartment compartment;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-10-25T10:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        stateMachineService = new ParcelStateMachineService(fixedClock);

        station = new LockerStation("STATION-AVEIRO-01", "Aveiro Train Station", "Largo da Estação", "Aveiro", 40.6405, -8.6538);
        compartment = new Compartment(station, "A01", Dimension.M);
        compartment.setState(CompartmentState.RESERVED);

        Instant dropoffDeadline = fixedInstant.plusSeconds(48 * 3600);
        parcel = new Parcel("TB-2026-0001", Dimension.M, "+351912345678", "client@tuttibox.pt",
                "hashedPin123", dropoffDeadline, station, compartment);
    }

    @Test
    @DisplayName("UC3: Courier deposits parcel within 48h -> transitions to READY_FOR_PICKUP")
    void testCourierDepositsParcelSuccessfully() {
        assertEquals(ParcelState.CREATED, parcel.getState());
        assertEquals(CompartmentState.RESERVED, compartment.getState());

        stateMachineService.transitionToReadyForPickup(parcel, true);

        assertEquals(ParcelState.READY_FOR_PICKUP, parcel.getState());
        assertEquals(CompartmentState.OCCUPIED, compartment.getState());
        assertNotNull(parcel.getPickupDeadline());
        assertEquals(fixedInstant.plusSeconds(72 * 3600), parcel.getPickupDeadline());

        assertEquals(1, parcel.getTransitions().size());
        assertEquals(Actor.COURIER, parcel.getTransitions().get(0).getActor());
    }

    @Test
    @DisplayName("UC3: Courier cannot deposit parcel if drop-off deadline has elapsed (48h)")
    void testCourierCannotDepositAfterDropoffDeadline() {
        // Move clock 49 hours into the future
        Clock futureClock = Clock.fixed(fixedInstant.plusSeconds(49 * 3600), ZoneId.of("UTC"));
        ParcelStateMachineService futureService = new ParcelStateMachineService(futureClock);

        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                futureService.transitionToReadyForPickup(parcel, true)
        );
        assertEquals("DROPOFF_EXPIRED", ex.getErrorCode());
        assertEquals(ParcelState.CREATED, parcel.getState());
    }

    @Test
    @DisplayName("UC3: Courier cannot deposit without hardware door opening confirmation")
    void testCourierCannotDepositWithoutDoorOpening() {
        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                stateMachineService.transitionToReadyForPickup(parcel, false)
        );
        assertEquals("DOOR_NOT_OPENED", ex.getErrorCode());
    }

    @Test
    @DisplayName("UC1/UC2: Partner cancels shipment before courier deposit -> CANCELLED and compartment freed")
    void testPartnerCancelsShipment() {
        stateMachineService.transitionToCancelledByPartner(parcel);

        assertEquals(ParcelState.CANCELLED, parcel.getState());
        assertEquals(CompartmentState.FREE, compartment.getState());
        assertNull(parcel.getCompartment());
    }

    @Test
    @DisplayName("System cancels parcel when 48h drop-off window elapses without deposit")
    void testSystemCancelsExpiredDropoff() {
        Clock futureClock = Clock.fixed(fixedInstant.plusSeconds(48 * 3600 + 1), ZoneId.of("UTC"));
        ParcelStateMachineService futureService = new ParcelStateMachineService(futureClock);

        futureService.transitionToCancelledByTimeout(parcel);

        assertEquals(ParcelState.CANCELLED, parcel.getState());
        assertEquals(CompartmentState.FREE, compartment.getState());
        assertEquals(Actor.SYSTEM, parcel.getTransitions().get(0).getActor());
    }

    @Test
    @DisplayName("UC4: Recipient collects parcel and closes door within 72h -> PICKED_UP and compartment freed")
    void testRecipientCollectsParcelSuccessfully() {
        stateMachineService.transitionToReadyForPickup(parcel, true);

        stateMachineService.transitionToPickedUp(parcel, true);

        assertEquals(ParcelState.PICKED_UP, parcel.getState());
        assertEquals(CompartmentState.FREE, compartment.getState());
        assertTrue(parcel.getState().isTerminal());
    }

    @Test
    @DisplayName("UC4: Door jammed on pickup -> WAITING_FOR_OPERATOR and compartment OUT_OF_SERVICE")
    void testHardwareFailureTransitionsToWaitingForOperator() {
        stateMachineService.transitionToReadyForPickup(parcel, true);

        stateMachineService.transitionToWaitingForOperatorOnJammed(parcel, "Solenoid lock motor timeout");

        assertEquals(ParcelState.WAITING_FOR_OPERATOR, parcel.getState());
        assertEquals(CompartmentState.OUT_OF_SERVICE, compartment.getState());
        assertTrue(compartment.getMaintenanceNote().contains("Solenoid lock motor timeout"));
    }

    @Test
    @DisplayName("UC6: Operator relocates jammed parcel to another FREE compartment -> READY_FOR_PICKUP")
    void testOperatorRelocatesJammedParcel() {
        stateMachineService.transitionToReadyForPickup(parcel, true);
        stateMachineService.transitionToWaitingForOperatorOnJammed(parcel, "Mechanical door latch jammed");

        Compartment newComp = new Compartment(station, "B02", Dimension.M);
        newComp.setState(CompartmentState.FREE);

        stateMachineService.transitionToReadyForPickupRelocated(parcel, newComp);

        assertEquals(ParcelState.READY_FOR_PICKUP, parcel.getState());
        assertEquals(newComp, parcel.getCompartment());
        assertEquals(CompartmentState.OCCUPIED, newComp.getState());
    }

    @Test
    @DisplayName("72h pickup window elapses -> transitions to EXPIRED, compartment remains OCCUPIED")
    void testPickupWindowElapsesTransitionsToExpired() {
        stateMachineService.transitionToReadyForPickup(parcel, true);

        // Advance clock past 72h
        Clock expiredClock = Clock.fixed(fixedInstant.plusSeconds(73 * 3600), ZoneId.of("UTC"));
        ParcelStateMachineService expiredService = new ParcelStateMachineService(expiredClock);

        expiredService.transitionToExpiredByTimeout(parcel);

        assertEquals(ParcelState.EXPIRED, parcel.getState());
        assertEquals(CompartmentState.OCCUPIED, compartment.getState());
    }

    @Test
    @DisplayName("UC6: Operator retrieves expired parcel -> transitions to RETURNED and frees compartment")
    void testOperatorRetrievesExpiredParcel() {
        stateMachineService.transitionToReadyForPickup(parcel, true);
        Clock expiredClock = Clock.fixed(fixedInstant.plusSeconds(73 * 3600), ZoneId.of("UTC"));
        ParcelStateMachineService expiredService = new ParcelStateMachineService(expiredClock);
        expiredService.transitionToExpiredByTimeout(parcel);

        expiredService.transitionToReturnedFromExpired(parcel);

        assertEquals(ParcelState.RETURNED, parcel.getState());
        assertEquals(CompartmentState.FREE, compartment.getState());
    }

    @ParameterizedTest
    @EnumSource(value = ParcelState.class, names = {"READY_FOR_PICKUP", "WAITING_FOR_OPERATOR", "PICKED_UP", "CANCELLED", "EXPIRED", "RETURNED"})
    @DisplayName("Guards: Illegal states cannot transition to READY_FOR_PICKUP via dropoff")
    void testInvalidStatesCannotDropoff(ParcelState invalidState) {
        parcel.setState(invalidState);

        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                stateMachineService.transitionToReadyForPickup(parcel, true)
        );
        assertEquals("INVALID_STATE", ex.getErrorCode());
    }
}
