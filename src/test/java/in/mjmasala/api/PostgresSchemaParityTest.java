package in.mjmasala.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MySQL migration must match what Hibernate expects for MySQL.
 *
 * This is the check that matters most for the PostgreSQL deployment, because the application
 * starts with {@code ddl-auto=validate}: if the migration and the entity model disagree
 * about a column type, the app refuses to boot. It caught 11 enum columns that had been
 * written as {@code varchar} where MySQLDialect wants a native {@code enum}.
 *
 * Hibernate is asked to emit its own PostgreSQL schema to a script, and that script is compared
 * against {@code V1__schema.sql}. No PostgreSQL server is needed.
 */
@SpringBootTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "app.seed.enabled=false",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=target/postgres-hibernate.sql",
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=none"
})
@ActiveProfiles("test")
class PostgresSchemaParityTest {

    private static final Path GENERATED = Path.of("target/postgres-hibernate.sql");
    /*
     * Hibernate appends to the script target rather than replacing it, so a second run
     * would otherwise compare against a file holding several generations of the schema.
     */
    static {
        try {
            java.nio.file.Files.deleteIfExists(Path.of("target/postgres-hibernate.sql"));
        } catch (java.io.IOException ignored) {
            // nothing to clear
        }
    }

    /*
     * Hibernate appends to the script target rather than replacing it, so a second run
     * would otherwise compare against a file holding several generations of the schema.
     */
    static {
        try {
            java.nio.file.Files.deleteIfExists(Path.of("target/postgres-hibernate.sql"));
        } catch (java.io.IOException ignored) {
            // nothing to clear
        }
    }

    /*
     * Hibernate appends to the script target rather than replacing it, so a second run
     * would otherwise compare against a file holding several generations of the schema.
     */
    static {
        try {
            java.nio.file.Files.deleteIfExists(Path.of("target/postgres-hibernate.sql"));
        } catch (java.io.IOException ignored) {
            // nothing to clear
        }
    }

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/postgresql/V1__schema.sql");

    @Test
    @DisplayName("the PostgreSQL migration matches Hibernate's PostgreSQL schema column for column")
    void migrationMatchesTheEntityModel() throws Exception {
        var generated = SchemaParity.parseGenerated(GENERATED);
        var migration = SchemaParity.parseMigration(MIGRATION);

        assertThat(generated).as("Hibernate should have emitted tables").isNotEmpty();
        assertThat(generated.keySet())
                .as("every entity should be in the migration")
                .containsExactlyInAnyOrderElementsOf(migration.keySet());

        List<String> problems = SchemaParity.differences(generated, migration);
        assertThat(problems)
                .as("the PostgreSQL migration has drifted from the entity model — fix V1__schema.sql:\n  %s",
                        String.join("\n  ", problems))
                .isEmpty();
    }
}
