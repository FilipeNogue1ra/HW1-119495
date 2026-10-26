# TuttiBox - Smart Parcel Locker System

**Course:** TQS - Software Testing & Quality Assurance (2025/2026)
**Student:** Filipe Nogueira (NM: 119495)
**Assignment:** HW1 Midterm Project

---

## 🚀 Overview

TuttiBox is a smart locker management platform designed using **Hexagonal Architecture (Ports and Adapters)** to decouple core business logic from databases, external geolocation APIs, and physical locker hardware.

### Key Capabilities:

- **Core Domain & State Machine:** 7 parcel lifecycle states with immutable audit transition logs.
- **Hardware Decoupling:** In-memory hardware fake with state tracking and configurable failure injection (solenoid lock jams).
- **Geolocation & Ranking:** Distance calculations using Haversine formula and OpenStreetMap Nominatim geocoding with caching.
- **Operator Supervision Panel:** Real-time visual monitoring of locker compartments and door sensor states at `http://localhost:8080/operator`.

---

## 🛠 Tech Stack

- **Java:** 21 LTS
- **Framework:** Spring Boot 3.4.2
- **Persistence:** Spring Data JPA, PostgreSQL (Testcontainers for integration testing, H2 for lightweight dev run)
- **Testing Pyramid:** JUnit 6 (Jupiter), Mockito, REST Assuredd, Playwright, Cucumber BDD
- **Coverage & Quality:** JaCoCo (>80% target), SonarQube

---

## 🧪 Running Tests & Quality Analysis

### Execute All Unit & Integration Tests:

```bash
mvn clean test
```

### View Local JaCoCo Coverage Report:

After running tests, open `target/site/jacoco/index.html` in your browser.

### Run Local Development Server:

```bash
mvn spring-boot:run
```

Access the Operator Dashboard at `http://localhost:8080/operator` or API docs at `http://localhost:8080/swagger-ui.html`.
