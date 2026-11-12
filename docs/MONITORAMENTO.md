# Monitoramento

Todos os serviços Java expõem métricas em `/actuator/prometheus` (Micrometer). O rótulo que
identifica o serviço (`servico`) é acrescentado pelo Prometheus na coleta, não pela aplicação.

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
| `kafka_consumer_fetch_manager_records_lag_max` | `client_id` (`consumer-<grupo>-<n>`) | escolar e notificação |
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

## Observação sobre o gateway

O `api-gateway` não tinha o `micrometer-registry-prometheus`: o endpoint `/actuator/prometheus` não
existia e a coleta recebia `401`. A dependência foi adicionada e há um teste que confere o endpoint.
