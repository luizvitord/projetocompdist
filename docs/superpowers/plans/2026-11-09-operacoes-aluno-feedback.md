# Operações por matrícula e feedback visual Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer as operações escolares usarem a matrícula institucional do aluno e fornecer confirmação e feedback visual uniformes no frontend.

**Architecture:** O backend resolve a matrícula institucional para os IDs internos já persistidos e expõe nome e matrícula nas respostas de matrícula. O frontend centraliza alertas e confirmações, e adapta os fluxos de matrícula, notas, frequência e remoção de professor ao novo contrato.

**Tech Stack:** Java 21, Spring Boot, JPA, JUnit/Mockito, React 19, TypeScript, Radix Dialog, Vitest, Testing Library e MSW.

**Spec:** `docs/superpowers/specs/2026-11-09-operacoes-aluno-feedback-design.md`

## Global Constraints

- IDs permanecem internos ao banco, entidades e eventos; a UI não os mostra para operações de aluno.
- O identificador público é `matriculaAluno` (`String`) em todas as requisições alteradas.
- Operações mutáveis exigem confirmação explícita e retornam feedback visual categorizado.
- Não adicionar dependências, Docker, Kubernetes, autenticação ou alterações no gateway.

## Review Focus

- Matrícula institucional inexistente deve retornar 404 sem criar ou alterar dados — testada na Tarefa 1.
- Matrícula de aluno fora da turma ou inativa deve falhar sem gerar nota ou chamada — testada na Tarefa 2.
- Cancelar confirmação não pode disparar requisição — testada na Tarefa 3.
- Resposta 2xx deve gerar alerta de sucesso e mensagem Problem Detail deve gerar alerta de erro — testada na Tarefa 3.
- As listas de matrícula não podem renderizar `alunoId` ou `matriculaId` — testada na Tarefa 4.

---

### Task 1: Contrato público de matrícula

**Files:**
- Modify: `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/aluno/AlunoRepository.java`
- Modify: `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/aluno/AlunoService.java`
- Modify: `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/matricula/MatriculaRequest.java`
- Modify: `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/matricula/MatriculaResponse.java`
- Modify: `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/matricula/MatriculaService.java`
- Modify: `gestao-escolar-service/src/test/java/com/distrischool/gestao_escolar/matricula/MatriculaServiceTest.java`
- Test: `gestao-escolar-service/src/test/java/com/distrischool/gestao_escolar/matricula/MatriculaControllerTest.java`

**Interfaces:**
- Produces: `AlunoService.obterPorMatricula(String): Aluno` and `MatriculaResponse.alunoNome()/matriculaAluno()`.
- Produces: `MatriculaRequest(String matriculaAluno, Long turmaId)`.

- [ ] **Step 1: Write failing backend tests**

Add tests proving a known institutional number creates a matrícula and its response has `alunoNome` and `matriculaAluno`; prove a nonexistent number returns the existing not-found error.

- [ ] **Step 2: Run the matrícula tests to verify they fail**

Run: `cd gestao-escolar-service && ./gradlew test --tests '*MatriculaServiceTest' --tests '*MatriculaControllerTest'`
Expected: FAIL because `matriculaAluno`, response fields and lookup do not exist.

- [ ] **Step 3: Implement matrícula lookup and response projection**

Add `findByMatricula` to the repository, resolve the trimmed public number in `AlunoService`, persist its ID in `MatriculaService`, and construct the new response with the resolved name and number.

- [ ] **Step 4: Run the matrícula tests to verify they pass**

Run: `cd gestao-escolar-service && ./gradlew test --tests '*MatriculaServiceTest' --tests '*MatriculaControllerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -am "feat: use student registration in enrollments"`

### Task 2: Contrato público de notas e frequência

**Files:**
- Modify: `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/avaliacao/LancamentoNotaRequest.java`
- Modify: `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/avaliacao/NotaService.java`
- Modify: `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/frequencia/ChamadaRequest.java`
- Modify: `gestao-escolar-service/src/main/java/com/distrischool/gestao_escolar/frequencia/ChamadaService.java`
- Test: `gestao-escolar-service/src/test/java/com/distrischool/gestao_escolar/avaliacao/NotaServiceTest.java`
- Test: `gestao-escolar-service/src/test/java/com/distrischool/gestao_escolar/frequencia/ChamadaServiceTest.java`

**Interfaces:**
- Consumes: `AlunoService.obterPorMatricula(String)` from Task 1.
- Produces: public request items with `matriculaAluno`, while `Nota` and `RegistroFrequencia` still persist `matriculaId`.

- [ ] **Step 1: Write failing backend tests**

Add one grade test and one attendance test that send `matriculaAluno`, resolve the correct active enrollment, and reject a registration that is not active for the target turma.

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `cd gestao-escolar-service && ./gradlew test --tests '*NotaServiceTest' --tests '*ChamadaServiceTest'`
Expected: FAIL because request items still require `matriculaId`.

- [ ] **Step 3: Resolve active enrollments by public registration**

Inject `AlunoService` where needed, map each public number to the active enrollment for the relevant turma, and retain existing duplicate, completeness, maximum-grade, persistence and event rules using the resolved IDs.

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `cd gestao-escolar-service && ./gradlew test --tests '*NotaServiceTest' --tests '*ChamadaServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -am "feat: accept student registration for grades and attendance"`

### Task 3: Reusable frontend feedback and confirmation

**Files:**
- Create: `frontend/src/components/feedback/FeedbackProvider.tsx`
- Create: `frontend/src/components/feedback/FeedbackBanner.tsx`
- Create: `frontend/src/components/ui/ConfirmDialog.tsx`
- Modify: `frontend/src/main.tsx`
- Test: `frontend/src/components/feedback/__tests__/FeedbackProvider.test.tsx`
- Test: `frontend/src/components/ui/__tests__/ConfirmDialog.test.tsx`

**Interfaces:**
- Produces: `useFeedback()` with `showSuccess(message)`, `showError(message)`, and `showWarning(message)`.
- Produces: `ConfirmDialog` that calls `onConfirm(): Promise<void> | void` only after its visible confirm action.

- [ ] **Step 1: Write failing component tests**

Cover each alert category, close action, confirm action and cancellation without confirmation callback execution.

- [ ] **Step 2: Run the component tests to verify they fail**

Run: `cd frontend && npm test -- --run src/components/feedback/__tests__/FeedbackProvider.test.tsx src/components/ui/__tests__/ConfirmDialog.test.tsx`
Expected: FAIL because the provider and confirmation component do not exist.

- [ ] **Step 3: Implement the provider, banner and asynchronous confirmation dialog**

Use React context for one visible message at a time and the existing Radix dialog dependency for accessible modal behavior. Include success, error and warning visual variants and disabled/loading confirm state.

- [ ] **Step 4: Wrap the application at its entry point**

Place `FeedbackProvider` around `RouterProvider` so every route shares the same feedback surface.

- [ ] **Step 5: Run the component tests to verify they pass**

Run: `cd frontend && npm test -- --run src/components/feedback/__tests__/FeedbackProvider.test.tsx src/components/ui/__tests__/ConfirmDialog.test.tsx`
Expected: PASS.

- [ ] **Step 6: Commit**

`git commit -am "feat: add global feedback and action confirmation"`

### Task 4: Adapt operational screens to the public contract

**Files:**
- Modify: `frontend/src/types/escolar.ts`
- Modify: `frontend/src/features/matriculas/MatriculasPage.tsx`
- Modify: `frontend/src/features/avaliacoes/AvaliacoesPage.tsx`
- Modify: `frontend/src/features/frequencia/FrequenciaPage.tsx`
- Modify: `frontend/src/features/turmas/TurmaDetailPage.tsx`
- Modify: `frontend/src/features/administrativo/RegistryPage.tsx`
- Modify: `frontend/src/features/matriculas/__tests__/MatriculasPage.test.tsx`
- Modify: `frontend/src/features/avaliacoes/__tests__/LancamentoNotasDrawer.test.tsx`
- Modify: `frontend/src/features/frequencia/__tests__/ChamadaForm.test.tsx`
- Test: `frontend/src/features/administrativo/__tests__/RegistryPage.test.tsx`
- Test: `frontend/src/features/turmas/__tests__/TurmaDetailPage.test.tsx`

**Interfaces:**
- Consumes: backend fields `matriculaAluno`, `alunoNome` and request field `matriculaAluno`.
- Consumes: `useFeedback` and `ConfirmDialog` from Task 3.

- [ ] **Step 1: Write failing screen tests**

Update MSW assertions for public registration payloads and responses; verify the matrícula table displays name and registration, successful responses show success feedback, errors show error feedback, and cancelling a mutation sends no request. Add the same confirmation and feedback coverage to the generic registry form.

- [ ] **Step 2: Run screen tests to verify they fail**

Run: `cd frontend && npm test -- --run src/features/matriculas/__tests__/MatriculasPage.test.tsx src/features/avaliacoes/__tests__/LancamentoNotasDrawer.test.tsx src/features/frequencia/__tests__/ChamadaForm.test.tsx src/features/turmas/__tests__/TurmaDetailPage.test.tsx src/features/administrativo/__tests__/RegistryPage.test.tsx`
Expected: FAIL because views use IDs, local messages and `window.confirm`.

- [ ] **Step 3: Implement screen integration**

Replace student/matrícula ID fields with the labeled public registration field; display `matriculaAluno` and `alunoNome`; route mutation and loading feedback through the provider; require `ConfirmDialog` before every state-changing request, including the generic registry creation form.

- [ ] **Step 4: Run screen tests to verify they pass**

Run: `cd frontend && npm test -- --run src/features/matriculas/__tests__/MatriculasPage.test.tsx src/features/avaliacoes/__tests__/LancamentoNotasDrawer.test.tsx src/features/frequencia/__tests__/ChamadaForm.test.tsx src/features/turmas/__tests__/TurmaDetailPage.test.tsx src/features/administrativo/__tests__/RegistryPage.test.tsx`
Expected: PASS.

- [ ] **Step 5: Run complete verification and commit**

Run: `cd gestao-escolar-service && ./gradlew test && cd ../frontend && npm test -- --run && npm run lint && npm run build`
Expected: all backend and frontend tests, lint and production build pass.

`git commit -am "feat: show student registration and confirm operations"`

### Task 5: Guided lookups for class operations

**Files:**
- Modify: `frontend/src/features/matriculas/MatriculasPage.tsx`
- Modify: `frontend/src/features/turmas/TurmaDetailPage.tsx`
- Modify: `frontend/src/features/avaliacoes/AvaliacoesPage.tsx`
- Modify: `frontend/src/features/frequencia/FrequenciaPage.tsx`

**Interfaces:**
- `GET /api/turmas/{id}` supplies the offered disciplines and their responsible professor.
- `GET /api/alunos?turmaId={id}` supplies the active students of the selected class.

- [x] Replace technical ID inputs with readable selectors for class, discipline and professor.
- [x] Build the call sheet from every active enrollment in the selected class and post their `matriculaAluno` values.
- [x] Build the grade-entry form from the active students of the evaluation class and post their `matriculaAluno` values.
- [x] Verify the lookup and payload flow with MSW screen tests and a production frontend build.
