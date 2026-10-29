package pt.ua.deti.tqs.tuttibox.acceptance;

import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import pt.ua.deti.tqs.tuttibox.core.model.*;
import pt.ua.deti.tqs.tuttibox.core.ports.out.LockerHardwarePort;
import pt.ua.deti.tqs.tuttibox.core.service.ParcelService;
import pt.ua.deti.tqs.tuttibox.core.service.TerminalService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.PickupRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.PickupResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.CompartmentRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.ParcelRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ParcelPickupSteps {

    @Autowired
    private LockerStationRepository stationRepo;

    @Autowired
    private CompartmentRepository compartmentRepo;

    @Autowired
    private ParcelRepository parcelRepo;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private LockerHardwarePort hardwarePort;

    private PickupResponse lastPickupResponse;
    private String lastTrackingCode;
    private String lastStationCode;

    @Before
    public void cleanUp() {
        parcelRepo.deleteAll();
        stationRepo.findAll().forEach(s -> {
            s.resetFailedPins();
            stationRepo.save(s);
        });
    }

    @Given("a parcel with tracking code {string} is ready for pickup at station {string} in compartment {string}")
    public void aParcelWithTrackingCodeIsReadyForPickup(String trackingCode, String stationCode, String compartmentNum) {
        lastTrackingCode = trackingCode;
        lastStationCode = stationCode;

        LockerStation station = stationRepo.findByStationCode(stationCode)
                .orElseGet(() -> stationRepo.save(new LockerStation(stationCode, "Test Station", "Rua Teste", "Aveiro", 40.64, -8.64)));

        Compartment comp = compartmentRepo.findByStationAndCompartmentNumber(station, compartmentNum)
                .orElseGet(() -> {
                    Compartment c = new Compartment(station, compartmentNum, Dimension.S);
                    return compartmentRepo.save(c);
                });
        comp.setState(CompartmentState.OCCUPIED);
        compartmentRepo.save(comp);

        String pinHash = ParcelService.hashPin("842910");
        Parcel p = parcelRepo.findByTrackingCode(trackingCode)
                .orElseGet(() -> new Parcel(
                        trackingCode,
                        Dimension.S,
                        "+351912345678",
                        "client@ua.pt",
                        pinHash,
                        Instant.now().plus(48, ChronoUnit.HOURS),
                        station,
                        comp
                ));
        p.setState(ParcelState.READY_FOR_PICKUP);
        p.setPickupPinHash(pinHash);
        p.setPickupDeadline(Instant.now().plus(72, ChronoUnit.HOURS));
        parcelRepo.save(p);
    }

    @And("the customer has received the valid pickup PIN {string}")
    public void theCustomerHasReceivedValidPin(String pin) {
        assertNotNull(pin);
    }

    @When("the customer enters PIN {string} at station {string}")
    public void theCustomerEntersPinAtStation(String pin, String stationCode) {
        PickupRequest req = new PickupRequest(null, pin);
        lastPickupResponse = terminalService.pickupParcel(stationCode, req);
    }

    @Then("the compartment door {string} should unlock and open")
    public void theCompartmentDoorShouldUnlockAndOpen(String compartmentNum) {
        assertNotNull(lastPickupResponse);
        assertEquals(compartmentNum, lastPickupResponse.compartmentNumber());
    }

    @And("when the customer closes compartment door {string}")
    public void whenTheCustomerClosesCompartmentDoor(String compartmentNum) {
        hardwarePort.closeDoor(lastStationCode, compartmentNum);
    }

    @Then("the parcel state should be {string}")
    public void theParcelStateShouldBe(String expectedState) {
        Parcel p = parcelRepo.findByTrackingCode(lastTrackingCode).orElseThrow();
        assertEquals(ParcelState.valueOf(expectedState), p.getState());
    }

    @And("the compartment {string} should be free for new shipments")
    public void theCompartmentShouldBeFree(String compartmentNum) {
        LockerStation s = stationRepo.findByStationCode(lastStationCode).orElseThrow();
        Compartment c = compartmentRepo.findByStationAndCompartmentNumber(s, compartmentNum).orElseThrow();
        assertEquals(CompartmentState.FREE, c.getState());
    }
}
