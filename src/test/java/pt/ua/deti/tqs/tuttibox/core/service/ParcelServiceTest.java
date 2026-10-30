package pt.ua.deti.tqs.tuttibox.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.ua.deti.tqs.tuttibox.core.model.*;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.ParcelDetailResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.CompartmentRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.ParcelRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParcelServiceTest {

    @Mock
    private ParcelRepository parcelRepo;

    @Mock
    private LockerStationRepository stationRepo;

    @Mock
    private CompartmentRepository compartmentRepo;

    private Clock clock;
    private ParcelStateMachineService stateMachineService;
    private ParcelService parcelService;

    private LockerStation station;
    private Compartment compartmentS;
    private Compartment compartmentM;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-29T10:00:00Z");
        clock = Clock.fixed(now, ZoneId.of("UTC"));
        stateMachineService = new ParcelStateMachineService(clock);

        parcelService = new ParcelService(
                parcelRepo,
                stationRepo,
                compartmentRepo,
                stateMachineService,
                clock,
                48,
                true // allow S to M fallback
        );

        station = new LockerStation("STATION-AVEIRO-01", "Estação CP Aveiro", "Largo da Estação", "Aveiro", 40.6433,
                -8.6406);
        compartmentS = new Compartment(station, "A01", Dimension.S);
        compartmentM = new Compartment(station, "B01", Dimension.M);
    }

    @Test
    @DisplayName("UC1: Alocação exata - pacote S é alocado no cacifo S disponível")
    void testCreateParcelExactSizeMatch() {
        CreateParcelRequest request = new CreateParcelRequest(Dimension.S, "+351912345678", "client@ua.pt",
                "STATION-AVEIRO-01");

        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));
        when(compartmentRepo.findAvailableForUpdate(station, CompartmentState.FREE, Dimension.S))
                .thenReturn(List.of(compartmentS));

        CreateParcelResponse response = parcelService.createParcel(request);

        assertNotNull(response);
        assertTrue(response.getTrackingCode().startsWith("TB-2026-"));
        assertEquals(6, response.getPickupPin().length());
        assertEquals("A01", response.getCompartmentNumber());
        assertEquals(CompartmentState.RESERVED, compartmentS.getState());
        assertEquals(now.plusSeconds(48 * 3600), response.getDropoffDeadline());

        verify(compartmentRepo).save(compartmentS);
        verify(parcelRepo).save(any(Parcel.class));
    }

    @Test
    @DisplayName("Regra 1 (Fallback): pacote S usa cacifo M quando todos os S estão esgotados")
    void testCreateParcelFallbackSToM() {
        CreateParcelRequest request = new CreateParcelRequest(Dimension.S, "+351912345678", "client@ua.pt",
                "STATION-AVEIRO-01");

        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));
        // Nenhum S livre
        when(compartmentRepo.findAvailableForUpdate(station, CompartmentState.FREE, Dimension.S))
                .thenReturn(Collections.emptyList());
        // Mas existe M livre
        when(compartmentRepo.findAvailableForUpdate(station, CompartmentState.FREE, Dimension.M))
                .thenReturn(List.of(compartmentM));

        CreateParcelResponse response = parcelService.createParcel(request);

        assertNotNull(response);
        assertEquals("B01", response.getCompartmentNumber());
        assertEquals(CompartmentState.RESERVED, compartmentM.getState());
        verify(compartmentRepo).save(compartmentM);
    }

    @Test
    @DisplayName("Rejeição: Estação cheia lança DomainRuleException (NO_LOCKERS_AVAILABLE)")
    void testCreateParcelFailsWhenStationFull() {
        CreateParcelRequest request = new CreateParcelRequest(Dimension.L, "+351912345678", "client@ua.pt",
                "STATION-AVEIRO-01");

        when(stationRepo.findByStationCode("STATION-AVEIRO-01")).thenReturn(Optional.of(station));
        when(compartmentRepo.findAvailableForUpdate(station, CompartmentState.FREE, Dimension.L))
                .thenReturn(Collections.emptyList());

        DomainRuleException ex = assertThrows(DomainRuleException.class, () -> parcelService.createParcel(request));

        assertEquals("NO_LOCKERS_AVAILABLE", ex.getErrorCode());

        verify(parcelRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rejeição: Estação inexistente lança DomainRuleException (STATION_NOT_FOUND)")
    void testCreateParcelStationNotFound() {
        CreateParcelRequest request = new CreateParcelRequest(Dimension.S, "+351912345678", "client@ua.pt",
                "UNKNOWN-STATION");

        when(stationRepo.findByStationCode("UNKNOWN-STATION")).thenReturn(Optional.empty());

        DomainRuleException ex = assertThrows(DomainRuleException.class, () -> parcelService.createParcel(request));

        assertEquals("STATION_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    @DisplayName("Consulta: Obter detalhes da encomenda por trackingCode")
    void testGetParcelByTrackingCode() {
        Parcel parcel = new Parcel("TB-2026-TEST01", Dimension.S, "+351912345678", "client@ua.pt",
                "hash123", now.plusSeconds(48 * 3600), station, compartmentS);

        when(parcelRepo.findByTrackingCode("TB-2026-TEST01")).thenReturn(Optional.of(parcel));

        ParcelDetailResponse response = parcelService.getParcelByTrackingCode("TB-2026-TEST01");

        assertNotNull(response);
        assertEquals("TB-2026-TEST01", response.getTrackingCode());
        assertEquals(ParcelState.CREATED, response.getState());
        assertEquals("A01", response.getCompartmentNumber());
    }

    @Test
    @DisplayName("Cancelamento: Parceiro cancela encomenda e liberta cacifo")
    void testCancelParcelByPartner() {
        compartmentS.setState(CompartmentState.RESERVED);
        Parcel parcel = new Parcel("TB-2026-CANCEL01", Dimension.S, "+351912345678", "client@ua.pt",
                "hash123", now.plusSeconds(48 * 3600), station, compartmentS);

        when(parcelRepo.findByTrackingCode("TB-2026-CANCEL01")).thenReturn(Optional.of(parcel));

        ParcelDetailResponse response = parcelService.cancelParcel("TB-2026-CANCEL01");

        assertNotNull(response);
        assertEquals(ParcelState.CANCELLED, response.getState());
        assertEquals(CompartmentState.FREE, compartmentS.getState());
        assertNull(parcel.getCompartment());
        verify(parcelRepo).save(parcel);
    }

    @Test
    @DisplayName("Segurança do PIN: Hash SHA-256 é determinístico e tem 64 caracteres hexadecimais")
    void testPinHashing() {
        String hash1 = ParcelService.hashPin("123456");
        String hash2 = ParcelService.hashPin("123456");
        String hash3 = ParcelService.hashPin("654321");

        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
    }
}
