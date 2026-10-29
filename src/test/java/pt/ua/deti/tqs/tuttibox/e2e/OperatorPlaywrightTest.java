package pt.ua.deti.tqs.tuttibox.e2e;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import pt.ua.deti.tqs.tuttibox.core.model.Dimension;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.model.Compartment;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.CompartmentRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class OperatorPlaywrightTest {

    @LocalServerPort
    private int port;

    @Autowired
    private LockerStationRepository stationRepo;

    @Autowired
    private CompartmentRepository compartmentRepo;

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
    void setupData() {
        org.junit.jupiter.api.Assumptions.assumeTrue(browser != null, "Playwright browser is unavailable in this environment; skipping E2E browser tests.");
        context = browser.newContext();
        page = context.newPage();

        LockerStation station = stationRepo.findByStationCode("STATION-AVEIRO-01").orElseGet(() ->
                stationRepo.save(new LockerStation("STATION-AVEIRO-01", "Estação CP Aveiro", "Largo da Estação", "Aveiro", 40.643, -8.646))
        );

        if (compartmentRepo.findByStation(station).isEmpty()) {
            compartmentRepo.save(new Compartment(station, "A01", Dimension.S));
            compartmentRepo.save(new Compartment(station, "A02", Dimension.M));
        }
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void testOperatorNavigationAndStationInspection() {
        page.navigate("http://localhost:" + port + "/operator");

        // Verify operator title
        assertTrue(page.title().contains("TuttiBox"));

        // Wait for station cards to render via AJAX fetch
        page.waitForSelector(".station-card");
        assertTrue(page.locator(".station-card").count() > 0);

        // Click first station card to inspect compartments
        page.locator(".station-card").first().click();

        // Verify lockers view is displayed
        page.waitForSelector("#lockers-view");
        assertTrue(page.locator("#lockers-view").isVisible());

        // Verify locker compartments are listed
        page.waitForSelector(".locker-card");
        assertTrue(page.locator(".locker-card").count() >= 2);
        assertTrue(page.locator(".locker-number").first().innerText().length() > 0);
    }
}
