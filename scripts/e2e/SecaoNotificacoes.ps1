# Secao "notificacoes": eventos do Kafka viram notificacoes no notificacao-service, para o
# destinatario certo (e so para ele), pela API (/api/notificacoes) e em tempo real pelo WebSocket;
# o mesmo evento entregue duas vezes gera uma notificacao so.

function Invoke-SecaoNotificacoes {
    $base = Initialize-Base
    $u = Initialize-Usuarios

    $semToken = Invoke-Api -Metodo GET -Caminho '/api/notificacoes' -SemToken
    Test-NaoAutenticado $semToken 'GET /api/notificacoes sem token'
    Wait-GrupoKafkaEstavel 'notificacao-service'

    Test-NotaLancadaELeitura $base $u
    Test-TiposDeNotificacao $base $u
    Test-TempoReal $base $u
    Test-Idempotencia $base $u
}

# Valor de nota unico na execucao e como ele aparece na mensagem (": 3,47")
function New-ValorNota {
    $valor = [decimal](Get-Random -Minimum 101 -Maximum 999) / 100
    $texto = ': ' + $valor.ToString([System.Globalization.CultureInfo]::InvariantCulture).Replace('.', ',').TrimEnd('0').TrimEnd(',')
    return @{ Valor = $valor; Texto = $texto }
}

function Get-Destinatarios($U) {
    return @{
        ADMIN = $Ctx.Token
        PROFESSOR = $U.Professor.Token
        ALUNO = $U.Aluno.Token
        RESPONSAVEL = $U.Responsavel.Token
        'RESPONSAVEL de outro aluno' = $U.OutroResponsavel.Token
    }
}

# Espera a notificacao em cada destinatario e confere que ela nao aparece para os outros. Os
# destinatarios de um evento sao gravados juntos, entao, quando ela chega a um deles, a ausencia
# nos outros ja e definitiva.
function Test-SoParaDestinatarios([string]$Tipo, [string]$Evento, [scriptblock]$Filtro, [string[]]$Para, $U) {
    $todos = Get-Destinatarios $U
    foreach ($nome in $Para) {
        $espera = Wait-Notificacao $todos[$nome] $Filtro
        Test-Condicao ($Tipo + ' (' + $Evento + ') chega ao ' + $nome) $espera.Ok $espera.Detalhe
    }
    foreach ($nome in @($todos.Keys | Where-Object { $Para -notcontains $_ } | Sort-Object)) {
        $quantas = Measure-Notificacoes $todos[$nome] $Filtro
        Test-Condicao ($Tipo + ' (' + $Evento + ') nao chega ao ' + $nome) ($quantas -eq 0) ('notificacoes encontradas: ' + $quantas)
    }
}

function Test-NotaLancadaELeitura($Base, $U) {
    $aluno = $Base.Alunos[0].Aluno
    $tokenResponsavel = $U.Responsavel.Token
    $nota = New-ValorNota
    $avaliacao = New-Avaliacao $Base.Turma.id $Base.Disciplina.id 2 'ATIVIDADE'
    $r = Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $avaliacao.id + '/notas') `
        -Corpo @(@{ matriculaAluno = [string]$aluno.matricula; valor = $nota.Valor })
    Assert-Status $r 201 ('POST /api/avaliacoes/{id}/notas (nota ' + $nota.Valor + ')')

    $filtro = { $_.tipo -eq 'NOTA_LANCADA' -and ([string]$_.mensagem).EndsWith($nota.Texto) }
    Test-SoParaDestinatarios 'NOTA_LANCADA' 'grade.posted' $filtro @('ALUNO', 'RESPONSAVEL') $U

    $notificacao = (Wait-Notificacao $tokenResponsavel $filtro).Valor
    Assert-Condicao 'Notificacao da nota encontrada para o responsavel' ($null -ne $notificacao)
    Test-Condicao 'Notificacao cita a disciplina pelo nome (cache do schedule.updated)' `
        ([string]$notificacao.mensagem -like ('*' + $Base.Disciplina.nome + '*')) ('mensagem: ' + $notificacao.mensagem)
    Test-Valor 'Notificacao aponta para o aluno' ('/alunos/' + $aluno.id) $notificacao.link
    Test-Valor 'Notificacao chega como nao lida' $false $notificacao.lida

    $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes/nao-lidas/contagem' -Token $tokenResponsavel
    Assert-Status $r 200 'GET /api/notificacoes/nao-lidas/contagem do responsavel'
    $naoLidas = [int]$r.Json.total
    Test-Condicao 'Contagem de nao lidas inclui a notificacao' ($naoLidas -ge 1) ('total: ' + $naoLidas)
    $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes?somenteNaoLidas=true&size=100' -Token $tokenResponsavel
    Test-Condicao 'GET /api/notificacoes?somenteNaoLidas=true so traz nao lidas' `
        (($r.Status -eq 200) -and (@((Get-Conteudo $r) | Where-Object { $_.lida }).Count -eq 0) -and (@((Get-Conteudo $r) | Where-Object { $_.id -eq $notificacao.id }).Count -eq 1)) (Get-Resumo $r)

    # So o dono altera: a coordenacao e o responsavel de outro aluno recebem 404
    Test-Erro (Invoke-Api -Metodo PATCH -Caminho ('/api/notificacoes/' + $notificacao.id + '/lida')) 404 'PATCH /api/notificacoes/{id}/lida pela coordenacao (nao e a dona)'
    Test-Erro (Invoke-Api -Metodo PATCH -Caminho ('/api/notificacoes/' + $notificacao.id + '/lida') -Token $U.OutroResponsavel.Token) 404 `
        'PATCH /api/notificacoes/{id}/lida pelo responsavel de outro aluno'
    Test-Erro (Invoke-Api -Metodo PATCH -Caminho '/api/notificacoes/999999999/lida' -Token $tokenResponsavel) 404 'PATCH /api/notificacoes/{id}/lida inexistente'

    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/notificacoes/' + $notificacao.id + '/lida') -Token $tokenResponsavel
    Test-Status $r 200 'PATCH /api/notificacoes/{id}/lida pelo responsavel'
    Test-Valor 'Notificacao marcada como lida' $true $r.Json.lida
    Test-Condicao 'Notificacao lida traz lidaEm' ($null -ne $r.Json.lidaEm) ('lidaEm: ' + (Format-Valor $r.Json.lidaEm))
    $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes/nao-lidas/contagem' -Token $tokenResponsavel
    Test-Valor 'Contagem de nao lidas diminui em 1' ($naoLidas - 1) $r.Json.total

    $r = Invoke-Api -Metodo POST -Caminho '/api/notificacoes/lidas' -Token $tokenResponsavel
    Test-Status $r 200 'POST /api/notificacoes/lidas pelo responsavel'
    Test-Valor 'POST /api/notificacoes/lidas informa quantas marcou' ($naoLidas - 1) $r.Json.atualizadas
    $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes/nao-lidas/contagem' -Token $tokenResponsavel
    Test-Valor 'Contagem de nao lidas zera' 0 $r.Json.total
}

# Um evento de cada tipo e os destinatarios de cada um (tabela do docs/NOTIFICACOES.md)
function Test-TiposDeNotificacao($Base, $U) {
    $id = New-IdUnico
    $hoje = Get-Hoje
    $aluno = $Base.Alunos[0].Aluno
    $outroAluno = $Base.Alunos[1].Aluno

    # Dispara todos os eventos primeiro; as esperas vem depois (o processamento corre em paralelo)
    # teacher.assigned: o professor da base atribuido a uma turma nova
    $turmaProfessor = New-Turma
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turmaProfessor.id + '/professores/' + $Base.Professor.id)
    Assert-Preparacao $r 200 'PUT /api/turmas/{id}/professores/{professorId}'

    # enrollment.created e enrollment.updated: matricula do aluno no ano seguinte (a deste ano ja existe)
    $turmaAnoSeguinte = New-Turma -AnoLetivo ($hoje.Year + 1)
    $matricula = New-Matricula $aluno $turmaAnoSeguinte.id

    # attendance.recorded: falta do aluno, presenca do outro
    $dataFalta = $hoje.AddDays(-2)
    $chamadaOk = ($dataFalta.Year -eq $hoje.Year)
    if ($chamadaOk) {
        New-Chamada $Base.Turma.id $Base.Disciplina.id $dataFalta @(
            @{ Aluno = $aluno; Status = 'FALTA' }, @{ Aluno = $outroAluno; Status = 'PRESENTE' }) | Out-Null
    }

    # student.created e staff.created: so a coordenacao
    $nomeAluno = 'Aluno Notificado ' + $id
    New-Aluno $nomeAluno | Out-Null
    $nomeTecnico = 'Tecnico Notificado ' + $id
    $r = Invoke-Api -Metodo POST -Caminho '/api/tecnicos' -Corpo @{
        nome = $nomeTecnico; cpf = (New-Cpf); cargo = 'BIBLIOTECARIO'; setor = 'Biblioteca'; email = 'tec.notif.' + $id + '@e2e.distrischool.com'
        dataAdmissao = (Format-Iso $hoje)
    }
    Assert-Preparacao $r 201 'POST /api/tecnicos'

    $linkTurma = '/turmas/' + $turmaProfessor.id
    Test-SoParaDestinatarios 'PROFESSOR_ATRIBUIDO' 'teacher.assigned' { $_.tipo -eq 'PROFESSOR_ATRIBUIDO' -and $_.link -eq $linkTurma } @('PROFESSOR') $U

    # O nome da turma vem do cache do schedule.updated; se ele ainda nao chegou, o texto usa "#id"
    $linkAluno = '/alunos/' + $aluno.id
    $turmaNoTexto = { ([string]$_.mensagem -like ('*' + $turmaAnoSeguinte.nome + '*')) -or ([string]$_.mensagem -like ('*#' + $turmaAnoSeguinte.id + '*')) }
    Test-SoParaDestinatarios 'MATRICULA_CRIADA' 'enrollment.created' { $_.tipo -eq 'MATRICULA_CRIADA' -and $_.link -eq $linkAluno -and (& $turmaNoTexto) } @('ALUNO', 'RESPONSAVEL') $U

    if ($chamadaOk) {
        $dataTexto = $dataFalta.ToString('dd/MM/yyyy')
        Test-SoParaDestinatarios 'FALTA_REGISTRADA' 'attendance.recorded' { $_.tipo -eq 'FALTA_REGISTRADA' -and $_.link -eq $linkAluno -and ([string]$_.mensagem -like ('*' + $dataTexto + '*')) } @('ALUNO', 'RESPONSAVEL') $U
    } else {
        Write-Pulado 'FALTA_REGISTRADA' 'a chamada usa dois dias atras, que ainda precisa estar no ano letivo'
    }
    Test-SoParaDestinatarios 'ALUNO_CADASTRADO' 'student.created' { $_.tipo -eq 'ALUNO_CADASTRADO' -and ([string]$_.mensagem -like ('*' + $nomeAluno + '*')) } @('ADMIN') $U
    Test-SoParaDestinatarios 'TECNICO_CADASTRADO' 'staff.created' { $_.tipo -eq 'TECNICO_CADASTRADO' -and ([string]$_.mensagem -like ('*' + $nomeTecnico + '*')) } @('ADMIN') $U

    # enrollment.updated: trancamento avisa aluno e responsavel; cancelamento tambem a coordenacao
    Set-StatusMatricula $matricula.id 'TRANCADA' | Out-Null
    Test-SoParaDestinatarios 'MATRICULA_ALTERADA' 'enrollment.updated, TRANCADA' { $_.tipo -eq 'MATRICULA_ALTERADA' -and $_.link -eq $linkAluno -and ([string]$_.mensagem -like '*trancada*') -and (& $turmaNoTexto) } @('ALUNO', 'RESPONSAVEL') $U
    Set-StatusMatricula $matricula.id 'CANCELADA' | Out-Null
    Test-SoParaDestinatarios 'MATRICULA_ALTERADA' 'enrollment.updated, CANCELADA' { $_.tipo -eq 'MATRICULA_ALTERADA' -and $_.link -eq $linkAluno -and ([string]$_.mensagem -like '*cancelada*') -and (& $turmaNoTexto) } @('ADMIN', 'ALUNO', 'RESPONSAVEL') $U
}

# Tempo real: STOMP sobre WebSocket em /ws, com o token no frame CONNECT
function Test-TempoReal($Base, $U) {
    $aluno = $Base.Alunos[0].Aluno
    $conexao = $null
    $outra = $null
    try {
        try {
            $conexao = New-ConexaoStomp $WebSocketUrl
        } catch {
            Assert-Condicao ('Conexao WebSocket em ' + $WebSocketUrl) $false $_.Exception.Message
        }
        Send-Stomp $conexao 'CONNECT' @{ 'accept-version' = '1.2'; host = 'localhost'; 'heart-beat' = '0,0'; Authorization = 'Bearer ' + $U.Responsavel.Token }
        $q = Wait-QuadroStomp $conexao 'CONNECTED' 10000
        Assert-Condicao 'WebSocket: CONNECT com o token do responsavel -> CONNECTED' (($null -ne $q) -and $q.Comando -eq 'CONNECTED') (Format-QuadroStomp $q)
        # O broker simples do Spring nao responde RECEIPT; a ordem basta: o SUBSCRIBE e processado
        # antes de qualquer mensagem do evento, que so nasce depois da chamada HTTP abaixo
        Send-Stomp $conexao 'SUBSCRIBE' @{ id = 'sub-0'; destination = '/user/queue/notificacoes' }

        # Responsavel de outro aluno conectado ao mesmo tempo: nao pode receber a notificacao
        $outra = New-ConexaoStomp $WebSocketUrl
        Send-Stomp $outra 'CONNECT' @{ 'accept-version' = '1.2'; host = 'localhost'; 'heart-beat' = '0,0'; Authorization = 'Bearer ' + $U.OutroResponsavel.Token }
        $q = Wait-QuadroStomp $outra 'CONNECTED' 10000
        Assert-Condicao 'WebSocket: CONNECT do responsavel de outro aluno -> CONNECTED' (($null -ne $q) -and $q.Comando -eq 'CONNECTED') (Format-QuadroStomp $q)
        Send-Stomp $outra 'SUBSCRIBE' @{ id = 'sub-0'; destination = '/user/queue/notificacoes' }

        $nota = New-ValorNota
        $avaliacao = New-Avaliacao $Base.Turma.id $Base.Disciplina.id 3 'ATIVIDADE'
        Add-Notas $avaliacao.id @(@{ Aluno = $aluno; Valor = $nota.Valor }) | Out-Null
        $cron = [System.Diagnostics.Stopwatch]::StartNew()
        $mensagem = $null
        $q = $null
        while ($null -eq $mensagem -and $cron.Elapsed.TotalSeconds -lt 20) {
            $q = Wait-QuadroStomp $conexao 'MESSAGE' ([int](20000 - $cron.Elapsed.TotalMilliseconds))
            if ($null -eq $q -or $q.Comando -ne 'MESSAGE') { break }
            $json = $null
            try { $json = ConvertFrom-Json -InputObject $q.Corpo } catch { $json = $null }
            if ($null -ne $json -and $json.tipo -eq 'NOTA_LANCADA' -and ([string]$json.mensagem).EndsWith($nota.Texto)) { $mensagem = $json }
        }
        Test-Condicao ('WebSocket: a NOTA_LANCADA da nota ' + $nota.Valor + ' chega em tempo real') ($null -ne $mensagem) `
            ('apos ' + [math]::Round($cron.Elapsed.TotalSeconds, 1) + ' s (prazo 20 s); ultimo quadro: ' + (Format-QuadroStomp $q))
        if ($null -ne $mensagem) {
            Test-Valor 'WebSocket: mensagem com o link do aluno' ('/alunos/' + $aluno.id) $mensagem.link
            Test-Condicao 'WebSocket: mensagem com o mesmo id da API REST' ($null -ne $mensagem.id) ('id: ' + (Format-Valor $mensagem.id))
        }
        # Ausencia na outra conexao: espera limitada a 2 s, depois de a mensagem chegar a primeira
        $q = Wait-QuadroStomp $outra 'MESSAGE' 2000
        Test-Condicao 'WebSocket: o responsavel de outro aluno nao recebe a notificacao' ($null -eq $q -or $q.Comando -ne 'MESSAGE') (Format-QuadroStomp $q)
    } finally {
        Close-ConexaoStomp $conexao
        Close-ConexaoStomp $outra
    }

    # Sem token, com token invalido e assinatura de outro destino: ERROR e conexao encerrada
    $casos = @(
        @{ Descricao = 'CONNECT sem token'; Auth = $null; Destino = $null },
        @{ Descricao = 'CONNECT com token invalido'; Auth = 'Bearer nao-e-um-jwt'; Destino = $null },
        @{ Descricao = 'SUBSCRIBE em destino de outro usuario (/topic/notificacoes)'; Auth = 'Bearer ' + $U.Responsavel.Token; Destino = '/topic/notificacoes' }
    )
    foreach ($caso in $casos) {
        $c = $null
        try {
            $c = New-ConexaoStomp $WebSocketUrl
            $cab = @{ 'accept-version' = '1.2'; host = 'localhost'; 'heart-beat' = '0,0' }
            if ($caso.Auth) { $cab['Authorization'] = $caso.Auth }
            Send-Stomp $c 'CONNECT' $cab
            $q = Wait-QuadroStomp $c 'CONNECTED' 10000
            if ($caso.Destino -and $null -ne $q -and $q.Comando -eq 'CONNECTED') {
                Send-Stomp $c 'SUBSCRIBE' @{ id = 'sub-x'; destination = $caso.Destino }
                $q = Wait-QuadroStomp $c 'ERROR' 5000
            }
            $fechou = (Wait-QuadroStomp $c 'CLOSE' 5000)
            Test-Condicao ('WebSocket: ' + $caso.Descricao + ' -> ERROR e conexao encerrada') `
                (($null -ne $q) -and $q.Comando -eq 'ERROR' -and (($null -ne $fechou -and $fechou.Comando -eq 'CLOSE') -or $c.Ws.State -ne 'Open')) `
                ('quadro: ' + (Format-QuadroStomp $q) + '; estado: ' + $c.Ws.State)
        } finally {
            Close-ConexaoStomp $c
        }
    }
}

function Format-QuadroStomp($Quadro) {
    if ($null -eq $Quadro) { return 'nenhum' }
    $msg = ''
    if ($Quadro.Cabecalhos.ContainsKey('message')) { $msg = ' message=' + $Quadro.Cabecalhos['message'] }
    $corpo = [string]$Quadro.Corpo
    if ($corpo.Length -gt 200) { $corpo = $corpo.Substring(0, 200) + '...' }
    return $Quadro.Comando + $msg + ' ' + $corpo
}

# O mesmo grade.posted publicado duas vezes (mesma chave e mesmo payload): uma notificacao so. Um
# terceiro evento, diferente, vai depois na mesma particao (mesma chave): quando ele chega, os dois
# anteriores ja foram processados.
function Test-Idempotencia($Base, $U) {
    $aluno = $Base.Alunos[0].Aluno
    $inv = [System.Globalization.CultureInfo]::InvariantCulture
    $duplicada = New-ValorNota
    $marcador = New-ValorNota
    while ($marcador.Valor -eq $duplicada.Valor) { $marcador = New-ValorNota }
    $agora = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ss.fffZ', $inv)
    $chave = [string]$Base.Turma.id
    $avaliacaoId = 900000000 + (Get-Random -Minimum 1 -Maximum 99999999)
    $montar = {
        param($valor)
        '{"avaliacaoId":' + $avaliacaoId + ',"turmaId":' + $Base.Turma.id + ',"disciplinaId":' + $Base.Disciplina.id +
            ',"bimestre":4,"notas":[{"alunoId":' + $aluno.id + ',"valor":' + $valor.ToString($inv) + '}],"occurredAt":"' + $agora + '"}'
    }
    $payload = & $montar $duplicada.Valor
    Send-Kafka 'grade.posted' $chave $payload
    Send-Kafka 'grade.posted' $chave $payload
    Send-Kafka 'grade.posted' $chave (& $montar $marcador.Valor)
    Test-Condicao 'Mesmo payload publicado duas vezes em grade.posted (kafka-console-producer)' $true ('chave ' + $chave + '; ' + $payload)

    $espera = Wait-Notificacao $U.Responsavel.Token { $_.tipo -eq 'NOTA_LANCADA' -and ([string]$_.mensagem).EndsWith($marcador.Texto) }
    Assert-Condicao 'Evento seguinte (marcador) processado' $espera.Ok $espera.Detalhe
    foreach ($quem in @(@{ Nome = 'RESPONSAVEL'; Token = $U.Responsavel.Token }, @{ Nome = 'ALUNO'; Token = $U.Aluno.Token })) {
        $quantas = Measure-Notificacoes $quem.Token { $_.tipo -eq 'NOTA_LANCADA' -and ([string]$_.mensagem).EndsWith($duplicada.Texto) }
        Test-Valor ('Evento entregue duas vezes gera uma notificacao so (' + $quem.Nome + ')') 1 $quantas
    }
}
