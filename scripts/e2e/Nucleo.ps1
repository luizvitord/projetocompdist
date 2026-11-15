# Nucleo do e2e: registro das verificacoes, execucao das secoes, espera com prazo, resumo e JUnit.
# Carregado por dot-sourcing a partir do scripts/e2e.ps1 (as variaveis ficam no escopo dele).
# Arquivo so com ASCII: o Windows PowerShell 5.1 le scripts sem BOM na codificacao ANSI.

# Estados de uma verificacao
$EstadoOk = 'OK'
$EstadoFalhou = 'FALHOU'
$EstadoAviso = 'AVISO'
$EstadoPulado = 'PULADO'

$Resultados = New-Object System.Collections.Generic.List[object]
$ResumoSecoes = New-Object System.Collections.Generic.List[object]
$Execucao = @{ SecaoAtual = 'Pre-requisitos'; InicioCaso = [DateTime]::UtcNow }

function Write-Resultado([string]$Estado, [string]$Descricao, [string]$Detalhe) {
    $agora = [DateTime]::UtcNow
    $Resultados.Add([pscustomobject]@{
        Secao     = $Execucao.SecaoAtual
        Estado    = $Estado
        Descricao = $Descricao
        Detalhe   = $Detalhe
        Segundos  = [math]::Round(($agora - $Execucao.InicioCaso).TotalSeconds, 3)
    })
    $Execucao.InicioCaso = $agora
    $cor = @{ OK = 'Green'; FALHOU = 'Red'; AVISO = 'Yellow'; PULADO = 'DarkYellow' }[$Estado]
    Write-Host ('[' + $Estado + ']').PadRight(9) -ForegroundColor $cor -NoNewline
    Write-Host $Descricao
    if ($Detalhe) {
        Write-Host ('         ' + $Detalhe) -ForegroundColor DarkGray
    }
}

function Write-Info([string]$Texto) {
    Write-Host ('         ' + $Texto) -ForegroundColor DarkGray
}

# Registra a verificacao e segue em frente
function Test-Condicao([string]$Descricao, [bool]$Condicao, [string]$Detalhe) {
    if ($Condicao) { Write-Resultado $EstadoOk $Descricao $Detalhe } else { Write-Resultado $EstadoFalhou $Descricao $Detalhe }
}

# Registra a verificacao e, se falhar, interrompe a secao (os passos seguintes dependem dela)
function Assert-Condicao([string]$Descricao, [bool]$Condicao, [string]$Detalhe) {
    Test-Condicao $Descricao $Condicao $Detalhe
    if (-not $Condicao) {
        throw 'SECAO_INTERROMPIDA'
    }
}

# Comportamento diferente do ideal, mas conhecido e aceito por enquanto (nao conta como falha)
function Write-Aviso([string]$Descricao, [string]$Detalhe) {
    Write-Resultado $EstadoAviso $Descricao $Detalhe
}

function Write-Pulado([string]$Descricao, [string]$Detalhe) {
    Write-Resultado $EstadoPulado $Descricao $Detalhe
}

# Pula o restante da secao (ex.: Prometheus fora do ar)
function Skip-Secao([string]$Descricao, [string]$Motivo) {
    Write-Pulado $Descricao $Motivo
    throw 'SECAO_PULADA'
}

function Format-Valor($Valor) {
    if ($null -eq $Valor) { return 'null' }
    if ($Valor -is [DateTime]) { return $Valor.ToString('yyyy-MM-ddTHH:mm:ss') }
    if ($Valor -is [System.Array]) { return '[' + ((@($Valor) | ForEach-Object { Format-Valor $_ }) -join ', ') + ']' }
    return [string]$Valor
}

# Datas: o ConvertFrom-Json do PowerShell 7 transforma "2026-11-15" em DateTime; o do 5.1 deixa texto
function Format-Data($Valor) {
    if ($null -eq $Valor) { return $null }
    if ($Valor -is [DateTime]) { return $Valor.ToString('yyyy-MM-dd') }
    $texto = [string]$Valor
    if ($texto.Length -ge 10) { return $texto.Substring(0, 10) }
    return $texto
}

# Compara numeros pelo valor (75 = 75.00), booleanos como booleanos e o resto como texto
function Test-Valor([string]$Descricao, $Esperado, $Obtido) {
    $igual = $false
    if ($null -eq $Esperado) {
        $igual = ($null -eq $Obtido)
    } elseif ($null -ne $Obtido) {
        if ($Esperado -is [bool]) {
            $igual = ([string]$Obtido -eq [string]$Esperado)
        } elseif ($Esperado -is [ValueType]) {
            try { $igual = ([decimal]$Obtido -eq [decimal]$Esperado) } catch { $igual = $false }
        } else {
            $igual = ((Format-Valor $Obtido) -ceq [string]$Esperado)
        }
    }
    Test-Condicao $Descricao $igual ('esperado: ' + (Format-Valor $Esperado) + '; obtido: ' + (Format-Valor $Obtido))
}

# Espera uma condicao com polling e prazo (consistencia eventual: Kafka, replica, notificacoes).
# $Condicao devolve o valor esperado (qualquer coisa verdadeira) ou $null/$false para tentar de
# novo. Devolve Ok, Valor, Segundos e Detalhe ("apos 1.2 s (prazo 20 s)").
function Wait-Ate {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Condicao,
        [int]$PrazoSeg = 20,
        [int]$IntervaloMs = 500
    )
    # A condicao roda num escopo filho deste: os nomes locais tem prefixo para nao esconder as
    # variaveis de quem chamou (o PowerShell resolve variaveis pela pilha de chamadas)
    $__waCron = [System.Diagnostics.Stopwatch]::StartNew()
    $__waValor = $null
    while ($true) {
        try { $__waValor = & $Condicao } catch { $__waValor = $null }
        if ($__waValor) { break }
        if ($__waCron.Elapsed.TotalSeconds -ge $PrazoSeg) { break }
        Start-Sleep -Milliseconds $IntervaloMs
    }
    $__waSegundos = [math]::Round($__waCron.Elapsed.TotalSeconds, 1)
    return [pscustomobject]@{
        Ok       = [bool]$__waValor
        Valor    = $__waValor
        Segundos = $__waSegundos
        Detalhe  = 'apos ' + $__waSegundos + ' s (prazo ' + $PrazoSeg + ' s)'
    }
}

# Roda uma secao; uma falha interrompe so ela. Devolve $true se a secao chegou ao fim.
function Invoke-Secao([string]$Nome, [scriptblock]$Bloco) {
    Write-Host ''
    Write-Host ('=== ' + $Nome + ' ===') -ForegroundColor Cyan
    $Execucao.SecaoAtual = $Nome
    $Execucao.InicioCaso = [DateTime]::UtcNow
    $antes = $Resultados.Count
    $cron = [System.Diagnostics.Stopwatch]::StartNew()
    $completa = $false
    try {
        & $Bloco | Out-Null
        $completa = $true
    } catch {
        $mensagem = $_.Exception.Message
        if ($mensagem -eq 'SECAO_PULADA') {
            $completa = $true
        } else {
            if ($mensagem -ne 'SECAO_INTERROMPIDA') {
                $local = ''
                if ($_.InvocationInfo) { $local = ' (' + $_.InvocationInfo.ScriptName + ':' + $_.InvocationInfo.ScriptLineNumber + ')' }
                Write-Resultado $EstadoFalhou ("Erro inesperado na secao '" + $Nome + "'") ($mensagem + $local)
            }
            Write-Host ("         Secao '" + $Nome + "' interrompida.") -ForegroundColor Yellow
        }
    }
    $cron.Stop()
    $casos = @($Resultados | Select-Object -Skip $antes)
    $ResumoSecoes.Add([pscustomobject]@{
        Secao    = $Nome
        Total    = $casos.Count
        Ok       = @($casos | Where-Object { $_.Estado -eq $EstadoOk }).Count
        Falhas   = @($casos | Where-Object { $_.Estado -eq $EstadoFalhou }).Count
        Avisos   = @($casos | Where-Object { $_.Estado -eq $EstadoAviso }).Count
        Pulados  = @($casos | Where-Object { $_.Estado -eq $EstadoPulado }).Count
        Segundos = [math]::Round($cron.Elapsed.TotalSeconds, 1)
    })
    return $completa
}

function Write-ResumoFinal([double]$SegundosTotais) {
    Write-Host ''
    Write-Host '=== Resumo por secao ===' -ForegroundColor Cyan
    $linha = '{0,-44} {1,6} {2,6} {3,7} {4,7} {5,8} {6,8}'
    Write-Host ($linha -f 'Secao', 'Total', 'OK', 'Falhas', 'Avisos', 'Pulados', 'Tempo')
    foreach ($s in $ResumoSecoes) {
        $cor = 'Gray'
        if ($s.Falhas -gt 0) { $cor = 'Red' } elseif ($s.Avisos -gt 0 -or $s.Pulados -gt 0) { $cor = 'Yellow' }
        $nome = $s.Secao
        if ($nome.Length -gt 44) { $nome = $nome.Substring(0, 43) + '~' }
        Write-Host ($linha -f $nome, $s.Total, $s.Ok, $s.Falhas, $s.Avisos, $s.Pulados, ([string]$s.Segundos + ' s')) -ForegroundColor $cor
    }
    $total = $Resultados.Count
    $falhas = @($Resultados | Where-Object { $_.Estado -eq $EstadoFalhou })
    $avisos = @($Resultados | Where-Object { $_.Estado -eq $EstadoAviso })
    $pulados = @($Resultados | Where-Object { $_.Estado -eq $EstadoPulado })
    Write-Host ($linha -f 'TOTAL', $total, ($total - $falhas.Count - $avisos.Count - $pulados.Count), $falhas.Count,
        $avisos.Count, $pulados.Count, ([string][math]::Round($SegundosTotais, 1) + ' s'))

    foreach ($grupo in @(@{ Titulo = 'Falhas'; Itens = $falhas; Cor = 'Red' },
                         @{ Titulo = 'Avisos'; Itens = $avisos; Cor = 'Yellow' },
                         @{ Titulo = 'Pulados'; Itens = $pulados; Cor = 'DarkYellow' })) {
        if ($grupo.Itens.Count -gt 0) {
            Write-Host ''
            Write-Host ($grupo.Titulo + ':') -ForegroundColor $grupo.Cor
            foreach ($item in $grupo.Itens) {
                Write-Host ('  - [' + $item.Secao + '] ' + $item.Descricao) -ForegroundColor $grupo.Cor
            }
        }
    }
    Write-Host ''
    if ($falhas.Count -eq 0 -and $total -gt 0) {
        Write-Host ('Nenhuma falha em ' + $total + ' verificacoes.') -ForegroundColor Green
        return $true
    }
    Write-Host ($falhas.Count.ToString() + ' de ' + $total + ' verificacoes falharam.') -ForegroundColor Red
    return $false
}

# Texto seguro para XML 1.0 (remove caracteres de controle que o XML nao aceita)
function ConvertTo-TextoXml([string]$Texto) {
    if ($null -eq $Texto) { return '' }
    $limpo = [regex]::Replace($Texto, '[\x00-\x08\x0B\x0C\x0E-\x1F]', '')
    return [System.Security.SecurityElement]::Escape($limpo)
}

# Relatorio JUnit: uma testsuite por secao e um testcase por verificacao. AVISO vira um teste que
# passou com a mensagem em system-out; PULADO vira <skipped/>.
function Write-JUnit([string]$Caminho, [double]$SegundosTotais) {
    $inv = [System.Globalization.CultureInfo]::InvariantCulture
    $sb = New-Object System.Text.StringBuilder
    $falhasTotais = @($Resultados | Where-Object { $_.Estado -eq $EstadoFalhou }).Count
    $puladosTotais = @($Resultados | Where-Object { $_.Estado -eq $EstadoPulado }).Count
    [void]$sb.AppendLine('<?xml version="1.0" encoding="UTF-8"?>')
    [void]$sb.AppendLine(('<testsuites name="DistriSchool e2e" tests="{0}" failures="{1}" skipped="{2}" time="{3}">' -f
        $Resultados.Count, $falhasTotais, $puladosTotais, $SegundosTotais.ToString('0.###', $inv)))
    foreach ($s in $ResumoSecoes) {
        $casos = @($Resultados | Where-Object { $_.Secao -eq $s.Secao })
        [void]$sb.AppendLine(('  <testsuite name="{0}" tests="{1}" failures="{2}" skipped="{3}" time="{4}">' -f
            (ConvertTo-TextoXml $s.Secao), $casos.Count, $s.Falhas, $s.Pulados, ([double]$s.Segundos).ToString('0.###', $inv)))
        foreach ($c in $casos) {
            $nome = ConvertTo-TextoXml $c.Descricao
            $classe = ConvertTo-TextoXml ('e2e.' + $s.Secao)
            $tempo = ([double]$c.Segundos).ToString('0.###', $inv)
            $detalhe = ConvertTo-TextoXml $c.Detalhe
            [void]$sb.Append(('    <testcase classname="{0}" name="{1}" time="{2}"' -f $classe, $nome, $tempo))
            switch ($c.Estado) {
                'FALHOU' { [void]$sb.AppendLine('>'); [void]$sb.AppendLine(('      <failure message="{0}">{1}</failure>' -f $nome, $detalhe)); [void]$sb.AppendLine('    </testcase>') }
                'PULADO' { [void]$sb.AppendLine('>'); [void]$sb.AppendLine(('      <skipped message="{0}"/>' -f $detalhe)); [void]$sb.AppendLine('    </testcase>') }
                'AVISO'  { [void]$sb.AppendLine('>'); [void]$sb.AppendLine(('      <system-out>[AVISO] {0}</system-out>' -f $detalhe)); [void]$sb.AppendLine('    </testcase>') }
                default  { [void]$sb.AppendLine('/>') }
            }
        }
        [void]$sb.AppendLine('  </testsuite>')
    }
    [void]$sb.AppendLine('</testsuites>')
    [System.IO.File]::WriteAllText($Caminho, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
}
