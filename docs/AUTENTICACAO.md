# Autenticação e perfis de acesso

O requisito do projeto pede um "Serviço de Usuários com JWT: autenticação, autorização e perfis
(aluno, professor, admin, pai)". A implementação escolhe sempre a opção mais simples de entender.

## Visão geral

```
Navegador ──login──> API Gateway ──> usuarios-service ──> confere a senha (BCrypt) e devolve um JWT
    │
    └──demais requisições com "Authorization: Bearer <token>"──> API Gateway
                                                                   │ valida assinatura e expiração
                                                                   │ confere o perfil da rota
                                                                   └──> gestao-escolar / gestao-administrativa
```

- **usuarios-service** (porta 8083, banco `usuarios`): guarda os usuários e emite o token no login.
- **api-gateway**: valida o token de toda requisição HTTP. Sem token válido, responde `401`. Com
  token válido mas perfil sem permissão, responde `403`. É o único ponto que valida o token,
  **exceto no WebSocket de notificações** (abaixo).
- **Serviços de negócio**: não mudaram. Confiam no gateway, que é a entrada externa das APIs (Ingress).
- **notificacao-service**: também valida o token (mesmo segredo), por dois motivos. Na API REST,
  ele precisa saber de quem são as notificações, e tira o destinatário das claims `perfil` e
  `referenciaId` do próprio token, nunca de parâmetros. No WebSocket, ele é a porta de entrada.

## Exceção: WebSocket de notificações

O gateway é o Spring Cloud Gateway **Server MVC**, que não faz proxy de WebSocket (a versão em uso,
5.0.4, não tem suporte). Por isso o endpoint `/ws` vai **direto** para o `notificacao-service`:

```
Navegador ──/api/...──> nginx do frontend / Ingress ──> API Gateway (valida o JWT) ──> serviços
    │
    └──/ws (WebSocket)──> nginx do frontend / Ingress "distrischool-ws" ──> notificacao-service
                                                                              valida o JWT no frame CONNECT
```

| Ambiente | Quem encaminha o `/ws` |
|---|---|
| Docker Compose | `frontend/nginx.conf` (`location = /ws`) |
| Frontend em modo de desenvolvimento | proxy do Vite (`/ws` → `http://localhost:8084`) |
| Kubernetes | Ingress `distrischool-ws` |

O navegador não consegue enviar cabeçalhos no handshake do WebSocket, então o token vai no frame
`CONNECT` do STOMP (`Authorization: Bearer <token>`). Sem token válido, o `notificacao-service`
recusa a conexão. Depois de conectado, o cliente só consegue assinar a própria fila
(`/user/queue/notificacoes`). Detalhes no
[README do notificacao-service](../notificacao-service/README.md).

## Decisões

| Decisão | Escolha | Por quê |
|---|---|---|
| Assinatura do token | HS256 com um segredo compartilhado (`JWT_SECRET`) | Uma única chave, configurada no usuarios-service e no gateway |
| Quem valida o token | O api-gateway (e o notificacao-service, para as notificações) | Os serviços de negócio continuam simples; o Ingress só expõe o gateway e o `/ws` |
| Renovação | Sem refresh token; o token vale 8 horas (`JWT_VALIDADE`) | Ao expirar, o frontend volta para o login |
| Senhas | Hash BCrypt | A senha em texto puro nunca é gravada |

## Perfis e permissões

| Rota | ADMIN | PROFESSOR | ALUNO | RESPONSAVEL |
|---|:-:|:-:|:-:|:-:|
| `POST /api/auth/login` | livre | livre | livre | livre |
| Consultas (`GET /api/...`) | ✔ | ✔ | ✔ | ✔ |
| Lançar notas e frequência (`/api/avaliacoes`, `/api/notas`, `/api/frequencias`) | ✔ | ✔ | | |
| Demais alterações (alunos, turmas, matrículas, professores...) | ✔ | | | |
| Administração de usuários (`/api/usuarios`) | ✔ | | | |
| Notificações do próprio usuário (`/api/notificacoes`, todos os métodos) | ✔ | ✔ | ✔ | ✔ |

As regras ficam em [`SecurityConfig`](../api-gateway/src/main/java/com/distrischool/api_gateway/config/SecurityConfig.java).

O campo `referenciaId` do usuário liga a conta ao professor (PROFESSOR) ou ao aluno (ALUNO e
RESPONSAVEL) nos outros serviços. Ele já vai no token e permite, numa próxima etapa, restringir o
aluno ao próprio boletim ou o professor às próprias turmas.

## Conteúdo do token

```json
{
  "iss": "distrischool-usuarios",
  "sub": "1",
  "nome": "Coordenação",
  "email": "coordenacao@distrischool.com",
  "perfil": "ADMIN",
  "referenciaId": 3,
  "iat": 1767268800,
  "exp": 1767297600
}
```

## Usando a API

A migration cria o administrador inicial `coordenacao@distrischool.com` / `123456`.

```bash
# 1. Login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email": "coordenacao@distrischool.com", "senha": "123456"}'
# {"token": "eyJ...", "expiraEm": "...", "usuario": {"id": 1, "perfil": "ADMIN", ...}}

# 2. Demais chamadas levam o token
TOKEN=eyJ...
curl http://localhost:8080/api/alunos -H "Authorization: Bearer $TOKEN"

# 3. Cadastrar um professor (somente ADMIN)
curl -X POST http://localhost:8080/api/usuarios \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"nome": "Maria Lima", "email": "maria@distrischool.com", "senha": "segredo1",
       "perfil": "PROFESSOR", "referenciaId": 3}'
```

Swagger do serviço: `http://localhost:8080/usuarios/swagger-ui.html` (via gateway).

## Segredo do JWT

| Ambiente | Onde fica |
|---|---|
| Execução local / Docker Compose | Valor padrão de desenvolvimento em `application.properties` e no `docker-compose.yml` |
| minikube | Secret `jwt-secret`, gerado em `k8s/overlays/minikube/kustomization.yaml` |
| Nuvem | Deve vir de um cofre (AWS Secrets Manager / Azure Key Vault), nunca do repositório |

O segredo precisa ter pelo menos 32 bytes e ser **igual** no usuarios-service, no api-gateway e no
notificacao-service; se forem diferentes, todo token é recusado com `401`.

## Próximos passos possíveis

- Criar a conta automaticamente quando um aluno ou professor for cadastrado (consumindo
  `student.created` do Kafka).
- Tela de administração de usuários no frontend.
- Regras finas nos serviços usando o `referenciaId` (aluno vê só o próprio boletim).
