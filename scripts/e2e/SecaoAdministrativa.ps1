# Secao "administrativa": disciplinas, professores, tecnicos administrativos e turmas no
# gestao-administrativa-service, e o efeito das mudancas na replica do gestao-escolar-service
# (conferido pela API do gestao-escolar, nunca pelo banco).

function Invoke-SecaoAdministrativa {
    Initialize-Admin
    Test-Disciplinas
    Test-Professores
    Test-Tecnicos
    Test-Turmas
    Test-RenomearDisciplinaOfertada
}

function Test-Disciplinas {
    $id = New-IdUnico
    $codigo = 'DA' + $id
    $r = Invoke-Api -Metodo POST -Caminho '/api/disciplinas' -Corpo @{ codigo = $codigo; nome = 'Quimica E2E ' + $id; cargaHoraria = 60 }
    Assert-Status $r 201 'POST /api/disciplinas'
    $d = $r.Json
    Test-Condicao 'Location aponta para a disciplina criada' ([string]$r.Headers['Location'] -like ('*/api/disciplinas/' + $d.id)) ('Location: ' + (Format-Valor $r.Headers['Location']))
    Test-Valor 'Disciplina criada ativa' $true $d.ativa
    Test-Valor 'Carga horaria gravada' 60 $d.cargaHoraria

    $r = Invoke-Api -Metodo POST -Caminho '/api/disciplinas' -Corpo @{ codigo = $codigo.ToLower(); nome = 'Outra' }
    Test-Erro $r 409 'POST /api/disciplinas com codigo ja usado (minusculas)'
    $r = Invoke-Api -Metodo POST -Caminho '/api/disciplinas' -Corpo @{ codigo = ('X' * 21); nome = ''; cargaHoraria = -5 }
    Test-ErroValidacao $r @('codigo', 'nome', 'cargaHoraria') 'POST /api/disciplinas com codigo longo, nome vazio e carga negativa'

    $r = Invoke-Api -Metodo GET -Caminho ('/api/disciplinas/' + $d.id)
    Test-Status $r 200 'GET /api/disciplinas/{id}'
    Test-Valor 'GET /api/disciplinas/{id} devolve o codigo' $codigo $r.Json.codigo
    Test-Erro (Invoke-Api -Metodo GET -Caminho '/api/disciplinas/999999999') 404 'GET /api/disciplinas/{id} inexistente'

    $r = Invoke-Api -Metodo GET -Caminho ('/api/disciplinas?nome=' + ('quimica e2e ' + $id).Replace(' ', '%20'))
    Test-Status $r 200 'GET /api/disciplinas?nome= (parte do nome, sem diferenciar maiusculas)'
    Test-Valor 'Filtro por nome traz so a disciplina' 1 @(Get-Conteudo $r).Count

    $r = Invoke-Api -Metodo PUT -Caminho ('/api/disciplinas/' + $d.id) -Corpo @{ codigo = $codigo; nome = 'Quimica Organica E2E ' + $id; cargaHoraria = 80 }
    Test-Status $r 200 'PUT /api/disciplinas/{id}'
    Test-Valor 'Disciplina renomeada' ('Quimica Organica E2E ' + $id) $r.Json.nome
    $outra = New-Disciplina
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/disciplinas/' + $d.id) -Corpo @{ codigo = $outra.codigo; nome = 'x' }
    Test-Erro $r 409 'PUT /api/disciplinas/{id} com o codigo de outra disciplina'
    $r = Invoke-Api -Metodo PUT -Caminho '/api/disciplinas/999999999' -Corpo @{ codigo = 'DX' + $id; nome = 'x' }
    Test-Erro $r 404 'PUT /api/disciplinas/{id} inexistente'

    # Exclusao logica; recusada enquanto a disciplina estiver ofertada
    $turma = New-Turma
    Set-Oferta $turma.id $outra.id | Out-Null
    $r = Invoke-Api -Metodo DELETE -Caminho ('/api/disciplinas/' + $outra.id)
    Test-Erro $r 409 'DELETE /api/disciplinas/{id} de disciplina ofertada'

    $r = Invoke-Api -Metodo DELETE -Caminho ('/api/disciplinas/' + $d.id)
    Test-Status $r 204 'DELETE /api/disciplinas/{id} sem ofertas'
    $r = Invoke-Api -Metodo GET -Caminho ('/api/disciplinas/' + $d.id)
    Test-Valor 'Disciplina excluida continua consultavel, inativa' $false $r.Json.ativa
    $r = Invoke-Api -Metodo GET -Caminho ('/api/disciplinas?size=50&nome=' + $id)
    Test-Condicao 'Listagem padrao nao traz a disciplina inativa' (@((Get-Conteudo $r) | Where-Object { $_.id -eq $d.id }).Count -eq 0)
    $r = Invoke-Api -Metodo GET -Caminho ('/api/disciplinas?size=50&ativa=false&nome=' + $id)
    Test-Condicao 'Listagem com ativa=false traz a disciplina inativa' (@((Get-Conteudo $r) | Where-Object { $_.id -eq $d.id }).Count -eq 1)
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id + '/disciplinas/' + $d.id) -Corpo @{}
    Test-Erro $r 409 'PUT /api/turmas/{id}/disciplinas/{disciplinaId} com disciplina inativa'
}

function Test-Professores {
    $id = New-IdUnico
    $email = 'prof.adm.' + $id + '@e2e.distrischool.com'
    $r = Invoke-Api -Metodo POST -Caminho '/api/professores' -Corpo @{ nome = 'Helena Prado ' + $id; qualificacao = 'Mestrado'; email = $email; telefone = '1133334444' }
    Assert-Status $r 201 'POST /api/professores'
    $p = $r.Json
    Test-Valor 'Professor criado com a qualificacao' 'Mestrado' $p.qualificacao

    $r = Invoke-Api -Metodo POST -Caminho '/api/professores' -Corpo @{ nome = 'Outro'; email = $email }
    Test-Erro $r 409 'POST /api/professores com email ja usado'
    $r = Invoke-Api -Metodo POST -Caminho '/api/professores' -Corpo @{ nome = ''; email = 'nao-e-email'; telefone = ('9' * 31) }
    Test-ErroValidacao $r @('nome', 'email', 'telefone') 'POST /api/professores com nome vazio, email invalido e telefone longo'

    $r = Invoke-Api -Metodo GET -Caminho ('/api/professores?nome=' + ('helena prado ' + $id).Replace(' ', '%20'))
    Test-Status $r 200 'GET /api/professores?nome='
    Test-Valor 'Filtro por nome traz so o professor' 1 @(Get-Conteudo $r).Count
    $r = Invoke-Api -Metodo GET -Caminho '/api/professores?size=5'
    Test-Condicao 'GET /api/professores (paginado) -> 200 com pagina' (($r.Status -eq 200) -and ($null -ne $r.Json.page)) (Get-Resumo $r)

    $r = Invoke-Api -Metodo GET -Caminho ('/api/professores/' + $p.id)
    Test-Status $r 200 'GET /api/professores/{id}'
    Test-Erro (Invoke-Api -Metodo GET -Caminho '/api/professores/999999999') 404 'GET /api/professores/{id} inexistente'

    $r = Invoke-Api -Metodo PUT -Caminho ('/api/professores/' + $p.id) -Corpo @{ nome = 'Helena Prado Souza ' + $id; qualificacao = 'Doutorado'; email = $email }
    Test-Status $r 200 'PUT /api/professores/{id}'
    Test-Valor 'Professor editado: qualificacao' 'Doutorado' $r.Json.qualificacao
    $outro = New-Professor
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/professores/' + $p.id) -Corpo @{ nome = 'x'; email = $outro.email }
    Test-Erro $r 409 'PUT /api/professores/{id} com o email de outro professor'
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/professores/' + $p.id) -Corpo @{ nome = 'x'; email = 'invalido' }
    Test-ErroValidacao $r @('email') 'PUT /api/professores/{id} com email invalido'
}

function Test-Tecnicos {
    $id = New-IdUnico
    $cpf = New-Cpf
    $email = 'tecnico.' + $id + '@e2e.distrischool.com'
    $setor = 'Setor ' + $id
    $corpo = @{ nome = 'Joana Lima ' + $id; cpf = $cpf; cargo = 'SECRETARIO'; setor = $setor; email = $email
        telefone = '11955554444'; dataAdmissao = (Format-Iso (Get-Hoje).AddYears(-1)) }
    $r = Invoke-Api -Metodo POST -Caminho '/api/tecnicos' -Corpo $corpo
    Assert-Status $r 201 'POST /api/tecnicos'
    $t = $r.Json
    Test-Valor 'Tecnico criado ativo' $true $t.ativo
    Test-Valor 'Tecnico criado com o cargo' 'SECRETARIO' $t.cargo

    # CPF invalido e duplicado; o CPF nunca aparece na resposta de erro
    $cpfInvalido = $cpf.Substring(0, 10) + ([string](([int][string]$cpf[10] + 1) % 10))
    $r = Invoke-Api -Metodo POST -Caminho '/api/tecnicos' -Corpo (Copy-Corpo $corpo @{ cpf = $cpfInvalido; email = 'outro.' + $email })
    Test-ErroValidacao $r @('cpf') 'POST /api/tecnicos com CPF invalido'
    Test-Condicao 'Resposta do CPF invalido nao repete o CPF' ($r.Texto -notmatch $cpfInvalido) $r.Texto
    $r = Invoke-Api -Metodo POST -Caminho '/api/tecnicos' -Corpo (Copy-Corpo $corpo @{ email = 'outro.' + $email })
    Test-Erro $r 409 'POST /api/tecnicos com CPF ja usado'
    Test-Condicao 'Resposta do CPF duplicado nao repete o CPF' ($r.Texto -notmatch $cpf) $r.Texto
    $r = Invoke-Api -Metodo POST -Caminho '/api/tecnicos' -Corpo (Copy-Corpo $corpo @{ cpf = (New-Cpf) })
    Test-Erro $r 409 'POST /api/tecnicos com email ja usado'
    $r = Invoke-Api -Metodo POST -Caminho '/api/tecnicos' -Corpo (Copy-Corpo $corpo @{ cpf = (New-Cpf); email = 'futuro.' + $email; dataAdmissao = (Format-Iso (Get-Hoje).AddDays(5)) })
    Test-ErroValidacao $r @('dataAdmissao') 'POST /api/tecnicos com dataAdmissao futura'
    $r = Invoke-Api -Metodo POST -Caminho '/api/tecnicos' -Corpo @{ nome = ''; cpf = ''; setor = ''; email = 'x' }
    Test-ErroValidacao $r @('nome', 'cpf', 'cargo', 'setor', 'email', 'dataAdmissao') 'POST /api/tecnicos sem os campos obrigatorios'

    # Filtros
    $outro = Invoke-Api -Metodo POST -Caminho '/api/tecnicos' -Corpo (Copy-Corpo $corpo @{
        nome = 'Marcos Lima ' + $id; cpf = (New-Cpf); cargo = 'TECNICO_TI'; email = 'ti.' + $email })
    Assert-Preparacao $outro 201 'POST /api/tecnicos (segundo tecnico do setor)'
    $r = Invoke-Api -Metodo GET -Caminho ('/api/tecnicos?setor=' + $setor.ToUpper().Replace(' ', '%20'))
    Test-Valor 'GET /api/tecnicos?setor= (sem diferenciar maiusculas) traz os 2 do setor' 2 @(Get-Conteudo $r).Count
    $r = Invoke-Api -Metodo GET -Caminho ('/api/tecnicos?cargo=TECNICO_TI&setor=' + $setor.Replace(' ', '%20'))
    $lista = @(Get-Conteudo $r)
    Test-Condicao 'GET /api/tecnicos?cargo=TECNICO_TI&setor= traz so o tecnico de TI' (($lista.Count -eq 1) -and ($lista[0].id -eq $outro.Json.id)) ('itens: ' + $lista.Count)
    $r = Invoke-Api -Metodo GET -Caminho ('/api/tecnicos?nome=' + ('joana lima ' + $id).Replace(' ', '%20'))
    Test-Valor 'GET /api/tecnicos?nome= traz so a tecnica' 1 @(Get-Conteudo $r).Count
    $r = Invoke-Api -Metodo GET -Caminho ('/api/tecnicos/' + $t.id)
    Test-Status $r 200 'GET /api/tecnicos/{id}'
    Test-Erro (Invoke-Api -Metodo GET -Caminho '/api/tecnicos/999999999') 404 'GET /api/tecnicos/{id} inexistente'

    $r = Invoke-Api -Metodo PUT -Caminho ('/api/tecnicos/' + $t.id) -Corpo (Copy-Corpo $corpo @{ cargo = 'COORDENADOR' })
    Test-Status $r 200 'PUT /api/tecnicos/{id}'
    Test-Valor 'Tecnico editado: cargo' 'COORDENADOR' $r.Json.cargo
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/tecnicos/' + $t.id) -Corpo (Copy-Corpo $corpo @{ cpf = $outro.Json.cpf })
    Test-Erro $r 409 'PUT /api/tecnicos/{id} com o CPF de outro tecnico'
    Test-Condicao 'Resposta do CPF duplicado na edicao nao repete o CPF' ($r.Texto -notmatch [string]$outro.Json.cpf) $r.Texto

    $r = Invoke-Api -Metodo DELETE -Caminho ('/api/tecnicos/' + $t.id)
    Test-Status $r 204 'DELETE /api/tecnicos/{id}'
    $r = Invoke-Api -Metodo GET -Caminho ('/api/tecnicos?setor=' + $setor.Replace(' ', '%20'))
    Test-Condicao 'Tecnico excluido some da listagem padrao (ativos)' (@((Get-Conteudo $r) | Where-Object { $_.id -eq $t.id }).Count -eq 0) ('itens: ' + @(Get-Conteudo $r).Count)
    $r = Invoke-Api -Metodo GET -Caminho ('/api/tecnicos?ativo=false&setor=' + $setor.Replace(' ', '%20'))
    $inativos = @(Get-Conteudo $r)
    Test-Condicao 'Tecnico excluido aparece com ativo=false' ((@($inativos | Where-Object { $_.id -eq $t.id }).Count -eq 1) -and (@($inativos | Where-Object { $_.ativo }).Count -eq 0)) ('itens: ' + $inativos.Count)
}

# Copia um corpo trocando alguns campos
function Copy-Corpo([hashtable]$Corpo, [hashtable]$Trocas) {
    $novo = @{}
    foreach ($k in $Corpo.Keys) { $novo[$k] = $Corpo[$k] }
    foreach ($k in $Trocas.Keys) { $novo[$k] = $Trocas[$k] }
    return $novo
}

function Test-Turmas {
    $ano = Get-AnoLetivo
    $id = New-IdUnico
    $codigo = 'TA-' + $id
    $r = Invoke-Api -Metodo POST -Caminho '/api/turmas' -Corpo @{ codigo = $codigo; nome = 'Turma Adm ' + $id; anoLetivo = $ano; turno = 'NOITE'; capacidade = 25 }
    Assert-Status $r 201 'POST /api/turmas'
    $turma = $r.Json
    Test-Valor 'Turma criada sem disciplinas' 0 @($turma.disciplinas).Count

    Test-Erro (Invoke-Api -Metodo POST -Caminho '/api/turmas' -Corpo @{ codigo = $codigo.ToLower(); nome = 'x'; anoLetivo = $ano; turno = 'MANHA' }) 409 'POST /api/turmas com codigo ja usado'
    $r = Invoke-Api -Metodo POST -Caminho '/api/turmas' -Corpo @{ codigo = ''; nome = ''; anoLetivo = 1999; capacidade = 0 }
    Test-ErroValidacao $r @('codigo', 'nome', 'anoLetivo', 'turno', 'capacidade') 'POST /api/turmas com campos invalidos'

    $r = Invoke-Api -Metodo GET -Caminho ('/api/turmas/' + $turma.id)
    Test-Status $r 200 'GET /api/turmas/{id}'
    Test-Erro (Invoke-Api -Metodo GET -Caminho '/api/turmas/999999999') 404 'GET /api/turmas/{id} inexistente'

    $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id) -Corpo @{ codigo = $codigo; nome = 'Turma Adm Editada ' + $id; anoLetivo = $ano; turno = 'TARDE'; capacidade = 30 }
    Test-Status $r 200 'PUT /api/turmas/{id}'
    Test-Valor 'Turma editada: turno' 'TARDE' $r.Json.turno
    Test-Valor 'Turma editada: capacidade' 30 $r.Json.capacidade
    $outra = New-Turma
    Test-Erro (Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id) -Corpo @{ codigo = $outra.codigo; nome = 'x'; anoLetivo = $ano; turno = 'TARDE' }) 409 'PUT /api/turmas/{id} com o codigo de outra turma'

    # Oferta de disciplina, com atribuicao automatica do professor
    $disciplina = New-Disciplina
    $professor = New-Professor
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id + '/disciplinas/' + $disciplina.id) -Corpo @{ professorId = $professor.id }
    Test-Status $r 200 'PUT /api/turmas/{id}/disciplinas/{disciplinaId} (ofertar com professor)'
    $oferta = @($r.Json.disciplinas) | Where-Object { [string]$_.disciplinaId -eq [string]$disciplina.id } | Select-Object -First 1
    Test-Valor 'Oferta com o professor responsavel' $professor.id $oferta.professorId
    Test-Condicao 'Professor da oferta atribuido a turma automaticamente' (@($r.Json.professores | Where-Object { $_.id -eq $professor.id }).Count -eq 1) $r.Texto
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id + '/disciplinas/' + $disciplina.id) -Corpo @{ professorId = $professor.id }
    Test-Valor 'Repetir a mesma oferta e idempotente (uma oferta so)' 1 @($r.Json.disciplinas).Count
    Test-Erro (Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id + '/disciplinas/' + $disciplina.id) -Corpo @{ professorId = 999999999 }) 404 'Ofertar com professor inexistente'
    Test-Erro (Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id + '/disciplinas/999999999') -Corpo @{}) 404 'Ofertar disciplina inexistente'

    $r = Invoke-Api -Metodo GET -Caminho ('/api/turmas?anoLetivo=' + $ano + '&turno=TARDE&professorId=' + $professor.id)
    $lista = @(Get-Conteudo $r)
    Test-Condicao 'GET /api/turmas?anoLetivo=&turno=&professorId= traz so a turma' (($lista.Count -eq 1) -and ($lista[0].id -eq $turma.id)) ('itens: ' + $lista.Count)
    $r = Invoke-Api -Metodo GET -Caminho ('/api/turmas?turno=NOITE&professorId=' + $professor.id)
    Test-Valor 'Filtro por turno exclui a turma de outro turno' 0 @(Get-Conteudo $r).Count

    # Professor atribuido sem oferta: atribuir e remover
    $outroProfessor = New-Professor
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id + '/professores/' + $outroProfessor.id)
    Test-Status $r 200 'PUT /api/turmas/{id}/professores/{professorId}'
    Test-Condicao 'Professor atribuido aparece na turma' (@($r.Json.professores | Where-Object { $_.id -eq $outroProfessor.id }).Count -eq 1) $r.Texto
    Test-Erro (Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id + '/professores/999999999')) 404 'Atribuir professor inexistente'
    $r = Invoke-Api -Metodo DELETE -Caminho ('/api/turmas/' + $turma.id + '/professores/' + $outroProfessor.id)
    Test-Status $r 204 'DELETE /api/turmas/{id}/professores/{professorId}'
    $r = Invoke-Api -Metodo GET -Caminho ('/api/turmas/' + $turma.id)
    Test-Condicao 'Professor removido nao aparece mais na turma' (@($r.Json.professores | Where-Object { $_.id -eq $outroProfessor.id }).Count -eq 0) $r.Texto
    Test-Erro (Invoke-Api -Metodo DELETE -Caminho ('/api/turmas/' + $turma.id + '/professores/' + $outroProfessor.id)) 404 'Remover professor que nao esta na turma'

    # Na replica do gestao-escolar: um aluno matriculado, com chamada e nota (historico)
    $aluno = New-Aluno
    New-Matricula $aluno $turma.id | Out-Null
    Wait-OfertaNoEscolar $aluno.id $disciplina.id 'Oferta replicada no gestao-escolar-service (boletim do aluno lista a disciplina)'
    $chamada = New-Chamada $turma.id $disciplina.id (Get-Hoje) @(@{ Aluno = $aluno; Status = 'PRESENTE' })
    $avaliacao = New-Avaliacao $turma.id $disciplina.id 1 'PROVA'
    Add-Notas $avaliacao.id @(@{ Aluno = $aluno; Valor = 8 }) | Out-Null

    # Codigo da turma alterado chega a replica (o boletim mostra o codigo da turma de cada matricula)
    $novoCodigo = 'TA2-' + $id
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turma.id) -Corpo @{ codigo = $novoCodigo; nome = 'Turma Adm Editada ' + $id; anoLetivo = $ano; turno = 'TARDE'; capacidade = 30 }
    Assert-Status $r 200 'PUT /api/turmas/{id} (novo codigo)'
    $espera = Wait-Ate -Condicao {
        $b = Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $aluno.id + '/boletim?anoLetivo=' + $ano)
        ($b.Status -eq 200) -and (@($b.Json.matriculas)[0].turmaCodigo -eq $novoCodigo)
    }
    Test-Condicao 'Novo codigo da turma chega a replica do gestao-escolar (schedule.updated)' $espera.Ok $espera.Detalhe

    # Retirar a oferta: o gestao-escolar passa a recusar chamadas e avaliacoes nela (409)
    $r = Invoke-Api -Metodo DELETE -Caminho ('/api/turmas/' + $turma.id + '/disciplinas/' + $disciplina.id)
    Test-Status $r 204 'DELETE /api/turmas/{id}/disciplinas/{disciplinaId}'
    Test-Erro (Invoke-Api -Metodo DELETE -Caminho ('/api/turmas/' + $turma.id + '/disciplinas/' + $disciplina.id)) 404 'Retirar oferta que nao existe mais'
    # Sonda sem efeito colateral: chamada com um aluno de fora da turma responde 400 enquanto a
    # oferta existe na replica e 409 depois que o schedule.updated da retirada chega
    $foraDaTurma = New-Aluno
    $sonda = @{ turmaId = $turma.id; disciplinaId = $disciplina.id; data = (Format-Iso ((Get-Hoje).AddDays(-1)))
        registros = @(@{ matriculaAluno = [string]$foraDaTurma.matricula; status = 'PRESENTE' }) }
    $espera = Wait-Ate -Condicao { (Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo $sonda).Status -eq 409 }
    $corpoChamada = @{ turmaId = $turma.id; disciplinaId = $disciplina.id; data = (Format-Iso ((Get-Hoje).AddDays(-1)))
        registros = @(@{ matriculaAluno = [string]$aluno.matricula; status = 'PRESENTE' }) }
    $r = Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo $corpoChamada
    Test-Erro $r 409 ('Chamada em disciplina retirada da turma (' + $espera.Detalhe + ')')
    $r = Invoke-Api -Metodo POST -Caminho '/api/avaliacoes' -Corpo @{ turmaId = $turma.id; disciplinaId = $disciplina.id; bimestre = 2; tipo = 'PROVA'; data = (Format-Iso (Get-Hoje)) }
    Test-Erro $r 409 'Avaliacao em disciplina retirada da turma'
    $r = Invoke-Api -Metodo GET -Caminho ('/api/frequencias/chamadas/' + $chamada.id)
    Test-Status $r 200 'Chamada antiga da oferta retirada continua consultavel'

    # Excluir a turma: novas matriculas recusadas (409), historico mantido
    $r = Invoke-Api -Metodo DELETE -Caminho ('/api/turmas/' + $turma.id)
    Test-Status $r 204 'DELETE /api/turmas/{id}'
    Test-Erro (Invoke-Api -Metodo GET -Caminho ('/api/turmas/' + $turma.id)) 404 'GET /api/turmas/{id} da turma excluida'
    # Sonda sem efeito colateral: o aluno ja matriculado recebe 409 "ja tem matricula vigente"
    # enquanto a turma existe na replica e 409 "foi removida" depois do schedule.updated REMOVIDA
    $sonda = @{ matriculaAluno = [string]$aluno.matricula; turmaId = $turma.id }
    $espera = Wait-Ate -Condicao {
        $p = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo $sonda
        ($p.Status -eq 409) -and ([string]$p.Json.detail -like '*removida*')
    }
    $novoAluno = New-Aluno
    $corpoMatricula = @{ matriculaAluno = [string]$novoAluno.matricula; turmaId = $turma.id }
    $r = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo $corpoMatricula
    Test-Erro $r 409 ('Nova matricula em turma excluida (' + $espera.Detalhe + ')')
    $r = Invoke-Api -Metodo GET -Caminho ('/api/matriculas?turmaId=' + $turma.id)
    Test-Valor 'Matricula antiga na turma excluida continua listada' 1 @(Get-Conteudo $r).Count
    $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $aluno.id + '/boletim?anoLetivo=' + $ano)
    Test-Status $r 200 'Boletim do aluno da turma excluida continua disponivel'
    $d = @($r.Json.disciplinas) | Where-Object { [string]$_.disciplinaId -eq [string]$disciplina.id } | Select-Object -First 1
    Test-Condicao 'Boletim mantem a nota e a frequencia da turma excluida' (($null -ne $d) -and ([decimal]$d.mediaAnual -eq 8) -and ($d.frequencia.totalAulas -eq 1)) (Format-Valor ($r.Texto))
}

# Renomear uma disciplina ofertada: o novo nome chega a replica do gestao-escolar e ao texto das
# notificacoes (os dois consomem o schedule.updated publicado para cada turma que a oferta)
function Test-RenomearDisciplinaOfertada {
    $base = Initialize-Base
    $u = Initialize-Usuarios
    $aluno = $base.Alunos[0].Aluno
    $novoNome = 'Fisica Renomeada E2E ' + (New-IdUnico)
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/disciplinas/' + $base.Disciplina.id) -Corpo @{ codigo = $base.Disciplina.codigo; nome = $novoNome; cargaHoraria = 80 }
    Assert-Status $r 200 'PUT /api/disciplinas/{id} (renomear disciplina ofertada)'
    $base.Disciplina = $r.Json

    $espera = Wait-Ate -Condicao { $d = Get-DisciplinaNoBoletim $aluno.id $base.Disciplina.id; ($null -ne $d) -and ($d.nome -eq $novoNome) }
    Test-Condicao 'Novo nome da disciplina chega a replica do gestao-escolar-service' $espera.Ok $espera.Detalhe

    $avaliacao = New-Avaliacao $base.Turma.id $base.Disciplina.id 4 'ATIVIDADE'
    Add-Notas $avaliacao.id @(@{ Aluno = $aluno; Valor = 6.5 }) | Out-Null
    $espera = Wait-Notificacao $u.Responsavel.Token { $_.tipo -eq 'NOTA_LANCADA' -and ([string]$_.mensagem) -like ('*' + $novoNome + '*') }
    Test-Condicao 'Notificacao da nota seguinte usa o novo nome da disciplina' $espera.Ok $espera.Detalhe
}
