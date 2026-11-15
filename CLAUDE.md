# CLAUDE.md — DistriSchool (raiz do monorepo)

Este arquivo descreve a **tarefa atual** do projeto DistriSchool. Leia-o inteiro antes de gerar
qualquer código. Os arquivos `CLAUDE.md` dentro de `gestao-administrativa-service/` e
`gestao-escolar-service/` descrevem o escopo de cada serviço; as "tarefas" escritas neles já foram
concluídas.

## Contexto

Plataforma de gestão escolar em microsserviços (projeto final de Sistemas Distribuídos). O
documento de requisitos do professor pede, entre outros itens ainda não implementados:

- **Serviço de Comunicação**: "Kafka + Spring Cloud Stream — notificações e avisos", com
  **WebSocket** para notificações em tempo real no frontend
- **Monitoramento**: Prometheus + Grafana, com métricas de latência e throughput e alertas
- **Avaliação**: testes de carga com JMeter

Esta tarefa implementa esses três itens.

### Estado atual (não altere o que não for necessário)

| Componente | Porta | Banco | Observações |
|---|---|---|---|
| `api-gateway` | 8080 | — | Spring Cloud Gateway **Server MVC**; valida o JWT; circuit breaker, retry de GET e rate limit do login (10/min por IP, por réplica) |
| `gestao-administrativa-service` | 8081 | `postgres-administrativa` (15432) | Professores, técnicos, turmas, disciplinas |
| `gestao-escolar-service` | 8082 | `postgres-escolar` (15433) | Alunos, matrículas, frequência, notas, boletim |
| `usuarios-service` | 8083 | `postgres-usuarios` (15434) | Login e emissão do JWT (HS256, `JWT_SECRET`) |
| `frontend` | 5173 | — | React + Vite + TypeScript + Tailwind; nginx no Compose |
| Kafka | 9092 (host) / 29092 (rede do Compose) / 9092 (Kubernetes) | — | KRaft, 1 nó |

- Stack: Java 21, Spring Boot 4.1.1, Spring Cloud `2025.1.4` (mesma versão do `api-gateway/pom.xml`),
  Resilience4j 2.4.0 (`resilience4j-spring-boot4`), PostgreSQL 17 + Flyway, Spring Kafka 4 / Jackson 3.
- Os produtores gravam eventos numa **transactional outbox** (`outbox_evento`) e o `OutboxRelay`
  publica no Kafka. A entrega é **pelo menos uma vez**: o mesmo evento pode chegar mais de uma vez,
  inclusive com offsets diferentes. Todo consumidor precisa ser idempotente.
- Os payloads são JSON **sem cabeçalhos de tipo Java**.
- Todos os serviços expõem `/actuator/prometheus`; no Kubernetes os pods já têm as anotações
  `prometheus.io/scrape|port|path`.
- O JWT tem as claims `sub` (id do usuário), `nome`, `email`, `perfil` (`ADMIN`, `PROFESSOR`,
  `ALUNO`, `RESPONSAVEL`) e `referenciaId` (id do professor para `PROFESSOR`; id do aluno para
  `ALUNO` e `RESPONSAVEL`). Veja `docs/AUTENTICACAO.md`.
- `scripts/e2e.ps1` testa os fluxos de ponta a ponta pelo gateway (Windows PowerShell 5.1, só
  ASCII). Ele precisa continuar passando.

### Leia antes de começar

1. `README.md`, `docs/AUTENTICACAO.md`, `k8s/README.md`, `docker-compose.yml`
2. `gestao-escolar-service/CLAUDE.md` (padrões, contrato dos eventos)
3. `scripts/e2e.ps1` (payloads atuais das APIs; algumas usam o número de matrícula do aluno)
4. `api-gateway/.../config/GatewayConfig.java` e `SecurityConfig.java`
5. O pacote `event/` do `gestao-escolar-service` (outbox, `KafkaTopics`, records dos eventos)

## Regras gerais

- Execute as etapas **em ordem**. Ao fim de cada uma, rode os testes dos módulos afetados e faça
  **um commit por etapa** (mensagens no estilo do repositório: `feat: ...`, `fix: ...`,
  `docs: ...`). Não faça push.
- Siga os padrões existentes: pacote por módulo, `ProblemDetail` via `ApiExceptionHandler`,
  schema só pelo Flyway (`ddl-auto=validate`), **nunca** edite migrations aplicadas, `Dockerfile`
  multi-stage com usuário de UID numérico, probes do Actuator, HPA, PDB, ConfigMap e Secret.
- Comentários e mensagens em português, como no resto do código.
- **LGPD**: payloads de eventos e o texto das notificações têm dados de alunos. Logs trazem só ids,
  tópico, chave e tipo, nunca payload, nome ou email.
- Fixe versões de imagens Docker, dependências Maven e pacotes npm. Nunca use `latest`.
- Antes de adicionar uma dependência, confirme que ela é compatível com Spring Boot 4.1 / Spring
  Cloud 2025.1. Se algo exigido aqui não for compatível, **pare e explique** antes de trocar de
  abordagem; registre a decisão no README do módulo.
- O ambiente de desenvolvimento é Windows (PowerShell) com Docker Desktop e minikube. Scripts
  novos para o usuário rodar são `.ps1` compatíveis com o Windows PowerShell 5.1; arquivos que
  rodam em Linux (`mvnw`, `.sh`) usam LF (o `.gitattributes` já cobre `mvnw`).
- Ao terminar cada etapa, atualize a documentação correspondente (README da raiz,
  `k8s/README.md`, `docs/`).

## Fora do escopo

- Restringir o que `ALUNO`/`RESPONSAVEL` podem consultar nas APIs existentes (problema conhecido,
  será tratado depois). Só as rotas novas de notificações precisam filtrar pelo dono.
- ELK, Redis, gRPC, serviço de relatórios, Terraform, CI/CD.
- Envio real de email ou push mobile.

---

## Parte A — `notificacao-service`

### Etapa 1 — Esqueleto, consumo dos eventos e persistência

Crie `notificacao-service/` com a mesma base dos outros serviços (Maven Wrapper, `Dockerfile`,
`.dockerignore`, pacote `common/` copiado e adaptado). Os serviços não compartilham código.

- `artifactId` e `spring.application.name`: `notificacao-service`; pacote
  `com.distrischool.notificacao`; porta **8084**.
- Banco próprio `notificacao` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; padrão
  `jdbc:postgresql://localhost:15435/notificacao`).
- Swagger sob o prefixo `/notificacao/swagger-ui.html` e `/notificacao/v3/api-docs`.

**Mensageria: Spring Cloud Stream com o binder Kafka** (exigência do documento), no estilo
funcional (`java.util.function.Consumer` + bindings `<nome>-in-0`). Use o BOM do Spring Cloud na
mesma versão do gateway.

- Grupo de consumidores: `notificacao-service` em todos os bindings de domínio. Isso demonstra
  publish/subscribe: o `schedule.updated` é lido pelo `gestao-escolar-service` (grupo próprio) e
  também por este serviço.
- Falhas: 3 tentativas com backoff exponencial (1 s, 2 s) e depois DLQ no tópico
  `<tópico>.DLT` (mesma convenção do `gestao-escolar-service`; o padrão do binder é outro, então
  configure `dlqName` explicitamente). Payload fora do contrato não é repetido: vai direto para a DLT.
- Leia o JSON sem depender de classes dos outros serviços e ignore campos desconhecidos.

**Tópicos consumidos e notificações geradas**

O destinatário de uma notificação é o par (`perfil`, `referenciaId`), o mesmo que vai no JWT.
`ADMIN` tem `referenciaId` nulo e representa a coordenação (todos os administradores veem as
mesmas notificações).

| Tópico | Produtor | Destinatários | Exemplo de texto |
|---|---|---|---|
| `grade.posted` | escolar | `ALUNO` e `RESPONSAVEL` de cada `alunoId` em `notas` | "Nova nota em Matemática (1º bimestre): 8,5" |
| `attendance.recorded` | escolar | `ALUNO` e `RESPONSAVEL` de cada `alunoId` em `ausencias` | "Falta registrada em Matemática em 10/11/2026" |
| `enrollment.created` | escolar | `ALUNO` e `RESPONSAVEL` do `alunoId` | "Matrícula confirmada na turma 9º Ano A (2026)" |
| `enrollment.updated` | escolar | `ALUNO` e `RESPONSAVEL` do `alunoId`; `ADMIN` quando `statusNovo` for `CANCELADA` ou `TRANSFERIDA` | "Sua matrícula na turma 9º Ano A foi trancada" |
| `student.created` | escolar | `ADMIN` | "Aluno cadastrado: Ana Souza (matrícula 2026000001)" |
| `teacher.assigned` | administrativa | `PROFESSOR` do `professorId` | "Você foi atribuído à turma 9º Ano A" |
| `staff.created` | administrativa | `ADMIN` | "Técnico administrativo cadastrado: João Lima (Secretaria)" |
| `schedule.updated` | administrativa | — | Só atualiza o cache de nomes (abaixo) |

Os payloads estão nos records de `gestao-escolar-service/.../event/` e
`gestao-administrativa-service/.../event/`. Confira os campos reais antes de escrever os DTOs.

**Cache local de nomes.** `grade.posted`, `attendance.recorded` e `enrollment.*` só trazem ids.
Para textos legíveis, mantenha as tabelas `turma_nome` (`turma_id`, `codigo`, `nome`,
`ultimo_evento_em`) e `disciplina_nome` (`disciplina_id`, `nome`, `ultimo_evento_em`) alimentadas
pelo `schedule.updated` (upsert; ignore eventos com `occurredAt` mais antigo). Se o nome não
estiver no cache, use um texto neutro ("turma #12"). Nunca chame outro serviço por HTTP aqui.

**Modelo de dados (banco `notificacao`)**

- `notificacao`: `id`, `destinatario_perfil`, `destinatario_referencia_id` (nulo para `ADMIN`),
  `tipo` (`NOTA_LANCADA`, `FALTA_REGISTRADA`, `MATRICULA_CRIADA`, `MATRICULA_ALTERADA`,
  `ALUNO_CADASTRADO`, `PROFESSOR_ATRIBUIDO`, `TECNICO_CADASTRADO`), `titulo`, `mensagem`, `link`
  (rota do frontend, ex.: `/alunos/12`), `criada_em`, `lida_em` (nulo = não lida),
  `evento_hash`.
- **Idempotência**: os eventos não têm id. Calcule `evento_hash` = SHA-256 de
  `tópico + chave + payload bruto` e crie um índice único em
  (`evento_hash`, `destinatario_perfil`, `destinatario_referencia_id`). Reentrega do mesmo evento
  não gera notificação duplicada (insert que viola o índice é ignorado, sem erro).
- Índices para a consulta do destinatário (`destinatario_perfil`, `destinatario_referencia_id`,
  `criada_em DESC`) e para não lidas.
- `CHECK` nos enums.

**API REST** (todas exigem JWT; o serviço valida o token com o mesmo `JWT_SECRET`, como resource
server, e identifica o destinatário pelas claims `perfil` e `referenciaId`; não confie em
parâmetros para isso):

- `GET /api/notificacoes` — paginado, mais recentes primeiro; filtro `somenteNaoLidas`
- `GET /api/notificacoes/nao-lidas/contagem` — `{ "total": 3 }`
- `PATCH /api/notificacoes/{id}/lida` — 404 se a notificação não for do destinatário
- `POST /api/notificacoes/lidas` — marca todas as do destinatário como lidas

Confirme, com um teste, que o gateway repassa o cabeçalho `Authorization` para o serviço.

**Métricas próprias** (Micrometer): contador `notificacoes_criadas_total` por `tipo`, contador
`notificacoes_enviadas_total` por canal (`websocket`) e gauge de sessões WebSocket ativas.

**Testes**: unitários para o mapeamento evento → notificações de cada tópico; idempotência (o mesmo
evento duas vezes gera uma notificação por destinatário); evento antigo do `schedule.updated`
ignorado; payload inválido vai para a DLT (use o test binder do Spring Cloud Stream);
`@WebMvcTest` do controller, incluindo 401 sem token e 404 para notificação de outro destinatário.

### Etapa 2 — Notificações em tempo real por WebSocket

- WebSocket com **STOMP**, endpoint `/ws` (sem SockJS, para não exigir sessão fixa no
  balanceador).
- Autenticação: o navegador não envia cabeçalhos no handshake, então o token vai no frame
  `CONNECT` (`Authorization: Bearer <token>`). Valide num `ChannelInterceptor`; token ausente ou
  inválido recusa a conexão. O `Principal` da sessão é o destinatário (`ALUNO:12`, `PROFESSOR:7`,
  `ADMIN`).
- Entrega com `convertAndSendToUser(<principal>, "/queue/notificacoes", dto)`.
- Heartbeats STOMP de 10 s nos dois sentidos.
- **Várias réplicas**: o consumidor de domínio roda em um grupo compartilhado, então o pod que
  grava a notificação pode não ser o pod em que o usuário está conectado. Resolva assim:
  1. depois do commit da notificação, publique um evento interno no tópico
     `notificacao.criada` (via `StreamBridge`), com o id e o destinatário;
  2. todas as réplicas consomem esse tópico em **grupo anônimo** (cada instância recebe todas as
     mensagens) e entregam às sessões conectadas nelas.
     Documente essa decisão (competing consumers × fan-out). Se o envio interno falhar, a notificação
     continua gravada e aparece no próximo carregamento da lista.
- Testes: conexão sem token é recusada; mensagem enviada ao destinatário certo e não aos outros.

### Etapa 3 — Gateway, Compose e Kubernetes

**WebSocket e o gateway.** O gateway é o Spring Cloud Gateway **Server MVC**. Verifique na
documentação da versão em uso se ele faz proxy de WebSocket. O mais provável é que não faça;
nesse caso, o `/ws` vai **direto** para o `notificacao-service` (nginx do frontend, proxy do Vite e
Ingress), e o próprio serviço valida o JWT. Documente essa exceção em `docs/AUTENTICACAO.md`
("o gateway é o único ponto que valida o token, exceto no WebSocket de notificações").

**`api-gateway`**:
- Rota `notificacao` para `/api/notificacoes/**` e `/notificacao/**` (Swagger), com o mesmo filtro
  `resiliente(...)` das outras rotas e a instância de circuit breaker `notificacao`
  (`base-config=default`). Nova propriedade `gateway.notificacao-url` / `NOTIFICACAO_URL`
  (padrão `http://localhost:8084`).
- `SecurityConfig`: `/api/notificacoes/**` liberado para **qualquer perfil autenticado**
  (todos os métodos), declarado **antes** da regra que restringe `/api/**` a `ADMIN`;
  `/notificacao/**` livre, como os outros Swaggers.
- Atualize os testes do gateway (rotas, segurança, serviço fora do ar → 503).

**`docker-compose.yml`**:
- `postgres-notificacao` (`postgres:17`, banco/usuário/senha `notificacao`, porta `15435:5432`,
  volume `postgres-notificacao-data`, healthcheck como os outros).
- `notificacao-service` (build local, `restart: unless-stopped`, `KAFKA_BOOTSTRAP_SERVERS:
  kafka:29092`, `JWT_SECRET` igual ao dos outros, healthcheck de readiness, depende do banco e do
  Kafka saudáveis). Publique `8084:8084` para o modo de desenvolvimento do frontend.
- `api-gateway` ganha `NOTIFICACAO_URL` e passa a depender de `notificacao-service`
  (`service_started`).

**Frontend (infra)**:
- `frontend/nginx.conf`: `location /ws` com `proxy_http_version 1.1`, cabeçalhos `Upgrade` e
  `Connection "upgrade"` e `proxy_read_timeout 3600s`, apontando para `notificacao-service:8084`.
- `vite.config.ts`: proxy `/ws` com `ws: true` para `http://localhost:8084`.

**Kubernetes** (mesmo padrão dos outros serviços):
- `k8s/base/postgres-notificacao/` (StatefulSet + Service, 2Gi)
- `k8s/base/notificacao-service/` (Deployment com initContainer `wait-for-postgres`, probes,
  anotações do Prometheus, Service, HPA 2–5, PDB)
- ConfigMap `notificacao-config` (`SERVER_PORT=8084`, `DB_URL`, `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`,
  `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`); `JWT_SECRET` do Secret `jwt-secret` existente
- `NOTIFICACAO_URL=http://notificacao-service:8084` no `api-gateway-config`
- Overlay do minikube: imagem `notificacao-service:dev`, `imagePullPolicy: Never`, Secret
  `postgres-notificacao-credentials`
- Ingress: `/notificacao` → `api-gateway`. Para o `/ws`, crie um **segundo** Ingress
  (`distrischool-ws`) apontando direto para o `notificacao-service`, com as anotações do
  ingress-nginx `proxy-read-timeout` e `proxy-send-timeout` em `3600`
- `k8s/README.md`: `minikube image build -t notificacao-service:dev notificacao-service`

### Etapa 4 — Frontend: central de notificações

- Cliente STOMP com `@stomp/stompjs` (versão fixa). Conecta em `/ws` (mesma origem) depois do
  login, com o token no `connectHeaders`; reconexão automática com espera crescente; desconecta no
  logout e quando o token expira.
- Ao conectar (e a cada reconexão), busque a contagem de não lidas pela API REST, para não perder
  o que chegou enquanto estava desconectado.
- No `Topbar`: sino com a contagem de não lidas; painel com as últimas notificações (link para a
  rota, "marcar como lida", "marcar todas como lidas"); aviso rápido (toast) quando chega uma nova,
  usando o feedback global que o frontend já tem.
- Use o cliente HTTP existente (`services/http.ts`) para as chamadas REST.
- Testes (Vitest + Testing Library + MSW): contagem exibida, marcar como lida, chegada de
  notificação pelo cliente STOMP simulado.
- Rode `npm test -- --run`, `npm run lint` e `npm run build`.

**E2E**: estenda `scripts/e2e.ps1` com uma seção de notificações: crie pela API um usuário
`RESPONSAVEL` com `referenciaId` = id do aluno do fluxo, lance uma nota, faça login como esse
responsável e confira (com espera de até ~15 s) que `GET /api/notificacoes` traz a notificação
`NOTA_LANCADA`. Mantenha o script só com ASCII e compatível com o PowerShell 5.1.

---

## Parte B — Prometheus + Grafana

### Etapa 5 — Métricas nos serviços

Em **todos** os serviços Java (gateway, administrativa, escolar, usuários, notificação):

- Histograma de latência HTTP para calcular percentis no Prometheus:
  `management.metrics.distribution.percentiles-histogram.http.server.requests=true` (confirme o nome
  da propriedade no Spring Boot 4.1).
- Métricas do Resilience4j (estado do circuit breaker, chamadas, retries) no gateway e no
  `gestao-escolar-service`; adicione o módulo de Micrometer do Resilience4j se não vierem sozinhas.
- Confirme que já aparecem: JVM, CPU do processo, HikariCP, métricas do cliente Kafka (incluindo o
  lag do consumidor) e o gauge da outbox (`outbox.eventos.pendentes`).

**Regra para os dashboards**: antes de usar uma métrica em PromQL, confirme o nome exato na saída
real de `/actuator/prometheus` de um serviço rodando. Não invente nomes de métricas.

O rótulo que identifica o serviço (`servico`) é adicionado pelo Prometheus (job/relabel), não pela
aplicação.

### Etapa 6 — Prometheus e Grafana no Docker Compose

- `prometheus` (imagem `prom/prometheus` com versão fixa), porta `9090`, retenção de 2 dias.
  Configuração em `monitoring/prometheus/prometheus.yml` com alvos estáticos (nomes dos containers e
  portas 8080–8084, caminho `/actuator/prometheus`, intervalo de 5 s) e rótulo `servico` por alvo.
- `grafana` (imagem `grafana/grafana` com versão fixa), porta **`3001:3000`** (a `3000` fica livre),
  usuário e senha de desenvolvimento por variável de ambiente.
- Provisionamento automático (nada configurado à mão): datasource Prometheus em
  `http://prometheus:9090` e dashboards carregados de arquivos.
- **Os JSON dos dashboards e o arquivo de datasource ficam num único lugar, que o Kustomize
  consegue ler**: `k8s/base/monitoring/grafana/` (o Kustomize não lê arquivos fora da pasta da
  kustomization). O Compose monta essa mesma pasta. O datasource usa a mesma URL nos dois
  ambientes, porque o Service do Kubernetes também se chama `prometheus`.

**Dashboard "DistriSchool — Visão geral"** (uma variável `servico`, intervalo padrão 15 min,
atualização a cada 5 s):

- Throughput (req/s) por serviço e por rota (`uri`)
- Latência p50, p95 e p99 por serviço
- Taxa de erros 4xx e 5xx (%)
- Réplicas ativas por serviço (no Kubernetes, `count(up == 1)` por `servico`; útil para ver o HPA
  escalando durante o teste de carga)
- CPU do processo e heap da JVM por instância
- Conexões HikariCP ativas e pendentes
- Estado do circuit breaker por rota do gateway e do `gestao-escolar-service`
- Eventos pendentes na outbox por serviço
- Lag dos consumidores Kafka por grupo
- Notificações criadas e enviadas por minuto, e sessões WebSocket ativas

**Alertas** (regras do Prometheus em `monitoring/prometheus/alertas.yml`, visíveis em
`/alerts`; sem Alertmanager): serviço fora (`up == 0` por 1 min), latência p95 acima de 1 s por
5 min, erros 5xx acima de 5% por 5 min, circuit breaker aberto, outbox acima de 100 pendentes por
5 min, lag de consumidor acima de 1000 por 5 min.

Valide subindo o Compose: os alvos aparecem `UP` em `http://localhost:9090/targets` e os painéis
mostram dados depois de algumas requisições.

### Etapa 7 — Prometheus e Grafana no Kubernetes

Em `k8s/base/monitoring/`:

- Prometheus com descoberta de pods (`kubernetes_sd_configs`, `role: pod`, só o namespace
  `distrischool`), usando as anotações `prometheus.io/*` existentes. Relabel: `servico` a partir do
  rótulo `app.kubernetes.io/name`, e `pod`. ServiceAccount + Role + RoleBinding no namespace (sem
  ClusterRole). Mesmas regras de alerta do Compose.
- Grafana com o mesmo provisionamento (ConfigMaps gerados pelo Kustomize a partir de
  `k8s/base/monitoring/grafana/`).
- Recursos pequenos (requests de CPU e memória baixos) e retenção curta: o minikube já está perto
  do limite. No `k8s/README.md`, recomende `--memory=8g` quando a máquina permitir.
- Acesso por `kubectl port-forward` (`svc/prometheus 9090`, `svc/grafana 3001:3000`); **não**
  exponha no Ingress.
- Inclua os novos recursos na `kustomization.yaml` da base.

---

## Parte C — Testes de carga com JMeter

### Etapa 8 — Plano de teste e execução

Crie `load-tests/` com:

- `distrischool.jmx`: plano parametrizado por propriedades `-J`: `host`, `porta`, `protocolo`,
  `usuarios`, `rampa` (s), `duracao` (s), `email`, `senha`.
- **Login uma única vez**: o gateway limita o login a 10 por minuto por IP. Use um **setUp Thread
  Group** que faz o login como ADMIN e guarda o token numa propriedade compartilhada; os grupos
  principais só reutilizam o token. Nunca faça login por iteração.
- **Massa de dados** no setUp: crie, pela API, os dados de que o teste precisa (disciplina,
  professor, turma com a disciplina ofertada, alguns alunos e suas matrículas), com códigos que
  incluem um identificador da execução, para que execuções repetidas não colidam (sem 409). Use os
  payloads de `scripts/e2e.ps1` como referência.
- **Cenário misto** (proporções aproximadas, com Throughput Controllers):
  - 70% leitura: listagem paginada de alunos (com e sem filtro), turmas, detalhe de turma, boletim
    e frequência de um aluno
  - 20% escrita que gera eventos: criar uma avaliação e lançar as notas das matrículas (gera
    `grade.posted` e, portanto, notificações)
  - 10% cadastro de aluno (gera `student.created`)
- Think time aleatório entre requisições (ex.: 300–1000 ms), assertions de status 2xx,
  `Authorization: Bearer ${__P(token)}` num HTTP Header Manager.
- Perfis prontos, documentados no README: **fumaça** (5 usuários, 1 min), **carga** (50 usuários,
  rampa de 2 min, 10 min), **estresse** (rampa até 200 usuários) e **HPA** (carga sustentada alta
  por 10 min para provocar o autoscaling).

`load-tests/executar.ps1` roda o JMeter em **modo não gráfico pelo Docker** (imagem com versão
fixa; confirme que a tag existe), sem exigir instalação local:

- Parâmetros: perfil e alvo (`compose` → container na rede do Compose apontando para
  `api-gateway:8080`; `minikube` → `host.docker.internal:8080` via `kubectl port-forward`).
- Gera o resultado bruto (`.jtl`) e o relatório HTML do JMeter (`-e -o`) em
  `load-tests/resultados/<data-hora>-<perfil>/` (adicione `load-tests/resultados/` ao
  `.gitignore`).

`load-tests/README.md`:

- Como rodar cada perfil nos dois ambientes.
- **Roteiro de demonstração**: abrir o Grafana, rodar o perfil HPA no minikube, acompanhar
  `kubectl get hpa -n distrischool -w`, ver throughput, latência e réplicas no dashboard, e apagar um
  pod no meio do teste para mostrar a tolerância a falhas.
- Como ler o relatório do JMeter (throughput, p90/p95/p99, % de erros) e compará-lo com o Grafana.

`load-tests/RESULTADOS.md`: **modelo** de tabela para registrar cada execução (data, ambiente,
perfil, usuários, throughput, p95, p99, % de erros, réplicas mínimas e máximas, observações).
**Não preencha com números inventados.** Se você conseguir rodar o perfil fumaça contra o Compose,
registre só os números dessa execução real e diga onde está o relatório.

### Etapa 9 — Documentação final

- README da raiz: novo serviço, Prometheus (`:9090`), Grafana (`:3001`), testes de carga e como
  ver as notificações em tempo real.
- `docs/NOTIFICACOES.md`: arquitetura (tópicos consumidos, destinatários, idempotência, DLT,
  fan-out entre réplicas, WebSocket fora do gateway), com um diagrama em texto.
- `docs/MONITORAMENTO.md`: o que cada painel mostra, as regras de alerta e como os percentis são
  calculados.
- `k8s/README.md`: imagens novas, recursos de monitoramento, port-forwards e roteiro de demonstração
  atualizado.

## Critérios de aceite

- `./mvnw test` passa em todos os serviços Java; `npm test -- --run`, `npm run lint` e
  `npm run build` passam no frontend.
- `docker compose up --build` sobe tudo; `scripts/e2e.ps1` passa, incluindo a nova seção de
  notificações.
- Lançar uma nota pelo portal faz aparecer, em tempo real, a notificação para um usuário
  `RESPONSAVEL` ligado ao aluno, logado em outra aba.
- O mesmo evento entregue duas vezes não gera notificação duplicada.
- Com o `notificacao-service` fora do ar, o resto do sistema continua funcionando; quando ele volta,
  as notificações dos eventos publicados nesse intervalo aparecem (o Kafka guardou os eventos).
- Prometheus mostra todos os alvos `UP` no Compose e no minikube; o dashboard do Grafana carrega
  sozinho e mostra throughput, latência, erros e réplicas.
- O perfil fumaça do JMeter roda sem erros contra o Compose e gera o relatório HTML.