# TuttiBox — Roteiro de Demonstração em Vídeo (<= 2 Minutos)
**Aluno:** Filipe Nogueira (119495)  
**Unidade Curricular:** TQS — Trabalho Prático Individual (HW1)  
**Duração Alvo:** 1m30s a 2m00s  

---

## 🎬 Minutagem Passo-a-Passo

### 1. Introdução & Arquitetura (0:00 - 0:20)
* **Visual**: Mostrar a página inicial do repositório GitHub ou Swagger UI (`/swagger-ui.html`).
* **Discurso**:
  > *"Olá, chamo-me Filipe Nogueira e apresento o TuttiBox, uma plataforma inteligente de gestão de cacifos (smart lockers) para a cidade de Aveiro, desenvolvida com Spring Boot 3.4 e PostgreSQL em Arquitetura Hexagonal (Ports & Adapters). A plataforma isola completamente regras de domínio e dependências externas através de portas desacopladas de persistência, hardware e geocodificação."*

---

### 2. Quiosque Tátil do Terminal & Recolha com PIN (0:20 - 0:50)
* **Visual**: Abrir no navegador `http://localhost:8080/terminal`.
* **Ações**:
  1. Mostrar o teclado tátil 0-9 e o mostrador de 6 pontos de PIN.
  2. Introduzir o PIN de uma encomenda pronta para levantamento através do teclado virtual.
  3. Clicar em **OK**.
  4. Observar a porta do cacifo abrir visualmente no painel de simulação cyber-física (solenóide ativado, badge verde).
  5. Clicar em **"Simular: Fechar Porta Fisicamente"**.
  6. Mostrar o feedback de sucesso e a transição para `PICKED_UP`.
* **Discurso**:
  > *"Aqui vemos o quiosque tátil do terminal em `/terminal`. O destinatário introduz o seu PIN de 6 dígitos no teclado tátil. Ao submeter, o sistema compara o hash com MessageDigest.isEqual em tempo constante, destranca o cacifo via sinal de hardware e, após o fecho físico da porta pelo cliente, o cacifo é automaticamente libertado e o estado passa a PICKED_UP."*

---

### 3. Mecanismo de Segurança (Lockout 60s) e Falha de Hardware (0:50 - 1:20)
* **Visual**: Continuar no terminal `/terminal`.
* **Ações**:
  1. Digitar 3 PINs incorretos consecutivamente (`000000`, `111111`, `222222`).
  2. Mostrar o ecrã completo de bloqueio com temporizador regressivo de 60 segundos (HTTP 429).
  3. Marcar a caixa **"Injetar Falha: Simular Tranca Solenóide Encravada (BR7)"** para demonstrar tolerância a falhas.
* **Discurso**:
  > *"Para prevenir ataques de força bruta, o sistema monitoriza tentativas consecutivas. À terceira tentativa inválida, o terminal entra em bloqueio de segurança durante 60 segundos com HTTP 429, rejeitando qualquer entrada subsequente. Além disso, suportamos injeção de falhas cyber-físicas: se o sensor magnético detetar um encravamento no fecho da porta, o compartimento é marcado como OUT_OF_SERVICE e a encomenda transita para WAITING_FOR_OPERATOR para intervenção manual."*

---

### 4. Painel do Operador & Concorrência (1:20 - 1:40)
* **Visual**: Abrir `http://localhost:8080/operator`.
* **Ações**:
  1. Mostrar a lista de estações de Aveiro com contadores de ocupação.
  2. Clicar numa estação e ver a grelha de cacifos com cores dinâmicas (verde = livre, vermelho = ocupado, amarelo = manutenção) e estado dos sensores de porta.
* **Discurso**:
  > *"No painel do operador em `/operator`, temos visão em tempo real de todas as estações e cacifos. Para garantir consistência sob carga concorrente, o TuttiBox utiliza Pessimistic Locking na base de dados PostgreSQL, verificado via Testcontainers, impedindo overbooking quando múltiplos estafetas competem pelo último cacifo."*

---

### 5. Pirâmide de Testes, Performance & SonarQube Cloud (1:40 - 2:00)
* **Visual**: Mostrar o terminal com `mvn test` (70 testes verdes) e o dashboard do SonarCloud.
* **Discurso**:
  > *"A qualidade do sistema assenta numa pirâmide de testes completa: 70 testes automatizados cobrindo testes unitários com fakes, fatiamento web com MockMvc, testes de aceitação BDD com Cucumber em Gherkin, e automação E2E de navegador com Microsoft Playwright. Testes de carga com Grafana k6 confirmaram p95 de 5.5ms e 0% de erros sob 60 utilizadores simultâneos. No SonarQube Cloud, o Quality Gate está APROVADO com 73.2% de cobertura de código, 0 bugs e 0 vulnerabilidades. Obrigado!"*
