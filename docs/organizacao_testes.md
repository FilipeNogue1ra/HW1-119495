# TuttiBox — Organização Completa da Suíte de Testes (TQS 2026)

**Autor:** Filipe Nogueira (119495)  
**Projeto:** TuttiBox — Smart Parcel Lockers (Aveiro)  
**Disciplina:** Testes e Qualidade de Software (DETI UA)  
**Data da Auditoria:** 29 de Outubro de 2026  
**Resultado da Execução:** 70 testes executados, 0 falhas, 0 erros (100% de aprovação)

---

## 🗺️ Mapa de Estrutura de Ficheiros de Teste

```
HW1-119495/src/test/
├── acceptance/                                  --> [Cat. 5] BDD / Cucumber
│   ├── features/                                --> Gherkin Feature Files
│   │   ├── parcel_pickup.feature
│   │   ├── security_lockout.feature
│   │   └── hardware_failure.feature
│   ├── CucumberSpringConfiguration.java
│   ├── CucumberTestRunnerTest.java
│   ├── ParcelPickupSteps.java
│   ├── SecurityLockoutSteps.java
│   └── HardwareFailureSteps.java
├── core/service/                                --> [Cat. 1 e Cat. 2] Testes Unitários Puros e Test Doubles (Mocks)
│   ├── ParcelStateMachineServiceTest.java       --> [Cat. 1] Máquina de estados pura + Clock fixo (16 testes)
│   ├── StationRankingServiceTest.java           --> [Cat. 1] Cálculo trigonométrico Haversine (7 testes)
│   ├── ParcelServiceTest.java                   --> [Cat. 2] Regras de negócio + Mocks de repositório (7 testes)
│   └── TerminalServiceTest.java                 --> [Cat. 2, 8, 10] Coordenação de hardware, lockout e falhas (10 testes)
├── e2e/                                         --> [Cat. 6, 8, 9] Testes End-to-End no Browser Real (Playwright)
│   ├── TerminalPlaywrightTest.java              --> Quiosque tátil, teclado 0-9, overlay 60s
│   └── OperatorPlaywrightTest.java              --> Dashboard do operador, grelha de cacifos
├── infrastructure/
│   ├── adapters/in/web/                         --> [Cat. 3 e Cat. 8] Web Slice Tests (@WebMvcTest)
│   │   ├── ParcelControllerTest.java            --> REST API de encomendas, RFC 7807, validações DTO (6 testes)
│   │   ├── StationControllerTest.java           --> REST API de estações e ranking por geolocalização (3 testes)
│   │   ├── TerminalControllerTest.java          --> REST API do terminal, HTTP 429 e 500 (7 testes)
│   │   └── TerminalUiControllerTest.java        --> Renderização de views HTML do quiosque (2 testes)
│   └── adapters/out/hardware/                   --> [Cat. 2 e Cat. 10] Fakes de Hardware em Memória
│       └── InMemoryHardwareFakeTest.java        --> Port de hardware e injeção de encravamento (5 testes)
├── integration/                                 --> [Cat. 4] Persistência Real & Concorrência
│   └── PessimisticLockingPostgresTest.java      --> Testcontainers PostgreSQL + Multithreading
└── scripts/                                     --> [Cat. 7 e Cat. 8] Carga e Desempenho
    └── performance_test.js                      --> Script k6 (Spike 50 VUs, P95 < 200ms)
```

---

## 🔬 Auditoria Detalhada: As 10 Categorias de Teste

### 1. Testes Unitários Puros (Pure Domain Unit Tests)
* **Princípio TQS:** Zero dependências de Spring, zero base de dados, execução em microssegundos. Testa invariantes lógicas e matemáticas puras.
* **Ficheiros e Métodos:**
  * `core/service/ParcelStateMachineServiceTest.java` (16 testes):
    * `testValidLifecycleFromReservedToPickedUp()`: Garante a sequência linear `RESERVED` $\to$ `READY_FOR_PICKUP` $\to$ `PICKED_UP`.
    * `testValidLifecyclePartnerCancellation()`: Valida transição `RESERVED` $\to$ `CANCELLED`.
    * `testValidDropoffExpiry()`: Valida que uma encomenda não depositada em 48h expira para `EXPIRED_UNCLAIMED`.
    * `testValidPickupExpiry()`: Valida que uma encomenda depositada mas não recolhida em 72h passa a `RETURNED_TO_SENDER`.
    * `testIllegalTransitionsAreRejected()`: `@ParameterizedTest` que testa transições ilegais (ex.: tentar recolher diretamente de `RESERVED` ou cancelar uma encomenda já levantada). Lança obrigatoriamente `IllegalStateException`.
    * `testCalculateDropoffDeadline48Hours()` & `testCalculatePickupDeadline72Hours()`: Usa um `Clock` fixo injetado (sem recorrer ao relógio do sistema) para garantir rigor ao segundo (exatamente $+48\text{h}$ e $+72\text{h}$).
  * `core/service/StationRankingServiceTest.java` (7 testes):
    * `testHaversineDistanceKnownPoints()`: Compara a distância calculada entre a Reitoria da UA e a Estação de Aveiro ($1.53\text{ km} \pm 50\text{ m}$).
    * `testHaversineDistanceZeroForSamePoint()`: Distância de um ponto a si mesmo devolve rigorosamente $0.0$.
    * `testHaversineDistanceSymmetry()`: A distância de $A \to B$ é rigorosamente igual a $B \to A$.
    * `testRankStationsByProximityOrder()`: Ordena uma lista de estações pela distância crescente ao utilizador.
    * `testRankStationsWithEmptyList()` & `testRankStationsWithInvalidCoordinates()`: Valida tratamento de casos limite e coordenadas inválidas (ex.: latitude fora do intervalo $[-90, +90]$).

---

### 2. Testes com Dublês de Teste (Test Doubles: Mocks & Fakes)
* **Princípio TQS:** Mockito para isolar a camada de serviço da persistência; Fakes em memória para simular dispositivos de hardware físicos.
* **Ficheiros e Métodos:**
  * `core/service/ParcelServiceTest.java` (7 testes):
    * `testCreateParcelHappyPath()`: Criação bem-sucedida, hash SHA-256 gerado, `verify(parcelRepository).save(...)`.
    * `testCreateParcelFallbackS_to_M()`: **Regra de Negócio 1** — quando não há cacifos S disponíveis, aloca automaticamente cacifo M.
    * `testCreateParcelStationFullThrowsConflict()`: Se nem S nem M existirem, lança `NoCompartmentAvailableException` com código `NO_LOCKERS_AVAILABLE`.
    * `testCancelParcelWhenReservedSucceeds()`: Cancela a encomenda e liberta o cacifo de volta a `FREE`.
    * `testCancelParcelAlreadyPickedUpThrowsException()`: Tentar cancelar uma encomenda já recolhida lança `IllegalStateException`.
    * `testHashPinSHA256()`: Garante que o PIN não é persistido em texto claro na base de dados (devolve hash hexadecimal de 64 caracteres).
  * `core/service/TerminalServiceTest.java` (10 testes):
    * `testCourierDropoffSuccess()`: Valida que o estafeta destranca a porta, o estado passa a `READY_FOR_PICKUP` e o cacifo a `OCCUPIED`.
    * `testRecipientPickupSuccess()`: Valida que o PIN correto abre o trinco via `LockerHardwarePort` e liberta o cacifo.
    * `testFailedPinIncrementsCounter()`: Valida que um PIN errado incrementa as tentativas falhadas e lança `InvalidPinException` indicando quantas tentativas restam.
  * `infrastructure/adapters/out/hardware/InMemoryHardwareFakeTest.java` (5 testes):
    * Valida o estado real do solenoide elétrico (`unlockSolenoid`, `isDoorOpen`, `closeDoorPhysicalSensor`).
    * `testDoorJamSimulation()`: Simula falha física do trinco (porta encravada) e garante que o método devolve `false`.

---

### 3. Testes de Fatiamento Web (Web Slice Tests / `@WebMvcTest`)
* **Princípio TQS:** Testa a camada HTTP/REST isolada com `MockMvc`. Valida códigos HTTP, DTOs `@Valid` e o formato de erro estándar **RFC 7807** (`ProblemDetail`).
* **Ficheiros e Métodos:**
  * `infrastructure/adapters/in/web/ParcelControllerTest.java` (6 testes):
    * `testCreateParcelSuccessReturns201()`: Devolve `201 Created` e cabeçalho `Location`.
    * `testCreateParcelInvalidPhoneReturns400RFC7807()`: Envia telefone com formato inválido $\to$ valida que devolve `400 Bad Request` com `title = "Bad Request"` e detalhe do campo inválido.
    * `testCreateParcelStationFullReturns409RFC7807()`: Sem cacifos $\to$ devolve `409 Conflict` com `type = "https://tuttibox.ua.pt/errors/no-lockers-available"`.
    * `testGetParcelByTrackingCodeNotFoundReturns404()`: Encomenda inexistente $\to$ `404 Not Found`.
  * `infrastructure/adapters/in/web/TerminalControllerTest.java` (7 testes):
    * `testPickupSuccessReturns200()`: Código `200 OK` com dados do cacifo aberto.
    * `testPickupInvalidPinReturns401()`: Devolve `401 Unauthorized` com cabeçalho ou corpo com `remainingAttempts`.
    * `testPickupTerminalLockedReturns429()`: Quando bloqueado por força bruta $\to$ devolve `429 Too Many Requests` com cabeçalho `Retry-After: 60`.
    * `testPickupHardwareJamReturns500()`: Falha física do trinco $\to$ devolve `500 Internal Server Error` com RFC 7807 de avaria de hardware.
  * `infrastructure/adapters/in/web/StationControllerTest.java` (3 testes):
    * Testa os endpoints de listagem de estações e ranking por geolocalização (`/api/v1/stations/nearby?lat=...&lng=...`).
  * `infrastructure/adapters/in/web/TerminalUiControllerTest.java` (2 testes):
    * Valida que os endpoints `/terminal` e `/operator` devolvem `200 OK` e o template Thymeleaf correto (`terminal.html` e `operator.html`).

---

### 4. Testes de Persistência e Concorrência (Testcontainers & JPA)
* **Princípio TQS:** Concorrência multithreaded com bloqueio pessimista (`Pessimistic Locking`) para evitar *double-booking* de cacifos.
* **Ficheiro:** `integration/PessimisticLockingPostgresTest.java`
  * Configura `@Testcontainers(disabledWithoutDocker = true)` subindo uma instância oficial de `postgres:16-alpine`.
  * Cenário: Dispara **4 threads concorrentes em simultâneo** (via `CountDownLatch` e `ExecutorService`) tentando reservar o **último cacifo disponível** na estação.
  * Asserção: Exatamente **1 thread tem sucesso** e as outras **3 recebem `NoCompartmentAvailableException`**, provando que `SELECT ... FOR UPDATE` na base de dados previne corrida de dados (*race condition*).
  * *Nota de transparência:* Usa `disabledWithoutDocker = true` para que a pipeline execute com sucesso tanto no ambiente de CI/CD sem socket local de Docker como com Docker Desktop ativo.

---

### 5. Testes de Aceitação BDD (Cucumber & Gherkin)
* **Princípio TQS:** Cenários legíveis por negócio (*Ubiquitous Language*), ligando passos Gherkin à camada aplicacional.
* **Ficheiros:**
  * Features: `parcel_pickup.feature`, `security_lockout.feature`, `hardware_failure.feature`.
  * Steps: `ParcelPickupSteps.java`, `SecurityLockoutSteps.java`, `HardwareFailureSteps.java`.
* **Cenários Executados e Aprovados (3 Scenarios, 19 Steps):**
  1. *Happy Path Levantamento*: Cliente insere PIN válido de 6 dígitos $\to$ porta destranca $\to$ porta fecha $\to$ encomenda `PICKED_UP`, cacifo `FREE`.
  2. *Bloqueio de Segurança*: 3 PINs incorretos consecutivos (`000000`, `111111`, `222222`) $\to$ quiosque entra em `locked = true` por 60 segundos $\to$ 4º pedido rejeitado com erro de bloqueio.
  3. *Avaria Física de Hardware*: Simulação de encravamento físico do trinco $\to$ sistema regista incidente crítico e não marca a encomenda como levantada.

---

### 6. Testes End-to-End no Navegador Real (Playwright)
* **Princípio TQS:** Automação web num browser real (Google Chrome) para validar a experiência visual do utilizador final.
* **Ficheiros e Métodos:**
  * `e2e/TerminalPlaywrightTest.java` (2 testes):
    * `testTerminalPickupHappyPath()`: Abre `http://localhost:port/terminal`, clica nos botões táteis `[data-key="8"]`, `[data-key="4"]`..., clica em "OK", valida que a mensagem verde de sucesso surge no DOM e que o cacifo muda graficamente para aberto.
    * `testTerminalSecurityLockoutUiFlow()`: Clica 3 vezes seguidas num PIN errado; valida que surge o ecrã vermelho de bloqueio com o relógio decrescente e que o teclado tátil fica desativado.
  * `e2e/OperatorPlaywrightTest.java` (1 teste):
    * Abre o painel `/operator`, seleciona uma estação no seletor de localização e verifica se a grelha de cacifos (A01, A02, B01) é renderizada em tempo real com a respetiva taxa de ocupação.

---

### 7. Testes Não Funcionais de Desempenho e Carga (k6)
* **Princípio TQS:** Medição empírica de latência sob carga concorrente.
* **Ficheiro:** `scripts/performance_test.js`
  * **Cenários executados:**
    1. *Carga Nominal*: 10 VUs constantes a consultar estações próximas.
    2. *Spike Black Friday*: Subida repentina até 50 VUs criando encomendas em simultâneo.
    3. *Teste de Força Bruta*: 5 VUs disparando PINs errados para validar o rate limiting a nível de servidor.
  * **Resultados auditados:** 5.294 pedidos HTTP processados, 9.948 verificações com **100% de sucesso**, latência média de **2.1ms** e percentil **p95 de 5.5ms** (muito abaixo do limite de 200ms exigido).

---

### 8. Testes de Segurança e Proteção contra Força Bruta (Rate-Limiting)
* **Princípio TQS:** Verificação em várias camadas (*Defense in Depth*) contra ataques ao PIN.
* **Como está testado:**
  1. *Camada de Domínio*: `TerminalServiceTest.testTerminalLockoutAfterThreeFailedAttempts()` garante que o mapa de tentativas em memória é ativado com temporizador de 60 segundos.
  2. *Camada REST / HTTP*: `TerminalControllerTest.testPickupTerminalLockedReturns429()` valida o código HTTP **429 Too Many Requests** e o cabeçalho `Retry-After`.
  3. *Camada BDD*: Cenário Gherkin no ficheiro `security_lockout.feature`.
  4. *Camada E2E Visual*: `TerminalPlaywrightTest` testa o bloqueio do quiosque no browser.

---

### 9. Testes de Acessibilidade (WCAG 2.1 AA)
* **Princípio TQS:** Conformidade para quiosques públicos (utilizadores com deficiência motora ou visual).
* **Como está implementado e validado:**
  1. *Foco e Teclado*: Todas as ações táteis possuem `:focus-visible` com contorno de alto contraste (3px amarelo/azul) e suporte nativo a teclas físicas `Enter`, `Escape` e `Tab`.
  2. *Leitores de Ecrã*: Todos os alertas visuais têm atributos `role="alert"` e `aria-live="assertive"` para que o anúncio sonoro seja imediato.
  3. *Verificação*: Validado durante os testes Playwright ao navegar por seletores acessíveis (`[aria-label]`).

---

### 10. Testes de Injeção de Falhas e Resiliência (Fault Injection)
* **Princípio TQS:** O software tem de lidar graciosamente com falhas físicas de hardware sem crashar nem deixar dados inconsistentes.
* **Como está testado:**
  1. *Fake com Falha Injetada*: `InMemoryHardwareFake.setSimulateDoorJam(true)`.
  2. *Tratamento no Serviço*: `TerminalServiceTest.testPickupDoorJamEscalation()` garante que se a porta encravar, é emitido um log de erro crítico `Hardware fault: compartment door A01 jammed` e o estado da encomenda **não** é alterado para `PICKED_UP`.
  3. *Tratamento na API*: `TerminalControllerTest.testPickupHardwareFailureReturns500()` garante que a API devolve **500 Internal Server Error** em formato RFC 7807 (`hardware-failure`) em vez de silenciar o erro.
  4. *BDD*: Cenário Gherkin completo em `hardware_failure.feature`.
