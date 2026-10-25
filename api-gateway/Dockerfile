FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -q -DskipTests package
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 gateway
USER 10001
COPY --from=build /workspace/target/api-gateway-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
