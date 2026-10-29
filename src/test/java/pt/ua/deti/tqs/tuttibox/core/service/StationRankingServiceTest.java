package pt.ua.deti.tqs.tuttibox.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.geocoding.InMemoryGeocodingFake;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StationRankingServiceTest {

    private InMemoryGeocodingFake geocodingFake;
    private StationRankingService rankingService;
    private List<LockerStation> stations;

    @BeforeEach
    void setUp() {
        geocodingFake = new InMemoryGeocodingFake();
        rankingService = new StationRankingService(geocodingFake);

        LockerStation s1 = new LockerStation("STATION-AVEIRO-01", "Estação CP Aveiro", "Largo da Estação", "Aveiro", 40.6433, -8.6406);
        LockerStation s2 = new LockerStation("STATION-AVEIRO-02", "Universidade de Aveiro", "Campus Universitário de Santiago", "Aveiro", 40.6306, -8.6575);
        LockerStation s3 = new LockerStation("STATION-AVEIRO-03", "Fórum Aveiro", "Rua Batalhão de Caçadores 10", "Aveiro", 40.6412, -8.6515);
        LockerStation s4 = new LockerStation("STATION-AVEIRO-04", "Hospital Infante D. Pedro", "Avenida Artur Ravara", "Aveiro", 40.6358, -8.6472);

        stations = List.of(s1, s2, s3, s4);
    }

    @Test
    @DisplayName("Haversine: cálculo de distância matemática entre dois pontos GPS conhecidos")
    void testHaversineFormulaAccuracy() {
        // Distância entre Estação CP (40.6433, -8.6406) e Campus UA (40.6306, -8.6575)
        double distance = rankingService.calculateDistanceKm(40.6433, -8.6406, 40.6306, -8.6575);

        // A distância real em linha reta em Aveiro é de aproximadamente 2.01 km
        assertTrue(distance >= 1.9 && distance <= 2.1, "A distância calculada deve ser de ~2.01 km, obtido: " + distance);
    }

    @Test
    @DisplayName("Haversine: distância entre um ponto e ele próprio é 0.0 km")
    void testHaversineSameLocationIsZero() {
        double distance = rankingService.calculateDistanceKm(40.6433, -8.6406, 40.6433, -8.6406);
        assertEquals(0.0, distance, "Distância entre o mesmo ponto deve ser zero.");
    }

    @Test
    @DisplayName("UC1: Ordenação de estações por proximidade da Universidade de Aveiro")
    void testRankStationsNearUniversity() {
        List<StationRankingService.StationDistance> ranked = rankingService.rankStationsByAddress("Campus de Santiago, Aveiro", stations);

        assertNotNull(ranked);
        assertEquals(4, ranked.size());

        // A primeira estação tem de ser a Universidade (distância 0.0 km)
        assertEquals("STATION-AVEIRO-02", ranked.get(0).station().getStationCode());
        assertEquals(0.0, ranked.get(0).distanceKm());

        // As distâncias devem estar estritamente em ordem crescente
        for (int i = 0; i < ranked.size() - 1; i++) {
            assertTrue(ranked.get(i).distanceKm() <= ranked.get(i + 1).distanceKm(),
                    "A lista de estações deve estar ordenada por proximidade crescente.");
        }
    }

    @Test
    @DisplayName("UC1: Ordenação de estações por proximidade da Estação CP")
    void testRankStationsNearTrainStation() {
        List<StationRankingService.StationDistance> ranked = rankingService.rankStationsByAddress("Largo da Estação, Aveiro", stations);

        assertNotNull(ranked);
        assertEquals("STATION-AVEIRO-01", ranked.get(0).station().getStationCode());
        assertEquals(0.0, ranked.get(0).distanceKm());
    }

    @Test
    @DisplayName("Falha: Morada desconhecida lança DomainRuleException (GEOCODING_NOT_FOUND)")
    void testUnknownAddressThrowsDomainRuleException() {
        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                rankingService.rankStationsByAddress("Rua Inexistente Num Lugar Distante", stations)
        );
        assertEquals("GEOCODING_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    @DisplayName("Injeção de Falha: Serviço externo indisponível lança DomainRuleException")
    void testGeocodingProviderUnavailable() {
        geocodingFake.setSimulateUnavailable(true);

        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                rankingService.rankStationsByAddress("Largo da Estação", stations)
        );
        assertEquals("GEOCODING_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    @DisplayName("Ordenação de estações por coordenadas GPS diretas")
    void testRankStationsByCoordinates() {
        // Coordenadas da Estação CP Aveiro
        List<StationRankingService.StationDistance> ranked = rankingService.rankStationsByCoordinates(40.6433, -8.6406, stations);

        assertNotNull(ranked);
        assertEquals(4, ranked.size());
        assertEquals("STATION-AVEIRO-01", ranked.get(0).station().getStationCode());
        assertEquals(0.0, ranked.get(0).distanceKm());
        assertTrue(ranked.get(0).distanceKm() <= ranked.get(1).distanceKm());
    }
}
