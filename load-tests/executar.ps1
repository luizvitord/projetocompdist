<#
.SYNOPSIS
    Roda o plano de carga do DistriSchool (distrischool.jmx) no JMeter em modo nao grafico, pelo Docker.

.DESCRIPTION
    Nao precisa do JMeter instalado: usa a imagem alpine/jmeter com versao e digest fixos.
    Gera o resultado bruto (.jtl), o log do JMeter e o relatorio HTML em
    load-tests/resultados/<data-hora>-<perfil>/.

    Alvos:
      compose   O container do JMeter entra na rede do Docker Compose e chama api-gateway:8080.
                A pilha precisa estar no ar (docker compose up).
      minikube  O JMeter chama host.docker.internal:8080, onde fica um kubectl port-forward para o
                svc/api-gateway. Se a porta 8080 da maquina estiver livre, o script abre o
                port-forward e o fecha no fim.

    Perfis (usuarios / rampa / duracao total, contando a rampa):
      fumaca    5 usuarios, rampa de 10 s, 1 min
      carga     50 usuarios, rampa de 2 min, 12 min (10 min com todos os usuarios)
      estresse  rampa ate 200 usuarios em 10 min, mais 2 min no pico
      hpa       120 usuarios, rampa de 1 min, 11 min (10 min de carga alta para o HPA escalar)

.EXAMPLE
    .\load-tests\executar.ps1 -Perfil fumaca -Alvo compose

.EXAMPLE
    .\load-tests\executar.ps1 -Perfil hpa -Alvo minikube
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('fumaca', 'carga', 'estresse', 'hpa')]
    [string]$Perfil,

    [ValidateSet('compose', 'minikube')]
    [string]$Alvo = 'compose',

    [string]$Email = 'coordenacao@distrischool.com',
    [string]$Senha = '123456',

    # Alunos matriculados criados pelo setUp (cada lancamento de notas cobre todos eles)
    [ValidateRange(1, 40)]
    [int]$AlunosMassa = 5
)

$ErrorActionPreference = 'Stop'

# Imagem fixa: tag e digest (a tag 5.6.3 e republicada quando a imagem base muda)
$Imagem = 'alpine/jmeter:5.6.3@sha256:c928d710bf4d148d9662b6329c012931a878965df04b99f32b79952ca01cc530'

$Perfis = @{
    fumaca   = @{ Usuarios = 5;   Rampa = 10;  Duracao = 60 }
    carga    = @{ Usuarios = 50;  Rampa = 120; Duracao = 720 }
    estresse = @{ Usuarios = 200; Rampa = 600; Duracao = 720 }
    hpa      = @{ Usuarios = 120; Rampa = 60;  Duracao = 660 }
}
$p = $Perfis[$Perfil]

$Pasta = $PSScriptRoot
$Raiz = Split-Path $Pasta -Parent
$Nome = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $Perfil + '-' + $Alvo
$Saida = Join-Path (Join-Path $Pasta 'resultados') $Nome
New-Item -ItemType Directory -Force $Saida | Out-Null

function Test-PortaLocal([int]$Porta) {
    $cliente = New-Object System.Net.Sockets.TcpClient
    try {
        $tentativa = $cliente.BeginConnect('127.0.0.1', $Porta, $null, $null)
        return ($tentativa.AsyncWaitHandle.WaitOne(500) -and $cliente.Connected)
    } catch {
        return $false
    } finally {
        $cliente.Close()
    }
}

$portForward = $null
$argsRede = @()
if ($Alvo -eq 'compose') {
    Push-Location $Raiz
    try {
        $container = (& docker compose ps -q api-gateway | Out-String).Trim()
    } finally {
        Pop-Location
    }
    if (-not $container) {
        throw 'O api-gateway nao esta rodando no Docker Compose. Rode "docker compose up -d" na raiz do projeto.'
    }
    # Rede do Compose em que o gateway esta (o nome depende da pasta do projeto)
    $rede = (& docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}} {{end}}' $container | Out-String).Trim().Split(' ')[0]
    $argsRede = @('--network', $rede)
    $AlvoHost = 'api-gateway'
    $AlvoPorta = 8080
} else {
    $AlvoHost = 'host.docker.internal'
    $AlvoPorta = 8080
    if (-not (Test-PortaLocal $AlvoPorta)) {
        Write-Host 'Abrindo kubectl port-forward -n distrischool svc/api-gateway 8080:8080...'
        $portForward = Start-Process -FilePath 'kubectl' -PassThru -WindowStyle Hidden `
            -ArgumentList @('port-forward', '-n', 'distrischool', 'svc/api-gateway', '8080:8080')
        $limite = (Get-Date).AddSeconds(20)
        while (-not (Test-PortaLocal $AlvoPorta) -and (Get-Date) -lt $limite) { Start-Sleep -Milliseconds 500 }
        if (-not (Test-PortaLocal $AlvoPorta)) {
            Stop-Process -Id $portForward.Id -ErrorAction SilentlyContinue
            throw 'O port-forward para o svc/api-gateway nao abriu. O minikube esta no ar (kubectl get pods -n distrischool)?'
        }
    } else {
        Write-Host 'A porta 8080 ja esta aberta; usando o port-forward (ou servico) que ja esta rodando.'
    }
}

Write-Host ('Perfil {0}: {1} usuarios, rampa {2} s, duracao {3} s; alvo {4} ({5}:{6})' -f `
    $Perfil, $p.Usuarios, $p.Rampa, $p.Duracao, $Alvo, $AlvoHost, $AlvoPorta)
Write-Host ('Resultados em ' + $Saida)

$argsJmeter = @(
    '-n', '-t', '/testes/distrischool.jmx',
    '-l', ('/testes/resultados/' + $Nome + '/resultado.jtl'),
    '-j', ('/testes/resultados/' + $Nome + '/jmeter.log'),
    '-e', '-o', ('/testes/resultados/' + $Nome + '/relatorio'),
    ('-Jhost=' + $AlvoHost), ('-Jporta=' + $AlvoPorta), '-Jprotocolo=http',
    ('-Jusuarios=' + $p.Usuarios), ('-Jrampa=' + $p.Rampa), ('-Jduracao=' + $p.Duracao),
    ('-Jemail=' + $Email), ('-Jsenha=' + $Senha), ('-JalunosMassa=' + $AlunosMassa),
    # Relatorio HTML: agrupa os graficos de tempo em janelas de 10 s
    '-Jjmeter.reportgenerator.overall_granularity=10000'
)

# O entrypoint da imagem reserva 80% da memoria livre do Docker para o JMeter; chamando o jmeter
# direto, o HEAP fica fixo (cabe junto com o minikube)
$argsDocker = @('run', '--rm') + $argsRede + @(
    '-e', 'HEAP=-Xms256m -Xmx1g -XX:MaxMetaspaceSize=256m',
    '-v', ($Pasta + ':/testes'),
    '--entrypoint', 'jmeter',
    $Imagem
) + $argsJmeter

try {
    & docker @argsDocker
    $codigo = $LASTEXITCODE
} finally {
    if ($portForward) {
        Stop-Process -Id $portForward.Id -ErrorAction SilentlyContinue
    }
}

if ($codigo -ne 0) {
    throw ('O JMeter terminou com codigo ' + $codigo + '. Veja ' + (Join-Path $Saida 'jmeter.log'))
}

# Resumo a partir do .jtl (CSV): total, erros e quais rotulos falharam
$jtl = Join-Path $Saida 'resultado.jtl'
$linhas = @(Import-Csv $jtl)
$falhas = @($linhas | Where-Object { $_.success -ne 'true' })
$pct = 0
if ($linhas.Count -gt 0) { $pct = [math]::Round(100.0 * $falhas.Count / $linhas.Count, 2) }
Write-Host ''
Write-Host ('Requisicoes: ' + $linhas.Count + '; erros: ' + $falhas.Count + ' (' + $pct + '%)')
if ($falhas.Count -gt 0) {
    $falhas | Group-Object label, responseCode | Sort-Object Count -Descending | Select-Object -First 10 |
        ForEach-Object { Write-Host ('  ' + $_.Count + ' x ' + $_.Name) }
}
Write-Host ('Relatorio HTML: ' + (Join-Path $Saida 'relatorio\index.html'))
