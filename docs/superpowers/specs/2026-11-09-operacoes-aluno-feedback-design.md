# Operações por matrícula e feedback visual — Design

## Objetivo

Eliminar a exposição e a digitação de IDs internos de aluno e matrícula nas operações da coordenação, além de dar confirmação visual consistente para cada ação enviada ao backend.

## Contrato de API

Os IDs continuam sendo chaves internas persistidas no banco. A API pública destinada ao frontend passa a usar a matrícula institucional (`String`) para identificar o aluno.

- `POST /api/matriculas` recebe `{ "matriculaAluno": "2026000001", "turmaId": 1 }`.
- A resposta de matrícula contém `alunoNome` e `matriculaAluno`, além dos campos administrativos existentes. Não contém `alunoId`.
- Cada item de `POST /api/avaliacoes/{avaliacaoId}/notas` recebe `{ "matriculaAluno": "2026000001", "valor": 8.5 }`.
- Cada item de `POST` e `PUT /api/frequencias/chamadas` recebe `{ "matriculaAluno": "2026000001", "status": "PRESENTE", "observacao": null }`.
- Serviços resolvem matrícula institucional para `Aluno`, e depois para uma matrícula ATIVA da turma pertinente. Os repositórios e eventos continuam usando IDs internamente.
- Erros de validação que antes expunham IDs de matrícula passam a devolver as matrículas institucionais correspondentes quando for possível.

## Interface

- Matrículas exibe as colunas **Matrícula** e **Aluno**; IDs internos não aparecem.
- Matrícula e transferência usam seletor de turma com código, nome, ano e turno; a tabela também mostra a turma por código e nome, sem expor seu ID.
- Frequência segue uma consulta guiada: a coordenação escolhe a turma, vê somente as disciplinas ofertadas nela e o professor responsável é derivado da oferta. A tela carrega todos os alunos com matrícula ativa na turma e envia uma linha de presença para cada um.
- Em notas, abrir uma avaliação carrega os alunos com matrícula ativa na turma dela. A coordenação informa as notas ao lado do nome e da matrícula institucional, sem descobrir ou digitar IDs.
- Ao ofertar uma disciplina, a tela de turma oferece seletores de disciplina ativa e professor pelo nome; o backend mantém a associação da oferta e atribui o professor à turma quando necessário.
- Antes de qualquer operação que persista ou altere estado, o usuário vê um diálogo de confirmação. Confirmar executa a ação; cancelar preserva o formulário sem requisição.
- Um centro de feedback global mostra banners fecháveis: sucesso (verde), erro (vermelho) e aviso (amarelo). Respostas 200/201 mostram sucesso; erros da API mostram a mensagem retornada.
- As páginas deixam de manter mensagens locais para mutações. Falhas de carregamento e operações também usam o centro global.
- Os cadastros genéricos de alunos, turmas, disciplinas e pessoas também confirmam o envio e exibem o feedback global, para que não exista ação persistente sem retorno visual.

## Arquitetura

No backend, `AlunoService` será a fronteira de resolução por matrícula. `MatriculaService`, `NotaService` e `ChamadaService` chamarão métodos explícitos que resolvem somente a matrícula ATIVA e pertencente à turma da operação, mantendo entidades e eventos inalterados.

No frontend, um `FeedbackProvider` no ponto de entrada fornece `showSuccess`, `showError` e `showWarning`. Um `ConfirmDialog` reutilizável encapsula a confirmação assíncrona e seu estado de carregamento. As páginas de matrícula, avaliações, frequência e turma passam a usar ambos, em vez de mensagens locais ou `window.confirm`.

## Limites

- Não há autenticação, alteração de gateway, Docker ou Kubernetes nesta entrega.
- Não é necessário criar entidade, relação ou endpoint novo para os lookups: eles compõem `GET /turmas/{id}` com `GET /alunos?turmaId={id}`.
- Rotas administrativas de consulta por ID fora desses fluxos não mudam, pois são internas ou não são exibidas em telas operacionais.
