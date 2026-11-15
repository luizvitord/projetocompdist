# DistriSchool

Plataforma de gestão escolar com quatro microsserviços (gestão administrativa, gestão escolar, usuários e notificações) e um API Gateway, com mensageria pelo Kafka, monitoramento com Prometheus e Grafana e testes de carga com JMeter.

| Serviço | Porta | Responsabilidade |
|---|---|---|
| `api-gateway` | 8080 | Entrada única; valida o JWT e aplica as permissões por perfil |
| `gestao-administrativa-service` | 8081 | Professores, técnicos, turmas e disciplinas |
| `gestao-escolar-service` | 8082 | Alunos, matrículas, frequência, notas e boletim |
| `usuarios-service` | 8083 | Login e emissão do JWT |
| [`notificacao-service`](notificacao-service/README.md) | 8084 | Serviço de Comunicação: notificações a partir dos eventos do Kafka (Spring Cloud Stream), entregues em tempo real por WebSocket |
| `frontend` | 5173 | Portal (React + Vite) |
| Prometheus | 9090 | Coleta das métricas e regras de alerta |
| Grafana | 3001 | Dashboard **DistriSchool — Visão geral** |

## Executar localmente

Para a pilha completa em containers: `docker compose up --build`. O portal fica em `http://localhost:5173`, o gateway em `http://localhost:8080` e o health check em `/actuator/health`.

Para desenvolvimento do frontend: inicie a infraestrutura e os serviços, depois execute `cd frontend && npm run dev`. O Vite encaminha `/api` para o gateway e o WebSocket `/ws` para o `notificacao-service`.

Login: `coordenacao@distrischool.com` / `123456` (administrador criado pela migration do `usuarios-service`). O login devolve um JWT que o API Gateway exige em todas as rotas `/api`; perfis e permissões estão em [docs/AUTENTICACAO.md](docs/AUTENTICACAO.md).

`scripts/e2e.ps1` testa todas as funcionalidades de ponta a ponta pelo gateway, contra o Compose no ar: autenticação e perfis, cadastros, matrículas, frequência, notas e boletim, notificações (inclusive em tempo real), Kafka, tolerância a falhas e observabilidade. Roda no Windows PowerShell 5.1 e no PowerShell 7, leva uns 5 minutos e gera um relatório JUnit; seções, parâmetros e cobertura em [scripts/README.md](scripts/README.md).

## Portal do aluno e do responsável

Usuários `ALUNO` e `RESPONSAVEL` entram pelo mesmo login e vão para `/portal`: dados do aluno ligado à conta, seletor de ano letivo (pelas matrículas dele), boletim, frequência e a central de notificações. Eles não veem a navegação da coordenação, e as URLs dela redirecionam para o portal. O gateway só deixa esses perfis lerem o aluno cujo id é o `referenciaId` do token; todo o resto responde `403` ([regras](docs/AUTENTICACAO.md#aluno-e-responsavel-só-o-aluno-ligado-à-conta)).

Para entrar no portal, crie pela API (como coordenação) um usuário com `referenciaId` = id de um aluno e faça login com ele:

```bash
curl -X POST http://localhost:8080/api/usuarios -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json'   -d '{"nome": "Responsável da Ana", "email": "resp.ana@distrischool.com", "senha": "segredo1", "perfil": "RESPONSAVEL", "referenciaId": 12}'
```

Uma conta sem `referenciaId` vê só um aviso para procurar a coordenação.

## Notificações em tempo real

O sino no topo do portal mostra as notificações do usuário logado e a contagem de não lidas; uma notificação nova chega por WebSocket (STOMP) e aparece na hora. Para ver: crie um usuário `RESPONSAVEL` ligado a um aluno (pela API, `POST /api/usuarios`), entre com ele numa janela anônima e, na janela da coordenação, lance uma nota para o aluno. O passo a passo, a arquitetura (tópicos, destinatários, idempotência, DLT, fan-out entre réplicas e por que o WebSocket não passa pelo gateway) estão em [docs/NOTIFICACOES.md](docs/NOTIFICACOES.md).

## Monitoramento

O Compose também sobe o Prometheus em `http://localhost:9090` (alvos em `/targets`, alertas em `/alerts`) e o Grafana em `http://localhost:3001` (`admin` / `distrischool`), já com o dashboard **DistriSchool — Visão geral**: throughput, latência p50/p95/p99, erros, réplicas, JVM, HikariCP, circuit breakers, outbox, lag do Kafka e notificações. Nada é configurado à mão: datasource, dashboard e alertas vêm de arquivos. Painéis, alertas e cálculo dos percentis em [docs/MONITORAMENTO.md](docs/MONITORAMENTO.md).

## Testes de carga

`.\load-tests\executar.ps1 -Perfil fumaca -Alvo compose` roda o JMeter pelo Docker (sem instalação) e gera o relatório HTML em `load-tests/resultados/`. Há perfis de fumaça, carga, estresse e HPA, contra o Compose ou o minikube; perfis, roteiro de demonstração e como ler o relatório em [load-tests/README.md](load-tests/README.md), e as execuções registradas em [load-tests/RESULTADOS.md](load-tests/RESULTADOS.md).

## Kubernetes

Use `kubectl apply -k k8s/overlays/minikube`. O Ingress expõe o API Gateway para `/api`, `/administrativa`, `/escolar`, `/usuarios` e `/notificacao`; os serviços de negócio permanecem internos. A única exceção é o WebSocket das notificações (`/ws`), que vai direto para o `notificacao-service` porque o gateway não faz proxy de WebSocket ([detalhes](docs/AUTENTICACAO.md)). O Prometheus e o Grafana também sobem no cluster e são acessados por port-forward (`kubectl port-forward -n distrischool svc/prometheus 9090` e `svc/grafana 3001:3000`). Imagens, recursos, port-forwards e o roteiro de demonstração estão no [k8s/README.md](k8s/README.md).

## Documentação

| Documento | Assunto |
|---|---|
| [docs/EXECUTAR-FRONTEND-API-GATEWAY.md](docs/EXECUTAR-FRONTEND-API-GATEWAY.md) | Executar o portal e o API Gateway |
| [docs/AUTENTICACAO.md](docs/AUTENTICACAO.md) | Login, JWT, perfis e permissões |
| [docs/NOTIFICACOES.md](docs/NOTIFICACOES.md) | Arquitetura das notificações |
| [docs/MONITORAMENTO.md](docs/MONITORAMENTO.md) | Métricas, dashboard e alertas |
| [load-tests/README.md](load-tests/README.md) | Testes de carga com JMeter |
| [scripts/README.md](scripts/README.md) | Teste de ponta a ponta (`e2e.ps1`): seções, parâmetros e cobertura |
| [docs/E2E-PROBLEMAS.md](docs/E2E-PROBLEMAS.md) | Divergências entre o documentado e o real encontradas pelo e2e |
| [k8s/README.md](k8s/README.md) | Implantação no Kubernetes (minikube) |
| [notificacao-service/README.md](notificacao-service/README.md) | Detalhes do `notificacao-service` |
