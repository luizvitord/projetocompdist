# Docker do e2e: docker compose, psql, Kafka e a garantia de que nenhum container fica parado.

# Servicos que alguma secao pode parar. O finally do e2e.ps1 liga todos de novo (tambem em Ctrl+C).
$ServicosQuePodemParar = @('kafka', 'gestao-escolar-service', 'gestao-administrativa-service', 'notificacao-service')

# Roda um programa externo e separa stdout de stderr. O docker compose escreve o progresso no
# stderr; com 2>&1 no 5.1 cada linha vira um ErrorRecord, que com 'Stop' interromperia o script.
function Invoke-Nativo([string]$Programa, [string[]]$Argumentos, [string]$Entrada = $null) {
    $ErrorActionPreference = 'Continue'
    if ($null -ne $Entrada -and $Entrada -ne '') {
        $saida = $Entrada | & $Programa @Argumentos 2>&1
    } else {
        $saida = & $Programa @Argumentos 2>&1
    }
    $codigo = $LASTEXITCODE
    $linhas = @()
    $erros = @()
    foreach ($item in @($saida)) {
        if ($item -is [System.Management.Automation.ErrorRecord]) {
            $erros += $item.ToString()
        } elseif ($null -ne $item) {
            $linhas += [string]$item
        }
    }
    return [pscustomobject]@{ ExitCode = $codigo; Saida = $linhas; Erros = $erros }
}

function Invoke-Compose([string[]]$Argumentos, [string]$Entrada = $null) {
    return Invoke-Nativo 'docker' (@('compose') + $Argumentos) $Entrada
}

function Stop-Servico([string]$Servico) {
    Write-Info ('docker compose stop ' + $Servico)
    # Registrado antes de parar: um Ctrl+C no meio do stop tambem religa o servico
    if ($Ctx.Parados -notcontains $Servico) { $Ctx.Parados.Add($Servico) }
    $r = Invoke-Compose @('stop', $Servico)
    $detalhe = ''
    if ($r.ExitCode -ne 0) { $detalhe = ($r.Erros + $r.Saida) -join ' ' }
    Assert-Condicao ('docker compose stop ' + $Servico) ($r.ExitCode -eq 0) $detalhe
}

function Start-Servico([string]$Servico) {
    Write-Info ('docker compose start ' + $Servico)
    $r = Invoke-Compose @('start', $Servico)
    $detalhe = ''
    if ($r.ExitCode -ne 0) { $detalhe = ($r.Erros + $r.Saida) -join ' ' } else { [void]$Ctx.Parados.Remove($Servico) }
    Test-Condicao ('docker compose start ' + $Servico) ($r.ExitCode -eq 0) $detalhe
}

# Id do container do servico no projeto do compose
function Get-IdContainer([string]$Servico) {
    $ps = Invoke-Compose @('ps', '-a', '-q', $Servico)
    return (@($ps.Saida | Where-Object { $_ }) | Select-Object -First 1)
}

# Estado do container: running/exited/... e o status do healthcheck (healthy, starting, unhealthy
# ou vazio quando o servico nao tem healthcheck)
function Get-EstadoContainer([string]$Servico) {
    $id = Get-IdContainer $Servico
    if (-not $id) { return [pscustomobject]@{ Existe = $false; Estado = 'sem-container'; Saude = '' } }
    $inspect = Invoke-Nativo 'docker' @('inspect', '-f', '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}', $id)
    $partes = ((@($inspect.Saida) -join '').Trim()).Split('|')
    $saude = ''
    if ($partes.Count -gt 1) { $saude = $partes[1] }
    return [pscustomobject]@{ Existe = $true; Estado = $partes[0]; Saude = $saude }
}

# Pronto = rodando e, se tiver healthcheck, healthy
function Test-ContainerPronto([string]$Servico) {
    $e = Get-EstadoContainer $Servico
    return ($e.Estado -eq 'running' -and ($e.Saude -eq '' -or $e.Saude -eq 'healthy'))
}

# Espera o healthcheck de um servico religado (a JVM leva de 20 a 60 s para subir)
function Wait-ContainerPronto([string]$Servico, [int]$PrazoSeg = 150) {
    $espera = Wait-Ate -PrazoSeg $PrazoSeg -IntervaloMs 2000 -Condicao { Test-ContainerPronto $Servico }
    $e = Get-EstadoContainer $Servico
    Assert-Condicao ($Servico + ' saudavel depois de religado') $espera.Ok ($espera.Detalhe + '; estado: ' + $e.Estado + ' ' + $e.Saude)
}

# Servicos do compose (docker compose config --services)
function Get-ServicosCompose {
    $r = Invoke-Compose @('config', '--services')
    return @($r.Saida | Where-Object { $_ } | Sort-Object)
}

# Liga de novo tudo o que o script pode ter parado. Chamado no finally (falha, Ctrl+C ou fim normal).
function Restore-Containers {
    $alvos = @($ServicosQuePodemParar + @($Ctx.Parados)) | Select-Object -Unique
    $r = Invoke-Compose (@('start') + $alvos)
    if ($r.ExitCode -ne 0) {
        return ('docker compose start ' + ($alvos -join ' ') + ' falhou: ' + (($r.Erros + $r.Saida) -join ' '))
    }
    $Ctx.Parados.Clear()
    return ''
}

# Consulta de uma linha/coluna. O SQL usa so aspas simples: o 5.1 nao escapa aspas duplas ao
# passar argumentos para programas externos.
function Invoke-Psql([string]$Servico, [string]$Banco, [string]$Sql) {
    $r = Invoke-Compose @('exec', '-T', $Servico, 'psql', '-U', $Banco, '-d', $Banco,
        '-v', 'ON_ERROR_STOP=1', '-t', '-A', '-c', $Sql)
    if ($r.ExitCode -ne 0) {
        throw ('psql em ' + $Servico + ' falhou (exit ' + $r.ExitCode + '): ' + (($r.Erros + $r.Saida) -join ' '))
    }
    return ((@($r.Saida) | Where-Object { $_ -ne '' }) -join "`n").Trim()
}

function Invoke-PsqlAdministrativa([string]$Sql) {
    return Invoke-Psql 'postgres-administrativa' 'gestao_administrativa' $Sql
}

function Invoke-PsqlEscolar([string]$Sql) {
    return Invoke-Psql 'postgres-escolar' 'gestao_escolar' $Sql
}

# Publica uma mensagem num topico pelo kafka-console-producer.sh do container kafka. A mensagem vai
# pelo stdin ("chave|valor"), nao por argumento: o 5.1 nao escapa as aspas do JSON nos argumentos.
function Send-Kafka([string]$Topico, [string]$Chave, [string]$Valor) {
    $r = Invoke-Compose @('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-producer.sh',
        '--bootstrap-server', 'kafka:29092', '--topic', $Topico,
        '--property', 'parse.key=true', '--property', 'key.separator=|') ($Chave + '|' + $Valor)
    if ($r.ExitCode -ne 0) {
        throw ('kafka-console-producer em ' + $Topico + ' falhou (exit ' + $r.ExitCode + '): ' + (($r.Erros + $r.Saida) -join ' '))
    }
}

# Mensagens de um topico, do inicio, ate ficar $TimeoutMs sem mensagem nova
function Read-Kafka([string]$Topico, [int]$TimeoutMs = 4000) {
    $r = Invoke-Compose @('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-console-consumer.sh',
        '--bootstrap-server', 'kafka:29092', '--topic', $Topico, '--from-beginning',
        '--timeout-ms', [string]$TimeoutMs, '--property', 'print.key=true')
    return @($r.Saida)
}

# Estado do grupo de consumidores no Kafka (Stable, PreparingRebalance, Empty...) e numero de membros
function Get-EstadoGrupoKafka([string]$Grupo) {
    $r = Invoke-Compose @('exec', '-T', 'kafka', '/opt/kafka/bin/kafka-consumer-groups.sh',
        '--bootstrap-server', 'kafka:29092', '--describe', '--group', $Grupo, '--state')
    return [string](@($r.Saida | Where-Object { $_ -match [regex]::Escape($Grupo) }) | Select-Object -Last 1)
}

# Logo depois de subir (ou de o Kafka reiniciar), os consumidores ainda estao entrando no grupo;
# os eventos so sao lidos quando o grupo fica Stable com membros
function Wait-GrupoKafkaEstavel([string]$Grupo, [int]$PrazoSeg = 90) {
    $espera = Wait-Ate -PrazoSeg $PrazoSeg -IntervaloMs 2000 -Condicao {
        (Get-EstadoGrupoKafka $Grupo) -match '\sStable\s+[1-9]'
    }
    Assert-Condicao ('Consumidores do grupo ' + $Grupo + ' prontos (Stable no Kafka)') $espera.Ok `
        ($espera.Detalhe + '; estado: ' + (Get-EstadoGrupoKafka $Grupo))
}
