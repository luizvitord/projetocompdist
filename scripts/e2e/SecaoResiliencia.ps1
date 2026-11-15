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
