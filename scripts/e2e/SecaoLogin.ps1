# Secao "login": login da coordenacao e uso do JWT pelo gateway.

function Invoke-SecaoLogin {
    $semToken = Invoke-Api -Metodo GET -Caminho '/api/turmas' -SemToken
    Test-NaoAutenticado $semToken 'GET /api/turmas sem token'

    $login = Invoke-Login $EmailCoordenacao $SenhaCoordenacao
    Assert-Status $login 200 ('POST /api/auth/login com ' + $EmailCoordenacao)
    Assert-Condicao 'Resposta do login traz o token' ([bool]$login.Json.token)
    Test-Valor 'Perfil do usuario logado' 'ADMIN' $login.Json.usuario.perfil
    Test-Condicao 'Resposta do login traz a expiracao (expiraEm)' ($null -ne $login.Json.expiraEm) ('expiraEm: ' + (Format-Valor $login.Json.expiraEm))
    $Ctx.Token = [string]$login.Json.token

    $comToken = Invoke-Api -Metodo GET -Caminho '/api/turmas?size=1'
    Assert-Status $comToken 200 'GET /api/turmas com o token'

    # Senha errada: 401 do usuarios-service, com ProblemDetail e sem token
    $r = Invoke-Login $EmailCoordenacao 'senha-errada'
    Test-Erro $r 401 'POST /api/auth/login com senha errada'
    Test-Condicao 'Login com senha errada nao devolve token' (-not ($r.Json -and $r.Json.token)) (Get-Resumo $r)

    # Campos obrigatorios: 400 com a lista de campos
    $r = Invoke-Login '' ''
    Test-ErroValidacao $r @('email', 'senha') 'POST /api/auth/login com email e senha vazios'

    # Token adulterado: troca um caractere da assinatura (o gateway confere o HS256)
    $partes = $Ctx.Token.Split('.')
    $assinatura = $partes[2]
    $trocado = 'A'
    if ($assinatura.Substring(0, 1) -eq 'A') { $trocado = 'B' }
    $adulterado = $partes[0] + '.' + $partes[1] + '.' + $trocado + $assinatura.Substring(1)
    $r = Invoke-Api -Metodo GET -Caminho '/api/turmas?size=1' -Token $adulterado
    Test-NaoAutenticado $r 'GET /api/turmas com a assinatura do token adulterada'

    # Payload adulterado (perfil trocado) com a assinatura original: tambem recusado
    $payload = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String((ConvertTo-Base64Padded $partes[1])))
    $payloadTrocado = $payload.Replace('"ADMIN"', '"PROFESSOR"')
    $novoPayload = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($payloadTrocado)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $r = Invoke-Api -Metodo GET -Caminho '/api/turmas?size=1' -Token ($partes[0] + '.' + $novoPayload + '.' + $partes[2])
    Test-NaoAutenticado $r 'GET /api/turmas com o payload do token alterado (assinatura original)'

    $r = Invoke-Api -Metodo GET -Caminho '/api/turmas?size=1' -Token 'nao-e-um-jwt'
    Test-NaoAutenticado $r 'GET /api/turmas com um token mal formado'
}

# base64url do JWT para base64 com padding
function ConvertTo-Base64Padded([string]$Texto) {
    $b = $Texto.Replace('-', '+').Replace('_', '/')
    switch ($b.Length % 4) { 2 { $b += '==' } 3 { $b += '=' } }
    return $b
}
