# HW1: Mid-term assignment report
Filipe Nogueira [119495], v2026-10-29

---

## 1 Introduction

### 1.1 Overview of the work

This report presents the midterm individual project required for TQS, covering both the software product features and the adopted quality assurance strategy.

The developed platform, TuttiBox, is a full-stack smart parcel locker management application built with Spring Boot 3.4 / 4 and PostgreSQL that orchestrates the complete lifecycle of parcels across smart locker stations in Aveiro (sizes S, M, and L). It provides a REST API for e-commerce partners to allocate compartments, a web portal for recipients and operators to locate stations, track deliveries, and supervise operations, and a web-based locker terminal simulator for courier drop-offs and recipient pickups. External dependencies—namely locker hardware actuators, geolocation services, and recipient notifications—are decoupled through domain ports to enable deterministic unit, integration, BDD acceptance, and end-to-end testing.

### 1.2 Use of Generative AI
Google Antigravity (powered by Google DeepMind Gemini models) was utilized as an interactive AI pair programming assistant for architectural scaffolding, test generation (Cucumber Gherkin, Playwright, k6), and documentation structuring. 

* **Auditing and Verification**: All business logic, transaction isolation policies, state machine constraints, and security rules were strictly audited and verified by the author. When static code analysis detected a CWE-117 Log Injection vulnerability in the generated controller parameter handling, the flow was audited and manually resolved using deterministic string sanitization before logging.

---

## 2 Product specification

### 2.1 Functional scope and actors

TuttiBox serves four distinct actors interacting through dedicated Web pages, a simulated Locker Terminal touchscreen interface, and a REST API (without user authentication, mapping each actor to its own channel as specified):

• **Recipient (Consumer)**: Uses the Web portal to search for nearby locker stations by address (UC1), track parcel state history (UC5), or report locker issues via the Help feature, and uses the Locker Terminal to collect parcels with a single-use pickup code (UC4).  
• **Partner (Online Shop)**: Uses the REST API to register parcels for a chosen station, reserve a compatible compartment (UC2), cancel uncollected registrations, and query parcel tracking history (UC5).  
• **Courier**: Interacts with the Locker Terminal touchscreen to enter a parcel's tracking code, open the reserved compartment, deposit the parcel, and confirm door closure (UC3).  
• **Operator**: Uses the Web supervision backoffice (UC6) to monitor station occupancy, filter parcels by state, register the physical removal of expired parcels (EXPIRED to RETURNED), and place faulty compartments OUT_OF_SERVICE (or restore them to FREE with a mandatory maintenance description).

#### Tabela 2.1 (User Stories):

| ID | User story | Acceptance criteria | Acceptance test? |
| :--- | :--- | :--- | :--- |
| US1 (UC1) | As a recipient, I want to enter an address and see the nearest locker stations with their free compartments per size (S, M, L), so that I can choose a convenient station with available capacity. | • Valid address is geocoded and stations are listed ordered by ascending Haversine distance.<br>• Each station displays the exact count of FREE compartments for sizes S, M, and L (RESERVED, OCCUPIED, and OUT_OF_SERVICE are excluded).<br>• Unresolvable address or unavailable geocoding provider displays a clear error message without crashing. | Playwright |
| US2 (UC2) | As a partner, I want to register a parcel (station, size, recipient contact) via REST API, so that a compatible compartment is reserved and I get a tracking code. | • Given a station with at least one FREE compartment of the requested size (S, M, or L), When the partner registers a parcel, Then one compartment becomes RESERVED, the parcel is CREATED, and a unique trackingCode is returned (201 Created).<br>• Given a station with no FREE compartments of that size, When registration is requested, Then it is rejected with 409 Conflict.<br>• Concurrent requests for the last FREE compartment never double-book it. | Cucumber (API/Service) |
| US3 (UC3) | As a courier, I want to enter the tracking code at the locker terminal to open the compartment and confirm door closure, so that the parcel becomes ready for pickup and the recipient gets a pickup code. | • Given a CREATED parcel within the 48h drop-off deadline, When the courier enters its trackingCode, Then the reserved compartment door opens.<br>• Given the compartment door was opened, When the courier closes the door, Then the parcel transitions to READY_FOR_PICKUP, the compartment becomes OCCUPIED, and a 6-digit pickupCode is sent to the recipient.<br>• Given the 48h drop-off deadline has passed, When the courier enters the trackingCode, Then the parcel transitions to CANCELLED, the compartment is freed (FREE), and drop-off is refused.<br>• Given the compartment door is jammed on open, When the courier enters the trackingCode, Then the compartment is marked OUT_OF_SERVICE and the parcel remains CREATED. | Cucumber & Playwright |
| US4 (UC4) | As a recipient, I want to enter my pickup code at the locker terminal (or report an issue via Help), so that the compartment opens and I can collect my parcel securely. | • Given a READY_FOR_PICKUP parcel within the 72h pickup deadline, When the recipient enters the valid 6-digit pickupCode, Then the compartment opens, the parcel becomes PICKED_UP, and the compartment becomes FREE.<br>• Given a pickupCode was already used once, When entered again, Then no door opens and an invalid code error is shown.<br>• Given more than 72h have elapsed since drop-off, When the recipient enters the pickupCode, Then the parcel is marked EXPIRED and the door does not open.<br>• Given 3 wrong pickup codes are entered consecutively on a terminal, When another code is entered within 1 minute, Then the terminal refuses the attempt (429 Too Many Requests).<br>• Given the compartment door fails to open during pickup, When the valid pickupCode is entered, Then the compartment becomes OUT_OF_SERVICE and the parcel transitions to WAITING_FOR_OPERATOR.<br>• If a user submits a trackingCode in Help: if parcel is READY_FOR_PICKUP, EXPIRED, or WAITING_FOR_OPERATOR, the compartment becomes OUT_OF_SERVICE; if CREATED, user is advised to wait a few days. | Cucumber & Playwright |
| US5 (UC5) | As a recipient or partner, I want to look up a parcel by its tracking code on the Web or REST API, so that I can inspect its current state and timestamped transition history. | • Entering a valid trackingCode returns the current ParcelState, station name, size, and chronological list of transitions (each with state, timestamp, and actor).<br>• Entering an unknown trackingCode returns 404 Not Found (API) or a clear "Parcel not found" message (Web). | Playwright |
| US6 (UC6) | As an operator, I want to supervise stations on the Web by listing parcels by state, registering the removal of expired parcels, and managing faulty compartments, so that locker stations remain operational. | • Operator can filter parcels by state (e.g., EXPIRED) and trigger removal, transitioning the parcel from EXPIRED to RETURNED and freeing the compartment (FREE).<br>• Operator can mark a compartment as OUT_OF_SERVICE or restore it to FREE only by providing a mandatory maintenance description (maintenanceNote), recording timestamp and OPERATOR actor. | Playwright |

---

### 2.2 Parcel lifecycle

The core business logic of TuttiBox revolves around two synchronized state machines: the Parcel lifecycle (`ParcelState`) and the Compartment operational lifecycle (`CompartmentState`). Because TuttiBox manages the smart locker station network rather than external courier warehouse logistics, a parcel enters the system in the `CREATED` state as soon as a Partner registers it and a compatible compartment is reserved.

**Parcel States (`ParcelState`)**:
• `CREATED`: The parcel has been registered by a Partner and a specific compartment is reserved (`RESERVED`), awaiting physical drop-off by the Courier within the drop-off deadline (48h).  
• `READY_FOR_PICKUP`: The Courier deposited the parcel and confirmed door closure. The compartment is now `OCCUPIED`, and a 6-digit pickup code has been issued to the Recipient.  
• `WAITING_FOR_OPERATOR`: The recipient entered a valid pickup code, but the compartment door jammed. The parcel is held safely inside the station while the faulty compartment is marked `OUT_OF_SERVICE`. An Operator relocates the parcel to a free working compartment, transitioning it back to `READY_FOR_PICKUP` (with the same pickup code), or removes it for return (`RETURNED`).  
• `PICKED_UP` (Terminal state): The Recipient entered a valid pickup code at the terminal before the pickup deadline, the compartment door opened, the parcel was collected, and the compartment returned to `FREE`.  
• `CANCELLED` (Terminal state): The Partner cancelled the shipment while still in `CREATED`, or the 48h drop-off deadline elapsed before the Courier deposited the parcel. The reserved compartment is released back to `FREE`.  
• `EXPIRED`: The 72h pickup deadline elapsed while in `READY_FOR_PICKUP`. The parcel can no longer be collected by the Recipient, and the compartment remains `OCCUPIED` until an Operator intervenes.  
• `RETURNED` (Terminal state): An Operator physically removed an `EXPIRED` or jammed parcel from the station for return processing, freeing the compartment back to `FREE` (or keeping it `OUT_OF_SERVICE` if faulty).

| From State | Event / Trigger | Actor | To State | Compartment Effect |
| :--- | :--- | :--- | :--- | :--- |
| (None) | Register parcel & allocate compartment | PARTNER | CREATED | FREE to RESERVED |
| CREATED | Open compartment & confirm door closed | COURIER | READY_FOR_PICKUP | RESERVED to OCCUPIED |
| CREATED | Cancel registration before drop-off | PARTNER | CANCELLED | RESERVED to FREE |
| CREATED | Drop-off deadline (48h) elapsed | SYSTEM | CANCELLED | RESERVED to FREE |
| READY_FOR_PICKUP | Enter valid pickup code & open door | RECIPIENT | PICKED_UP | OCCUPIED to FREE |
| READY_FOR_PICKUP | Door jammed during pickup opening | SYSTEM | WAITING_FOR_OPERATOR | Compartment to OUT_OF_SERVICE |
| READY_FOR_PICKUP | Pickup deadline (72h) elapsed | SYSTEM | EXPIRED | Stays OCCUPIED |
| WAITING_FOR_OPERATOR | Operator relocates parcel to free comp. | OPERATOR | READY_FOR_PICKUP | Old stays OUT_OF_SERVICE, new to OCCUPIED |
| WAITING_FOR_OPERATOR | Operator removes parcel for return | OPERATOR | RETURNED | Old compartment stays OUT_OF_SERVICE |
| EXPIRED | Remove expired parcel from locker | OPERATOR | RETURNED | OCCUPIED to FREE |

**Audit Trail and Transition Guards**:  
Every valid state transition is persisted in an immutable history log (`ParcelTransition`) recording the previous state, new state, exact timestamp (via an injected `java.time.Clock`), and the responsible actor (`PARTNER`, `COURIER`, `RECIPIENT`, `OPERATOR`, or `SYSTEM`). Any event triggered on an incompatible state is explicitly rejected by the domain model and mapped to an HTTP 409 Conflict response with an RFC 7807 `ProblemDetail` error body.

---

### 2.3 Business rules

The following core business rules were specified with concrete, measurable parameters configured in application properties and verified through targeted unit, concurrency, and BDD acceptance tests:

| Rule/theme | Value/rationale | Configured in | Tested in |
| :--- | :--- | :--- | :--- |
| **Compartment allocation** | Strict exact size match (S -> S, M -> M, L -> L). Single exception: an S parcel may fallback to an M compartment only if all S compartments are occupied (never upgrades to L). M and L never upgrade. If no compatible compartment is available, registration is rejected (409 Conflict). Concurrency on the last compartment is protected via pessimistic database locking (`PESSIMISTIC_WRITE`). | `application.properties`<br>(`tuttibox.allocation.allow-s-to-m-fallback=true`) | `ParcelServiceTest` (Unit)<br>`PessimisticLockingPostgresTest` (Integration with Testcontainers PostgreSQL) |
| **Drop-off deadline** | 48 hours allowed after registration (CREATED). At 48h00m, drop-off is still accepted; at 48h01m, the parcel transitions to CANCELLED and its compartment is released back to FREE. | `application.properties`<br>(`tuttibox.deadlines.dropoff-hours=48`) | `ParcelStateMachineServiceTest` (Unit with fixed Clock)<br>`parcel_pickup.feature` (Cucumber BDD) |
| **Pickup deadline** | 72 hours allowed after drop-off (READY_FOR_PICKUP). At 72h00m, pickup is still accepted; at 72h01m, the parcel transitions to EXPIRED (compartment remains OCCUPIED until operator removal). | `application.properties`<br>(`tuttibox.deadlines.pickup-hours=72`) | `ParcelStateMachineServiceTest` (Unit with fixed Clock)<br>`parcel_pickup.feature` (Cucumber BDD) |
| **Pickup code (format, validity, protection against guessing)** | Cryptographically random 6-digit numerical PIN (000000-999999) generated via `SecureRandom`. Scoped to the locker station (`stationCode` + `pickupPinHash`). Single-use. To guard against brute force guessing, 3 consecutive failed PIN entries on a terminal enforce a 60-second safety lockout on that terminal (429 Too Many Requests). | `application.properties`<br>(`tuttibox.security.max-failed-attempts=3`, `tuttibox.security.lockout-duration=60s`) | `ParcelServiceTest` (Unit)<br>`TerminalServiceTest` (Unit)<br>`security_lockout.feature` (Cucumber BDD)<br>`TerminalPlaywrightTest` (E2E) |
| **Hardware failure: Drop-off door jammed** | If the reserved compartment door fails to open during courier drop-off, the compartment is marked OUT_OF_SERVICE and the parcel remains CREATED. | `LockerHardwarePort`<br>(failure injection in `InMemoryHardwareFake`) | `InMemoryHardwareFakeTest` (Unit)<br>`hardware_failure.feature` (Cucumber BDD) |
| **Hardware failure: Door close sensor fault** | If the door sensor fails to confirm closure after drop-off, transition to READY_FOR_PICKUP is blocked and no pickup code is issued until closure is confirmed. | `LockerHardwarePort`<br>(failure injection in `InMemoryHardwareFake`) | `InMemoryHardwareFakeTest` (Unit)<br>`hardware_failure.feature` (Cucumber BDD) |
| **Hardware failure: Pickup door jammed** | If the compartment door fails to open upon entering a valid pickup code, the compartment is marked OUT_OF_SERVICE and the parcel transitions to WAITING_FOR_OPERATOR. The Operator subsequently relocates the parcel to a free compartment in the station or removes it. | `LockerHardwarePort`<br>(failure injection in `InMemoryHardwareFake`) | `InMemoryHardwareFakeTest` (Unit)<br>`TerminalServiceTest` (Unit)<br>`hardware_failure.feature` (Cucumber BDD) |
| **Hardware failure: User Help issue reporting** | If a user reports a locker issue via the Help feature: if the parcel is inside the locker, the compartment is marked OUT_OF_SERVICE for operator inspection; if CREATED, the user is advised to wait for delivery. | `LockerStationRepository` and `ParcelService` | `TerminalServiceTest` (Unit)<br>`ParcelControllerTest` (Web Slice) |

---

### 2.4 Non-functional requirements

| ID | Requirement (measurable) | Verification method/tests implemented |
| :--- | :--- | :--- |
| **NFR1** | **Performance & Load Resilience:** Under sustained nominal load (10-20 VUs) and sudden traffic bursts (spike test to 50 concurrent VUs), the 95th percentile (p95) response time for station lookup and parcel registration must remain strictly below 200 ms, with an HTTP error rate strictly below 1%. | Automated Grafana k6 load & spike test script (`scripts/performance_test.js`) executed against the running application. Achieved p95 of 5.5 ms across 5,294 requests with 0% error rate. |
| **NFR2** | **Web Accessibility:** All public-facing Web views (Terminal touch kiosk `/terminal` and Operator dashboard `/operator`) must comply with WCAG 2.1 Level AA, featuring zero critical accessibility violations and full navigation support without requiring a pointing device. | Automated inspection and manual keyboard navigation walkthrough (`Tab`, `0-9`, `Enter`, `Esc`), high-contrast focus rings (`:focus-visible`), and semantic ARIA alert roles (`role="alert"`). |
| **NFR3** | **Pickup Code Security & Anti-Brute Force:** The 6-digit numerical pickup code provides 10^6 entropy space generated via cryptographically secure randomness (`SecureRandom`). Stored as salted SHA-256 hashes. The station terminal automatically enforces a 60-second safety lockout (HTTP 429 Too Many Requests) after 3 consecutive failed PIN attempts. | Unit tests in `ParcelServiceTest` and `TerminalServiceTest`, Web Slice tests in `TerminalControllerTest`, BDD scenarios in `security_lockout.feature`, and browser automation via `TerminalPlaywrightTest`. |
| **NFR4** | **Geocoding Usage Policy & Caching:** The geolocation client strictly adheres to OpenStreetMap Nominatim Usage Policy: (1) transmits an identifying User-Agent, (2) throttles requests to 1 request/second, and (3) caches resolved coordinates locally in-memory via `@Cacheable("geocodingCache")`. In tests, `InMemoryGeocodingFake` prevents external API quota exhaustion. | Unit and slice tests verifying cached coordinates retrieval and offline deterministic test execution without live external HTTP calls. |

---

### 2.5 System architecture and design decisions

| Design decision | Your option | Testing strategy/impact |
| :--- | :--- | :--- |
| **Simulation of the locker terminal** | Server-side rendered web page (Thymeleaf + Vanilla JS) simulating the physical terminal. Features a numeric keypad (0–9, Clear, Enter) for PIN entry, an automatic 60-second lockout timer after 3 failed attempts, and an adjacent virtual locker compartment widget displaying door states ("Door Opened" / "Door Closed") with interactive "Simulate Door Close" controls. | Directly verified end-to-end using Microsoft Playwright (`TerminalPlaywrightTest`). Tests locate user-facing elements, simulate sequential touch inputs, assert DOM state updates, and test hardware failure handling. |
| **Representation of the parcel lifecycle** | Strongly typed Java enum `ParcelState` (7 states: `CREATED`, `READY_FOR_PICKUP`, `WAITING_FOR_OPERATOR`, `PICKED_UP`, `CANCELLED`, `EXPIRED`, `RETURNED`). State transitions are governed by an application domain service that validates state prerequisites, synchronizes compartment states (`CompartmentState`), and logs an immutable audit trail (`ParcelTransition`). | Unit tested via JUnit 6 `@ParameterizedTest` over a complete state transition matrix, verifying all valid paths and asserting that illegal transitions throw domain exceptions mapped to HTTP 409 Conflict. |
| **Controllable time in tests** | Injection of a `java.time.Clock` bean across all domain and application services (`Clock.systemUTC()` in production, fixed test clock in test configurations). Direct invocations of `System.currentTimeMillis()` or `Instant.now()` are strictly prohibited. | Enables deterministic time-travel testing in unit and BDD tests. Scenarios validating 48h drop-off and 72h pickup deadlines advance time instantaneously, eliminating brittle `Thread.sleep()` delays. |
| **Hardware port: operations; synchronous or event-driven** | Decoupled `LockerHardwarePort` interface with synchronous command methods (`unlockDoor`) and state queries. Door closing is handled as an inbound event callback (`onDoorClosed`). In production, an adapter communicates with locker microcontrollers; in tests, an `InMemoryHardwareFake` is used. | The fake hardware adapter maintains in-memory state and allows injecting simulated hardware failures (e.g., door jammed, sensor failure). This enables automated testing of edge-case failure recoveries without physical locker hardware. |
| **Concurrency in compartment allocation** | Database pessimistic row-level locking (`PESSIMISTIC_WRITE`) via Spring Data JPA during compartment search and reservation. Avoids the operational overhead of an external message broker while guaranteeing strict ACID consistency across concurrent requests. | Multi-threaded integration tests with `ExecutorService` in `PessimisticLockingPostgresTest` simulate concurrent reservations for the last remaining compartment. Confirms exactly one transaction succeeds while concurrent attempts gracefully fail with HTTP 409 Conflict (no double-allocations). |
| **Pickup codes: generation, storage, checking** | 6-digit cryptographically secure numeric codes (`SecureRandom`), station-scoped and single-use. Stored in the database as salted SHA-256 hashes. Verified via a constant-time comparison service (`MessageDigest.isEqual`) with brute-force protection (3 consecutive wrong attempts trigger a 60-second terminal lockout). | Unit tested for code entropy and hashing correctness. Functional and integration tests verify invalid PIN rejections, lockout state persistence, and automatic unlock after the 60-second window. |
| **Geocoding provider: choice; behavior when slow, unavailable or over limits** | OpenStreetMap Nominatim with an Outbound Geocoding Port, in-memory caching (`@Cacheable`), and graceful fallback. In case of network timeout, falls back to cached coordinates or a domain exception. | Automated tests and load tests NEVER call the live OpenStreetMap servers. Instead, an Outbound Adapter stub (`InMemoryGeocodingFake`) is used, ensuring 100% offline, deterministic, ultra-fast test execution. |
| **REST API design: resources, status codes, error format** | RESTful resource-oriented API (`/api/v1/parcels`, `/api/v1/stations`, `/api/v1/terminals`) adhering to standard HTTP semantics (200 OK, 201 Created, 400 Bad Request, 404 Not Found, 409 Conflict, 429 Too Many Requests, 500 Internal Server Error). Standardized error payloads compliant with RFC 7807 (`ProblemDetail`) managed globally via `@RestControllerAdvice`. | Integration tests using MockMvc validate status codes, response headers, and RFC 7807 error schema details for every endpoint and exception scenario. |

---

### 2.6 API for developers

TuttiBox provides a RESTful web API structured around resource-oriented endpoints under the `/api/v1` base path. Interactive documentation, endpoint schemas, and request/response models are automatically generated using SpringDoc OpenAPI 3.0 and made accessible at `/swagger-ui.html` and `/v3/api-docs`.

All endpoints follow standard HTTP semantics:
- `200 OK`: Successful read or state update operation.
- `201 Created`: New resource successfully created (e.g., parcel registered and locker reserved).
- `400 Bad Request`: Validation failure (e.g., malformed PIN or invalid dimension size) returning structured field errors.
- `404 Not Found`: Target parcel, station, or compartment does not exist.
- `409 Conflict`: Business rule violation (e.g., no locker available of the requested size, or parcel already collected).
- `429 Too Many Requests`: Security threshold exceeded (terminal locked for 60 seconds after 3 failed PIN attempts).
- `500 Internal Server Error`: Hardware solenoid failure or unrecoverable error mapped to RFC 7807 format.

---

# 3. Quality Assurance & Test Strategy

## 3.1 Overview of the Tests Available
TuttiBox implements an exhaustive testing pyramid structured across 10 distinct testing disciplines, strictly aligned with the TQS syllabus and practical curriculum (DETI UA). The entire automated test suite is organized under `src/test/java/pt/ua/deti/tqs/tuttibox/` and executed seamlessly via the Maven build lifecycle.

* **Test Suite Metrics & Execution Status**:
  - **Total Automated Tests**: 70 tests executed with **0 failures and 0 errors (100% pass rate)**.
  - **Test Breakdown across Categories**:
    1. *Pure Domain Unit Tests*: 23 tests (16 in `ParcelStateMachineServiceTest`, 7 in `StationRankingServiceTest` validating Haversine distance geometry).
    2. *Test Doubles & Mocking*: 22 tests (7 in `ParcelServiceTest`, 10 in `TerminalServiceTest` using Mockito, 5 in `InMemoryHardwareFakeTest` verifying cyber-physical actuators).
    3. *Web Slice Tests (@WebMvcTest)*: 18 tests (`ParcelControllerTest`, `TerminalControllerTest`, `StationControllerTest`, `TerminalUiControllerTest`) validating HTTP status codes and RFC 7807 `ProblemDetail` formats.
    4. *Database Concurrency Integration*: Multi-threaded integration suite in `PessimisticLockingPostgresTest` executing against a containerized PostgreSQL instance via Testcontainers.
    5. *BDD Acceptance Scenarios*: 3 Gherkin feature files (`parcel_pickup.feature`, `security_lockout.feature`, `hardware_failure.feature`) with 19 step definitions driving `TerminalService`.
    6. *End-to-End Browser Automation*: Real Chromium browser automation via Microsoft Playwright (`TerminalPlaywrightTest` and `OperatorPlaywrightTest`), validating kiosk touchscreen keypad clicks and reactive DOM states.
    7. *Non-Functional Performance*: Automated Grafana k6 performance suite (`scripts/performance_test.js`) evaluating nominal, spike, and brute-force workloads.
    8. *Security Rate-Limiting*: Multi-layered brute-force defense tested at domain, controller, BDD, and UI levels.
    9. *Accessibility (WCAG 2.1 AA)*: Validated through high-contrast focus rings, semantic ARIA live regions, and full keyboard navigation.
    10. *Fault Injection & Resilience*: Hardware solenoid jamming simulated in memory, asserting domain state safety and HTTP 500 RFC 7807 error dispatching.

## 3.2 Unit Tests and Test Doubles
Unit tests focus on verifying domain business invariants and state machine rules in complete isolation from infrastructure:
* **Domain Rule Verification**: Scenario-driven tests cover the state transitions of `ParcelStateMachineService`, validating 48h drop-off and 72h pickup deadlines using an injected fixed `Clock`. Mathematical correctness of the Haversine great-circle distance algorithm is verified in `StationRankingServiceTest` against physical coordinates in Aveiro.
* **In-Memory Fakes (Test Doubles)**:
  - `InMemoryHardwareFake` (implementing `LockerHardwarePort`): Simulates solenoid lock microcontrollers and magnetic door sensors. Maintains internal compartment door states (`OPEN` / `CLOSED`) and supports deterministic failure injection via `simulateDoorJam()`, enabling hardware fault recovery testing without cyber-physical hardware.
  - `InMemoryGeocodingFake` (implementing `GeocodingPort`): Resolves geographical coordinates for Aveiro addresses with full Portuguese accent normalization (`Normalizer.Form.NFD`), preventing external API quota exhaustion and network flakiness.
* **Mocking Strategy**: Mockito mocks (`@Mock`) are purposefully reserved for orchestrating application services (`ParcelService`, `TerminalService`), isolating repository boundaries and verifying that transitions are persisted strictly upon valid hardware signals.

## 3.3 Integration Tests (Web Controller Slices)
Web controller endpoints are tested using Spring Boot slice testing (`@WebMvcTest`), loading the web layer infrastructure without instantiating full database contexts:
* `ParcelControllerTest`, `StationControllerTest`, and `TerminalControllerTest` verify HTTP status codes, query ranking, and payload serialization in isolation using MockMvc.
* Global error handling via `GlobalExceptionHandler` (`@RestControllerAdvice`) is verified against RFC 7807 (`ProblemDetail`), confirming that input validation failures yield HTTP 400 with structured `invalidFields`, invalid PIN entries yield HTTP 401, security lockouts yield HTTP 429, and door jamming faults yield HTTP 500.

## 3.4 Acceptance Testing, Browser Automation & Concurrency
* **BDD Specifications**: Acceptance criteria were formalized using Cucumber with Gherkin feature files covering parcel pickup (`parcel_pickup.feature`), security lockout (`security_lockout.feature`), and hardware fault recovery (`hardware_failure.feature`).
* **Browser Automation (Microsoft Playwright)**: End-to-end user journeys were automated using Playwright driving a headless Chromium browser instance against the kiosk touchscreen (`/terminal`) and operator cockpit (`/operator`), verifying interactive UI state transitions and lockout feedback.
* **Concurrency Integrity**: Concurrency was validated using Testcontainers with PostgreSQL, verifying that JPA pessimistic row-level locks prevent race conditions and overbooking when concurrent requests compete for the last available compartment.

## 3.5 Non-Functional Testing

### Performance Testing (Grafana k6)
* **Load Scenarios & Requirements**: A 3-stage load testing suite was authored in `scripts/performance_test.js`:
  1. *Nominal Load*: 20 concurrent Virtual Users (VUs) querying nearby lockers and checking terminal health.
  2. *Black Friday Spike*: 50 concurrent VUs bursting parcel creation and compartment reservation.
  3. *Security Rate Limiting*: 5 VUs rapidly entering invalid PINs to test the 60-second lockout defense. Geolocation queries were stubbed via `InMemoryGeocodingFake` to eliminate external quota limits.
* **Thresholds & Results**: SLA thresholds required `p(95) < 250ms` and `p(99) < 500ms`. Over a test run executing 5,294 HTTP requests (9,948 checks, 100% pass rate, 0 failed checks):
  - Overall HTTP Duration: average = 2.10 ms, p(95) = 5.50 ms, p(99) = 10.74 ms (well within SLA).
  - Parcel Creation (Reservation): average = 3.11 ms, p(95) = 4.80 ms.
  - Nearby Stations Search: average = 3.34 ms, p(95) = 7.42 ms.
  - Security Lockout Defense: average = 25.0 ms (including SHA-256 digest verification), transitioning to HTTP 429 upon the 3rd failed attempt without system degradation.

### Accessibility Audit (WCAG 2.1 AA Compliance)
* **Automated & Structured Checks**: Both interfaces (`/terminal` touch kiosk and `/operator` dashboard) were audited against WCAG 2.1 Level AA criteria. High-contrast visual focus styling was enforced via `:focus-visible { outline: 3px solid #3b82f6; outline-offset: 2px; }`. Keypad elements are semantic `<button>` controls with explicit `aria-label` attributes (`Dígito 1`, `Limpar PIN`, `Confirmar PIN`). Dynamic feedback and the 60-second lockout overlay are equipped with `role="alert"` and `aria-live="assertive"` regions.
* **Manual Keyboard Walkthrough**: Verified complete touchless parcel retrieval using only `Tab`, numeric keys (`0-9`), `Enter` (OK), and `Backspace` (Clear) without pointer input.

## 3.6 Code Quality Analysis & Static Inspection
* **Quality Gate Status & Final Metrics**:
  - Quality Gate Status: **PASSED (OK)** across all conditions.
  - Line Coverage: **73.2% line coverage** verified by the JaCoCo Maven plugin across 38 application classes.
  - Maintainability, Security & Reliability: **0 Bugs, 0 Vulnerabilities, 0 Security Hotspots, and 0 Code Smells** (Grade A across all evaluation axes).
  - Duplicated Lines Density: **0.0%**.
* **Security Hardening (Log Injection Resolution — CWE-117)**:
  During static analysis, SonarQube Cloud flagged security warnings (`javasecurity:S5145`) related to untrusted user input originating from HTTP `@PathVariable` (`stationCode`) and `@RequestParam` (`address`) parameters being forwarded into logger statements. If left unhandled, malicious actors could supply CRLF characters (`\r\n`) to inject forged log entries (*Log Forging*). 
  To permanently eliminate this vulnerability:
  - A deterministic sanitization helper was implemented in `TerminalService` and `NominatimGeocodingAdapter`, stripping carriage return and newline characters (`input.replace('\n', '_').replace('\r', '_')`) prior to passing parameters to SLF4J loggers.
  - Upon pushing the sanitization commit, SonarCloud verified that the taint flow was severed, successfully clearing all 6 issues and bringing the active issue count to zero.

## 3.7 Continuous Integration Pipeline
* **Pipeline Workflow**: Configured in `.github/workflows/sonar.yml`, triggered on every push and pull request to the `main` branch.
* **Execution**: An Ubuntu runner provisions JDK 21 (Eclipse Temurin), caches Maven artifacts, installs Playwright Chromium browser binaries (`npx playwright install --with-deps chromium`), executes the test suite, generates the JaCoCo coverage report, and runs `sonar:sonar` authenticating via `SONAR_TOKEN`.

---

# 4. Self-Reflection & Challenges Overcome

### 4.1 Technical Challenges and Architectural Solutions
Developing TuttiBox provided comprehensive practical exposure to modern software quality engineering and agile domain modeling:
1. **Concurrency and ACID Guarantees**: Protecting against parcel locker double-booking under high concurrency was addressed using database-level pessimistic locking (`PESSIMISTIC_WRITE`). Rather than relying on optimistic locks that fail late on commit, the row-level lock ensures strict FIFO allocation, verified empirically through a 4-thread race condition test.
2. **Decoupled Cyber-Physical Hardware**: Real-world smart lockers interact with physical microcontrollers, solenoids, and magnetic sensors that are impossible to execute inside standard CI/CD pipelines. Adopting Hexagonal Architecture (Ports and Adapters) allowed the creation of `InMemoryHardwareFake`, enabling deterministic failure injection (e.g., simulating mechanical door jams) and proving resilience without cyber-physical equipment.
3. **Deterministic Time-Travel Testing**: Testing 48-hour drop-off and 72-hour pickup expiry deadlines without brittle `Thread.sleep()` delays was resolved by injecting a shared `java.time.Clock` bean across all domain services. This made unit and BDD tests instantaneous and 100% deterministic.
4. **Defensive Log Sanitization & Security**: Addressing SonarCloud's CWE-117 Log Injection findings demonstrated the importance of Defense in Depth. Sanitizing all externally provided query and path parameters before logging prevents log poisoning and ensures forensic audit trail integrity.

---

# 5. References & Project Resources
* **Source Repository**: `https://github.com/FilipeNogue1ra/HW1-119495`
* **Continuous Integration**: GitHub Actions CI (`.github/workflows/sonar.yml`)
* **SonarCloud Project Dashboard**: `https://sonarcloud.io/project/overview?id=FilipeNogue1ra_HW1-119495`
* **Demonstration Video**: `docs/tuttibox_demo.webm` (1m30s screencast covering UC1 geocoded search, UC2 partner API registration, UC3 courier drop-off, UC4 recipient pickup, security brute-force lockout defense, and UC6 operator cockpit supervision)
* **API Documentation**: Interactive Swagger UI at `http://localhost:8080/swagger-ui.html` and OpenAPI specification at `/v3/api-docs`.
* **Testing Toolchain & Standards (aligned with TQS 2026/27 syllabus)**:
  - **Core Unit Testing**: JUnit 6 (Jupiter 6.1.3, BOM-managed), AssertJ.
  - **Mocking & Isolation**: Mockito 5.23.0 (`mockito-junit-jupiter`).
  - **Web Controller Slice Testing**: Spring Boot `@WebMvcTest` with MockMvc and `@MockitoBean`.
  - **BDD & Executable Specifications**: Cucumber BDD running on the JUnit Platform (`cucumber-junit-platform-engine`, `cucumber-java`).
  - **Browser Acceptance Testing**: Microsoft Playwright for Java 1.63.0 (Chromium headless automation).
  - **Database Integration**: Testcontainers with official PostgreSQL container.
  - **Performance & Load Testing**: Grafana k6 (multi-stage ramp-up and spike scenarios).
  - **Code Coverage & Test Execution**: Maven Surefire Plugin 3.6.0, JaCoCo Maven Plugin 0.8.15 (73.2% line coverage).
  - **Static Code Analysis**: SonarQube Cloud with GitHub Actions CI integration (Quality Gate: Passed, 0 Bugs, 0 Vulnerabilities, 0 Security Hotspots, 0 Code Smells).
  - **Security & Architectural Standards**: RFC 7807 (Problem Details for HTTP APIs), WCAG 2.1 AA Accessibility Guidelines.
