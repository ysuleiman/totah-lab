package totah.lab.docking.importer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

public final class ChemflowImportCommand {

    private ChemflowImportCommand() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1 || !"--apply".equals(arguments[0])) {
            throw new IllegalArgumentException(
                    "Explicit --apply argument is required"
            );
        }

        String user = environment("PGUSER", "postgres");
        String password = environment("PGPASSWORD", "admin");
        String sourceUrl = environment(
                "CHEMFLOW_DB_URL",
                "jdbc:postgresql://localhost:5432/chemflow3"
        );
        String destinationUrl = environment(
                "TOTAH_DB_URL",
                "jdbc:postgresql://localhost:5432/totah_lab_db"
        );
        Path artifactRoot = Path.of(environment(
                "CHEMFLOW_ARTIFACT_ROOT",
                "/Users/yazan/projects/chemflow/backend/artifact-storage"
        ));

        try (Connection source = DriverManager.getConnection(
                     sourceUrl,
                     user,
                     password
             );
             Connection destination = DriverManager.getConnection(
                     destinationUrl,
                     user,
                     password
             )) {
            ChemflowImportResult result =
                    new ChemflowDockingImporter(artifactRoot)
                            .importData(source, destination);
            System.out.printf(
                    "Imported runs=%d poses=%d contacts=%d rejectedPoses=%d%n",
                    result.runs(),
                    result.poses(),
                    result.contacts(),
                    result.rejectedPoses()
            );
        }
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
