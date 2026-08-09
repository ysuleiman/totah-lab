package totah.lab.web.docking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import totah.lab.daedalus.docking.DockingInput;
import totah.lab.daedalus.docking.VinaDockingOptions;
import totah.lab.daedalus.docking.VinaDockingResult;
import totah.lab.daedalus.docking.VinaDockingRunner;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hephaestus.client.HephaestusClients;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.web.docking.PoseContactCalculator.PocketAtomPoint;
import totah.lab.web.docking.PoseContactCalculator.ResidueContact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs one vina docking and persists the run, its poses and the
 * residue-level contacts into the existing docking tables.
 *
 * <p>Gated one-shot runner, launched with
 * {@code --spring.main.web-application-type=none
 * --totah.docking.enabled=true} plus the {@code totah.docking.*}
 * properties below. Dry run (default) executes vina and computes
 * everything but performs no database writes. A run is an event: every
 * invocation inserts a new docking_run row; there is no dedupe. All
 * writes happen in a single transaction after vina and parsing
 * succeeded, so relaunching after a failure is always safe.</p>
 */
@Component
@ConditionalOnProperty(
        name = "totah.docking.enabled",
        havingValue = "true"
)
public class DockingPersistenceRunner implements CommandLineRunner {

    private static final Logger LOG =
            LoggerFactory.getLogger(DockingPersistenceRunner.class);

    private static final DateTimeFormatter RUN_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final String SOURCE_SYSTEM = "web-api";

    private final DockingPersistenceService persistenceService;
    private final String receptorPdbqt;
    private final String ligand;
    private final Long structureId;
    private final Long pocketId;
    private final String box;
    private final double padding;
    private final String vinaExecutable;
    private final String runsDirectory;
    private final int exhaustiveness;
    private final Integer seed;
    private final String ligandId;
    private final String ligandLabel;
    private final boolean dryRun;

    public DockingPersistenceRunner(
            DockingPersistenceService persistenceService,
            @Value("${totah.docking.receptor-pdbqt:}")
            String receptorPdbqt,
            @Value("${totah.docking.ligand:}")
            String ligand,
            @Value("${totah.docking.structure-id:}")
            String structureId,
            @Value("${totah.docking.pocket-id:}")
            String pocketId,
            @Value("${totah.docking.box:}")
            String box,
            @Value("${totah.docking.padding:8}")
            double padding,
            @Value("${totah.docking.vina:}")
            String vinaExecutable,
            @Value("${totah.docking.runs-dir:}")
            String runsDirectory,
            @Value("${totah.docking.exhaustiveness:8}")
            int exhaustiveness,
            @Value("${totah.docking.seed:}")
            String seed,
            @Value("${totah.docking.ligand-id:}")
            String ligandId,
            @Value("${totah.docking.ligand-label:}")
            String ligandLabel,
            @Value("${totah.docking.dry-run:true}")
            boolean dryRun
    ) {
        this.persistenceService =
                Objects.requireNonNull(persistenceService);
        this.receptorPdbqt = receptorPdbqt;
        this.ligand = ligand;
        this.structureId = parseLong("structure-id", structureId);
        this.pocketId = parseLong("pocket-id", pocketId);
        this.box = box;
        this.padding = padding;
        this.vinaExecutable = vinaExecutable;
        this.runsDirectory = runsDirectory;
        this.exhaustiveness = exhaustiveness;
        Long parsedSeed = parseLong("seed", seed);
        this.seed = parsedSeed == null ? null : parsedSeed.intValue();
        this.ligandId = ligandId == null || ligandId.isBlank()
                ? null
                : ligandId.trim();
        this.ligandLabel = ligandLabel == null || ligandLabel.isBlank()
                ? null
                : ligandLabel.trim();
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) throws Exception {
        Path receptor = requiredFile("receptor-pdbqt", receptorPdbqt);
        Path ligandInput = requiredFile("ligand", ligand);
        Path vina = requiredFile("vina", vinaExecutable);
        if (structureId == null) {
            throw new IllegalArgumentException(
                    "totah.docking.structure-id is required");
        }
        if (pocketId == null) {
            throw new IllegalArgumentException(
                    "totah.docking.pocket-id is required");
        }
        Path runsRoot = runsDirectory == null || runsDirectory.isBlank()
                ? null
                : Path.of(runsDirectory);
        if (runsRoot == null) {
            throw new IllegalArgumentException(
                    "totah.docking.runs-dir is required");
        }

        VinaDockingOptions options = box == null || box.isBlank()
                ? persistenceService.deriveBox(structureId, pocketId,
                        padding)
                : parseBox(box);
        if (exhaustiveness != 8 || seed != null) {
            options = new VinaDockingOptions(
                    options.centerX(), options.centerY(),
                    options.centerZ(), options.sizeX(), options.sizeY(),
                    options.sizeZ(), exhaustiveness, seed);
        }

        Path runDirectory = Files.createDirectories(runsRoot.resolve(
                "run-" + RUN_ID_FORMAT.format(
                        java.time.LocalDateTime.now())));
        Path stagedLigand = stageLigand(ligandInput, runDirectory);

        LOG.info("Docking run{}: structure={}, pocket={}, box center"
                        + " ({}, {}, {}) size ({}, {}, {}), ligand={}",
                dryRun ? " DRY RUN (no database writes)" : "",
                structureId, pocketId,
                options.centerX(), options.centerY(), options.centerZ(),
                options.sizeX(), options.sizeY(), options.sizeZ(),
                stagedLigand);

        VinaDockingResult result = new VinaDockingRunner(vina).run(
                new DockingInput(receptor, stagedLigand,
                        Optional.empty()),
                options);
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    "Vina exited with code " + result.exitCode()
                            + ": " + tail(result.output()));
        }
        if (result.poses().isEmpty()) {
            throw new IllegalStateException(
                    "Vina produced no poses: " + tail(result.output()));
        }

        Path poseFile = vinaOutputFile(stagedLigand);
        if (!Files.isRegularFile(poseFile)) {
            throw new IllegalStateException(
                    "Vina did not write the expected pose file: "
                            + poseFile);
        }

        List<List<String>> models = PdbqtModelSplitter.split(poseFile);
        if (models.size() != result.poses().size()) {
            throw new IllegalStateException("Vina wrote " + models.size()
                    + " models for " + result.poses().size()
                    + " pose table rows; refusing to guess the mapping");
        }

        List<PocketAtomPoint> pocketAtoms =
                persistenceService.pocketAtomPoints(pocketId);
        List<List<ResidueContact>> contactsPerPose = new ArrayList<>();
        for (int index = 0; index < models.size(); index++) {
            Path modelPath = runDirectory.resolve(
                    "pose-" + (index + 1) + ".pdbqt");
            Files.write(modelPath, models.get(index));
            List<double[]> poseAtoms = new PdbqtReader().read(modelPath)
                    .firstModel()
                    .atoms().stream()
                    .map(atom -> new double[]{
                            atom.x(),
                            atom.y(),
                            atom.z()})
                    .toList();
            contactsPerPose.add(
                    PoseContactCalculator.compute(poseAtoms,
                            pocketAtoms));
        }

        String resolvedLigandId = ligandId == null
                ? truncate(stripExtension(
                        ligandInput.getFileName().toString()), 32)
                : truncate(ligandId, 32);

        int totalContacts = contactsPerPose.stream()
                .mapToInt(List::size).sum();
        LOG.info("Run directory: {}; poses: {}; best affinity: {}"
                        + " kcal/mol; contact rows: {}",
                runDirectory, result.poses().size(),
                result.bestPose().orElseThrow().affinityKcalPerMol(),
                totalContacts);

        if (dryRun) {
            LOG.info("DRY RUN: nothing written; rerun with"
                    + " --totah.docking.dry-run=false to persist");
            return;
        }

        DockingPersistenceService.PersistResult persisted =
                persistenceService.persist(
                        new DockingPersistenceService.PersistRequest(
                                structureId,
                                pocketId,
                                options,
                                vinaVersion(result.output()),
                                SOURCE_SYSTEM,
                                resolvedLigandId,
                                ligandLabel,
                                poseFile,
                                result.poses(),
                                contactsPerPose));
        LOG.info("Persisted docking run id={} (artifact id={}, poses={},"
                        + " contact rows={})",
                persisted.runId(), persisted.artifactId(),
                persisted.poseCount(), persisted.contactCount());
    }

    /*
     * Vina has no --out here (daedalus VinaDockingRunner does not expose
     * it): it writes <stem>_out.pdbqt next to the ligand file, which is
     * why the ligand is staged inside the run directory first (verified
     * behavior of vina 1.2.5).
     */
    private Path stageLigand(Path ligandInput, Path runDirectory)
            throws IOException {
        String name = ligandInput.getFileName().toString();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".sdf")) {
            Path prepared = runDirectory.resolve(
                    stripExtension(name) + ".pdbqt");
            HephaestusClients.createDefault().prepareAndWriteLigand(
                    ligandInput, prepared,
                    LigandPreparationOptions.defaults());
            return prepared;
        }
        Path staged = runDirectory.resolve(name);
        Files.copy(ligandInput, staged,
                StandardCopyOption.REPLACE_EXISTING);
        return staged;
    }

    private static Path vinaOutputFile(Path stagedLigand) {
        return stagedLigand.resolveSibling(
                stripExtension(
                        stagedLigand.getFileName().toString())
                        + "_out.pdbqt");
    }

    private static String vinaVersion(String output) {
        for (String line : output.split("\\R")) {
            if (line.startsWith("AutoDock Vina")) {
                return truncate(line.trim(), 50);
            }
        }
        return null;
    }

    private static VinaDockingOptions parseBox(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 6) {
            throw new IllegalArgumentException(
                    "totah.docking.box needs six comma-separated numbers"
                            + " (cx,cy,cz,sx,sy,sz): " + value);
        }
        double[] numbers = new double[6];
        for (int index = 0; index < parts.length; index++) {
            try {
                numbers[index] = Double.parseDouble(parts[index].trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid number in totah.docking.box: "
                                + parts[index]);
            }
        }
        return VinaDockingOptions.ofBox(
                numbers[0], numbers[1], numbers[2],
                numbers[3], numbers[4], numbers[5]);
    }

    private static Path requiredFile(String property, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "totah.docking." + property + " is required");
        }
        Path path = Path.of(value);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("totah.docking."
                    + property + " is not a file: " + value);
        }
        return path.toAbsolutePath().normalize();
    }

    private static Long parseLong(String property, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "totah.docking." + property + " is not a number: "
                            + value);
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 1 ? filename : filename.substring(0, dot);
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum
                ? value
                : value.substring(0, maximum);
    }

    private static String tail(String output) {
        if (output == null || output.isBlank()) {
            return "(no output)";
        }
        String trimmed = output.strip();
        return trimmed.length() <= 300
                ? trimmed
                : trimmed.substring(trimmed.length() - 300);
    }
}
