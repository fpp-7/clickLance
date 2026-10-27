package br.com.clicklance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in verification against a disposable PostgreSQL instance, never the production datasource. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "CLICKLANCE_POSTGRES_TEST_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:.*")
class PostgresMigrationTest {
    private final String schema = "clicklance_test_" + UUID.randomUUID().toString().replace("-", "");
    private final UUID empresa = UUID.randomUUID();
    private final UUID quadra = UUID.randomUUID();
    private final UUID otherQuadra = UUID.randomUUID();
    private final UUID event = UUID.randomUUID();
    private Flyway flyway;

    private String url() { return System.getenv("CLICKLANCE_POSTGRES_TEST_URL"); }
    private String user() { return System.getenv().getOrDefault("CLICKLANCE_POSTGRES_TEST_USER", "clicklance_test"); }
    private String password() { return System.getenv().getOrDefault("CLICKLANCE_POSTGRES_TEST_PASSWORD", ""); }

    private Connection connection() throws SQLException {
        Connection c = DriverManager.getConnection(url(), user(), password());
        c.setSchema(schema);
        return c;
    }

    @BeforeAll void migrateDisposableSchema() {
        flyway = Flyway.configure().dataSource(url(), user(), password())
                .schemas(schema).defaultSchema(schema).createSchemas(true)
                .locations("classpath:db/migration").load();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }

    @AfterAll void removeOnlyThisTestsSchema() throws SQLException {
        assertTrue(schema.matches("clicklance_test_[a-f0-9]{32}"));
        try (Connection c = connection(); var statement = c.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @BeforeEach void fixtures() throws SQLException {
        try (Connection c = connection(); var statement = c.createStatement()) {
            statement.execute("TRUNCATE usuario_lances,lances,dispositivos,quadras,empresas,usuarios CASCADE");
            try (PreparedStatement p = c.prepareStatement("INSERT INTO empresas(id,nome,criado_em) VALUES(?, 'Teste PostgreSQL',CURRENT_TIMESTAMP)")) {
                p.setObject(1, empresa); p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO quadras(id,empresa_id,nome,criado_em) VALUES(?,?,'Quadra',CURRENT_TIMESTAMP)")) {
                for (UUID q : new UUID[]{quadra, otherQuadra}) {
                    p.setObject(1,q); p.setObject(2,empresa); p.executeUpdate();
                }
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO dispositivos(id,quadra_id,token_acesso_hash,criado_em) VALUES('disp_test',?,'hash',CURRENT_TIMESTAMP)")) {
                p.setObject(1,quadra); p.executeUpdate();
            }
        }
    }

    private UUID insertLance(Connection c, UUID q, UUID eventId, String key, OffsetDateTime timestamp) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement p = c.prepareStatement("INSERT INTO lances(id,quadra_id,dispositivo_id,evento_id,timestamp_evento,s3_key,tamanho_bytes,expira_em,criado_em) VALUES(?,?,'disp_test',?,?,?,32,?,CURRENT_TIMESTAMP)")) {
            p.setObject(1,id); p.setObject(2,q); p.setObject(3,eventId); p.setObject(4,timestamp);
            p.setString(5,key); p.setObject(6,timestamp.plusDays(2)); p.executeUpdate();
        }
        return id;
    }

    @Test void migrationEnforcesDeviceOriginAndEventUniqueness() throws SQLException {
        try (Connection c = connection()) {
            OffsetDateTime timestamp = OffsetDateTime.parse("2026-10-26T22:14:50-03:00");
            SQLException wrongOrigin = assertThrows(SQLException.class,
                    () -> insertLance(c,otherQuadra,event,"replays/wrong.mp4",timestamp));
            assertEquals("23503",wrongOrigin.getSQLState());
            insertLance(c,quadra,event,"replays/first.mp4",timestamp);
            SQLException duplicate = assertThrows(SQLException.class,
                    () -> insertLance(c,quadra,event,"replays/second.mp4",timestamp));
            assertEquals("23505",duplicate.getSQLState());
            try (var statement = c.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM lances")) {
                assertTrue(result.next()); assertEquals(1,result.getInt(1));
            }
        }
    }

    @Test void timestampWithOffsetRoundTripsAsTheSameInstant() throws SQLException {
        try (Connection c = connection()) {
            OffsetDateTime timestamp = OffsetDateTime.parse("2026-10-26T23:59:59.123456-03:00");
            UUID id = insertLance(c,quadra,event,"replays/time.mp4",timestamp);
            try (PreparedStatement p = c.prepareStatement("SELECT timestamp_evento FROM lances WHERE id=?")) {
                p.setObject(1,id);
                try (var result = p.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(timestamp.toInstant(),result.getObject(1,OffsetDateTime.class).toInstant());
                }
            }
        }
    }

    @Test void rowLocksSerializeTransactionsAndReleaseAfterRollback() throws SQLException {
        try (Connection first = connection(); Connection second = connection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            try (var statement = first.createStatement()) {
                statement.executeQuery("SELECT id FROM dispositivos WHERE id='disp_test' FOR UPDATE").close();
            }
            try (var statement = second.createStatement()) {
                statement.execute("SET LOCAL lock_timeout = '150ms'");
                SQLException locked = assertThrows(SQLException.class,
                        () -> statement.executeQuery("SELECT id FROM dispositivos WHERE id='disp_test' FOR UPDATE"));
                assertEquals("55P03",locked.getSQLState());
            }
            second.rollback();
            first.rollback();
            try (var statement = second.createStatement(); var result = statement.executeQuery(
                    "SELECT id FROM dispositivos WHERE id='disp_test' FOR UPDATE")) {
                assertTrue(result.next()); assertEquals("disp_test",result.getString(1));
            }
            second.rollback();
        }
    }
}
