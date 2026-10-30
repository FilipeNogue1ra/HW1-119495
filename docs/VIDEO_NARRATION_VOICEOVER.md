# 🎙️ Guião de Narração do Vídeo de Demonstração (TuttiBox)
**Autor:** Filipe Nogueira (119495)  
**Duração:** 1 minuto e 30 segundos  
**Ficheiro de Vídeo:** `docs/tuttibox_demo.webm`

---

### [00:00 - 00:16] — Cena 1: UC2 — Registo de Encomenda via REST API (Swagger UI)
* **O que está no ecrã:** Abertura da página do Swagger UI (`/swagger-ui.html`), expansão de `POST /api/v1/parcels`, clique em "Try it out", envio do payload JSON e receção imediata de `HTTP 201 Created` com o `trackingCode` e `pickupPin` atribuídos.
* **O que deves dizer:**
> *"Olá, chamo-me Filipe Nogueira e apresento a demonstração prática do TuttiBox, o sistema inteligente de gestão de cacifos desenvolvido para TQS.*  
> *Começamos pelo Caso de Uso 2: um parceiro de comércio eletrónico regista uma nova encomenda via REST API. No Swagger UI, executamos o pedido POST e recebemos imediatamente o código HTTP 201 Created, com o código de rastreio e o PIN de levantamento gerados com segurança."*

---

### [00:16 - 00:30] — Cena 2: UC1 — Pesquisa Georreferenciada de Cacifos por Morada
* **O que está no ecrã:** Expansão do endpoint `GET /api/v1/stations/nearby`, introdução da morada `"Universidade de Aveiro"`, clique em "Execute" e apresentação da lista ordenada com cálculo de distância (`distanceKm`) via serviço de geocodificação Nominatim.
* **O que deves dizer:**
> *"De seguida, temos o Caso de Uso 1: a localização inteligente de cacifos. Pesquisando pela morada 'Universidade de Aveiro', o nosso adaptador de geocodificação converte a morada em coordenadas geográficas e devolve as estações mais próximas ordenadas por distância e com a contagem de compartimentos livres por tamanho."*

---

### [00:30 - 00:46] — Cena 3: UC3 — Depósito pelo Estafeta no Quiosque Cyber-Físico
* **O que está no ecrã:** Navegação para `/terminal`, seleção da aba "Depósito (Estafeta)", introdução do código de rastreio acabado de criar, clique em "Destrancar", abertura física da porta (badge verde e registo de sinal elétrico) e simulação do fecho da porta.
* **O que deves dizer:**
> *"Passamos agora ao Quiosque do Terminal (Caso de Uso 3). O estafeta seleciona o modo de depósito e introduz o código de rastreio da encomenda. Ao confirmar, o sistema aciona o solenóide de hardware, destranca o cacifo reservado e, ao fechar fisicamente a porta, os sensores magnéticos armam a tranca e a encomenda fica pronta para levantamento."*

---

### [00:46 - 01:02] — Cena 4: UC4 — Levantamento pelo Cliente com Código PIN
* **O que está no ecrã:** Mudança para a aba "Levantamento", digitação do PIN de 6 dígitos no teclado tátil virtual, clique em "OK", porta destrancada, recolha da encomenda e clique em "Fechar Porta".
* **O que deves dizer:**
> *"No Caso de Uso 4, simulamos o cliente destinatário. No ecrã tátil, o cliente digita o seu PIN de 6 dígitos. A validação é feita em tempo constante para evitar timing attacks, a tranca abre, o cliente retira a encomenda e, após o fecho da porta, o cacifo é libertado com sucesso."*

---

### [01:02 - 01:15] — Cena 5: Defesa Ativa contra Ataques de Força Bruta (Regra BR6)
* **O que está no ecrã:** Digitação rápida de 3 PINs incorretos (`111111`, `222222`, `333333`) ativando o ecrã vermelho de bloqueio: "TERMINAL BLOQUEADO - 60s" com temporizador regressivo.
* **O que deves dizer:**
> *"Como requisito de segurança e resiliência (Regra de Negócio 6), se forem inseridos 3 PINs inválidos consecutivos, o terminal bloqueia-se imediatamente durante 60 segundos com HTTP 429, prevenindo tentativas de força bruta no quiosque."*

---

### [01:15 - 01:30] — Cena 6: UC6 — Cockpit do Operador e Manutenção de Avarias
* **O que está no ecrã:** Navegação para `/operator`, visualização das estações e taxas de ocupação, clique numa estação para inspecionar os cacifos e alternância de avaria (*Sinalizar Avaria*) colocando o cacifo em manutenção `OUT_OF_SERVICE`.
* **O que deves dizer:**
> *"Por fim, no Caso de Uso 6, temos o Cockpit de Supervisão do Operador. Aqui monitorizamos em tempo real a ocupação de todas as estações de Aveiro, inspecionamos o estado dos sensores de cada porta e podemos colocar compartimentos avariados fora de serviço para manutenção.*  
> *Com isto, o TuttiBox cobre 100% dos use cases com 70 testes automatizados e SonarCloud Aprovado com zero issues. Obrigado!"*
