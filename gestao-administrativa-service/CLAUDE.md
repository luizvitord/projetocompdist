# CLAUDE.md — gestao-administrativa-service

Este arquivo descreve o `gestao-administrativa-service`, parte do projeto DistriSchool
(plataforma de gestão escolar distribuída em microsserviços). Leia este arquivo antes
de gerar qualquer código para este serviço.

O serviço se chamava `school-core-service`. O sistema passou a ter dois microsserviços
de negócio, cada um com o próprio banco de dados:

| Serviço | Responsabilidade | Porta | Banco |
|---|---|---|---|
| `gestao-administrativa-service` (este) | Professores, técnicos administrativos, turmas e disciplinas | 8081 | `gestao_administrativa` |
| `gestao-escolar-service` | Alunos, matrículas, frequências e notas | 8082 | `gestao_escolar` |

## Tarefa atual: reestruturação

Execute as etapas abaixo **em ordem**. Rode `./mvnw test` ao final de cada etapa e faça
**um commit por etapa**, com mensagem clara. O commit da Etapa 2 é importante: o
`gestao-escolar-service` vai recuperar o código de alunos pelo histórico do git.

### Etapa 1 — Mover para o monorepo e renomear

O repositório vira um monorepo, com um diretório por serviço:

```
projetocompdist/
├── docker-compose.yml               # infraestrutura local compartilhada
├── k8s/                             # manifests de todos os serviços
├── gestao-administrativa-service/   # este serviço
└── gestao-escolar-service/          # criado em uma tarefa posterior
```

- Use `git mv` (para preservar o histórico) para mover `src/`, `pom.xml`, `mvnw`, `mvnw.cmd`,
  `.mvn/`, `Dockerfile` e `.dockerignore` para `gestao-administrativa-service/`.
  `docker-compose.yml`, `k8s/`, `.gitignore` e `.gitattributes` ficam na raiz; confira se os
  padrões do `.gitignore` continuam valendo para o subdiretório.
- Mova este arquivo para `gestao-administrativa-service/CLAUDE.md`.
- Renomeie:
  - `artifactId`, `name` e `spring.application.name` → `gestao-administrativa-service`
  - pacote `com.distrischool.school_core_service` → `com.distrischool.gestao_administrativa`
  - classe principal → `GestaoAdministrativaServiceApplication`
  - título do OpenAPI → "DistriSchool - gestao-administrativa-service"
- Swagger sob prefixo próprio, porque o Ingress vai expor os dois serviços:
  `/administrativa/swagger-ui.html` e `/administrativa/v3/api-docs`. Verifique se o springdoc
  serve os recursos estáticos corretamente com esse prefixo.
- Banco de dados `gestao_administrativa` (banco, usuário e senha de desenvolvimento iguais).
  No `docker-compose.yml`, o serviço `postgres` vira `postgres-administrativa`, mantendo a porta
  `15432`. O Kafka continua na `9092`. Ajuste o `application.properties`.
- Kubernetes: renomeie `k8s/base/school-core-service` → `gestao-administrativa-service`,
  `k8s/base/postgres` → `postgres-administrativa`, o Secret `postgres-credentials` →
  `postgres-administrativa-credentials` e o ConfigMap `school-core-config` →
  `gestao-administrativa-config`. Atualize o overlay do minikube, o Ingress e o `k8s/README.md`
  (o build da imagem passa a ser `minikube image build -t gestao-administrativa-service:dev gestao-administrativa-service`).
- A porta da aplicação continua `8081`.

### Etapa 2 — Remover o módulo de alunos

Alunos passam a pertencer ao `gestao-escolar-service`.

- Apague o pacote `aluno/`, o `AlunoControllerTest`, o `StudentCreatedEvent`, a constante
  `STUDENT_CREATED` e a criação do tópico `student.created` no `KafkaTopicConfig`.
- Em `Turma`: remova o relacionamento `alunos`, o campo `quantidadeAlunos` e a regra que impede
  reduzir a capacidade abaixo do número de alunos. O campo `capacidade` continua existindo;
  quem a valida, no momento da matrícula, é o `gestao-escolar-service`.
- Remova os endpoints `PUT/DELETE /api/turmas/{id}/alunos/{alunoId}` (a matrícula de alunos em
  turmas passa a ser feita em `POST /api/matriculas`, no outro serviço).
- Migration `V3__remove_alunos.sql`: `DROP TABLE turma_aluno`, `DROP TABLE aluno` e
  `DROP SEQUENCE aluno_matricula_seq`. **Nunca** edite a V1 ou a V2.
- Remova a rota `/api/alunos` do Ingress.

### Etapa 3 — Disciplinas

As notas e frequências do `gestao-escolar-service` são lançadas por disciplina, então este
serviço precisa cadastrar disciplinas e ofertá-las nas turmas.

**Disciplina** (novo pacote `disciplina/`):
- Campos: `codigo` (obrigatório, único, até 20 caracteres), `nome` (obrigatório, até 100),
  `cargaHoraria` (horas, opcional, positiva), `ativa` (gerenciado pelo sistema, padrão `true`)
- `POST /api/disciplinas`
- `GET /api/disciplinas` (paginado; filtros `nome`, `ativa`; inativas ocultas por padrão)
- `GET /api/disciplinas/{id}`
- `PUT /api/disciplinas/{id}`
- `DELETE /api/disciplinas/{id}` (exclusão lógica; retorna 409 se a disciplina estiver
  ofertada em alguma turma)

**Oferta de disciplina na turma** (tabela `turma_disciplina`):
- Colunas: `turma_id`, `disciplina_id`, `professor_id` (opcional); chave única em
  (`turma_id`, `disciplina_id`)
- `PUT /api/turmas/{id}/disciplinas/{disciplinaId}`, com corpo `{ "professorId": 7 }`
  (opcional): oferta a disciplina na turma ou troca o professor responsável
- `DELETE /api/turmas/{id}/disciplinas/{disciplinaId}`: retira a oferta
- Disciplina inativa não pode ser ofertada (409). Professor inexistente → 404.
- Se o professor indicado ainda não estiver atribuído à turma (`turma_professor`), atribua-o
  automaticamente e publique `teacher.assigned`, como já acontece hoje.
- `TurmaResponse` ganha a lista `disciplinas` (`disciplinaId`, `codigo`, `nome`,
  `professorId`, `professorNome`) e perde `quantidadeAlunos`.
- Migration `V4__disciplinas.sql`, com índices em `lower(nome)` e nas chaves estrangeiras.

### Etapa 4 — Enriquecer o evento `schedule.updated`

O `gestao-escolar-service` mantém uma cópia local das turmas e disciplinas a partir deste
evento. O payload passa a carregar o estado completo da turma (ver **Contrato do evento
`schedule.updated`** abaixo).

Publique o evento, sempre após o commit e com a chave `turmaId`, quando:
- uma turma for criada, alterada ou removida;
- uma disciplina for ofertada ou retirada de uma turma, ou o professor da oferta mudar;
- uma disciplina for renomeada ou tiver o código alterado (um evento para cada turma que a oferta).

Atualize os testes existentes de turma e crie testes para disciplinas.

## Escopo deste serviço (estado após a tarefa)

### Gestão de Professores
- Implementado: cadastro, edição, consulta e listagem (sem exclusão); atribuição a turmas
- Pendente: visualização de horários, relatórios de desempenho, logs de auditoria

### Gestão de Técnicos Administrativos
- Implementado: CRUD completo com exclusão lógica e evento `staff.created`

### Gestão de Turmas e Disciplinas
- Implementado: CRUD de turmas, atribuição de professores, capacidade; disciplinas e oferta
  de disciplinas por turma (nesta tarefa)
- Pendente: horários de aula, calendário escolar, feriados, detecção de conflitos de horário,
  importação via Excel

## Fora do escopo deste serviço

- Alunos, matrículas, frequências, notas e boletim → `gestao-escolar-service`. Não
  reimplemente nada disso aqui e nunca acesse o banco do outro serviço.
- Autenticação/autorização (login, JWT, OAuth2) → serviço de usuários (futuro)
- Envio de notificações → `notification-service` (futuro)
- Relatórios analíticos/ML → serviço de relatórios (futuro)

## Comunicação com outros serviços

Este serviço **publica** no Kafka:

- `teacher.assigned` — professor atribuído a uma turma
- `schedule.updated` — turma ou suas disciplinas alteradas (contrato abaixo)
- `staff.created` — técnico administrativo cadastrado (sem CPF no payload)

`student.created` **não** é mais publicado aqui; agora pertence ao `gestao-escolar-service`.

Este serviço **não consome** eventos.

O `gestao-escolar-service` depende de duas interfaces deste serviço: o evento
`schedule.updated` e o endpoint `GET /api/turmas/{id}`, que ele chama quando uma turma ainda
não está na sua cópia local. **Não altere o formato de nenhum dos dois** sem atualizar
também o `gestao-escolar-service/CLAUDE.md`. Adicionar campos novos é seguro; remover ou
renomear campos quebra o outro serviço.

### Contrato do evento `schedule.updated`

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

- `acao`: `CRIADA`, `ATUALIZADA` ou `REMOVIDA`. Em `REMOVIDA`, os demais campos trazem o último
  estado da turma.
- `capacidade` e `professorId` podem ser `null`.
- `disciplinas` traz sempre a lista completa e atual de ofertas da turma (não um delta).
- Chave da mensagem: `turmaId` como string. JSON sem cabeçalhos de tipo Java.

## Padrões de implementação

- Um pacote por módulo (`professor/`, `tecnico/`, `turma/`, `disciplina/`), cada um com
  entidade, repository, specifications, service, controller e DTOs
- Schema controlado só pelo Flyway (`ddl-auto=validate`); toda mudança é uma migration nova com
  o próximo número de versão; **nunca** edite migrations já aplicadas
- Listagens paginadas; índices em colunas usadas em filtros
- Erros no formato `ProblemDetail` via `ApiExceptionHandler`: 400 (validação, com a lista de
  campos), 404 (não encontrado), 409 (regra de negócio)
- Eventos Kafka publicados somente após o commit da transação, em JSON sem cabeçalhos de tipo Java
- Dados pessoais (CPF) nunca aparecem em logs nem em eventos
- Testes de slice web (`@WebMvcTest`) para os controllers; rode `./mvnw test` ao final

## Infraestrutura local

- `docker compose up -d` na raiz sobe `postgres-administrativa` (porta `15432`, banco
  `gestao_administrativa`) e o Kafka (`9092`). O `gestao-escolar-service` adicionará o próprio
  Postgres ao mesmo arquivo.
- A aplicação roda na porta `8081`.

## Stack técnica do serviço

- Java 21, Spring Boot 4.1
- Spring Web MVC (REST) + springdoc-openapi (Swagger)
- Spring Data JPA
- PostgreSQL 17 + Flyway
- Spring for Apache Kafka (produtor)
- Spring Boot Actuator + Micrometer/Prometheus
- Docker e Kubernetes (Kustomize, minikube)