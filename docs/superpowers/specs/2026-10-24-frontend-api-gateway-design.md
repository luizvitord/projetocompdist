# DistriSchool: frontend da coordenação e API Gateway

## Intenção

Entregar uma interface web interna para a coordenação executar os fluxos já
implementados nos dois microsserviços de negócio. A primeira entrega precisa
ser navegável e demonstrável mesmo antes da autenticação real, sem criar novas
regras de negócio no frontend. Um API Gateway dará ao navegador uma única API e
preservará a separação entre os serviços Escolar e Administrativo.

## Usuários e escopo

- Usuário inicial: coordenação/secretaria escolar.
- Alunos não acessam a aplicação. Não haverá portal de aluno nesta etapa.
- Um login demonstrativo permite acessar as telas com uma credencial fixa,
  apenas no cliente. Não representa autenticação nem autorização.
- O backend de autenticação, emissão/validação de tokens, perfis e permissões
  estão fora do escopo e substituirão o login demonstrativo no futuro.
- Os microsserviços de negócio, seus bancos, migrations e contratos não serão
  alterados nesta entrega.

## Abordagens consideradas

1. **React + Vite + Tailwind + API Gateway Spring Boot (escolhida).** Permite
   uma SPA rápida, componentes reutilizáveis e uma apresentação visual moderna;
   o gateway torna o frontend independente de portas e endereços internos.
2. **Angular + gateway.** Oferece convenções fortes, mas exige mais estrutura e
   tempo de aprendizado sem benefício proporcional para esta entrega acadêmica.
3. **HTML/CSS/JavaScript puro sem gateway.** É simples no início, mas multiplica
   código de estado/formulários e acopla o navegador a dois microsserviços.

## Arquitetura proposta

```text
Navegador
  └── frontend (React/Vite, porta 5173 em desenvolvimento)
        └── /api/*
              └── api-gateway (Spring Cloud Gateway MVC, porta 8080)
                    ├── gestão administrativa (porta 8081)
                    └── gestão escolar (porta 8082)
```

O frontend terá uma variável `VITE_API_BASE_URL` com padrão `/api`. Em
desenvolvimento, o Vite fará proxy de `/api` para o gateway; em produção, o
servidor que expõe o frontend encaminhará o mesmo prefixo ao gateway. Portanto,
os componentes nunca chamam `8081` ou `8082` diretamente.

O gateway manterá os caminhos públicos atuais, sem reescrita:

| Caminho | Serviço de destino |
|---|---|
| `/api/alunos/**`, `/api/matriculas/**`, `/api/avaliacoes/**`, `/api/notas/**`, `/api/frequencias/**` | `gestao-escolar-service` |
| `/api/turmas/**`, `/api/disciplinas/**`, `/api/professores/**`, `/api/tecnicos/**` | `gestao-administrativa-service` |
| `/escolar/**` | `gestao-escolar-service` (Swagger/desenvolvimento) |
| `/administrativa/**` | `gestao-administrativa-service` (Swagger/desenvolvimento) |

O gateway terá configuração externa para as URLs de cada serviço, com padrões
locais `http://localhost:8082` e `http://localhost:8081`. No computador em que
8081 estiver ocupado, `ADMINISTRATIVA_URL=http://localhost:8083` permite a
execução temporária sem modificar o código. CORS será permitido somente para a
origem local do frontend nesta etapa; a configuração será substituída por regras
de produção quando existir autenticação.

### Contratos confirmados e limites do frontend

Os controladores atuais já expõem os caminhos exatamente sob `/api`, sem
prefixo adicional de serviço. As listagens retornam `PagedModel` do Spring e
aceitam `page`, `size` e `sort`, além dos filtros abaixo. O adaptador HTTP do
frontend normalizará a resposta para `items`, `page`, `size` e `totalElements`;
um teste de integração registrará o formato JSON exato retornado pela versão do
Spring usada no projeto.

| Recurso | Filtros confirmados |
|---|---|
| alunos | `nome`, `matricula`, `turmaId` |
| turmas | `anoLetivo`, `turno`, `professorId` |
| disciplinas | `nome`, `ativa` (padrão `true`) |
| professores | `nome` |
| técnicos | `nome`, `cargo`, `setor`, `ativo` (padrão `true`) |
| matrículas | `alunoId`, `turmaId`, `anoLetivo`, `status` |
| avaliações | `turmaId`, `disciplinaId`, `bimestre` |
| chamadas | `turmaId`, `disciplinaId`, `dataInicio`, `dataFim` |

Os vínculos de turma já existem: `PUT`/`DELETE`
`/api/turmas/{id}/professores/{professorId}` e `PUT`/`DELETE`
`/api/turmas/{id}/disciplinas/{disciplinaId}`, sendo que a oferta de disciplina
aceita opcionalmente `{ "professorId": number }`. Matrículas têm alteração de
status e transferência; notas têm lançamento em lote, correção e histórico;
frequências têm criação, edição e relatórios; boletim e frequência anual são
consultáveis por aluno. Logo, nenhuma operação proposta requer endpoint novo.

Erros dos dois serviços seguem RFC 9457 (`ProblemDetail`), com `status`,
`detail` e, em erros de validação, propriedade `erros` no formato
`{ campo: mensagem }`. O cliente mapeará `erros` para campos e preservará os
demais detalhes para erros globais. No momento não há endpoint de dashboard;
por decisão, essa tela permanece de navegação e não de indicadores.

## Login demonstrativo

- Rota pública: `/login`.
- Credencial inicial fixa: `coordenacao@distrischool.com` e senha `123456`.
- Ao validar, o cliente registra apenas uma sessão demonstrativa em
  `sessionStorage` e redireciona para a visão geral.
- Rotas internas verificam essa sessão e redirecionam para `/login` se ausente.
- O segredo não será tratado como segurança: ficará centralizado em um módulo
  temporário para remoção simples quando o serviço de autenticação existir.
- Sair remove a sessão demonstrativa.

## Modelo de navegação e telas

O aplicativo é desktop-first e responsivo para consulta em telas menores. A
estrutura permanente contém barra lateral, cabeçalho com usuário demonstrativo,
breadcrumbs, área de conteúdo, mensagens de erro e toasts de confirmação.

| Rota/tela | Conteúdo e ações | API existente |
|---|---|---|
| `/dashboard` — Visão geral | atalhos para fluxos; estado vazio útil, sem KPIs inventados | composição local |
| `/alunos` — Alunos | tabela paginada, busca por nome/matrícula/turma, criar/editar/excluir | alunos |
| `/alunos/:id` — Perfil do aluno | dados pessoais, matrículas, boletim por ano e frequência por ano | alunos, matrículas, boletim, frequência |
| `/turmas` — Turmas | tabela, filtros por ano/turno, criar/editar/excluir | turmas |
| `/turmas/:id` — Detalhe da turma | dados, professores e disciplinas ofertadas; vincular/remover professor e disciplina | turmas e vínculos |
| `/disciplinas` — Disciplinas | tabela, filtro de ativas, criar/editar/inativar | disciplinas |
| `/pessoas` — Pessoas | abas para professores e técnicos; cadastros e edições | professores e técnicos |
| `/matriculas` — Matrículas | tabela/filtros, criar, alterar status e transferir | matrículas |
| `/avaliacoes` — Avaliações e notas | filtros por turma/disciplina/bimestre, CRUD de avaliação, lançamento em lote e histórico de correções | avaliações e notas |
| `/frequencia` — Frequência | criar/editar chamada, consultar chamadas e relatório por turma/período | frequências |

Páginas de lista compartilham o padrão: título, descrição curta, botão de ação
primária, filtros, tabela paginada, estado de carregamento, estado vazio e erro
recuperável. Criação/edição usará drawer lateral ou modal conforme o formulário;
fluxos que exigem leitura contextual, como lançamento de notas e chamada, usarão
uma página/drawer amplo.

## Fluxos prioritários

1. Cadastrar aluno → localizar turma → criar matrícula.
2. Cadastrar turma → cadastrar/ofertar disciplinas → vincular professor.
3. Escolher turma/disciplina/data → registrar chamada → consultar relatório.
4. Criar avaliação → lançar notas de todas as matrículas ativas → consultar
   boletim do aluno.
5. Atualizar status de matrícula ou transferir aluno, preservando o histórico.

Os seletores de turmas, disciplinas e professores serão carregados das APIs
administrativas. O frontend exibirá IDs como fallback técnico, mas dará
preferência a código e nome quando disponíveis. Nenhuma regra de capacidade,
média, frequência ou transição de status será reimplementada no cliente: erros
`ProblemDetail` retornados pelos serviços aparecem de forma legível no formulário.

## Direção visual

- Fundo neutro cinza-claro, superfícies brancas e boa densidade para tabelas.
- Navegação lateral azul-marinho; azul/teal como destaque de ações.
- Tipografia limpa, hierarquia forte e ícones discretos.
- Tags semânticas: verde para ativo/aprovado, âmbar para pendência/trancada,
  vermelho para cancelamento, reprovação ou frequência abaixo do mínimo.
- Acessibilidade: contraste AA, foco visível, rótulos nos campos, mensagens de
  validação associadas ao respectivo input e ações destrutivas confirmadas.

## Estrutura técnica do frontend

O diretório `frontend/` conterá Vite, React, TypeScript, Tailwind, React Router
e uma biblioteca leve de componentes acessíveis. O código será organizado em:

```text
src/
  app/             # rotas, providers e layout
  components/      # componentes reutilizáveis de UI
  features/        # telas, formulários e adaptadores por domínio
  services/        # cliente HTTP e contratos das APIs
  mocks/           # dados somente para os estados demonstrativos
```

As interfaces TypeScript refletirão os DTOs atuais dos backends. A resposta
paginada do Spring será adaptada em um ponto único, evitando que detalhes do
formato de paginação se espalhem pelas telas.

## Tratamento de falhas e estados

- `400`: mostra validações por campo ou mensagem geral.
- `404`: mostra recurso não encontrado com retorno à lista.
- `409`: explica o conflito de regra de negócio preservando os dados digitados.
- `503`/rede: oferece nova tentativa; não apresenta dados como salvos.
- Todos os formulários bloqueiam reenvio enquanto a requisição está em curso.
- Enquanto a integração real não existir, dados mockados serão explicitamente
  marcados como demonstração e nunca misturados com dados da API.

## Verificação de entrega

- Frontend: build de produção, lint e testes dos componentes/rotas críticos.
- Gateway: testes de rotas para cada grupo de caminhos e execução local com os
  dois serviços disponíveis.
- Integração: o navegador acessa exclusivamente o gateway, cria/lê ao menos um
  recurso de cada serviço e apresenta erros de API de forma compreensível.

## Decisões deixadas para a implementação

- Biblioteca específica de componentes acessíveis: selecionar a opção que
  integre melhor com Tailwind e mantenha o bundle simples.
- Dados de demonstração iniciais: representar cenários realistas sem dados
  pessoais reais.
- Dashboard: até que existam endpoints agregados, usar atalhos operacionais e
  orientação de fluxo, não contagens calculadas no cliente.
