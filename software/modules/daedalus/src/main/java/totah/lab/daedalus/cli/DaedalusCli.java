package totah.lab.daedalus.cli;

import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.DockingProperties;
import totah.lab.daedalus.Pipeline;
import totah.lab.daedalus.PipelineContext;
import totah.lab.daedalus.PipelineFactory;
import totah.lab.daedalus.PipelineProperties;
import totah.lab.daedalus.docking.PocketGridBox;
import totah.lab.daedalus.docking.PocketGridBoxLoader;
import totah.lab.daedalus.docking.VinaDockingOptions;
import totah.lab.daedalus.docking.VinaDockingResult;
import totah.lab.daedalus.conformance.ligandprep.Ad4TypingDiagnosis;
import totah.lab.daedalus.conformance.ligandprep.FileLigandPrepSampler;
import totah.lab.daedalus.conformance.ligandprep.LigandPrepComparisonRunner;
import totah.lab.hephaestus.client.HephaestusClients;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Daedalus command line: end-to-end "prepare target and ligand for
 * docking" with optional vina execution. Mirrors the hephaestus CLI
 * idiom (registry, exit-code contract).
 */
public final class DaedalusCli {

    private static final java.time.format.DateTimeFormatter RUN_ID_FORMAT =
            java.time.format.DateTimeFormatter
                    .ofPattern("yyyyMMdd-HHmmss");

    private static final String DOCK_PREP_HELP = """
            USAGE

                daedalus dock-prep
                    --target <receptor.pdb>
                    --ligand <ligand.sdf>
                    --out <runs-dir>
                    (--box cx,cy,cz,sx,sy,sz | --pocket-id <db-pocket-id>)
                    [options]

            REQUIRED

                --target <file>
                    Input receptor PDB or mmCIF structure.

                --ligand <file>
                    Input ligand SDF (V2000, explicit hydrogens, 3D
                    coordinates).

                --out <dir>
                    Runs directory; a fresh run directory is created
                    inside it.

            BOX (optional unless --vina is used)

                --box <cx,cy,cz,sx,sy,sz>
                    Explicit search box: center and per-axis size in
                    angstroms.

                --pocket-id <id>
                    Derive the search box from this docking.pocket row
                    (alpha spheres preferred, pocket atoms otherwise).

                --padding <angstrom>
                    Padding per side for a --pocket-id box. Default: 8.

                Exactly one of --box / --pocket-id is required when
                --vina is used. Without --vina the box is optional.

            OPTIONS

                --vina <file>
                    AutoDock Vina executable; when given, docking runs
                    after preparation.

                --overwrite
                    Allow a non-empty --out directory.

                --help
                    Print command help.

            DATABASE (--pocket-id only)

                DB_URL (default jdbc:postgresql://localhost:5432/totah_lab_db),
                DB_USERNAME (default postgres), PGPASSWORD (required, no
                default).
            """;

    private static final String COMPARE_LIGAND_PREP_HELP = """
            USAGE

                daedalus compare-ligand-prep
                    [options]

            OPTIONS

                --count <n>
                    Number of sampled ligands. Default: 100.

                --report <file>
                    CSV report destination. Default:
                    analysis/ligand-prep-comparison/report-<timestamp>.csv
                    (relative to the current directory).

                --reference-dir <dir>
                    Directory of locally prepared reference ligands:
                    a manifest.tsv (id, name, h_added) plus, per row,
                    <id>.sdf and <id>.meeko.pdbqt produced by a local
                    Meeko mk_prepare_ligand.py run. Default:
                    /Users/yazan/artifacts/ligands/meeko-prepared

                --help
                    Print command help.

            The comparison prepares each sampled source SDF with
            hephaestus and compares the result with the Meeko
            (mk_prepare_ligand.py) reference PDBQT: heavy-atom counts,
            total and per-atom Gasteiger charge deltas, AD4 type
            agreement, and torsion counts. Ligands that fail
            preparation (for example SDFs without explicit hydrogens)
            are recorded with their failure reason.
            """;

    private final CommandRegistry registry;

    public DaedalusCli() {
        this.registry = new CommandRegistry(List.of(
                command("dock-prep",
                        "Prepare receptor and ligand for docking,"
                                + " optionally docking with vina.",
                        DOCK_PREP_HELP, this::dockPrep),
                command("compare-ligand-prep",
                        "Compare hephaestus ligand preparation against"
                                + " Meeko reference preparations.",
                        COMPARE_LIGAND_PREP_HELP, this::compareLigandPrep),
                command("diagnose-ad4-typing",
                        "Group AD4 typing mismatches with Meeko by rule"
                                + " pair with chemical context.",
                        COMPARE_LIGAND_PREP_HELP, this::diagnoseAd4Typing),
                command("version", "Print version information.",
                        "USAGE\n\n    daedalus version\n",
                        this::version),
                command("help", "Print this help.",
                        "USAGE\n\n    daedalus help [command]\n",
                        this::help)));
    }

    public CommandRegistry registry() {
        return registry;
    }

    public String topLevelHelp() {
        StringBuilder help = new StringBuilder("""
                Daedalus
                Molecular Docking Workflow Orchestration

                USAGE

                    daedalus <command> [options]

                COMMANDS

                """);
        for (CliCommand command : registry.commands()) {
            help.append("    ").append(command.name()).append("\n")
                    .append("        ").append(command.description())
                    .append("\n\n");
        }
        return help.append("""
                Run:

                    daedalus <command> --help

                for command-specific help.
                """).toString();
    }

    public int run(String[] arguments, PrintWriter out, PrintWriter err) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        if (arguments.length == 0
                || "--help".equals(arguments[0])
                || "-h".equals(arguments[0])) {
            out.print(topLevelHelp());
            out.flush();
            return CliExitCode.SUCCESS;
        }
        CliCommand command = registry.find(arguments[0]).orElse(null);
        if (command == null) {
            err.println("Unknown command: " + arguments[0]);
            err.println("Run 'daedalus help' for available commands.");
            err.flush();
            return CliExitCode.INVALID_ARGUMENTS;
        }
        String[] commandArguments = Arrays.copyOfRange(
                arguments, 1, arguments.length);
        if (List.of(commandArguments).contains("--help")) {
            out.print(command.help());
            out.flush();
            return CliExitCode.SUCCESS;
        }
        try {
            return command.execute(commandArguments, out, err);
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            return CliExitCode.INVALID_ARGUMENTS;
        } catch (RuntimeException exception) {
            err.println("Internal failure: " + exception.getMessage());
            return CliExitCode.INTERNAL_FAILURE;
        } finally {
            out.flush();
            err.flush();
        }
    }

    private int dockPrep(String[] arguments, PrintWriter out, PrintWriter err) {
        Arguments parsed = Arguments.parse(arguments,
                Set.of("target", "ligand", "out", "box", "pocket-id",
                        "padding", "vina"),
                Set.of("overwrite"));

        Path target = parsed.requiredPath("target");
        Path ligand = parsed.requiredPath("ligand");
        Path runsDirectory = parsed.requiredPath("out");

        boolean hasBox = parsed.has("box");
        boolean hasPocket = parsed.has("pocket-id");
        if (hasBox && hasPocket) {
            err.println("Specify exactly one of --box and --pocket-id.");
            return CliExitCode.INVALID_ARGUMENTS;
        }
        if (hasBox && parsed.has("padding")) {
            err.println("--padding applies only to --pocket-id.");
            return CliExitCode.INVALID_ARGUMENTS;
        }
        boolean hasVina = parsed.has("vina");
        if (hasVina && !hasBox && !hasPocket) {
            err.println("--vina requires a search box:"
                    + " --box or --pocket-id.");
            return CliExitCode.INVALID_ARGUMENTS;
        }

        if (Files.isDirectory(runsDirectory)
                && !parsed.flag("overwrite")) {
            try (var entries = Files.list(runsDirectory)) {
                if (entries.findAny().isPresent()) {
                    err.println("Output directory is not empty;"
                            + " use --overwrite: " + runsDirectory);
                    return CliExitCode.EXPORT_FAILURE;
                }
            } catch (IOException exception) {
                err.println("Input/output failure: "
                        + exception.getMessage());
                return CliExitCode.IO_FAILURE;
            }
        }

        final VinaDockingOptions box;
        try {
            box = resolveBox(parsed, hasBox, hasPocket);
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            return CliExitCode.INVALID_ARGUMENTS;
        } catch (IllegalStateException exception) {
            err.println("Pocket box failure: " + exception.getMessage());
            return CliExitCode.PREPARATION_FAILURE;
        } catch (SQLException exception) {
            err.println("Database failure: " + exception.getMessage());
            return CliExitCode.IO_FAILURE;
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put(ContextKeys.LIGAND_PATH, ligand);
        if (box != null) {
            config.put(ContextKeys.VINA_DOCKING_OPTIONS, box);
        }

        DockingProperties dockingProperties = new DockingProperties(
                hasVina
                        ? Path.of(parsed.values().get("vina"))
                        : null);

        final Pipeline pipeline;
        try {
            pipeline = new PipelineFactory(
                    new PipelineProperties(runsDirectory))
                    .createDockingPipeline(
                            config, target, dockingProperties);
        } catch (IOException exception) {
            err.println("Input/output failure: "
                    + exception.getMessage());
            return CliExitCode.IO_FAILURE;
        } catch (Exception exception) {
            err.println("Internal failure: " + exception.getMessage());
            return CliExitCode.INTERNAL_FAILURE;
        }

        try {
            pipeline.run();
        } catch (IllegalArgumentException exception) {
            err.println(exception.getMessage());
            return CliExitCode.INVALID_ARGUMENTS;
        } catch (IOException exception) {
            err.println("Input/output failure: "
                    + exception.getMessage());
            return CliExitCode.IO_FAILURE;
        } catch (IllegalStateException exception) {
            err.println("Stage failure: " + exception.getMessage());
            return CliExitCode.PREPARATION_FAILURE;
        } catch (Exception exception) {
            err.println("Internal failure: " + exception.getMessage());
            return CliExitCode.INTERNAL_FAILURE;
        }

        printSummary(pipeline.getContext(), box, hasVina, out);
        return CliExitCode.SUCCESS;
    }

    private VinaDockingOptions resolveBox(
            Arguments parsed,
            boolean hasBox,
            boolean hasPocket
    ) throws SQLException {

        if (hasBox) {
            return parseBox(parsed.values().get("box"));
        }
        if (hasPocket) {
            final long pocketId;
            try {
                pocketId = Long.parseLong(
                        parsed.values().get("pocket-id"));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid pocket id: "
                                + parsed.values().get("pocket-id"));
            }
            double padding = parsed.doubleValue(
                    "padding", 8.0, 0.0, Double.POSITIVE_INFINITY);
            PocketGridBox box = new PocketGridBoxLoader(
                    PocketGridBoxLoader.DatabaseConfig.fromEnvironment())
                    .load(pocketId, padding);
            return box.toVinaOptions();
        }
        return null;
    }

    private static VinaDockingOptions parseBox(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 6) {
            throw new IllegalArgumentException(
                    "--box needs six comma-separated numbers"
                            + " (cx,cy,cz,sx,sy,sz): " + value);
        }
        double[] numbers = new double[6];
        for (int index = 0; index < parts.length; index++) {
            try {
                numbers[index] = Double.parseDouble(parts[index].trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid number in --box: " + parts[index]);
            }
        }
        return VinaDockingOptions.ofBox(
                numbers[0], numbers[1], numbers[2],
                numbers[3], numbers[4], numbers[5]);
    }

    private void printSummary(
            PipelineContext context,
            VinaDockingOptions box,
            boolean vinaRan,
            PrintWriter out
    ) {
        out.println("Run directory: " + context.getRunDirectory());
        out.println("Receptor PDBQT: "
                + context.get(ContextKeys.RECEPTOR_PDBQT, "(none)"));
        out.println("Ligand PDBQT: "
                + context.get(ContextKeys.LIGAND_PDBQT_PATH, "(none)"));
        if (box != null) {
            out.println(String.format(
                    java.util.Locale.ROOT,
                    "Search box: center %.3f %.3f %.3f,"
                            + " size %.1f %.1f %.1f",
                    box.centerX(), box.centerY(), box.centerZ(),
                    box.sizeX(), box.sizeY(), box.sizeZ()));
        } else {
            out.println("Search box: (none)");
        }
        if (vinaRan) {
            VinaDockingResult result =
                    context.get(ContextKeys.DOCKING_RESULT, null);
            if (result != null) {
                out.println("Poses: " + result.poses().size());
                result.bestPose().ifPresent(pose ->
                        out.println("Best affinity: "
                                + pose.affinityKcalPerMol()
                                + " kcal/mol"));
            }
        }
    }

    private int compareLigandPrep(
            String[] arguments, PrintWriter out, PrintWriter err) {
        Arguments parsed = Arguments.parse(arguments,
                Set.of("count", "report", "reference-dir"),
                Set.of());

        int count = (int) parsed.doubleValue(
                "count", 100, 1, Double.POSITIVE_INFINITY);
        Path referenceDirectory = parsed.has("reference-dir")
                ? Path.of(parsed.values().get("reference-dir"))
                : Path.of("/Users/yazan/artifacts/ligands"
                        + "/meeko-prepared");
        Path report = parsed.optionalPath("report");
        if (report == null) {
            report = Path.of("analysis", "ligand-prep-comparison",
                    "report-" + RUN_ID_FORMAT.format(
                            java.time.LocalDateTime.now()) + ".csv");
        }

        Path workDirectory = report.toAbsolutePath().normalize()
                .getParent()
                .resolve("work-" + RUN_ID_FORMAT.format(
                        java.time.LocalDateTime.now()));

        LigandPrepComparisonRunner runner = new LigandPrepComparisonRunner(
                new FileLigandPrepSampler(referenceDirectory),
                HephaestusClients.createDefault(),
                workDirectory);

        final List<LigandPrepComparisonRunner.Outcome> outcomes;
        try {
            outcomes = runner.run(count);
        } catch (IOException exception) {
            err.println("Input/output failure: " + exception.getMessage());
            return CliExitCode.IO_FAILURE;
        } catch (Exception exception) {
            err.println("Internal failure: " + exception.getMessage());
            return CliExitCode.INTERNAL_FAILURE;
        }

        try {
            Files.createDirectories(report.toAbsolutePath()
                    .normalize().getParent());
            Files.writeString(report,
                    LigandPrepComparisonRunner.csv(outcomes));
        } catch (IOException exception) {
            err.println("Export failure: " + exception.getMessage());
            return CliExitCode.EXPORT_FAILURE;
        }

        out.print(LigandPrepComparisonRunner.summary(outcomes));
        out.println("Report: "
                + report.toAbsolutePath().normalize());
        out.println("Prepared PDBQTs: " + workDirectory);
        return CliExitCode.SUCCESS;
    }

    private int diagnoseAd4Typing(
            String[] arguments, PrintWriter out, PrintWriter err) {
        Arguments parsed = Arguments.parse(arguments,
                Set.of("count", "report", "reference-dir"),
                Set.of());

        int count = (int) parsed.doubleValue(
                "count", 100, 1, Double.POSITIVE_INFINITY);
        Path referenceDirectory = parsed.has("reference-dir")
                ? Path.of(parsed.values().get("reference-dir"))
                : Path.of("/Users/yazan/artifacts/ligands"
                        + "/meeko-prepared");
        Path report = parsed.optionalPath("report");
        if (report == null) {
            report = Path.of("analysis", "ligand-prep-comparison",
                    "ad4-diagnosis.md");
        }

        Path workDirectory = report.toAbsolutePath().normalize()
                .getParent()
                .resolve("diag-work-" + RUN_ID_FORMAT.format(
                        java.time.LocalDateTime.now()));

        String markdown;
        try {
            markdown = new Ad4TypingDiagnosis(
                    new FileLigandPrepSampler(referenceDirectory),
                    HephaestusClients.createDefault(),
                    workDirectory).diagnose(count);
        } catch (IOException exception) {
            err.println("Input/output failure: " + exception.getMessage());
            return CliExitCode.IO_FAILURE;
        } catch (Exception exception) {
            err.println("Internal failure: " + exception.getMessage());
            return CliExitCode.INTERNAL_FAILURE;
        }

        try {
            Files.createDirectories(report.toAbsolutePath()
                    .normalize().getParent());
            Files.writeString(report, markdown);
        } catch (IOException exception) {
            err.println("Export failure: " + exception.getMessage());
            return CliExitCode.EXPORT_FAILURE;
        }

        out.println("Diagnosis: " + report.toAbsolutePath().normalize());
        return CliExitCode.SUCCESS;
    }

    private int version(String[] arguments, PrintWriter out, PrintWriter err) {
        Arguments.parse(arguments, Set.of(), Set.of());
        String version =
                DaedalusCli.class.getPackage().getImplementationVersion();
        out.println("Daedalus " + (version == null ? "development" : version));
        return CliExitCode.SUCCESS;
    }

    private int help(String[] arguments, PrintWriter out, PrintWriter err) {
        if (arguments.length == 0) {
            out.print(topLevelHelp());
            return CliExitCode.SUCCESS;
        }
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Usage: daedalus help [command]");
        }
        CliCommand command = registry.find(arguments[0]).orElseThrow(() ->
                new IllegalArgumentException("Unknown command: " + arguments[0]));
        out.print(command.help());
        return CliExitCode.SUCCESS;
    }

    private CliCommand command(
            String name, String description, String help, Executor executor) {
        return new CliCommand() {
            public String name() { return name; }
            public String description() { return description; }
            public String help() { return help; }
            public int execute(String[] args, PrintWriter out, PrintWriter err) {
                return executor.execute(args, out, err);
            }
        };
    }

    public static void main(String[] arguments) {
        int exitCode = new DaedalusCli().run(arguments,
                new PrintWriter(System.out), new PrintWriter(System.err));
        if (exitCode != CliExitCode.SUCCESS) {
            System.exit(exitCode);
        }
    }

    @FunctionalInterface
    private interface Executor {
        int execute(String[] arguments, PrintWriter out, PrintWriter err);
    }

    record Arguments(Map<String, String> values, Set<String> flags) {
        static Arguments parse(
                String[] arguments, Set<String> valueOptions,
                Set<String> flagOptions) {
            Map<String, String> values = new LinkedHashMap<>();
            Set<String> flags = new java.util.LinkedHashSet<>();
            List<String> tokens = new ArrayList<>(List.of(arguments));
            for (int index = 0; index < tokens.size(); index++) {
                String token = tokens.get(index);
                if (!token.startsWith("--")) {
                    throw new IllegalArgumentException(
                            "Unexpected argument: " + token);
                }
                String name = token.substring(2);
                if (flagOptions.contains(name)) {
                    if (!flags.add(name)) {
                        throw new IllegalArgumentException(
                                "Duplicate option: " + token);
                    }
                } else if (valueOptions.contains(name)) {
                    if (index + 1 >= tokens.size()
                            || tokens.get(index + 1).startsWith("--")) {
                        throw new IllegalArgumentException(
                                "Missing value for " + token);
                    }
                    if (values.putIfAbsent(name, tokens.get(++index)) != null) {
                        throw new IllegalArgumentException(
                                "Duplicate option: " + token);
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Unknown option: " + token);
                }
            }
            return new Arguments(Map.copyOf(values), Set.copyOf(flags));
        }

        boolean has(String name) { return values.containsKey(name); }
        boolean flag(String name) { return flags.contains(name); }
        Path optionalPath(String name) {
            return has(name) ? Path.of(values.get(name)) : null;
        }
        Path requiredPath(String name) {
            Path path = optionalPath(name);
            if (path == null) {
                throw new IllegalArgumentException(
                        "Missing required option: --" + name);
            }
            return path;
        }
        double doubleValue(
                String name, double defaultValue, double minimum,
                double maximum) {
            if (!has(name)) return defaultValue;
            final double value;
            try {
                value = Double.parseDouble(values.get(name));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid number for --" + name + ": " + values.get(name));
            }
            if (!Double.isFinite(value) || value < minimum || value > maximum
                    || minimum == 0.0 && maximum == Double.POSITIVE_INFINITY
                    && value <= 0.0) {
                throw new IllegalArgumentException(
                        "Value out of range for --" + name + ": " + value);
            }
            return value;
        }
    }
}
