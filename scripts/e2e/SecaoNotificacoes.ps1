# Secao "notificacoes": eventos do Kafka viram notificacoes no notificacao-service, para o
# destinatario certo, pela API (/api/notificacoes) e em tempo real pelo WebSocket.

function Invoke-SecaoNotificacoes {
    $base = Initialize-Base
    $u = Initialize-Usuarios
    $aluno = $base.Alunos[0].Aluno
    $tokenResponsavel = $u.Responsavel.Token

    $semToken = Invoke-Api -Metodo GET -Caminho '/api/notificacoes' -SemToken
    Test-NaoAutenticado $semToken 'GET /api/notificacoes sem token'

    Wait-GrupoKafkaEstavel 'notificacao-service'

    # Nova nota com valor unico na execucao (a mensagem termina com a nota no formato brasileiro)
    $valor = [decimal](Get-Random -Minimum 101 -Maximum 999) / 100
    $texto = ': ' + $valor.ToString([System.Globalization.CultureInfo]::InvariantCulture).Replace('.', ',').TrimEnd('0').TrimEnd(',')
    $avaliacao = New-Avaliacao $base.Turma.id $base.Disciplina.id 2 'ATIVIDADE'
    $r = Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $avaliacao.id + '/notas') `
        -Corpo @(@{ matriculaAluno = [string]$aluno.matricula; valor = $valor })
    Assert-Status $r 201 ('POST /api/avaliacoes/{id}/notas (nota ' + $valor + ')')

    # grade.posted -> outbox -> Kafka -> notificacao-service: assincrono
    $espera = Wait-Notificacao $tokenResponsavel { $_.tipo -eq 'NOTA_LANCADA' -and ([string]$_.mensagem).EndsWith($texto) }
    Assert-Condicao ('GET /api/notificacoes do responsavel traz a NOTA_LANCADA da nota ' + $valor) $espera.Ok $espera.Detalhe
    $notificacao = $espera.Valor
    Test-Condicao 'Notificacao cita a disciplina pelo nome (cache do schedule.updated)' `
        ([string]$notificacao.mensagem -like ('*' + $base.Disciplina.nome + '*')) ('mensagem: ' + $notificacao.mensagem)
    Test-Valor 'Notificacao aponta para o aluno' ('/alunos/' + $aluno.id) $notificacao.link
    Test-Valor 'Notificacao chega como nao lida' $false $notificacao.lida

    $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes/nao-lidas/contagem' -Token $tokenResponsavel
    Assert-Status $r 200 'GET /api/notificacoes/nao-lidas/contagem do responsavel'
    $naoLidas = [int]$r.Json.total
    Test-Condicao 'Contagem de nao lidas inclui a notificacao' ($naoLidas -ge 1) ('total: ' + $naoLidas)

    # So o dono: a coordenacao nao ve nem altera a notificacao do responsavel
    $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes?size=100'
    Test-Status $r 200 'GET /api/notificacoes da coordenacao'
    $vazou = @((Get-Conteudo $r) | Where-Object { $_.id -eq $notificacao.id })
    Test-Condicao 'Lista da coordenacao nao traz a notificacao do responsavel' ($vazou.Count -eq 0) `
        ('notificacoes na lista da coordenacao: ' + @(Get-Conteudo $r).Count)
    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/notificacoes/' + $notificacao.id + '/lida')
    Test-Erro $r 404 'PATCH /api/notificacoes/{id}/lida pela coordenacao (nao e a dona)'

    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/notificacoes/' + $notificacao.id + '/lida') -Token $tokenResponsavel
    Test-Status $r 200 'PATCH /api/notificacoes/{id}/lida pelo responsavel'
    Test-Valor 'Notificacao marcada como lida' $true $r.Json.lida

    $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes/nao-lidas/contagem' -Token $tokenResponsavel
    Test-Valor 'Contagem de nao lidas diminui em 1' ($naoLidas - 1) $r.Json.total

    $r = Invoke-Api -Metodo POST -Caminho '/api/notificacoes/lidas' -Token $tokenResponsavel
    Test-Status $r 200 'POST /api/notificacoes/lidas pelo responsavel'
    $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes/nao-lidas/contagem' -Token $tokenResponsavel
    Test-Valor 'Contagem de nao lidas zera' 0 $r.Json.total
}
