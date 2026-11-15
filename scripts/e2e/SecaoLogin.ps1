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
}
