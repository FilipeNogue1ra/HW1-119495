package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.deti.tqs.tuttibox.core.model.ParcelState;
import pt.ua.deti.tqs.tuttibox.core.service.DomainRuleException;
import pt.ua.deti.tqs.tuttibox.core.service.TerminalService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.DropoffRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.DropoffResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.PickupRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.PickupResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.TerminalStatusResponse;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TerminalController.class)
@Import(GlobalExceptionHandler.class)
class TerminalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TerminalService terminalService;

    @Test
    @DisplayName("POST /api/v1/terminals/{station}/dropoff -> 200 OK com sucesso no depósito")
    void testCourierDropoffSuccess() throws Exception {
        DropoffRequest request = new DropoffRequest("TB-2026-ABCD1234");
        DropoffResponse response = new DropoffResponse(
                "TB-2026-ABCD1234",
                "STATION-AVEIRO-01",
                "A01",
                ParcelState.READY_FOR_PICKUP,
                Instant.parse("2026-11-02T10:00:00Z"),
                "Parcel deposited successfully."
        );

        when(terminalService.dropoffParcel(eq("STATION-AVEIRO-01"), any(DropoffRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/terminals/STATION-AVEIRO-01/dropoff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value("TB-2026-ABCD1234"))
                .andExpect(jsonPath("$.compartmentNumber").value("A01"))
                .andExpect(jsonPath("$.state").value("READY_FOR_PICKUP"));
    }

    @Test
    @DisplayName("POST /api/v1/terminals/{station}/dropoff -> 400 Bad Request se trackingCode estiver vazio")
    void testCourierDropoffValidationFailure() throws Exception {
        DropoffRequest badRequest = new DropoffRequest("");

        mockMvc.perform(post("/api/v1/terminals/STATION-AVEIRO-01/dropoff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields").exists());
    }

    @Test
    @DisplayName("POST /api/v1/terminals/{station}/pickup -> 200 OK com levantamento bem-sucedido")
    void testRecipientPickupSuccess() throws Exception {
        PickupRequest request = new PickupRequest("TB-2026-ABCD1234", "123456");
        PickupResponse response = new PickupResponse(
                "TB-2026-ABCD1234",
                "STATION-AVEIRO-01",
                "A01",
                ParcelState.PICKED_UP,
                "Parcel collected successfully."
        );

        when(terminalService.pickupParcel(eq("STATION-AVEIRO-01"), any(PickupRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/terminals/STATION-AVEIRO-01/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value("TB-2026-ABCD1234"))
                .andExpect(jsonPath("$.compartmentNumber").value("A01"))
                .andExpect(jsonPath("$.state").value("PICKED_UP"));
    }

    @Test
    @DisplayName("POST /api/v1/terminals/{station}/pickup -> 401 Unauthorized com PIN incorreto")
    void testRecipientPickupInvalidPin() throws Exception {
        PickupRequest request = new PickupRequest(null, "999999");

        when(terminalService.pickupParcel(eq("STATION-AVEIRO-01"), any(PickupRequest.class)))
                .thenThrow(new DomainRuleException("INVALID_PIN", "Invalid PIN. Remaining attempts: 2"));

        mockMvc.perform(post("/api/v1/terminals/STATION-AVEIRO-01/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid Pickup PIN"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("INVALID_PIN"));
    }

    @Test
    @DisplayName("POST /api/v1/terminals/{station}/pickup -> 429 Too Many Requests quando terminal bloqueado")
    void testRecipientPickupTerminalLocked() throws Exception {
        PickupRequest request = new PickupRequest(null, "123456");

        when(terminalService.pickupParcel(eq("STATION-AVEIRO-01"), any(PickupRequest.class)))
                .thenThrow(new DomainRuleException("TERMINAL_LOCKED", "Terminal locked for 60 seconds"));

        mockMvc.perform(post("/api/v1/terminals/STATION-AVEIRO-01/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Terminal Lockout Active"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.errorCode").value("TERMINAL_LOCKED"));
    }

    @Test
    @DisplayName("POST /api/v1/terminals/{station}/pickup -> 500 Internal Server Error com porta encravada")
    void testRecipientPickupDoorJammed() throws Exception {
        PickupRequest request = new PickupRequest(null, "123456");

        when(terminalService.pickupParcel(eq("STATION-AVEIRO-01"), any(PickupRequest.class)))
                .thenThrow(new DomainRuleException("DOOR_JAMMED", "Locker door jammed"));

        mockMvc.perform(post("/api/v1/terminals/STATION-AVEIRO-01/pickup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Compartment Door Jammed"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.errorCode").value("DOOR_JAMMED"));
    }

    @Test
    @DisplayName("GET /api/v1/terminals/{station}/status -> 200 OK")
    void testGetStatus() throws Exception {
        TerminalStatusResponse status = new TerminalStatusResponse("STATION-AVEIRO-01", false, 0, 0);
        when(terminalService.getTerminalStatus("STATION-AVEIRO-01")).thenReturn(status);

        mockMvc.perform(get("/api/v1/terminals/STATION-AVEIRO-01/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationCode").value("STATION-AVEIRO-01"))
                .andExpect(jsonPath("$.locked").value(false));
    }
}
