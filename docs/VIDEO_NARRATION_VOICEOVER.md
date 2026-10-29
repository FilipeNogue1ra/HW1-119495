# 🎙️ Guião de Narração do Vídeo de Demonstração (TuttiBox)
**Autor:** Filipe Nogueira (119495)  
**Duração:** ~1 minuto e 45 segundos  
**Ficheiro de Vídeo:** `docs/tuttibox_demo.webm`

---

### [00:00 - 00:15] — Cena 1: Documentação da API (OpenAPI & Swagger)
* **O que está no ecrã:** Abertura da página do Swagger UI (`/swagger-ui.html`) e scroll pelos endpoints da API REST.
* **O que deves dizer:**
> *"Olá, chamo-me Filipe Nogueira e esta é a demonstração prática da plataforma **TuttiBox**, desenvolvida no âmbito do midterm de TQS.*  
> *Começamos pela documentação viva da API em OpenAPI e Swagger UI. Aqui temos expostos todos os endpoints REST de gestão de encomendas, estações de cacifos inteligentes e operações de quiosque, com tratamento rigoroso de erros segundo a norma RFC 7807 Problem Details."*

---

### [00:15 - 00:42] — Cena 2: Depósito de Encomenda pelo Estafeta (Quiosque)
* **O que está no ecrã:** Navegação para o ecrã do quiosque (`/terminal`), clique na aba de estafeta, preenchimento do código de rastreio `TB-2026-...`, destrancamento da porta B01 (fica verde) e clique em "Simular: Fechar Porta".
* **O que deves dizer:**
> *"Passamos agora ao Quiosque Cyber-Físico. O ecrã da esquerda simula a interface tátil do cacifo e a coluna da direita simula os sensores magnéticos e solenóides de hardware.*  
> *Como estafeta, seleciono o modo de depósito e introduzo o código de rastreio da encomenda previamente alocada. Ao confirmar, o sistema destranca imediatamente o compartimento B01. Na consola de hardware vemos o sinal elétrico disparado e a porta aberta. Ao colocarmos a encomenda e fecharmos a porta fisicamente, os sensores magnéticos armam a tranca e o estado transita para 'Pronto para Levantamento'."*

---

### [00:42 - 01:08] — Cena 3: Levantamento pelo Cliente com Código PIN
* **O que está no ecrã:** Mudança para a aba "Levantamento", digitação do PIN de 6 dígitos no teclado numérico, clique em "OK", porta abre, simulação do fecho da porta e mensagem de sucesso.
* **O que deves dizer:**
> *"De seguida, simulamos a experiência do cliente destinatário. No separador de levantamento, o cliente usa o teclado tátil para inserir o seu PIN seguro de 6 dígitos recebido por SMS ou email.*  
> *Ao carregar em OK, o sistema valida as credenciais contra a base de dados com locking pessimista, destranca a porta certa e permite a recolha da encomenda. Assim que a porta é fechada, a encomenda é dada como entregue com sucesso."*

---

### [01:08 - 01:28] — Cena 4: Segurança Ativa — Bloqueio Anti-Força Bruta
* **O que está no ecrã:** Digitação rápida de 3 PINs errados (111111, 222222, 333333) e surgimento do ecrã vermelho de bloqueio: "TERMINAL BLOQUEADO - 60s".
* **O que deves dizer:**
> *"Uma das regras de negócio críticas do TuttiBox é a defesa em profundidade. Se um atacante tentar adivinhar códigos PIN por força bruta, após 3 tentativas inválidas consecutivas o terminal bloqueia-se automaticamente.*  
> *Como vemos aqui, surge imediatamente um bloqueio de 60 segundos com contagem decrescente, rejeitando qualquer interação física ou via API até expirar o tempo de segurança."*

---

### [01:28 - 01:45] — Cena 5: Cockpit e Monitorização da Frota pelo Operador
* **O que está no ecrã:** Abertura da página `/operator`, visualização dos cartões das 6 estações em Aveiro, taxas de ocupação dos cacifos S, M e L, e registos de auditoria.
* **O que deves dizer:**
> *"Por fim, temos o Cockpit do Operador de Frota. Neste painel centralizado, a empresa de logística supervisiona em tempo real a capacidade e ocupação das 6 estações espalhadas por Aveiro — desde o Campus de Santiago até à Estação da CP — acompanhando a disponibilidade de compartimentos S, M e L e os registos de auditoria em tempo real.*  
> *Toda a solução foi coberta por 70 testes automatizados, integrando BDD com Cucumber, Playwright, Testcontainers e análise estática no SonarCloud com zero issues."*
