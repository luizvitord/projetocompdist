# Secoes de resiliencia. As que param containers ficam marcadas no catalogo do e2e.ps1 e sao
# puladas com -PularResiliencia. Toda secao que para um servico o religa no finally e espera o
# healthcheck; o finally do e2e.ps1 ainda religa tudo no fim (tambem em Ctrl+C).

# Kafka parado: a criacao de turma responde rapido e o evento fica pendente em outbox_evento; com
# o Kafka de volta, o evento e publicado e a turma chega a turma_replica do gestao-escolar-service.
# Aqui a replica e conferida no banco: qualquer chamada do gestao-escolar sobre a turma acionaria o
# fallback sincrono e mascararia o teste.
function Invoke-SecaoOutbox {
    Initialize-Admin
    $s = New-IdUnico
    $ano = Get-AnoLetivo

    Stop-Servico 'kafka'
    try {
        $r = Invoke-Api -Metodo POST -Caminho '/api/turmas' -TimeoutSec 30 -Corpo @{
            codigo = 'T-OUT-' + $s; nome = 'Turma Outbox ' + $s; anoLetivo = $ano; turno = 'TARDE'; capacidade = 20
        }
        Assert-Status $r 201 'POST /api/turmas com o Kafka parado'
        Test-Condicao 'Resposta em menos de 5 s com o Kafka parado' ($r.Ms -lt 5000) ($r.Ms.ToString() + ' ms')
        $turmaId = $r.Json.id
        Assert-Condicao 'Turma criada com id' ($null -ne $turmaId) ('id: ' + (Format-Valor $turmaId))

        $filtro = "FROM outbox_evento WHERE topico = 'schedule.updated' AND chave = '" + $turmaId + "'"
        $pendentes = Invoke-PsqlAdministrativa ('SELECT count(*) ' + $filtro + ' AND publicado_em IS NULL')
        Test-Condicao 'Evento schedule.updated pendente em outbox_evento (publicado_em vazio)' ([int]$pendentes -ge 1) `
            ('pendentes: ' + $pendentes)

        $naReplica = Invoke-PsqlEscolar ('SELECT count(*) FROM turma_replica WHERE id = ' + $turmaId)
        Test-Valor 'Turma ainda ausente de turma_replica com o Kafka parado' '0' $naReplica
    } finally {
        Start-Servico 'kafka'
    }

    $sqlPublicado = 'SELECT bool_and(publicado_em IS NOT NULL) ' + $filtro
    $espera = Wait-Ate -PrazoSeg 90 -IntervaloMs 2000 -Condicao { (Invoke-PsqlAdministrativa $sqlPublicado) -eq 't' }
    Test-Condicao 'Evento publicado depois de religar o Kafka (publicado_em preenchido)' $espera.Ok $espera.Detalhe

    $sqlReplica = 'SELECT count(*) FROM turma_replica WHERE id = ' + $turmaId + ' AND ultimo_evento_em IS NOT NULL'
    $espera = Wait-Ate -PrazoSeg 90 -IntervaloMs 2000 -Condicao { (Invoke-PsqlEscolar $sqlReplica) -eq '1' }
    Test-Condicao 'Turma aplicada em turma_replica pelo consumidor do schedule.updated' $espera.Ok $espera.Detalhe

    # As proximas secoes dependem dos consumidores de volta ao grupo
    Wait-GrupoKafkaEstavel 'notificacao-service'
}

# gestao-administrativa-service parado: a replica de turmas do gestao-escolar-service mantem
# matriculas e chamadas funcionando nas turmas ja replicadas; uma turma nunca replicada depende do
# fallback sincrono, que responde 503 (retry + circuit breaker).
function Invoke-SecaoResilienciaAdministrativa {
    Initialize-Admin
    $cenario = New-TurmaComAlunos 1
    $turma = $cenario.Turma
    $novo = New-Aluno
    $outro = New-Aluno

    Stop-Servico 'gestao-administrativa-service'
    try {
        $r = Invoke-Api -Metodo GET -Caminho '/api/turmas?size=1' -TimeoutSec 60
        Test-Erro $r 503 'GET /api/turmas com o gestao-administrativa-service parado (gateway)'

        $r = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -TimeoutSec 60 -Corpo @{ matriculaAluno = [string]$novo.matricula; turmaId = $turma.id }
        Test-Status $r 201 'Matricula em turma ja replicada, com o gestao-administrativa-service parado'
        $r = Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -TimeoutSec 60 -Corpo @{
            turmaId = $turma.id; disciplinaId = $cenario.Disciplina.id; data = (Format-Iso (Get-Hoje))
            registros = @(@{ matriculaAluno = [string]$cenario.Alunos[0].Aluno.matricula; status = 'PRESENTE' }, @{ matriculaAluno = [string]$novo.matricula; status = 'FALTA' })
        }
        Test-Status $r 201 'Chamada em turma ja replicada, com o gestao-administrativa-service parado'

        $r = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -TimeoutSec 60 -Corpo @{ matriculaAluno = [string]$outro.matricula; turmaId = 999999999 }
        Test-Erro $r 503 'Matricula em turma nunca replicada (fallback sincrono indisponivel)'
        $r = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -TimeoutSec 60 -Corpo @{ matriculaAluno = [string]$outro.matricula; turmaId = 999999998 }
        Test-Erro $r 503 'Segunda tentativa em turma nunca replicada (circuito do fallback)'
        Write-Info ('segunda tentativa respondeu em ' + $r.Ms + ' ms')
    } finally {
        Start-Servico 'gestao-administrativa-service'
    }
    Wait-ContainerPronto 'gestao-administrativa-service'

    # O gateway e o fallback do gestao-escolar voltam quando os circuitos fecham. Sonda sem efeito:
    # turma inexistente responde 404 (o fallback chegou ao gestao-administrativa) em vez de 503.
    $espera = Wait-Ate -PrazoSeg 90 -IntervaloMs 2000 -Condicao { (Invoke-Api -Metodo GET -Caminho '/api/turmas?size=1' -TimeoutSec 20).Status -eq 200 }
    Test-Condicao 'GET /api/turmas volta a responder 200 depois de religar o servico' $espera.Ok $espera.Detalhe
    $espera = Wait-Ate -PrazoSeg 90 -IntervaloMs 2000 -Condicao {
        (Invoke-Api -Metodo POST -Caminho '/api/matriculas' -TimeoutSec 20 -Corpo @{ matriculaAluno = [string]$outro.matricula; turmaId = 999999999 }).Status -eq 404
    }
    Test-Condicao 'Fallback do gestao-escolar volta a consultar o gestao-administrativa (turma inexistente -> 404)' $espera.Ok $espera.Detalhe
}

# notificacao-service parado: o resto do sistema responde normalmente e os eventos ficam no Kafka;
# ao religar, as notificacoes dos eventos do intervalo aparecem
function Invoke-SecaoResilienciaNotificacao {
    $base = Initialize-Base
    $u = Initialize-Usuarios
    $aluno = $base.Alunos[0].Aluno
    $nota = New-ValorNota

    Stop-Servico 'notificacao-service'
    try {
        $r = Invoke-Api -Metodo GET -Caminho '/api/alunos?size=1'
        Test-Status $r 200 'GET /api/alunos com o notificacao-service parado'
        $avaliacao = New-Avaliacao $base.Turma.id $base.Disciplina.id 1 'ATIVIDADE'
        $r = Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $avaliacao.id + '/notas') -Corpo @(@{ matriculaAluno = [string]$aluno.matricula; valor = $nota.Valor })
        Test-Status $r 201 ('Lancamento de nota com o notificacao-service parado (nota ' + $nota.Valor + ')')
        $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $aluno.id + '/boletim?anoLetivo=' + (Get-AnoLetivo))
        Test-Status $r 200 'Boletim com o notificacao-service parado'
        $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes' -Token $u.Responsavel.Token -TimeoutSec 60
        Test-Erro $r 503 'GET /api/notificacoes com o notificacao-service parado (gateway)'
    } finally {
        Start-Servico 'notificacao-service'
    }
    Wait-ContainerPronto 'notificacao-service'
    Wait-GrupoKafkaEstavel 'notificacao-service'
    $espera = Wait-Notificacao $u.Responsavel.Token { $_.tipo -eq 'NOTA_LANCADA' -and ([string]$_.mensagem).EndsWith($nota.Texto) } -PrazoSeg 90
    Test-Condicao 'Notificacao da nota lancada durante a parada aparece depois de religar o servico' $espera.Ok $espera.Detalhe
}

# Dead Letter Topic: um schedule.updated fora do contrato vai para schedule.updated.DLT e nao trava
# os consumidores. O evento invalido usa a mesma chave da turma, entao cai na mesma particao do
# evento valido seguinte; se o consumidor travasse nele, o seguinte nunca seria aplicado.
function Invoke-SecaoDlt {
    Initialize-Admin
    $turma = New-Turma
    $aluno = New-Aluno
    $matricula = New-Matricula $aluno $turma.id
    $marca = 'e2e-dlt-' + (New-IdUnico)

    Send-Kafka 'schedule.updated' ([string]$turma.id) ('{"marca":"' + $marca + '","turmaId":' + $turma.id + ',"quebrado":')
    Test-Condicao 'schedule.updated invalido publicado (kafka-console-producer)' $true ('chave ' + $turma.id + '; marca ' + $marca)

    $novoCodigo = 'TD-' + (New-IdUnico)
    $novoNome = 'Turma DLT ' + $novoCodigo
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id) -Corpo @{ codigo = $novoCodigo; nome = $novoNome; anoLetivo = $turma.anoLetivo; turno = $turma.turno; capacidade = 40 }
    Assert-Status $r 200 'PUT /api/turmas/{id} logo depois do evento invalido (mesma particao)'

    $espera = Wait-Ate -Condicao {
        $b = Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $aluno.id + '/boletim?anoLetivo=' + $turma.anoLetivo)
        ($b.Status -eq 200) -and (@($b.Json.matriculas)[0].turmaCodigo -eq $novoCodigo)
    }
    Test-Condicao 'gestao-escolar-service continua consumindo: o evento seguinte atualiza a replica' $espera.Ok $espera.Detalhe

    # notificacao-service: o nome novo da turma (cache do schedule.updated) aparece no texto do
    # cancelamento, que a coordenacao recebe
    Set-StatusMatricula $matricula.id 'CANCELADA' | Out-Null
    $espera = Wait-Notificacao $Ctx.Token { $_.tipo -eq 'MATRICULA_ALTERADA' -and ([string]$_.mensagem -like ('*#' + $aluno.id + ' *')) }
    Test-Condicao 'Cancelamento da matricula chega a coordenacao' $espera.Ok $espera.Detalhe
    if ($espera.Ok) {
        Test-Condicao 'notificacao-service continua consumindo: o texto usa o nome novo da turma' ([string]$espera.Valor.mensagem -like ('*' + $novoNome + '*')) `
            ('mensagem: ' + $espera.Valor.mensagem)
    }

    # Os dois consumidores ja passaram do evento invalido (acima), entao ja o publicaram no DLT:
    # uma copia de cada grupo (gestao-escolar-service e notificacao-service), os dois usam
    # schedule.updated.DLT. O kafka-console-consumer leva alguns segundos para entrar no topico.
    $espera = Wait-Ate -PrazoSeg 40 -IntervaloMs 1000 -Condicao {
        $linhas = Read-Kafka 'schedule.updated.DLT' 10000
        $achadas = @($linhas | Where-Object { $_ -like ('*' + $marca + '*') })
        if ($achadas.Count -ge 2) { return $achadas.Count }
        return $null
    }
    Test-Condicao 'Evento invalido chega ao schedule.updated.DLT (uma copia de cada consumidor)' ($espera.Ok -and $espera.Valor -eq 2) `
        ($espera.Detalhe + '; copias no DLT: ' + (Format-Valor $espera.Valor))
}

# gestao-escolar-service parado: /api/alunos responde 503 com ProblemDetail (circuit breaker do
# gateway) e o resto do gateway continua respondendo
function Invoke-SecaoCircuitBreaker {
    Initialize-Admin
    Stop-Servico 'gestao-escolar-service'
    try {
        $r = Invoke-Api -Metodo GET -Caminho '/api/alunos' -TimeoutSec 60
        Test-Erro $r 503 'GET /api/alunos com o gestao-escolar-service parado'

        $r = Invoke-Api -Metodo GET -Caminho '/api/turmas?size=1'
        Test-Status $r 200 'GET /api/turmas continua respondendo'

        $r = Invoke-Login $EmailCoordenacao $SenhaCoordenacao
        Test-Status $r 200 'POST /api/auth/login continua respondendo'
    } finally {
        Start-Servico 'gestao-escolar-service'
    }

    # Espera o healthcheck do container antes de chamar o gateway: falhas seguidas durante a subida
    # abririam o circuito e atrasariam a volta em 30 s
    Wait-ContainerPronto 'gestao-escolar-service'
    $espera = Wait-Ate -PrazoSeg 60 -IntervaloMs 2000 -Condicao {
        (Invoke-Api -Metodo GET -Caminho '/api/alunos?size=1' -TimeoutSec 20).Status -eq 200
    }
    Test-Condicao 'GET /api/alunos volta a responder 200 depois de religar o servico' $espera.Ok $espera.Detalhe
}
