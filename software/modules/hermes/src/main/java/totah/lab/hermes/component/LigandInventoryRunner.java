package totah.lab.hermes.component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Thin command-line adapter over {@link ComponentInventoryService}. */
public final class LigandInventoryRunner {

    private LigandInventoryRunner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: LigandInventoryRunner <structures-dir> <output-dir> [--dry-run]");
        }
        boolean dryRun = args.length == 3 && "--dry-run".equals(args[2]);
        if (args.length == 3 && !dryRun) {
            throw new IllegalArgumentException("Unknown option: " + args[2]);
        }
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        ComponentInventoryResult result = new DefaultComponentInventoryService().build(
                new ComponentInventoryRequest(Path.of(args[0]), output, true, dryRun));
        Path ligandDirectory = output.resolve("ligands");
        Files.createDirectories(ligandDirectory);
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                ligandDirectory.resolve("inventory.json").toFile(), result.inventory());
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                ligandDirectory.resolve("summary.json").toFile(), result.summary());
        String summary = ComponentInventoryTextReport.format(result.summary());
        Files.writeString(ligandDirectory.resolve("summary.txt"), summary);
        System.out.print(summary);
    }
}
