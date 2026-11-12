# notificacao-service

Serviço de Comunicação do DistriSchool: consome os eventos de domínio dos outros serviços pelo
Kafka (Spring Cloud Stream) e os transforma em notificações para alunos, responsáveis, professores
e coordenação.

| | |
|---|---|
| Porta | `8084` (`SERVER_PORT`) |
| Banco | `notificacao` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; padrão `jdbc:postgresql://localhost:15435/notificacao`) |
| Kafka | `KAFKA_BOOTSTRAP_SERVERS` (padrão `localhost:9092`) |
| JWT | `JWT_SECRET`, o mesmo do `usuarios-service` e do `api-gateway` |
| Swagger | `/notificacao/swagger-ui.html` e `/notificacao/v3/api-docs` |
| WebSocket | `/ws` (STOMP) |

## Eventos consumidos

O destinatário de uma notificação é o par (`perfil`, `referenciaId`), o mesmo que vai no JWT:
`referenciaId` é o id do professor (`PROFESSOR`) ou do aluno (`ALUNO` e `RESPONSAVEL`). `ADMIN` não
tem referência e representa a coordenação: todos os administradores veem as mesmas notificações (e
quando um deles marca uma como lida, ela fica lida para todos).

| Tópico | Produtor | Destinatários | Exemplo de texto |
|---|---|---|---|
| `grade.posted` | escolar | `ALUNO` e `RESPONSAVEL` de cada `alunoId` em `notas` | "Nova nota em Matemática (1º bimestre): 8,5" |
| `attendance.recorded` | escolar | `ALUNO` e `RESPONSAVEL` de cada `alunoId` em `ausencias` | "Falta registrada em Matemática em 10/11/2026" |
| `enrollment.created` | escolar | `ALUNO` e `RESPONSAVEL` do `alunoId` | "Matrícula confirmada na turma 9º Ano A (2026)" |
| `enrollment.updated` | escolar | `ALUNO` e `RESPONSAVEL` do `alunoId`; `ADMIN` quando `statusNovo` é `CANCELADA` ou `TRANSFERIDA` | "Sua matrícula na turma 9º Ano A foi trancada" |
| `student.created` | escolar | `ADMIN` | "Aluno cadastrado: Ana Souza (matrícula 2026000001)" |
| `teacher.assigned` | administrativa | `PROFESSOR` do `professorId` | "Você foi atribuído à turma 9º Ano A" |
| `staff.created` | administrativa | `ADMIN` | "Técnico administrativo cadastrado: João Lima (Secretaria)" |
| `schedule.updated` | administrativa | — | Só atualiza o cache de nomes |

O JSON é lido sem as classes dos produtores (`evento/Mensagens.java`), só com os campos usados;
campos desconhecidos são ignorados.

**Cache de nomes.** Notas, frequência e matrículas só trazem ids. As tabelas `turma_nome` e
`disciplina_nome` guardam os nomes vindos do `schedule.updated` (upsert; eventos com `occurredAt`
mais antigo que o último aplicado são ignorados). Se o nome ainda não é conhecido, o texto usa o
id: "turma #12", "disciplina #3". O serviço nunca chama outro serviço por HTTP.

## Spring Cloud Stream

Os consumidores são beans `java.util.function.Consumer` (`evento/ConsumidoresConfig.java`), um por
tópico, ligados pelos bindings `<função>-in-0` de `application.properties`.

- **Grupo `notificacao-service`** em todos os bindings: as réplicas dividem as partições, então
  cada evento é processado por uma só. O `schedule.updated` também é lido pelo
  `gestao-escolar-service`, no grupo dele: é o publish/subscribe do Kafka.
- **Falhas**: 3 tentativas no total, com espera de 1 s e depois 2 s; em seguida o registro vai para
  `<tópico>.DLT` (`enable-dlq` + `dlq-name` do binder Kafka; o padrão do binder seria
  `error.<tópico>.<grupo>`). `MensagemInvalidaException` (payload fora do contrato) não é repetida:
  vai direto para a DLT.
- **Offsets**: o grupo guarda o offset de cada partição. Com o serviço parado, o Kafka guarda os
  eventos; quando ele volta, lê do ponto em que parou.
- **Reinícios**: ao desligar, os consumidores avisam o Kafka que saíram do grupo, e a réplica que
  sobe (ou as que ficaram) assume as partições em poucos segundos. Isso precisa de ~4 s de
  desligamento gracioso (`stop_grace_period` no Compose, `terminationGracePeriodSeconds` no
  Kubernetes). Se a réplica for morta sem se despedir, o grupo fica parado até o
  `session.timeout.ms` dela vencer; por isso ele foi reduzido de 45 s (padrão) para 15 s.
- **Primeira subida**: um grupo novo começa do início dos tópicos (`earliest`), para montar o cache
  de nomes. Os eventos antigos de notas e matrículas também viram notificações e, como os tópicos
  são lidos em paralelo, algumas podem sair com o nome neutro ("disciplina #3").

### Idempotência

A entrega do Kafka é "pelo menos uma vez" e os eventos não têm id. Cada notificação guarda
`evento_hash` = SHA-256 de tópico + chave + payload bruto, e o índice único
(`evento_hash`, `destinatario_perfil`, `destinatario_referencia_id`) impede a duplicata; a inserção
usa `ON CONFLICT DO NOTHING`. O índice é criado com `NULLS NOT DISTINCT`: sem isso, as linhas de
`ADMIN` (referência nula) nunca entrariam em conflito.

## API REST

Todas as rotas exigem o JWT do login. O serviço valida o token (resource server, HS256) e tira o
destinatário das claims `perfil` e `referenciaId`; parâmetros nunca definem de quem são as
notificações.

| Rota | Descrição |
|---|---|
| `GET /api/notificacoes?somenteNaoLidas=&page=&size=` | Lista paginada, mais recentes primeiro |
| `GET /api/notificacoes/nao-lidas/contagem` | `{ "total": 3 }` |
| `PATCH /api/notificacoes/{id}/lida` | Marca como lida; `404` se não for do destinatário |
| `POST /api/notificacoes/lidas` | Marca todas como lidas: `{ "atualizadas": 2 }` |

## Tempo real (WebSocket + STOMP)

O cliente conecta em `/ws` (WebSocket puro, sem SockJS, para não exigir sessão fixa no
balanceador) e assina `/user/queue/notificacoes`. Cada notificação nova chega como o mesmo JSON da
API REST.

- **Autenticação.** O navegador não envia cabeçalhos no handshake do WebSocket, então o token vai
  no frame `CONNECT` do STOMP (`Authorization: Bearer <token>`) e é validado por um
  `ChannelInterceptor`. Sem token válido, o servidor responde com `ERROR` e fecha a conexão. O
  usuário da sessão é o destinatário: `ALUNO:12`, `PROFESSOR:7`, `ADMIN`.
- **Só a própria fila.** O cliente só pode assinar `/user/queue/notificacoes` e não envia
  mensagens (`SEND` é recusado).
- **Heartbeats** de 10 s nos dois sentidos.
- **Token expirado.** O token é conferido na conexão; quem desconecta quando ele expira é o
  frontend.

### Várias réplicas: competing consumers × fan-out

Os eventos de domínio são lidos no grupo `notificacao-service`, que é compartilhado: cada evento
vai para **uma** réplica (competing consumers), e é isso que garante uma notificação por evento.
Mas a réplica que grava a notificação pode não ser a réplica em que o usuário está conectado.

```
grade.posted ──(grupo notificacao-service: só uma réplica recebe)──> réplica A
                                                                        │ grava a notificação
                                                                        │ depois do commit:
                                                                        ▼
                                              notificacao.criada  { id, perfil, referenciaId }
                                                 │ (grupo anônimo: todas recebem)
                               ┌─────────────────┴─────────────────┐
                               ▼                                   ▼
                           réplica A                           réplica B
                     (usuário não está aqui:            (usuário conectado: busca a
                          descarta)                      notificação e envia pelo WebSocket)
```

1. Depois do commit, a réplica publica um aviso no tópico interno `notificacao.criada`
   (`StreamBridge`), só com o id e o destinatário, sem o texto.
2. Todas as réplicas leem esse tópico em **grupo anônimo** (binding sem `group`): cada instância
   recebe todos os avisos (fan-out), confere se o destinatário tem sessão nela e, se tiver, busca a
   notificação no banco e entrega com `convertAndSendToUser`.

O WebSocket é um atalho, não a fonte da verdade. Se o envio do aviso falhar (Kafka fora do ar,
réplica reiniciando), a notificação continua gravada e aparece no próximo carregamento da lista; o
frontend busca a contagem de não lidas a cada conexão.

## Métricas (`/actuator/prometheus`)

| Métrica | Significado |
|---|---|
| `notificacoes_criadas_total{tipo}` | Notificações gravadas (reentregas não contam) |
| `notificacoes_enviadas_total{canal="websocket"}` | Entregas em tempo real |
| `websocket_sessoes_ativas` | Sessões WebSocket abertas na réplica |
| `spring_cloud_stream_binder_kafka_offset{group,topic}` | Lag do consumidor (do binder Kafka) |

## Decisões registradas

- **Spring Cloud Stream com o Spring Boot 4.1.** O BOM do Spring Cloud `2025.1.4` (o mesmo do
  `api-gateway`) traz o Spring Cloud Stream `5.0.4`, compilado contra o Spring Boot 4.0.x. A
  combinação com o Boot 4.1.1 foi confirmada na prática: a suíte de testes sobe o contexto completo
  e o serviço foi executado contra um Kafka real (consumo, novas tentativas e DLT).
- **Teste da DLT.** O test binder do Spring Cloud Stream repete as tentativas e entrega as falhas
  ao canal de erros, mas não publica em DLT: isso é função do binder Kafka. Por isso
  `ConsumidoresStreamTest` confere que o payload inválido não é repetido e chega ao tratamento de
  erro, e que cada binding tem `dlq-name = <tópico>.DLT`. A publicação em si foi verificada com o
  Kafka do Docker Compose (comando abaixo).
- **Dados pessoais (LGPD).** Os logs trazem só ids, tópico, chave e tipo. O texto das notificações
  e os payloads nunca são logados; a mensagem de erro de um JSON inválido não repete o conteúdo.

## Rodando e testando

```sh
# Kafka e o banco pelo Docker Compose (na raiz do repositório)
docker compose up -d kafka postgres-notificacao
./mvnw spring-boot:run

./mvnw test
```

`NotificacaoPostgresTest` usa um PostgreSQL real pelo Testcontainers e precisa do Docker; sem ele, a
classe é pulada e os demais testes rodam normalmente.

Para ver a DLT funcionando, publique um payload inválido e leia o tópico de destino:

```sh
docker compose exec -T kafka sh -c "echo '12|{ quebrado' | /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server kafka:29092 --topic grade.posted --property parse.key=true --property 'key.separator=|'"
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:29092 \
  --topic grade.posted.DLT --from-beginning --timeout-ms 5000
```
