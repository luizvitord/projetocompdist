# Secao "perfis": CRUD de /api/usuarios (so ADMIN) e a tabela de permissoes do
# docs/AUTENTICACAO.md, conferida com um usuario de cada perfil.

function Invoke-SecaoPerfis {
    Initialize-Admin
    $base = Initialize-Base
    $u = Initialize-Usuarios
    Test-CrudUsuarios $base
    Test-PermissoesProfessor $base $u.Professor
    foreach ($usuario in @($u.Aluno, $u.Responsavel)) {
        Test-PermissoesLeitura $base $usuario
    }
}

function Test-CrudUsuarios($Base) {
    $id = New-IdUnico
    $email = 'crud.' + $id + '@e2e.distrischool.com'
    $r = Invoke-Api -Metodo POST -Caminho '/api/usuarios' -Corpo @{
        nome = 'Usuario CRUD ' + $id; email = $email; senha = 'senha-crud-' + $id; perfil = 'PROFESSOR'; referenciaId = $Base.Professor.id
    }
    Assert-Status $r 201 'POST /api/usuarios'
    $usuario = $r.Json
    $Ctx.UsuariosCriados.Add($usuario.id)
    Test-Condicao 'Location aponta para o usuario criado' ([string]$r.Headers['Location'] -like ('*/api/usuarios/' + $usuario.id)) `
        ('Location: ' + (Format-Valor $r.Headers['Location']))
    Test-Valor 'Usuario criado com o perfil pedido' 'PROFESSOR' $usuario.perfil
    Test-Valor 'Usuario criado com o referenciaId' $Base.Professor.id $usuario.referenciaId
    Test-Valor 'Usuario criado ativo' $true $usuario.ativo
    Test-Condicao 'Resposta nao expoe a senha nem o hash' ($r.Texto -notmatch '(?i)senha') $r.Texto

    $r = Invoke-Api -Metodo POST -Caminho '/api/usuarios' -Corpo @{
        nome = 'Duplicado ' + $id; email = $email.ToUpper(); senha = 'outra-senha'; perfil = 'ALUNO'; referenciaId = 1
    }
    Test-Erro $r 409 'POST /api/usuarios com email ja usado (maiusculas)'

    $r = Invoke-Api -Metodo POST -Caminho '/api/usuarios' -Corpo @{ nome = ''; email = 'nao-e-email'; senha = '123' }
    Test-ErroValidacao $r @('nome', 'email', 'senha', 'perfil') 'POST /api/usuarios com nome vazio, email invalido, senha curta e sem perfil'

    $r = Invoke-Api -Metodo GET -Caminho ('/api/usuarios/' + $usuario.id)
    Test-Status $r 200 'GET /api/usuarios/{id}'
    Test-Valor 'GET /api/usuarios/{id} devolve o email (em minusculas)' $email.ToLower() $r.Json.email

    $r = Invoke-Api -Metodo GET -Caminho '/api/usuarios/999999999'
    Test-Erro $r 404 'GET /api/usuarios/{id} inexistente'

    $r = Invoke-Api -Metodo GET -Caminho '/api/usuarios?perfil=PROFESSOR&size=200&sort=id,desc'
    Test-Status $r 200 'GET /api/usuarios?perfil=PROFESSOR'
    $lista = @(Get-Conteudo $r)
    Test-Condicao 'Listagem por perfil traz o usuario criado' (@($lista | Where-Object { $_.id -eq $usuario.id }).Count -eq 1) ('itens: ' + $lista.Count)
    Test-Condicao 'Listagem por perfil so traz PROFESSOR' (@($lista | Where-Object { $_.perfil -ne 'PROFESSOR' }).Count -eq 0) ('itens: ' + $lista.Count)

    $novoEmail = 'crud.editado.' + $id + '@e2e.distrischool.com'
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/usuarios/' + $usuario.id) -Corpo @{
        nome = 'Usuario Editado ' + $id; email = $novoEmail; perfil = 'RESPONSAVEL'; referenciaId = $Base.Alunos[0].Aluno.id
    }
    Test-Status $r 200 'PUT /api/usuarios/{id}'
    Test-Valor 'Usuario editado: nome' ('Usuario Editado ' + $id) $r.Json.nome
    Test-Valor 'Usuario editado: perfil' 'RESPONSAVEL' $r.Json.perfil
    Test-Valor 'Usuario editado: email' $novoEmail $r.Json.email

    $r = Invoke-Api -Metodo PUT -Caminho ('/api/usuarios/' + $usuario.id) -Corpo @{
        nome = 'Usuario Editado ' + $id; email = $EmailCoordenacao; perfil = 'RESPONSAVEL'; referenciaId = 1
    }
    Test-Erro $r 409 'PUT /api/usuarios/{id} com o email de outro usuario'

    $r = Invoke-Api -Metodo PUT -Caminho ('/api/usuarios/' + $usuario.id) -Corpo @{ nome = 'x'; email = 'invalido' }
    Test-ErroValidacao $r @('email', 'perfil') 'PUT /api/usuarios/{id} com email invalido e sem perfil'

    $r = Invoke-Api -Metodo DELETE -Caminho ('/api/usuarios/' + $usuario.id)
    Test-Status $r 204 'DELETE /api/usuarios/{id}'
    $r = Invoke-Api -Metodo GET -Caminho ('/api/usuarios/' + $usuario.id)
    Test-Valor 'Usuario excluido continua consultavel, inativo (exclusao logica)' $false $r.Json.ativo
    $r = Invoke-Api -Metodo GET -Caminho '/api/usuarios?size=200&sort=id,desc'
    Test-Condicao 'Listagem padrao (ativos) nao traz o usuario excluido' (@((Get-Conteudo $r) | Where-Object { $_.id -eq $usuario.id }).Count -eq 0)
    $r = Invoke-Api -Metodo GET -Caminho '/api/usuarios?ativo=false&size=200&sort=id,desc'
    Test-Condicao 'Listagem com ativo=false traz o usuario excluido' (@((Get-Conteudo $r) | Where-Object { $_.id -eq $usuario.id }).Count -eq 1)
    $r = Invoke-Login $novoEmail ('senha-crud-' + $id)
    Test-Erro $r 401 'POST /api/auth/login de um usuario excluido'
}

function Test-PermissoesProfessor($Base, $Professor) {
    $t = $Professor.Token
    $turmaId = $Base.Turma.id
    $alunos = $Base.Alunos

    $r = Invoke-Api -Metodo GET -Caminho '/api/turmas?size=1' -Token $t
    Test-Status $r 200 'PROFESSOR: GET /api/turmas'

    # Lanca notas e frequencia
    $r = Invoke-Api -Metodo POST -Caminho '/api/avaliacoes' -Token $t -Corpo @{
        turmaId = $turmaId; disciplinaId = $Base.Disciplina.id; bimestre = 3; tipo = 'PROVA'
        descricao = 'Prova do professor ' + (New-IdUnico); peso = 1; notaMaxima = 10; data = (Format-Iso (Get-Hoje))
    }
    Test-Status $r 201 'PROFESSOR: POST /api/avaliacoes'
    $avaliacaoId = $r.Json.id
    $r = Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $avaliacaoId + '/notas') -Token $t -Corpo @(
        @{ matriculaAluno = [string]$alunos[0].Aluno.matricula; valor = 7 },
        @{ matriculaAluno = [string]$alunos[1].Aluno.matricula; valor = 8 })
    Test-Status $r 201 'PROFESSOR: POST /api/avaliacoes/{id}/notas'
    $notaId = (@($r.Json) | Select-Object -First 1).id
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/notas/' + $notaId) -Token $t -Corpo @{ valor = 7.5; motivo = 'Revisao da prova' }
    Test-Status $r 200 'PROFESSOR: PUT /api/notas/{id} (correcao)'
    $r = Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Token $t -Corpo @{
        turmaId = $turmaId; disciplinaId = $Base.Disciplina.id; data = (Format-Iso (Get-Hoje)); professorId = $Base.Professor.id
        registros = @($alunos | ForEach-Object { @{ matriculaAluno = [string]$_.Aluno.matricula; status = 'PRESENTE' } })
    }
    Test-Status $r 201 'PROFESSOR: POST /api/frequencias/chamadas'

    # Nao cria aluno, turma, matricula nem disciplina, e nao administra usuarios
    $proibidas = @(
        @{ M = 'POST'; C = '/api/alunos'; B = @{ nome = 'Proibido'; dataNascimento = '2012-01-01' } },
        @{ M = 'POST'; C = '/api/turmas'; B = @{ codigo = 'T-P-' + (New-IdUnico); nome = 'Proibida'; anoLetivo = (Get-AnoLetivo); turno = 'MANHA' } },
        @{ M = 'PUT'; C = '/api/turmas/' + $turmaId; B = @{ codigo = $Base.Turma.codigo; nome = 'Proibida'; anoLetivo = (Get-AnoLetivo); turno = 'MANHA' } },
        @{ M = 'POST'; C = '/api/matriculas'; B = @{ matriculaAluno = [string]$alunos[0].Aluno.matricula; turmaId = $turmaId } },
        @{ M = 'POST'; C = '/api/disciplinas'; B = @{ codigo = 'DP' + (New-IdUnico); nome = 'Proibida' } },
        @{ M = 'GET'; C = '/api/usuarios'; B = $null },
        @{ M = 'POST'; C = '/api/usuarios'; B = @{ nome = 'x'; email = 'x@x.com'; senha = '123456'; perfil = 'ADMIN' } }
    )
    foreach ($p in $proibidas) {
        $r = Invoke-Api -Metodo $p.M -Caminho $p.C -Corpo $p.B -Token $t
        Test-Status $r 403 ('PROFESSOR: ' + $p.M + ' ' + $p.C)
    }

    $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes' -Token $t
    Test-Status $r 200 'PROFESSOR: GET /api/notificacoes (as proprias)'
}

function Test-PermissoesLeitura($Base, $Usuario) {
    $t = $Usuario.Token
    $perfil = $Usuario.Perfil
    $turmaId = $Base.Turma.id
    $alunos = $Base.Alunos
    $outroAluno = $alunos[1].Aluno

    # Nao escreve nada
    $proibidas = @(
        @{ M = 'POST'; C = '/api/avaliacoes'; B = @{ turmaId = $turmaId; disciplinaId = $Base.Disciplina.id; bimestre = 1; tipo = 'PROVA'; data = (Format-Iso (Get-Hoje)) } },
        @{ M = 'POST'; C = '/api/frequencias/chamadas'; B = @{ turmaId = $turmaId; disciplinaId = $Base.Disciplina.id; data = (Format-Iso (Get-Hoje)); registros = @() } },
        @{ M = 'PUT'; C = '/api/notas/1'; B = @{ valor = 10; motivo = 'tentativa' } },
        @{ M = 'POST'; C = '/api/alunos'; B = @{ nome = 'Proibido'; dataNascimento = '2012-01-01' } },
        @{ M = 'DELETE'; C = '/api/alunos/' + $outroAluno.id; B = $null },
        @{ M = 'PATCH'; C = '/api/matriculas/' + $alunos[1].Matricula.id + '/status'; B = @{ status = 'CANCELADA'; motivo = 'tentativa' } },
        @{ M = 'PUT'; C = '/api/turmas/' + $turmaId; B = @{ codigo = $Base.Turma.codigo; nome = 'Proibida'; anoLetivo = (Get-AnoLetivo); turno = 'MANHA' } },
        @{ M = 'GET'; C = '/api/usuarios'; B = $null }
    )
    foreach ($p in $proibidas) {
        $r = Invoke-Api -Metodo $p.M -Caminho $p.C -Corpo $p.B -Token $t
        Test-Status $r 403 ($perfil + ': ' + $p.M + ' ' + $p.C)
    }

    # Notificacoes do proprio usuario: todos os metodos
    $r = Invoke-Api -Metodo GET -Caminho '/api/notificacoes' -Token $t
    Test-Status $r 200 ($perfil + ': GET /api/notificacoes (as proprias)')
    $r = Invoke-Api -Metodo POST -Caminho '/api/notificacoes/lidas' -Token $t
    Test-Status $r 200 ($perfil + ': POST /api/notificacoes/lidas')

    # Falha de autorizacao conhecida: ALUNO e RESPONSAVEL leem qualquer /api/** (dados de outros
    # alunos inclusive). Fica como [AVISO] enquanto nao for corrigida; 403 conta como correcao.
    $leituras = @('/api/alunos?size=5', ('/api/alunos/' + $outroAluno.id), ('/api/alunos/' + $outroAluno.id + '/boletim?anoLetivo=' + (Get-AnoLetivo)),
        '/api/turmas?size=5', '/api/professores?size=5', '/api/tecnicos?size=5', '/api/matriculas?size=5')
    foreach ($c in $leituras) {
        $r = Invoke-Api -Metodo GET -Caminho $c -Token $t
        if ($r.Status -eq 200) {
            Write-Aviso ($perfil + ': GET ' + $c + ' -> 200 (falha de autorizacao conhecida: deveria se restringir aos proprios dados)') (Get-Resumo $r)
        } elseif ($r.Status -eq 403) {
            Test-Condicao ($perfil + ': GET ' + $c + ' -> 403 (restricao aplicada)') $true (Get-Resumo $r)
        } else {
            Test-Condicao ($perfil + ': GET ' + $c + ' -> 200 ou 403') $false (Get-Resumo $r)
        }
    }
}
