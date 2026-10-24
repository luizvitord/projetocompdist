# Kubernetes — DistriSchool

Manifests para implantar o `gestao-administrativa-service` e sua infraestrutura (PostgreSQL e Kafka)
no Kubernetes. Os arquivos são organizados com [Kustomize](https://kustomize.io/), que já vem
embutido no `kubectl`:

```
k8s/
├── base/                        # comum a todos os ambientes
│   ├── namespace.yaml           # namespace "distrischool"
│   ├── ingress.yaml             # entrada externa (/api/...)
│   ├── postgres-administrativa/ # StatefulSet + Service (volume persistente)
│   ├── kafka/                   # StatefulSet + Service (KRaft, 1 nó)
│   └── gestao-administrativa-service/ # Deployment + Service + HPA
└── overlays/
    └── minikube/                # desenvolvimento local: imagem local + Secret de dev
```

Novos serviços (usuários, API Gateway, notificações...) entram como novas pastas em
`base/`, e um ambiente de nuvem (EKS/AKS) entra como um novo overlay.

## O que cada recurso demonstra

| Requisito do projeto | Como é atendido |
|---|---|
| Containers (Docker) | `Dockerfile` multi-stage em `gestao-administrativa-service/`, imagem só com JRE e usuário sem root |
| Orquestração (Kubernetes) | Deployment, StatefulSets, Services e Ingress no namespace `distrischool` |
| Escalabilidade horizontal | 2 réplicas iniciais + `HorizontalPodAutoscaler` (2 a 5 pods, CPU em 70%) |
| Health checks (Actuator) | `startupProbe`/`livenessProbe` em `/actuator/health/liveness`, `readinessProbe` em `/actuator/health/readiness` (inclui o banco) |
| Tolerância a falhas | Pods com falha são recriados; rolling update sem indisponibilidade (`maxUnavailable: 0`); desligamento gracioso |
| Configuração de ambientes | ConfigMap (`gestao-administrativa-config`) e Secret (`postgres-administrativa-credentials`) |
| Monitoramento (preparação) | `/actuator/prometheus` exposto e anotações `prometheus.io/*` nos pods |
| Logs centralizados (preparação) | Logs em JSON (formato ECS), prontos para o ELK |

## Pré-requisitos

- Docker Desktop
- [minikube](https://minikube.sigs.k8s.io/docs/start/)
- `kubectl`

## Subindo no minikube

Os comandos abaixo valem para PowerShell e para bash, e devem ser executados na raiz do repositório.

```sh
# 1. Cria o cluster (Postgres + Kafka + 2 réplicas do serviço precisam de ~4 GB)
minikube start --driver=docker --cpus=4 --memory=6g

# 2. Habilita o Ingress e o metrics-server (necessário para o HPA)
minikube addons enable ingress
minikube addons enable metrics-server

# 3. Constrói a imagem direto dentro do minikube (não precisa de registry)
minikube image build -t gestao-administrativa-service:dev gestao-administrativa-service

# 4. Aplica todos os manifests
kubectl apply -k k8s/overlays/minikube

# 5. Acompanha a subida (Ctrl+C para sair)
kubectl get pods -n distrischool -w
```

Quando os pods do `gestao-administrativa-service` estiverem `1/1 Running`, o Flyway já criou as tabelas.

> Se já existir um cluster minikube, o `minikube start` ignora `--cpus` e `--memory` e mantém
> os recursos antigos. Com 2 CPUs o ambiente sobe, mas o HPA não tem espaço para criar
> réplicas extras. Para recriar o cluster com mais recursos: `minikube delete` e depois o passo 1.

## Acessando a API

**Opção 1: Ingress.** Em outro terminal, deixe rodando:

```sh
minikube tunnel
```

A API fica disponível em `http://127.0.0.1/api/professores`, `/api/turmas`, `/api/disciplinas`
e `/api/tecnicos`. A documentação interativa (Swagger) fica em
`http://127.0.0.1/administrativa/swagger-ui.html`.

**Opção 2: port-forward** (direto no Service, sem Ingress):

```sh
kubectl port-forward -n distrischool svc/gestao-administrativa-service 8081:8081
```

A API fica em `http://localhost:8081/api/...` e o Actuator em `http://localhost:8081/actuator/health`.

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

**Rolling update.** Depois de alterar o código:

```sh
minikube image build -t gestao-administrativa-service:dev gestao-administrativa-service
kubectl rollout restart deployment gestao-administrativa-service -n distrischool
kubectl rollout status deployment gestao-administrativa-service -n distrischool
```

**Eventos no Kafka.** Depois de cadastrar um técnico, o evento aparece no tópico `staff.created`:

```sh
kubectl exec -n distrischool kafka-0 -- /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic staff.created --from-beginning --timeout-ms 5000
```

No Git Bash, rode `export MSYS_NO_PATHCONV=1` antes; caso contrário, o caminho `/opt/kafka/...`
é convertido para um caminho do Windows.

**Logs e eventos do cluster:**

```sh
kubectl logs -n distrischool -l app.kubernetes.io/name=gestao-administrativa-service -f
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
- O PostgreSQL roda com uma única instância no cluster. Em produção, o recomendado é um banco
  gerenciado (Amazon RDS ou Azure Database for PostgreSQL).
- As credenciais do overlay `minikube` são apenas para desenvolvimento.
