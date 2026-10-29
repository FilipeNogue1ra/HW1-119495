package pt.ua.deti.tqs.tuttibox.e2e;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import pt.ua.deti.tqs.tuttibox.core.model.Dimension;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.model.Parcel;
import pt.ua.deti.tqs.tuttibox.core.model.ParcelState;
import pt.ua.deti.tqs.tuttibox.core.service.ParcelService;
import pt.ua.deti.tqs.tuttibox.core.service.TerminalService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelResponse;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.DropoffRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.ParcelRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class TerminalPlaywrightTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private LockerStationRepository stationRepo;

    @Autowired
    private ParcelRepository parcelRepo;

    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void launchBrowser() {
        try {
            playwright = Playwright.create();
            try {
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setChannel("chrome")
                        .setHeadless(true));
            } catch (Exception e) {
                browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(true));
            }
        } catch (Exception e) {
            browser = null;
        }
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void createContextAndSetupData() {
        org.junit.jupiter.api.Assumptions.assumeTrue(browser != null, "Playwright browser is unavailable in this environment; skipping E2E browser tests.");
        context = browser.newContext();
        page = context.newPage();

        // Ensure station exists and reset lockout
        LockerStation s = stationRepo.findByStationCode("STATION-AVEIRO-01").orElseGet(() ->
                stationRepo.save(new LockerStation("STATION-AVEIRO-01", "Estação CP Aveiro", "Largo da Estação", "Aveiro", 40.643, -8.646))
        );
        s.resetFailedPins();
        stationRepo.save(s);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void testSuccessfulPickupViaKioskTouchpad() {
        // Prepare parcel for pickup
        parcelRepo.deleteAll();
        CreateParcelRequest req = new CreateParcelRequest(Dimension.S, "+351910000000", "ana@example.com", "STATION-AVEIRO-01");
        CreateParcelResponse resp = parcelService.createParcel(req);
        String pin = resp.getPickupPin();
        assertNotNull(pin, "Pickup PIN must be generated");

        // Courier drops off parcel so it is ready for customer pickup
        terminalService.dropoffParcel("STATION-AVEIRO-01", new DropoffRequest(resp.getTrackingCode()));

        // Navigate to the kiosk terminal
        page.navigate("http://localhost:" + port + "/terminal?station=STATION-AVEIRO-01");

        // Verify page loaded
        assertTrue(page.title().contains("TuttiBox"));

        // Enter 6-digit PIN by clicking touch keypad buttons
        for (char ch : pin.toCharArray()) {
            page.locator(".keypad-btn", new Page.LocatorOptions().setHasText(String.valueOf(ch))).click();
        }

        // Click OK / Enter
        page.locator("#btnEnter").click();

        // Verify door opened on UI simulator
        page.waitForSelector("#doorGraphic.open");
        String doorBadgeText = page.locator("#doorStatusBadge").innerText();
        assertTrue(doorBadgeText.contains("Aberto") || doorBadgeText.contains("ABERTO"), "Door status badge should indicate open state");

        // Click simulate close door button
        page.locator("#btnCloseDoor").click();

        // Verify door closed and feedback indicates completed pickup
        page.waitForSelector("#doorGraphic:not(.open)");
        String screenFeedback = page.locator("#screenFeedback").innerText();
        assertTrue(screenFeedback.contains("sucesso") || screenFeedback.contains("concluído") || screenFeedback.contains("Obrigado"),
                "Feedback should indicate success, got: " + screenFeedback);

        // Verify parcel transitioned to PICKED_UP in DB
        Parcel updated = parcelRepo.findByTrackingCode(resp.getTrackingCode()).orElseThrow();
        assertEquals(ParcelState.PICKED_UP, updated.getState());
    }

    @Test
    void testConsecutiveWrongPinsTriggersLockoutOverlay() {
        page.navigate("http://localhost:" + port + "/terminal?station=STATION-AVEIRO-01");

        // Wrong PIN 1: 000000
        for (int i = 0; i < 6; i++) {
            page.locator(".keypad-btn", new Page.LocatorOptions().setHasText("0")).click();
        }
        page.locator("#btnEnter").click();
        page.waitForTimeout(600);

        // Wrong PIN 2: 111111
        for (int i = 0; i < 6; i++) {
            page.locator(".keypad-btn", new Page.LocatorOptions().setHasText("1")).click();
        }
        page.locator("#btnEnter").click();
        page.waitForTimeout(600);

        // Wrong PIN 3: 222222 -> should trigger lockout
        for (int i = 0; i < 6; i++) {
            page.locator(".keypad-btn", new Page.LocatorOptions().setHasText("2")).click();
        }
        page.locator("#btnEnter").click();

        // Verify lockout overlay becomes active
        page.waitForSelector("#lockoutOverlay.active");
        assertTrue(page.locator("#lockoutOverlay").isVisible());
        assertTrue(page.locator(".lockout-title").innerText().contains("BLOQUEADO"));
    }
}
