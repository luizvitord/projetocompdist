# Executar o frontend e o API Gateway

Este documento explica como executar o portal interno da coordenação e como verificar a comunicação com os microsserviços.

## Arquitetura local

```text
Frontend React (5173)
        |
        v
API Gateway (8080)
        |
        +--> gestao-administrativa-service (8081)
        |
        +--> gestao-escolar-service (8082)
```

O navegador deve acessar somente o API Gateway. As portas `8081` e `8082` são usadas pelos serviços internos e não devem ser configuradas diretamente no frontend.

## Pré-requisitos

- Docker e Docker Compose;
- Node.js e npm, para executar o frontend em modo desenvolvimento;
- Java 21 e Maven Wrapper, caso o gateway seja executado fora do Docker;
- `kubectl` e Minikube somente para o cenário Kubernetes.

## Opção 1: executar tudo com Docker Compose

Essa é a opção mais simples para subir toda a aplicação em containers:

```bash
cd /home/janylson/Documentos/GITHUB/projetocompdist
docker compose up --build
```

Após a inicialização:

- Frontend: <http://localhost:5173>;
- API Gateway: <http://localhost:8080>;
- Health check: <http://localhost:8080/actuator/health>.

Para parar os containers:

```bash
docker compose down
```

Não use `docker compose down -v` sem necessidade, pois a opção `-v` remove os volumes dos bancos locais.

## Opção 2: frontend em modo desenvolvimento

Use esta opção quando estiver trabalhando nas telas React. Primeiro, deixe a infraestrutura, os microsserviços e o gateway disponíveis em `localhost:8080`.

Em um terminal, execute:

```bash
cd /home/janylson/Documentos/GITHUB/projetocompdist
docker compose up -d postgres-administrativa postgres-escolar kafka
```

Em terminais separados, execute os serviços backend conforme necessário. O API Gateway pode ser executado com:

```bash
cd /home/janylson/Documentos/GITHUB/projetocompdist/api-gateway
./mvnw spring-boot:run
```

Se o serviço administrativo estiver usando a porta `8083` em vez de `8081`, inicie o gateway assim:

```bash
ADMINISTRATIVA_URL=http://localhost:8083 ./mvnw spring-boot:run
```

Por fim, execute o frontend:

```bash
cd /home/janylson/Documentos/GITHUB/projetocompdist/frontend
npm install
npm run dev
```

O Vite executa em <http://localhost:5173> e encaminha `/api`, `/escolar` e `/administrativa` para <http://localhost:8080>.

## Opção 3: frontend local conectado aos pods Kubernetes

Esta opção executa o backend no Minikube e o frontend diretamente pelo Vite. Não é necessário subir o Docker Compose ao mesmo tempo.

### 1. Iniciar o cluster

```bash
minikube start --driver=docker
```

### 2. Verificar o cluster

```bash
kubectl get nodes
kubectl get pods -n distrischool
```

O nó deve estar como `Ready` e os pods dos serviços devem estar `Running`.

### 3. Aplicar ou atualizar os manifests

```bash
cd /home/janylson/Documentos/GITHUB/projetocompdist
kubectl apply -k k8s/overlays/minikube
```

### 4. Encaminhar o gateway para a máquina local

Em um terminal que ficará aberto:

```bash
kubectl port-forward -n distrischool svc/api-gateway 8080:8080
```

### 5. Iniciar o frontend

Em outro terminal:

```bash
cd /home/janylson/Documentos/GITHUB/projetocompdist/frontend
npm run dev
```

Acesse <http://localhost:5173>. O frontend usará o port-forward e a comunicação seguirá este caminho:

```text
http://localhost:5173/api
        -> http://localhost:8080
        -> pod api-gateway
        -> pods dos microsserviços
```

## Login demonstrativo

O login atual é apenas visual e não usa o serviço de autenticação:

```text
E-mail: coordenacao@distrischool.com
Senha: 123456
```

A sessão é armazenada somente no `sessionStorage` do navegador. Essas credenciais não devem ser usadas como mecanismo de segurança em produção.

## Verificações rápidas

Health check do gateway:

```bash
curl http://localhost:8080/actuator/health
```

Listagem escolar passando pelo gateway:

```bash
curl http://localhost:8080/api/alunos
```

Testes do frontend:

```bash
cd frontend
npm test -- --run
npm run lint
npm run build
```

Testes do gateway:

```bash
cd api-gateway
MAVEN_USER_HOME=/tmp/projetocompdist-m2 \
  ./mvnw -Dmaven.repo.local=/tmp/projetocompdist-m2/repository test
```

## Cuidados com memória e armazenamento

Não execute Docker Compose e Minikube simultaneamente em máquinas com pouca RAM. Escolha apenas uma das opções de backend.

Para liberar a memória do Minikube:

```bash
minikube stop
```

Para verificar espaço em disco:

```bash
df -h /
docker system df
```

Limpeza segura de cache Docker, sem remover volumes dos bancos:

```bash
docker builder prune
docker image prune
```

## Observações sobre Kubernetes

Os manifests Kubernetes atuais implantam os dois microsserviços, bancos, Kafka e o API Gateway. O frontend ainda é executado pelo Vite ou pelo Docker Compose; não existe um Deployment Kubernetes para o frontend nesta etapa.

O HPA está configurado, mas precisa do `metrics-server` habilitado para coletar métricas de CPU:

```bash
minikube addons enable metrics-server
```
