# DistriSchool

Plataforma de gestão escolar com quatro microsserviços (gestão administrativa, gestão escolar, usuários e notificações) e um API Gateway.

| Serviço | Porta | Responsabilidade |
|---|---|---|
| `api-gateway` | 8080 | Entrada única; valida o JWT e aplica as permissões por perfil |
| `gestao-administrativa-service` | 8081 | Professores, técnicos, turmas e disciplinas |
| `gestao-escolar-service` | 8082 | Alunos, matrículas, frequência, notas e boletim |
| `usuarios-service` | 8083 | Login e emissão do JWT |
| [`notificacao-service`](notificacao-service/README.md) | 8084 | Notificações e avisos a partir dos eventos do Kafka (Spring Cloud Stream) |

## Executar localmente

Para a pilha completa em containers: `docker compose up --build`. O portal fica em `http://localhost:5173`, o gateway em `http://localhost:8080` e o health check em `/actuator/health`.

Para desenvolvimento do frontend: inicie a infraestrutura e os serviços, depois execute `cd frontend && npm run dev`. O Vite encaminha `/api` exclusivamente para o gateway.

Monitoramento: o Compose também sobe o Prometheus em `http://localhost:9090` (alvos em `/targets`, alertas em `/alerts`) e o Grafana em `http://localhost:3001` (`admin` / `distrischool`), já com o dashboard **DistriSchool — Visão geral** (throughput, latência p50/p95/p99, erros, réplicas, JVM, HikariCP, circuit breakers, outbox, lag do Kafka e notificações). Detalhes em [docs/MONITORAMENTO.md](docs/MONITORAMENTO.md).

Login: `coordenacao@distrischool.com` / `123456` (administrador criado pela migration do `usuarios-service`). O login devolve um JWT que o API Gateway exige em todas as rotas `/api`; perfis e permissões estão em [docs/AUTENTICACAO.md](docs/AUTENTICACAO.md).

## Kubernetes

Use `kubectl apply -k k8s/overlays/minikube`. O Ingress expõe o API Gateway para `/api`, `/administrativa`, `/escolar`, `/usuarios` e `/notificacao`; os serviços de negócio permanecem internos. A única exceção é o WebSocket das notificações (`/ws`), que vai direto para o `notificacao-service` porque o gateway não faz proxy de WebSocket ([detalhes](docs/AUTENTICACAO.md)).
