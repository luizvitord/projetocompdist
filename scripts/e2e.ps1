<#
.SYNOPSIS
    Teste de ponta a ponta do DistriSchool, pelo api-gateway.

.DESCRIPTION
    Roda contra o docker compose ja no ar (execute a partir da raiz do projeto):
      1. Login da coordenacao (ADMIN) e uso do JWT nas chamadas seguintes.
      2. Fluxo funcional: disciplina, professor, turma com a disciplina ofertada, aluno,
         matricula, chamadas e notas; confere boletim e frequencia do aluno.
      3. Outbox: com o Kafka parado, a criacao de turma responde rapido e o evento fica pendente
         em outbox_evento; com o Kafka de volta, o evento e publicado e a turma chega a
         turma_replica do gestao-escolar-service.
      4. Circuit breaker: com o gestao-escolar-service parado, /api/alunos responde 503 com
         ProblemDetail e o resto do gateway continua respondendo.
      5. Rate limit do login (por ultimo): 11 logins com senha errada, o 11o recebe 429.

    Cada verificacao imprime [OK] ou [FALHOU]. Termina com exit 1 se algo falhar. Em qualquer
    falha ou interrupcao (Ctrl+C), o kafka e o gestao-escolar-service sao ligados de novo.

    Compativel com o Windows PowerShell 5.1. O arquivo e mantido so com caracteres ASCII: o 5.1
    le scripts sem BOM na codificacao ANSI do sistema.

.PARAMETER GatewayUrl
    Endereco do api-gateway. Padrao: http://localhost:8080

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\e2e.ps1
#>
[CmdletBinding()]
param(
    [string]$GatewayUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
# A barra de progresso do Invoke-WebRequest deixa o 5.1 muito lento
$ProgressPreference = 'SilentlyContinue'
# Sem o "Expect: 100-continue" que o .NET Framework manda por padrao nos POST
[System.Net.ServicePointManager]::Expect100Continue = $false

$GatewayUrl = $GatewayUrl.TrimEnd('/')
$EmailCoordenacao = 'coordenacao@distrischool.com'
$SenhaCoordenacao = '123456'

# Estado compartilhado entre as etapas (os blocos das etapas rodam em escopo filho)
$Ctx = @{
    Token        = $null
    UltimoLogin  = [DateTime]::MinValue
    Sufixo       = (Get-Date -Format 'yyMMddHHmmss') + (Get-Random -Minimum 100 -Maximum 1000)
}
$Resultados = New-Object System.Collections.Generic.List[object]

# ---------------------------------------------------------------------------------------------
# Resultados
# ---------------------------------------------------------------------------------------------

function Write-Resultado([bool]$Ok, [string]$Descricao, [string]$Detalhe) {
    $Resultados.Add([pscustomobject]@{ Ok = $Ok; Descricao = $Descricao })
    if ($Ok) {
        Write-Host '[OK]     ' -ForegroundColor Green -NoNewline
    } else {
        Write-Host '[FALHOU] ' -ForegroundColor Red -NoNewline
    }
    Write-Host $Descricao
    if ($Detalhe) {
        Write-Host ('         ' + $Detalhe) -ForegroundColor DarkGray
    }
}

function Write-Info([string]$Texto) {
    Write-Host ('         ' + $Texto) -ForegroundColor DarkGray
}

# Registra a verificacao e segue em frente
function Test-Condicao([string]$Descricao, [bool]$Condicao, [string]$Detalhe) {
    Write-Resultado $Condicao $Descricao $Detalhe
}

# Registra a verificacao e, se falhar, interrompe a etapa (os passos seguintes dependem dela)
function Assert-Condicao([string]$Descricao, [bool]$Condicao, [string]$Detalhe) {
    Write-Resultado $Condicao $Descricao $Detalhe
    if (-not $Condicao) {
        throw 'ETAPA_INTERROMPIDA'
    }
}

function Format-Valor($Valor) {
    if ($null -eq $Valor) { return 'null' }
    return [string]$Valor
}

# Compara numeros pelo valor (75 = 75.00) e o resto como texto
function Test-Valor([string]$Descricao, $Esperado, $Obtido) {
    $igual = $false
    if ($null -eq $Esperado) {
        $igual = ($null -eq $Obtido)
    } elseif ($null -ne $Obtido) {
        if ($Esperado -is [ValueType] -and $Esperado -isnot [bool]) {
            try { $igual = ([decimal]$Obtido -eq [decimal]$Esperado) } catch { $igual = $false }
        } else {
            $igual = ([string]$Obtido -ceq [string]$Esperado)
        }
    }
    Test-Condicao $Descricao $igual ('esperado: ' + (Format-Valor $Esperado) + '; obtido: ' + (Format-Valor $Obtido))
}

# Roda uma etapa; uma falha interrompe so ela. Devolve $true se a etapa chegou ao fim.
function Invoke-Etapa([string]$Nome, [scriptblock]$Bloco) {
    Write-Host ''
    Write-Host ('=== ' + $Nome + ' ===') -ForegroundColor Cyan
    try {
        & $Bloco | Out-Null
        return $true
    } catch {
        if ($_.Exception.Message -ne 'ETAPA_INTERROMPIDA') {
            Write-Resultado $false ("Erro inesperado na etapa '" + $Nome + "'") $_.Exception.Message
        }
        Write-Host ("         Etapa '" + $Nome + "' interrompida.") -ForegroundColor Yellow
        return $false
    }
}

# ---------------------------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------------------------

# Corpo de uma resposta de erro. No 5.1 o Invoke-WebRequest lanca WebException para status >= 400;
# a resposta fica em $_.Exception.Response.
function Read-CorpoErro($Resposta) {
    try {
        $stream = $Resposta.GetResponseStream()
        if ($stream.CanSeek) { $stream.Position = 0 }
        $leitor = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
        return $leitor.ReadToEnd()
    } catch {
        return ''
    }
}

# Chama o gateway e devolve status, corpo, JSON, Content-Type, cabecalhos e tempo, sem lancar
# excecao por status HTTP. Status 0 = sem resposta (conexao recusada, timeout).
function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Metodo,
        [Parameter(Mandatory = $true)][string]$Caminho,
        $Corpo = $null,
        [switch]$SemToken,
        [int]$TimeoutSec = 30
    )
    $headers = @{}
    if (-not $SemToken -and $Ctx.Token) {
        $headers['Authorization'] = 'Bearer ' + $Ctx.Token
    }
    $parametros = @{
        Uri             = $GatewayUrl + $Caminho
        Method          = $Metodo
        Headers         = $headers
        UseBasicParsing = $true
        TimeoutSec      = $TimeoutSec
    }
    if ($null -ne $Corpo) {
        # -InputObject (e nao pipeline) para uma lista de um item continuar sendo lista
        $json = ConvertTo-Json -InputObject $Corpo -Depth 10 -Compress
        $parametros['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
        $parametros['ContentType'] = 'application/json; charset=utf-8'
    }

    $status = 0
    $texto = ''
    $tipo = ''
    $cabecalhos = @{}
    $erroConexao = $null
    $cronometro = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $r = Invoke-WebRequest @parametros
        $status = [int]$r.StatusCode
        $tipo = [string]$r.Headers['Content-Type']
        foreach ($k in $r.Headers.Keys) { $cabecalhos[$k] = [string]$r.Headers[$k] }
        if ($null -ne $r.RawContentStream) {
            # O 5.1 decodifica application/json sem charset como ISO-8859-1; os bytes sao UTF-8
            $texto = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        }
    } catch [System.Net.WebException] {
        $resposta = $_.Exception.Response
        if ($null -eq $resposta) {
            $erroConexao = $_.Exception.Message
        } else {
            $status = [int]$resposta.StatusCode
            $tipo = [string]$resposta.ContentType
            foreach ($k in $resposta.Headers.AllKeys) { $cabecalhos[$k] = [string]$resposta.Headers[$k] }
            $texto = Read-CorpoErro $resposta
            if (-not $texto -and $null -ne $_.ErrorDetails) {
                $texto = $_.ErrorDetails.Message
            }
        }
    }
    $cronometro.Stop()

    $dados = $null
    if ($texto) {
        try { $dados = ConvertFrom-Json -InputObject $texto } catch { $dados = $null }
    }
    return [pscustomobject]@{
        Status      = $status
        Texto       = $texto
        Json        = $dados
        ContentType = $tipo
        Headers     = $cabecalhos
        Ms          = [int]$cronometro.Elapsed.TotalMilliseconds
        ErroConexao = $erroConexao
    }
}

function Get-Resumo($Resposta) {
    if ($Resposta.Status -eq 0) {
        return 'sem resposta em ' + $Resposta.Ms + ' ms: ' + $Resposta.ErroConexao
    }
    $resumo = 'HTTP ' + $Resposta.Status + ' em ' + $Resposta.Ms + ' ms'
    if ($Resposta.Status -ge 400 -and $Resposta.Texto) {
        $corpo = $Resposta.Texto -replace '\s+', ' '
        if ($corpo.Length -gt 400) { $corpo = $corpo.Substring(0, 400) + '...' }
        $resumo += ': ' + $corpo
    }
    return $resumo
}

function Assert-Status($Resposta, [int]$Esperado, [string]$Descricao) {
    Assert-Condicao ($Descricao + ' -> ' + $Esperado) ($Resposta.Status -eq $Esperado) (Get-Resumo $Resposta)
}

function Test-Status($Resposta, [int]$Esperado, [string]$Descricao) {
    Test-Condicao ($Descricao + ' -> ' + $Esperado) ($Resposta.Status -eq $Esperado) (Get-Resumo $Resposta)
}

# ProblemDetail (RFC 9457): Content-Type application/problem+json, status e detail no corpo
function Test-ProblemDetail($Resposta, [int]$Status, [string]$Descricao) {
    $ok = ($Resposta.ContentType -like 'application/problem+json*') -and ($null -ne $Resposta.Json) `
        -and ([string]$Resposta.Json.status -eq [string]$Status) -and [bool]$Resposta.Json.detail
    Test-Condicao $Descricao $ok ('Content-Type: ' + $Resposta.ContentType + '; corpo: ' + $Resposta.Texto)
}

function Invoke-Login([string]$Senha) {
    $Ctx.UltimoLogin = Get-Date
    return Invoke-Api -Metodo POST -Caminho '/api/auth/login' -SemToken `
        -Corpo @{ email = $EmailCoordenacao; senha = $Senha }
}

# ---------------------------------------------------------------------------------------------
# Docker
# ---------------------------------------------------------------------------------------------

# Roda um programa externo e separa stdout de stderr. O docker compose escreve o progresso no
# stderr; com 2>&1 no 5.1 cada linha vira um ErrorRecord, que com 'Stop' interromperia o script.
function Invoke-Nativo([string]$Programa, [string[]]$Argumentos) {
    $ErrorActionPreference = 'Continue'
    $saida = & $Programa @Argumentos 2>&1
    $codigo = $LASTEXITCODE
    $linhas = @()
    $erros = @()
    foreach ($item in @($saida)) {
        if ($item -is [System.Management.Automation.ErrorRecord]) {
            $erros += $item.ToString()
        } elseif ($null -ne $item) {
            $linhas += [string]$item
        }
    }
    return [pscustomobject]@{ ExitCode = $codigo; Saida = $linhas; Erros = $erros }
}

function Invoke-Compose([string[]]$Argumentos) {
    return Invoke-Nativo 'docker' (@('compose') + $Argumentos)
}

function Stop-Servico([string]$Servico) {
    Write-Info ('docker compose stop ' + $Servico)
    $r = Invoke-Compose @('stop', $Servico)
    $detalhe = ''
    if ($r.ExitCode -ne 0) { $detalhe = ($r.Erros + $r.Saida) -join ' ' }
    Assert-Condicao ('docker compose stop ' + $Servico) ($r.ExitCode -eq 0) $detalhe
}

function Start-Servico([string]$Servico) {
    Write-Info ('docker compose start ' + $Servico)
    $r = Invoke-Compose @('start', $Servico)
    $detalhe = ''
    if ($r.ExitCode -ne 0) { $detalhe = ($r.Erros + $r.Saida) -join ' ' }
    Test-Condicao ('docker compose start ' + $Servico) ($r.ExitCode -eq 0) $detalhe
}

# Consulta de uma linha/coluna. O SQL usa so aspas simples: o 5.1 nao escapa aspas duplas ao
# passar argumentos para programas externos.
function Invoke-Psql([string]$Servico, [string]$Banco, [string]$Sql) {
    $r = Invoke-Compose @('exec', '-T', $Servico, 'psql', '-U', $Banco, '-d', $Banco,
        '-v', 'ON_ERROR_STOP=1', '-t', '-A', '-c', $Sql)
    if ($r.ExitCode -ne 0) {
        throw ('psql em ' + $Servico + ' falhou (exit ' + $r.ExitCode + '): ' + (($r.Erros + $r.Saida) -join ' '))
    }
    return ((@($r.Saida) | Where-Object { $_ -ne '' }) -join "`n").Trim()
}

function Invoke-PsqlAdministrativa([string]$Sql) {
    return Invoke-Psql 'postgres-administrativa' 'gestao_administrativa' $Sql
}

function Invoke-PsqlEscolar([string]$Sql) {
    return Invoke-Psql 'postgres-escolar' 'gestao_escolar' $Sql
}

# Status do healthcheck do container do servico: healthy, starting, unhealthy ou vazio
function Get-SaudeContainer([string]$Servico) {
    $ps = Invoke-Compose @('ps', '-q', $Servico)
    $id = @($ps.Saida | Where-Object { $_ }) | Select-Object -First 1
    if (-not $id) { return 'sem-container' }
    $inspect = Invoke-Nativo 'docker' @('inspect', '-f', '{{.State.Health.Status}}', $id)
    return ((@($inspect.Saida) -join '').Trim())
}

# ---------------------------------------------------------------------------------------------
# Etapas
# ---------------------------------------------------------------------------------------------

$Raiz = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $Raiz 'docker-compose.yml'))) {
    Write-Host ('docker-compose.yml nao encontrado em ' + $Raiz) -ForegroundColor Red
    exit 1
}

Write-Host ('Gateway: ' + $GatewayUrl)
Write-Host ('Projeto: ' + $Raiz)
Write-Host ('Sufixo dos dados de teste: ' + $Ctx.Sufixo)

$codigoSaida = 1
Push-Location $Raiz
try {
    $preRequisitos = Invoke-Etapa 'Pre-requisitos' {
        $versao = Invoke-Compose @('version')
        Assert-Condicao 'docker compose disponivel' ($versao.ExitCode -eq 0) (($versao.Saida + $versao.Erros) -join ' ')

        $saude = Invoke-Api -Metodo GET -Caminho '/actuator/health/readiness' -SemToken -TimeoutSec 10
        Assert-Status $saude 200 'GET /actuator/health/readiness do gateway'
    }

    $logado = $false
    if ($preRequisitos) {
        $logado = Invoke-Etapa 'Login e JWT' {
            $semToken = Invoke-Api -Metodo GET -Caminho '/api/turmas' -SemToken
            Test-Status $semToken 401 'GET /api/turmas sem token'

            $login = Invoke-Login $SenhaCoordenacao
            Assert-Status $login 200 ('POST /api/auth/login com ' + $EmailCoordenacao)
            Assert-Condicao 'Resposta do login traz o token' ([bool]$login.Json.token)
            Test-Valor 'Perfil do usuario logado' 'ADMIN' $login.Json.usuario.perfil
            $Ctx.Token = [string]$login.Json.token

            $comToken = Invoke-Api -Metodo GET -Caminho '/api/turmas?size=1'
            Assert-Status $comToken 200 'GET /api/turmas com o token'
        }
    }

    if ($logado) {
        Invoke-Etapa 'Fluxo funcional' {
            $s = $Ctx.Sufixo
            # Datas em UTC, como o LocalDate.now() dos servicos nos containers
            $hoje = [DateTime]::UtcNow.Date
            $ano = $hoje.Year
            $hojeTexto = $hoje.ToString('yyyy-MM-dd')
            Assert-Condicao 'Hoje (UTC) permite 4 chamadas em dias distintos no ano letivo' ($hoje.DayOfYear -ge 4) `
                'as chamadas usam hoje e os 3 dias anteriores, todos no ano letivo da turma'

            # Disciplina
            $codigoDisciplina = 'D' + $s
            $r = Invoke-Api -Metodo POST -Caminho '/api/disciplinas' -Corpo @{
                codigo = $codigoDisciplina; nome = 'Disciplina E2E ' + $s; cargaHoraria = 80
            }
            Assert-Status $r 201 'POST /api/disciplinas'
            $disciplinaId = $r.Json.id
            Assert-Condicao 'Disciplina criada com id' ($null -ne $disciplinaId) ('id: ' + (Format-Valor $disciplinaId))

            # Professor
            $r = Invoke-Api -Metodo POST -Caminho '/api/professores' -Corpo @{
                nome = 'Professor E2E ' + $s; qualificacao = 'Licenciatura'
                email = 'prof.e2e.' + $s + '@distrischool.com'; telefone = '11999990000'
            }
            Assert-Status $r 201 'POST /api/professores'
            $professorId = $r.Json.id
            Assert-Condicao 'Professor criado com id' ($null -ne $professorId) ('id: ' + (Format-Valor $professorId))

            # Turma com a disciplina ofertada
            $codigoTurma = 'T-E2E-' + $s
            $r = Invoke-Api -Metodo POST -Caminho '/api/turmas' -Corpo @{
                codigo = $codigoTurma; nome = 'Turma E2E ' + $s; anoLetivo = $ano; turno = 'MANHA'; capacidade = 30
            }
            Assert-Status $r 201 'POST /api/turmas'
            $turmaId = $r.Json.id
            Assert-Condicao 'Turma criada com id' ($null -ne $turmaId) ('id: ' + (Format-Valor $turmaId))

            $r = Invoke-Api -Metodo PUT -Caminho ('/api/turmas/' + $turmaId + '/disciplinas/' + $disciplinaId) `
                -Corpo @{ professorId = $professorId }
            Assert-Status $r 200 'PUT /api/turmas/{id}/disciplinas/{disciplinaId}'
            $oferta = @($r.Json.disciplinas) | Where-Object { $_.disciplinaId -eq $disciplinaId } | Select-Object -First 1
            Assert-Condicao 'Turma oferta a disciplina' ($null -ne $oferta) $r.Texto
            Test-Valor 'Professor responsavel pela disciplina na turma' $professorId $oferta.professorId

            # Aluno
            $r = Invoke-Api -Metodo POST -Caminho '/api/alunos' -Corpo @{
                nome = 'Aluno E2E ' + $s; dataNascimento = '2012-05-10'
                email = 'aluno.e2e.' + $s + '@distrischool.com'; telefone = '11988880000'
            }
            Assert-Status $r 201 'POST /api/alunos'
            $alunoId = $r.Json.id
            $numeroMatricula = [string]$r.Json.matricula
            Assert-Condicao 'Aluno criado com id e numero de matricula' (($null -ne $alunoId) -and [bool]$numeroMatricula) `
                ('id: ' + (Format-Valor $alunoId) + '; matricula: ' + $numeroMatricula)

            # A chamada exige a oferta na replica local; o schedule.updated da oferta chega pelo Kafka
            $sqlOferta = 'SELECT count(*) FROM turma_disciplina_replica WHERE turma_id = ' + $turmaId +
                ' AND disciplina_id = ' + $disciplinaId + ' AND NOT removida'
            $limite = (Get-Date).AddSeconds(30)
            $cron = [System.Diagnostics.Stopwatch]::StartNew()
            $replicada = $false
            while (-not $replicada -and (Get-Date) -lt $limite) {
                try { $replicada = ((Invoke-PsqlEscolar $sqlOferta) -eq '1') } catch { $replicada = $false }
                if (-not $replicada) { Start-Sleep -Seconds 1 }
            }
            Assert-Condicao 'Oferta da disciplina replicada em turma_disciplina_replica (gestao-escolar)' $replicada `
                ('apos ' + [math]::Round($cron.Elapsed.TotalSeconds, 1) + ' s (limite 30 s)')

            # Matricula
            $r = Invoke-Api -Metodo POST -Caminho '/api/matriculas' -Corpo @{
                matriculaAluno = $numeroMatricula; turmaId = $turmaId
            }
            Assert-Status $r 201 'POST /api/matriculas'
            Test-Valor 'Status da matricula' 'ATIVA' $r.Json.status
            Test-Valor 'Ano letivo da matricula' $ano $r.Json.anoLetivo
            Test-Valor 'Aluno da matricula' $alunoId $r.Json.alunoId

            # Chamadas: 2 presencas, 1 atraso, 1 falta = 75% (atraso conta como presenca)
            $statusChamadas = @('PRESENTE', 'PRESENTE', 'ATRASO', 'FALTA')
            for ($i = 0; $i -lt $statusChamadas.Count; $i++) {
                $data = $hoje.AddDays(-($statusChamadas.Count - 1 - $i)).ToString('yyyy-MM-dd')
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

            # Boletim
            $r = Invoke-Api -Metodo GET -Caminho ('/api/alunos/' + $alunoId + '/boletim?anoLetivo=' + $ano)
            Assert-Status $r 200 'GET /api/alunos/{id}/boletim'
            $b = $r.Json
            Test-Valor 'Boletim: situacao' 'CURSANDO' $b.situacao
            $matriculas = @($b.matriculas)
            Test-Valor 'Boletim: quantidade de matriculas no ano' 1 $matriculas.Count
            Test-Valor 'Boletim: turma da matricula' $turmaId $matriculas[0].turmaId
            Test-Valor 'Boletim: codigo da turma' $codigoTurma $matriculas[0].turmaCodigo
            $d = @($b.disciplinas) | Where-Object { $_.disciplinaId -eq $disciplinaId } | Select-Object -First 1
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

            # Frequencia do aluno
            $r = Invoke-Api -Metodo GET -Caminho ('/api/frequencias/alunos/' + $alunoId + '?anoLetivo=' + $ano)
            Assert-Status $r 200 'GET /api/frequencias/alunos/{id}'
            $f = $r.Json
            Test-Valor 'Frequencia: aluno' $alunoId $f.alunoId
            $fd = @($f.disciplinas) | Where-Object { $_.disciplinaId -eq $disciplinaId } | Select-Object -First 1
            Assert-Condicao 'Frequencia: disciplina presente' ($null -ne $fd) $r.Texto
            Test-Valor 'Frequencia: total de aulas na disciplina' 4 $fd.frequencia.totalAulas
            Test-Valor 'Frequencia: presencas + atrasos' 3 ($fd.frequencia.presencas + $fd.frequencia.atrasos)
            Test-Valor 'Frequencia: faltas' 1 $fd.frequencia.faltas
            Test-Valor 'Frequencia: percentual na disciplina' 75.00 $fd.frequencia.percentual
            Test-Valor 'Frequencia: abaixo do minimo' $false $fd.frequencia.abaixoDoMinimo
            Test-Valor 'Frequencia: percentual geral' 75.00 $f.geral.percentual
        } | Out-Null

        Invoke-Etapa 'Outbox com o Kafka fora do ar' {
            $s = $Ctx.Sufixo
            $ano = [DateTime]::UtcNow.Year

            Stop-Servico 'kafka'
            try {
                # Esta turma so pode chegar ao gestao-escolar pelo evento: nenhuma matricula ou
                # chamada nela (isso acionaria o fallback sincrono e mascararia o teste)
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
            $sqlReplica = 'SELECT count(*) FROM turma_replica WHERE id = ' + $turmaId + ' AND ultimo_evento_em IS NOT NULL'
            $limite = (Get-Date).AddSeconds(90)
            $cron = [System.Diagnostics.Stopwatch]::StartNew()
            $publicadoEm = $null
            $replicadoEm = $null
            while (($null -eq $publicadoEm -or $null -eq $replicadoEm) -and (Get-Date) -lt $limite) {
                if ($null -eq $publicadoEm) {
                    try { if ((Invoke-PsqlAdministrativa $sqlPublicado) -eq 't') { $publicadoEm = $cron.Elapsed.TotalSeconds } } catch { }
                }
                if ($null -eq $replicadoEm) {
                    try { if ((Invoke-PsqlEscolar $sqlReplica) -eq '1') { $replicadoEm = $cron.Elapsed.TotalSeconds } } catch { }
                }
                if ($null -eq $publicadoEm -or $null -eq $replicadoEm) { Start-Sleep -Seconds 2 }
            }
            $detalhePublicado = 'nao publicado em 90 s'
            if ($null -ne $publicadoEm) { $detalhePublicado = 'apos ' + [math]::Round($publicadoEm, 1) + ' s (limite 90 s)' }
            $detalheReplica = 'ausente de turma_replica apos 90 s'
            if ($null -ne $replicadoEm) { $detalheReplica = 'apos ' + [math]::Round($replicadoEm, 1) + ' s (limite 90 s)' }
            Test-Condicao 'Evento publicado depois de religar o Kafka (publicado_em preenchido)' ($null -ne $publicadoEm) $detalhePublicado
            Test-Condicao 'Turma aplicada em turma_replica pelo consumidor do schedule.updated' ($null -ne $replicadoEm) $detalheReplica
        } | Out-Null

        Invoke-Etapa 'Circuit breaker com o gestao-escolar-service fora do ar' {
            Stop-Servico 'gestao-escolar-service'
            try {
                $r = Invoke-Api -Metodo GET -Caminho '/api/alunos' -TimeoutSec 60
                Test-Status $r 503 'GET /api/alunos com o gestao-escolar-service parado'
                Test-ProblemDetail $r 503 'Resposta 503 e um ProblemDetail (application/problem+json com status e detail)'

                $r = Invoke-Api -Metodo GET -Caminho '/api/turmas?size=1'
                Test-Status $r 200 'GET /api/turmas continua respondendo'

                $r = Invoke-Login $SenhaCoordenacao
                Test-Status $r 200 'POST /api/auth/login continua respondendo'
            } finally {
                Start-Servico 'gestao-escolar-service'
            }

            # Espera o healthcheck do container antes de chamar o gateway: falhas seguidas durante
            # a subida abririam o circuito e atrasariam a volta em 30 s
            $limite = (Get-Date).AddSeconds(120)
            $cron = [System.Diagnostics.Stopwatch]::StartNew()
            $voltou = $false
            $ultimo = $null
            $saude = ''
            while (-not $voltou -and (Get-Date) -lt $limite) {
                $saude = Get-SaudeContainer 'gestao-escolar-service'
                if ($saude -ne 'starting' -and $saude -ne 'unhealthy' -and $saude -ne 'sem-container') {
                    $ultimo = Invoke-Api -Metodo GET -Caminho '/api/alunos?size=1' -TimeoutSec 20
                    $voltou = ($ultimo.Status -eq 200)
                }
                if (-not $voltou) { Start-Sleep -Seconds 3 }
            }
            $detalhe = 'apos ' + [math]::Round($cron.Elapsed.TotalSeconds, 1) + ' s (limite 120 s); healthcheck: ' + $saude
            if ($null -ne $ultimo -and -not $voltou) { $detalhe += '; ultima resposta: ' + (Get-Resumo $ultimo) }
            Test-Condicao 'GET /api/alunos volta a responder 200 depois de religar o servico' $voltou $detalhe
        } | Out-Null
    }

    if ($preRequisitos) {
        Invoke-Etapa 'Rate limit do login' {
            # 10 logins por minuto por IP, com reposicao gradual: espera um periodo inteiro desde o
            # ultimo login do script para o balde estar cheio
            $decorrido = ((Get-Date) - $Ctx.UltimoLogin).TotalSeconds
            if ($decorrido -lt 61) {
                $espera = [int][math]::Ceiling(61 - $decorrido)
                Write-Info ('Aguardando ' + $espera + ' s para o limite de logins se recompor...')
                Start-Sleep -Seconds $espera
            }

            $statusLogins = @()
            $ultima = $null
            for ($i = 1; $i -le 11; $i++) {
                $ultima = Invoke-Login ('senha-errada-' + $i)
                $statusLogins += $ultima.Status
            }
            $sequencia = 'status: ' + ($statusLogins -join ', ')
            $primeiras = @($statusLogins[0..9] | Where-Object { $_ -ne 401 })
            Test-Condicao '10 primeiros logins com senha errada -> 401' ($primeiras.Count -eq 0) $sequencia
            Test-Condicao '11o login com senha errada -> 429' ($ultima.Status -eq 429) (Get-Resumo $ultima)
            Test-ProblemDetail $ultima 429 'Resposta 429 e um ProblemDetail (application/problem+json com status e detail)'
            Test-Condicao 'Resposta 429 traz o cabecalho Retry-After' ([bool]$ultima.Headers['Retry-After']) `
                ('Retry-After: ' + (Format-Valor $ultima.Headers['Retry-After']))
        } | Out-Null
    }

    # Resumo
    $falhas = @($Resultados | Where-Object { -not $_.Ok })
    $total = $Resultados.Count
    Write-Host ''
    Write-Host '=== Resumo ===' -ForegroundColor Cyan
    if ($falhas.Count -eq 0 -and $total -gt 0) {
        Write-Host ('Todas as ' + $total + ' verificacoes passaram.') -ForegroundColor Green
        $codigoSaida = 0
    } else {
        Write-Host (($total - $falhas.Count).ToString() + ' de ' + $total + ' verificacoes passaram; ' +
            $falhas.Count + ' falharam:') -ForegroundColor Red
        foreach ($falha in $falhas) {
            Write-Host ('  - ' + $falha.Descricao) -ForegroundColor Red
        }
    }
} finally {
    # Tambem em Ctrl+C: o teste nunca deixa o ambiente com servicos parados
    Write-Host ''
    Write-Host 'Garantindo kafka e gestao-escolar-service ligados...' -ForegroundColor DarkGray
    try {
        $religar = Invoke-Compose @('start', 'kafka', 'gestao-escolar-service')
        $falhaReligar = ''
        if ($religar.ExitCode -ne 0) { $falhaReligar = ($religar.Erros + $religar.Saida) -join ' ' }
    } catch {
        $falhaReligar = $_.Exception.Message
    }
    if ($falhaReligar) {
        Write-Host ('Falha ao religar os servicos: ' + $falhaReligar) -ForegroundColor Red
        Write-Host 'Rode: docker compose start kafka gestao-escolar-service' -ForegroundColor Red
        $codigoSaida = 1
    }
    Pop-Location
}

exit $codigoSaida
