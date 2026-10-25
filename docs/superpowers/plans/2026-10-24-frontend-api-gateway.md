# DistriSchool Frontend and API Gateway Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a React administrative portal for the coordination team and a Spring Boot API Gateway that is the portal's sole backend entry point.

**Architecture:** A new `api-gateway` Spring Boot application exposes the current `/api/**`, `/administrativa/**`, and `/escolar/**` paths on port 8080 and routes them to the two business services without path rewrites. A Vite React/TypeScript SPA in `frontend` uses `/api` only, provides a demo-only session login, reusable administrative UI primitives, and feature modules aligned to the existing backend DTOs.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Cloud Gateway MVC (version selected from the compatible Spring Cloud BOM), Maven Wrapper, React, TypeScript, Vite, Tailwind CSS, React Router, TanStack Query, React Hook Form, Zod, Vitest, Testing Library, MSW, Docker, Kubernetes/Kustomize.

**Spec:** `docs/superpowers/specs/2026-10-24-frontend-api-gateway-design.md`

## Global Constraints

- The portal is exclusively for coordination/secretariat; no student-facing routes are built.
- Demo login is `coordenacao@distrischool.com` / `123456`, stored only in `sessionStorage`; it is not security.
- The browser calls only `/api/**` through the gateway, never the services' ports directly.
- The gateway preserves existing paths and routes `/api/alunos`, `/api/matriculas`, `/api/avaliacoes`, `/api/notas`, `/api/frequencias`, and `/escolar/**` to the school service; it routes `/api/turmas`, `/api/disciplinas`, `/api/professores`, `/api/tecnicos`, and `/administrativa/**` to the administrative service.
- Gateway target URLs are configurable through environment variables, defaulting to `http://localhost:8082` and `http://localhost:8081`.
- Do not change business-service APIs, database schemas, Flyway migrations, or Kafka contracts.
- The gateway listens on port 8080; Vite uses port 5173 in development.
- All lists must preserve Spring pagination through one client-side adapter and surface RFC 9457 `ProblemDetail`, including its `erros` field map.
- The dashboard uses shortcuts and instructions only; it must not manufacture business metrics.

## Review Focus

- Gateway must forward nested paths (for example, `/api/alunos/7/boletim`) without losing method, query string, status, or `ProblemDetail`; covered in Task 2 route tests.
- A protected route reached with no or cleared demo session must redirect to `/login`; covered in Task 4 router tests.
- A `409` API error must preserve typed form values and show the backend detail rather than appearing successful; covered in Task 5 client/form tests.
- Pagination must handle the Spring `PagedModel` payload produced by the running services, including an empty last page; covered in Task 5 adapter tests.
- Batch grade and attendance rows must map validation errors such as `notas[0].valor` to the correct row; covered in Tasks 8 and 9.

---

## File Structure

| Area | Files | Responsibility |
|---|---|---|
| Gateway | `api-gateway/pom.xml`, `src/main/**`, `src/test/**`, `Dockerfile` | Gateway dependencies, property-bound route configuration, CORS and route tests. |
| Frontend foundation | `frontend/package.json`, Vite/Tailwind config, `src/main.tsx`, `src/app/**` | Build tooling, router, app providers, auth guard and permanent layout. |
| Frontend shared | `frontend/src/components/**`, `src/lib/**`, `src/services/**`, `src/types/**` | Accessible UI primitives, HTTP/pagination/error adapters and backend DTO types. |
| Frontend features | `frontend/src/features/<domain>/**` | Screen-specific table, form, detail and API-query components. |
| Local/runtime infra | root `docker-compose.yml`, Dockerfiles, `.env.example` files | One-command development topology and documented environment variables. |
| Kubernetes | `k8s/base/api-gateway/**`, `k8s/base/ingress.yaml`, `k8s/base/kustomization.yaml`, `k8s/overlays/minikube/kustomization.yaml`, `k8s/README.md` | Gateway deployment, service, config and ingress entry point. |

### Task 1: Create the API Gateway module and its configuration contract

**Files:**
- Create: `api-gateway/pom.xml`
- Create: `api-gateway/mvnw`, `api-gateway/mvnw.cmd`, `api-gateway/.mvn/wrapper/**`
- Create: `api-gateway/src/main/java/com/distrischool/api_gateway/ApiGatewayApplication.java`
- Create: `api-gateway/src/main/java/com/distrischool/api_gateway/config/GatewayRoutesProperties.java`
- Create: `api-gateway/src/main/resources/application.properties`
- Create: `api-gateway/src/test/java/com/distrischool/api_gateway/ApiGatewayApplicationTests.java`
- Create: `api-gateway/.env.example`

**Interfaces:**
- Produces: `GatewayRoutesProperties` with `administrativaUrl`, `escolarUrl`, and `allowedOrigin`; later route configuration consumes it.
- Produces: port `8080`, `ADMINISTRATIVA_URL`, `ESCOLAR_URL`, and `FRONTEND_ORIGIN` environment contract.

- [ ] **Step 1: Write the failing context test**

```java
@SpringBootTest(properties = {
  "ADMINISTRATIVA_URL=http://administrativa.test",
  "ESCOLAR_URL=http://escolar.test"
})
class ApiGatewayApplicationTests {
  @Test void contextLoads() {}
}
```

- [ ] **Step 2: Run the test to verify it fails because the gateway module does not exist**

Run: `cd api-gateway && ./mvnw test`

Expected: FAIL because `api-gateway` and its Maven project do not yet exist.

- [ ] **Step 3: Create the Maven module and property binding**

Use Java 21 and Spring Boot 4.1.1. Select and pin the Spring Cloud BOM release declared compatible with that Boot version in Spring's compatibility documentation, then add the MVC gateway starter, actuator and test dependencies. Bind `gateway.administrativa-url`, `gateway.escolar-url` and `gateway.allowed-origin` to `GatewayRoutesProperties`; property defaults are `http://localhost:8081`, `http://localhost:8082` and `http://localhost:5173`.

- [ ] **Step 4: Run the context test**

Run: `cd api-gateway && ./mvnw test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add api-gateway
git commit -m "feat: scaffold configurable API gateway"
```

### Task 2: Implement gateway routing, CORS, and route-level verification

**Files:**
- Create: `api-gateway/src/main/java/com/distrischool/api_gateway/config/GatewayConfig.java`
- Create: `api-gateway/src/test/java/com/distrischool/api_gateway/config/GatewayConfigTest.java`
- Modify: `api-gateway/src/main/resources/application.properties`

**Interfaces:**
- Consumes: `GatewayRoutesProperties` from Task 1.
- Produces: `RouterFunction<ServerResponse>`/gateway route definitions that retain path and query string, plus CORS for the configured frontend origin.

- [ ] **Step 1: Write failing route tests using two mock upstream servers**

```java
@Test void routesNestedBoletimPathAndQueryToEscolar() { /* GET /api/alunos/7/boletim?anoLetivo=2026 */ }
@Test void routesTurmaPathToAdministrativa() { /* GET /api/turmas?page=1 */ }
@Test void relaysConflictProblemDetailWithoutChangingStatus() { /* upstream 409 RFC 9457 */ }
@Test void allowsConfiguredFrontendOriginAndRejectsAnother() { /* CORS preflight */ }
```

- [ ] **Step 2: Run the route test class to verify it fails**

Run: `cd api-gateway && ./mvnw test -Dtest=GatewayConfigTest`

Expected: FAIL because no routes or CORS rules exist.

- [ ] **Step 3: Implement explicit route predicates**

Configure the school paths `/api/alunos/**`, `/api/matriculas/**`, `/api/avaliacoes/**`, `/api/notas/**`, `/api/frequencias/**`, `/escolar/**`; configure the administrative paths `/api/turmas/**`, `/api/disciplinas/**`, `/api/professores/**`, `/api/tecnicos/**`, `/administrativa/**`. Do not add rewrite filters. Set CORS for GET, POST, PUT, PATCH, DELETE and OPTIONS from `gateway.allowed-origin`.

- [ ] **Step 4: Run gateway verification**

Run: `cd api-gateway && ./mvnw test`

Expected: PASS, including path/query preservation and `409` passthrough.

- [ ] **Step 5: Commit**

```bash
git add api-gateway
git commit -m "feat: route school APIs through gateway"
```

### Task 3: Bootstrap the frontend toolchain and shared design tokens

**Files:**
- Create: `frontend/package.json`, `frontend/package-lock.json`, `frontend/tsconfig.json`, `frontend/vite.config.ts`, `frontend/index.html`
- Create: `frontend/tailwind.config.ts`, `frontend/postcss.config.js`, `frontend/.env.example`
- Create: `frontend/src/main.tsx`, `frontend/src/styles/index.css`, `frontend/src/vite-env.d.ts`
- Create: `frontend/src/test/setup.ts`, `frontend/src/test/render.tsx`
- Create: `frontend/src/components/ui/Button.tsx`, `Card.tsx`, `Dialog.tsx`, `EmptyState.tsx`, `DataTable.tsx`, `Pagination.tsx`, `StatusBadge.tsx`
- Create: `frontend/src/components/ui/__tests__/StatusBadge.test.tsx`

**Interfaces:**
- Produces: Tailwind visual tokens: navy navigation, blue/teal primary action, neutral surfaces and semantic status colors.
- Produces: reusable `DataTable<T>`, `Pagination`, `StatusBadge` and accessible dialog primitives for feature tasks.

- [ ] **Step 1: Write failing UI tests**

```tsx
it('renders semantic labels with non-color text', () => {
  render(<StatusBadge tone="warning">Trancada</StatusBadge>);
  expect(screen.getByText('Trancada')).toBeVisible();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npm test -- --run src/components/ui/__tests__/StatusBadge.test.tsx`

Expected: FAIL because the Vite project and component do not exist.

- [ ] **Step 3: Create Vite/React/TypeScript/Tailwind tooling and UI primitives**

Install React Router, TanStack Query, React Hook Form, Zod, Vitest, Testing Library, MSW and an accessible primitive library compatible with Tailwind. Configure `VITE_API_BASE_URL=/api` as the production default and a Vite development proxy from `/api`, `/escolar` and `/administrativa` to `http://localhost:8080`.

- [ ] **Step 4: Run frontend quality checks**

Run: `cd frontend && npm run lint && npm test -- --run && npm run build`

Expected: all commands pass.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: scaffold DistriSchool frontend"
```

### Task 4: Implement demo authentication, shell, navigation, and dashboard

**Files:**
- Create: `frontend/src/features/auth/demoAuth.ts`, `LoginPage.tsx`, `RequireDemoSession.tsx`
- Create: `frontend/src/app/router.tsx`, `AppShell.tsx`, `Sidebar.tsx`, `Topbar.tsx`, `NotFoundPage.tsx`
- Create: `frontend/src/features/dashboard/DashboardPage.tsx`
- Create: `frontend/src/features/auth/__tests__/RequireDemoSession.test.tsx`, `LoginPage.test.tsx`

**Interfaces:**
- Produces: `signIn(email: string, password: string): boolean`, `signOut(): void`, `hasDemoSession(): boolean`.
- Produces: protected route tree rooted at `/dashboard`; feature tasks attach routes below this shell.

- [ ] **Step 1: Write failing auth/router tests**

```tsx
it('redirects a visitor without demo session to /login', async () => { /* initial /alunos */ });
it('stores a session only for the fixed credentials and navigates to dashboard', async () => { /* login */ });
it('removes the session when the user signs out', async () => { /* topbar action */ });
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npm test -- --run src/features/auth/__tests__`

Expected: FAIL because the auth module and protected router do not exist.

- [ ] **Step 3: Implement demo session and app shell**

Put the only fixed credentials in `demoAuth.ts`, persist a non-sensitive session marker in `sessionStorage`, and never send those credentials to the API. Build login, responsive sidebar, breadcrumbs, logout control, 404 page and a dashboard containing only shortcut cards to the operational flows.

- [ ] **Step 4: Run test and build checks**

Run: `cd frontend && npm test -- --run src/features/auth && npm run build`

Expected: PASS; an unauthenticated `/alunos` visit lands on `/login`.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: add coordinator shell and demo login"
```

### Task 5: Create the typed API client, pagination adapter, and error mapping

**Files:**
- Create: `frontend/src/services/http.ts`, `pagination.ts`, `problemDetail.ts`
- Create: `frontend/src/types/api.ts`, `aluno.ts`, `turma.ts`, `administrativo.ts`, `escolar.ts`
- Create: `frontend/src/services/__tests__/http.test.ts`, `pagination.test.ts`, `problemDetail.test.ts`

**Interfaces:**
- Produces: `api.get<T>(path, options)`, `api.post<T>(path, body)`, `api.put<T>(path, body)`, `api.patch<T>(path, body)`, `api.delete(path)`.
- Produces: `toPage<T>(payload: SpringPagedModel<T>): Page<T>` and `ApiProblem` with `status`, `detail`, and `fieldErrors: Record<string, string>`.

- [ ] **Step 1: Write failing adapter tests with MSW**

```ts
it('normalizes a PagedModel content and page metadata');
it('maps a 400 problem.errors map into fieldErrors');
it('throws a 409 ApiProblem and does not clear caller state');
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npm test -- --run src/services/__tests__`

Expected: FAIL because no service adapter exists.

- [ ] **Step 3: Implement DTOs and client boundary**

Mirror the current request/response DTO names and enums. Fetch using `VITE_API_BASE_URL` only. Normalize `content`/`page` into the shared `Page<T>` type, parse RFC 9457 responses, and expose `fieldErrors` from `erros`.

- [ ] **Step 4: Run client tests**

Run: `cd frontend && npm test -- --run src/services/__tests__`

Expected: PASS for normal, empty, validation and conflict responses.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/services frontend/src/types
git commit -m "feat: add typed gateway API client"
```

### Task 6: Deliver administrative registry screens and class composition

**Files:**
- Create: `frontend/src/features/alunos/**`
- Create: `frontend/src/features/turmas/**`
- Create: `frontend/src/features/disciplinas/**`
- Create: `frontend/src/features/pessoas/**`
- Create: `frontend/src/features/administrativo/__tests__/RegistryFlows.test.tsx`
- Modify: `frontend/src/app/router.tsx`, `frontend/src/app/Sidebar.tsx`

**Interfaces:**
- Consumes: Task 3 UI primitives and Task 5 API client.
- Produces: routes `/alunos`, `/alunos/:id`, `/turmas`, `/turmas/:id`, `/disciplinas`, `/pessoas`.

- [ ] **Step 1: Write failing MSW interaction tests**

```tsx
it('filters and paginates alunos through GET /api/alunos');
it('creates an aluno with inline backend validation errors');
it('offers a disciplina to a turma with an optional professorId');
it('removes a professor from a turma only after confirmation');
it('keeps professores and tecnicos as separate tabs with their correct APIs');
```

- [ ] **Step 2: Run the registry test suite to verify it fails**

Run: `cd frontend && npm test -- --run src/features/administrativo`

Expected: FAIL because routes and feature components do not exist.

- [ ] **Step 3: Implement each registry and detail flow**

Implement paged tables and drawers/forms for Alunos, Turmas, Disciplinas, Professores and Técnicos. Add the Turma detail view with professor assignment and discipline offering/removal. The aluno profile loads the student's basic data and leaves grade/attendance panels to Task 9. Respect available filters only; do not invent delete for professor.

- [ ] **Step 4: Run frontend checks**

Run: `cd frontend && npm test -- --run src/features/administrativo && npm run lint && npm run build`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: add registry and class management screens"
```

### Task 7: Deliver enrolment lifecycle screens

**Files:**
- Create: `frontend/src/features/matriculas/MatriculasPage.tsx`, `MatriculaForm.tsx`, `StatusDialog.tsx`, `TransferDialog.tsx`, `api.ts`
- Create: `frontend/src/features/matriculas/__tests__/MatriculasPage.test.tsx`
- Modify: `frontend/src/app/router.tsx`, `frontend/src/app/Sidebar.tsx`

**Interfaces:**
- Consumes: `api`, `Aluno`, `Turma`, `Matricula` types.
- Produces: route `/matriculas` with create, filter, status transition and transfer interactions.

- [ ] **Step 1: Write failing lifecycle tests**

```tsx
it('creates an enrollment from selected aluno and turma');
it('sends PATCH /api/matriculas/:id/status with reason and surfaces 409 detail');
it('sends POST /api/matriculas/:id/transferencia with destination and reason');
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npm test -- --run src/features/matriculas`

Expected: FAIL because the enrolment feature does not exist.

- [ ] **Step 3: Implement the enrolment flows**

Load paged selections through the existing APIs, render filters for `alunoId`, `turmaId`, `anoLetivo`, and `status`, and use separate dialogs for status transition and transfer. Preserve entered values on API failure and display the returned `ProblemDetail`.

- [ ] **Step 4: Run feature checks**

Run: `cd frontend && npm test -- --run src/features/matriculas && npm run build`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: add enrollment management screens"
```

### Task 8: Deliver assessments, batch grade entry, and correction history

**Files:**
- Create: `frontend/src/features/avaliacoes/AvaliacoesPage.tsx`, `AvaliacaoForm.tsx`, `LancamentoNotasDrawer.tsx`, `CorrecaoNotaDialog.tsx`, `HistoricoNotaDialog.tsx`, `api.ts`
- Create: `frontend/src/features/avaliacoes/__tests__/LancamentoNotasDrawer.test.tsx`
- Modify: `frontend/src/app/router.tsx`, `frontend/src/app/Sidebar.tsx`

**Interfaces:**
- Consumes: evaluation, enrollment, class and discipline types from Task 5.
- Produces: route `/avaliacoes` and grade actions under an assessment.

- [ ] **Step 1: Write failing batch-grade tests**

```tsx
it('posts one grade record per chosen enrollment to /api/avaliacoes/:id/notas');
it('shows notas[0].valor validation beside the matching grade row');
it('opens correction and history using /api/notas/:id endpoints');
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npm test -- --run src/features/avaliacoes`

Expected: FAIL because the assessment feature does not exist.

- [ ] **Step 3: Implement assessments and grades**

Build filters for class, discipline and term; CRUD evaluation form; notes list; batch grade drawer; correction dialog requiring a reason; and immutable correction-history view. Reflect all existing fields including weight, maximum grade, type and date.

- [ ] **Step 4: Run feature checks**

Run: `cd frontend && npm test -- --run src/features/avaliacoes && npm run build`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: add assessments and grades screens"
```

### Task 9: Deliver attendance, reports, and student academic panels

**Files:**
- Create: `frontend/src/features/frequencia/FrequenciaPage.tsx`, `ChamadaForm.tsx`, `RelatorioFrequencia.tsx`, `api.ts`
- Create: `frontend/src/features/alunos/BoletimPanel.tsx`, `FrequenciaAlunoPanel.tsx`
- Create: `frontend/src/features/frequencia/__tests__/ChamadaForm.test.tsx`, `frontend/src/features/alunos/__tests__/AlunoAcademicPanels.test.tsx`
- Modify: `frontend/src/features/alunos/AlunoDetailPage.tsx`, `frontend/src/app/router.tsx`, `frontend/src/app/Sidebar.tsx`

**Interfaces:**
- Consumes: shared API client, student profile route and enrollment types.
- Produces: `/frequencia` and complete academic panels on `/alunos/:id`.

- [ ] **Step 1: Write failing attendance/report tests**

```tsx
it('submits a complete chamada payload to POST /api/frequencias/chamadas');
it('maps registros[2].status validation to the corresponding attendance row');
it('requests the class attendance report with selected date range');
it('renders boletim and annual frequency selected by year on the student profile');
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npm test -- --run src/features/frequencia src/features/alunos/__tests__`

Expected: FAIL because attendance/report components do not exist.

- [ ] **Step 3: Implement calls, reports, and profile panels**

Support create, browse and edit of a call; per-student presence status and observation; report filters for class, optional discipline and period; annual frequency and report-card panels on student detail. Render low-frequency and academic situations with semantic text badges, not color alone.

- [ ] **Step 4: Run feature checks**

Run: `cd frontend && npm test -- --run src/features/frequencia src/features/alunos/__tests__ && npm run build`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend
git commit -m "feat: add attendance and academic report screens"
```

### Task 10: Containerize, expose in Kubernetes, and document local startup

**Files:**
- Create: `api-gateway/Dockerfile`, `api-gateway/.dockerignore`
- Create: `frontend/Dockerfile`, `frontend/nginx.conf`, `frontend/.dockerignore`
- Create: `k8s/base/api-gateway/deployment.yaml`, `service.yaml`, `hpa.yaml`
- Modify: `docker-compose.yml`, `k8s/base/kustomization.yaml`, `k8s/base/ingress.yaml`, `k8s/overlays/minikube/kustomization.yaml`, `k8s/README.md`
- Create: `README.md`

**Interfaces:**
- Consumes: port and environment contracts from Tasks 1–3.
- Produces: local URLs `http://localhost:5173` (dev frontend) and `http://localhost:8080` (gateway); Kubernetes ingress sends all API and Swagger paths to the gateway.

- [ ] **Step 1: Write failing deployment/configuration checks**

```sh
docker compose config --quiet
kubectl kustomize k8s/overlays/minikube | rg 'api-gateway|gestao-escolar-service|gestao-administrativa-service'
```

Expected before implementation: configuration is missing an `api-gateway` service/deployment and ingress still exposes business services directly.

- [ ] **Step 2: Implement local and Kubernetes topology**

Add the gateway service to Docker Compose with target URLs using Docker service names; keep databases and Kafka intact. Add a non-root gateway deployment with actuator probes, a service, an HPA and config map values for internal service DNS. Replace direct ingress API/Swagger backends with the gateway only. Add a production frontend image served by nginx that forwards `/api`, `/escolar` and `/administrativa` to the gateway service; do not expose actuator through ingress.

- [ ] **Step 3: Validate manifests and build artifacts**

Run: `docker compose config --quiet && kubectl kustomize k8s/overlays/minikube >/tmp/distrischool-manifests.yaml && cd api-gateway && ./mvnw test && cd ../frontend && npm run build`

Expected: exit code 0; rendered manifests reference all three applications and the ingress references only `api-gateway` for public API paths.

- [ ] **Step 4: Perform local smoke test with running infrastructure**

Run: `docker compose up -d --build && curl --fail http://localhost:8080/actuator/health && curl --fail http://localhost:8080/api/alunos && curl --fail http://localhost:5173/login`

Expected: gateway health returns `UP`, a routed API receives the school-service response, and the login document is served.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml README.md api-gateway frontend k8s
git commit -m "feat: deploy frontend through API gateway"
```

## Final Verification

- [ ] Run `cd api-gateway && ./mvnw test`.
- [ ] Run `cd frontend && npm run lint && npm test -- --run && npm run build`.
- [ ] Run `docker compose config --quiet` and `kubectl kustomize k8s/overlays/minikube`.
- [ ] With services running, exercise login, one administrative list, one school list and a nested student report-card route through `http://localhost:8080`; verify no browser request targets `8081` or `8082`.
- [ ] Review `git diff --check` and `git status --short` before the final handoff.
