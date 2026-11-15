# Monitoramento

Todos os serviços Java expõem métricas em `/actuator/prometheus` (Micrometer). O Prometheus coleta
esses endpoints a cada 5 s, avalia as regras de alerta e o Grafana mostra o dashboard
**DistriSchool — Visão geral**. O rótulo que identifica o serviço (`servico`) é acrescentado pelo
Prometheus na coleta, não pela aplicação.

## Acesso (Docker Compose)

| | Endereço | Observação |
|---|---|---|
| Prometheus | `http://localhost:9090` | alvos em `/targets`, alertas em `/alerts`; retenção de 2 dias |
| Grafana | `http://localhost:3001` | `admin` / `distrischool` (só desenvolvimento); o dashboard abre na página inicial |

Arquivos:

| Arquivo | Conteúdo |
|---|---|
| `monitoring/prometheus/prometheus.yml` | alvos estáticos (um container por serviço) e o rótulo `servico` de cada um |
| `k8s/base/monitoring/prometheus/alertas.yml` | regras de alerta (as mesmas no Compose e no Kubernetes) |
| `k8s/base/monitoring/grafana/datasources.yml` | datasource Prometheus (`http://prometheus:9090`, uid `prometheus`) |
| `k8s/base/monitoring/grafana/dashboards.yml` | provider que carrega os dashboards de `/var/lib/grafana/dashboards` |
| `k8s/base/monitoring/grafana/distrischool-visao-geral.json` | o dashboard |

As regras de alerta e os arquivos do Grafana ficam em `k8s/base/monitoring/` porque o Kustomize só
lê arquivos dentro da pasta da kustomization; o Compose monta esses mesmos arquivos. A URL do datasource é a mesma
nos dois ambientes (o Service do Kubernetes também se chama `prometheus`). Nada é configurado pela
interface: para mudar um painel, edite o JSON e reinicie o Grafana (ou espere 30 s, o intervalo de
releitura do provider).

## Métricas expostas

Os nomes abaixo foram conferidos na saída real de `/actuator/prometheus` de cada serviço rodando
no Docker Compose. Use sempre esses nomes nas consultas (PromQL); não invente outros.

| Métrica | Rótulos principais | Serviços |
|---|---|---|
| `http_server_requests_seconds_bucket` / `_count` / `_sum` | `method`, `uri`, `status`, `outcome` | todos |
| `jvm_memory_used_bytes`, `jvm_memory_max_bytes` | `area` (`heap`), `id` | todos |
| `process_cpu_usage` | — | todos |
| `hikaricp_connections_active`, `_pending`, `_max` | `pool` | administrativa, escolar, usuários, notificação |
| `resilience4j_circuitbreaker_state` (1 no estado atual) | `name`, `state` | gateway (`gestao-escolar`, `gestao-administrativa`, `usuarios`, `notificacao`) e escolar (`administrativa`) |
| `resilience4j_circuitbreaker_calls_seconds_count`, `resilience4j_circuitbreaker_not_permitted_calls_total` | `name`, `kind` | gateway e escolar |
| `resilience4j_retry_calls_total` | `name`, `kind` | escolar |
| `outbox_eventos_pendentes` | — | administrativa e escolar |
| `kafka_consumer_fetch_manager_records_lag` (lag atual por partição) | `client_id` (`consumer-<grupo>-<n>`), `topic`, `partition` | escolar e notificação |
| `spring_cloud_stream_binder_kafka_offset` (lag por grupo e tópico) | `group`, `topic` | notificação |
| `notificacoes_criadas_total` | `tipo` | notificação |
| `notificacoes_enviadas_total` | `canal` (`websocket`) | notificação |
| `websocket_sessoes_ativas` | — | notificação |

No gateway, o `uri` das requisições é o padrão da rota (`/api/alunos/**`); nos serviços, é o
caminho do controller (`/api/alunos/{id}`).

## Percentis de latência

`management.metrics.distribution.percentiles-histogram.http.server.requests=true` faz cada serviço
publicar o histograma da latência HTTP (`http_server_requests_seconds_bucket`, entre 5 ms e 10 s).
Com os buckets, o percentil é calculado no Prometheus, somando todas as réplicas:

```promql
histogram_quantile(0.95, sum by (le, servico) (rate(http_server_requests_seconds_bucket[1m])))
```

Um percentil calculado dentro de cada réplica não poderia ser combinado com o das outras.

## Kubernetes

O Prometheus (`k8s/base/monitoring/prometheus/prometheus.yml`) descobre os pods do namespace
`distrischool` pela API do Kubernetes e coleta os pods prontos que têm a anotação
`prometheus.io/scrape: "true"`. O rótulo `servico` vem de `app.kubernetes.io/name`; `pod` e
`instance` são o nome do pod. O acesso é por port-forward (`svc/prometheus 9090`,
`svc/grafana 3001:3000`); veja o [k8s/README.md](../k8s/README.md#monitoramento).

## Dashboard "DistriSchool — Visão geral"

Variável `servico` (múltipla, padrão: todos), intervalo padrão de 15 min, atualização a cada 5 s.
As taxas usam `$__rate_interval` (no mínimo 4 × o intervalo de coleta, 20 s). As requisições para
`/actuator/*` (coleta do Prometheus e probes) ficam fora dos painéis e alertas de HTTP.

| Painel | O que mostra | Consulta (resumida) |
|---|---|---|
| Throughput por serviço | req/s somando todas as réplicas | `sum by (servico) (rate(http_server_requests_seconds_count[..]))` |
| Throughput por rota | req/s por `servico` e `uri` | `sum by (servico, uri) (...)` |
| Latência p50 / p95 / p99 | percentis da latência HTTP de cada serviço | `histogram_quantile(q, sum by (le, servico) (rate(..._bucket[..])))` |
| Taxa de erros (4xx e 5xx) | % das requisições com cada classe de status; 0% quando não há erros | `100 * (sum(rate(..{status=~"5.."})) or 0 * sum(rate(..))) / sum(rate(..))` |
| Réplicas ativas | alvos respondendo à coleta; mostra o HPA escalando | `count by (servico) (up == 1)` |
| CPU do processo | uso de CPU de cada JVM | `process_cpu_usage` |
| Heap da JVM | heap usada por instância | `sum by (servico, instance) (jvm_memory_used_bytes{area="heap"})` |
| Conexões HikariCP | conexões em uso e threads esperando conexão | `hikaricp_connections_active`, `_pending` |
| Circuit breakers | estado de cada circuit breaker ao longo do tempo (Fechado / Meio aberto / Aberto) | `half_open + 2 × (open ou forced_open)` sobre `resilience4j_circuitbreaker_state` |
| Eventos pendentes na outbox | eventos ainda não publicados no Kafka | `max by (servico) (outbox_eventos_pendentes)` |
| Lag dos consumidores Kafka | mensagens ainda não consumidas, por grupo | `spring_cloud_stream_binder_kafka_offset` (notificação) e `sum(kafka_consumer_fetch_manager_records_lag)` (escolar) |
| Notificações por minuto | notificações criadas por tipo e entregues por WebSocket | `60 * rate(notificacoes_criadas_total[..])`, `notificacoes_enviadas_total` |
| Sessões WebSocket | usuários conectados agora | `sum(websocket_sessoes_ativas)` |

Gauges que todas as réplicas leem da mesma fonte (tabela da outbox, lag do grupo) usam `max`, e não
`sum`, para não multiplicar o valor pelo número de réplicas.

Sobre o lag do Kafka:

- No `notificacao-service`, o binder do Spring Cloud Stream calcula o lag de cada grupo e tópico
  (`spring_cloud_stream_binder_kafka_offset`), inclusive dos bindings que ainda não receberam
  partições. As métricas do cliente Kafka só aparecem para os consumidores que já buscaram dados.
- No `gestao-escolar-service` (Spring Kafka, sem o binder), o lag vem do cliente:
  `kafka_consumer_fetch_manager_records_lag`, uma série por partição. Cada partição pertence a uma
  réplica, então a soma é o lag do grupo; o grupo é extraído do `client_id`.
- O cliente publica cada tópico duas vezes (`schedule.updated` e `schedule_updated`); as consultas
  só usam a forma com ponto.
- `kafka_consumer_fetch_manager_records_lag_max` não é usado: depois que o consumidor reinicia, a
  série vira `NaN` e a versão sem rótulo `topic` desaparece. O circuit breaker também usa `max`:
cada réplica tem o seu, e o painel mostra o pior estado entre elas.

## Alertas

Regras em `k8s/base/monitoring/prometheus/alertas.yml`, visíveis em `http://localhost:9090/alerts`. Não há
Alertmanager: os alertas só aparecem no Prometheus (e podem ser consultados como a série `ALERTS`).

| Alerta | Condição | Duração |
|---|---|---|
| `ServicoFora` | `up == 0`, ou nenhum alvo do serviço (`absent(up{servico="..."})`) | 1 min |
| `LatenciaP95Alta` | p95 do serviço acima de 1 s | 5 min |
| `TaxaErros5xxAlta` | mais de 5% das requisições com 5xx | 5 min |
| `CircuitBreakerAberto` | algum circuit breaker em `open` ou `forced_open` | imediato |
| `OutboxAcumulada` | mais de 100 eventos pendentes na outbox | 5 min |
| `LagConsumidorAlto` | mais de 1000 mensagens atrasadas num grupo de consumidores | 5 min |

O `absent(...)` cobre o Kubernetes: como só os pods prontos são coletados, um Deployment sem nenhuma
réplica pronta some da lista de alvos em vez de aparecer com `up == 0`. No Compose, o container
parado continua sendo alvo e o alerta vem do `up == 0`.

Para ver um alerta disparar: `docker compose stop usuarios-service` faz `ServicoFora` passar de
*pending* para *firing* depois de 1 minuto.

## Observação sobre o gateway

O `api-gateway` não tinha o `micrometer-registry-prometheus`: o endpoint `/actuator/prometheus` não
existia e a coleta recebia `401`. A dependência foi adicionada e há um teste que confere o endpoint.
