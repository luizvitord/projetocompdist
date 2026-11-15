# Secao "infra": health e readiness, Swagger de cada servico pelo gateway, Prometheus e Grafana.
# Prometheus ou Grafana fora do ar: as verificacoes deles ficam [PULADO], nao [FALHOU].

function Invoke-SecaoInfra {
    $r = Invoke-Api -Metodo GET -Caminho '/actuator/health' -SemToken
    Test-Condicao 'GET /actuator/health do gateway -> 200 UP' (($r.Status -eq 200) -and ($r.Json.status -eq 'UP')) (Get-Resumo $r)

    # Readiness: todos os containers rodando e, os que tem healthcheck, healthy
    foreach ($servico in (Get-ServicosCompose)) {
        $e = Get-EstadoContainer $servico
        $saude = $e.Saude
        if (-not $saude) { $saude = 'sem healthcheck' }
        Test-Condicao ('Container ' + $servico + ' pronto') ($e.Estado -eq 'running' -and ($e.Saude -eq '' -or $e.Saude -eq 'healthy')) `
            ('estado: ' + $e.Estado + '; healthcheck: ' + $saude)
    }

    # Swagger de cada servico pelo gateway (rotas livres, sem token)
    $swagger = @(
        @{ Prefixo = 'administrativa'; Rota = '/api/turmas' },
        @{ Prefixo = 'escolar'; Rota = '/api/matriculas' },
        @{ Prefixo = 'usuarios'; Rota = '/api/usuarios' },
        @{ Prefixo = 'notificacao'; Rota = '/api/notificacoes' }
    )
    foreach ($s in $swagger) {
        $r = Invoke-Api -Metodo GET -Caminho ('/' + $s.Prefixo + '/v3/api-docs') -SemToken
        $rotas = @()
        if ($null -ne $r.Json -and $null -ne $r.Json.paths) { $rotas = @($r.Json.paths.PSObject.Properties | ForEach-Object { $_.Name }) }
        Test-Condicao ('GET /' + $s.Prefixo + '/v3/api-docs -> 200 com o OpenAPI e a rota ' + $s.Rota) `
            (($r.Status -eq 200) -and [bool]$r.Json.openapi -and ($rotas -contains $s.Rota)) ((Get-Resumo $r) + '; rotas: ' + $rotas.Count)
        $r = Invoke-Api -Metodo GET -Caminho ('/' + $s.Prefixo + '/swagger-ui.html') -SemToken
        Test-Condicao ('GET /' + $s.Prefixo + '/swagger-ui.html -> 200 (Swagger UI)') (($r.Status -eq 200) -and ($r.Texto -match '(?i)swagger')) (Get-Resumo $r)
    }

    Test-Prometheus
    Test-Grafana
}

function Test-Prometheus {
    $r = Invoke-Api -Metodo GET -Url ($PrometheusUrl + '/api/v1/targets') -TimeoutSec 10
    if ($r.Status -eq 0) {
        Write-Pulado 'Prometheus: alvos e regras de alerta' ('Prometheus fora do ar em ' + $PrometheusUrl + ': ' + $r.ErroConexao)
        return
    }
    Test-Status $r 200 'GET /api/v1/targets do Prometheus'
    $alvos = @($r.Json.data.activeTargets)
    $esperados = @('api-gateway', 'gestao-administrativa-service', 'gestao-escolar-service', 'usuarios-service', 'notificacao-service')
    foreach ($servico in $esperados) {
        $doServico = @($alvos | Where-Object { $_.labels.servico -eq $servico })
        $fora = @($doServico | Where-Object { $_.health -ne 'up' })
        $erro = (@($fora | ForEach-Object { $_.lastError }) -join '; ')
        Test-Condicao ('Prometheus: alvo ' + $servico + ' UP') (($doServico.Count -ge 1) -and ($fora.Count -eq 0)) `
            ('alvos: ' + $doServico.Count + '; fora: ' + $fora.Count + ' ' + $erro)
    }
    Test-Condicao 'Prometheus: todos os alvos UP' (@($alvos | Where-Object { $_.health -ne 'up' }).Count -eq 0) ('alvos: ' + $alvos.Count)

    $r = Invoke-Api -Metodo GET -Url ($PrometheusUrl + '/api/v1/rules') -TimeoutSec 10
    $regras = @($r.Json.data.groups | ForEach-Object { @($_.rules) })
    $nomes = @($regras | ForEach-Object { $_.name } | Select-Object -Unique)
    $esperadas = @('ServicoFora', 'LatenciaP95Alta', 'TaxaErros5xxAlta', 'CircuitBreakerAberto', 'OutboxAcumulada', 'LagConsumidorAlto')
    $faltando = @($esperadas | Where-Object { $nomes -notcontains $_ })
    Test-Condicao 'Prometheus: as 6 regras de alerta carregadas' (($r.Status -eq 200) -and ($faltando.Count -eq 0)) `
        ('regras: ' + ($nomes -join ', ') + '; faltando: ' + ($faltando -join ', '))
    $comErro = @($regras | Where-Object { $_.health -ne 'ok' })
    Test-Condicao 'Prometheus: regras de alerta sem erro de avaliacao' ($comErro.Count -eq 0) (($comErro | ForEach-Object { $_.name + ': ' + $_.lastError }) -join '; ')
}

function Test-Grafana {
    $r = Invoke-Api -Metodo GET -Url ($GrafanaUrl + '/api/health') -TimeoutSec 10
    if ($r.Status -eq 0) {
        Write-Pulado 'Grafana: health e dashboard' ('Grafana fora do ar em ' + $GrafanaUrl + ': ' + $r.ErroConexao)
        return
    }
    Test-Condicao 'GET /api/health do Grafana -> 200 com o banco ok' (($r.Status -eq 200) -and ($r.Json.database -eq 'ok')) (Get-Resumo $r)
    $basico = 'Basic ' + [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($GrafanaUsuario + ':' + $GrafanaSenha))
    $r = Invoke-Api -Metodo GET -Url ($GrafanaUrl + '/api/search?type=dash-db') -Cabecalhos @{ Authorization = $basico } -TimeoutSec 10
    $painel = @($r.Json) | Where-Object { $_.uid -eq 'distrischool-visao-geral' } | Select-Object -First 1
    Test-Condicao 'Grafana: dashboard DistriSchool - Visao geral provisionado' (($r.Status -eq 200) -and ($null -ne $painel)) (Get-Resumo $r)
    $r = Invoke-Api -Metodo GET -Url ($GrafanaUrl + '/api/datasources/uid/prometheus/health') -Cabecalhos @{ Authorization = $basico } -TimeoutSec 20
    Test-Condicao 'Grafana: datasource Prometheus responde' (($r.Status -eq 200) -and ($r.Json.status -eq 'OK')) (Get-Resumo $r)
}
