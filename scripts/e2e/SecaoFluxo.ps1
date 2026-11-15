# Secao "fluxo": o caminho completo de um aluno pelo gateway: disciplina, professor, turma com a
# disciplina ofertada, aluno, matricula, chamadas e notas; confere boletim e frequencia.

function Invoke-SecaoFluxo {
    Initialize-Admin
    $s = New-IdUnico
    $hoje = Get-Hoje
    $ano = $hoje.Year
    $hojeTexto = Format-Iso $hoje
    if ($hoje.DayOfYear -lt 4) {
        Skip-Secao 'Fluxo funcional' 'as 4 chamadas usam hoje e os 3 dias anteriores, que precisam estar no ano letivo'
    }

    $codigoDisciplina = 'D' + $s
    $r = Invoke-Api -Metodo POST -Caminho '/api/disciplinas' -Corpo @{
        codigo = $codigoDisciplina; nome = 'Disciplina E2E ' + $s; cargaHoraria = 80
    }
    Assert-Status $r 201 'POST /api/disciplinas'
    $disciplinaId = $r.Json.id

    $r = Invoke-Api -Metodo POST -Caminho '/api/professores' -Corpo @{
        nome = 'Professor E2E ' + $s; qualificacao = 'Licenciatura'
        email = 'prof.fluxo.' + $s + '@distrischool.com'; telefone = '11999990000'
    }
    Assert-Status $r 201 'POST /api/professores'
    $professorId = $r.Json.id

    $codigoTurma = 'T-E2E-' + $s
    $r = Invoke-Api -Metodo POST -Caminho '/api/turmas' -Corpo @{
        codigo = $codigoTurma; nome = 'Turma E2E ' + $s; anoLetivo = $ano; turno = 'MANHA'; capacidade = 30
    }
    Assert-Status $r 201 'POST /api/turmas'
    $turmaId = $r.Json.id

    $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turmaId + '/disciplinas/' + $disciplinaId) `
        -Corpo @{ professorId = $professorId }
    Assert-Status $r 200 'PUT /api/turmas/{id}/disciplinas/{disciplinaId}'
    $oferta = @($r.Json.disciplinas) | Where-Object { [string]$_.disciplinaId -eq [string]$disciplinaId } | Select-Object -First 1
    Assert-Condicao 'Turma oferta a disciplina' ($null -ne $oferta) $r.Texto
    Test-Valor 'Professor responsavel pela disciplina na turma' $professorId $oferta.professorId

    $r = Invoke-Api -Metodo POST -Caminho '/api/alunos' -Corpo @{
        nome = 'Aluno E2E ' + $s; dataNascimento = '2012-05-10'
        email = 'aluno.fluxo.' + $s + '@distrischool.com'; telefone = '11988880000'
    }
    Assert-Status $r 201 'POST /api/alunos'
    $alunoId = $r.Json.id
    $numeroMatricula = [string]$r.Json.matricula
    Assert-Condicao 'Aluno criado com id e numero de matricula' (($null -ne $alunoId) -and [bool]$numeroMatricula) `
        ('id: ' + (Format-Valor $alunoId) + '; matricula: ' + $numeroMatricula)

    $r = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{ matriculaAluno = $numeroMatricula; turmaId = $turmaId }
    Assert-Status $r 201 'POST /api/matriculas'
    Test-Valor 'Status da matricula' 'ATIVA' $r.Json.status
    Test-Valor 'Ano letivo da matricula' $ano $r.Json.anoLetivo
    Test-Valor 'Aluno da matricula' $alunoId $r.Json.alunoId

    # A chamada exige a oferta na replica local do gestao-escolar; ela chega pelo schedule.updated
    Wait-OfertaNoEscolar $alunoId $disciplinaId 'Oferta da disciplina replicada no gestao-escolar-service (boletim lista a disciplina)'

    # Chamadas: 2 presencas, 1 atraso, 1 falta = 75% (atraso conta como presenca)
    $statusChamadas = @('PRESENTE', 'PRESENTE', 'ATRASO', 'FALTA')
    for ($i = 0; $i -lt $statusChamadas.Count; $i++) {
        $data = Format-Iso $hoje.AddDays(-($statusChamadas.Count - 1 - $i))
        $r = Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo @{
            turmaId = $turmaId; disciplinaId = $disciplinaId; data = $data; professorId = $professorId
            registros = @(@{ matriculaAluno = $numeroMatricula; status = $statusChamadas[$i] })
        }
        Assert-Status $r 201 ('POST /api/frequencias/chamadas (' + $data + ', ' + $statusChamadas[$i] + ')')
        Test-Valor ('Registro da chamada de ' + $data) $statusChamadas[$i] (@($r.Json.registros)[0]).status
    }

    # Notas do 1o bimestre: prova 8/10 (peso 2) e trabalho 10/20 = 5/10 (peso 1)
    # Media do bimestre = (8 x 2 + 5 x 1) / 3 = 7.00; media anual = 7.00
    $avaliacoes = @(
        @{ tipo = 'PROVA'; peso = 2; notaMaxima = 10; valor = 8 },
        @{ tipo = 'TRABALHO'; peso = 1; notaMaxima = 20; valor = 10 }
    )
    foreach ($a in $avaliacoes) {
        $r = Invoke-Api -Metodo POST -Caminho '/api/avaliacoes' -Corpo @{
            turmaId = $turmaId; disciplinaId = $disciplinaId; bimestre = 1; tipo = $a.tipo
            descricao = $a.tipo + ' E2E ' + $s; peso = $a.peso; notaMaxima = $a.notaMaxima; data = $hojeTexto
        }
        Assert-Status $r 201 ('POST /api/avaliacoes (' + $a.tipo + ')')
        $avaliacaoId = $r.Json.id

        $r = Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $avaliacaoId + '/notas') `
            -Corpo @(@{ matriculaAluno = $numeroMatricula; valor = $a.valor })
        Assert-Status $r 201 ('POST /api/avaliacoes/{id}/notas (' + $a.tipo + ')')
        Test-Valor ('Nota lancada na ' + $a.tipo) $a.valor (@($r.Json)[0]).valor
    }

    $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $alunoId + '/boletim?anoLetivo=' + $ano)
    Assert-Status $r 200 'GET /api/alunos/{id}/boletim'
    $b = $r.Json
    Test-Valor 'Boletim: situacao' 'CURSANDO' $b.situacao
    $matriculas = @($b.matriculas)
    Test-Valor 'Boletim: quantidade de matriculas no ano' 1 $matriculas.Count
    Test-Valor 'Boletim: turma da matricula' $turmaId $matriculas[0].turmaId
    Test-Valor 'Boletim: codigo da turma' $codigoTurma $matriculas[0].turmaCodigo
    $d = @($b.disciplinas) | Where-Object { [string]$_.disciplinaId -eq [string]$disciplinaId } | Select-Object -First 1
    Assert-Condicao 'Boletim: disciplina presente' ($null -ne $d) $r.Texto
    Test-Valor 'Boletim: codigo da disciplina' $codigoDisciplina $d.codigo
    $b1 = @($d.bimestres) | Where-Object { $_.bimestre -eq 1 } | Select-Object -First 1
    Test-Valor 'Boletim: media do 1o bimestre' 7.00 $b1.media
    Test-Valor 'Boletim: media anual' 7.00 $d.mediaAnual
    Test-Valor 'Boletim: avaliacoes pendentes' 0 $d.avaliacoesPendentes
    Test-Valor 'Boletim: total de aulas na disciplina' 4 $d.frequencia.totalAulas
    Test-Valor 'Boletim: presencas' 2 $d.frequencia.presencas
    Test-Valor 'Boletim: atrasos' 1 $d.frequencia.atrasos
    Test-Valor 'Boletim: faltas' 1 $d.frequencia.faltas
    Test-Valor 'Boletim: percentual de frequencia na disciplina' 75.00 $d.frequencia.percentual
    Test-Valor 'Boletim: abaixo do minimo de frequencia' $false $d.frequencia.abaixoDoMinimo
    Test-Valor 'Boletim: frequencia geral' 75.00 $b.frequenciaGeral.percentual

    $r = Invoke-Api -Metodo GET -Caminho ('/api/frequencias/alunos/' + $alunoId + '?anoLetivo=' + $ano)
    Assert-Status $r 200 'GET /api/frequencias/alunos/{id}'
    $f = $r.Json
    Test-Valor 'Frequencia: aluno' $alunoId $f.alunoId
    $fd = @($f.disciplinas) | Where-Object { [string]$_.disciplinaId -eq [string]$disciplinaId } | Select-Object -First 1
    Assert-Condicao 'Frequencia: disciplina presente' ($null -ne $fd) $r.Texto
    Test-Valor 'Frequencia: total de aulas na disciplina' 4 $fd.frequencia.totalAulas
    Test-Valor 'Frequencia: presencas + atrasos' 3 ($fd.frequencia.presencas + $fd.frequencia.atrasos)
    Test-Valor 'Frequencia: faltas' 1 $fd.frequencia.faltas
    Test-Valor 'Frequencia: percentual na disciplina' 75.00 $fd.frequencia.percentual
    Test-Valor 'Frequencia: abaixo do minimo' $false $fd.frequencia.abaixoDoMinimo
    Test-Valor 'Frequencia: percentual geral' 75.00 $f.geral.percentual
}
