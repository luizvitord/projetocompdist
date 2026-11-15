# Testes de carga (JMeter)

O plano `distrischool.jmx` exercita o sistema pelo `api-gateway` com um cenário misto de leituras
e escritas. O `executar.ps1` roda o JMeter em modo não gráfico **pelo Docker** (imagem
`alpine/jmeter:5.6.3`, fixada também pelo digest), então não é preciso instalar o JMeter.

| Arquivo | Conteúdo |
|---|---|
| `distrischool.jmx` | plano de teste (abre no JMeter 5.6.3 para edição) |
| `executar.ps1` | roda um perfil contra o Docker Compose ou o minikube e gera o relatório |
| `RESULTADOS.md` | tabela para registrar cada execução |
| `resultados/` | saída das execuções (fora do Git) |

## Como o plano funciona

**setUp (uma vez, antes dos usuários).** Um único login como `ADMIN`. O gateway limita o login a 10
por minuto por IP, então os usuários virtuais nunca fazem login: todos reutilizam esse token, guardado
na propriedade `token` (`Authorization: Bearer ${__P(token)}`). Depois o setUp cria, pela API, a massa
de dados da execução:

- uma disciplina, um professor e uma turma do ano corrente com a disciplina ofertada;
- `alunosMassa` alunos (padrão 5), cada um matriculado na turma;
- uma primeira avaliação. Ela só é aceita depois que a oferta da disciplina chega à réplica local do
  `gestao-escolar-service` (evento `schedule.updated` pelo Kafka). Enquanto isso, a API responde 422 e
  o setUp tenta de novo a cada segundo, por até 30 s; essas tentativas não contam como erro.

Os códigos e emails levam um identificador da execução (`runId`, data e hora), então execuções
repetidas não colidem (nenhum 409). Qualquer falha no setUp encerra o teste.

**Usuários (cenário misto).** Cada usuário repete, até o fim da duração, uma ação sorteada nas
proporções abaixo (Throughput Controllers em modo percentual, compartilhados entre os usuários), com
uma pausa aleatória de 300 a 1000 ms antes de cada requisição:

| Proporção | Ação | Requisições |
|---|---|---|
| 70% | leitura (uma por vez, sorteada) | `GET /api/alunos` paginado, com filtro `nome` e com filtro `turmaId`; `GET /api/turmas`; `GET /api/turmas/{id}`; boletim e frequência de um aluno da massa |
| 20% | escrita que gera eventos | `POST /api/avaliacoes` e `POST /api/avaliacoes/{id}/notas` com as notas de todas as matrículas da massa (um `grade.posted`, que vira notificações para `ALUNO` e `RESPONSAVEL`) |
| 10% | cadastro | `POST /api/alunos` (um `student.created`, notificação para a coordenação) |

Toda resposta precisa ser 2xx (Response Assertion).

**Propriedades** (`-J`, todas com padrão): `host` (`localhost`), `porta` (`8080`), `protocolo`
(`http`), `usuarios` (5), `rampa` em segundos (10), `duracao` em segundos, contando a rampa (60),
`email` e `senha` do administrador e `alunosMassa` (5).

## Perfis

| Perfil | Usuários | Rampa | Duração total | Para quê |
|---|---|---|---|---|
| `fumaca` | 5 | 10 s | 1 min | conferir que tudo funciona, sem erros |
| `carga` | 50 | 2 min | 12 min (10 min com todos) | comportamento com carga normal |
| `estresse` | até 200 | 10 min | 12 min (2 min no pico) | achar o ponto em que latência e erros sobem |
| `hpa` | 120 | 1 min | 11 min (10 min de carga alta) | provocar o autoscaling no minikube |

Com 300 a 1000 ms de pausa, cada usuário faz em média 1,5 requisição por segundo: 50 usuários dão
cerca de 75 req/s, e 120 usuários, cerca de 180 req/s.

## Executando

Na raiz do repositório, no PowerShell:

```powershell
# Docker Compose (docker compose up -d antes)
.\load-tests\executar.ps1 -Perfil fumaca -Alvo compose
.\load-tests\executar.ps1 -Perfil carga -Alvo compose

# minikube (cluster no ar; o script abre o port-forward do svc/api-gateway na porta 8080 se ela
# estiver livre, e o fecha no fim)
.\load-tests\executar.ps1 -Perfil hpa -Alvo minikube
```

Parâmetros opcionais: `-Email`, `-Senha` e `-AlunosMassa`.

- **compose**: o container do JMeter entra na rede do Compose e chama `api-gateway:8080`.
- **minikube**: o JMeter chama `host.docker.internal:8080`, onde está o
  `kubectl port-forward -n distrischool svc/api-gateway 8080:8080`. A porta 8080 é a mesma do
  gateway do Compose: pare o Compose antes (`docker compose stop`; os dois também não cabem juntos na
  memória). O port-forward liga a uma única réplica do gateway; as réplicas dos serviços de trás
  continuam recebendo carga pelo Service do Kubernetes.

Cada execução cria `load-tests/resultados/<data-hora>-<perfil>-<alvo>/` com:

- `resultado.jtl`: uma linha por requisição (CSV);
- `jmeter.log`: log do JMeter;
- `relatorio/index.html`: relatório HTML do JMeter.

No fim, o script mostra o total de requisições, os erros e os rótulos que falharam.

## Roteiro de demonstração (minikube)

1. Suba o cluster (veja `k8s/README.md`) e abra os port-forwards do monitoramento, cada um num
   terminal:

   ```powershell
   kubectl port-forward -n distrischool svc/prometheus 9090
   kubectl port-forward -n distrischool svc/grafana 3001:3000
   ```

2. Abra o Grafana em `http://localhost:3001` (`admin` / `distrischool`), dashboard
   **DistriSchool — Visão geral**, com o intervalo "Last 15 minutes".
3. Em outro terminal, acompanhe o HPA:

   ```powershell
   kubectl get hpa -n distrischool -w
   ```

4. Rode o perfil HPA: `.\load-tests\executar.ps1 -Perfil hpa -Alvo minikube`.
5. No dashboard, acompanhe **Throughput por serviço** subir com a rampa, **Latência p50 / p95 / p99**
   e **CPU do processo** subirem, e **Réplicas ativas** aumentar quando o HPA passar de 70% de CPU
   (as réplicas novas aparecem depois de subir a JVM e passar no readiness). **Notificações por
   minuto** mostra os `grade.posted` virando notificações.
6. **Tolerância a falhas**: no meio do teste, apague um pod de um serviço:

   ```powershell
   kubectl get pods -n distrischool -l app.kubernetes.io/name=gestao-escolar-service
   kubectl delete pod -n distrischool <nome-do-pod>
   ```

   **Réplicas ativas** cai e volta quando o Kubernetes recria o pod; as outras réplicas continuam
   atendendo. Compare depois a % de erros do relatório do JMeter: o desligamento gracioso e o
   readiness devem manter os erros perto de zero.
7. No fim, abra o relatório HTML e registre a execução em `RESULTADOS.md`.

Depois do teste, o HPA reduz as réplicas devagar (janela de estabilização de 5 min).

## Lendo o relatório do JMeter

Em `relatorio/index.html`:

- **Statistics** (no Dashboard): uma linha por requisição e o total. As colunas que importam:
  - `Throughput`: requisições por segundo no período todo, incluindo a rampa;
  - `90th / 95th / 99th pct`: percentis do tempo de resposta (ms). O p95 diz que 95% das requisições
    terminaram nesse tempo ou menos;
  - `Error %`: requisições que falharam (status fora de 2xx ou timeout).
- **Errors** e **Top 5 Errors by sampler**: quais requisições falharam e com qual código.
- **Charts → Over Time**: tempo de resposta, throughput e usuários ativos ao longo do teste. Use para
  ver onde a latência começou a subir no perfil de estresse.

As linhas `setUp - ...` são da preparação e aparecem uma vez; o restante é a carga.

**Comparando com o Grafana.** Os números não batem exatamente, e isso é esperado:

- O JMeter mede do lado do cliente (rede e fila incluídas); o Grafana mede dentro de cada serviço
  (`http_server_requests_seconds`). A latência do JMeter fica um pouco acima da do `api-gateway` no
  Grafana, e a do gateway acima da dos serviços de trás.
- O throughput do JMeter deve ficar próximo do throughput do `api-gateway` no Grafana. Os serviços de
  trás somam mais ou menos o mesmo total, divididos por rota.
- O Grafana calcula os percentis a partir de buckets do histograma (5 ms a 10 s), então o valor é
  aproximado dentro do bucket; o JMeter calcula sobre todas as amostras.
- O relatório resume o teste inteiro; o Grafana mostra janelas de alguns segundos. Para comparar,
  olhe o Grafana no intervalo exato do teste.
