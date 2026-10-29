package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.deti.tqs.tuttibox.core.model.Compartment;
import pt.ua.deti.tqs.tuttibox.core.model.CompartmentState;
import pt.ua.deti.tqs.tuttibox.core.model.Dimension;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.service.StationRankingService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StationController.class)
@Import(GlobalExceptionHandler.class)
class StationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LockerStationRepository stationRepo;

    @MockBean
    private StationRankingService stationRankingService;

    @Test
    @DisplayName("GET /api/v1/stations -> 200 OK com lista de estações")
    void testGetAllStations() throws Exception {
        LockerStation s1 = new LockerStation("STATION-AVEIRO-01", "Estação CP Aveiro", "Largo da Estação", "Aveiro", 40.6433, -8.6406);
        Compartment c1 = new Compartment(s1, "A01", Dimension.S);
        c1.setState(CompartmentState.FREE);
        s1.getCompartments().add(c1);

        when(stationRepo.findAll()).thenReturn(List.of(s1));

        mockMvc.perform(get("/api/v1/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stationCode").value("STATION-AVEIRO-01"))
                .andExpect(jsonPath("$[0].totalCompartments").value(1))
                .andExpect(jsonPath("$[0].availableCompartments").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/stations/nearby?address=Campus -> 200 OK com estações ordenadas por distância")
    void testGetNearbyStationsByAddress() throws Exception {
        LockerStation s1 = new LockerStation("STATION-AVEIRO-02", "Universidade de Aveiro", "Campus Santiago", "Aveiro", 40.6306, -8.6575);
        when(stationRepo.findAll()).thenReturn(List.of(s1));
        when(stationRankingService.rankStationsByAddress(eq("Campus Santiago"), anyList()))
                .thenReturn(List.of(new StationRankingService.StationDistance(s1, 0.05)));

        mockMvc.perform(get("/api/v1/stations/nearby").param("address", "Campus Santiago"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stationCode").value("STATION-AVEIRO-02"))
                .andExpect(jsonPath("$[0].distanceKm").value(0.05));
    }

    @Test
    @DisplayName("GET /api/v1/stations/nearby?lat=40.64&lon=-8.64 -> 200 OK com GPS direto")
    void testGetNearbyStationsByCoordinates() throws Exception {
        LockerStation s1 = new LockerStation("STATION-AVEIRO-01", "Estação CP", "Largo da Estação", "Aveiro", 40.6433, -8.6406);
        when(stationRepo.findAll()).thenReturn(List.of(s1));
        when(stationRankingService.rankStationsByCoordinates(eq(40.64), eq(-8.64), anyList()))
                .thenReturn(List.of(new StationRankingService.StationDistance(s1, 0.12)));

        mockMvc.perform(get("/api/v1/stations/nearby")
                        .param("lat", "40.64")
                        .param("lon", "-8.64"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stationCode").value("STATION-AVEIRO-01"))
                .andExpect(jsonPath("$[0].distanceKm").value(0.12));
    }
}
