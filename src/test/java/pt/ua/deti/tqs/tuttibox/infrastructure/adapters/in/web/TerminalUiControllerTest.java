package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.service.TerminalService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.TerminalStatusResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TerminalUiController.class)
@Import(GlobalExceptionHandler.class)
class TerminalUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LockerStationRepository stationRepo;

    @MockBean
    private TerminalService terminalService;

    @Test
    @DisplayName("GET /terminal -> 200 OK com vista terminal e estações no modelo")
    void testGetTerminalViewDefaultStation() throws Exception {
        LockerStation s1 = new LockerStation("STATION-AVEIRO-01", "Estação CP Aveiro", "Largo da Estação", "Aveiro", 40.6433, -8.6406);
        when(stationRepo.findAll()).thenReturn(List.of(s1));
        when(terminalService.getTerminalStatus("STATION-AVEIRO-01"))
                .thenReturn(new TerminalStatusResponse("STATION-AVEIRO-01", false, 0, 0));

        mockMvc.perform(get("/terminal"))
                .andExpect(status().isOk())
                .andExpect(view().name("terminal"))
                .andExpect(model().attributeExists("stations"))
                .andExpect(model().attributeExists("selectedStation"))
                .andExpect(model().attributeExists("terminalStatus"));
    }

    @Test
    @DisplayName("GET /terminal?station=STATION-AVEIRO-02 -> seleciona a estação indicada")
    void testGetTerminalViewSpecificStation() throws Exception {
        LockerStation s1 = new LockerStation("STATION-AVEIRO-01", "Estação CP Aveiro", "Largo da Estação", "Aveiro", 40.6433, -8.6406);
        LockerStation s2 = new LockerStation("STATION-AVEIRO-02", "Universidade de Aveiro", "Campus", "Aveiro", 40.6306, -8.6575);
        when(stationRepo.findAll()).thenReturn(List.of(s1, s2));
        when(terminalService.getTerminalStatus("STATION-AVEIRO-02"))
                .thenReturn(new TerminalStatusResponse("STATION-AVEIRO-02", false, 0, 0));

        mockMvc.perform(get("/terminal").param("station", "STATION-AVEIRO-02"))
                .andExpect(status().isOk())
                .andExpect(view().name("terminal"))
                .andExpect(model().attribute("selectedStation", s2));
    }
}
