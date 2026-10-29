# TuttiBox — Guia de Teste Manual e Preparação para a Defesa Oral (TQS 2026)
**Autor:** Filipe Nogueira (119495)  
**Projeto:** TuttiBox — Smart Parcel Lockers (Aveiro)  
**Objetivo:** Permitir ao aluno testar manualmente todas as regras de negócio, interfaces e cenários de erro, e dominar todas as perguntas teóricas e práticas que o docente ("stor") possa colocar na avaliação.

---

## 🚀 Passo 0: Como Arrancar a Aplicação para Testes Manuais

1. Abre um terminal na pasta do projeto:
   ```powershell
   cd "c:\Users\filip\OneDrive\Ambiente de Trabalho\tqs\HW1_TQS\HW1-119495"
   mvn spring-boot:run
   ```
2. Aguarda até surgir a mensagem:
   `Started TuttiBoxApplication in X seconds (process running for Y)`
3. A aplicação está agora acessível em:
   * **Quiosque Tátil do Terminal:** [http://localhost:8080/terminal](http://localhost:8080/terminal)
   * **Painel do Operador:** [http://localhost:8080/operator](http://localhost:8080/operator)
   * **Swagger OpenAPI (Documentação Interativa da API):** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

# PARTE 1: Guião de Testes Manuais Passo-a-Passo

---

### Teste Manual 1: Ciclo de Vida Completo (Happy Path — E-commerce até Recolha)
> **O que valida:** Criação de encomenda por parceiro, alocação de cacifo, entrega pelo estafeta e recolha pelo cliente no quiosque tátil.

1. **Criar Encomenda via Swagger / cURL:**
   * Abre o Swagger em [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) ou corre no terminal:
   ```bash
   curl -X POST http://localhost:8080/api/v1/parcels \
     -H "Content-Type: application/json" \
     -d '{"dimension":"S","recipientPhone":"+351912345678","recipientEmail":"cliente@ua.pt","stationCode":"STATION-AVEIRO-01"}'
   ```
   * **O que verificar na resposta (201 Created):**
     * Recebes um `trackingCode` (ex: `TB-2026-ABCD1234`).
     * Recebes um `pickupPin` de 6 dígitos (ex: `123456`).
     * Cacifo atribuído (ex: `A01`) em estado `RESERVED`.
     * `dropoffDeadline`: exatamente 48 horas no futuro (Regra BR2).

2. **Simular Depósito pelo Estafeta:**
   * Vai ao browser em [http://localhost:8080/terminal](http://localhost:8080/terminal).
   * Clica no separador **"🚚 Depósito (Estafeta)"**.
   * Insere o `trackingCode` recebido e clica em **"Destrancar Cacifo para Depósito"**.
   * **O que verificar:**
     * A porta abre visualmente no simulador cyber-físico (lado direito).
     * O registo de hardware regista o sinal magnético.
     * Clica em **"Simular: Fechar Porta Fisicamente"**.
     * A encomenda passa a `READY_FOR_PICKUP` e o cacifo fica `OCCUPIED`.

3. **Recolha pelo Cliente no Quiosque Tátil:**
   * Clica no separador **"📦 Levantamento (Destinatário)"**.
   * Introduz o PIN de 6 dígitos clicando nos botões táteis **`0-9`** (ou usando o teclado físico).
   * Repara nos pontos do PIN a preencher: `• • • • • •`.
   * Clica em **`OK`**.
   * **O que verificar:**
     * A porta do cacifo destranca e abre (gráfico verde inclinado).
     * Surge mensagem: *"Sucesso! Cacifo A01 aberto. Retire a sua encomenda."*
     * Clica em **"Simular: Fechar Porta Fisicamente"**.
     * A encomenda passa a `PICKED_UP`, o cacifo regressa a `FREE` (pronto para nova encomenda) e o ecrã reinicia limpo.

---

### Teste Manual 2: Regra de Negócio 1 (Alocação Dinâmica & Fallback S -> M)
> **Regra BR1:** Se todos os cacifos de tamanho `S` estiverem ocupados, a plataforma deve atribuir automaticamente um cacifo de tamanho `M`.

1. Abre o Swagger ou terminal.
2. Faz 3 pedidos consecutivos de tamanho `S` para a estação `STATION-AVEIRO-01`.
   * Como a estação CP Aveiro tem apenas 2 cacifos `S` (`A01`, `A02`):
   * O 1º pedido `S` vai para o cacifo `A01`.
   * O 2º pedido `S` vai para o cacifo `A02`.
   * **O 3º pedido `S` ativa a Regra 1 de Fallback:** é alocado no cacifo `B01` (tamanho `M`)!
3. Faz pedidos até esgotar também os cacifos `M`:
   * Ao tentar alocar mais um `S`, a API devolve erro **`409 Conflict`** com código `NO_LOCKERS_AVAILABLE` e mensagem clara RFC 7807 (sem crash 500).

---

### Teste Manual 3: Regra de Negócio 6 (Segurança & Bloqueio de 60 Segundos)
> **Regra BR6:** 3 tentativas consecutivas de PIN incorreto no terminal devem bloquear o quiosque por 60 segundos com `HTTP 429 Too Many Requests`.

1. Abre o quiosque em [http://localhost:8080/terminal](http://localhost:8080/terminal).
2. Introduz um PIN errado qualquer (ex: `000000`) e clica em **OK**.
   * **Feedback:** Mensagem a vermelho *"PIN Incorreto! Restam 2 tentativas antes do bloqueio."*
3. Introduz um 2º PIN errado (ex: `111111`) e clica em **OK**.
   * **Feedback:** *"PIN Incorreto! Restam 1 tentativas antes do bloqueio."*
4. Introduz o 3º PIN errado (ex: `222222`) e clica em **OK**.
   * **O que verificar (Efeito imediato de segurança):**
     * O ecrã fica coberto por um ecrã de bloqueio escuro (*lockout overlay*).
     * Ícone de cadeado 🔒 pulsante com título **"TERMINAL BLOQUEADO"**.
     * Temporizador regressivo em contagem decrescente: `60s... 59s... 58s...`
     * Tenta clicar nos botões ou usar o teclado: o terminal ignora completamente.
     * Na API REST, qualquer pedido devolve **`HTTP 429`**.
5. Espera os 60 segundos terminarem:
   * O ecrã desbloqueia automaticamente, volta a ficar disponível e emite log: *"Período de bloqueio expirou. Terminal reativado."*

---

### Teste Manual 4: Regra de Negócio 7 (Tolerância a Falhas de Hardware & Porta Encravada)
> **Regra BR7:** Se a porta encravar ao fechar (tranca solenóide não fecha ou sensor magnético deteta obstrução), a encomenda deve transitar para `WAITING_FOR_OPERATOR` e o cacifo ser colocado em `OUT_OF_SERVICE`.

1. Cria uma encomenda e faz o depósito pelo estafeta para ficar pronta para levantamento.
2. No terminal `/terminal`, **marca a checkbox:**
   ☑ **"Injetar Falha: Simular Tranca Solenóide Encravada (BR7)"**.
3. Introduz o PIN correto da encomenda e clica em **OK**.
4. **O que verificar:**
   * O visualizador de porta fica a vermelho vivo com contorno de aviso.
   * Mensagem no ecrã: *"⚠️ Tranca Encravada • Alerta Operador"*.
   * Log de hardware: `[HARDWARE ALERTA] Tranca encravada! Compartimento colocado em OUT_OF_SERVICE.`
   * Na base de dados, a encomenda transita para `WAITING_FOR_OPERATOR` e o cacifo para `OUT_OF_SERVICE`.

---

### Teste Manual 5: Painel do Operador e Monitorização em Tempo Real
> **O que valida:** Capacidade do operador humano supervisionar as estações de Aveiro e verificar o estado físico de cada porta.

1. Abre no browser: [http://localhost:8080/operator](http://localhost:8080/operator).
2. **Ecrã de Estações:**
   * Visualiza os cartões das 6 estações de Aveiro (Estação CP, Universidade, Fórum, Hospital, Glicínias, Cais da Fonte Nova).
   * Confirma as badges de ocupação: cacifos livres vs total.
3. Clica na **"Estação CP Aveiro"**:
   * Acede à grelha individual dos cacifos (`A01`, `A02`, `B01`, `C01`, etc.).
   * **Cores de estado:**
     * Verde suave = Livre (`FREE`).
     * Vermelho suave = Ocupado / Reservado (`OCCUPIED` / `RESERVED`).
     * Amarelo suave = Avariado / Manutenção (`OUT_OF_SERVICE`).
   * **Estado do sensor magnético:** `🔒 Porta Fechada` (vermelho) ou `🔓 Porta Aberta` (verde).
4. Clica em **"← Voltar às Estações"** e confirma a transição suave de vistas sem recarregar a página.

---

### Teste Manual 6: Algoritmo Haversine & Geocodificação de Estações Próximas
> **O que valida:** Ordenação precisa de cacifos pela distância real ao utilizador com normalização de acentos em português.

1. Testa a pesquisa por morada de Aveiro via browser ou curl:
   ```bash
   curl "http://localhost:8080/api/v1/stations/nearby?address=Campus+Universitario+de+Santiago"
   ```
2. **O que verificar na resposta JSON:**
   * A 1ª estação da lista é a **"Universidade de Aveiro"** com distância de ~0.0 km.
   * As restantes estações vêm estritamente ordenadas por distância crescente calculada pela fórmula matemática de Haversine (`distanceKm`).
3. Testa por coordenadas GPS (ex: Centro de Aveiro `lat=40.6405&lon=-8.6538`):
   ```bash
   curl "http://localhost:8080/api/v1/stations/nearby?lat=40.6405&lon=-8.6538"
   ```
   * A estação do Fórum Aveiro surge em 1º lugar com a distância exata em km.

---

# PARTE 2: Perguntas Típicas do Professor ("Stor") e Como Responder para 20/20

---

### P1: "Porque é que usaste Fakes em vez de Mocks para o hardware e geocodificação?"
* **Resposta de 20/20:**
  > *"Usei Fakes em memória (`InMemoryHardwareFake` e `InMemoryGeocodingFake`) porque implementam o contrato completo da interface do domínio (`LockerHardwarePort` e `GeocodingPort`) mantendo estado real interno (ex: se a porta está aberta ou fechada e se a tranca encravou). Isso permite testar cenários complexos de negócio e injeção de falhas com comportamento determinístico e sem chamadas de rede lentas ou instáveis. Os Mocks do Mockito foram reservados exclusivamente para isolar as fronteiras de serviços e repositórios nos testes unitários clássicos."*

---

### P2: "Onde está a Arquitetura Hexagonal no teu código e como garante a separação de responsabilidades?"
* **Resposta de 20/20:**
  > *"A arquitetura divide-se estritamente em Core (Domínio e Serviços) e Infraestrutura (Adaptadores):*
  > * *O **Core** (`pt.ua.deti.tqs.tuttibox.core`) contém as entidades (`Parcel`, `LockerStation`, `Compartment`), a máquina de estados e as regras de negócio puras. Não depende de frameworks web nem de bibliotecas externas.*
  > * *As **Portas** (`core.ports.out`) definem os contratos que o domínio exige para funcionar: `LockerHardwarePort`, `GeocodingPort` e `NotificationPort`.*
  > * *Os **Adaptadores** (`infrastructure.adapters`) implementam essas portas para tecnologias concretas: controladores REST (`in.web`), persistência JPA/PostgreSQL (`out.persistence`) e simulação de hardware (`out.hardware`).*
  > * *Graças à **Inversão de Dependências**, o domínio é 100% testável em isolamento sem base de dados e sem hardware real."*

---

### P3: "Como é que garantiste que dois estafetas não reservam o mesmo último cacifo ao mesmo tempo (concorrência)?"
* **Resposta de 20/20:**
  > *"Utilizei **Pessimistic Locking** ao nível da linha na base de dados PostgreSQL. No repositório JPA, a consulta de seleção de cacifos livres está anotada com `@Lock(LockModeType.PESSIMISTIC_WRITE)`, o que emite a instrução SQL `SELECT ... FOR UPDATE`. Quando duas transações concorrentes tentam alocar o mesmo cacifo em simultâneo, a primeira obtém o lock exclusivo e reserva o cacifo; a segunda aguarda e, ao inspecionar a linha, vê que o estado já é `RESERVED`, procurando o próximo ou devolvendo `NO_LOCKERS_AVAILABLE`. Validámos este comportamento com um teste de integração de concorrência com 4 threads simultâneas via Testcontainers."*

---

### P4: "Qual foi o bug crítico que encontraste ao ligar a base de dados real e como o corrigiste?"
* **Resposta de 20/20:**
  > *"Detetámos dois bugs reais fundamentais:*
  > 1. *No `TerminalService`, o `@Transactional` do Spring fazia rollback por omissão ao lançar a exceção de negócio `DomainRuleException`. Isto apagava o contador de PINs errados da base de dados, contornando o bloqueio de segurança! Corrigimos com `@Transactional(noRollbackFor = DomainRuleException.class)` para persistir as tentativas falhadas e estados de avaria.*
  > 2. *No `StationController`, ocorria `LazyInitializationException` ao aceder à lista de cacifos fora de sessão. Corrigimos com `@EntityGraph(attributePaths = {\"compartments\"})` no repositório, que faz um `JOIN FETCH` num único query SQL otimizado, eliminando o problema do N+1 e acelerando a resposta."*

---

### P5: "Qual a diferença entre os teus testes de fatiamento (@WebMvcTest), de aceitação (Cucumber) e E2E (Playwright)?"
* **Resposta de 20/20:**
  > * **Slice Tests (`@WebMvcTest`):** Testam apenas a camada web (rotas, serialização JSON e códigos HTTP de erro RFC 7807) simulando o serviço com `@MockBean`. São muito rápidos.
  > * **Testes de Aceitação BDD (`Cucumber` com Gherkin):** Formalizam a perspetiva do utilizador e do negócio em ficheiros `.feature` legíveis por humanos (`Given`, `When`, `Then`). Conduzem a camada de serviços e repositórios para validação determinística de regras.
  > * **Testes End-to-End (`Microsoft Playwright`):** Executam o navegador Chromium real em modo *headless*. Clicam nos botões táteis do quiosque `/terminal`, inspecionam o DOM dinâmico, verificam tempos de transição visual e validam a contagem decrescente do ecrã de bloqueio sem recurso a pausas arbitrárias (*flakiness-free*).

---

### P6: "Quais foram os resultados dos testes de carga com k6 e que metas (thresholds) definiste?"
* **Resposta de 20/20:**
  > *"Configurámos thresholds rigorosos no k6: latência p95 < 250ms e p99 < 500ms. Durante 43 segundos de teste sob 3 estágios (carga normal, pico Black Friday de 60 utilizadores virtuais em rajada e ataque de força bruta):*
  > * *Processámos **5.294 pedidos HTTP** (9.948 verificações automáticas com **100% de sucesso** e 0 falhas).*
  > * *O tempo de resposta global foi de **p95 = 5.5ms** e **p99 = 10.74ms**, muito abaixo do limite contratual.*
  > * *A criação de encomendas respondeu em **p95 = 4.80ms** e a pesquisa de cacifos em **p95 = 7.42ms**, provando a resiliência do pool HikariCP e do motor de persistência."*

---

### P7: "Como garantiste a acessibilidade (WCAG 2.1 AA) no quiosque e na dashboard?"
* **Resposta de 20/20:**
  > *"Auditámos as páginas `/terminal` e `/operator` garantindo três princípios fundamentais:*
  > 1. *Contraste e Foco Visível: Adicionámos regras `:focus-visible` com anéis de foco azuis de 3px e rácio de contraste superior a 4.5:1.*
  > 2. *Acessibilidade por Teclado: Qualquer utilizador consegue efetuar uma recolha completa sem rato nem ecrã tátil, usando apenas Tab, teclas 0-9, Enter e Backspace.*
  > 3. *Leitores de Ecrã (Semântica ARIA): Os botões têm `aria-label` explícito (`Dígito 1`, `Limpar PIN`), as mensagens de erro têm `role=\"status\"` com `aria-live=\"polite\"` e o ecrã de bloqueio de 60s emite alerta prioritário com `role=\"alert\"` e `aria-live=\"assertive\"`."*

---

### P8: "Qual é a tua política de Quality Gate no SonarQube Cloud?"
* **Resposta de 20/20:**
  > *"O nosso Quality Gate está **PASSED (OK)** no SonarCloud com **73.2% de cobertura de código por testes** em 38 classes analisadas, **0 Bugs**, **0 Vulnerabilidades** e **0 Security Hotspots**. A cobertura foca-se na lógica de negócio e serviços transacionais. Os relatórios XML do JaCoCo são automaticamente injetados no SonarScanner pelo GitHub Actions em cada push para a branch main."*

---

## 📋 Resumo das Regras de Negócio para Consulta Rápida
| Regra | Nome | Descrição & Parâmetro |
| :--- | :--- | :--- |
| **BR1** | Alocação Dinâmica | Aloca tamanho exato (`S`, `M`, `L`). Se `S` estiver esgotado, faz fallback automático para `M`. |
| **BR2** | Prazo de Depósito | O estafeta tem **48 horas** após a reserva para depositar a encomenda; caso contrário, é cancelada. |
| **BR3** | Prazo de Levantamento | O cliente tem **72 horas** para levantar após o depósito; expirado o prazo, passa a `EXPIRED`. |
| **BR4** | PIN Criptográfico | PIN aleatório de **6 dígitos** (`000000` a `999999`) guardado em hash **SHA-256**. |
| **BR5** | Comparação Constant-Time | Comparação de PIN via `MessageDigest.isEqual` para prevenir ataques de temporização (*timing attacks*). |
| **BR6** | Bloqueio por Força Bruta | **3 tentativas erradas consecutivas** bloqueiam o quiosque durante **60 segundos** (`HTTP 429`). |
| **BR7** | Tolerância a Falhas | Encravamento de porta coloca compartimento em `OUT_OF_SERVICE` e encomenda em `WAITING_FOR_OPERATOR`. |
| **BR8** | Distância Haversine | Pesquisa de estações ordenadas pela fórmula trigonométrica de Haversine com normalização NFD. |
