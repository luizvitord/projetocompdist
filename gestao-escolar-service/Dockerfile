# Imagem do gestao-escolar-service (build multi-stage)

# Etapa 1: compila o jar com Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Baixa as dependencias antes de copiar o codigo, para aproveitar o cache de camadas
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src/ src/
# Os testes rodam fora do build da imagem (./mvnw test / CI)
RUN mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# Etapa 2: imagem de execucao, somente com o JRE
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system --gid 1001 spring && useradd --system --uid 1001 --gid spring spring
# UID numerico: o Kubernetes so consegue validar runAsNonRoot com um usuario numerico
USER 1001

# Camadas da menos para a mais volatil: dependencias mudam pouco, o codigo muda sempre
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

EXPOSE 8082
# Ajusta o heap ao limite de memoria do container
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
