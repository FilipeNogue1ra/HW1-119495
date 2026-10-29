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
import pt.ua.deti.tqs.tuttibox.core.model.Dimension;
import pt.ua.deti.tqs.tuttibox.core.model.ParcelState;
import pt.ua.deti.tqs.tuttibox.core.service.DomainRuleException;
import pt.ua.deti.tqs.tuttibox.core.service.ParcelService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.ParcelDetailResponse;

import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ParcelController.class)
@Import(GlobalExceptionHandler.class)
class ParcelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ParcelService parcelService;

    @Test
    @DisplayName("POST /api/v1/parcels -> 201 Created com Location header")
    void testCreateParcelSuccess() throws Exception {
        CreateParcelRequest request = new CreateParcelRequest(
                Dimension.S,
                "+351912345678",
                "client@ua.pt",
                "STATION-AVEIRO-01"
        );

        CreateParcelResponse response = new CreateParcelResponse(
                "TB-2026-ABCD1234",
                "839201",
                Dimension.S,
                "STATION-AVEIRO-01",
                "Estação CP Aveiro",
                "A01",
                ParcelState.CREATED,
                Instant.parse("2026-10-31T10:00:00Z")
        );

        when(parcelService.createParcel(any(CreateParcelRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.trackingCode").value("TB-2026-ABCD1234"))
                .andExpect(jsonPath("$.pickupPin").value("839201"))
                .andExpect(jsonPath("$.compartmentNumber").value("A01"))
                .andExpect(jsonPath("$.state").value("CREATED"));
    }

    @Test
    @DisplayName("POST /api/v1/parcels -> 400 Bad Request quando campos obrigatórios estão em falta")
    void testCreateParcelValidationFailure() throws Exception {
        CreateParcelRequest invalidRequest = new CreateParcelRequest(
                null, // Missing dimension
                "invalid-phone",
                "not-an-email",
                ""
        );

        mockMvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.invalidFields").exists());
    }

    @Test
    @DisplayName("POST /api/v1/parcels -> 409 Conflict quando não há cacifos disponíveis (RFC 7807)")
    void testCreateParcelNoLockersAvailableConflict() throws Exception {
        CreateParcelRequest request = new CreateParcelRequest(
                Dimension.L,
                "+351912345678",
                "client@ua.pt",
                "STATION-AVEIRO-01"
        );

        when(parcelService.createParcel(any(CreateParcelRequest.class)))
                .thenThrow(new DomainRuleException("NO_LOCKERS_AVAILABLE", "Station is fully occupied"));

        mockMvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("No Lockers Available"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value("NO_LOCKERS_AVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/v1/parcels/{trackingCode} -> 200 OK com detalhes da encomenda")
    void testGetParcelSuccess() throws Exception {
        ParcelDetailResponse detail = new ParcelDetailResponse(
                "TB-2026-ABCD1234",
                Dimension.S,
                ParcelState.CREATED,
                "STATION-AVEIRO-01",
                "Estação CP Aveiro",
                "A01",
                Instant.parse("2026-10-31T10:00:00Z"),
                null,
                Collections.emptyList()
        );

        when(parcelService.getParcelByTrackingCode("TB-2026-ABCD1234")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/parcels/TB-2026-ABCD1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value("TB-2026-ABCD1234"))
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andExpect(jsonPath("$.compartmentNumber").value("A01"));
    }

    @Test
    @DisplayName("GET /api/v1/parcels/{trackingCode} -> 404 Not Found quando não existe")
    void testGetParcelNotFound() throws Exception {
        when(parcelService.getParcelByTrackingCode("TB-UNKNOWN"))
                .thenThrow(new DomainRuleException("PARCEL_NOT_FOUND", "Parcel not found"));

        mockMvc.perform(get("/api/v1/parcels/TB-UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Parcel Not Found"));
    }

    @Test
    @DisplayName("POST /api/v1/parcels/{trackingCode}/cancel -> 200 OK")
    void testCancelParcelSuccess() throws Exception {
        ParcelDetailResponse cancelled = new ParcelDetailResponse(
                "TB-2026-CANCEL",
                Dimension.S,
                ParcelState.CANCELLED,
                "STATION-AVEIRO-01",
                "Estação CP Aveiro",
                null,
                Instant.parse("2026-10-31T10:00:00Z"),
                null,
                Collections.emptyList()
        );

        when(parcelService.cancelParcel("TB-2026-CANCEL")).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/parcels/TB-2026-CANCEL/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CANCELLED"));
    }
}
