package totah.lab.web.service;

import totah.lab.web.persistence.SchemaRemappingPhysicalNamingStrategy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Shared machinery for DB-backed integration tests: a throwaway
 * {@code docking_test} schema cloned from the live DDL.
 *
 * Each concrete subclass must call {@link #recreateTestSchema()} from a
 * static initializer so the schema exists before the Spring context
 * boots (ddl-auto=validate). The first call in the JVM drops and
 * recreates the schema; later calls only re-apply the schema-remap
 * system property.
 *
 * The schema is deliberately NOT dropped between test classes: dropping
 * it invalidates the pocket_source enum's type OID, and a later test
 * class reusing a cached Spring context (same configuration) would see
 * "cache lookup failed for type" on its pooled connections. Instead the
 * schema is recreated from scratch at the start of every JVM test run,
 * and every test class truncates all tables after each test. The empty
 * schema remains in the dev database after the run.
 */
public abstract class DockingTestSchemaSupport {

    protected static final String TEST_SCHEMA = "docking_test";

    private static boolean recreatedInThisJvm;

    protected static synchronized void recreateTestSchema() {
        System.setProperty(
                SchemaRemappingPhysicalNamingStrategy
                        .PUBLIC_SCHEMA_PROPERTY,
                TEST_SCHEMA
        );

        if (recreatedInThisJvm) {
            return;
        }
        recreatedInThisJvm = true;

        String ddl;
        try (InputStream in = DockingTestSchemaSupport.class
                .getResourceAsStream("/docking_test_schema.sql")) {
            if (in == null) {
                throw new IllegalStateException(
                        "docking_test_schema.sql not on the classpath"
                );
            }
            ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }

        try (InputStream in = DockingTestSchemaSupport.class
                .getResourceAsStream("/experimental-assembly.sql")) {
            if (in == null) {
                throw new IllegalStateException(
                        "experimental-assembly.sql not on the classpath");
            }
            String assemblyDdl = new String(in.readAllBytes(),
                    StandardCharsets.UTF_8)
                    .replace("docking.", TEST_SCHEMA + ".")
                    .replace("public.targets", TEST_SCHEMA + ".targets");
            ddl = ddl + System.lineSeparator() + assemblyDdl;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }

        try (InputStream in = DockingTestSchemaSupport.class
                .getResourceAsStream("/component-pocket-annotation.sql")) {
            if (in == null) {
                throw new IllegalStateException(
                        "component-pocket-annotation.sql not on classpath");
            }
            String annotationDdl = new String(in.readAllBytes(),
                    StandardCharsets.UTF_8)
                    .replace("docking.", TEST_SCHEMA + ".");
            ddl = ddl + System.lineSeparator() + annotationDdl;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }

        try (Connection connection = testConnection();
             Statement statement = connection.createStatement()) {
            for (String command : ddl.split(";")) {
                if (!command.isBlank()) {
                    statement.execute(command);
                }
            }
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    protected static Connection testConnection() throws SQLException {
        String url = System.getenv().getOrDefault(
                "DB_URL",
                "jdbc:postgresql://localhost:5432/totah_lab_db"
        );
        String user = System.getenv().getOrDefault(
                "DB_USERNAME",
                "postgres"
        );
        String password = System.getenv().getOrDefault(
                "DB_PASSWORD",
                "admin"
        );
        return DriverManager.getConnection(url, user, password);
    }
}
