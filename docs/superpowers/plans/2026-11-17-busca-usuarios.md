# Busca de usuários — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir pesquisar usuários por nome ou e-mail na tela de gestão, mantendo perfil, paginação e estado da busca sincronizados.

**Architecture:** O usuários-service recebe o parâmetro opcional `busca` e aplica a mesma consulta junto aos filtros de perfil e ativo. A tela reutiliza `SearchInput` e `useBuscaNaUrl`, padrão já usado nas listagens administrativas, enviando o termo ao backend e retornando à primeira página.

**Tech Stack:** Spring Data JPA, Spring MVC, React, TypeScript, Vitest, MSW.

**Spec:** Solicitação do usuário de 2026-11-17 para busca textual na nova tela de usuários.

## Global Constraints

- A busca textual existente em outras telas não deve ser alterada.
- A busca deve respeitar os filtros `perfil` e `ativo` existentes e a paginação.
- Sem novas dependências.

## Review Focus

- Termo vazio mantém a listagem normal.
- Busca por e-mail retorna resultados, não só nome.
- Trocar a busca retorna à primeira página.
- Perfil e busca são aplicados juntos.
- Resposta antiga não pode substituir uma busca mais recente.

---

### Task 1: Busca paginada no usuários-service

**Files:**
- Modify: `usuarios-service/src/main/java/com/distrischool/usuarios/usuario/UsuarioController.java`
- Modify: `usuarios-service/src/main/java/com/distrischool/usuarios/usuario/UsuarioService.java`
- Modify: `usuarios-service/src/main/java/com/distrischool/usuarios/usuario/UsuarioRepository.java`
- Test: `usuarios-service/src/test/java/com/distrischool/usuarios/usuario/UsuarioServiceTest.java`

**Interfaces:**
- Consumes: `GET /api/usuarios?busca={texto}&perfil={perfil}&ativo={ativo}&page={n}`.
- Produces: página de usuários cujo nome ou e-mail contém o texto, sem ignorar filtros existentes.

- [ ] Escrever teste que falha ao buscar por nome/e-mail com perfil e ativo.
- [ ] Executar o teste e confirmar a falha.
- [ ] Propagar `busca` do controller para service e repository, usando consulta paginada case-insensitive.
- [ ] Executar `sh mvnw -q test` e confirmar sucesso.
- [ ] Commitar a etapa.

### Task 2: Campo de busca na gestão de usuários

**Files:**
- Modify: `frontend/src/features/usuarios/UsuariosPage.tsx`
- Test: `frontend/src/features/usuarios/__tests__/UsuariosPage.test.tsx`

**Interfaces:**
- Consumes: o parâmetro `busca` de `GET /api/usuarios`.
- Produces: busca debounced, estado `?busca=` na URL e paginação reiniciada.

- [ ] Escrever teste de tela que falha ao digitar e enviar `busca`.
- [ ] Executar o teste e confirmar a falha.
- [ ] Reutilizar `SearchInput` e `useBuscaNaUrl`, cancelando consulta anterior como nas demais listagens.
- [ ] Executar testes do arquivo e a suíte completa, lint e build do frontend.
- [ ] Commitar a etapa.
