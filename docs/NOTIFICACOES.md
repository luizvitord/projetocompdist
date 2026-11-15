# Notificações

O `notificacao-service` é o Serviço de Comunicação do DistriSchool: lê os eventos de domínio que os
outros serviços publicam no Kafka (Spring Cloud Stream), grava uma notificação para cada destinatário
e a entrega em tempo real por WebSocket (STOMP). Este documento descreve a arquitetura; os detalhes de
implementação (bindings, API, métricas, testes) estão no
[README do serviço](../notificacao-service/README.md).

## Visão geral

```
 gestao-escolar-service          gestao-administrativa-service
   (alunos, matrículas,            (turmas, professores,
    frequência, notas)              técnicos)
          │ grava o evento na mesma transação do dado (outbox_evento)
          │ e o OutboxRelay publica no Kafka
          ▼                                  ▼
 ┌───────────────────────────── Kafka ─────────────────────────────┐
 │ grade.posted  attendance.recorded  enrollment.created/updated   │
 │ student.created          teacher.assigned  staff.created        │
 │                          schedule.updated                       │
 └──────────────┬──────────────────────────────────────────────────┘
                │ grupo "notificacao-service": cada evento vai para UMA réplica
                ▼
 ┌──────────────────────── notificacao-service ────────────────────┐
 │ 1. monta título, mensagem e link (nomes do cache turma_nome /   │
 │    disciplina_nome, alimentado pelo schedule.updated)           │
 │ 2. grava uma linha por destinatário (ON CONFLICT DO NOTHING)    │──> PostgreSQL "notificacao"
 │ 3. depois do commit, publica um aviso em notificacao.criada     │
 └──────────────┬──────────────────────────────────────────────────┘
                │ notificacao.criada, grupo anônimo: TODAS as réplicas recebem
                ▼
      réplica em que o destinatário está conectado
                │ STOMP: /user/queue/notificacoes
                ▼
 Navegador ──/ws (fora do gateway)──> nginx do frontend / Ingress "distrischool-ws"
    │         token no frame CONNECT
    └──/api/notificacoes──> api-gateway ──> notificacao-service (lista, contagem, marcar como lida)
```

## Tópicos consumidos e destinatários

O destinatário é o par (`perfil`, `referenciaId`), o mesmo que vai no JWT: `referenciaId` é o id do
professor (`PROFESSOR`) ou do aluno (`ALUNO` e `RESPONSAVEL`). `ADMIN` não tem referência e representa
a coordenação: todos os administradores veem as mesmas notificações.

| Tópico | Produtor | Destinatários | Tipo |
|---|---|---|---|
| `grade.posted` | escolar | `ALUNO` e `RESPONSAVEL` de cada aluno com nota | `NOTA_LANCADA` |
| `attendance.recorded` | escolar | `ALUNO` e `RESPONSAVEL` de cada aluno com falta | `FALTA_REGISTRADA` |
| `enrollment.created` | escolar | `ALUNO` e `RESPONSAVEL` do aluno | `MATRICULA_CRIADA` |
| `enrollment.updated` | escolar | `ALUNO` e `RESPONSAVEL` do aluno; também `ADMIN` se a matrícula foi cancelada ou transferida | `MATRICULA_ALTERADA` |
| `student.created` | escolar | `ADMIN` | `ALUNO_CADASTRADO` |
| `teacher.assigned` | administrativa | `PROFESSOR` atribuído | `PROFESSOR_ATRIBUIDO` |
| `staff.created` | administrativa | `ADMIN` | `TECNICO_CADASTRADO` |
| `schedule.updated` | administrativa | nenhum: só atualiza o cache de nomes de turmas e disciplinas | — |
| `notificacao.criada` | o próprio `notificacao-service` | interno: aviso para a entrega em tempo real | — |

O `schedule.updated` também é lido pelo `gestao-escolar-service`, no grupo dele. Grupos diferentes
recebem cada um todos os eventos (publish/subscribe); dentro de um grupo, as réplicas dividem as
partições.

O serviço lê o JSON sem as classes dos produtores e ignora campos desconhecidos. Ele nunca chama
outro serviço por HTTP: se o nome de uma turma ainda não está no cache, o texto usa "turma #12".

## Entrega pelo menos uma vez e idempotência

Os produtores usam uma **transactional outbox**: o evento é gravado na mesma transação do dado e um
relay publica no Kafka. A entrega é **pelo menos uma vez**: um mesmo evento pode chegar duas vezes
(relay reiniciado antes de marcar o envio, rebalanceamento do grupo, nova tentativa depois de falha).

Os eventos não têm id, então cada notificação guarda `evento_hash` = SHA-256 de
tópico + chave + payload bruto. O índice único (`evento_hash`, `destinatario_perfil`,
`destinatario_referencia_id`), criado com `NULLS NOT DISTINCT` para valer também nas linhas de `ADMIN`,
barra a duplicata, e a inserção usa `ON CONFLICT DO NOTHING`: a reentrega é ignorada sem erro. A
métrica `notificacoes_criadas_total` só conta inserções novas.

## Falhas e DLT

- **Erro ao processar** (banco fora, por exemplo): 3 tentativas no total, com espera de 1 s e 2 s.
  Depois disso, o registro vai para `<tópico>.DLT` (a mesma convenção do `gestao-escolar-service`) e
  o consumidor segue para o próximo.
- **Payload fora do contrato** (JSON inválido, campo obrigatório ausente): não adianta repetir; vai
  direto para a DLT.
- **Serviço fora do ar**: o resto do sistema não depende dele. Notas, matrículas e cadastros
  continuam funcionando, e os eventos ficam no Kafka. Quando o serviço volta, o grupo retoma do último
  offset confirmado e as notificações do intervalo aparecem.

Os logs trazem só ids, tópico, chave e tipo; o texto das notificações e os payloads (que têm dados de
alunos) nunca são logados (LGPD).

## Várias réplicas: competing consumers e fan-out

Com duas ou mais réplicas, aparecem dois requisitos opostos:

- **Gravar cada notificação uma vez.** Os eventos de domínio são lidos no grupo compartilhado
  `notificacao-service`: cada evento vai para uma só réplica (competing consumers).
- **Entregar a quem está conectado.** O usuário pode ter a sessão WebSocket em outra réplica, que não
  recebeu o evento.

A solução é um segundo tópico, interno. Depois do commit, a réplica que gravou publica em
`notificacao.criada` um aviso só com o id da notificação e o destinatário (sem o texto). Todas as
réplicas leem esse tópico em **grupo anônimo** (um grupo único por instância), então cada uma recebe
todos os avisos (fan-out). A réplica que tem uma sessão do destinatário busca a notificação no banco
e a envia; as outras descartam o aviso.

O WebSocket é um atalho, não a fonte da verdade: se o aviso se perder, a notificação continua gravada
e aparece na lista e na contagem de não lidas, que o frontend busca a cada conexão.

## WebSocket fora do gateway

O `api-gateway` é o Spring Cloud Gateway **Server MVC**, que não faz proxy de WebSocket. Por isso o
`/ws` vai direto para o `notificacao-service`:

| Ambiente | Quem encaminha o `/ws` |
|---|---|
| Docker Compose | nginx do frontend (`frontend/nginx.conf`) |
| `npm run dev` | proxy do Vite, para `http://localhost:8084` |
| Kubernetes | Ingress `distrischool-ws` (timeouts de 3600 s) |

Como o gateway não está no caminho, o próprio serviço valida o JWT. O navegador não envia cabeçalhos
no handshake do WebSocket, então o token vai no frame `CONNECT` do STOMP
(`Authorization: Bearer <token>`); sem token válido, a conexão é recusada. Depois de conectado, o
cliente só pode assinar a própria fila (`/user/queue/notificacoes`) e não pode enviar mensagens. A
API REST (`/api/notificacoes`) continua passando pelo gateway, e o serviço tira o destinatário das
claims do token, nunca de parâmetros. Mais em [AUTENTICACAO.md](AUTENTICACAO.md).

## Vendo as notificações em tempo real

Com o Docker Compose no ar (`docker compose up --build`):

1. Entre no portal (`http://localhost:5173`) como `coordenacao@distrischool.com` / `123456` e
   anote o id de um aluno matriculado numa turma (a página do aluno é `/alunos/<id>`).
2. Crie um usuário `RESPONSAVEL` ligado a esse aluno. Não há tela para isso; use a API (troque
   `12` pelo id do aluno):

   ```powershell
   $login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
       -ContentType 'application/json' -Body '{"email":"coordenacao@distrischool.com","senha":"123456"}'
   Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/usuarios `
       -Headers @{ Authorization = 'Bearer ' + $login.token } -ContentType 'application/json' `
       -Body '{"nome":"Responsavel Teste","email":"responsavel@teste.com","senha":"segredo123","perfil":"RESPONSAVEL","referenciaId":12}'
   ```

3. Abra **outra janela anônima** (a sessão fica no navegador) e entre como
   `responsavel@teste.com` / `segredo123`.
4. Na primeira janela, lance uma nota ou registre uma falta para esse aluno.
5. Na janela do responsável, aparece um aviso e o sino do topo mostra a contagem de não lidas; o
   painel do sino lista a notificação, com o link para o aluno.

Para ver a tolerância a falhas: `docker compose stop notificacao-service`, lance outra nota (o portal
continua funcionando), `docker compose start notificacao-service`; a notificação aparece quando o
serviço volta e lê o Kafka.

O `scripts/e2e.ps1` automatiza esse fluxo (responsável ligado ao aluno, nota lançada, notificação na
lista e na contagem). A reentrega do mesmo evento sem duplicata é coberta pelos testes do serviço
(`NotificacaoServiceTest` e, contra um PostgreSQL real, `NotificacaoPostgresTest`). No
Grafana, o painel **Notificações por minuto** mostra as notificações criadas e entregues, e
**Sessões WebSocket**, quantos usuários estão conectados ([MONITORAMENTO.md](MONITORAMENTO.md)).
