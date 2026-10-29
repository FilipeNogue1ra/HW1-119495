package pt.ua.deti.tqs.tuttibox.acceptance;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.service.DomainRuleException;
import pt.ua.deti.tqs.tuttibox.core.service.TerminalService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.PickupRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.TerminalStatusResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityLockoutSteps {

    @Autowired
    private LockerStationRepository stationRepo;

    @Autowired
    private TerminalService terminalService;

    private DomainRuleException lastException;

    @Given("the terminal at station {string} is operational with 0 failed attempts")
    public void theTerminalIsOperational(String stationCode) {
        LockerStation s = stationRepo.findByStationCode(stationCode)
                .orElseGet(() -> stationRepo.save(new LockerStation(stationCode, "Test Station", "Rua Teste", "Aveiro", 40.64, -8.64)));
        s.resetFailedPins();
        stationRepo.save(s);
    }

    @When("a user enters an incorrect PIN {string} at station {string}")
    public void aUserEntersAnIncorrectPin(String pin, String stationCode) {
        lastException = null;
        try {
            terminalService.pickupParcel(stationCode, new PickupRequest(null, pin));
        } catch (DomainRuleException ex) {
            lastException = ex;
        }
    }

    @Then("the terminal should report {int} failed attempt and remain unlocked")
    @Then("the terminal should report {int} failed attempts and remain unlocked")
    public void theTerminalShouldReportFailedAttempts(int count) {
        TerminalStatusResponse status = terminalService.getTerminalStatus("STATION-AVEIRO-01");
        assertEquals(count, status.consecutiveFailedPins());
        assertFalse(status.locked());
    }

    @Then("the terminal should become locked for 60 seconds")
    public void theTerminalShouldBecomeLocked() {
        assertNotNull(lastException);
        assertEquals("TERMINAL_LOCKED", lastException.getErrorCode());
        TerminalStatusResponse status = terminalService.getTerminalStatus("STATION-AVEIRO-01");
        assertTrue(status.locked());
        assertTrue(status.remainingLockoutSeconds() > 0 && status.remainingLockoutSeconds() <= 60);
    }

    @And("any subsequent PIN entry during lockout should be rejected with a lockout error")
    public void anySubsequentPinEntryDuringLockoutShouldBeRejected() {
        DomainRuleException ex = assertThrows(DomainRuleException.class, () ->
                terminalService.pickupParcel("STATION-AVEIRO-01", new PickupRequest(null, "842910"))
        );
        assertEquals("TERMINAL_LOCKED", ex.getErrorCode());
    }
}
