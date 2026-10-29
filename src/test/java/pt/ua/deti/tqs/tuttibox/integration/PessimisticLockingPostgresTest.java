package pt.ua.deti.tqs.tuttibox.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pt.ua.deti.tqs.tuttibox.core.model.Compartment;
import pt.ua.deti.tqs.tuttibox.core.model.Dimension;
import pt.ua.deti.tqs.tuttibox.core.model.LockerStation;
import pt.ua.deti.tqs.tuttibox.core.service.DomainRuleException;
import pt.ua.deti.tqs.tuttibox.core.service.ParcelService;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto.CreateParcelRequest;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.CompartmentRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.LockerStationRepository;
import pt.ua.deti.tqs.tuttibox.infrastructure.adapters.out.persistence.ParcelRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public class PessimisticLockingPostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tuttibox_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private LockerStationRepository stationRepo;

    @Autowired
    private CompartmentRepository compartmentRepo;

    @Autowired
    private ParcelRepository parcelRepo;

    @BeforeEach
    void setupTestData() {
        parcelRepo.deleteAll();
        compartmentRepo.deleteAll();
        stationRepo.deleteAll();

        LockerStation station = stationRepo.save(
                new LockerStation("STATION-CONCURRENCY-01", "Aveiro Concurrency Hub", "Avenida Central", "Aveiro", 40.64, -8.64)
        );

        // Only 1 Dimension.S compartment available!
        compartmentRepo.save(new Compartment(station, "C01", Dimension.S));
    }

    @Test
    @DisplayName("Pessimistic locking prevents double booking when multiple concurrent requests compete for the last free compartment")
    void testConcurrentCompartmentAllocationWithPessimisticLocking() throws InterruptedException {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously
                    CreateParcelRequest req = new CreateParcelRequest(
                            Dimension.S,
                            "+35191000000" + id,
                            "user" + id + "@example.com",
                            "STATION-CONCURRENCY-01"
                    );
                    parcelService.createParcel(req);
                    successCount.incrementAndGet();
                } catch (DomainRuleException ex) {
                    if ("NO_LOCKERS_AVAILABLE".equals(ex.getErrorCode())) {
                        rejectedCount.incrementAndGet();
                    } else {
                        unexpectedErrors.add(ex);
                    }
                } catch (Exception ex) {
                    unexpectedErrors.add(ex);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire!

        executor.shutdown();
        assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS), "Threads must finish within timeout");

        assertTrue(unexpectedErrors.isEmpty(), "No unexpected errors occurred: " + unexpectedErrors);
        assertEquals(1, successCount.get(), "Exactly one request should successfully allocate the single free compartment");
        assertEquals(threadCount - 1, rejectedCount.get(), "All other concurrent requests must be rejected with NO_LOCKERS_AVAILABLE");
    }
}
