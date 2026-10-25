# DistriSchool

Portal interno da coordenação escolar com dois microsserviços e um API Gateway.

## Executar localmente

Para a pilha completa em containers: `docker compose up --build`. O portal fica em `http://localhost:5173`, o gateway em `http://localhost:8080` e o health check em `/actuator/health`.

Para desenvolvimento do frontend: inicie a infraestrutura e os serviços, depois execute `cd frontend && npm run dev`. O Vite encaminha `/api` exclusivamente para o gateway.

Login demonstrativo: `coordenacao@distrischool.com` / `123456`. Ele usa apenas `sessionStorage` e será substituído pelo serviço de autenticação.

## Kubernetes

Use `kubectl apply -k k8s/overlays/minikube`. O Ingress expõe somente o API Gateway para `/api`, `/administrativa` e `/escolar`; os serviços de negócio permanecem internos.
