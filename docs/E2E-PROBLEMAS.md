# Problemas encontrados pelo e2e

Divergências entre o comportamento documentado e o real, encontradas ao escrever e rodar o
`scripts/e2e.ps1` (validado em 15/11/2026). O código das aplicações não foi alterado e nenhuma
verificação foi adaptada para aceitar um comportamento diferente do documentado.

Nenhuma regra de negócio, cálculo ou status HTTP documentado divergiu: as 604 verificações passam.
As divergências abaixo são de documentação desatualizada; as observações no fim são
comportamentos que a documentação não cobre.

## 1. Payloads com `matriculaId`/`alunoId` no `gestao-escolar-service/CLAUDE.md`

**O que foi feito:** as requisições exatamente como o `gestao-escolar-service/CLAUDE.md` as
descreve, pelo gateway, com o token da coordenação.

| Requisição | Documentado | Obtido |
|---|---|---|
| `POST /api/matriculas` | `{ "alunoId": 1, "turmaId": 12 }` (Etapa 4) | 400 `{"erros": {"matriculaAluno": "must not be blank"}}` |
| `POST /api/avaliacoes/{id}/notas` | `[{ "matriculaId": 4, "valor": 8.5 }]` (Etapa 6) | 400 `{"erros": {"notas[0].matriculaAluno": "must not be blank"}}` |
| `POST /api/frequencias/chamadas` | registros com `matriculaId` (Etapa 5) | 400 `{"erros": {"registros[0].matriculaAluno": "must not be blank"}}` |

**Esperado (pela documentação):** as requisições acima seriam aceitas.

**Obtido:** a API identifica o aluno pelo **número de matrícula** (`matriculaAluno`, ex.
`"2026000001"`) nos três casos. As respostas continuam trazendo `matriculaId` (registros da
chamada, notas). O `CLAUDE.md` da raiz menciona que "algumas [APIs] usam o número de matrícula do
aluno", mas o `CLAUDE.md` do serviço não foi atualizado.

**Onde está no código:**
- `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/matricula/MatriculaRequest.java`
- `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/avaliacao/LancamentoNotaRequest.java`
- `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/frequencia/ChamadaRequest.java` (`Registro`)

**No e2e:** usa a API real (`matriculaAluno`), como o `scripts/e2e.ps1` anterior já fazia.
**Sugestão:** atualizar os exemplos do `gestao-escolar-service/CLAUDE.md`.

## Observações (comportamentos que a documentação não cobre)

Não são falhas do e2e; ficam registradas porque um cliente da API pode tropeçar nelas.

### `Location` aponta para o endereço interno do serviço

**O que foi feito:** `POST /api/usuarios`, `/api/disciplinas` e `/api/alunos` pelo gateway.

**Obtido:** `Location: http://usuarios-service:8083/api/usuarios/22`,
`http://gestao-administrativa-service:8081/api/disciplinas/62`,
`http://gestao-escolar-service:8082/api/alunos/257`. O endereço só existe dentro da rede do
Compose (ou do cluster); um cliente externo não consegue seguir o link. O e2e confere só o final
do caminho (`/api/usuarios/{id}`).

**Onde:** os serviços montam o `Location` a partir da própria requisição
(`ServletUriComponentsBuilder` nos controllers), e o `api-gateway` (Spring Cloud Gateway Server
MVC) não repassa o `Host` original nem cabeçalhos `X-Forwarded-*` que os serviços usem.

### 401 e 403 do gateway sem corpo `ProblemDetail`

Os 401 e 403 vêm do Spring Security do gateway, sem corpo (o 401 traz
`WWW-Authenticate: Bearer ...`). Todos os outros erros da API são `ProblemDetail`, inclusive o 401
de senha errada (do `usuarios-service`) e o 429 do rate limit. O `docs/AUTENTICACAO.md` só fala
dos status, então o e2e confere status e `WWW-Authenticate`.

### WebSocket: sem `RECEIPT` para o `SUBSCRIBE`

O STOMP 1.2 prevê um frame `RECEIPT` quando o cliente manda o cabeçalho `receipt`. O broker simples
do Spring usado pelo `notificacao-service` não responde `RECEIPT` para `SUBSCRIBE`. A documentação
do projeto não promete isso; o e2e não depende dele.

### Falha de autorização já conhecida

ALUNO e RESPONSAVEL conseguem fazer GET em qualquer `/api/**`, inclusive dados de outros alunos
(regra `GET /api/** -> authenticated` no `SecurityConfig` do gateway). Já conhecida e fora do
escopo desta tarefa; o e2e marca cada caso como `[AVISO]`.
