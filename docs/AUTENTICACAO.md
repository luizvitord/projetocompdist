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
- **api-gateway**: único ponto que valida o token. Sem token válido, responde `401`. Com token
  válido mas perfil sem permissão, responde `403`.
- **Serviços de negócio**: não mudaram. Confiam no gateway, que é a única entrada externa (Ingress).

## Decisões

| Decisão | Escolha | Por quê |
|---|---|---|
| Assinatura do token | HS256 com um segredo compartilhado (`JWT_SECRET`) | Uma única chave, configurada no usuarios-service e no gateway |
| Quem valida o token | Só o api-gateway | Os serviços de negócio continuam simples; o Ingress só expõe o gateway |
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

O segredo precisa ter pelo menos 32 bytes e ser **igual** no usuarios-service e no api-gateway;
se forem diferentes, todo token é recusado com `401`.

## Próximos passos possíveis

- Criar a conta automaticamente quando um aluno ou professor for cadastrado (consumindo
  `student.created` do Kafka).
- Tela de administração de usuários no frontend.
- Regras finas nos serviços usando o `referenciaId` (aluno vê só o próprio boletim).
