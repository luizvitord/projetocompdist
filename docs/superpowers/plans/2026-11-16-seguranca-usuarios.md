# Segurança do cadastro de usuários Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Garantir que perfis e vínculos sejam válidos, que administradores redefinam senhas e que sessões antigas percam acesso após uma mudança sensível.

**Architecture:** O usuários-service passa a ser a fonte de verdade da versão e atividade da sessão. Gateway e notificações validam o JWT assinado e consultam o estado interno do usuário antes de aceitar uma requisição ou nova conexão STOMP. A tela administrativa usa o novo endpoint de senha e evita ações inseguras sobre a própria conta.

**Tech Stack:** Spring Boot 4, Spring Security Resource Server, JPA/Flyway, React, TypeScript, Vitest/MSW.

**Spec:** `docs/superpowers/specs/2026-11-16-seguranca-usuarios-design.md`

## Global Constraints

- Não alterar a pesquisa textual já entregue em outro commit.
- Não adicionar dependências; reutilizar Spring Web e Spring Security existentes.
- Não enviar alterações remotas.

## Review Focus

- `ADMIN` com vínculo deve receber 409; os demais perfis sem vínculo ou com zero também.
- O JWT anterior deve falhar depois de troca de perfil, vínculo, senha ou desativação.
- Falha de comunicação com o usuários-service deve negar, e não liberar, uma sessão.
- Não pode ser possível remover o último ADMIN ativo.
- A alteração de senha deve seguir o mesmo contrato de tamanho do cadastro e não expor hashes.

---

### Task 1: Estado e regras no usuários-service

**Files:**
- Modify: `usuarios-service/src/main/java/com/distrischool/usuarios/usuario/Usuario.java`
- Modify: `usuarios-service/src/main/java/com/distrischool/usuarios/usuario/UsuarioService.java`
- Modify: `usuarios-service/src/main/java/com/distrischool/usuarios/usuario/UsuarioRepository.java`
- Modify: `usuarios-service/src/main/java/com/distrischool/usuarios/usuario/UsuarioController.java`
- Create: `usuarios-service/src/main/java/com/distrischool/usuarios/usuario/AlteracaoSenhaRequest.java`
- Create: `usuarios-service/src/main/java/com/distrischool/usuarios/usuario/EstadoSessaoResponse.java`
- Create: `usuarios-service/src/main/resources/db/migration/V2__versao_sessao_usuario.sql`
- Test: `usuarios-service/src/test/java/com/distrischool/usuarios/usuario/UsuarioServiceTest.java`
- Test: `usuarios-service/src/test/java/com/distrischool/usuarios/usuario/UsuarioControllerTest.java`

**Interfaces:**
- Produces `PATCH /api/usuarios/{id}/senha` and `GET /interno/sessoes/{id}`.
- `EstadoSessaoResponse` exposes `ativo` and `versaoSessao`.

- [ ] Write failing service/controller tests for invalid profile links, password reset, last-admin protection and the internal session state.
- [ ] Run the focused tests and confirm they fail because the APIs and rules are absent.
- [ ] Implement the entity/migration/version changes, the endpoint and the server-side rules.
- [ ] Run focused and module tests, then commit the backend change.

### Task 2: Validate session state at the gateway and notification service

**Files:**
- Create: `api-gateway/src/main/java/com/distrischool/api_gateway/config/ValidadorSessaoUsuario.java`
- Create: `api-gateway/src/main/java/com/distrischool/api_gateway/config/FiltroSessaoAtiva.java`
- Modify: `api-gateway/src/main/java/com/distrischool/api_gateway/config/SecurityConfig.java`
- Modify: `notificacao-service/src/main/java/com/distrischool/notificacao/seguranca/SecurityConfig.java`
- Modify: `notificacao-service/src/main/java/com/distrischool/notificacao/temporeal/StompAutenticacaoInterceptor.java`
- Test: `api-gateway/src/test/java/com/distrischool/api_gateway/config/SecurityConfigTest.java`
- Test: `notificacao-service/src/test/java/com/distrischool/notificacao/temporeal/StompAutenticacaoInterceptorTest.java`

**Interfaces:**
- Consumes `GET /interno/sessoes/{id}` and JWT claim `sessao`.
- Produces 401 whenever the user is inactive or the claim version differs from the current state.

- [ ] Write failing tests that reject a stale session version and an inactive user.
- [ ] Run them and verify the unprotected token is accepted before implementation.
- [ ] Implement the HTTP client abstraction, the gateway filter and the equivalent notification/STOMP validation.
- [ ] Run gateway and notification test modules, then commit the security integration.

### Task 3: Password flow and account-safe actions in the frontend

**Files:**
- Modify: `frontend/src/features/usuarios/UsuariosPage.tsx`
- Modify: `frontend/src/features/usuarios/__tests__/UsuariosPage.test.tsx`

**Interfaces:**
- Consumes `PATCH /api/usuarios/{id}/senha` with `{ senha: string }`.

- [ ] Write a failing UI test that submits a password reset and one that hides self-deactivation.
- [ ] Run the focused frontend test and confirm the missing behavior.
- [ ] Add the password reset control and block the self-deactivation action in the management page.
- [ ] Run the affected and full frontend checks, then commit the UI change.

### Task 4: Cross-service verification

**Files:**
- Modify: no production files expected.

- [ ] Run all affected Maven modules and the full frontend test/lint/build commands.
- [ ] Inspect the final diff to confirm no search-related files changed.
- [ ] Record verification results and create the final commit if a verification-only correction is needed.
