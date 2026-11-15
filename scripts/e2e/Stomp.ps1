# Cliente STOMP minimo sobre System.Net.WebSockets.ClientWebSocket, para o WebSocket de
# notificacoes (/ws). Funciona no Windows PowerShell 5.1 (.NET Framework) e no PowerShell 7.
#
# Dois cuidados por causa do .NET Framework (5.1):
#   - Cancelar um ReceiveAsync pendente aborta o socket. Por isso ha no maximo uma leitura pendente
#     por conexao, e a espera usa Task.Wait(timeout) sem cancelar a leitura.
#   - O ClientWebSocket do .NET Framework recusa a resposta do handshake do Tomcat
#     ("Connection: upgrade, keep-alive"). A conexao vai pelo nginx do frontend (/ws), que e o
#     caminho documentado para o navegador; o nginx responde "Connection: upgrade".

$StompNulo = [string][char]0

function New-ConexaoStomp([string]$Url) {
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter(10000)
    # No 5.1, GetResult() de uma Task sem valor devolve um VoidTaskResult, que viraria saida da funcao
    $ws.ConnectAsync([Uri]$Url, $cts.Token).GetAwaiter().GetResult() | Out-Null
    return @{
        Ws       = $ws
        Buffer   = New-Object byte[] 65536
        Pendente = $null
        Parcial  = New-Object System.IO.MemoryStream
    }
}

function Send-Stomp($Conexao, [string]$Comando, [hashtable]$Cabecalhos = @{}, [string]$Corpo = '') {
    $texto = $Comando + "`n"
    foreach ($k in $Cabecalhos.Keys) { $texto += $k + ':' + $Cabecalhos[$k] + "`n" }
    $texto += "`n" + $Corpo + $StompNulo
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($texto)
    $segmento = New-Object System.ArraySegment[byte] -ArgumentList @(, $bytes)
    $Conexao.Ws.SendAsync($segmento, [System.Net.WebSockets.WebSocketMessageType]::Text, $true,
        [System.Threading.CancellationToken]::None).GetAwaiter().GetResult() | Out-Null
}

# Proximo frame STOMP, ou $null se nada chegar em $TimeoutMs. Heartbeats ("\n") sao ignorados.
# Devolve @{ Comando; Cabecalhos; Corpo } ou @{ Comando = 'CLOSE'; ... } se o servidor fechou.
function Receive-Stomp($Conexao, [int]$TimeoutMs = 5000) {
    $limite = (Get-Date).AddMilliseconds($TimeoutMs)
    while ($true) {
        if ($Conexao.Ws.State -ne [System.Net.WebSockets.WebSocketState]::Open -and $null -eq $Conexao.Pendente) {
            return @{ Comando = 'CLOSE'; Cabecalhos = @{}; Corpo = [string]$Conexao.Ws.CloseStatusDescription }
        }
        if ($null -eq $Conexao.Pendente) {
            $segmento = New-Object System.ArraySegment[byte] -ArgumentList @(, $Conexao.Buffer)
            $Conexao.Pendente = $Conexao.Ws.ReceiveAsync($segmento, [System.Threading.CancellationToken]::None)
        }
        $restante = [int][math]::Max(0, ($limite - (Get-Date)).TotalMilliseconds)
        $terminou = $false
        try { $terminou = $Conexao.Pendente.Wait($restante) } catch { $terminou = $true }
        if (-not $terminou) { return $null }
        $tarefa = $Conexao.Pendente
        $Conexao.Pendente = $null
        if ($tarefa.IsFaulted -or $tarefa.IsCanceled) {
            return @{ Comando = 'CLOSE'; Cabecalhos = @{}; Corpo = 'leitura falhou: ' + $tarefa.Exception.InnerException.Message }
        }
        $resultado = $tarefa.Result
        if ($resultado.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
            return @{ Comando = 'CLOSE'; Cabecalhos = @{}; Corpo = [string]$Conexao.Ws.CloseStatusDescription }
        }
        $Conexao.Parcial.Write($Conexao.Buffer, 0, $resultado.Count)
        if (-not $resultado.EndOfMessage) { continue }
        $texto = [System.Text.Encoding]::UTF8.GetString($Conexao.Parcial.ToArray())
        $Conexao.Parcial.SetLength(0)
        $quadro = ConvertFrom-QuadroStomp $texto
        if ($null -ne $quadro) { return $quadro }
        if ((Get-Date) -ge $limite) { return $null }
    }
}

function ConvertFrom-QuadroStomp([string]$Texto) {
    $texto = $Texto.TrimStart("`r", "`n")
    if (-not $texto) { return $null }
    $fimCabecalho = $texto.IndexOf("`n`n")
    if ($fimCabecalho -lt 0) { $fimCabecalho = $texto.Length }
    $linhas = $texto.Substring(0, $fimCabecalho).Split("`n")
    $cabecalhos = @{}
    foreach ($linha in @($linhas | Select-Object -Skip 1)) {
        $i = $linha.IndexOf(':')
        if ($i -gt 0 -and -not $cabecalhos.ContainsKey($linha.Substring(0, $i))) {
            $cabecalhos[$linha.Substring(0, $i)] = $linha.Substring($i + 1)
        }
    }
    $corpo = ''
    if ($fimCabecalho + 2 -le $texto.Length) { $corpo = $texto.Substring([math]::Min($texto.Length, $fimCabecalho + 2)).TrimEnd([char]0) }
    return @{ Comando = $linhas[0].Trim(); Cabecalhos = $cabecalhos; Corpo = $corpo }
}

# Espera um frame com o comando pedido (descarta os outros), ate $TimeoutMs
function Wait-QuadroStomp($Conexao, [string]$Comando, [int]$TimeoutMs = 5000) {
    $limite = (Get-Date).AddMilliseconds($TimeoutMs)
    while ((Get-Date) -lt $limite) {
        $restante = [int][math]::Max(1, ($limite - (Get-Date)).TotalMilliseconds)
        $quadro = Receive-Stomp $Conexao $restante
        if ($null -eq $quadro) { return $null }
        if ($quadro.Comando -eq $Comando -or $quadro.Comando -eq 'CLOSE' -or $quadro.Comando -eq 'ERROR') { return $quadro }
    }
    return $null
}

function Close-ConexaoStomp($Conexao) {
    if ($null -eq $Conexao) { return }
    try {
        if ($Conexao.Ws.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            $cts = New-Object System.Threading.CancellationTokenSource
            $cts.CancelAfter(2000)
            $Conexao.Ws.CloseOutputAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'fim', $cts.Token).GetAwaiter().GetResult() | Out-Null
        }
    } catch { }
    try { $Conexao.Ws.Dispose() } catch { }
}
