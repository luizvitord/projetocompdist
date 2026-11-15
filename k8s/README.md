# Kubernetes — DistriSchool

Manifests para implantar o `gestao-administrativa-service`, o `gestao-escolar-service`, o
`usuarios-service`, o `notificacao-service`, o `api-gateway`, a infraestrutura deles (um PostgreSQL
por serviço e o Kafka) e o monitoramento (Prometheus e Grafana) no Kubernetes. Os arquivos são organizados com
[Kustomize](https://kustomize.io/), que já vem embutido no `kubectl`:

```
k8s/
├── base/                        # comum a todos os ambientes
│   ├── namespace.yaml           # namespace "distrischool"
│   ├── ingress.yaml             # entrada externa: /api/... (gateway) e /ws (WebSocket)
│   ├── postgres-administrativa/ # StatefulSet + Service (volume persistente)
│   ├── postgres-escolar/        # StatefulSet + Service (volume persistente)
│   ├── postgres-usuarios/       # StatefulSet + Service (volume persistente)
│   ├── postgres-notificacao/    # StatefulSet + Service (volume persistente)
│   ├── kafka/                   # StatefulSet + Service (KRaft, 1 nó)
│   ├── gestao-administrativa-service/ # Deployment + Service + HPA + PDB
│   ├── gestao-escolar-service/  # Deployment + Service + HPA + PDB
│   ├── usuarios-service/        # Deployment + Service + HPA + PDB (login e JWT)
│   ├── notificacao-service/     # Deployment + Service + HPA + PDB (notificações e WebSocket)
│   ├── api-gateway/             # Deployment + Service + HPA + PDB (valida o JWT)
│   └── monitoring/
│       ├── prometheus/          # Deployment + Service + RBAC; prometheus.yml e alertas.yml
│       └── grafana/             # Deployment + Service; datasource e dashboards (também usados pelo Compose)
└── overlays/
    └── minikube/                # desenvolvimento local: imagens locais + Secrets de dev
```

Novos serviços (relatórios...) entram como novas pastas em
`base/`, e um ambiente de nuvem (EKS/AKS) entra como um novo overlay.

## O que cada recurso demonstra

| Requisito do projeto | Como é atendido |
|---|---|
| Containers (Docker) | `Dockerfile` multi-stage em cada serviço, imagem só com JRE e usuário sem root |
| Orquestração (Kubernetes) | Deployment, StatefulSets, Services e Ingress no namespace `distrischool` |
| Escalabilidade horizontal | 2 réplicas iniciais por serviço + `HorizontalPodAutoscaler` (2 a 5 pods, CPU em 70%) |
| Health checks (Actuator) | `startupProbe`/`livenessProbe` em `/actuator/health/liveness`, `readinessProbe` em `/actuator/health/readiness` (inclui o banco) |
| Tolerância a falhas | Pods com falha são recriados; rolling update sem indisponibilidade (`maxUnavailable: 0`); desligamento gracioso; o `gestao-escolar-service` mantém uma réplica local das turmas e usa retry + circuit breaker (Resilience4j) no fallback síncrono |
| Configuração de ambientes | ConfigMaps (`gestao-administrativa-config`, `gestao-escolar-config`, `usuarios-config`, `notificacao-config`, `api-gateway-config`) e Secrets (credenciais de cada Postgres e `jwt-secret`) |
| Autenticação (JWT) | `usuarios-service` emite o token no login; o `api-gateway` valida e aplica as regras por perfil. O `notificacao-service` também valida, para as notificações e o WebSocket. Os três leem o mesmo Secret `jwt-secret` ([detalhes](../docs/AUTENTICACAO.md)) |
| Comunicação (Kafka + Spring Cloud Stream, WebSocket) | `notificacao-service` consome os eventos e entrega as notificações em tempo real; com várias réplicas, o tópico interno `notificacao.criada` leva o aviso à réplica em que o usuário está conectado ([detalhes](../notificacao-service/README.md)) |
| Monitoramento (Prometheus + Grafana) | O Prometheus descobre os pods anotados com `prometheus.io/*` no namespace (ServiceAccount + Role, sem ClusterRole), avalia as mesmas regras de alerta do Compose e alimenta o dashboard do Grafana, provisionado por ConfigMaps ([detalhes](../docs/MONITORAMENTO.md)) |
| Logs centralizados (preparação) | Logs em JSON (formato ECS), prontos para o ELK |

## Pré-requisitos

- Docker Desktop
- [minikube](https://minikube.sigs.k8s.io/docs/start/)
- `kubectl`

## Subindo no minikube

Os comandos abaixo valem para PowerShell e para bash, e devem ser executados na raiz do repositório.

```sh
# 1. Cria o cluster (4 Postgres + Kafka + 2 réplicas de cada serviço + monitoramento precisam
#    de ~6 GB; com 8 GB sobra espaço para o HPA criar réplicas no teste de carga)
minikube start --driver=docker --cpus=4 --memory=8g

# 2. Habilita o Ingress e o metrics-server (necessário para o HPA)
minikube addons enable ingress
minikube addons enable metrics-server

# 3. Constrói as imagens direto dentro do minikube (não precisa de registry)
minikube image build -t gestao-administrativa-service:dev gestao-administrativa-service
minikube image build -t gestao-escolar-service:dev gestao-escolar-service
minikube image build -t usuarios-service:dev usuarios-service
minikube image build -t notificacao-service:dev notificacao-service
minikube image build -t api-gateway:dev2 api-gateway

# 4. Aplica todos os manifests
kubectl apply -k k8s/overlays/minikube

# 5. Acompanha a subida (Ctrl+C para sair)
kubectl get pods -n distrischool -w
```

Quando os pods dos serviços estiverem `1/1 Running`, o Flyway já criou as tabelas de cada banco.

> **Memória.** Use `--memory=8g` quando a máquina permitir: o Docker Desktop precisa ter mais de
> 8 GB disponíveis (Settings → Resources, ou `memory=` no `.wslconfig` com o backend WSL 2). Com
> menos, use `--memory=6g`: tudo sobe, mas o HPA tem pouco espaço para réplicas extras. O Docker
> Compose e o minikube juntos não cabem em 8 GB; pare um (`docker compose stop`) antes de subir o
> outro.

> Se já existir um cluster minikube, o `minikube start` ignora `--cpus` e `--memory` e mantém
> os recursos antigos. Com 2 CPUs o ambiente sobe, mas o HPA não tem espaço para criar
> réplicas extras. Para recriar o cluster com mais recursos: `minikube delete` e depois o passo 1.

## Acessando a API

**Opção 1: Ingress.** Em outro terminal, deixe rodando:

```sh
minikube tunnel
```

A API fica disponível em:

- `gestao-administrativa-service`: `http://127.0.0.1/api/professores`, `/api/turmas`,
  `/api/disciplinas` e `/api/tecnicos`; Swagger em `http://127.0.0.1/administrativa/swagger-ui.html`
- `gestao-escolar-service`: `http://127.0.0.1/api/alunos` (inclui `/api/alunos/{id}/boletim`),
  `/api/matriculas`, `/api/frequencias`, `/api/avaliacoes` e `/api/notas`; Swagger em
  `http://127.0.0.1/escolar/swagger-ui.html`
- `usuarios-service`: `http://127.0.0.1/api/auth/login` e `/api/usuarios`; Swagger em
  `http://127.0.0.1/usuarios/swagger-ui.html`
- `notificacao-service`: `http://127.0.0.1/api/notificacoes`; Swagger em
  `http://127.0.0.1/notificacao/swagger-ui.html`; WebSocket (STOMP) em `ws://127.0.0.1/ws`

Tudo passa pelo `api-gateway`, que exige o token do login (`Authorization: Bearer ...`) em todas as
rotas `/api`, exceto o próprio login. Veja os exemplos com `curl` em [docs/AUTENTICACAO.md](../docs/AUTENTICACAO.md).

A única rota que não passa pelo gateway é o `/ws`: o Spring Cloud Gateway Server MVC não faz proxy
de WebSocket, então o Ingress `distrischool-ws` (com timeouts de leitura e envio de 3600 s) aponta
direto para o `notificacao-service`, que valida o token no frame `CONNECT`.

**Opção 2: port-forward** (direto no Service, sem Ingress):

```sh
kubectl port-forward -n distrischool svc/gestao-administrativa-service 8081:8081
kubectl port-forward -n distrischool svc/gestao-escolar-service 8082:8082
```

As APIs ficam em `http://localhost:8081/api/...` e `http://localhost:8082/api/...`, e o Actuator
em `/actuator/health` de cada porta.

## Monitoramento

O Prometheus e o Grafana não ficam no Ingress; o acesso é por port-forward (um terminal para cada):

```sh
kubectl port-forward -n distrischool svc/prometheus 9090
kubectl port-forward -n distrischool svc/grafana 3001:3000
```

- Prometheus: `http://localhost:9090/targets` (um alvo por pod pronto, todos `UP`) e
  `http://localhost:9090/alerts`
- Grafana: `http://localhost:3001`, usuário `admin` e senha `distrischool` (Secret `grafana-admin`
  do overlay). O dashboard **DistriSchool — Visão geral** abre na página inicial.

Como funciona:

- O Prometheus descobre os pods do namespace `distrischool` (`kubernetes_sd_configs`, `role: pod`)
  e coleta os que têm `prometheus.io/scrape: "true"`, na porta e no caminho das anotações. Só entram
  os pods prontos: uma réplica nova ainda subindo a JVM não aparece como fora do ar. O rótulo
  `servico` vem de `app.kubernetes.io/name`, e `pod`/`instance` são o nome do pod.
- A permissão é um Role no namespace (ler pods), ligado à ServiceAccount `prometheus`; não há
  ClusterRole.
- As regras de alerta (`base/monitoring/prometheus/alertas.yml`) e os arquivos do Grafana
  (`base/monitoring/grafana/`) são os mesmos que o Docker Compose monta. Os ConfigMaps são gerados
  pelo Kustomize com sufixo de hash: depois de editar um desses arquivos, `kubectl apply -k` recria
  o pod com a configuração nova.
- Recursos pequenos (requests de 50m de CPU; 192 Mi para o Prometheus e 128 Mi para o Grafana),
  dados em `emptyDir` e retenção de 6 horas: o histórico recomeça se o pod for recriado.

## Roteiro de demonstração

**Autorrecuperação (simulação de falha).** Apague um pod e veja o Kubernetes criar outro no lugar:

```sh
kubectl get pods -n distrischool -l app.kubernetes.io/name=gestao-administrativa-service
kubectl delete pod -n distrischool <nome-do-pod>
kubectl get pods -n distrischool -w
```

Durante a recriação, a outra réplica continua atendendo as requisições.

**Escalabilidade.** Abra o Grafana (port-forward acima), acompanhe o HPA e gere carga com o
perfil HPA do JMeter (`.\load-tests\executar.ps1 -Perfil hpa -Alvo minikube`; roteiro completo em
[load-tests/README.md](../load-tests/README.md#roteiro-de-demonstração-minikube)) para ver o número
de réplicas subir:

```sh
kubectl get hpa -n distrischool -w
```

No dashboard, o painel **Réplicas ativas** acompanha o HPA, e os painéis de throughput, latência e
CPU mostram o efeito das réplicas novas. Apagar um pod no meio do teste (roteiro de
autorrecuperação acima) aparece como uma queda temporária em **Réplicas ativas**, sem erros 5xx.

Logo depois de habilitar o metrics-server, o HPA mostra `cpu: <unknown>` por cerca de um minuto,
até a primeira coleta de métricas.

Para escalar manualmente: `kubectl scale deployment gestao-administrativa-service -n distrischool --replicas=4`
(o HPA volta a ajustar o número de réplicas depois).

**Rolling update.** Depois de alterar o código (troque pelo nome do serviço alterado):

```sh
minikube image build -t gestao-escolar-service:dev gestao-escolar-service
kubectl rollout restart deployment gestao-escolar-service -n distrischool
kubectl rollout status deployment gestao-escolar-service -n distrischool
```

**Eventos no Kafka.** Depois de cadastrar um técnico, o evento aparece no tópico `staff.created`:

```sh
kubectl exec -n distrischool kafka-0 -- /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic staff.created --from-beginning --timeout-ms 5000
```

O `gestao-escolar-service` publica `student.created`, `enrollment.created`, `enrollment.updated`,
`attendance.recorded` e `grade.posted` (troque o `--topic`), e consome o `schedule.updated`;
eventos que falham 3 vezes vão para `schedule.updated.DLT`.

O `notificacao-service` consome todos esses tópicos no grupo `notificacao-service` (o
`schedule.updated` é lido pelos dois serviços, cada um no seu grupo) e usa `<tópico>.DLT` para as
falhas. Para ver os grupos e o lag de cada um:

```sh
kubectl exec -n distrischool kafka-0 -- /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 --describe --group notificacao-service
```

**Réplica de turmas e circuit breaker.** O `gestao-escolar-service` guarda uma cópia local das
turmas (alimentada pelo `schedule.updated`), então matrículas, chamadas e notas de turmas já
conhecidas continuam funcionando com o `gestao-administrativa-service` fora do ar:

```sh
kubectl scale deployment gestao-administrativa-service -n distrischool --replicas=0
# Turma já replicada: 201. Turma desconhecida: 503 (ProblemDetail); depois de algumas falhas
# o circuit breaker abre e o 503 passa a ser imediato
kubectl scale deployment gestao-administrativa-service -n distrischool --replicas=2
```

Se o banco do `gestao-escolar-service` for recriado (por exemplo, com o PVC apagado), a réplica é
reconstruída relendo o tópico desde o início. Com os pods do serviço parados
(`kubectl scale deployment gestao-escolar-service -n distrischool --replicas=0`), rode:

```sh
kubectl exec -n distrischool kafka-0 -- /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 --group gestao-escolar-service \
  --reset-offsets --to-earliest --topic schedule.updated --execute
```

Sem isso, as turmas voltam aos poucos pelo fallback síncrono, conforme forem usadas.

No Git Bash, rode `export MSYS_NO_PATHCONV=1` antes; caso contrário, o caminho `/opt/kafka/...`
é convertido para um caminho do Windows.

**Logs e eventos do cluster:**

```sh
kubectl logs -n distrischool -l app.kubernetes.io/name=gestao-escolar-service -f
kubectl get events -n distrischool --sort-by=.lastTimestamp
```

## Removendo

```sh
kubectl delete -k k8s/overlays/minikube
```

Os volumes do Postgres e do Kafka (PVCs) não são apagados junto com os StatefulSets. Para
apagar também os dados, use `kubectl delete pvc -n distrischool --all`, ou `minikube delete`
para remover o cluster inteiro.

## Limitações do ambiente de desenvolvimento

- O Kafka roda com um único nó, então os tópicos têm fator de replicação 1. Em produção seriam
  3 brokers (por exemplo, com o operador Strimzi ou um serviço gerenciado como o Amazon MSK).
- Cada PostgreSQL roda com uma única instância no cluster. Em produção, o recomendado é um banco
  gerenciado (Amazon RDS ou Azure Database for PostgreSQL).
- As credenciais do overlay `minikube` são apenas para desenvolvimento.
