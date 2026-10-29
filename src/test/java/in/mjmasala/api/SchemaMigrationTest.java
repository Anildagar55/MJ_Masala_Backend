package in.mjmasala.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the hand-written migration matches the entities.
 *
 * This is the test that matters most for the SQL files: it turns Flyway on, points it at
 * the PostgreSQL migration, and leaves Hibernate on {@code ddl-auto=validate}. If a column
 * name, type, precision or scale in the migration drifts from what the entity declares,
 * the context fails to start and this test fails with it. Without this, a typo in the
 * schema would only surface on the first real deployment.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/postgresql",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
class SchemaMigrationTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "app_user", "address", "category", "product", "product_pack", "product_spec",
            "product_badge", "combo", "combo_item", "combo_spec", "combo_badge",
            "review", "review_helpful", "coupon", "cart_item", "wishlist_item",
            "orders", "order_item", "enquiry");

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("Flyway applies the schema and Hibernate validates every entity against it")
    void migrationsApplyAndEntitiesMatch() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            List<String> tables = new ArrayList<>();
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT));
                }
            }

            for (String expected : EXPECTED_TABLES) {
                assertThat(tables)
                        .as("table %s should exist after the migration", expected)
                        .contains(expected);
            }

            // Flyway records what it applied, so a second run is a no-op
            assertThat(tables).contains("flyway_schema_history");
        }
    }

    @Test
    @DisplayName("the coupon value column is coupon_value, not the reserved word")
    void reservedWordsAvoided() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            // H2 runs with DATABASE_TO_LOWER here, so match the table name case-insensitively
            List<String> columns = new ArrayList<>();
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, null, null)) {
                while (rs.next()) {
                    if ("coupon".equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        columns.add(rs.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
            assertThat(columns).isNotEmpty();
            assertThat(columns).contains("coupon_value").doesNotContain("value");
        }
    }
}
