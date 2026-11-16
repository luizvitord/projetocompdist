# Gestão de usuários no frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Disponibilizar a administração de contas e perfis no frontend, respeitando as permissões do backend.

**Architecture:** Uma página dedicada em `features/usuarios` consome diretamente `/api/usuarios` e reutiliza a camada HTTP, modais, tabela, paginação, confirmações e feedback. A rota usa uma guarda exclusiva de `ADMIN`, e a Sidebar deriva a visibilidade da sessão atual.

**Tech Stack:** React 19, TypeScript, React Router, Vitest, Testing Library e MSW.

**Spec:** `docs/superpowers/specs/2026-11-16-gestao-usuarios-frontend-design.md`

## Global Constraints

- Não alterar o contrato existente do backend nem adicionar dependências.
- Manter textos e mensagens em português.
- Perfis permitidos: `ADMIN`, `PROFESSOR`, `ALUNO`, `RESPONSAVEL`.
- Somente `ADMIN` acessa `/usuarios`; `PROFESSOR` continua nas páginas operacionais e `ALUNO`/`RESPONSAVEL` no portal.

## Review Focus

- Um perfil não administrativo acessando `/usuarios` deve ser redirecionado, não receber uma página parcialmente funcional — coberto na Tarefa 1.
- O cadastro de `ADMIN` não pode preservar um `referenciaId` de uma seleção anterior — coberto na Tarefa 2.
- `PROFESSOR`, `ALUNO` e `RESPONSAVEL` devem exigir referência positiva — coberto na Tarefa 2.
- A edição não pode enviar senha, pois o endpoint de atualização não a aceita — coberto na Tarefa 2.
- A desativação deve comunicar o efeito real e chamar `DELETE`, sem remover registros por engano — coberto na Tarefa 2.

### Task 1: Isolamento de navegação administrativa

**Files:**
- Modify: `frontend/src/features/auth/RequirePerfil.tsx`
- Modify: `frontend/src/app/routes.tsx`
- Modify: `frontend/src/app/Sidebar.tsx`
- Modify: `frontend/src/features/auth/__tests__/RequirePerfil.test.tsx`

**Interfaces:**
- Produces: `RequireAdmin`, uma guarda que renderiza `Outlet` apenas quando a sessão tem perfil `ADMIN`.
- Consumes: `getSession`, `paginaInicial` e `Usuario.perfil`.

- [ ] **Step 1: Write the failing route-guard test**

```tsx
it('sends PROFESSOR away from user management', async () => {
  login('PROFESSOR', 3);
  const { router } = renderAt('/usuarios');
  expect(await screen.findByRole('heading', { name: 'Visão geral' })).toBeVisible();
  expect(router.state.location.pathname).toBe('/dashboard');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `PATH=/home/janylson/.nvm/versions/node/v22.19.0/bin:$PATH npm test -- --run src/features/auth/__tests__/RequirePerfil.test.tsx`
Expected: FAIL because `/usuarios` is not protected by an admin-specific guard.

- [ ] **Step 3: Implement the guard and route**

Add `RequireAdmin` in `RequirePerfil.tsx`; nest `/usuarios` under it in `routes.tsx`. Make `Sidebar` consult the current session and include “Usuários” only for `ADMIN`.

- [ ] **Step 4: Run route tests to verify they pass**

Run: `PATH=/home/janylson/.nvm/versions/node/v22.19.0/bin:$PATH npm test -- --run src/features/auth/__tests__/RequirePerfil.test.tsx`
Expected: PASS.

### Task 2: Página de gestão de usuários

**Files:**
- Create: `frontend/src/features/usuarios/UsuariosPage.tsx`
- Create: `frontend/src/features/usuarios/__tests__/UsuariosPage.test.tsx`
- Modify: `frontend/src/app/routes.tsx`
- Modify: `README.md`

**Interfaces:**
- Consumes: `api`, `isApiProblem`, `toPage`, `FeedbackProvider`, `Dialog`, `ConfirmDialog`, `DataTable`, `Pagination`, `Perfil` e `/api/usuarios`.
- Produces: `UsuariosPage`, a rota administrativa de contas.

- [ ] **Step 1: Write failing tests for profile-aware create and update payloads**

```tsx
it('creates a RESPONSAVEL with the selected student reference', async () => {
  // preenche perfil RESPONSAVEL e referência 12
  // espera POST /api/usuarios com senha e referenciaId: 12
});

it('updates an ADMIN without password or reference', async () => {
  // edita a conta e espera PUT sem senha e com referenciaId: null
});
```

- [ ] **Step 2: Run the page test to verify it fails**

Run: `PATH=/home/janylson/.nvm/versions/node/v22.19.0/bin:$PATH npm test -- --run src/features/usuarios/__tests__/UsuariosPage.test.tsx`
Expected: FAIL because `UsuariosPage` does not exist.

- [ ] **Step 3: Implement `UsuariosPage`**

List accounts through `GET /usuarios` with optional profile filter, render the four profile labels, and provide create/edit dialogs. Validate a positive numeric reference for all non-admin profiles; omit password from the update payload; send `null` reference for `ADMIN`; use confirmation before `DELETE /usuarios/{id}` and refresh the page after successful mutations.

- [ ] **Step 4: Run the page test to verify it passes**

Run: `PATH=/home/janylson/.nvm/versions/node/v22.19.0/bin:$PATH npm test -- --run src/features/usuarios/__tests__/UsuariosPage.test.tsx`
Expected: PASS.

- [ ] **Step 5: Update the README**

Replace the instruction that requires the API to create a `RESPONSAVEL` with the administrative UI flow, retaining the requirement that `referenciaId` is the student identifier.

### Task 3: Full verification

**Files:**
- Verify all frontend files modified by Tasks 1 and 2.

- [ ] **Step 1: Run the complete frontend test suite**

Run: `PATH=/home/janylson/.nvm/versions/node/v22.19.0/bin:$PATH npm test -- --run`
Expected: PASS with zero failures.

- [ ] **Step 2: Run lint and production build**

Run: `PATH=/home/janylson/.nvm/versions/node/v22.19.0/bin:$PATH npm run lint && PATH=/home/janylson/.nvm/versions/node/v22.19.0/bin:$PATH npm run build`
Expected: both commands exit successfully.
