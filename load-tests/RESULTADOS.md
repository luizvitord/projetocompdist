# Resultados dos testes de carga

Registre aqui cada execução de `executar.ps1`. Os números vêm do relatório HTML do JMeter
(`relatorio/index.html`, tabela **Statistics**, linha **Total**); as réplicas, do painel
**Réplicas ativas** do Grafana (ou de `kubectl get hpa -n distrischool -w`). Só registre execuções
reais.

| Campo | Onde encontrar |
|---|---|
| Throughput | coluna `Throughput` (req/s) da linha Total |
| p95 / p99 | colunas `95th pct` e `99th pct` (ms) da linha Total |
| % de erros | coluna `Error %` da linha Total |
| Réplicas mín./máx. | menor e maior número de réplicas do serviço observado durante o teste |

## Execuções

| Data | Ambiente | Perfil | Usuários | Requisições | Throughput (req/s) | p95 (ms) | p99 (ms) | Erros (%) | Réplicas mín./máx. | Observações |
|---|---|---|---|---|---|---|---|---|---|---|
| 2026-11-15 | Docker Compose | fumaça | 5 | 443 | 7,1 | 22 | 74,6 | 0,00 | 1 / 1 (uma por serviço) | Média de 14 ms, p90 de 19 ms. Mistura observada: 71% leitura, 20% escrita, 10% cadastro. Relatório em `load-tests/resultados/20261115-144807-fumaca-compose/relatorio/index.html` (fora do Git, só na máquina em que rodou). |
