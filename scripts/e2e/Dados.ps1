# Dados de teste do e2e: identificadores unicos por execucao, CPF valido, cadastros pela API e o
# calculo esperado das medias do boletim (feito aqui, no proprio teste).

# Identificador unico: sufixo da execucao (data/hora + aleatorio) + contador. 17 caracteres, cabe
# no codigo da disciplina (20) e da turma (30).
function New-IdUnico {
    $Ctx.Contador++
    return $Ctx.Sufixo + ([string]$Ctx.Contador).PadLeft(2, '0')
}

# CPF valido (com os dois digitos verificadores), so numeros
function New-Cpf {
    do {
        $d = @(1..9 | ForEach-Object { Get-Random -Minimum 0 -Maximum 10 })
    } while (@($d | Select-Object -Unique).Count -eq 1)
    foreach ($pesoInicial in @(10, 11)) {
        $soma = 0
        for ($i = 0; $i -lt $d.Count; $i++) { $soma += $d[$i] * ($pesoInicial - $i) }
        $resto = $soma % 11
        $dv = 0
        if ($resto -ge 2) { $dv = 11 - $resto }
        $d += $dv
    }
    return ($d -join '')
}

function Get-Hoje { return [DateTime]::UtcNow.Date }

# Datas em UTC, como o LocalDate.now() dos servicos nos containers
function Format-Iso([DateTime]$Data) { return $Data.ToString('yyyy-MM-dd') }

function Get-AnoLetivo { return (Get-Hoje).Year }

# ---------------------------------------------------------------------------------------------
# Cadastros (so aparecem na saida se falharem)
# ---------------------------------------------------------------------------------------------

function New-Disciplina([string]$Nome = '') {
    $id = New-IdUnico
    if (-not $Nome) { $Nome = 'Disciplina E2E ' + $id }
    $r = Invoke-Api -Metodo POST -Caminho '/api/disciplinas' -Corpo @{ codigo = 'D' + $id; nome = $Nome; cargaHoraria = 80 }
    Assert-Preparacao $r 201 'POST /api/disciplinas'
    return $r.Json
}

function New-Professor([string]$Nome = '') {
    $id = New-IdUnico
    if (-not $Nome) { $Nome = 'Professor E2E ' + $id }
    $r = Invoke-Api -Metodo POST -Caminho '/api/professores' -Corpo @{
        nome = $Nome; qualificacao = 'Licenciatura'; email = 'prof.' + $id + '@e2e.distrischool.com'; telefone = '11999990000'
    }
    Assert-Preparacao $r 201 'POST /api/professores'
    return $r.Json
}

function New-Turma([int]$Capacidade = 40, [int]$AnoLetivo = 0, [string]$Turno = 'MANHA') {
    if ($AnoLetivo -eq 0) { $AnoLetivo = Get-AnoLetivo }
    $id = New-IdUnico
    $corpo = @{ codigo = 'T-' + $id; nome = 'Turma E2E ' + $id; anoLetivo = $AnoLetivo; turno = $Turno }
    if ($Capacidade -gt 0) { $corpo['capacidade'] = $Capacidade }
    $r = Invoke-Api -Metodo POST -Caminho '/api/turmas' -Corpo $corpo
    Assert-Preparacao $r 201 'POST /api/turmas'
    return $r.Json
}

function Set-Oferta($TurmaId, $DisciplinaId, $ProfessorId = $null) {
    $corpo = @{}
    if ($null -ne $ProfessorId) { $corpo['professorId'] = $ProfessorId }
    $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $TurmaId + '/disciplinas/' + $DisciplinaId) -Corpo $corpo
    Assert-Preparacao $r 200 'PUT /api/turmas/{id}/disciplinas/{disciplinaId}'
    return $r.Json
}

function New-Aluno([string]$Nome = '') {
    $id = New-IdUnico
    if (-not $Nome) { $Nome = 'Aluno E2E ' + $id }
    $r = Invoke-Api -Metodo POST -Caminho '/api/alunos' -Corpo @{
        nome = $Nome; dataNascimento = '2012-05-10'; email = 'aluno.' + $id + '@e2e.distrischool.com'; telefone = '11988880000'
    }
    Assert-Preparacao $r 201 'POST /api/alunos'
    return $r.Json
}

function New-Matricula($Aluno, $TurmaId) {
    $r = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{ matriculaAluno = [string]$Aluno.matricula; turmaId = $TurmaId }
    Assert-Preparacao $r 201 'POST /api/matriculas'
    return $r.Json
}

function New-Avaliacao($TurmaId, $DisciplinaId, [int]$Bimestre, [string]$Tipo, [decimal]$Peso = 1, [decimal]$NotaMaxima = 10) {
    $r = Invoke-Api -Metodo POST -Caminho '/api/avaliacoes' -Corpo @{
        turmaId = $TurmaId; disciplinaId = $DisciplinaId; bimestre = $Bimestre; tipo = $Tipo
        descricao = $Tipo + ' E2E ' + (New-IdUnico); peso = $Peso; notaMaxima = $NotaMaxima; data = (Format-Iso (Get-Hoje))
    }
    Assert-Preparacao $r 201 ('POST /api/avaliacoes (' + $Tipo + ')')
    return $r.Json
}

# Lancamento: lista de @{ Aluno = <aluno>; Valor = <decimal> }
function Add-Notas($AvaliacaoId, $Lancamentos) {
    $corpo = @($Lancamentos | ForEach-Object { @{ matriculaAluno = [string]$_.Aluno.matricula; valor = $_.Valor } })
    $r = Invoke-Api -Metodo POST -Caminho ('/api/avaliacoes/' + $AvaliacaoId + '/notas') -Corpo $corpo
    Assert-Preparacao $r 201 'POST /api/avaliacoes/{id}/notas'
    return @($r.Json)
}

# Registros: lista de @{ Aluno = <aluno>; Status = 'PRESENTE' | 'FALTA' | 'ATRASO' }
function New-Chamada($TurmaId, $DisciplinaId, [DateTime]$Data, $Registros, $ProfessorId = $null) {
    $corpo = @{
        turmaId = $TurmaId; disciplinaId = $DisciplinaId; data = (Format-Iso $Data)
        registros = @($Registros | ForEach-Object { @{ matriculaAluno = [string]$_.Aluno.matricula; status = $_.Status } })
    }
    if ($null -ne $ProfessorId) { $corpo['professorId'] = $ProfessorId }
    $r = Invoke-Api -Metodo POST -Caminho '/api/frequencias/chamadas' -Corpo $corpo
    Assert-Preparacao $r 201 ('POST /api/frequencias/chamadas (' + (Format-Iso $Data) + ')')
    return $r.Json
}

function Set-StatusMatricula($MatriculaId, [string]$Status, [string]$Motivo = 'Motivo E2E') {
    $r = Invoke-Api -Metodo PATCH -Caminho ('/api/matriculas/' + $MatriculaId + '/status') -Corpo @{ status = $Status; motivo = $Motivo }
    Assert-Preparacao $r 200 ('PATCH /api/matriculas/{id}/status (' + $Status + ')')
    return $r.Json
}

# Usuario de teste (senha conhecida); o login fica para Connect-Usuario
function New-Usuario([string]$Perfil, $ReferenciaId) {
    $id = New-IdUnico
    $senha = 'senha-e2e-' + $id
    $email = $Perfil.ToLower() + '.' + $id + '@e2e.distrischool.com'
    $r = Invoke-Api -Metodo POST -Caminho '/api/usuarios' -Corpo @{
        nome = $Perfil + ' E2E ' + $id; email = $email; senha = $senha; perfil = $Perfil; referenciaId = $ReferenciaId
    }
    Assert-Preparacao $r 201 ('POST /api/usuarios (' + $Perfil + ')')
    $Ctx.UsuariosCriados.Add($r.Json.id)
    return @{ Id = $r.Json.id; Email = $email; Senha = $senha; Perfil = $Perfil; ReferenciaId = $ReferenciaId; Token = '' }
}

function Connect-Usuario($Usuario) {
    $login = Invoke-Login $Usuario.Email $Usuario.Senha
    Assert-Preparacao $login 200 ('POST /api/auth/login (' + $Usuario.Perfil + ')')
    $Usuario.Token = [string]$login.Json.token
    return $Usuario
}

# ---------------------------------------------------------------------------------------------
# Replica do gestao-escolar-service (vista pela API dele, nunca pelo banco)
# ---------------------------------------------------------------------------------------------

# Disciplina como o gestao-escolar-service a ve para um aluno: o boletim lista as disciplinas
# ofertadas hoje na turma atual da matricula (replica turma_disciplina_replica), com codigo e nome
function Get-DisciplinaNoBoletim($AlunoId, $DisciplinaId, [int]$Ano = 0) {
    if ($Ano -eq 0) { $Ano = Get-AnoLetivo }
    $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $AlunoId + '/boletim?anoLetivo=' + $Ano)
    if ($r.Status -ne 200) { return $null }
    return (@($r.Json.disciplinas) | Where-Object { [string]$_.disciplinaId -eq [string]$DisciplinaId } | Select-Object -First 1)
}

# A oferta chega ao gestao-escolar-service pelo schedule.updated (Kafka); ate la, chamadas e
# avaliacoes respondem 409
function Wait-OfertaNoEscolar($AlunoId, $DisciplinaId, [string]$Descricao = 'Oferta da disciplina replicada no gestao-escolar-service') {
    $espera = Wait-Ate -PrazoSeg 20 -Condicao { Get-DisciplinaNoBoletim $AlunoId $DisciplinaId }
    Assert-Condicao $Descricao $espera.Ok $espera.Detalhe
}

# Dados comuns a varias secoes: disciplina, professor, turma do ano com a disciplina ofertada pelo
# professor e dois alunos matriculados. Criados uma vez por execucao, na primeira secao que pedir.
function Initialize-Base {
    if ($null -ne $Ctx.Base) { return $Ctx.Base }
    Initialize-Admin
    $disciplina = New-Disciplina
    $professor = New-Professor
    $turma = New-Turma -Capacidade 40
    Set-Oferta $turma.id $disciplina.id $professor.id | Out-Null
    $alunos = @()
    foreach ($n in 1..2) {
        $aluno = New-Aluno
        $matricula = New-Matricula $aluno $turma.id
        $alunos += @{ Aluno = $aluno; Matricula = $matricula }
    }
    Wait-OfertaNoEscolar $alunos[0].Aluno.id $disciplina.id 'Base: oferta replicada no gestao-escolar-service'
    $Ctx.Base = @{ Disciplina = $disciplina; Professor = $professor; Turma = $turma; Alunos = $alunos }
    return $Ctx.Base
}

# Usuarios PROFESSOR (o da base), ALUNO e RESPONSAVEL (do 1o aluno) e RESPONSAVEL do 2o aluno
# (para conferir que a notificacao de um aluno nao chega aos outros). 4 logins.
function Initialize-Usuarios {
    if ($null -ne $Ctx.Usuarios) { return $Ctx.Usuarios }
    $base = Initialize-Base
    $u = @{
        Professor    = Connect-Usuario (New-Usuario 'PROFESSOR' $base.Professor.id)
        Aluno        = Connect-Usuario (New-Usuario 'ALUNO' $base.Alunos[0].Aluno.id)
        Responsavel  = Connect-Usuario (New-Usuario 'RESPONSAVEL' $base.Alunos[0].Aluno.id)
        OutroResponsavel = Connect-Usuario (New-Usuario 'RESPONSAVEL' $base.Alunos[1].Aluno.id)
    }
    $Ctx.Usuarios = $u
    return $u
}

# ---------------------------------------------------------------------------------------------
# Notificacoes
# ---------------------------------------------------------------------------------------------

function Get-Notificacoes([string]$Token, [int]$Tamanho = 100) {
    $r = Invoke-Api -Metodo GET -Caminho ('/api/notificacoes?size=' + $Tamanho) -Token $Token
    if ($r.Status -ne 200) { return $null }
    # Virgula: uma lista vazia (ou de um item) nao e desmontada no retorno; $null = erro na API
    return ,@($r.Json.content)
}

# Espera uma notificacao que satisfaca $Filtro (recebe a notificacao em $_) na lista do usuario
function Wait-Notificacao([string]$Token, [scriptblock]$Filtro, [int]$PrazoSeg = 20) {
    return (Wait-Ate -PrazoSeg $PrazoSeg -Condicao {
        $lista = Get-Notificacoes $Token
        if ($null -eq $lista) { return $null }
        return (@($lista | Where-Object $Filtro) | Select-Object -First 1)
    })
}

# Quantas notificacoes da lista satisfazem o filtro (sem esperar)
function Measure-Notificacoes([string]$Token, [scriptblock]$Filtro) {
    $lista = Get-Notificacoes $Token
    if ($null -eq $lista) { return -1 }
    return @($lista | Where-Object $Filtro).Count
}

# ---------------------------------------------------------------------------------------------
# Calculo esperado do boletim (regras do gestao-escolar-service/CLAUDE.md)
# ---------------------------------------------------------------------------------------------

# HALF_UP do BigDecimal: para valores positivos e o "away from zero" do .NET
function Get-Arredondado([decimal]$Valor, [int]$Casas) {
    return [math]::Round($Valor, $Casas, [System.MidpointRounding]::AwayFromZero)
}

# Media de um bimestre. $Avaliacoes: lista de @{ Tipo; Peso; NotaMaxima; Valor } (Valor $null =
# pendente). Normaliza para 0-10 (valor / notaMaxima x 10), media ponderada pelo peso das que nao
# sao RECUPERACAO; com recuperacao, vale a maior entre a media e a recuperacao. 2 casas, HALF_UP
# (as contas intermediarias com 10 casas, como no servico).
function Get-MediaBimestre($Avaliacoes) {
    $somaPonderada = [decimal]0
    $somaPesos = [decimal]0
    $recuperacao = $null
    $pendentes = 0
    foreach ($a in @($Avaliacoes)) {
        if ($null -eq $a.Valor) { $pendentes++; continue }
        $normalizada = Get-Arredondado ([decimal]$a.Valor * 10 / [decimal]$a.NotaMaxima) 10
        if ($a.Tipo -eq 'RECUPERACAO') {
            if ($null -eq $recuperacao -or $normalizada -gt $recuperacao) { $recuperacao = $normalizada }
        } else {
            $somaPonderada += $normalizada * [decimal]$a.Peso
            $somaPesos += [decimal]$a.Peso
        }
    }
    $semRecuperacao = $null
    if ($somaPesos -gt 0) { $semRecuperacao = Get-Arredondado ($somaPonderada / $somaPesos) 10 }
    $media = $semRecuperacao
    if ($null -ne $recuperacao -and ($null -eq $media -or $recuperacao -gt $media)) { $media = $recuperacao }
    $r = @{ Media = $null; SemRecuperacao = $null; Recuperacao = $null; Pendentes = $pendentes }
    if ($null -ne $media) { $r.Media = Get-Arredondado $media 2 }
    if ($null -ne $semRecuperacao) { $r.SemRecuperacao = Get-Arredondado $semRecuperacao 2 }
    if ($null -ne $recuperacao) { $r.Recuperacao = Get-Arredondado $recuperacao 2 }
    return $r
}

# Media anual: media aritmetica das medias dos bimestres que tem media, 2 casas HALF_UP
function Get-MediaAnual($MediasBimestres) {
    $medias = @($MediasBimestres | Where-Object { $null -ne $_ })
    if ($medias.Count -eq 0) { return $null }
    $soma = [decimal]0
    foreach ($m in $medias) { $soma += [decimal]$m }
    return Get-Arredondado ($soma / $medias.Count) 2
}

# Percentual de presenca: (PRESENTE + ATRASO) / total x 100, 2 casas HALF_UP; abaixo do minimo se o
# percentual exato for menor que o minimo
function Get-Frequencia([int]$Presencas, [int]$Faltas, [int]$Atrasos, [decimal]$Minimo = 75) {
    $total = $Presencas + $Faltas + $Atrasos
    if ($total -eq 0) { return @{ Total = 0; Percentual = $null; Abaixo = $false } }
    $exato = [decimal]($Presencas + $Atrasos) * 100 / $total
    return @{ Total = $total; Percentual = (Get-Arredondado $exato 2); Abaixo = ($exato -lt $Minimo) }
}
