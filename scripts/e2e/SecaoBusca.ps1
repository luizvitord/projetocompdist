# Secao "busca": parametro busca das listagens. Para cada endpoint, cria um registro com um texto
# unico com acento, busca por parte dele em minusculas e sem acento, confere que ele vem e que um
# registro parecido (mesmo texto, outro identificador) nao vem; depois combina a busca com um
# filtro existente e confere o 400 para busca com mais de 100 caracteres.
#
# O arquivo fica so com ASCII (o 5.1 le scripts sem BOM na codificacao ANSI): os acentos dos dados
# sao montados com [char] e enviados em UTF-8 pelo Invoke-Api.

$script:Ac = @{
    a_agudo  = [char]0x00E1   # a com acento agudo
    a_til    = [char]0x00E3   # a com til
    c_cedil  = [char]0x00E7   # c cedilha
    e_circ   = [char]0x00EA   # e circunflexo
    i_agudo  = [char]0x00ED   # i com acento agudo
}

function Invoke-SecaoBusca {
    Initialize-Admin
    Test-BuscaAlunos
    Test-BuscaProfessores
    Test-BuscaTecnicos
    Test-BuscaDisciplinas
    Test-BuscaTurmas
    Test-BuscaMatriculas
    Test-BuscaAvaliacoes
}

# GET <caminho>?busca=<texto>&size=100[&filtros]
function Get-Busca([string]$Caminho, [string]$Busca, [string]$Filtros = '') {
    $url = $Caminho + '?size=100&busca=' + [uri]::EscapeDataString($Busca)
    if ($Filtros) { $url += '&' + $Filtros }
    return Invoke-Api -Metodo GET -Caminho $url
}

function Get-IdsBusca($Resposta) { return @((Get-Conteudo $Resposta) | ForEach-Object { [long]$_.id }) }

# O registro esperado vem e o de controle (se houver) nao vem
function Test-BuscaTraz([string]$Descricao, $Resposta, $IdEsperado, $IdAusente = $null) {
    $ids = Get-IdsBusca $Resposta
    $ok = ($Resposta.Status -eq 200) -and ($ids -contains [long]$IdEsperado)
    if ($null -ne $IdAusente) { $ok = $ok -and ($ids -notcontains [long]$IdAusente) }
    $detalhe = 'esperado id ' + $IdEsperado
    if ($null -ne $IdAusente) { $detalhe += ' e sem o id ' + $IdAusente }
    Test-Condicao $Descricao $ok ($detalhe + '; ids: ' + (Format-Valor $ids) + '; ' + (Get-Resumo $Resposta))
}

function Test-BuscaNaoTraz([string]$Descricao, $Resposta, $IdAusente) {
    $ids = Get-IdsBusca $Resposta
    $ok = ($Resposta.Status -eq 200) -and ($ids -notcontains [long]$IdAusente)
    Test-Condicao $Descricao $ok ('sem o id ' + $IdAusente + '; ids: ' + (Format-Valor $ids) + '; ' + (Get-Resumo $Resposta))
}

function Test-BuscaLonga([string]$Caminho) {
    $r = Invoke-Api -Metodo GET -Caminho ($Caminho + '?busca=' + ('a' * 101))
    Test-ErroValidacao $r @('busca') ('GET ' + $Caminho + '?busca= com 101 caracteres')
    $r = Invoke-Api -Metodo GET -Caminho ($Caminho + '?size=1&busca=' + ('a' * 100))
    Test-Status $r 200 ('GET ' + $Caminho + '?busca= com 100 caracteres (no limite)')
}

function Test-BuscaAlunos {
    $id1 = New-IdUnico
    $id2 = New-IdUnico
    $nome = 'Concei' + $Ac.c_cedil + $Ac.a_til + 'o'
    $alvo = New-Aluno ('Maria ' + $nome + ' ' + $id1)
    $controle = New-Aluno ('Maria ' + $nome + ' ' + $id2)

    Test-BuscaTraz 'GET /api/alunos?busca= parte do nome, minusculas e sem acento' (Get-Busca '/api/alunos' ('conceicao ' + $id1)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/alunos?busca= numero de matricula' (Get-Busca '/api/alunos' ([string]$alvo.matricula)) $alvo.id $controle.id
    # New-Aluno gera o email com outro identificador: busca pela parte antes do @ do email gravado
    $parteEmail = ([string]$alvo.email).Split('@')[0] + '@'
    Test-BuscaTraz 'GET /api/alunos?busca= parte do email' (Get-Busca '/api/alunos' $parteEmail) $alvo.id $controle.id
    $r = Get-Busca '/api/alunos' '   '
    Test-Condicao 'GET /api/alunos?busca= so com espacos nao filtra' (($r.Status -eq 200) -and ([long]$r.Json.page.totalElements -ge 2)) (Get-Resumo $r)

    # Combinada (E) com o filtro turmaId
    $turma = New-Turma
    $outraTurma = New-Turma
    New-Matricula $alvo $turma.id | Out-Null
    Test-BuscaTraz 'GET /api/alunos?busca=&turmaId= da turma do aluno' (Get-Busca '/api/alunos' ('conceicao ' + $id1) ('turmaId=' + $turma.id)) $alvo.id
    Test-BuscaNaoTraz 'GET /api/alunos?busca=&turmaId= de outra turma' (Get-Busca '/api/alunos' ('conceicao ' + $id1) ('turmaId=' + $outraTurma.id)) $alvo.id
    Test-BuscaLonga '/api/alunos'
}

function Test-BuscaProfessores {
    $id1 = New-IdUnico
    $id2 = New-IdUnico
    $qualificacao = 'Licenciatura em Educa' + $Ac.c_cedil + $Ac.a_til + 'o F' + $Ac.i_agudo + 'sica ' + $id1
    $r = Invoke-Api -Metodo POST -Caminho '/api/professores' -Corpo @{
        nome = 'Professor Busca ' + $id1; qualificacao = $qualificacao; email = 'prof.busca.' + $id1 + '@e2e.distrischool.com'; telefone = '11999990000'
    }
    Assert-Preparacao $r 201 'POST /api/professores (busca)'
    $alvo = $r.Json
    $controle = New-Professor ('Professor Busca ' + $id2)

    Test-BuscaTraz 'GET /api/professores?busca= parte da qualificacao, minusculas e sem acento' (Get-Busca '/api/professores' ('educacao fisica ' + $id1)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/professores?busca= parte do email' (Get-Busca '/api/professores' ('prof.busca.' + $id1)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/professores?busca= parte do nome' (Get-Busca '/api/professores' ('professor busca ' + $id1)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/professores?busca=&nome= que corresponde' (Get-Busca '/api/professores' ('educacao fisica ' + $id1) 'nome=professor%20busca') $alvo.id
    Test-BuscaNaoTraz 'GET /api/professores?busca=&nome= que nao corresponde' (Get-Busca '/api/professores' ('educacao fisica ' + $id1) 'nome=inexistente') $alvo.id
    Test-BuscaLonga '/api/professores'
}

function Test-BuscaTecnicos {
    $id1 = New-IdUnico
    $id2 = New-IdUnico
    $cpf = New-Cpf
    $corpo = @{ nome = 'Tecnico Busca ' + $id1; cpf = $cpf; cargo = 'SECRETARIO'; setor = 'Secretaria Acad' + $Ac.e_circ + 'mica ' + $id1
        email = 'tec.busca.' + $id1 + '@e2e.distrischool.com'; telefone = '11955554444'; dataAdmissao = (Format-Iso (Get-Hoje).AddYears(-1)) }
    $r = Invoke-Api -Metodo POST -Caminho '/api/tecnicos' -Corpo $corpo
    Assert-Preparacao $r 201 'POST /api/tecnicos (busca)'
    $alvo = $r.Json
    $r = Invoke-Api -Metodo POST -Caminho '/api/tecnicos' -Corpo @{ nome = 'Tecnico Busca ' + $id2; cpf = (New-Cpf); cargo = 'TECNICO_TI'; setor = 'Secretaria Academica ' + $id2
        email = 'tec.busca.' + $id2 + '@e2e.distrischool.com'; telefone = '11955554444'; dataAdmissao = (Format-Iso (Get-Hoje).AddYears(-1)) }
    Assert-Preparacao $r 201 'POST /api/tecnicos (controle da busca)'
    $controle = $r.Json

    Test-BuscaTraz 'GET /api/tecnicos?busca= parte do setor, minusculas e sem acento' (Get-Busca '/api/tecnicos' ('secretaria academica ' + $id1)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/tecnicos?busca= parte do email' (Get-Busca '/api/tecnicos' ('tec.busca.' + $id1)) $alvo.id $controle.id
    Test-BuscaNaoTraz 'GET /api/tecnicos?busca= com o CPF nao encontra (CPF fora da busca, LGPD)' (Get-Busca '/api/tecnicos' $cpf) $alvo.id
    Test-BuscaTraz 'GET /api/tecnicos?busca=&cargo= que corresponde' (Get-Busca '/api/tecnicos' ('tecnico busca ' + $id1) 'cargo=SECRETARIO') $alvo.id
    Test-BuscaNaoTraz 'GET /api/tecnicos?busca=&cargo= que nao corresponde' (Get-Busca '/api/tecnicos' ('tecnico busca ' + $id1) 'cargo=TECNICO_TI') $alvo.id
    Test-BuscaLonga '/api/tecnicos'
}

function Test-BuscaDisciplinas {
    $id1 = New-IdUnico
    $id2 = New-IdUnico
    $nome = 'Educa' + $Ac.c_cedil + $Ac.a_til + 'o F' + $Ac.i_agudo + 'sica'
    $r = Invoke-Api -Metodo POST -Caminho '/api/disciplinas' -Corpo @{ codigo = 'DB' + $id1; nome = $nome + ' ' + $id1; cargaHoraria = 80 }
    Assert-Preparacao $r 201 'POST /api/disciplinas (busca)'
    $alvo = $r.Json
    $controle = New-Disciplina ($nome + ' ' + $id2)

    Test-BuscaTraz 'GET /api/disciplinas?busca= parte do nome, minusculas e sem acento' (Get-Busca '/api/disciplinas' ('educacao fisica ' + $id1)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/disciplinas?busca= parte do codigo em minusculas' (Get-Busca '/api/disciplinas' ('db' + $id1)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/disciplinas?busca=&ativa=true' (Get-Busca '/api/disciplinas' ('educacao fisica ' + $id1) 'ativa=true') $alvo.id
    Test-BuscaNaoTraz 'GET /api/disciplinas?busca=&ativa=false (a disciplina esta ativa)' (Get-Busca '/api/disciplinas' ('educacao fisica ' + $id1) 'ativa=false') $alvo.id
    Test-BuscaLonga '/api/disciplinas'
}

function Test-BuscaTurmas {
    $id1 = New-IdUnico
    $ano = Get-AnoLetivo
    $r = Invoke-Api -Metodo POST -Caminho '/api/turmas' -Corpo @{ codigo = 'TB-' + $id1; nome = 'Turma A' + $Ac.c_cedil + $Ac.a_til + 'o ' + $id1; anoLetivo = $ano; turno = 'MANHA'; capacidade = 30 }
    Assert-Preparacao $r 201 'POST /api/turmas (busca)'
    $alvo = $r.Json
    $controle = New-Turma

    Test-BuscaTraz 'GET /api/turmas?busca= parte do nome, minusculas e sem acento' (Get-Busca '/api/turmas' ('turma acao ' + $id1)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/turmas?busca= parte do codigo em minusculas' (Get-Busca '/api/turmas' ('tb-' + $id1)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/turmas?busca=&anoLetivo= do ano da turma' (Get-Busca '/api/turmas' ('turma acao ' + $id1) ('anoLetivo=' + $ano)) $alvo.id
    Test-BuscaNaoTraz 'GET /api/turmas?busca=&anoLetivo= de outro ano' (Get-Busca '/api/turmas' ('turma acao ' + $id1) ('anoLetivo=' + ($ano + 1))) $alvo.id
    Test-BuscaLonga '/api/turmas'
}

function Test-BuscaMatriculas {
    $id1 = New-IdUnico
    $id2 = New-IdUnico
    $joao = 'Jo' + $Ac.a_til + 'o Busca '
    $turma = New-Turma
    $outraTurma = New-Turma
    $alvoAluno = New-Aluno ($joao + $id1)
    $outroAluno = New-Aluno ($joao + $id2)
    $alvo = New-Matricula $alvoAluno $turma.id
    $controle = New-Matricula $outroAluno $outraTurma.id

    Test-BuscaTraz 'GET /api/matriculas?busca= parte do nome do aluno, minusculas e sem acento' (Get-Busca '/api/matriculas' ('joao busca ' + $id1)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/matriculas?busca= numero de matricula do aluno' (Get-Busca '/api/matriculas' ([string]$alvoAluno.matricula)) $alvo.id $controle.id
    $codigoTurma = ([string]$turma.codigo).ToLower()
    Test-BuscaTraz 'GET /api/matriculas?busca= codigo da turma em minusculas (replica local)' (Get-Busca '/api/matriculas' $codigoTurma) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/matriculas?busca= nome da turma (replica local)' (Get-Busca '/api/matriculas' ([string]$turma.nome)) $alvo.id $controle.id
    Test-BuscaTraz 'GET /api/matriculas?busca=&status=ATIVA' (Get-Busca '/api/matriculas' ('joao busca ' + $id1) 'status=ATIVA') $alvo.id
    Test-BuscaNaoTraz 'GET /api/matriculas?busca=&status=CANCELADA' (Get-Busca '/api/matriculas' ('joao busca ' + $id1) 'status=CANCELADA') $alvo.id
    Test-BuscaLonga '/api/matriculas'
}

function Test-BuscaAvaliacoes {
    $id1 = New-IdUnico
    $id2 = New-IdUnico
    $quimica = 'Qu' + $Ac.i_agudo + 'mica'
    $turma = New-Turma
    $disciplina = New-Disciplina ($quimica + ' ' + $id1)
    Set-Oferta $turma.id $disciplina.id | Out-Null
    $aluno = New-Aluno
    New-Matricula $aluno $turma.id | Out-Null
    Wait-OfertaNoEscolar $aluno.id $disciplina.id 'Preparacao: oferta replicada no gestao-escolar-service (busca)'

    $avaliacao = 'Avalia' + $Ac.c_cedil + $Ac.a_til + 'o Busca '
    $criar = {
        param($Descricao, $Bimestre)
        $r = Invoke-Api -Metodo POST -Caminho '/api/avaliacoes' -Corpo @{ turmaId = $turma.id; disciplinaId = $disciplina.id; bimestre = $Bimestre; tipo = 'PROVA'
            descricao = $Descricao; peso = 1; notaMaxima = 10; data = (Format-Iso (Get-Hoje)) }
        Assert-Preparacao $r 201 'POST /api/avaliacoes (busca)'
        return $r.Json
    }
    $alvo = & $criar ($avaliacao + $id1) 2
    $controle = & $criar ($avaliacao + $id2) 3

    Test-BuscaTraz 'GET /api/avaliacoes?busca= parte da descricao, minusculas e sem acento' (Get-Busca '/api/avaliacoes' ('avaliacao busca ' + $id1)) $alvo.id $controle.id
    $r = Get-Busca '/api/avaliacoes' ('quimica ' + $id1)
    $ids = Get-IdsBusca $r
    Test-Condicao 'GET /api/avaliacoes?busca= nome da disciplina (replica local) traz as duas da oferta' (($r.Status -eq 200) -and ($ids -contains [long]$alvo.id) -and ($ids -contains [long]$controle.id)) ('ids: ' + (Format-Valor $ids))
    $codigoTurma = ([string]$turma.codigo).ToLower()
    Test-BuscaTraz 'GET /api/avaliacoes?busca= codigo da turma em minusculas (replica local)' (Get-Busca '/api/avaliacoes' $codigoTurma) $alvo.id
    Test-BuscaTraz 'GET /api/avaliacoes?busca=&bimestre= que corresponde' (Get-Busca '/api/avaliacoes' ('quimica ' + $id1) 'bimestre=2') $alvo.id $controle.id
    Test-BuscaNaoTraz 'GET /api/avaliacoes?busca=&bimestre= que nao corresponde' (Get-Busca '/api/avaliacoes' ('avaliacao busca ' + $id1) 'bimestre=4') $alvo.id
    Test-BuscaLonga '/api/avaliacoes'
}
