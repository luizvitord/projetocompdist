# Segurança do cadastro de usuários

## Objetivo

Completar o cadastro de usuários com regras de vínculo por perfil, redefinição de senha pelo administrador e revogação de sessões quando os dados de acesso mudarem.

## Decisões

- `ADMIN` não pode ter `referenciaId`; `PROFESSOR`, `ALUNO` e `RESPONSAVEL` exigem um identificador positivo. O usuários-service armazena o identificador, mas não consulta outros microsserviços: a existência do professor ou aluno continua sendo responsabilidade do fluxo de cadastro correspondente.
- A senha é alterada somente por `PATCH /api/usuarios/{id}/senha`, com o mesmo limite de 6 a 72 caracteres do cadastro. O `PUT` de dados cadastrais não aceita senha.
- Cada usuário recebe `versaoSessao`, incluída no JWT como `sessao`. Alterar perfil, vínculo, e-mail, senha ou ativação incrementa essa versão. O gateway consulta o usuários-service para comparar a versão do token com o estado atual antes de autorizar uma chamada autenticada.
- As chamadas REST passam pelo gateway, que consulta esse estado. O notificacao-service também o consulta ao receber um `CONNECT` STOMP direto. Uma sessão WebSocket já aberta não recebe novas autorizações depois da mudança, mas permanece aberta até a reconexão ou expiração; o canal só entrega notificações e não aceita mensagens de negócio.
- A desativação do último administrador ativo é recusada. A tela também não oferece desativar a própria conta, reduzindo o risco operacional antes de o servidor devolver esse bloqueio.

## Fora de escopo

- Busca textual de usuários: já foi implementada em outro commit e não será modificada.
- Pesquisa/seleção remota de professor ou aluno para preencher o vínculo: os endpoints de listagem entre serviços ainda não fazem parte do contrato atual.
