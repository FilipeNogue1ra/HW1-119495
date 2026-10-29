package pt.ua.deti.tqs.tuttibox.acceptance;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import pt.ua.deti.tqs.tuttibox.core.model.*;
import pt.ua.deti.tqs.tuttibox.core.ports.out.LockerHardwarePort;
import pt.ua.deti.tqs.tuttibox.core.service.DomainRuleException;
import pt.ua.deti.tqs.tuttibox.core.service.TerminalService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.PickupRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.hardware.InMemoryHardwareFake;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.CompartmentRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.ParcelRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HardwareFailureSteps {

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

    private DomainRuleException lastException;
    private String lastTrackingCode;
    private String lastStationCode;
    private String lastCompartmentNum;

    @And("the locker hardware has a simulated solenoid jam on compartment {string}")
    public void theLockerHardwareHasASimulatedSolenoidJam(String compartmentNum) {
        lastCompartmentNum = compartmentNum;
        if (hardwarePort instanceof InMemoryHardwareFake fake) {
            fake.simulateDoorJam("STATION-AVEIRO-01", compartmentNum, true);
        }
    }

    @When("the customer enters PIN {string} for parcel {string} at station {string}")
    public void theCustomerEntersPinForParcelAtStation(String pin, String trackingCode, String stationCode) {
        lastTrackingCode = trackingCode;
        lastStationCode = stationCode;
        lastException = null;

        try {
            terminalService.pickupParcel(stationCode, new PickupRequest(trackingCode, pin));
        } catch (DomainRuleException ex) {
            lastException = ex;
        } finally {
            // reset hardware fake jam
            if (hardwarePort instanceof InMemoryHardwareFake fake) {
                fake.simulateDoorJam(stationCode, lastCompartmentNum, false);
            }
        }
    }

    @Then("the system should detect the jammed door")
    public void theSystemShouldDetectTheJammedDoor() {
        assertNotNull(lastException);
        assertEquals("DOOR_JAMMED", lastException.getErrorCode());
    }

    @And("the parcel should transition to state {string}")
    public void theParcelShouldTransitionToState(String expectedState) {
        Parcel p = parcelRepo.findByTrackingCode(lastTrackingCode).orElseThrow();
        assertEquals(ParcelState.valueOf(expectedState), p.getState());
    }

    @And("compartment {string} should be placed {string}")
    public void compartmentShouldBePlaced(String compartmentNum, String expectedState) {
        LockerStation s = stationRepo.findByStationCode(lastStationCode).orElseThrow();
        Compartment c = compartmentRepo.findByStationAndCompartmentNumber(s, compartmentNum).orElseThrow();
        assertEquals(CompartmentState.valueOf(expectedState), c.getState());
    }
}
