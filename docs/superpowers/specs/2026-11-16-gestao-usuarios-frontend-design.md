# Gestão de usuários no frontend — design

## Intenção

Permitir que a coordenação administre no frontend as contas já suportadas pelo backend, sem expor operações administrativas aos demais perfis.

## Escopo

- Criar uma página de usuários exclusiva do perfil `ADMIN`, com listagem, filtro por perfil, cadastro, edição e desativação lógica.
- Exibir e enviar os quatro perfis aceitos pela API: `ADMIN`, `PROFESSOR`, `ALUNO` e `RESPONSAVEL`.
- Tornar explícito o vínculo de referência: nenhum para `ADMIN`, professor para `PROFESSOR` e aluno para `ALUNO`/`RESPONSAVEL`.
- Ocultar a navegação administrativa de usuários para `PROFESSOR`; `ALUNO` e `RESPONSAVEL` permanecem no portal.

## Fora de escopo

- Alterar as permissões, validações ou contratos do backend.
- Redefinir senha, pois o endpoint de atualização não recebe senha.
- Implementar busca de professor/aluno para o vínculo, pois o contrato atual persiste somente `referenciaId` numérico.

## Abordagens consideradas

1. Reaproveitar integralmente `RegistryPage`: descartada porque a senha existe somente no cadastro e o campo de referência depende do perfil.
2. Criar um fluxo próprio em `features/usuarios`: escolhido, pois permite adaptar os campos por perfil e preservar os componentes visuais e o padrão de requisições já existentes.

## Decisões de interface

- A nova entrada “Usuários” aparece apenas para a coordenação.
- O formulário mostra a senha apenas no cadastro. Ao editar, há uma mensagem de que a senha não é alterada nessa tela.
- Perfis com vínculo obrigatório exigem um identificador positivo antes do envio; `ADMIN` envia `referenciaId: null`.
- A desativação é apresentada como “Desativar”, coerente com o `DELETE` lógico do backend.
