# CLAUDE.md — gestao-escolar-service

Este arquivo descreve o `gestao-escolar-service`, parte do projeto DistriSchool
(plataforma de gestão escolar distribuída em microsserviços). Leia este arquivo antes
de gerar qualquer código para este serviço.

O sistema tem dois microsserviços de negócio, cada um com o próprio banco de dados, no
mesmo repositório (monorepo):

| Serviço | Responsabilidade | Porta | Banco |
|---|---|---|---|
| `gestao-administrativa-service` | Professores, técnicos administrativos, turmas e disciplinas | 8081 | `gestao_administrativa` |
| `gestao-escolar-service` (este) | Alunos, matrículas, frequências e notas | 8082 | `gestao_escolar` |

```
projetocompdist/
├── docker-compose.yml
├── k8s/
├── gestao-administrativa-service/
└── gestao-escolar-service/          # este serviço
```

## Terminologia

Duas coisas diferentes usam a palavra "matrícula". Mantenha os nomes abaixo no código:

- **Número de matrícula** (`Aluno.matricula`): identificador do aluno na escola, gerado no
  cadastro (ex.: `2026000001`). Nunca muda.
- **Matrícula** (entidade `Matricula`): vínculo de um aluno com uma turma em um ano letivo.
  Um aluno tem várias ao longo dos anos.

## Tarefa atual: criar o serviço

O serviço não existe ainda. Execute as etapas **em ordem**, rode `./mvnw test` ao final de
cada uma e faça **um commit por etapa**.

### Etapa 1 — Esqueleto

- Crie `gestao-escolar-service/` com a mesma base do `gestao-administrativa-service`:
  `pom.xml` (Spring Boot 4.1.1, Java 21, mesmas dependências), Maven Wrapper (`mvnw`,
  `mvnw.cmd`, `.mvn/`), `Dockerfile` e `.dockerignore`.
- `artifactId` e `spring.application.name`: `gestao-escolar-service`; pacote
  `com.distrischool.gestao_escolar`; classe principal `GestaoEscolarServiceApplication`.
- Porta `8082`. Banco via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`, com padrão
  `jdbc:postgresql://localhost:15433/gestao_escolar`.
- Copie e adapte o pacote `common/` (`ApiExceptionHandler`, exceções, `OpenApiConfig`). Os
  serviços não compartilham código: cada um tem a sua cópia.
- Swagger sob prefixo próprio: `/escolar/swagger-ui.html` e `/escolar/v3/api-docs`. Verifique
  se o springdoc serve os recursos estáticos corretamente com esse prefixo.

### Etapa 2 — Alunos (migrado do gestão administrativa)

O módulo de alunos foi removido do `gestao-administrativa-service` e deve ser trazido para cá.
Recupere o código pelo histórico do git (ex.: `git log --diff-filter=D --name-only -- '*aluno/*'`
para achar o commit da remoção e `git show <commit>^:<caminho>` para ler os arquivos) e adapte
ao novo pacote. Recupere também o `AlunoControllerTest` e o `StudentCreatedEvent`.

Mantenha o comportamento existente:
- Campos: `nome`, `dataNascimento` (passada), `endereco`, `telefone`, `email`
- Número de matrícula gerado pelo servidor a partir de uma sequence, no formato ano + 6 dígitos
- `POST /api/alunos`, `GET /api/alunos` (paginado; filtros `nome`, `matricula`, `turmaId`),
  `GET/PUT/DELETE /api/alunos/{id}`
- O filtro `turmaId` agora usa as matrículas com status `ATIVA` (Etapa 4)
- Evento `student.created` (mesmo payload de antes)
- `DELETE` retorna 409 se o aluno tiver alguma matrícula (o histórico escolar não pode ser apagado)

### Etapa 3 — Cópia local de turmas e disciplinas

Turmas e disciplinas pertencem ao `gestao-administrativa-service`. Este serviço **nunca**
acessa o banco dele; mantém uma cópia local (réplica de leitura) alimentada por eventos.

**Tabelas da réplica:**
- `turma_replica`: `id` (o `turmaId` do outro serviço, sem sequence), `codigo`, `nome`,
  `ano_letivo`, `turno`, `capacidade`, `removida` (boolean), `ultimo_evento_em`
- `turma_disciplina_replica`: `turma_id`, `disciplina_id`, `codigo`, `nome`, `professor_id`,
  `removida`; chave primária (`turma_id`, `disciplina_id`)
- Linhas da réplica **nunca são apagadas**, só marcadas como `removida`, porque chamadas e
  avaliações antigas continuam apontando para elas.

**Consumidor do `schedule.updated`** (contrato no fim deste arquivo):
- `group-id` `gestao-escolar-service`, `auto-offset-reset=earliest`
- Leia o JSON sem depender de classes do outro serviço (o produtor não envia cabeçalhos de tipo);
  use as mesmas bibliotecas do Spring Kafka 4 / Jackson 3 já usadas no produtor
- Idempotente: faça upsert pela chave e ignore eventos com `occurredAt` mais antigo que
  `ultimo_evento_em` (o Kafka pode reentregar ou entregar fora de ordem)
- `disciplinas` traz a lista completa: ofertas ausentes da lista são marcadas `removida`
- `acao=REMOVIDA` marca a turma como `removida`
- Falhas: `DefaultErrorHandler` com 3 tentativas e backoff; depois, envio para
  `schedule.updated.DLT`

**Fallback síncrono:** se uma operação precisar de uma turma que não está na réplica, busque
`GET {ADMINISTRATIVA_URL}/api/turmas/{id}` (padrão `http://localhost:8081`) com `RestClient`,
timeout de 2 s, retry e circuit breaker, e grave o resultado na réplica. Use Resilience4j na
versão compatível com o Spring Boot 4.1 (confirme a compatibilidade antes de adicionar a
dependência). Se o outro serviço estiver fora do ar ou o circuito aberto, responda 503 com
`ProblemDetail`. Turmas que já estão na réplica funcionam normalmente mesmo com o outro
serviço fora do ar: esse é o objetivo da réplica.

### Etapa 4 — Matrículas

Vínculo de um aluno com uma turma em um ano letivo.

**Campos:** `alunoId`, `turmaId`, `anoLetivo` (copiado da turma), `status`, `dataMatricula`,
`dataEncerramento`, `motivo`, `matriculaOrigemId` (preenchido em transferências).

**Status e transições:**
- `ATIVA` → `TRANCADA`, `CANCELADA`, `CONCLUIDA` ou `TRANSFERIDA`
- `TRANCADA` → `ATIVA` ou `CANCELADA`
- `CANCELADA`, `CONCLUIDA` e `TRANSFERIDA` são finais
- Transição inválida → 409. `motivo` é obrigatório para `TRANCADA`, `CANCELADA` e `TRANSFERIDA`.

**Regras:**
- Um aluno tem no máximo uma matrícula `ATIVA` ou `TRANCADA` por ano letivo (índice único
  parcial no banco, além da validação no service)
- A turma precisa existir e não estar `removida`
- Capacidade: o número de matrículas `ATIVA` na turma não pode passar da `capacidade`
  (`null` = sem limite). Proteja contra matrículas simultâneas na última vaga (ex.: bloqueio
  pessimista na linha da turma da réplica)
- Transferência: encerra a matrícula atual como `TRANSFERIDA` e cria uma nova `ATIVA` na turma
  de destino, com `matriculaOrigemId`, na mesma transação. A turma de destino precisa ser do
  mesmo ano letivo
- Não existe exclusão de matrícula; use o cancelamento

**Endpoints:**
- `POST /api/matriculas` — `{ "alunoId": 1, "turmaId": 12 }`
- `GET /api/matriculas` — paginado; filtros `alunoId`, `turmaId`, `anoLetivo`, `status`
- `GET /api/matriculas/{id}`
- `PATCH /api/matriculas/{id}/status` — `{ "status": "TRANCADA", "motivo": "..." }`
- `POST /api/matriculas/{id}/transferencia` — `{ "turmaDestinoId": 15, "motivo": "..." }`

**Eventos:** `enrollment.created` e `enrollment.updated` (payloads abaixo).

### Etapa 5 — Frequências

**Chamada:** registro de presença de uma turma em uma disciplina em uma data.
- Campos: `turmaId`, `disciplinaId`, `data`, `professorId` (opcional) e a lista de registros
  (`matriculaId`, `status`: `PRESENTE`, `FALTA` ou `ATRASO`, `observacao` opcional)
- Única por (`turma_id`, `disciplina_id`, `data`)

**Regras:**
- A disciplina precisa estar ofertada na turma (réplica, não `removida`) e a turma não pode
  estar `removida`
- `data` não pode ser futura e precisa estar no ano letivo da turma
- A chamada deve conter **exatamente** as matrículas `ATIVA` da turma, cada uma uma vez;
  faltando ou sobrando matrícula → 400 com a lista das divergentes
- Edição substitui os registros, com as mesmas validações
- Percentual de presença = (`PRESENTE` + `ATRASO`) / total de chamadas em que a matrícula
  constava × 100. Abaixo do mínimo configurado (`escolar.frequencia.minima-percentual`,
  padrão `75`), o aluno é marcado como `abaixoDoMinimo`

**Endpoints:**
- `POST /api/frequencias/chamadas`
- `GET /api/frequencias/chamadas` — paginado; filtros `turmaId`, `disciplinaId`, `dataInicio`,
  `dataFim`
- `GET /api/frequencias/chamadas/{id}`
- `PUT /api/frequencias/chamadas/{id}`
- `GET /api/frequencias/relatorio?turmaId=&disciplinaId=&dataInicio=&dataFim=` — por aluno:
  total de aulas, presenças, faltas, atrasos, percentual e `abaixoDoMinimo`
- `GET /api/frequencias/alunos/{alunoId}?anoLetivo=` — percentual por disciplina e geral

**Evento:** `attendance.recorded`, publicado na criação e na edição de uma chamada.

### Etapa 6 — Notas e boletim

**Avaliação:**
- Campos: `turmaId`, `disciplinaId`, `bimestre` (1 a 4), `tipo` (`PROVA`, `TRABALHO`,
  `ATIVIDADE`, `RECUPERACAO`), `descricao`, `peso` (> 0, padrão 1), `notaMaxima` (> 0,
  padrão 10), `data`
- No máximo uma `RECUPERACAO` por turma, disciplina e bimestre
- Mesmas validações de turma e disciplina das chamadas
- `DELETE` retorna 409 se a avaliação já tiver notas

**Nota:**
- Uma por (avaliação, matrícula); `valor` entre 0 e `notaMaxima`
- Só para matrículas `ATIVA` da turma da avaliação
- Correção de nota exige `motivo` e grava em `nota_historico` (valor anterior, valor novo,
  motivo, data). O histórico nunca é alterado nem apagado

**Cálculo de médias** (classes puras, sem dependência do Spring, com testes unitários):
- Toda nota é normalizada para a escala 0–10 (`valor / notaMaxima × 10`)
- Média do bimestre: média ponderada pelo `peso` das avaliações que não são `RECUPERACAO`.
  Se houver recuperação no bimestre, a média final do bimestre é a maior entre a média e a
  nota da recuperação
- Avaliação sem nota lançada para o aluno não entra no cálculo; o boletim informa quantas
  estão pendentes
- Média anual da disciplina: média aritmética dos bimestres que têm média
- `BigDecimal`, 2 casas, arredondamento `HALF_UP`
- Média mínima configurável: `escolar.avaliacao.media-aprovacao`, padrão `6.0`

**Boletim** (`GET /api/alunos/{alunoId}/boletim?anoLetivo=`):
- Considera todas as matrículas do aluno no ano letivo, inclusive `TRANSFERIDA`, agrupando
  por disciplina
- Por disciplina: média de cada bimestre, média anual, percentual de presença, avaliações
  pendentes e situação
- Situação: enquanto a matrícula estiver `ATIVA`, `CURSANDO` (médias parciais). Com a matrícula
  `CONCLUIDA`: `REPROVADO_POR_FALTA` se a frequência geral estiver abaixo do mínimo;
  senão `APROVADO` se todas as disciplinas atingirem a média mínima; senão `REPROVADO_POR_NOTA`

**Endpoints:**
- `POST /api/avaliacoes`, `GET /api/avaliacoes` (paginado; filtros `turmaId`, `disciplinaId`,
  `bimestre`), `GET/PUT/DELETE /api/avaliacoes/{id}`
- `POST /api/avaliacoes/{id}/notas` — lançamento em lote: `[{ "matriculaId": 4, "valor": 8.5 }]`;
  409 se alguma matrícula já tiver nota (correção é pelo endpoint abaixo)
- `GET /api/avaliacoes/{id}/notas`
- `PUT /api/notas/{id}` — `{ "valor": 7.0, "motivo": "..." }`
- `GET /api/notas/{id}/historico`
- `GET /api/alunos/{alunoId}/boletim?anoLetivo=`

**Evento:** `grade.posted`, publicado no lançamento em lote e em cada correção.

### Etapa 7 — Infraestrutura

- `docker-compose.yml` (raiz): adicione `postgres-escolar` (`postgres:17`, banco, usuário e
  senha `gestao_escolar`, porta `15433:5432`, volume `postgres-escolar-data`). Não altere os
  serviços já existentes.
- Kubernetes, seguindo o mesmo padrão dos manifests do `gestao-administrativa-service`:
    - `k8s/base/postgres-escolar/` (StatefulSet + Service)
    - `k8s/base/gestao-escolar-service/` (Deployment com probes do Actuator, Service, HPA)
    - ConfigMap `gestao-escolar-config`: `SERVER_PORT=8082`,
      `DB_URL=jdbc:postgresql://postgres-escolar:5432/gestao_escolar`,
      `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`,
      `ADMINISTRATIVA_URL=http://gestao-administrativa-service:8081`,
      `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`
    - Overlay do minikube: imagem `gestao-escolar-service:dev`, `imagePullPolicy: Never` e
      Secret `postgres-escolar-credentials`
    - Ingress: `/api/alunos`, `/api/matriculas`, `/api/frequencias`, `/api/avaliacoes`,
      `/api/notas` e `/escolar` (Swagger) → `gestao-escolar-service`
- Atualize o `k8s/README.md` (build: `minikube image build -t gestao-escolar-service:dev gestao-escolar-service`).

## Modelo de dados (banco `gestao_escolar`)

- `aluno` + sequence `aluno_matricula_seq`
- `turma_replica`, `turma_disciplina_replica`
- `matricula` (FK para `aluno` e `turma_replica`; FK opcional para ela mesma em
  `matricula_origem_id`; índice único parcial em (`aluno_id`, `ano_letivo`) para status
  `ATIVA`/`TRANCADA`)
- `chamada` (FK composta para `turma_disciplina_replica`), `registro_frequencia`
  (FK para `chamada` e `matricula`; única por chamada e matrícula)
- `avaliacao` (FK composta para `turma_disciplina_replica`), `nota` (FK para `avaliacao` e
  `matricula`; única por avaliação e matrícula), `nota_historico`
- `CHECK` para enums, bimestre, valores não negativos; índices nas colunas de filtro e nas FKs

## Fora do escopo deste serviço

- Professores, técnicos administrativos, turmas e disciplinas → `gestao-administrativa-service`.
  Não crie, edite nem exclua esses dados aqui; use só a réplica (somente leitura).
- Autenticação/autorização → serviço de usuários (futuro). Por enquanto, `professorId` em
  chamadas é só informativo, sem validação de permissão.
- Envio de notificações a pais e alunos → `notification-service` (futuro), que consumirá os
  eventos deste serviço
- Relatórios analíticos/ML → serviço de relatórios (futuro)

## Comunicação com outros serviços

### Consome

- `schedule.updated` (produzido pelo `gestao-administrativa-service`)
- `GET {ADMINISTRATIVA_URL}/api/turmas/{id}` como fallback (resposta com `id`, `codigo`, `nome`,
  `anoLetivo`, `turno`, `capacidade` e `disciplinas` no mesmo formato do evento)

Contrato do `schedule.updated`:

```json
{
  "turmaId": 12,
  "codigo": "9A-2026",
  "nome": "9º Ano A",
  "anoLetivo": 2026,
  "turno": "MANHA",
  "capacidade": 35,
  "disciplinas": [
    { "disciplinaId": 3, "codigo": "MAT", "nome": "Matemática", "professorId": 7 }
  ],
  "acao": "ATUALIZADA",
  "occurredAt": "2026-10-24T11:00:00Z"
}
```

`acao`: `CRIADA`, `ATUALIZADA` ou `REMOVIDA`. `disciplinas` traz sempre a lista completa.
`capacidade` e `professorId` podem ser `null`. Ignore campos desconhecidos (o produtor pode
adicionar campos novos).

### Publica

Todos após o commit da transação, em JSON sem cabeçalhos de tipo Java, com
`occurredAt` em todos os payloads:

| Tópico | Chave | Payload |
|---|---|---|
| `student.created` | `alunoId` | `alunoId`, `matricula`, `nome`, `email` |
| `enrollment.created` | `alunoId` | `matriculaId`, `alunoId`, `turmaId`, `anoLetivo` |
| `enrollment.updated` | `alunoId` | `matriculaId`, `alunoId`, `turmaId`, `statusAnterior`, `statusNovo`, `turmaDestinoId` (em transferências) |
| `attendance.recorded` | `turmaId` | `chamadaId`, `turmaId`, `disciplinaId`, `data`, `ausencias`: lista de `alunoId` + `status` (só `FALTA` e `ATRASO`) |
| `grade.posted` | `turmaId` | `avaliacaoId`, `turmaId`, `disciplinaId`, `bimestre`, `notas`: lista de `alunoId` + `valor` |

Os eventos usam `alunoId` (e não `matriculaId`) nas listas porque os consumidores conhecem
alunos, não matrículas.

## Padrões de implementação

- Um pacote por módulo (`aluno/`, `turma/` para a réplica e o consumidor, `matricula/`,
  `frequencia/`, `avaliacao/`), cada um com entidade, repository, service, controller e DTOs
- Schema controlado só pelo Flyway (`ddl-auto=validate`); toda mudança é uma migration nova;
  **nunca** edite migrations já aplicadas
- Listagens paginadas; requisito de suportar mais de 10.000 alunos
- Erros no formato `ProblemDetail` via `ApiExceptionHandler`: 400 (validação, com a lista de
  campos), 404 (não encontrado), 409 (regra de negócio), 503 (dependência indisponível)
- Dados pessoais de alunos nunca aparecem em logs
- Testes: `@WebMvcTest` para cada controller; testes unitários para o cálculo de médias, de
  frequência, das transições de status da matrícula e do consumidor (idempotência e ordem
  dos eventos); rode `./mvnw test` ao final

## Infraestrutura local

- `docker compose up -d` na raiz sobe o Kafka (`9092`), o `postgres-administrativa` (`15432`)
  e o `postgres-escolar` (`15433`)
- O `gestao-administrativa-service` roda na `8081`; este serviço na `8082`

## Stack técnica do serviço

- Java 21, Spring Boot 4.1
- Spring Web MVC (REST) + springdoc-openapi (Swagger) + `RestClient`
- Spring Data JPA
- PostgreSQL 17 + Flyway
- Spring for Apache Kafka (produtor e consumidor)
- Resilience4j (circuit breaker e retry)
- Spring Boot Actuator + Micrometer/Prometheus
- Docker e Kubernetes (Kustomize, minikube)