# Kubernetes — DistriSchool

Manifests para implantar o `gestao-administrativa-service`, o `gestao-escolar-service` e a
infraestrutura deles (um PostgreSQL por serviço e o Kafka) no Kubernetes. Os arquivos são organizados com [Kustomize](https://kustomize.io/), que já vem
embutido no `kubectl`:

```
k8s/
├── base/                        # comum a todos os ambientes
│   ├── namespace.yaml           # namespace "distrischool"
│   ├── ingress.yaml             # entrada externa (/api/...)
│   ├── postgres-administrativa/ # StatefulSet + Service (volume persistente)
│   ├── postgres-escolar/        # StatefulSet + Service (volume persistente)
│   ├── kafka/                   # StatefulSet + Service (KRaft, 1 nó)
│   ├── gestao-administrativa-service/ # Deployment + Service + HPA
│   └── gestao-escolar-service/  # Deployment + Service + HPA
└── overlays/
    └── minikube/                # desenvolvimento local: imagens locais + Secrets de dev
```

Novos serviços (usuários, API Gateway, notificações...) entram como novas pastas em
`base/`, e um ambiente de nuvem (EKS/AKS) entra como um novo overlay.

## O que cada recurso demonstra

| Requisito do projeto | Como é atendido |
|---|---|
| Containers (Docker) | `Dockerfile` multi-stage em cada serviço, imagem só com JRE e usuário sem root |
| Orquestração (Kubernetes) | Deployment, StatefulSets, Services e Ingress no namespace `distrischool` |
| Escalabilidade horizontal | 2 réplicas iniciais por serviço + `HorizontalPodAutoscaler` (2 a 5 pods, CPU em 70%) |
| Health checks (Actuator) | `startupProbe`/`livenessProbe` em `/actuator/health/liveness`, `readinessProbe` em `/actuator/health/readiness` (inclui o banco) |
| Tolerância a falhas | Pods com falha são recriados; rolling update sem indisponibilidade (`maxUnavailable: 0`); desligamento gracioso; o `gestao-escolar-service` mantém uma réplica local das turmas e usa retry + circuit breaker (Resilience4j) no fallback síncrono |
| Configuração de ambientes | ConfigMaps (`gestao-administrativa-config`, `gestao-escolar-config`) e Secrets (`postgres-administrativa-credentials`, `postgres-escolar-credentials`) |
| Monitoramento (preparação) | `/actuator/prometheus` exposto e anotações `prometheus.io/*` nos pods |
| Logs centralizados (preparação) | Logs em JSON (formato ECS), prontos para o ELK |

## Pré-requisitos

- Docker Desktop
- [minikube](https://minikube.sigs.k8s.io/docs/start/)
- `kubectl`

## Subindo no minikube

Os comandos abaixo valem para PowerShell e para bash, e devem ser executados na raiz do repositório.

```sh
# 1. Cria o cluster (2 Postgres + Kafka + 2 réplicas de cada serviço precisam de ~5 GB)
minikube start --driver=docker --cpus=4 --memory=6g

# 2. Habilita o Ingress e o metrics-server (necessário para o HPA)
minikube addons enable ingress
minikube addons enable metrics-server

# 3. Constrói as imagens direto dentro do minikube (não precisa de registry)
minikube image build -t gestao-administrativa-service:dev gestao-administrativa-service
minikube image build -t gestao-escolar-service:dev gestao-escolar-service

# 4. Aplica todos os manifests
kubectl apply -k k8s/overlays/minikube

# 5. Acompanha a subida (Ctrl+C para sair)
kubectl get pods -n distrischool -w
```

Quando os pods dos dois serviços estiverem `1/1 Running`, o Flyway já criou as tabelas de cada banco.

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

**Opção 2: port-forward** (direto no Service, sem Ingress):

```sh
kubectl port-forward -n distrischool svc/gestao-administrativa-service 8081:8081
kubectl port-forward -n distrischool svc/gestao-escolar-service 8082:8082
```

As APIs ficam em `http://localhost:8081/api/...` e `http://localhost:8082/api/...`, e o Actuator
em `/actuator/health` de cada porta.

## Roteiro de demonstração

**Autorrecuperação (simulação de falha).** Apague um pod e veja o Kubernetes criar outro no lugar:

```sh
kubectl get pods -n distrischool -l app.kubernetes.io/name=gestao-administrativa-service
kubectl delete pod -n distrischool <nome-do-pod>
kubectl get pods -n distrischool -w
```

Durante a recriação, a outra réplica continua atendendo as requisições.

**Escalabilidade.** Acompanhe o HPA e gere carga (por exemplo, com JMeter) para ver o número
de réplicas subir:

```sh
kubectl get hpa -n distrischool -w
```

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
