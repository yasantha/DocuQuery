package com.docuquery.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for integration tests: a real pgvector Postgres container wired
 * into Spring via {@link ServiceConnection}. The {@code vector} extension is
 * created by Flyway migration V1.
 */
@Testcontainers
public abstract class PgVectorContainerSupport {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("docuquery")
            .withUsername("docuquery")
            .withPassword("secret");
}
