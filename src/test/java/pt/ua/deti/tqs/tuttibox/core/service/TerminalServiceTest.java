package pt.ua.deti.tqs.tuttibox.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.tuttibox.core.model.*;
import pt.ua.deti.tqs.tuttibox.core.ports.out.LockerHardwarePort;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.*;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.CompartmentRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.ParcelRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TerminalServiceTest {

    @Mock
    private LockerStationRepository stationRepo;

    @Mock
    private ParcelRepository parcelRepo;

    @Mock
    private CompartmentRepository compartmentRepo;

    @Mock
    private LockerHardwarePort hardwarePort;

    private ParcelStateMachineService stateMachineService;
    private TerminalService terminalService;

    private final Instant baseTime = Instant.parse("2026-10-30T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(baseTime, ZoneId.of("UTC"));

    private LockerStation station;
    private Compartment compartment;
    private Parcel parcel;
    private final String rawPin = "123456";

    @BeforeEach
    void setUp() {
        stateMachineService = new ParcelStateMachineService(fixedClock);
        terminalService = new TerminalService(
                stationRepo,
                parcelRepo,
                compartmentRepo,
                stateMachineService,
                hardwarePort,
                fixedClock
        );

        station = new LockerStation("STATION-AVEIRO-01", "Estação CP Aveiro", "Largo da Estação", "Aveiro", 40.6433, -8.6406);
        compartment = new Compartment(station, "A01", Dimension.S);
        compartment.setState(CompartmentState.RESERVED);

        String pinHash = ParcelService.hashPin(rawPin);
        parcel = new Parcel(
                "TB-2026-TEST0001",
                Dimension.S,
                "+351912345678",
                "client@ua.pt",
                pinHash,
                baseTime.plus(48, ChronoUnit.HOURS),
                station,
                compartment
        );
        parcel.addTransition(ParcelState.CREATED, Actor.PARTNER, baseTime, "Initialized");
    }

    @Test
    @DisplayName("UC3: Estafeta deposita encomenda com sucesso -> porta abre, fecha, estado READY_FOR_PICKUP")
    void testDropoffSuccess() {
        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));
        when(parcelRepo.findByTrackingCode("TB-2026-TEST0001")).thenReturn(Optional.of(parcel));
        when(hardwarePort.unlockDoor("STATION-AVEIRO-01", "A01")).thenReturn(true);
        when(hardwarePort.closeDoor("STATION-AVEIRO-01", "A01")).thenReturn(true);

        DropoffRequest request = new DropoffRequest("TB-2026-TEST0001");
        DropoffResponse response = terminalService.dropoffParcel("STATION-AVEIRO-01", request);

        assertNotNull(response);
        assertEquals(ParcelState.READY_FOR_PICKUP, response.state());
        assertEquals("A01", response.compartmentNumber());
        assertEquals(CompartmentState.OCCUPIED, compartment.getState());

        verify(hardwarePort).unlockDoor("STATION-AVEIRO-01", "A01");
        verify(hardwarePort).closeDoor("STATION-AVEIRO-01", "A01");
        verify(parcelRepo).save(parcel);
        verify(compartmentRepo).save(compartment);
    }

    @Test
    @DisplayName("UC3 Falha: Porta do cacifo encrava ao abrir para estafeta -> lança DOOR_UNLOCK_FAILED")
    void testDropoffHardwareUnlockFailure() {
        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));
        when(parcelRepo.findByTrackingCode("TB-2026-TEST0001")).thenReturn(Optional.of(parcel));
        when(hardwarePort.unlockDoor("STATION-AVEIRO-01", "A01")).thenReturn(false);

        DropoffRequest request = new DropoffRequest("TB-2026-TEST0001");
        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                terminalService.dropoffParcel("STATION-AVEIRO-01", request)
        );

        assertEquals("DOOR_UNLOCK_FAILED", ex.getErrorCode());
        assertEquals(ParcelState.CREATED, parcel.getState());
        verify(parcelRepo, never()).save(any());
    }

    @Test
    @DisplayName("UC3 Falha: Estafeta tenta depositar na estação errada -> lança INVALID_STATION")
    void testDropoffWrongStation() {
        LockerStation station2 = new LockerStation("STATION-AVEIRO-02", "UA", "Campus", "Aveiro", 40.63, -8.65);
        when(stationRepo.findByStationCode("STATION-AVEIRO-02")).thenReturn(Optional.of(station2));
        when(parcelRepo.findByTrackingCode("TB-2026-TEST0001")).thenReturn(Optional.of(parcel));

        DropoffRequest request = new DropoffRequest("TB-2026-TEST0001");
        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                terminalService.dropoffParcel("STATION-AVEIRO-02", request)
        );

        assertEquals("INVALID_STATION", ex.getErrorCode());
    }

    @Test
    @DisplayName("UC4: Destinatário levanta com PIN de 6 dígitos no terminal -> porta abre, fecha, estado PICKED_UP")
    void testPickupSuccessWithPinOnly() {
        // Encomenda já pronta para recolha
        stateMachineService.transitionToReadyForPickup(parcel, true);

        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));
        when(parcelRepo.findByStationAndPickupPinHashAndState(
                eq(station), any(String.class), eq(ParcelState.READY_FOR_PICKUP)
        )).thenReturn(Optional.of(parcel));
        when(hardwarePort.unlockDoor("STATION-AVEIRO-01", "A01")).thenReturn(true);
        when(hardwarePort.closeDoor("STATION-AVEIRO-01", "A01")).thenReturn(true);

        PickupRequest request = new PickupRequest(null, rawPin);
        PickupResponse response = terminalService.pickupParcel("STATION-AVEIRO-01", request);

        assertNotNull(response);
        assertEquals(ParcelState.PICKED_UP, response.state());
        assertEquals("A01", response.compartmentNumber());
        assertEquals(CompartmentState.FREE, compartment.getState());
        assertEquals(0, station.getConsecutiveFailedPins());

        verify(hardwarePort).unlockDoor("STATION-AVEIRO-01", "A01");
        verify(hardwarePort).closeDoor("STATION-AVEIRO-01", "A01");
        verify(parcelRepo).save(parcel);
        verify(compartmentRepo).save(compartment);
    }

    @Test
    @DisplayName("UC4: Destinatário levanta com TrackingCode + PIN -> sucesso")
    void testPickupSuccessWithTrackingAndPin() {
        stateMachineService.transitionToReadyForPickup(parcel, true);

        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));
        when(parcelRepo.findByTrackingCodeAndStation("TB-2026-TEST0001", station)).thenReturn(Optional.of(parcel));
        when(hardwarePort.unlockDoor("STATION-AVEIRO-01", "A01")).thenReturn(true);
        when(hardwarePort.closeDoor("STATION-AVEIRO-01", "A01")).thenReturn(true);

        PickupRequest request = new PickupRequest("TB-2026-TEST0001", rawPin);
        PickupResponse response = terminalService.pickupParcel("STATION-AVEIRO-01", request);

        assertNotNull(response);
        assertEquals(ParcelState.PICKED_UP, response.state());
    }

    @Test
    @DisplayName("UC4 Segurança: PIN incorreto incrementa tentativas falhadas -> lança INVALID_PIN")
    void testPickupInvalidPinIncrementsCounter() {
        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));
        when(parcelRepo.findByStationAndPickupPinHashAndState(eq(station), any(String.class), eq(ParcelState.READY_FOR_PICKUP)))
                .thenReturn(Optional.empty());

        PickupRequest request = new PickupRequest(null, "999999");
        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                terminalService.pickupParcel("STATION-AVEIRO-01", request)
        );

        assertEquals("INVALID_PIN", ex.getErrorCode());
        assertEquals(1, station.getConsecutiveFailedPins());
        assertFalse(station.isLocked(baseTime));
        verify(stationRepo).save(station);
    }

    @Test
    @DisplayName("UC4 Segurança: 3 tentativas consecutivas com PIN errado bloqueiam terminal por 60s -> lança TERMINAL_LOCKED")
    void testPickupThreeFailedPinsTriggersLockout() {
        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));
        when(parcelRepo.findByStationAndPickupPinHashAndState(eq(station), any(String.class), eq(ParcelState.READY_FOR_PICKUP)))
                .thenReturn(Optional.empty());

        PickupRequest badRequest = new PickupRequest(null, "999999");

        // 1ª tentativa
        assertThrows(DomainRuleException.class, () -> terminalService.pickupParcel("STATION-AVEIRO-01", badRequest));
        assertEquals(1, station.getConsecutiveFailedPins());

        // 2ª tentativa
        assertThrows(DomainRuleException.class, () -> terminalService.pickupParcel("STATION-AVEIRO-01", badRequest));
        assertEquals(2, station.getConsecutiveFailedPins());

        // 3ª tentativa -> bloqueio de 60 segundos
        DomainRuleException lockoutEx = assertThrows(DomainRuleException.class, () ->
                terminalService.pickupParcel("STATION-AVEIRO-01", badRequest)
        );

        assertEquals("TERMINAL_LOCKED", lockoutEx.getErrorCode());
        assertTrue(station.isLocked(baseTime));
        assertEquals(baseTime.plusSeconds(60), station.getLockedUntil());
    }

    @Test
    @DisplayName("UC4 Segurança: Tentativa de levantamento durante período de bloqueio é rejeitada imediatamente")
    void testPickupDuringLockoutPeriodBlocked() {
        station.setLockedUntil(baseTime.plusSeconds(45));
        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));

        PickupRequest request = new PickupRequest(null, rawPin);
        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                terminalService.pickupParcel("STATION-AVEIRO-01", request)
        );

        assertEquals("TERMINAL_LOCKED", ex.getErrorCode());
        verify(parcelRepo, never()).findByTrackingCodeAndStation(any(), any());
        verify(hardwarePort, never()).unlockDoor(any(), any());
    }

    @Test
    @DisplayName("UC4 Tolerância a Falhas: Porta encrava no levantamento -> transição para WAITING_FOR_OPERATOR e OUT_OF_SERVICE")
    void testPickupDoorJammedSimulation() {
        stateMachineService.transitionToReadyForPickup(parcel, true);

        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));
        when(parcelRepo.findByStationAndPickupPinHashAndState(eq(station), any(String.class), eq(ParcelState.READY_FOR_PICKUP)))
                .thenReturn(Optional.of(parcel));
        // Simulação de encravamento físico da tranca solenóide
        when(hardwarePort.unlockDoor("STATION-AVEIRO-01", "A01")).thenReturn(false);

        PickupRequest request = new PickupRequest(null, rawPin);
        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                terminalService.pickupParcel("STATION-AVEIRO-01", request)
        );

        assertEquals("DOOR_JAMMED", ex.getErrorCode());
        assertEquals(ParcelState.WAITING_FOR_OPERATOR, parcel.getState());
        assertEquals(CompartmentState.OUT_OF_SERVICE, compartment.getState());
        verify(parcelRepo).save(parcel);
        verify(compartmentRepo).save(compartment);
    }

    @Test
    @DisplayName("Estado do terminal: consulta de bloqueio e contadores")
    void testGetTerminalStatus() {
        station.setLockedUntil(baseTime.plusSeconds(30));
        station.setConsecutiveFailedPins(2);
        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));

        TerminalStatusResponse status = terminalService.getTerminalStatus("STATION-AVEIRO-01");

        assertNotNull(status);
        assertEquals("STATION-AVEIRO-01", status.stationCode());
        assertTrue(status.locked());
        assertEquals(30, status.remainingLockoutSeconds());
        assertEquals(2, status.consecutiveFailedPins());
    }
}
