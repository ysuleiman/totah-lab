package totah.lab.hephaestus.cli;

import totah.lab.hephaestus.client.DefaultHephaestusClient;
import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.hephaestus.client.HephaestusClients;
import totah.lab.hephaestus.factory.ProteinFactory;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hephaestus.model.PreparationIssue;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;
import totah.lab.hephaestus.receptor.ReceptorPreparer;
import totah.lab.hephaestus.receptor.ReceptorPreparerBuilder;
import totah.lab.hephaestus.receptor.hydrogen.ReceptorHydrogenator;
import totah.lab.hephaestus.receptor.operation.AD4AtomTypingOperation;
import totah.lab.hephaestus.receptor.operation.AlphaFoldFilterOperation;
import totah.lab.hephaestus.receptor.operation.ChargeAssignmentOperation;
import totah.lab.hephaestus.receptor.operation.HydrogenOptimizationOperation;
import totah.lab.hephaestus.receptor.operation.ReceptorHydrogenationOperation;
import totah.lab.hephaestus.receptor.operation.ResidueStateAssignmentOperation;
import totah.lab.hephaestus.receptor.operation.StructureCleanupOperation;
import totah.lab.hephaestus.receptor.operation.TopologyBuilderOperation;
import totah.lab.hephaestus.receptor.protonation.HistidineState;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.pdbqt.PdbqtWriteOptions;
import totah.lab.hermes.file.pdbqt.writer.PdbqtWriter;
import totah.lab.hermes.file.pdbqt.validation.PdbqtValidationReport;
import totah.lab.hermes.file.pdbqt.validation.PdbqtValidator;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class HephaestusCli {
    private static final String PREPARE_HELP = """
            USAGE

                hephaestus prepare-receptor
                    --input <file>
                    --output <file>
                    [options]

            REQUIRED

                --input <file>
                    Input PDB or mmCIF structure.

                --output <file>
                    Output rigid receptor PDBQT file.

            OPTIONS

                --ph <0-14>
                    Preparation pH. Default: 7.4.

                --plddt-cutoff <0-100>
                    Remove low-confidence AlphaFold residues.
                    Omit for experimental structures.

                --his <HID|HIE|HIP>
                    Histidine protonation state. Default: HIE.

                --detect-disulfides
                    Detect disulfide cysteines.

                --disulfide-cutoff <angstrom>
                    Sulfur-distance cutoff used for disulfide detection. Default: 2.2.

                --remove-waters
                    Remove water molecules.

                --keep-metals
                    Retain supported metal atoms.

                --strict
                    Treat validation warnings as failures.

                --overwrite
                    Replace an existing output file.

                --help
                    Print command help.
            """;

    private static final String PREPARE_LIGAND_HELP = """
            USAGE

                hephaestus prepare-ligand
                    --input <file>
                    --output <file>
                    [options]

            REQUIRED

                --input <file>
                    Input ligand SDF (V2000, single molecule, explicit
                    hydrogens and 3D coordinates).

                --output <file>
                    Output ligand PDBQT file.

            OPTIONS

                --largest-fragment
                    Keep only the largest fragment (salt stripping).

                --no-hydrogens
                    Do not add hydrogens (default: hydrogens are added).

                --no-charges
                    Do not assign Gasteiger charges (default: charges
                    are assigned).

                --no-atom-types
                    Do not assign AutoDock4 atom types (default: types
                    are assigned).

                --overwrite
                    Replace an existing output file.

                --help
                    Print command help.
            """;

    private static final String VALIDATE_HELP = """
            USAGE

                hephaestus validate-pdbqt
                    --input <file>
                    [options]

            OPTIONS

                --input <file>
                    PDBQT file to validate.

                --report <file>
                    Optional validation report output.

                --strict
                    Treat warnings as validation failure.

                --help
                    Print command help.
            """;

    private static final String VALIDATE_LIGAND_HELP = """
            USAGE

                hephaestus validate-ligand-pdbqt
                    --input <file>
                    [options]

            OPTIONS
                --input <file>
                    Ligand PDBQT file to validate.

                --report <file>
                    Optional validation report output.

                --strict
                    Treat warnings as validation failure.

                --help
                    Print command help.
            """;

    private static final String VALIDATE_FLEX_HELP = """
            USAGE

                hephaestus validate-flex-pdbqt
                    --rigid <file>
                    --flex <file>
                    [options]

            OPTIONS

                --rigid <file>
                    Rigid receptor PDBQT file.

                --flex <file>
                    Flexible receptor PDBQT file.

                --report <file>
                    Optional validation report output.

                --strict
                    Treat warnings as validation failure.

                --help
                    Print command help.
            """;

    private final HephaestusClient client;
    private final PdbqtWriter writer;
    private final CommandRegistry registry;

    public HephaestusCli(HephaestusClient client, PdbqtWriter writer) {
        this.client = Objects.requireNonNull(client, "client");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.registry = new CommandRegistry(List.of(
                command("prepare-receptor",
                        "Prepare a receptor and write rigid PDBQT output.",
                        PREPARE_HELP, this::prepareReceptor),
                command("prepare-ligand",
                        "Prepare an SDF ligand and write PDBQT output.",
                        PREPARE_LIGAND_HELP, this::prepareLigand),
                command("validate-pdbqt",
                        "Validate an existing PDBQT file.",
                        VALIDATE_HELP, this::validatePdbqt),
                command("validate-ligand-pdbqt",
                        "Validate an existing ligand PDBQT file.",
                        VALIDATE_LIGAND_HELP, this::validateLigandPdbqt),
                command("validate-flex-pdbqt",
                        "Validate a rigid/flexible PDBQT file pair.",
                        VALIDATE_FLEX_HELP, this::validateFlexiblePdbqt),
                command("version", "Print version information.",
                        "USAGE\n\n    hephaestus version\n",
                        this::version),
                command("help", "Print this help.",
                        "USAGE\n\n    hephaestus help [command]\n",
                        this::help)));
    }

    public CommandRegistry registry() {
        return registry;
    }

    public String topLevelHelp() {
        StringBuilder help = new StringBuilder("""
                Hephaestus
                Molecular Receptor Preparation and PDBQT Validation

                USAGE

                    hephaestus <command> [options]

                COMMANDS

                """);
        for (CliCommand command : registry.commands()) {
            help.append("    ").append(command.name()).append("\n")
                    .append("        ").append(command.description())
                    .append("\n\n");
        }
        return help.append("""
                Run:

                    hephaestus <command> --help

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
            err.println("Run 'hephaestus help' for available commands.");
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

    private int prepareReceptor(String[] arguments, PrintWriter out, PrintWriter err) {
        Arguments parsed = Arguments.parse(arguments,
                Set.of("input", "output", "ph", "plddt-cutoff", "his",
                        "disulfide-cutoff"),
                Set.of("detect-disulfides", "remove-waters", "keep-metals",
                        "strict", "overwrite"));
        Path input = parsed.requiredPath("input");
        Path output = parsed.requiredPath("output");
        if (Files.exists(output) && !parsed.flag("overwrite")) {
            err.println("Output already exists; use --overwrite: " + output);
            return CliExitCode.EXPORT_FAILURE;
        }
        ReceptorPreparationOptions options = preparationOptions(parsed);
        final ReceptorPreparationResult preparation;
        try {
            preparation = client.prepareReceptor(input, options);
        } catch (IOException exception) {
            err.println("Input/output failure: " + exception.getMessage());
            return CliExitCode.IO_FAILURE;
        } catch (RuntimeException exception) {
            err.println("Preparation failure: " + exception.getMessage());
            return CliExitCode.PREPARATION_FAILURE;
        }
        if (preparation.hasErrors()) {
            preparation.issues().forEach(issue -> err.println(
                    issue.severity() + " " + issue.code() + ": " + issue.message()));
            return CliExitCode.PREPARATION_FAILURE;
        }
        ValidationReport report = client.validatePreparedProtein(
                preparation.preparedProtein());
        writeReport(report, out);
        if (report.hasErrors()
                || parsed.flag("strict") && report.hasWarnings()) {
            return CliExitCode.VALIDATION_ERROR;
        }
        try {
            writer.write(preparation.preparedProtein().protein().structure(),
                    output, PdbqtWriteOptions.defaults());
            out.println("Wrote rigid receptor PDBQT: "
                    + output.toAbsolutePath().normalize());
            return CliExitCode.SUCCESS;
        } catch (IOException exception) {
            err.println("Export failure: " + exception.getMessage());
            return CliExitCode.EXPORT_FAILURE;
        } catch (RuntimeException exception) {
            err.println("Export failure: " + exception.getMessage());
            return CliExitCode.EXPORT_FAILURE;
        }
    }

    private int prepareLigand(String[] arguments, PrintWriter out, PrintWriter err) {
        Arguments parsed = Arguments.parse(arguments,
                Set.of("input", "output"),
                Set.of("largest-fragment", "no-hydrogens", "no-charges",
                        "no-atom-types", "overwrite"));
        Path input = parsed.requiredPath("input");
        Path output = parsed.requiredPath("output");
        if (Files.exists(output) && !parsed.flag("overwrite")) {
            err.println("Output already exists; use --overwrite: " + output);
            return CliExitCode.EXPORT_FAILURE;
        }
        LigandPreparationOptions defaults = LigandPreparationOptions.defaults();
        LigandPreparationOptions options = new LigandPreparationOptions(
                !parsed.flag("no-hydrogens") && defaults.addHydrogens(),
                defaults.generateProtonationStates(),
                defaults.generateTautomers(),
                !parsed.flag("no-charges") && defaults.assignCharges(),
                !parsed.flag("no-atom-types") && defaults.assignAtomTypes(),
                defaults.generateConformers(),
                defaults.maximumConformers(),
                parsed.flag("largest-fragment")
                        || defaults.selectLargestFragment());
        final LigandPreparationResult preparation;
        try {
            preparation = client.prepareLigand(input, options);
        } catch (IOException exception) {
            err.println("Input/output failure: " + exception.getMessage());
            return CliExitCode.IO_FAILURE;
        } catch (RuntimeException exception) {
            err.println("Preparation failure: " + exception.getMessage());
            return CliExitCode.PREPARATION_FAILURE;
        }
        if (preparation.hasErrors()) {
            preparation.issues().forEach(issue -> err.println(
                    issue.severity() + " " + issue.code() + ": " + issue.message()));
            return CliExitCode.PREPARATION_FAILURE;
        }
        writeReportIssues(preparation.issues(), out);
        final Path written;
        try {
            written = client.writePreparedLigand(
                    preparation.preparedLigand(), output);
        } catch (IOException exception) {
            err.println("Export failure: " + exception.getMessage());
            return CliExitCode.EXPORT_FAILURE;
        } catch (RuntimeException exception) {
            err.println("Export failure: " + exception.getMessage());
            return CliExitCode.EXPORT_FAILURE;
        }
        try {
            printLigandSummary(preparation, written, out);
        } catch (IOException exception) {
            err.println("Export failure: " + exception.getMessage());
            return CliExitCode.EXPORT_FAILURE;
        }
        out.println("Wrote ligand PDBQT: "
                + written.toAbsolutePath().normalize());
        return CliExitCode.SUCCESS;
    }

    /*
     * Summary from the validated artifact itself: ATOM records and the
     * TORSDOF torsion count are read back from the written PDBQT.
     */
    private void printLigandSummary(
            LigandPreparationResult preparation, Path pdbqt,
            PrintWriter out) throws IOException {
        long atoms = 0;
        int torsions = 0;
        try (var lines = Files.lines(pdbqt)) {
            for (String line : lines.toList()) {
                if (line.startsWith("ATOM")) {
                    atoms++;
                } else if (line.startsWith("TORSDOF")) {
                    torsions = Integer.parseInt(line.substring(7).trim());
                }
            }
        }
        out.println("Ligand atoms: " + atoms);
        preparation.preparedLigand().chargesOptional().ifPresent(charges ->
                out.println("Total charge: " + String.format(
                        java.util.Locale.ROOT, "%.3f",
                        charges.totalCharge())));
        out.println("Rotatable bonds: " + torsions);
    }

    private int validatePdbqt(String[] arguments, PrintWriter out, PrintWriter err) {
        Arguments parsed = Arguments.parse(arguments,
                Set.of("input", "report"), Set.of("strict"));
        try {
            return validationResult(client.validatePdbqt(
                    parsed.requiredPath("input")), parsed, out);
        } catch (IOException exception) {
            err.println("Input/output failure: " + exception.getMessage());
            return CliExitCode.IO_FAILURE;
        }
    }

    private int validateLigandPdbqt(
            String[] arguments, PrintWriter out, PrintWriter err) {
        Arguments parsed = Arguments.parse(arguments,
                Set.of("input", "report"), Set.of("strict"));
        try {
            return validationResult(client.validateLigandPdbqt(
                    parsed.requiredPath("input")), parsed, out);
        } catch (IOException exception) {
            err.println("Input/output failure: " + exception.getMessage());
            return CliExitCode.IO_FAILURE;
        }
    }

    private int validateFlexiblePdbqt(
            String[] arguments, PrintWriter out, PrintWriter err) {
        Arguments parsed = Arguments.parse(arguments,
                Set.of("rigid", "flex", "report"), Set.of("strict"));
        try {
            return validationResult(client.validateFlexiblePdbqt(
                    parsed.requiredPath("rigid"), parsed.requiredPath("flex")),
                    parsed, out);
        } catch (IOException exception) {
            err.println("Input/output failure: " + exception.getMessage());
            return CliExitCode.IO_FAILURE;
        }
    }

    private int validationResult(
            PdbqtValidationReport report, Arguments arguments, PrintWriter out)
            throws IOException {
        String text = report.issues().isEmpty()
                ? "VALID\n"
                : report.issues().stream()
                .map(issue -> issue.severity() + " " + issue.code()
                        + " " + issue.location() + ": " + issue.message())
                .reduce("", (left, right) -> left + right + System.lineSeparator());
        out.print(text);
        Path reportPath = arguments.optionalPath("report");
        if (reportPath != null) {
            Files.writeString(reportPath, text, StandardCharsets.UTF_8);
        }
        return report.hasErrors()
                || arguments.flag("strict") && report.hasWarnings()
                ? CliExitCode.VALIDATION_ERROR
                : CliExitCode.SUCCESS;
    }

    private int version(String[] arguments, PrintWriter out, PrintWriter err) {
        Arguments.parse(arguments, Set.of(), Set.of());
        String version = HephaestusCli.class.getPackage().getImplementationVersion();
        out.println("Hephaestus " + (version == null ? "development" : version));
        return CliExitCode.SUCCESS;
    }

    private int help(String[] arguments, PrintWriter out, PrintWriter err) {
        if (arguments.length == 0) {
            out.print(topLevelHelp());
            return CliExitCode.SUCCESS;
        }
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Usage: hephaestus help [command]");
        }
        CliCommand command = registry.find(arguments[0]).orElseThrow(() ->
                new IllegalArgumentException("Unknown command: " + arguments[0]));
        out.print(command.help());
        return CliExitCode.SUCCESS;
    }

    private ReceptorPreparationOptions preparationOptions(Arguments arguments) {
        ReceptorPreparationOptions defaults = ReceptorPreparationOptions.defaults();
        ProtonationConfig base = defaults.protonationConfig();
        double ph = arguments.doubleValue("ph", base.ph(), 0.0, 14.0);
        double disulfideCutoff = arguments.doubleValue(
                "disulfide-cutoff", base.disulfideCutoff(), 0.0,
                Double.POSITIVE_INFINITY);
        HistidineState histidine = arguments.enumValue(
                "his", HistidineState.class, base.histidineState());
        ProtonationConfig protonation = new ProtonationConfig(
                ph, base.voxelGridSize(), base.clashCutoff(), histidine,
                arguments.flag("detect-disulfides") || base.detectDisulfides(),
                disulfideCutoff, base.metalCoordinationCutoff(),
                base.nTerminusState(), base.cTerminusState(),
                base.amberParameterPath());
        Double plddt = arguments.has("plddt-cutoff")
                ? arguments.doubleValue("plddt-cutoff", 0.0, 0.0, 100.0)
                : null;
        return new ReceptorPreparationOptions(
                arguments.flag("remove-waters") || defaults.removeWaters(),
                arguments.flag("keep-metals"), defaults.allowedSpecialResidues(),
                plddt, defaults.addHydrogens(), defaults.optimizeHydrogens(),
                defaults.buildTopology(), defaults.assignCharges(),
                defaults.assignAtomTypes(), protonation,
                defaults.residueProtonationOverrides(),
                defaults.flexibilityConfig(), null);
    }

    private void writeReportIssues(
            List<PreparationIssue> issues, PrintWriter out) {
        issues.forEach(issue -> out.println(
                issue.severity() + " " + issue.code() + ": "
                        + issue.message()));
    }

    private void writeReport(ValidationReport report, PrintWriter out) {
        report.issues().forEach(issue -> out.println(
                issue.severity() + " " + issue.code() + " "
                        + issue.location() + ": " + issue.message()));
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

    public static HephaestusCli createDefault() {
        PdbqtWriter writer = new PdbqtWriter();
        return new HephaestusCli(
                HephaestusClients.createDefault(), writer);
    }

    public static void main(String[] arguments) {
        int exitCode = createDefault().run(arguments,
                new PrintWriter(System.out), new PrintWriter(System.err));
        if (exitCode != CliExitCode.SUCCESS) {
            System.exit(exitCode);
        }
    }

    @FunctionalInterface
    private interface Executor {
        int execute(String[] arguments, PrintWriter out, PrintWriter err);
    }

    private record Arguments(Map<String, String> values, Set<String> flags) {
        private static Arguments parse(
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

        private boolean has(String name) { return values.containsKey(name); }
        private boolean flag(String name) { return flags.contains(name); }
        private Path optionalPath(String name) {
            return has(name) ? Path.of(values.get(name)) : null;
        }
        private Path requiredPath(String name) {
            Path path = optionalPath(name);
            if (path == null) {
                throw new IllegalArgumentException(
                        "Missing required option: --" + name);
            }
            return path;
        }
        private double doubleValue(
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
        private <E extends Enum<E>> E enumValue(
                String name, Class<E> type, E defaultValue) {
            if (!has(name)) return defaultValue;
            try {
                return Enum.valueOf(type, values.get(name));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unsupported value for --" + name + ": " + values.get(name));
            }
        }
    }
}
