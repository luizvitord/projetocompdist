# HTTP do e2e: chamadas ao gateway (e ao Prometheus/Grafana) sem excecao por status, nos dois
# PowerShell:
#   - Windows PowerShell 5.1: o Invoke-WebRequest lanca WebException para status >= 400; a resposta
#     (HttpWebResponse) fica em $_.Exception.Response e o corpo e lido do stream dela.
#   - PowerShell 7: com -SkipHttpErrorCheck nao ha excecao; sem ele (6.x), a excecao e
#     HttpResponseException, a resposta e um HttpResponseMessage e o corpo fica em
#     $_.ErrorDetails.Message.

$EhPowerShell7 = ($PSVersionTable.PSVersion.Major -ge 7)

# Corpo de uma resposta de erro no 5.1 (HttpWebResponse)
function Read-CorpoErro($Resposta, $ErrorRecord) {
    $texto = ''
    try {
        if ($Resposta -is [System.Net.HttpWebResponse] -or $Resposta -is [System.Net.WebResponse]) {
            $stream = $Resposta.GetResponseStream()
            if ($stream.CanSeek) { $stream.Position = 0 }
            $leitor = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
            $texto = $leitor.ReadToEnd()
        } elseif ($null -ne $Resposta -and $null -ne $Resposta.Content) {
            # HttpResponseMessage (PowerShell 6/7 sem -SkipHttpErrorCheck)
            $texto = $Resposta.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        }
    } catch {
        $texto = ''
    }
    if (-not $texto -and $null -ne $ErrorRecord -and $null -ne $ErrorRecord.ErrorDetails) {
        $texto = [string]$ErrorRecord.ErrorDetails.Message
    }
    return $texto
}

# Cabecalhos como texto; no 7 cada valor e uma lista
function ConvertTo-Cabecalhos($Origem) {
    $cabecalhos = @{}
    if ($null -eq $Origem) { return $cabecalhos }
    foreach ($k in @($Origem.Keys)) {
        $cabecalhos[[string]$k] = (@($Origem[$k]) | ForEach-Object { [string]$_ }) -join ', '
    }
    return $cabecalhos
}

# Chama a API e devolve Status, Texto, Json, ContentType, Headers e Ms. Status 0 = sem resposta
# (conexao recusada, timeout). Sem -Url, o caminho e relativo ao gateway.
function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Metodo,
        [string]$Caminho = '',
        [string]$Url = '',
        $Corpo = $null,
        [switch]$SemToken,
        # Token de outro usuario; sem ele vale o da coordenacao ($Ctx.Token)
        [string]$Token = '',
        [hashtable]$Cabecalhos = @{},
        [int]$TimeoutSec = 30
    )
    $headers = @{}
    foreach ($k in $Cabecalhos.Keys) { $headers[$k] = $Cabecalhos[$k] }
    if (-not $SemToken -and -not $Url -and -not $headers.ContainsKey('Authorization')) {
        if ($Token) {
            $headers['Authorization'] = 'Bearer ' + $Token
        } elseif ($Ctx.Token) {
            $headers['Authorization'] = 'Bearer ' + $Ctx.Token
        }
    }
    $destino = $Url
    if (-not $destino) { $destino = $GatewayUrl + $Caminho }
    $parametros = @{
        Uri             = $destino
        Method          = $Metodo
        Headers         = $headers
        UseBasicParsing = $true
        TimeoutSec      = $TimeoutSec
    }
    if ($EhPowerShell7) { $parametros['SkipHttpErrorCheck'] = $true }
    if ($null -ne $Corpo) {
        if ($Corpo -is [string]) {
            $json = $Corpo
        } else {
            # -InputObject (e nao pipeline) para uma lista de um item continuar sendo lista
            $json = ConvertTo-Json -InputObject $Corpo -Depth 10 -Compress
        }
        $parametros['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $parametros['ContentType'] = 'application/json; charset=utf-8'
    }

    $status = 0
    $texto = ''
    $cabecalhosResposta = @{}
    $erroConexao = $null
    $cronometro = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-WebRequest @parametros
        $status = [int]$r.StatusCode
        $cabecalhosResposta = ConvertTo-Cabecalhos $r.Headers
        if ($null -ne $r.RawContentStream) {
            # O 5.1 decodifica application/json sem charset como ISO-8859-1; os bytes sao UTF-8
            $texto = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        }
    } catch {
        $excecao = $_.Exception
        $resposta = $null
        if ($null -ne $excecao -and $excecao.PSObject.Properties['Response']) { $resposta = $excecao.Response }
        if ($null -eq $resposta) {
            $erroConexao = $excecao.Message
        } else {
            $status = [int]$resposta.StatusCode
            if ($resposta -is [System.Net.WebResponse]) {
                foreach ($k in $resposta.Headers.AllKeys) { $cabecalhosResposta[$k] = [string]$resposta.Headers[$k] }
                if ($resposta.ContentType) { $cabecalhosResposta['Content-Type'] = [string]$resposta.ContentType }
            } else {
                $cabecalhosResposta = ConvertTo-Cabecalhos $resposta.Headers
                if ($null -ne $resposta.Content -and $null -ne $resposta.Content.Headers.ContentType) {
                    $cabecalhosResposta['Content-Type'] = $resposta.Content.Headers.ContentType.ToString()
                }
            }
            $texto = Read-CorpoErro $resposta $_
        }
    }
    $cronometro.Stop()

    $dados = $null
    if ($texto) {
        try { $dados = ConvertFrom-Json -InputObject $texto } catch { $dados = $null }
    }
    $tipo = ''
    if ($cabecalhosResposta.ContainsKey('Content-Type')) { $tipo = [string]$cabecalhosResposta['Content-Type'] }
    return [pscustomobject]@{
        Status      = $status
        Texto       = $texto
        Json        = $dados
        ContentType = $tipo
        Headers     = $cabecalhosResposta
        Ms          = [int]$cronometro.Elapsed.TotalMilliseconds
        ErroConexao = $erroConexao
    }
}

function Get-Resumo($Resposta) {
    if ($Resposta.Status -eq 0) {
        return 'sem resposta em ' + $Resposta.Ms + ' ms: ' + $Resposta.ErroConexao
    }
    $resumo = 'HTTP ' + $Resposta.Status + ' em ' + $Resposta.Ms + ' ms'
    if ($Resposta.Status -ge 400 -and $Resposta.Texto) {
        $corpo = $Resposta.Texto -replace '\s+', ' '
        if ($corpo.Length -gt 400) { $corpo = $corpo.Substring(0, 400) + '...' }
        $resumo += ': ' + $corpo
    }
    return $resumo
}

function Assert-Status($Resposta, [int]$Esperado, [string]$Descricao) {
    Assert-Condicao ($Descricao + ' -> ' + $Esperado) ($Resposta.Status -eq $Esperado) (Get-Resumo $Resposta)
}

function Test-Status($Resposta, [int]$Esperado, [string]$Descricao) {
    Test-Condicao ($Descricao + ' -> ' + $Esperado) ($Resposta.Status -eq $Esperado) (Get-Resumo $Resposta)
}

# Preparacao de dados: so aparece na saida se falhar (e ai interrompe a secao)
function Assert-Preparacao($Resposta, [int]$Esperado, [string]$Descricao) {
    if ($Resposta.Status -ne $Esperado) {
        Assert-Condicao ('Preparacao: ' + $Descricao + ' -> ' + $Esperado) $false (Get-Resumo $Resposta)
    }
}

# ProblemDetail (RFC 9457): Content-Type application/problem+json, status e detail no corpo
function Test-ProblemDetail($Resposta, [int]$Status, [string]$Descricao) {
    $ok = ($Resposta.ContentType -like 'application/problem+json*') -and ($null -ne $Resposta.Json) `
        -and ([string]$Resposta.Json.status -eq [string]$Status) -and [bool]$Resposta.Json.detail
    Test-Condicao $Descricao $ok ('Content-Type: ' + $Resposta.ContentType + '; corpo: ' + $Resposta.Texto)
}

# Erro esperado: status + ProblemDetail numa verificacao so
function Test-Erro($Resposta, [int]$Status, [string]$Descricao) {
    $ok = ($Resposta.Status -eq $Status) -and ($Resposta.ContentType -like 'application/problem+json*') `
        -and ($null -ne $Resposta.Json) -and ([string]$Resposta.Json.status -eq [string]$Status) -and [bool]$Resposta.Json.detail
    Test-Condicao ($Descricao + ' -> ' + $Status + ' (ProblemDetail)') $ok (Get-Resumo $Resposta)
}

# Campos de um ProblemDetail de validacao: { "erros": { "campo": "mensagem" } }
function Get-CamposErro($Resposta) {
    if ($null -eq $Resposta.Json -or $null -eq $Resposta.Json.erros) { return @() }
    return @($Resposta.Json.erros.PSObject.Properties | ForEach-Object { $_.Name })
}

# 400 com ProblemDetail e com cada um dos campos esperados na lista "erros"
function Test-ErroValidacao($Resposta, [string[]]$Campos, [string]$Descricao) {
    $presentes = Get-CamposErro $Resposta
    $faltando = @($Campos | Where-Object { $presentes -notcontains $_ })
    $ok = ($Resposta.Status -eq 400) -and ($Resposta.ContentType -like 'application/problem+json*') -and ($faltando.Count -eq 0)
    $detalhe = 'campos esperados: ' + ($Campos -join ', ') + '; obtidos: ' + ($presentes -join ', ') + '; ' + (Get-Resumo $Resposta)
    Test-Condicao ($Descricao + ' -> 400 com erros em ' + ($Campos -join ', ')) $ok $detalhe
}

# 401/403 do gateway: o Spring Security responde sem corpo e com WWW-Authenticate no 401
function Test-NaoAutenticado($Resposta, [string]$Descricao) {
    $ok = ($Resposta.Status -eq 401) -and ([string]$Resposta.Headers['WWW-Authenticate'] -like 'Bearer*')
    Test-Condicao ($Descricao + ' -> 401 com WWW-Authenticate: Bearer') $ok `
        ((Get-Resumo $Resposta) + '; WWW-Authenticate: ' + (Format-Valor $Resposta.Headers['WWW-Authenticate']))
}

# Lista de uma resposta paginada (PagedModel: { "content": [...], "page": {...} })
function Get-Conteudo($Resposta) {
    if ($null -eq $Resposta.Json) { return @() }
    return @($Resposta.Json.content)
}

# Logins: o gateway aceita 10 por minuto por IP (balde que se recompoe aos poucos). O script guarda
# o horario de cada login e espera antes do 9o dentro de 60 s; um 429 inesperado ainda espera o
# Retry-After e tenta mais uma vez.
function Invoke-Login([string]$Email, [string]$Senha) {
    $agora = Get-Date
    $recentes = @($Ctx.Logins | Where-Object { ($agora - $_).TotalSeconds -lt 60 })
    if ($recentes.Count -ge 8) {
        $espera = [math]::Ceiling(61 - ($agora - ($recentes | Sort-Object | Select-Object -First 1)).TotalSeconds)
        if ($espera -gt 0) {
            Write-Info ('Aguardando ' + $espera + ' s pelo limite de 10 logins por minuto...')
            Start-Sleep -Seconds $espera
        }
    }
    $Ctx.Logins.Add((Get-Date))
    $r = Invoke-Api -Metodo POST -Caminho '/api/auth/login' -SemToken -Corpo @{ email = $Email; senha = $Senha }
    if ($r.Status -eq 429) {
        $retry = 10
        try { $retry = [int]$r.Headers['Retry-After'] + 1 } catch { $retry = 10 }
        Write-Info ('Login recebeu 429; aguardando ' + $retry + ' s (Retry-After)...')
        Start-Sleep -Seconds $retry
        $Ctx.Logins.Add((Get-Date))
        $r = Invoke-Api -Metodo POST -Caminho '/api/auth/login' -SemToken -Corpo @{ email = $Email; senha = $Senha }
    }
    return $r
}

# Login da coordenacao, uma vez por execucao (secoes rodadas sozinhas com -Secoes tambem precisam)
function Initialize-Admin {
    if ($Ctx.Token) { return }
    $login = Invoke-Login $EmailCoordenacao $SenhaCoordenacao
    Assert-Status $login 200 ('Login da coordenacao (' + $EmailCoordenacao + ')')
    $Ctx.Token = [string]$login.Json.token
}
