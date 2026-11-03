# DistriSchool

Plataforma de gestão escolar com três microsserviços (gestão administrativa, gestão escolar e usuários) e um API Gateway.

## Executar localmente

Para a pilha completa em containers: `docker compose up --build`. O portal fica em `http://localhost:5173`, o gateway em `http://localhost:8080` e o health check em `/actuator/health`.

Para desenvolvimento do frontend: inicie a infraestrutura e os serviços, depois execute `cd frontend && npm run dev`. O Vite encaminha `/api` exclusivamente para o gateway.

Login: `coordenacao@distrischool.com` / `123456` (administrador criado pela migration do `usuarios-service`). O login devolve um JWT que o API Gateway exige em todas as rotas `/api`; perfis e permissões estão em [docs/AUTENTICACAO.md](docs/AUTENTICACAO.md).

## Kubernetes

Use `kubectl apply -k k8s/overlays/minikube`. O Ingress expõe somente o API Gateway para `/api`, `/administrativa`, `/escolar` e `/usuarios`; os serviços de negócio permanecem internos.
