package totah.lab.mettl7.campaign.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Generates the authoritative receptor x prepared-species x seed ledger.
 * It fails closed until both final manifests explicitly resolve every entry.
 * Receptor construction failures remain explicit ledger rows and are never
 * silently removed from the intended Cartesian product.
 * No scientific measurement or docking is performed here.
 */
public final class Mettl7CartesianLedgerGenerator {
    private static final String HEADER = String.join(",",
            "run_id", "receptor_id", "paralog", "receptor_mutations",
            "receptor_path", "receptor_sha256", "window_id", "compound_branch",
            "species_id", "stereoisomer", "protonation_or_speciation", "tautomer",
            "acceptor_atom", "ligand_path", "ligand_sha256", "cofactor_state",
            "seed", "technical_status", "artifact_path") + "\n";

    private final ObjectMapper mapper;

    public Mettl7CartesianLedgerGenerator() {
        this(new ObjectMapper());
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: <receptor-manifest> <ligand-manifest> <ledger.csv>");
        }
        int rows = new Mettl7CartesianLedgerGenerator().write(
                Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.println("AUTHORITATIVE_EXPECTED_RUN_COUNT=" + rows);
    }

    Mettl7CartesianLedgerGenerator(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public LedgerPlan plan(Path receptorManifest, Path ligandManifest) throws IOException {
        List<Receptor> receptors = readReceptors(receptorManifest);
        List<Species> species = readSpecies(ligandManifest);
        List<String> blockers = new ArrayList<>();
        receptors.stream().filter(r -> !r.resolved()).forEach(r ->
                blockers.add("receptor " + r.id()
                        + " is neither VALID nor TECHNICAL_FAILURE"));
        species.stream().filter(s -> !s.valid()).forEach(s ->
                blockers.add("species " + s.id() + " is not VALID"));
        requireUnique(receptors.stream().map(Receptor::id).toList(), "receptor", blockers);
        requireUnique(species.stream().map(Species::id).toList(), "species", blockers);
        return new LedgerPlan(List.copyOf(receptors), List.copyOf(species),
                Mettl7MechanisticMatrixV2Protocol.SEEDS, List.copyOf(blockers));
    }

    public int write(Path receptorManifest, Path ligandManifest, Path output) throws IOException {
        LedgerPlan plan = plan(receptorManifest, ligandManifest);
        if (!plan.ready()) {
            throw new IOException("Final manifests are not ledger-ready: "
                    + String.join("; ", plan.blockers()));
        }
        List<Row> rows = plan.rows();
        StringBuilder csv = new StringBuilder(HEADER);
        rows.forEach(row -> csv.append(row.toCsv()));
        Path parent = output.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(output, csv, StandardCharsets.UTF_8);
        return rows.size();
    }

    private List<Receptor> readReceptors(Path path) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        JsonNode entries = array(root, "receptors");
        List<Receptor> result = new ArrayList<>();
        for (JsonNode node : entries) {
            String integrityStatus = requiredEither(node, "integrity_status", "status");
            String paralog = required(node, "paralog");
            String windowId = text(node, "window_id");
            if (windowId.isBlank()) {
                windowId = paralog.equals("METTL7A")
                        ? "METTL7A_NATIVE_V1" : "METTL7B_NATIVE_V1";
            }
            result.add(new Receptor(required(node, "receptor_id"), paralog,
                    listEither(node, "mutations", "substitutions"),
                    required(node, "prepared_path"),
                    requiredSha(node, "prepared_sha256"), windowId,
                    integrityStatus));
        }
        result.sort(Comparator.comparing(Receptor::id));
        return result;
    }

    private List<Species> readSpecies(Path path) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        JsonNode entries = array(root, "species");
        List<Species> result = new ArrayList<>();
        for (JsonNode node : entries) {
            result.add(new Species(required(node, "species_id"),
                    required(node, "compound_branch"), text(node, "stereoisomer"),
                    text(node, "protonation_or_speciation"), text(node, "tautomer"),
                    text(node, "acceptor_atom"), required(node, "prepared_path"),
                    requiredSha(node, "prepared_sha256"),
                    Set.of("VALID", "PASS").contains(
                            required(node, "preparation_status"))));
        }
        result.sort(Comparator.comparing(Species::id));
        return result;
    }

    private static JsonNode array(JsonNode root, String field) throws IOException {
        JsonNode value = root.isArray() ? root : root.get(field);
        if (value == null || !value.isArray()) {
            throw new IOException("Manifest must contain array '" + field + "'");
        }
        return value;
    }

    private static String required(JsonNode node, String field) throws IOException {
        String value = text(node, field);
        if (value.isBlank()) throw new IOException("Missing manifest field: " + field);
        return value;
    }

    private static String requiredEither(JsonNode node, String first,
            String second) throws IOException {
        String value = text(node, first);
        return value.isBlank() ? required(node, second) : value;
    }

    private static String requiredSha(JsonNode node, String field) throws IOException {
        String value = required(node, field).toLowerCase(Locale.ROOT);
        if (!value.matches("[0-9a-f]{64}")) throw new IOException("Invalid SHA-256: " + field);
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText().trim();
    }

    private static List<String> list(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) throw new IOException("Missing array: " + field);
        List<String> result = new ArrayList<>();
        value.forEach(item -> result.add(item.asText()));
        return List.copyOf(result);
    }

    private static List<String> listEither(JsonNode node, String first,
            String second) throws IOException {
        JsonNode value = node.get(first);
        return value != null && value.isArray()
                ? list(node, first) : list(node, second);
    }

    private static void requireUnique(List<String> values, String label, List<String> blockers) {
        for (int index = 0; index < values.size(); index++) {
            if (values.subList(index + 1, values.size()).contains(values.get(index))) {
                blockers.add("duplicate " + label + " id " + values.get(index));
            }
        }
    }

    public record LedgerPlan(List<Receptor> receptors, List<Species> species,
                             List<Integer> seeds, List<String> blockers) {
        public LedgerPlan {
            receptors = List.copyOf(receptors); species = List.copyOf(species);
            seeds = List.copyOf(seeds); blockers = List.copyOf(blockers);
        }
        public boolean ready() { return blockers.isEmpty(); }
        public List<Row> rows() {
            if (!ready()) throw new IllegalStateException("Ledger plan is blocked");
            List<Row> rows = new ArrayList<>();
            for (Receptor receptor : receptors) for (Species ligand : species)
                for (int seed : seeds) rows.add(Row.of(receptor, ligand, seed));
            return List.copyOf(rows);
        }
    }

    public record Receptor(String id, String paralog, List<String> mutations,
                           String path, String sha256, String windowId,
                           String integrityStatus) {
        public Receptor { mutations = List.copyOf(mutations); }
        public Receptor(String id, String paralog, List<String> mutations,
                String path, String sha256, String windowId, boolean valid) {
            this(id, paralog, mutations, path, sha256, windowId,
                    valid ? "VALID" : "UNRESOLVED");
        }
        public boolean resolved() {
            return "VALID".equals(integrityStatus)
                    || "TECHNICAL_FAILURE".equals(integrityStatus);
        }
    }
    public record Species(String id, String branch, String stereoisomer, String speciation,
                          String tautomer, String acceptorAtom, String path, String sha256,
                          boolean valid) {}
    public record Row(String runId, Receptor receptor, Species species, int seed) {
        static Row of(Receptor receptor, Species species, int seed) {
            String id = String.format(Locale.ROOT, "%s__%s__s%d", receptor.id(), species.id(), seed);
            return new Row(id, receptor, species, seed);
        }
        String toCsv() {
            return String.join(",", q(runId), q(receptor.id()), q(receptor.paralog()),
                    q(String.join("+", receptor.mutations())), q(receptor.path()),
                    q(receptor.sha256()), q(receptor.windowId()), q(species.branch()),
                    q(species.id()), q(species.stereoisomer()), q(species.speciation()),
                    q(species.tautomer()), q(species.acceptorAtom()), q(species.path()),
                    q(species.sha256()), "SAM", Integer.toString(seed),
                    receptor.integrityStatus().equals("VALID")
                            ? "PENDING" : "TECHNICAL_FAILURE",
                    "") + "\n";
        }
        private static String q(String value) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
    }
}
