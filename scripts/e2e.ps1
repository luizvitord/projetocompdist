<#
.SYNOPSIS
    Teste de ponta a ponta do DistriSchool, pelo api-gateway.

.DESCRIPTION
    Roda contra o docker compose ja no ar (docker compose up -d --build, na raiz do projeto) e
    exercita as funcionalidades pelo api-gateway, como um cliente de verdade. As secoes ficam em
    scripts/e2e/*.ps1 (carregadas por dot-sourcing); este arquivo e o unico ponto de entrada.
    Cada secao cria os proprios dados, com um sufixo unico por execucao (data/hora), entao o script
    pode rodar quantas vezes for preciso, sem banco vazio e sem 409 por colisao.

    Secoes (ids para -Secoes), na ordem em que rodam:
      login            Login, 401 sem token / com token adulterado / senha errada
      perfis           Usuarios (CRUD) e a tabela de permissoes por perfil
      infra            Health e readiness, Swagger pelo gateway, Prometheus e Grafana
      administrativa   Disciplinas, professores, tecnicos e turmas; replica no gestao-escolar
      alunos           Alunos (CRUD, filtros, numero de matricula)
      matriculas       Transicoes de status, capacidade, transferencia
      frequencia       Chamadas, relatorio por turma e frequencia por aluno
      notas            Avaliacoes, lancamento e correcao de notas, historico
      boletim          Medias, recuperacao e situacao (CURSANDO, APROVADO, REPROVADO_*)
      fluxo            Fluxo funcional completo de um aluno
      notificacoes     Um tipo de notificacao por evento, destinatarios, WebSocket, idempotencia
      dlt              Dead Letter Topic do schedule.updated
      resiliencia-administrativa *  gestao-administrativa-service parado
      resiliencia-notificacao    *  notificacao-service parado
      outbox                     *  Kafka parado: eventos ficam na outbox
      circuitbreaker             *  gestao-escolar-service parado: 503 do gateway
      ratelimit        Rate limit do login (sempre por ultimo)
    (*) param containers; -PularResiliencia pula essas secoes.

    Cada verificacao imprime [OK], [FALHOU], [AVISO] (comportamento conhecido, nao e falha) ou
    [PULADO]. No fim ha um resumo por secao e o relatorio JUnit em scripts/e2e-resultado.xml.
    Termina com exit 1 se houver qualquer [FALHOU]. Em falha ou interrupcao (Ctrl+C), todo
    container que o script parou e ligado de novo.

    Compativel com o Windows PowerShell 5.1 e com o PowerShell 7 (Windows e Linux). Os arquivos sao
    mantidos so com ASCII: o 5.1 le scripts sem BOM na codificacao ANSI do sistema. Detalhes,
    tabela de cobertura e tempos em scripts/README.md.

.PARAMETER GatewayUrl
    Endereco do api-gateway. Padrao: http://localhost:8080

.PARAMETER Secoes
    Roda so estas secoes (ids acima, separados por virgula). Padrao: todas.

.PARAMETER PularResiliencia
    Pula as secoes que param containers (marcadas com * acima).

.PARAMETER WebSocketUrl
    WebSocket das notificacoes, pelo nginx do frontend. Padrao: ws://localhost:5173/ws

.PARAMETER PrometheusUrl
    Padrao: http://localhost:9090. Fora do ar, as verificacoes do Prometheus ficam [PULADO].

.PARAMETER GrafanaUrl
    Padrao: http://localhost:3001 (usuario e senha em -GrafanaUsuario / -GrafanaSenha).

.PARAMETER ResultadoXml
    Relatorio JUnit. Padrao: scripts/e2e-resultado.xml

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\e2e.ps1

.EXAMPLE
    pwsh ./scripts/e2e.ps1 -Secoes login,notificacoes -PularResiliencia
#>
[CmdletBinding()]
param(
    [string]$GatewayUrl = 'http://localhost:8080',
    [string[]]$Secoes = @(),
    [switch]$PularResiliencia,
    [string]$WebSocketUrl = 'ws://localhost:5173/ws',
    [string]$PrometheusUrl = 'http://localhost:9090',
    [string]$GrafanaUrl = 'http://localhost:3001',
    [string]$GrafanaUsuario = 'admin',
    [string]$GrafanaSenha = 'distrischool',
    [string]$ResultadoXml = ''
)

$ErrorActionPreference = 'Stop'
# A barra de progresso do Invoke-WebRequest deixa o 5.1 muito lento
$ProgressPreference = 'SilentlyContinue'
# Sem o "Expect: 100-continue" que o .NET Framework manda por padrao nos POST
try { [System.Net.ServicePointManager]::Expect100Continue = $false } catch { }
# Texto enviado a programas externos pelo stdin (kafka-console-producer) em UTF-8; no 5.1 o
# padrao e ASCII
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$GatewayUrl = $GatewayUrl.TrimEnd('/')
$PrometheusUrl = $PrometheusUrl.TrimEnd('/')
$GrafanaUrl = $GrafanaUrl.TrimEnd('/')
if (-not $ResultadoXml) { $ResultadoXml = Join-Path $PSScriptRoot 'e2e-resultado.xml' }
$EmailCoordenacao = 'coordenacao@distrischool.com'
$SenhaCoordenacao = '123456'

# Estado compartilhado entre as secoes (as funcoes rodam em escopo filho; o hashtable e o mesmo)
$Ctx = @{
    Token           = $null
    Logins          = New-Object System.Collections.Generic.List[DateTime]
    Sufixo          = (Get-Date -Format 'yyMMddHHmmss') + (Get-Random -Minimum 100 -Maximum 1000)
    Contador        = 0
    Base            = $null
    Usuarios        = $null
    Parados         = New-Object System.Collections.Generic.List[string]
    UsuariosCriados = New-Object System.Collections.Generic.List[object]
}

$PastaModulos = Join-Path $PSScriptRoot 'e2e'
foreach ($modulo in @('Nucleo', 'Http', 'Docker', 'Dados', 'Stomp', 'SecaoLogin', 'SecaoPerfis', 'SecaoAdministrativa', 'SecaoEscolar', 'SecaoFluxo', 'SecaoNotificacoes',
                      'SecaoResiliencia', 'SecaoRateLimit')) {
    . (Join-Path $PastaModulos ($modulo + '.ps1'))
}

# Catalogo das secoes, na ordem de execucao. ParaContainers: pulada com -PularResiliencia.
$Catalogo = @(
    @{ Id = 'login';                      Nome = 'Login e JWT';                                       Funcao = 'Invoke-SecaoLogin';                     ParaContainers = $false }
    @{ Id = 'perfis';                     Nome = 'Usuarios e perfis de acesso';                       Funcao = 'Invoke-SecaoPerfis';                    ParaContainers = $false }
    @{ Id = 'administrativa';             Nome = 'Gestao administrativa';                             Funcao = 'Invoke-SecaoAdministrativa';            ParaContainers = $false }
    @{ Id = 'alunos';                     Nome = 'Alunos';                                            Funcao = 'Invoke-SecaoAlunos';                    ParaContainers = $false }
    @{ Id = 'matriculas';                 Nome = 'Matriculas';                                        Funcao = 'Invoke-SecaoMatriculas';                ParaContainers = $false }
    @{ Id = 'frequencia';                 Nome = 'Frequencia';                                        Funcao = 'Invoke-SecaoFrequencia';                ParaContainers = $false }
    @{ Id = 'notas';                      Nome = 'Avaliacoes e notas';                                Funcao = 'Invoke-SecaoNotas';                     ParaContainers = $false }
    @{ Id = 'boletim';                    Nome = 'Boletim';                                           Funcao = 'Invoke-SecaoBoletim';                   ParaContainers = $false }
    @{ Id = 'fluxo';                      Nome = 'Fluxo funcional';                                   Funcao = 'Invoke-SecaoFluxo';                     ParaContainers = $false }
    @{ Id = 'notificacoes';               Nome = 'Notificacoes';                                      Funcao = 'Invoke-SecaoNotificacoes';              ParaContainers = $false }
    @{ Id = 'dlt';                        Nome = 'Dead Letter Topic';                                 Funcao = 'Invoke-SecaoDlt';                       ParaContainers = $false }
    @{ Id = 'resiliencia-administrativa'; Nome = 'gestao-administrativa-service fora do ar';          Funcao = 'Invoke-SecaoResilienciaAdministrativa'; ParaContainers = $true }
    @{ Id = 'resiliencia-notificacao';    Nome = 'notificacao-service fora do ar';                    Funcao = 'Invoke-SecaoResilienciaNotificacao';    ParaContainers = $true }
    @{ Id = 'outbox';                     Nome = 'Outbox com o Kafka fora do ar';                     Funcao = 'Invoke-SecaoOutbox';                    ParaContainers = $true }
    @{ Id = 'circuitbreaker';             Nome = 'Circuit breaker com o gestao-escolar-service fora'; Funcao = 'Invoke-SecaoCircuitBreaker';            ParaContainers = $true }
    @{ Id = 'ratelimit';                  Nome = 'Rate limit do login';                               Funcao = 'Invoke-SecaoRateLimit';                 ParaContainers = $false }
)

# -Secoes a,b chega como um texto so quando o script e chamado com powershell -File
$selecionadas = @($Secoes | ForEach-Object { ([string]$_).Split(',') } | ForEach-Object { $_.Trim().ToLower() } | Where-Object { $_ })
$idsValidos = @($Catalogo | ForEach-Object { $_.Id })
$desconhecidas = @($selecionadas | Where-Object { $idsValidos -notcontains $_ })
if ($desconhecidas.Count -gt 0) {
    Write-Host ('Secoes desconhecidas: ' + ($desconhecidas -join ', ')) -ForegroundColor Red
    Write-Host ('Secoes validas: ' + ($idsValidos -join ', ')) -ForegroundColor Red
    exit 2
}

$Raiz = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $Raiz 'docker-compose.yml'))) {
    Write-Host ('docker-compose.yml nao encontrado em ' + $Raiz) -ForegroundColor Red
    exit 1
}

Write-Host ('PowerShell ' + $PSVersionTable.PSVersion + ' (' + $PSVersionTable.PSEdition + ')')
Write-Host ('Gateway: ' + $GatewayUrl)
Write-Host ('Projeto: ' + $Raiz)
Write-Host ('Sufixo dos dados de teste: ' + $Ctx.Sufixo)
if ($selecionadas.Count -gt 0) { Write-Host ('Secoes: ' + ($selecionadas -join ', ')) }
if ($PularResiliencia) { Write-Host 'Pulando as secoes que param containers (-PularResiliencia)' }

$codigoSaida = 1
$cronTotal = [System.Diagnostics.Stopwatch]::StartNew()
Push-Location $Raiz
try {
    $preRequisitos = Invoke-Secao 'Pre-requisitos' {
        $versao = Invoke-Compose @('version')
        Assert-Condicao 'docker compose disponivel' ($versao.ExitCode -eq 0) (($versao.Saida + $versao.Erros) -join ' ')

        $saude = Invoke-Api -Metodo GET -Caminho '/actuator/health/readiness' -SemToken -TimeoutSec 10
        Assert-Status $saude 200 'GET /actuator/health/readiness do gateway'
    }

    if ($preRequisitos) {
        foreach ($secao in $Catalogo) {
            if ($selecionadas.Count -gt 0 -and $selecionadas -notcontains $secao.Id) { continue }
            if ($PularResiliencia -and $secao.ParaContainers) {
                Invoke-Secao $secao.Nome { Skip-Secao 'Secao pulada' 'para containers; -PularResiliencia' } | Out-Null
                continue
            }
            $funcao = $secao.Funcao
            Invoke-Secao $secao.Nome { & $funcao } | Out-Null
        }
    }
} finally {
    # Tambem em Ctrl+C: o teste nunca deixa o ambiente com servicos parados
    Write-Host ''
    Write-Host 'Garantindo que nenhum container ficou parado...' -ForegroundColor DarkGray
    $falhaReligar = ''
    try { $falhaReligar = Restore-Containers } catch { $falhaReligar = $_.Exception.Message }
    if ($falhaReligar) {
        Write-Host ('Falha ao religar os servicos: ' + $falhaReligar) -ForegroundColor Red
        Write-Host ('Rode: docker compose start ' + ($ServicosQuePodemParar -join ' ')) -ForegroundColor Red
    }

    # Usuarios de teste nao ficam ativos (exclusao logica)
    if ($Ctx.Token) {
        # ToArray(): @() de uma List[object] vazia lanca "Argument types do not match" no 5.1
        foreach ($id in $Ctx.UsuariosCriados.ToArray()) {
            try { Invoke-Api -Metodo DELETE -Caminho ('/api/usuarios/' + $id) -TimeoutSec 10 | Out-Null } catch { }
        }
    }

    $cronTotal.Stop()
    $semFalhas = Write-ResumoFinal $cronTotal.Elapsed.TotalSeconds
    if ($semFalhas -and -not $falhaReligar) { $codigoSaida = 0 }
    try {
        Write-JUnit $ResultadoXml $cronTotal.Elapsed.TotalSeconds
        Write-Host ('Relatorio JUnit: ' + $ResultadoXml) -ForegroundColor DarkGray
    } catch {
        Write-Host ('Nao foi possivel gravar o relatorio JUnit: ' + $_.Exception.Message) -ForegroundColor Red
    }
    Pop-Location
}

exit $codigoSaida
