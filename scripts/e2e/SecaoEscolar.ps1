# Secoes do gestao-escolar-service: alunos, matriculas, frequencia, avaliacoes e notas, boletim.
# Regras esperadas: gestao-escolar-service/CLAUDE.md. Os payloads usam o numero de matricula do
# aluno (matriculaAluno), como a API atual.

# Turma do ano com uma disciplina ofertada e os alunos pedidos matriculados (e a oferta ja na replica)
function New-TurmaComAlunos([int]$QuantidadeAlunos, [int]$Capacidade = 40, [int]$Disciplinas = 1) {
    $turma = New-Turma -Capacidade $Capacidade
    $listaDisciplinas = @()
    foreach ($n in 1..$Disciplinas) {
        $d = New-Disciplina
        Set-Oferta $turma.id $d.id | Out-Null
        $listaDisciplinas += $d
    }
    $alunos = @()
    foreach ($n in 1..$QuantidadeAlunos) {
        $aluno = New-Aluno
        $matricula = New-Matricula $aluno $turma.id
        $alunos += @{ Aluno = $aluno; Matricula = $matricula }
    }
    foreach ($d in $listaDisciplinas) {
        Wait-OfertaNoEscolar $alunos[0].Aluno.id $d.id ('Preparacao: oferta de ' + $d.codigo + ' replicada no gestao-escolar-service')
    }
    return @{ Turma = $turma; Disciplinas = $listaDisciplinas; Disciplina = $listaDisciplinas[0]; Alunos = $alunos }
}

function Find-PorId($Lista, $Id) {
    return (@($Lista) | Where-Object { [string]$_.id -eq [string]$Id } | Select-Object -First 1)
}

# =============================================================================================
# Alunos
# =============================================================================================

function Invoke-SecaoAlunos {
    Initialize-Admin
    $ano = Get-AnoLetivo
    $id = New-IdUnico
    $nome = 'Beatriz Nogueira ' + $id
    $r = Invoke-Api -Metodo POST -Caminho '/api/alunos' -Corpo @{
        nome = $nome; dataNascimento = '2011-03-15'; endereco = 'Rua A, 10'; telefone = '11977776666'; email = 'bia.' + $id + '@e2e.distrischool.com'
    }
    Assert-Status $r 201 'POST /api/alunos'
    $aluno = $r.Json
    Test-Condicao 'Location aponta para o aluno criado' ([string]$r.Headers['Location'] -like ('*/api/alunos/' + $aluno.id)) ('Location: ' + (Format-Valor $r.Headers['Location']))
    Test-Condicao 'Numero de matricula gerado no formato ano + 6 digitos' ([string]$aluno.matricula -match ('^' + $ano + '\d{6}$')) ('matricula: ' + $aluno.matricula)
    $segundo = New-Aluno
    Test-Condicao 'Numeros de matricula seguem a sequencia (o seguinte e maior)' ([long]$segundo.matricula -gt [long]$aluno.matricula) `
        ($aluno.matricula + ' -> ' + $segundo.matricula)

    $amanha = Format-Iso ((Get-Hoje).AddDays(1))
    $r = Invoke-Api -Metodo POST -Caminho '/api/alunos' -Corpo @{ nome = 'Futuro'; dataNascimento = $amanha }
    Test-ErroValidacao $r @('dataNascimento') 'POST /api/alunos com dataNascimento futura'
    $r = Invoke-Api -Metodo POST -Caminho '/api/alunos' -Corpo @{ nome = ''; email = 'nao-e-email'; telefone = ('9' * 31) }
    Test-ErroValidacao $r @('nome', 'dataNascimento', 'email', 'telefone') 'POST /api/alunos sem nome e data, com email invalido e telefone longo'

    $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $aluno.id)
    Test-Status $r 200 'GET /api/alunos/{id}'
    Test-Valor 'GET /api/alunos/{id} devolve o endereco' 'Rua A, 10' $r.Json.endereco
    Test-Erro (Invoke-Api -Metodo GET -Caminho '/api/alunos/999999999') 404 'GET /api/alunos/{id} inexistente'

    $r = Invoke-Api -Metodo PUT -Caminho ('/api/alunos/' + $aluno.id) -Corpo @{ nome = $nome + ' Silva'; dataNascimento = '2011-03-15'; endereco = 'Rua B, 20' }
    Test-Status $r 200 'PUT /api/alunos/{id}'
    Test-Valor 'Aluno editado: nome' ($nome + ' Silva') $r.Json.nome
    Test-Valor 'Numero de matricula nao muda na edicao' $aluno.matricula $r.Json.matricula
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/alunos/' + $aluno.id) -Corpo @{ nome = 'x'; dataNascimento = $amanha }
    Test-ErroValidacao $r @('dataNascimento') 'PUT /api/alunos/{id} com dataNascimento futura'
    Test-Erro (Invoke-Api -Metodo PUT -Caminho '/api/alunos/999999999' -Corpo @{ nome = 'x'; dataNascimento = '2011-01-01' }) 404 'PUT /api/alunos/{id} inexistente'

    # Filtros: nome (parte, sem diferenciar maiusculas), numero de matricula (exato) e turmaId
    # (so matriculas ATIVA)
    $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos?nome=' + ('beatriz nogueira ' + $id).Replace(' ', '%20'))
    $lista = @(Get-Conteudo $r)
    Test-Condicao 'GET /api/alunos?nome= traz o aluno' (($lista.Count -eq 1) -and ($lista[0].id -eq $aluno.id)) ('itens: ' + $lista.Count)
    $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos?matricula=' + $aluno.matricula)
    $lista = @(Get-Conteudo $r)
    Test-Condicao 'GET /api/alunos?matricula= traz so o aluno' (($lista.Count -eq 1) -and ($lista[0].id -eq $aluno.id)) ('itens: ' + $lista.Count)
    $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos?matricula=' + $aluno.matricula.Substring(0, 8))
    Test-Valor 'GET /api/alunos?matricula= com parte do numero nao traz nada (filtro exato)' 0 @(Get-Conteudo $r).Count

    $turma = New-Turma
    $ativo = New-Aluno
    $trancado = New-Aluno
    New-Matricula $ativo $turma.id | Out-Null
    $m = New-Matricula $trancado $turma.id
    Set-StatusMatricula $m.id 'TRANCADA' | Out-Null
    $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos?turmaId=' + $turma.id)
    # Lista exata de ids: o aluno com matricula ATIVA, sem o TRANCADO
    $esperados = @([long]$ativo.id)
    $obtidos = @(Get-Conteudo $r | ForEach-Object { [long]$_.id } | Sort-Object)
    Test-Condicao 'GET /api/alunos?turmaId= traz so os alunos com matricula ATIVA na turma' `
        (($r.Status -eq 200) -and (($obtidos -join ',') -eq ($esperados -join ','))) `
        ('esperado: ' + (Format-Valor $esperados) + '; obtido: ' + (Format-Valor $obtidos))

    # Exclusao: recusada se o aluno tiver qualquer matricula (historico escolar)
    Test-Erro (Invoke-Api -Metodo DELETE -Caminho ('/api/alunos/' + $trancado.id)) 409 'DELETE /api/alunos/{id} de aluno com matricula'
    $r = Invoke-Api -Metodo DELETE -Caminho ('/api/alunos/' + $segundo.id)
    Test-Status $r 204 'DELETE /api/alunos/{id} de aluno sem matricula'
    Test-Erro (Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $segundo.id)) 404 'GET /api/alunos/{id} do aluno excluido'
}

# =============================================================================================
# Matriculas
# =============================================================================================

function Invoke-SecaoMatriculas {
    Initialize-Admin
    $ano = Get-AnoLetivo
    $hoje = Format-Iso (Get-Hoje)
    $t1 = New-Turma
    $t2 = New-Turma
    $outroAno = New-Turma -AnoLetivo ($ano + 1)

    # ATIVA, matricula vigente unica no ano
    $a1 = New-Aluno
    $r = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{ matriculaAluno = [string]$a1.matricula; turmaId = $t1.id }
    Assert-Status $r 201 'POST /api/matriculas'
    $m1 = $r.Json
    Test-Valor 'Matricula criada ATIVA' 'ATIVA' $m1.status
    Test-Valor 'Ano letivo copiado da turma' $ano $m1.anoLetivo
    Test-Valor 'Data da matricula = hoje' $hoje (Format-Data $m1.dataMatricula)
    Test-Valor 'Matricula traz o numero do aluno' $a1.matricula $m1.matriculaAluno
    Test-Erro (Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{ matriculaAluno = [string]$a1.matricula; turmaId = $t2.id }) 409 `
        'Segunda matricula ATIVA do aluno no mesmo ano'
    Test-ErroValidacao (Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{}) @('matriculaAluno', 'turmaId') 'POST /api/matriculas sem aluno e turma'
    Test-Erro (Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{ matriculaAluno = '0000000000'; turmaId = $t1.id }) 404 'POST /api/matriculas com aluno inexistente'
    Test-Erro (Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{ matriculaAluno = [string](New-Aluno).matricula; turmaId = 999999999 }) 404 `
        'POST /api/matriculas com turma inexistente'
    Test-Erro (Invoke-Api -Metodo GET -Caminho '/api/matriculas/999999999') 404 'GET /api/matriculas/{id} inexistente'

    # ATIVA -> TRANCADA (motivo obrigatorio) -> ATIVA (reativacao)
    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m1.id + '/status') -Corpo @{ status = 'TRANCADA' }
    Test-ErroValidacao $r @('motivo') 'ATIVA -> TRANCADA sem motivo'
    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m1.id + '/status') -Corpo @{ status = 'TRANCADA'; motivo = 'Viagem' }
    Test-Status $r 200 'ATIVA -> TRANCADA'
    Test-Valor 'Matricula trancada guarda o motivo' 'Viagem' $r.Json.motivo
    Test-Valor 'Trancada nao e final (sem dataEncerramento)' $null $r.Json.dataEncerramento
    Test-Erro (Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{ matriculaAluno = [string]$a1.matricula; turmaId = $t2.id }) 409 `
        'Nova matricula com outra TRANCADA no mesmo ano'
    foreach ($invalido in @('CONCLUIDA', 'TRANSFERIDA', 'TRANCADA')) {
        $r = Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m1.id + '/status') -Corpo @{ status = $invalido; motivo = 'x' }
        Test-Erro $r 409 ('Transicao invalida TRANCADA -> ' + $invalido)
    }
    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m1.id + '/status') -Corpo @{ status = 'ATIVA' }
    Test-Status $r 200 'TRANCADA -> ATIVA (reativacao, sem motivo)'
    Test-Valor 'Matricula reativada' 'ATIVA' $r.Json.status
    Test-Erro (Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m1.id + '/status') -Corpo @{ status = 'ATIVA' }) 409 'Transicao invalida ATIVA -> ATIVA'

    # Transferencia entre turmas
    Test-Erro (Invoke-Api -Metodo POST -Caminho ('/api/matriculas/' + $m1.id + '/transferencia') -Corpo @{ turmaDestinoId = $outroAno.id; motivo = 'Mudanca' }) 409 `
        'Transferencia para turma de outro ano letivo'
    Test-Erro (Invoke-Api -Metodo POST -Caminho ('/api/matriculas/' + $m1.id + '/transferencia') -Corpo @{ turmaDestinoId = $t1.id; motivo = 'Mudanca' }) 409 `
        'Transferencia para a propria turma'
    Test-ErroValidacao (Invoke-Api -Metodo POST -Caminho ('/api/matriculas/' + $m1.id + '/transferencia') -Corpo @{ turmaDestinoId = $t2.id }) @('motivo') `
        'Transferencia sem motivo'
    $r = Invoke-Api -Metodo POST -Caminho ('/api/matriculas/' + $m1.id + '/transferencia') -Corpo @{ turmaDestinoId = $t2.id; motivo = 'Troca de turno' }
    Test-Status $r 201 'POST /api/matriculas/{id}/transferencia (mesmo ano)'
    $nova = $r.Json
    Test-Valor 'Nova matricula ATIVA na turma de destino' 'ATIVA' $nova.status
    Test-Valor 'Nova matricula na turma de destino' $t2.id $nova.turmaId
    Test-Valor 'Nova matricula aponta para a de origem (matriculaOrigemId)' $m1.id $nova.matriculaOrigemId
    $r = Invoke-Api -Metodo GET -Caminho ('/api/matriculas/' + $m1.id)
    Test-Valor 'Matricula de origem fica TRANSFERIDA' 'TRANSFERIDA' $r.Json.status
    Test-Valor 'Matricula de origem encerrada hoje' $hoje (Format-Data $r.Json.dataEncerramento)
    Test-Valor 'Matricula de origem guarda o motivo da transferencia' 'Troca de turno' $r.Json.motivo
    Test-Erro (Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m1.id + '/status') -Corpo @{ status = 'ATIVA' }) 409 'TRANSFERIDA e final (-> ATIVA)'

    # ATIVA -> CANCELADA (motivo obrigatorio); final; nova matricula depois do cancelamento
    $a2 = New-Aluno
    $m2 = New-Matricula $a2 $t1.id
    Test-ErroValidacao (Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m2.id + '/status') -Corpo @{ status = 'CANCELADA'; motivo = '  ' }) @('motivo') `
        'ATIVA -> CANCELADA com motivo em branco'
    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m2.id + '/status') -Corpo @{ status = 'CANCELADA'; motivo = 'Desistencia' }
    Test-Status $r 200 'ATIVA -> CANCELADA'
    Test-Valor 'Cancelada encerrada hoje' $hoje (Format-Data $r.Json.dataEncerramento)
    Test-Erro (Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m2.id + '/status') -Corpo @{ status = 'ATIVA' }) 409 'CANCELADA e final (-> ATIVA)'
    $r = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{ matriculaAluno = [string]$a2.matricula; turmaId = $t2.id }
    Test-Status $r 201 'Nova matricula no ano depois de uma CANCELADA'

    # ATIVA -> CONCLUIDA (sem motivo) e final; TRANCADA -> CANCELADA; ATIVA -> TRANSFERIDA (saida da escola)
    $a3 = New-Aluno
    $m3 = New-Matricula $a3 $t1.id
    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m3.id + '/status') -Corpo @{ status = 'CONCLUIDA' }
    Test-Status $r 200 'ATIVA -> CONCLUIDA (sem motivo)'
    Test-Erro (Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m3.id + '/status') -Corpo @{ status = 'ATIVA' }) 409 'CONCLUIDA e final (-> ATIVA)'
    $a4 = New-Aluno
    $m4 = New-Matricula $a4 $t1.id
    Set-StatusMatricula $m4.id 'TRANCADA' | Out-Null
    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m4.id + '/status') -Corpo @{ status = 'CANCELADA'; motivo = 'Nao voltou' }
    Test-Status $r 200 'TRANCADA -> CANCELADA'
    $a5 = New-Aluno
    $m5 = New-Matricula $a5 $t1.id
    Test-ErroValidacao (Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m5.id + '/status') -Corpo @{ status = 'TRANSFERIDA' }) @('motivo') 'ATIVA -> TRANSFERIDA sem motivo'
    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $m5.id + '/status') -Corpo @{ status = 'TRANSFERIDA'; motivo = 'Mudou de escola' }
    Test-Status $r 200 'ATIVA -> TRANSFERIDA (saida para outra escola)'
    Test-Valor 'Saida para outra escola nao cria matricula de destino' $null $r.Json.matriculaOrigemId

    # Capacidade: turma com 1 vaga
    $lotada = New-Turma -Capacidade 1
    $b1 = New-Aluno
    $b2 = New-Aluno
    $mb1 = New-Matricula $b1 $lotada.id
    Test-Erro (Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{ matriculaAluno = [string]$b2.matricula; turmaId = $lotada.id }) 409 `
        'Matricula alem da capacidade (turma com 1 vaga)'
    Set-StatusMatricula $mb1.id 'TRANCADA' | Out-Null
    $r = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{ matriculaAluno = [string]$b2.matricula; turmaId = $lotada.id }
    Test-Status $r 201 'Matricula na vaga liberada por um trancamento'
    Test-Erro (Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $mb1.id + '/status') -Corpo @{ status = 'ATIVA' }) 409 `
        'Reativacao de trancada sem vaga na turma'
    Test-Erro (Invoke-Api -Metodo POST -Caminho ('/api/matriculas/' + $nova.id + '/transferencia') -Corpo @{ turmaDestinoId = $lotada.id; motivo = 'x' }) 409 `
        'Transferencia para turma sem vaga'

    # Listagem com filtros
    $r = Invoke-Api -Metodo GET -Caminho ('/api/matriculas?alunoId=' + $a1.id)
    Test-Valor 'GET /api/matriculas?alunoId= traz a origem e a nova' 2 @(Get-Conteudo $r).Count
    $r = Invoke-Api -Metodo GET -Caminho ('/api/matriculas?turmaId=' + $t1.id + '&status=CANCELADA')
    Test-Valor 'GET /api/matriculas?turmaId=&status=CANCELADA' 2 @(Get-Conteudo $r).Count
    $r = Invoke-Api -Metodo GET -Caminho ('/api/matriculas?alunoId=' + $a1.id + '&anoLetivo=' + ($ano + 1))
    Test-Valor 'GET /api/matriculas?anoLetivo= de outro ano' 0 @(Get-Conteudo $r).Count
}

# =============================================================================================
# Frequencia
# =============================================================================================

function Invoke-SecaoFrequencia {
    Initialize-Admin
    $hoje = Get-Hoje
    if ($hoje.DayOfYear -lt 6) { Skip-Secao 'Frequencia' 'as chamadas usam os 4 dias anteriores, que precisam estar no ano letivo' }
    $cenario = New-TurmaComAlunos 3
    $turmaId = $cenario.Turma.id
    $disciplinaId = $cenario.Disciplina.id
    $f1 = $cenario.Alunos[0]; $f2 = $cenario.Alunos[1]; $f3 = $cenario.Alunos[2]
    $datas = @(4, 3, 2, 1 | ForEach-Object { $hoje.AddDays(-$_) })

    function Get-CorpoChamada([DateTime]$Data, $Registros) {
        return @{ turmaId = $turmaId; disciplinaId = $disciplinaId; data = (Format-Iso $Data)
            registros = @($Registros | ForEach-Object { @{ matriculaAluno = [string]$_.A.Aluno.matricula; status = $_.S } }) }
    }

    $r = Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo (Get-CorpoChamada $datas[0] @(
        @{ A = $f1; S = 'PRESENTE' }, @{ A = $f2; S = 'FALTA' }, @{ A = $f3; S = 'ATRASO' }))
    Assert-Status $r 201 'POST /api/frequencias/chamadas'
    $c1 = $r.Json
    Test-Valor 'Chamada com os 3 registros' 3 @($c1.registros).Count
    $regFalta = @($c1.registros) | Where-Object { [string]$_.matriculaId -eq [string]$f2.Matricula.id } | Select-Object -First 1
    Test-Valor 'Registro da falta pela matricula do aluno' 'FALTA' $regFalta.status

    # Divergencias: 400 com a lista; data futura ou fora do ano letivo: 400; mesma data: 409
    $r = Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo (Get-CorpoChamada $datas[1] @(@{ A = $f1; S = 'PRESENTE' }, @{ A = $f2; S = 'PRESENTE' }))
    Test-Erro $r 400 'Chamada faltando uma matricula ATIVA'
    Test-Condicao 'Resposta lista a matricula faltando (matriculasFaltando)' (@($r.Json.matriculasFaltando) -contains $f3.Matricula.id) $r.Texto
    $fora = New-Aluno
    $r = Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo (Get-CorpoChamada $datas[1] @(
        @{ A = $f1; S = 'PRESENTE' }, @{ A = $f2; S = 'PRESENTE' }, @{ A = $f3; S = 'PRESENTE' }, @{ A = @{ Aluno = $fora }; S = 'PRESENTE' }))
    Test-Erro $r 400 'Chamada com um aluno de fora da turma'
    Test-Condicao 'Resposta lista o aluno sobrando (matriculasNaoEsperadas)' (@($r.Json.matriculasNaoEsperadas) -contains [string]$fora.matricula) $r.Texto
    $r = Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo (Get-CorpoChamada $datas[1] @(
        @{ A = $f1; S = 'PRESENTE' }, @{ A = $f1; S = 'FALTA' }, @{ A = $f2; S = 'PRESENTE' }, @{ A = $f3; S = 'PRESENTE' }))
    Test-Erro $r 400 'Chamada com uma matricula repetida'
    Test-Condicao 'Resposta lista a matricula repetida (matriculasDuplicadas)' (@($r.Json.matriculasDuplicadas) -contains [string]$f1.Aluno.matricula) $r.Texto
    $todos = @(@{ A = $f1; S = 'PRESENTE' }, @{ A = $f2; S = 'PRESENTE' }, @{ A = $f3; S = 'PRESENTE' })
    Test-ErroValidacao (Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo (Get-CorpoChamada $hoje.AddDays(1) $todos)) @('data') 'Chamada com data futura'
    Test-ErroValidacao (Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo (Get-CorpoChamada (Get-Date -Year ($hoje.Year - 1) -Month 6 -Day 10) $todos)) @('data') `
        'Chamada fora do ano letivo da turma'
    Test-Erro (Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo (Get-CorpoChamada $datas[0] $todos)) 409 'Segunda chamada na mesma data'
    Test-ErroValidacao (Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo @{ turmaId = $turmaId; registros = @() }) @('disciplinaId', 'data', 'registros') `
        'Chamada sem disciplina, data e registros'

    # Mais tres chamadas; a terceira e editada depois
    $finais = @{
        1 = @(@{ A = $f1; S = 'PRESENTE' }, @{ A = $f2; S = 'PRESENTE' }, @{ A = $f3; S = 'FALTA' })
        2 = @(@{ A = $f1; S = 'ATRASO' }, @{ A = $f2; S = 'FALTA' }, @{ A = $f3; S = 'FALTA' })
        3 = @(@{ A = $f1; S = 'PRESENTE' }, @{ A = $f2; S = 'PRESENTE' }, @{ A = $f3; S = 'PRESENTE' })
    }
    $chamadas = @{ 0 = $c1 }
    foreach ($i in 1..3) {
        $r = Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo (Get-CorpoChamada $datas[$i] $finais[$i])
        Assert-Preparacao $r 201 ('POST /api/frequencias/chamadas (' + (Format-Iso $datas[$i]) + ')')
        $chamadas[$i] = $r.Json
    }
    $editado = @(@{ A = $f1; S = 'ATRASO' }, @{ A = $f2; S = 'PRESENTE' }, @{ A = $f3; S = 'FALTA' })
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/frequencias/chamadas/' + $chamadas[2].id) -Corpo (Get-CorpoChamada $datas[2] $editado)
    Test-Status $r 200 'PUT /api/frequencias/chamadas/{id} (falta de um aluno vira presenca)'
    $reg = @($r.Json.registros) | Where-Object { [string]$_.matriculaId -eq [string]$f2.Matricula.id } | Select-Object -First 1
    Test-Valor 'Registro editado' 'PRESENTE' $reg.status
    Test-Erro (Invoke-Api -Metodo PUT -Caminho ('/api/frequencias/chamadas/' + $chamadas[2].id) -Corpo (Get-CorpoChamada $datas[2] @(@{ A = $f1; S = 'PRESENTE' }))) 400 `
        'Edicao da chamada faltando matriculas'
    Test-Erro (Invoke-Api -Metodo PUT -Caminho '/api/frequencias/chamadas/999999999' -Corpo (Get-CorpoChamada $datas[2] $editado)) 404 'PUT /api/frequencias/chamadas/{id} inexistente'
    $finais[2] = $editado
    $finais[0] = @(@{ A = $f1; S = 'PRESENTE' }, @{ A = $f2; S = 'FALTA' }, @{ A = $f3; S = 'ATRASO' })

    # Consultas
    $r = Invoke-Api -Metodo GET -Caminho ('/api/frequencias/chamadas/' + $c1.id)
    Test-Status $r 200 'GET /api/frequencias/chamadas/{id}'
    Test-Erro (Invoke-Api -Metodo GET -Caminho '/api/frequencias/chamadas/999999999') 404 'GET /api/frequencias/chamadas/{id} inexistente'
    $r = Invoke-Api -Metodo GET -Caminho ('/api/frequencias/chamadas?turmaId=' + $turmaId + '&disciplinaId=' + $disciplinaId)
    Test-Valor 'GET /api/frequencias/chamadas?turmaId=&disciplinaId= traz as 4 chamadas' 4 @(Get-Conteudo $r).Count
    $r = Invoke-Api -Metodo GET -Caminho ('/api/frequencias/chamadas?turmaId=' + $turmaId + '&dataInicio=' + (Format-Iso $datas[1]) + '&dataFim=' + (Format-Iso $datas[2]))
    Test-Valor 'GET /api/frequencias/chamadas com dataInicio e dataFim traz 2 chamadas' 2 @(Get-Conteudo $r).Count

    # Relatorio da turma e frequencia por aluno, conferidos com o calculo feito aqui
    $esperado = @{}
    foreach ($a in @($f1, $f2, $f3)) {
        $p = 0; $f = 0; $t = 0
        foreach ($i in 0..3) {
            $s = (@($finais[$i] | Where-Object { $_.A.Aluno.id -eq $a.Aluno.id }) | Select-Object -First 1).S
            switch ($s) { 'PRESENTE' { $p++ } 'FALTA' { $f++ } 'ATRASO' { $t++ } }
        }
        $esperado[[string]$a.Aluno.id] = @{ P = $p; F = $f; T = $t; Calc = (Get-Frequencia $p $f $t) }
    }
    $r = Invoke-Api -Metodo GET -Caminho ('/api/frequencias/relatorio?turmaId=' + $turmaId + '&disciplinaId=' + $disciplinaId)
    Test-Status $r 200 'GET /api/frequencias/relatorio?turmaId=&disciplinaId='
    Test-Valor 'Relatorio com o minimo de frequencia configurado' 75 $r.Json.minimoPercentual
    foreach ($a in @($f1, $f2, $f3)) {
        $e = $esperado[[string]$a.Aluno.id]
        $linha = @($r.Json.alunos) | Where-Object { [string]$_.alunoId -eq [string]$a.Aluno.id } | Select-Object -First 1
        $obtido = 'null'
        if ($linha) { $obtido = '' + $linha.totalAulas + '/' + $linha.presencas + '/' + $linha.faltas + '/' + $linha.atrasos + ' ' + $linha.percentual + '% abaixo=' + $linha.abaixoDoMinimo }
        $esperadoTexto = '' + $e.Calc.Total + '/' + $e.P + '/' + $e.F + '/' + $e.T + ' ' + $e.Calc.Percentual + '% abaixo=' + $e.Calc.Abaixo
        $ok = ($null -ne $linha) -and ($linha.totalAulas -eq $e.Calc.Total) -and ($linha.presencas -eq $e.P) -and ($linha.faltas -eq $e.F) `
            -and ($linha.atrasos -eq $e.T) -and ([decimal]$linha.percentual -eq $e.Calc.Percentual) -and ([string]$linha.abaixoDoMinimo -eq [string]$e.Calc.Abaixo)
        Test-Condicao ('Relatorio do aluno ' + $a.Aluno.matricula + ': aulas/presencas/faltas/atrasos, percentual e abaixoDoMinimo') $ok `
            ('esperado: ' + $esperadoTexto + '; obtido: ' + $obtido)
    }
    $r = Invoke-Api -Metodo GET -Caminho ('/api/frequencias/relatorio?turmaId=' + $turmaId + '&dataInicio=' + (Format-Iso $datas[0]) + '&dataFim=' + (Format-Iso $datas[1]))
    $linha = @($r.Json.alunos) | Where-Object { [string]$_.alunoId -eq [string]$f3.Aluno.id } | Select-Object -First 1
    Test-Valor 'Relatorio no periodo de 2 dias conta 2 aulas' 2 $linha.totalAulas
    Test-ErroValidacao (Invoke-Api -Metodo GET -Caminho ('/api/frequencias/relatorio?turmaId=' + $turmaId + '&dataInicio=' + (Format-Iso $datas[2]) + '&dataFim=' + (Format-Iso $datas[0]))) `
        @('dataFim') 'Relatorio com dataFim antes de dataInicio'

    $e = $esperado[[string]$f3.Aluno.id]
    $r = Invoke-Api -Metodo GET -Caminho ('/api/frequencias/alunos/' + $f3.Aluno.id + '?anoLetivo=' + $hoje.Year)
    Test-Status $r 200 'GET /api/frequencias/alunos/{id}'
    Test-Valor 'Frequencia do aluno: percentual geral' $e.Calc.Percentual $r.Json.geral.percentual
    Test-Valor 'Frequencia do aluno: abaixoDoMinimo' $e.Calc.Abaixo $r.Json.geral.abaixoDoMinimo
    $d = @($r.Json.disciplinas) | Where-Object { [string]$_.disciplinaId -eq [string]$disciplinaId } | Select-Object -First 1
    Test-Valor 'Frequencia do aluno: percentual na disciplina' $e.Calc.Percentual $d.frequencia.percentual
    Test-Erro (Invoke-Api -Metodo GET -Caminho ('/api/frequencias/alunos/999999999?anoLetivo=' + $hoje.Year)) 404 'Frequencia de aluno inexistente'
}

# =============================================================================================
# Avaliacoes e notas
# =============================================================================================

function Invoke-SecaoNotas {
    Initialize-Admin
    $cenario = New-TurmaComAlunos 3
    $turmaId = $cenario.Turma.id
    $disciplinaId = $cenario.Disciplina.id
    $n1 = $cenario.Alunos[0]; $n2 = $cenario.Alunos[1]; $n3 = $cenario.Alunos[2]
    $hoje = Format-Iso (Get-Hoje)
    Set-StatusMatricula $n3.Matricula.id 'TRANCADA' | Out-Null

    # CRUD de avaliacao (peso e notaMaxima tem padrao 1 e 10)
    $r = Invoke-Api -Metodo POST -Caminho '/api/avaliacoes' -Corpo @{ turmaId = $turmaId; disciplinaId = $disciplinaId; bimestre = 1; tipo = 'ATIVIDADE'; data = $hoje }
    Assert-Status $r 201 'POST /api/avaliacoes (sem peso e notaMaxima)'
    $av = $r.Json
    Test-Valor 'Peso padrao 1' 1 $av.peso
    Test-Valor 'Nota maxima padrao 10' 10 $av.notaMaxima
    Test-ErroValidacao (Invoke-Api -Metodo POST -Caminho '/api/avaliacoes' -Corpo @{ turmaId = $turmaId; disciplinaId = $disciplinaId; bimestre = 5; peso = 0; notaMaxima = -1 }) `
        @('bimestre', 'tipo', 'peso', 'notaMaxima', 'data') 'POST /api/avaliacoes com bimestre 5, peso 0, notaMaxima negativa, sem tipo e data'
    Test-Erro (Invoke-Api -Metodo POST -Caminho '/api/avaliacoes' -Corpo @{ turmaId = $turmaId; disciplinaId = 999999999; bimestre = 1; tipo = 'PROVA'; data = $hoje }) 409 `
        'POST /api/avaliacoes em disciplina nao ofertada na turma'
    $r = Invoke-Api -Metodo GET -Caminho ('/api/avaliacoes/' + $av.id)
    Test-Status $r 200 'GET /api/avaliacoes/{id}'
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/avaliacoes/' + $av.id) -Corpo @{ turmaId = $turmaId; disciplinaId = $disciplinaId; bimestre = 2; tipo = 'ATIVIDADE'; descricao = 'Editada'; peso = 2; notaMaxima = 10; data = $hoje }
    Test-Status $r 200 'PUT /api/avaliacoes/{id}'
    Test-Valor 'Avaliacao editada: bimestre' 2 $r.Json.bimestre
    Test-Valor 'Avaliacao editada: peso' 2 $r.Json.peso
    $r = Invoke-Api -Metodo GET -Caminho ('/api/avaliacoes?turmaId=' + $turmaId + '&disciplinaId=' + $disciplinaId + '&bimestre=2')
    Test-Valor 'GET /api/avaliacoes?turmaId=&disciplinaId=&bimestre= traz a avaliacao' 1 @(Get-Conteudo $r).Count
    $r = Invoke-Api -Metodo DELETE -Caminho ('/api/avaliacoes/' + $av.id)
    Test-Status $r 204 'DELETE /api/avaliacoes/{id} sem notas'
    Test-Erro (Invoke-Api -Metodo GET -Caminho ('/api/avaliacoes/' + $av.id)) 404 'GET /api/avaliacoes/{id} excluida'

    # Uma recuperacao por turma, disciplina e bimestre
    New-Avaliacao $turmaId $disciplinaId 2 'RECUPERACAO' | Out-Null
    Test-Erro (Invoke-Api -Metodo POST -Caminho '/api/avaliacoes' -Corpo @{ turmaId = $turmaId; disciplinaId = $disciplinaId; bimestre = 2; tipo = 'RECUPERACAO'; data = $hoje }) 409 `
        'Segunda RECUPERACAO no mesmo bimestre'
    $r = Invoke-Api -Metodo POST -Caminho '/api/avaliacoes' -Corpo @{ turmaId = $turmaId; disciplinaId = $disciplinaId; bimestre = 3; tipo = 'RECUPERACAO'; data = $hoje }
    Test-Status $r 201 'RECUPERACAO em outro bimestre'

    # Lancamento em lote (nota maxima 20)
    $prova = New-Avaliacao $turmaId $disciplinaId 1 'PROVA' 2 20
    $r = Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $prova.id + '/notas') -Corpo @(
        @{ matriculaAluno = [string]$n1.Aluno.matricula; valor = 15.5 }, @{ matriculaAluno = [string]$n2.Aluno.matricula; valor = 21 })
    Test-Erro $r 400 'Lancamento com nota acima da notaMaxima (21 de 20)'
    Test-Condicao 'Resposta lista a matricula com nota acima do maximo' (@($r.Json.matriculasComValorAcimaDoMaximo) -contains [string]$n2.Aluno.matricula) $r.Texto
    $r = Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $prova.id + '/notas') -Corpo @(@{ matriculaAluno = [string]$n3.Aluno.matricula; valor = 10 })
    Test-Erro $r 400 'Lancamento para matricula TRANCADA'
    Test-Condicao 'Resposta lista a matricula que nao esta ATIVA na turma' (@($r.Json.matriculasNaoAtivasNaTurma) -contains [string]$n3.Aluno.matricula) $r.Texto
    Test-Erro (Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $prova.id + '/notas') -Corpo @(@{ matriculaAluno = [string]$n1.Aluno.matricula; valor = -1 })) 400 `
        'Lancamento com nota negativa'
    Test-Erro (Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $prova.id + '/notas') -Corpo '[]') 400 'Lancamento com lista vazia'
    $r = Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $prova.id + '/notas') -Corpo @(
        @{ matriculaAluno = [string]$n1.Aluno.matricula; valor = 15.5 }, @{ matriculaAluno = [string]$n2.Aluno.matricula; valor = 18 })
    Test-Status $r 201 'POST /api/avaliacoes/{id}/notas (lote com 2 notas)'
    $notas = @($r.Json)
    Test-Valor 'Lote devolve as 2 notas' 2 $notas.Count
    $nota1 = $notas | Where-Object { [string]$_.matriculaId -eq [string]$n1.Matricula.id } | Select-Object -First 1
    Test-Valor 'Nota gravada com 2 casas' 15.5 $nota1.valor
    Test-Erro (Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $prova.id + '/notas') -Corpo @(@{ matriculaAluno = [string]$n1.Aluno.matricula; valor = 10 })) 409 `
        'Lancamento repetido para a mesma matricula'
    $r = Invoke-Api -Metodo GET -Caminho ('/api/avaliacoes/' + $prova.id + '/notas')
    Test-Valor 'GET /api/avaliacoes/{id}/notas' 2 @($r.Json).Count
    Test-Erro (Invoke-Api -Metodo DELETE -Caminho ('/api/avaliacoes/' + $prova.id)) 409 'DELETE /api/avaliacoes/{id} com notas'

    # Correcao: motivo obrigatorio, historico preservado
    Test-ErroValidacao (Invoke-Api -Metodo PUT -Caminho ('/api/notas/' + $nota1.id) -Corpo @{ valor = 16 }) @('motivo') 'Correcao de nota sem motivo'
    Test-ErroValidacao (Invoke-Api -Metodo PUT -Caminho ('/api/notas/' + $nota1.id) -Corpo @{ valor = 25; motivo = 'Erro de digitacao' }) @('valor') 'Correcao acima da notaMaxima'
    Test-Erro (Invoke-Api -Metodo PUT -Caminho ('/api/notas/' + $nota1.id) -Corpo @{ valor = 15.5; motivo = 'Sem mudanca' }) 409 'Correcao para o mesmo valor'
    Test-Erro (Invoke-Api -Metodo PUT -Caminho '/api/notas/999999999' -Corpo @{ valor = 1; motivo = 'x' }) 404 'Correcao de nota inexistente'
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/notas/' + $nota1.id) -Corpo @{ valor = 16; motivo = 'Revisao da questao 3' }
    Test-Status $r 200 'PUT /api/notas/{id} (correcao com motivo)'
    Test-Valor 'Nota corrigida' 16 $r.Json.valor
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/notas/' + $nota1.id) -Corpo @{ valor = 17.25; motivo = 'Segunda revisao' }
    Test-Status $r 200 'Segunda correcao'
    $r = Invoke-Api -Metodo GET -Caminho ('/api/notas/' + $nota1.id + '/historico')
    Test-Status $r 200 'GET /api/notas/{id}/historico'
    $historico = @($r.Json)
    Test-Valor 'Historico com as 2 correcoes' 2 $historico.Count
    $primeira = @($historico | Where-Object { $_.motivo -eq 'Revisao da questao 3' }) | Select-Object -First 1
    Test-Condicao 'Historico guarda valor anterior, novo e motivo' (($null -ne $primeira) -and ([decimal]$primeira.valorAnterior -eq 15.5) -and ([decimal]$primeira.valorNovo -eq 16)) $r.Texto
    $segunda = @($historico | Where-Object { $_.motivo -eq 'Segunda revisao' }) | Select-Object -First 1
    Test-Condicao 'Segunda correcao parte do valor da primeira' (($null -ne $segunda) -and ([decimal]$segunda.valorAnterior -eq 16) -and ([decimal]$segunda.valorNovo -eq 17.25)) $r.Texto
    Test-Erro (Invoke-Api -Metodo GET -Caminho '/api/notas/999999999/historico') 404 'Historico de nota inexistente'
}

# =============================================================================================
# Boletim
# =============================================================================================

function Invoke-SecaoBoletim {
    Initialize-Admin
    $hoje = Get-Hoje
    $ano = $hoje.Year
    if ($hoje.DayOfYear -lt 6) { Skip-Secao 'Boletim' 'as chamadas usam os 4 dias anteriores, que precisam estar no ano letivo' }
    $cenario = New-TurmaComAlunos 4 -Disciplinas 2
    $turmaId = $cenario.Turma.id
    $d1 = $cenario.Disciplinas[0]; $d2 = $cenario.Disciplinas[1]
    # Um aluno para cada situacao
    $nomes = @('CURSANDO', 'APROVADO', 'REPROVADO_POR_NOTA', 'REPROVADO_POR_FALTA')
    $alunos = @{}
    for ($i = 0; $i -lt 4; $i++) { $alunos[$nomes[$i]] = $cenario.Alunos[$i] }

    # Frequencia (4 aulas de d1): APROVADO no limite (75%), REPROVADO_POR_FALTA com 50%
    $presencas = @{
        CURSANDO = @('PRESENTE', 'PRESENTE', 'PRESENTE', 'PRESENTE')
        APROVADO = @('PRESENTE', 'PRESENTE', 'ATRASO', 'FALTA')
        REPROVADO_POR_NOTA = @('PRESENTE', 'ATRASO', 'PRESENTE', 'PRESENTE')
        REPROVADO_POR_FALTA = @('FALTA', 'FALTA', 'PRESENTE', 'ATRASO')
    }
    for ($i = 0; $i -lt 4; $i++) {
        $registros = @($nomes | ForEach-Object { @{ Aluno = $alunos[$_].Aluno; Status = $presencas[$_][$i] } })
        New-Chamada $turmaId $d1.id $hoje.AddDays(-(4 - $i)) $registros | Out-Null
    }

    # Avaliacoes com pesos e notas maximas diferentes, recuperacao no 1o bimestre de d1. Valores:
    # $null = sem nota (pendente). CURSANDO fica com uma recuperacao e um trabalho pendentes.
    $plano = @(
        @{ D = $d1; B = 1; Tipo = 'PROVA';       Peso = 2; Max = 10;  Notas = @{ CURSANDO = 7;    APROVADO = 6.5; REPROVADO_POR_NOTA = 4; REPROVADO_POR_FALTA = 9 } }
        @{ D = $d1; B = 1; Tipo = 'TRABALHO';    Peso = 1; Max = 20;  Notas = @{ CURSANDO = 13;   APROVADO = 15;  REPROVADO_POR_NOTA = 9; REPROVADO_POR_FALTA = 18 } }
        @{ D = $d1; B = 1; Tipo = 'RECUPERACAO'; Peso = 1; Max = 10;  Notas = @{ CURSANDO = $null; APROVADO = 9;  REPROVADO_POR_NOTA = 5; REPROVADO_POR_FALTA = 6 } }
        @{ D = $d1; B = 2; Tipo = 'ATIVIDADE';   Peso = 1; Max = 5;   Notas = @{ CURSANDO = 3;    APROVADO = 4;   REPROVADO_POR_NOTA = 2; REPROVADO_POR_FALTA = 5 } }
        @{ D = $d2; B = 1; Tipo = 'PROVA';       Peso = 3; Max = 100; Notas = @{ CURSANDO = 60;   APROVADO = 70;  REPROVADO_POR_NOTA = 80; REPROVADO_POR_FALTA = 90 } }
        @{ D = $d2; B = 1; Tipo = 'ATIVIDADE';   Peso = 1; Max = 10;  Notas = @{ CURSANDO = 7;    APROVADO = 8;   REPROVADO_POR_NOTA = 9; REPROVADO_POR_FALTA = 10 } }
        @{ D = $d2; B = 2; Tipo = 'TRABALHO';    Peso = 1; Max = 10;  Notas = @{ CURSANDO = $null; APROVADO = 6;  REPROVADO_POR_NOTA = 7; REPROVADO_POR_FALTA = 9 } }
    )
    foreach ($p in $plano) {
        $av = New-Avaliacao $turmaId $p.D.id $p.B $p.Tipo $p.Peso $p.Max
        $lancamentos = @($nomes | Where-Object { $null -ne $p.Notas[$_] } | ForEach-Object { @{ Aluno = $alunos[$_].Aluno; Valor = $p.Notas[$_] } })
        Add-Notas $av.id $lancamentos | Out-Null
    }

    # Encerra o ano de tres alunos; CURSANDO continua com a matricula ATIVA
    foreach ($n in @('APROVADO', 'REPROVADO_POR_NOTA', 'REPROVADO_POR_FALTA')) {
        Set-StatusMatricula $alunos[$n].Matricula.id 'CONCLUIDA' | Out-Null
    }

    foreach ($situacao in $nomes) {
        $aluno = $alunos[$situacao].Aluno
        $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $aluno.id + '/boletim?anoLetivo=' + $ano)
        Assert-Status $r 200 ('GET /api/alunos/{id}/boletim (' + $situacao + ')')
        $b = $r.Json
        Test-Valor ('Boletim ' + $situacao + ': situacao') $situacao $b.situacao
        $atingiramTodas = $true
        foreach ($d in @($d1, $d2)) {
            $esperadoBimestres = @{}
            foreach ($bim in 1..4) {
                $avaliacoes = @($plano | Where-Object { $_.D.id -eq $d.id -and $_.B -eq $bim } | ForEach-Object {
                    @{ Tipo = $_.Tipo; Peso = $_.Peso; NotaMaxima = $_.Max; Valor = $_.Notas[$situacao] } })
                $esperadoBimestres[$bim] = Get-MediaBimestre $avaliacoes
            }
            $mediaAnual = Get-MediaAnual @(1..4 | ForEach-Object { $esperadoBimestres[$_].Media })
            $pendentes = 0
            foreach ($bim in 1..4) { $pendentes += $esperadoBimestres[$bim].Pendentes }
            $obtida = @($b.disciplinas) | Where-Object { [string]$_.disciplinaId -eq [string]$d.id } | Select-Object -First 1
            if (-not (Test-DisciplinaBoletim $situacao $d $obtida)) { continue }
            foreach ($bim in 1..2) {
                $e = $esperadoBimestres[$bim]
                $o = @($obtida.bimestres) | Where-Object { $_.bimestre -eq $bim } | Select-Object -First 1
                Test-Condicao ('Boletim ' + $situacao + ' / ' + $d.codigo + ' / ' + $bim + 'o bimestre: media, sem recuperacao e recuperacao') `
                    ((Test-Decimal $e.Media $o.media) -and (Test-Decimal $e.SemRecuperacao $o.mediaSemRecuperacao) -and (Test-Decimal $e.Recuperacao $o.recuperacao)) `
                    ('esperado: ' + (Format-Valor $e.Media) + ' / ' + (Format-Valor $e.SemRecuperacao) + ' / ' + (Format-Valor $e.Recuperacao) +
                     '; obtido: ' + (Format-Valor $o.media) + ' / ' + (Format-Valor $o.mediaSemRecuperacao) + ' / ' + (Format-Valor $o.recuperacao))
            }
            Test-Condicao ('Boletim ' + $situacao + ' / ' + $d.codigo + ': media anual') (Test-Decimal $mediaAnual $obtida.mediaAnual) `
                ('esperado: ' + (Format-Valor $mediaAnual) + '; obtido: ' + (Format-Valor $obtida.mediaAnual))
            Test-Valor ('Boletim ' + $situacao + ' / ' + $d.codigo + ': avaliacoes pendentes') $pendentes $obtida.avaliacoesPendentes
            $atingiu = ($null -ne $mediaAnual) -and ($mediaAnual -ge 6)
            if (-not $atingiu) { $atingiramTodas = $false }
            $situacaoDisciplina = Get-SituacaoEsperada $situacao $atingiu $presencas[$situacao]
            Test-Valor ('Boletim ' + $situacao + ' / ' + $d.codigo + ': situacao da disciplina') $situacaoDisciplina $obtida.situacao
        }
        $contagem = @{ PRESENTE = 0; FALTA = 0; ATRASO = 0 }
        foreach ($s in $presencas[$situacao]) { $contagem[$s]++ }
        $freq = Get-Frequencia $contagem.PRESENTE $contagem.FALTA $contagem.ATRASO
        Test-Condicao ('Boletim ' + $situacao + ': frequencia geral (percentual e abaixoDoMinimo)') `
            ((Test-Decimal $freq.Percentual $b.frequenciaGeral.percentual) -and ([string]$b.frequenciaGeral.abaixoDoMinimo -eq [string]$freq.Abaixo)) `
            ('esperado: ' + $freq.Percentual + '% abaixo=' + $freq.Abaixo + '; obtido: ' + (Format-Valor $b.frequenciaGeral.percentual) + '% abaixo=' + (Format-Valor $b.frequenciaGeral.abaixoDoMinimo))
        # A situacao geral confere com o calculo feito aqui
        $situacaoGeral = Get-SituacaoEsperada $situacao $atingiramTodas $presencas[$situacao]
        Test-Valor ('Boletim ' + $situacao + ': situacao geral calculada no teste') $situacaoGeral $b.situacao
    }

    Test-Erro (Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $alunos['CURSANDO'].Aluno.id + '/boletim?anoLetivo=' + ($ano - 1))) 404 'Boletim de ano sem matricula'
    Test-Erro (Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $alunos['CURSANDO'].Aluno.id + '/boletim')) 400 'Boletim sem anoLetivo'
    Test-Erro (Invoke-Api -Metodo GET -Caminho ('/api/alunos/999999999/boletim?anoLetivo=' + $ano)) 404 'Boletim de aluno inexistente'
}

function Test-DisciplinaBoletim([string]$Situacao, $Disciplina, $Obtida) {
    Test-Condicao ('Boletim ' + $Situacao + ': disciplina ' + $Disciplina.codigo + ' presente, com codigo e nome da replica') `
        (($null -ne $Obtida) -and ($Obtida.codigo -eq $Disciplina.codigo) -and ($Obtida.nome -eq $Disciplina.nome)) (Format-Valor $Obtida)
    return ($null -ne $Obtida)
}

function Test-Decimal($Esperado, $Obtido) {
    if ($null -eq $Esperado) { return ($null -eq $Obtido) }
    if ($null -eq $Obtido) { return $false }
    return ([decimal]$Obtido -eq [decimal]$Esperado)
}

# Situacao esperada: o aluno CURSANDO tem a matricula ATIVA; os outros, CONCLUIDA
function Get-SituacaoEsperada([string]$Aluno, [bool]$AtingiuMedia, $Presencas) {
    if ($Aluno -eq 'CURSANDO') { return 'CURSANDO' }
    $c = @{ PRESENTE = 0; FALTA = 0; ATRASO = 0 }
    foreach ($s in $Presencas) { $c[$s]++ }
    if ((Get-Frequencia $c.PRESENTE $c.FALTA $c.ATRASO).Abaixo) { return 'REPROVADO_POR_FALTA' }
    if ($AtingiuMedia) { return 'APROVADO' }
    return 'REPROVADO_POR_NOTA'
}
