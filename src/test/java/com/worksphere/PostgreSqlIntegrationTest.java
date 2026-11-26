package com.worksphere;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class PostgreSqlIntegrationTest {

    protected static final String TEST_JWT_SECRET = "test-only-jwt-secret-with-at-least-thirty-two-bytes";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt.secret", () -> TEST_JWT_SECRET);
    }

    /** Empties every application table (child rows included) so each test starts clean. */
    @BeforeEach
    void clearDatabase() {
        String tables = String.join(", ", jdbcTemplate.queryForList(
                "SELECT quote_ident(tablename) FROM pg_tables "
                        + "WHERE schemaname = current_schema() AND tablename <> 'flyway_schema_history'",
                String.class));
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + tables + " CASCADE");
        }
    }
}
