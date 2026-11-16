# Teste de ponta a ponta (`scripts/e2e.ps1`)

O `e2e.ps1` testa o DistriSchool inteiro pelo `api-gateway`, como um cliente de verdade: cria
os próprios dados pela API, confere status HTTP, corpos `ProblemDetail`, regras de negócio,
cálculos, eventos do Kafka e notificações (inclusive em tempo real), e para containers para testar
a tolerância a falhas.

- Roda no **Windows PowerShell 5.1** e no **PowerShell 7** (Windows e Linux, como no GitHub
  Actions). Os arquivos só têm ASCII: o 5.1 lê scripts sem BOM na codificação ANSI do sistema.
- Cada execução usa um sufixo próprio (data, hora e um número aleatório) nos códigos, emails e
  nomes, e gera CPFs válidos novos. Dá para rodar quantas vezes quiser, sem banco vazio e sem 409
  por colisão.
- Consistência eventual (Kafka, réplica de turmas, notificações) é esperada com polling e prazo
  (`Wait-Ate`), nunca com uma pausa fixa.
- Nenhum container fica parado: o script registra cada `docker compose stop` e, no fim (também em
  falha ou Ctrl+C), roda `docker compose start` em todos.

## Como rodar

Com a pilha no ar (`docker compose up -d --build` na raiz, todos os containers saudáveis):

```powershell
# Windows PowerShell 5.1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\e2e.ps1

# PowerShell 7 (Windows, Linux, macOS)
pwsh ./scripts/e2e.ps1

# Só algumas seções, sem as que param containers
pwsh ./scripts/e2e.ps1 -Secoes login,perfis,notificacoes -PularResiliencia
```

Saída: uma linha por verificação, `[OK]`, `[FALHOU]`, `[AVISO]` (comportamento conhecido, que
não conta como falha) ou `[PULADO]`, com o detalhe da resposta embaixo. No fim, um resumo por seção
(total, ok, falhas, avisos, pulados e tempo) e a lista de falhas, avisos e pulados. O relatório
JUnit vai para `scripts/e2e-resultado.xml` (fora do Git), no formato que o GitHub Actions mostra
como testes: uma `testsuite` por seção, um `testcase` por verificação, `[PULADO]` como `skipped`
e `[AVISO]` como teste aprovado com a mensagem em `system-out`.

**Exit code:** `0` sem nenhum `[FALHOU]`; `1` com alguma falha (ou se não deu para religar um
container); `2` com um nome de seção desconhecido em `-Secoes`.

## Parâmetros

| Parâmetro | Padrão | Para quê |
|---|---|---|
| `-GatewayUrl` | `http://localhost:8080` | Endereço do `api-gateway` |
| `-Secoes` | todas | Ids das seções a rodar, separados por vírgula (tabela abaixo). As escolhidas rodam na ordem da tabela |
| `-PularResiliencia` | — | Pula as seções que param containers (marcadas na tabela) |
| `-WebSocketUrl` | `ws://localhost:5173/ws` | WebSocket das notificações, pelo nginx do frontend |
| `-PrometheusUrl` | `http://localhost:9090` | Fora do ar: as verificações do Prometheus ficam `[PULADO]` |
| `-GrafanaUrl` | `http://localhost:3001` | Idem para o Grafana |
| `-GrafanaUsuario`, `-GrafanaSenha` | `admin`, `distrischool` | Login do Grafana (API de busca de dashboards) |
| `-ResultadoXml` | `scripts/e2e-resultado.xml` | Onde gravar o relatório JUnit |

Cada seção cria os dados de que precisa; as que dependem de usuários de teste (PROFESSOR, ALUNO,
RESPONSAVEL) os criam na primeira vez. Por isso qualquer combinação de `-Secoes` funciona sozinha.

## Seções

Rodam nesta ordem. Os erros esperados (400, 404, 409, 503) sempre conferem o corpo `ProblemDetail`
(`application/problem+json` com `status` e `detail`; nos 400 de validação, a lista `erros` com os
campos). Os 403 do gateway também são ProblemDetail; os 401 vêm sem corpo, com
`WWW-Authenticate: Bearer`.

| Id | Endpoints | O que verifica |
|---|---|---|
| `login` | `POST /api/auth/login`, qualquer rota `/api` | Login da coordenação (token, perfil, expiração); 401 sem token, com a assinatura adulterada, com o payload alterado e com token mal formado; senha errada (401, sem token); campos vazios (400) |
| `perfis` | `/api/usuarios` (CRUD), rotas de escrita de cada serviço, consultas de ALUNO e RESPONSAVEL, `/api/notificacoes` | CRUD de usuários (Location, sem senha na resposta, email duplicado na criação e na edição, validação, 404, exclusão lógica que some da listagem e impede o login); tabela de permissões do `docs/AUTENTICACAO.md` com usuários PROFESSOR, ALUNO e RESPONSAVEL reais: PROFESSOR lança notas, corrige e faz chamada, recebe 403 nos cadastros e em `/api/usuarios`; ALUNO e RESPONSAVEL recebem 403 em toda escrita, leem o próprio aluno, boletim, frequência e matrículas (200, com o id dele no corpo e só as matrículas dele), recebem 403 com ProblemDetail nas listagens (alunos, turmas, professores, técnicos, matrículas sem `alunoId`), nos dados de outro aluno, com `alunoId` repetido e com `/api/alunos/{id}abc`, e usam as próprias notificações; uma conta ALUNO sem `referenciaId` recebe 403 no boletim e 200 nas notificações |
| `infra` | `/actuator/health`, `/{servico}/v3/api-docs`, `/{servico}/swagger-ui.html`, Prometheus, Grafana | Health do gateway; todos os containers rodando e saudáveis; OpenAPI e Swagger UI de `administrativa`, `escolar`, `usuarios` e `notificacao` pelo gateway; um alvo UP por serviço no Prometheus e as 6 regras de alerta sem erro; health do Grafana, dashboard de visão geral provisionado e datasource respondendo |
| `administrativa` | `/api/disciplinas`, `/api/professores`, `/api/tecnicos`, `/api/turmas` (+ ofertas e professores) | CRUD com validação, duplicados (409) e filtros de cada cadastro; exclusão lógica de disciplinas e técnicos; CPF inválido e duplicado sem repetir o CPF no erro; `dataAdmissao` futura; oferta com atribuição automática do professor; disciplina inativa não pode ser ofertada; e, **pela API do gestao-escolar**: código novo da turma na réplica, oferta retirada recusa chamada e avaliação (409), turma excluída recusa matrícula (409) mantendo o histórico, disciplina renomeada chega à réplica e ao texto da notificação |
| `alunos` | `/api/alunos` | CRUD; número de matrícula no formato ano + 6 dígitos, crescente; `dataNascimento` futura e outras validações; filtros `nome`, `matricula` (exato) e `turmaId` (só matrículas ATIVA); DELETE com matrícula (409) e sem (204) |
| `matriculas` | `/api/matriculas`, `/status`, `/transferencia` | Todas as transições válidas e as inválidas (409); `motivo` obrigatório (400); uma matrícula vigente (ATIVA ou TRANCADA) por ano; capacidade, inclusive na reativação e na transferência; transferência no mesmo ano (origem TRANSFERIDA, nova ATIVA com `matriculaOrigemId`) e para outro ano (409); filtros |
| `frequencia` | `/api/frequencias/chamadas`, `/relatorio`, `/alunos/{id}` | Criação e edição; matrícula faltando, sobrando ou repetida (400 com a lista); data futura ou fora do ano letivo (400); mesma data (409); filtros; relatório por turma e frequência por aluno conferidos com percentual e `abaixoDoMinimo` calculados no teste |
| `notas` | `/api/avaliacoes`, `/notas`, `/api/notas/{id}`, `/historico` | CRUD de avaliação e valores padrão; uma RECUPERACAO por bimestre (409); DELETE com notas (409); lançamento em lote; nota acima da `notaMaxima`, de matrícula não ATIVA, negativa ou repetida; correção com motivo, sem motivo (400), acima do máximo e para o mesmo valor; histórico |
| `boletim` | `/api/alunos/{id}/boletim` | Pesos e notas máximas diferentes, recuperação e avaliações pendentes; médias de bimestre e anual calculadas no teste (normalização para 0–10, média ponderada, recuperação, 2 casas HALF_UP); situação CURSANDO, APROVADO, REPROVADO_POR_NOTA e REPROVADO_POR_FALTA (um aluno para cada) |
| `busca` | `GET` de `/api/alunos`, `/api/professores`, `/api/tecnicos`, `/api/disciplinas`, `/api/turmas`, `/api/matriculas`, `/api/avaliacoes` | Parâmetro `busca` de cada listagem: um registro com texto único acentuado (dados montados com `[char]`, o arquivo continua ASCII) é encontrado por parte do texto em minúsculas e sem acento, por cada campo pesquisado (nome, número de matrícula, email, qualificação, setor, código, nome da turma e da disciplina pela réplica), e um registro parecido com outro identificador não vem; busca só com espaços não filtra; o CPF do técnico não é pesquisável (LGPD); combinação (E) com um filtro existente de cada endpoint (`turmaId`, `nome`, `cargo`, `ativa`, `anoLetivo`, `status`, `bimestre`), nos dois sentidos; 400 com `erros.busca` para 101 caracteres e 200 para 100 |
| `fluxo` | os principais, em sequência | Caminho completo de um aluno: disciplina, professor, turma, oferta, aluno, matrícula, 4 chamadas, 2 notas, boletim e frequência |
| `notificacoes` | `/api/notificacoes`, `/ws` (STOMP), Kafka | Um evento de cada tipo (NOTA_LANCADA, FALTA_REGISTRADA, MATRICULA_CRIADA, MATRICULA_ALTERADA, ALUNO_CADASTRADO, PROFESSOR_ATRIBUIDO, TECNICO_CADASTRADO) chega aos destinatários certos e a nenhum outro; contagem, `somenteNaoLidas`, marcar uma e todas; PATCH de outro usuário (404); WebSocket: CONNECT com token, nota chegando em tempo real, outro usuário não recebe, sem token / token inválido / destino alheio recebem ERROR; o mesmo `grade.posted` publicado duas vezes gera uma notificação só |
| `dlt` | Kafka | `schedule.updated` inválido chega ao `schedule.updated.DLT` (uma cópia de cada consumidor) e os dois consumidores aplicam o evento válido seguinte da mesma partição |
| `resiliencia-administrativa` * | — | Com o `gestao-administrativa-service` parado: matrícula e chamada em turma já replicada funcionam; turma nunca replicada responde 503; tudo volta depois de religar |
| `resiliencia-notificacao` * | — | Com o `notificacao-service` parado: o resto responde; `/api/notificacoes` dá 503; ao religar, a notificação do evento do intervalo aparece |
| `outbox` * | Kafka, `outbox_evento` | Com o Kafka parado, a criação de turma responde rápido e o evento fica pendente na outbox; ao religar, é publicado e chega à réplica |
| `circuitbreaker` * | `/api/alunos` | Com o `gestao-escolar-service` parado, 503 com ProblemDetail e o resto do gateway respondendo; volta depois de religar |
| `ratelimit` | `POST /api/auth/login` | 10 logins com senha errada dão 401 e o 11º dá 429 com `Retry-After` (sempre por último: depois dela o limite fica esgotado por até um minuto) |

\* Param containers; puladas com `-PularResiliencia`.

### `[AVISO]` esperados

Nenhum: uma execução completa termina com 0 falhas e 0 avisos. O `[AVISO]` continua disponível
para comportamentos conhecidos que não devem contar como falha. A antiga falha de autorização
(ALUNO e RESPONSAVEL lendo qualquer `/api/**`) foi corrigida no gateway e hoje é conferida como
verificação normal na seção `perfis`.

### Detalhes de implementação

- **Logins**: o gateway aceita 10 logins por minuto por IP. O script reaproveita os tokens, espera
  antes do 9º login dentro de 60 s e, se receber 429 (por exemplo, logo depois de outra execução),
  espera o `Retry-After`.
- **Esperas sem efeito colateral**: quando a espera é por um evento que muda uma regra (oferta
  retirada, turma excluída), a sonda é uma requisição que nunca grava dados nos dois estados.
- **Réplica de turmas** é conferida pela API do gestao-escolar (o boletim lista as disciplinas
  ofertadas na turma atual com o código e o nome da réplica). Só a seção `outbox` olha o banco,
  porque qualquer chamada ao gestao-escolar sobre a turma acionaria o fallback síncrono e
  mascararia o teste.
- **WebSocket**: `System.Net.WebSockets.ClientWebSocket`, pelo nginx do frontend. No .NET
  Framework (5.1), o `ClientWebSocket` recusa o handshake direto do Tomcat
  (`Connection: upgrade, keep-alive`) e cancelar uma leitura pendente aborta o socket; por isso a
  conexão passa pelo nginx (o caminho documentado para o navegador) e o cliente mantém uma única
  leitura pendente, esperada com `Task.Wait(timeout)`.
- **Kafka**: as mensagens são publicadas pelo `kafka-console-producer.sh` do container `kafka`,
  enviadas pelo stdin (o 5.1 não escapa as aspas do JSON nos argumentos de programas externos).
- **Divergências** entre o documentado e o comportamento real ficam em
  [docs/E2E-PROBLEMAS.md](../docs/E2E-PROBLEMAS.md).

## Arquivos

| Arquivo | Conteúdo |
|---|---|
| `e2e.ps1` | Ponto de entrada: parâmetros, catálogo das seções, resumo, JUnit e a garantia de religar os containers |
| `e2e/Nucleo.ps1` | Registro das verificações, execução das seções, `Wait-Ate`, resumo e JUnit |
| `e2e/Http.ps1` | `Invoke-Api` (5.1 e 7), verificações de status e ProblemDetail, logins |
| `e2e/Docker.ps1` | `docker compose`, saúde dos containers, psql, Kafka (producer, consumer, grupos) |
| `e2e/Dados.ps1` | Identificadores únicos, CPF válido, cadastros, usuários de teste e o cálculo esperado das médias e da frequência |
| `e2e/Stomp.ps1` | Cliente STOMP sobre WebSocket |
| `e2e/Secao*.ps1` | Uma ou mais seções por arquivo |
