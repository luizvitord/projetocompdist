# Secao "ratelimit": 10 logins por minuto por IP no gateway. Roda por ultimo: depois dela, o
# limite fica esgotado por ate um minuto.

function Invoke-SecaoRateLimit {
    # O balde se recompoe aos poucos: espera um periodo inteiro desde o ultimo login do script para
    # ele estar cheio (espera calculada, nao um prazo fixo)
    if ($Ctx.Logins.Count -gt 0) {
        $ultimo = ($Ctx.Logins | Sort-Object | Select-Object -Last 1)
        $decorrido = ((Get-Date) - $ultimo).TotalSeconds
        if ($decorrido -lt 61) {
            $espera = [int][math]::Ceiling(61 - $decorrido)
            Write-Info ('Aguardando ' + $espera + ' s para o limite de logins se recompor...')
            Start-Sleep -Seconds $espera
        }
    }

    $statusLogins = @()
    $ultima = $null
    for ($i = 1; $i -le 11; $i++) {
        $Ctx.Logins.Add((Get-Date))
        $ultima = Invoke-Api -Metodo POST -Caminho '/api/auth/login' -SemToken `
            -Corpo @{ email = $EmailCoordenacao; senha = 'senha-errada-' + $i }
        $statusLogins += $ultima.Status
    }
    $sequencia = 'status: ' + ($statusLogins -join ', ')
    $primeiras = @($statusLogins[0..9] | Where-Object { $_ -ne 401 })
    Test-Condicao '10 primeiros logins com senha errada -> 401' ($primeiras.Count -eq 0) $sequencia
    Test-Condicao '11o login com senha errada -> 429' ($ultima.Status -eq 429) (Get-Resumo $ultima)
    Test-ProblemDetail $ultima 429 'Resposta 429 e um ProblemDetail (application/problem+json com status e detail)'
    Test-Condicao 'Resposta 429 traz o cabecalho Retry-After' ([bool]$ultima.Headers['Retry-After']) `
        ('Retry-After: ' + (Format-Valor $ultima.Headers['Retry-After']))
}
