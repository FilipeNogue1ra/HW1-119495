# TuttiBox — Smart Parcel Locker System

**Course:** Teste e Qualidade de Software (TQS 2025/2026)  
**Institution:** Universidade de Aveiro — DETI  
**Author:** Filipe Nogueira (NM: 119495)  
**Deliverable:** HW1 Midterm Project  
**Report:** Available in [`docs/TQS-HW1-Report-119495.docx`](docs/TQS-HW1-Report-119495.docx)

---

## 📦 Overview

TuttiBox is a smart locker management platform in Aveiro designed with **Hexagonal Architecture (Ports and Adapters)**. It decouples core business logic from databases, external geolocation APIs, and physical locker hardware.

### Key Features
- **Parcel Lifecycle & State Machine:** 7 parcel states with audit logs (`CREATED`, `IN_TRANSIT`, `DELIVERED`, `PICKED_UP`, `WAITING_FOR_OPERATOR`, `RETURNED`, `EXPIRED`).
- **Hardware Abstraction:** `LockerHardwarePort` with failure injection (`InMemoryHardwareFake` simulating solenoid jams).
- **Geolocation & Station Ranking:** Haversine distance formula with Nominatim OpenStreetMap caching.
- **Operator Cockpit:** Web dashboard at `/operator` with real-time door sensors, fault toggling, manual empty, and system reset.
- **Locker Terminal Kiosk:** Touchscreen interface at `/terminal` for parcel pickup and dropoff.

---

## 🛠 Tech Stack

- **Java:** 21 LTS
- **Framework:** Spring Boot 3.4.2
- **Testing Pyramid:** JUnit 5 (Jupiter), Mockito, REST Assured, Playwright, Cucumber BDD
- **Persistence & Isolation:** Spring Data JPA, PostgreSQL 16 (Testcontainers for integration), H2 (in-memory for local dev)
- **Quality & Performance:** JaCoCo (>88% coverage), SonarQube (Quality Gate Passed, Rating A), Grafana k6

---

## 🧪 Testing Pyramid (70 Automated Tests)

| Level | Scope / Strategy | Technologies | Count |
|---|---|---|---|
| **Domain Unit** | Pure domain business rules & Haversine distance (0 ms) | JUnit 5 | 23 |
| **Service Unit** | Service layer with mocked repositories | Mockito | 22 |
| **Web Slices** | REST API endpoints, validation, RFC 7807 problem details | `@WebMvcTest`, MockMvc | 18 |
| **Integration** | Pessimistic locking (`SELECT ... FOR UPDATE`) | Testcontainers, PostgreSQL 16 | 1 |
| **BDD Acceptance** | Pickup, security lockout, and hardware failure scenarios | Cucumber 7, Gherkin | 3 |
| **E2E Browser** | Virtual touchscreen kiosk and operator cockpit | Microsoft Playwright | 3 |
| **Total** | **Zero failures / 100% pass rate** | | **70** |

---

## 🚀 How to Run

### 1. Run All Automated Tests
```bash
mvn clean test
```
*Note: Ensure Docker is running if executing Testcontainers integration tests.*

### 2. View JaCoCo Coverage Report
```bash
# Generated after running tests:
target/site/jacoco/index.html
```

### 3. Run the Application
```bash
mvn spring-boot:run
```

Once started, access:
- **Operator Dashboard:** [http://localhost:8080/operator](http://localhost:8080/operator)
- **Terminal Kiosk:** [http://localhost:8080/terminal](http://localhost:8080/terminal)
- **Swagger / OpenAPI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### 4. Run k6 Performance Tests
```bash
k6 run scripts/performance_test.js
```
*Achieves average latency of 3.18 ms and p99 of 10.74 ms under 60 concurrent VUs.*
