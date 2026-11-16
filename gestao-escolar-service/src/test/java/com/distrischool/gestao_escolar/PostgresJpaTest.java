package com.distrischool.gestao_escolar;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base dos testes de Specification: repositórios JPA contra um PostgreSQL 17 real (Testcontainers),
 * com o schema criado pelas migrations do Flyway. Necessário porque a busca usa a função
 * {@code unaccent}, que só existe no PostgreSQL.
 * <p>
 * Um único container para todas as classes (iniciado uma vez e encerrado pelo Testcontainers no fim
 * da JVM), para que o contexto do Spring em cache continue apontando para um banco vivo. Sem Docker,
 * as classes são puladas e os demais testes continuam valendo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresJpaTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }
}
