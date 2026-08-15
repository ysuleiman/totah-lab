package totah.lab.prometheus.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import totah.lab.prometheus.identity.CanonicalAtomId;
import totah.lab.prometheus.identity.CanonicalAtomMap;
import totah.lab.prometheus.identity.MoleculeIdentity;

/**
 * Builds the full TSL-RSH {@link CanonicalAtomMap} from the execution-unit-02
 * {@code canonical_atom_inventory.csv} (rows with chemical_id TSL_RSH).
 *
 * <p>Column lookup is by header name: the canonical index comes from
 * {@code canonical_atom_index}, the label from {@code mol2_atom_name} (falling
 * back to {@code atom_name} or {@code label}), the element from
 * {@code element}. The number embedded in a label is NOT the canonical index
 * (TSL serial 10 is labeled C9); atoms must always be addressed by index.
 *
 * <p>The molecular formula is derived from the parsed element counts (Hill
 * order) rather than asserted.
 */
public final class LegacyCanonicalAtomLoader {

    public static final String MOLECULE_ID = "TSL-RSH";
    public static final String DISPLAY_NAME = "neutral TSL thiol";

    private LegacyCanonicalAtomLoader() {
    }

    /** Loads the TSL-RSH canonical atom map from the execution-unit-02 directory. */
    public static CanonicalAtomMap load(Path executionUnit02Dir) throws IOException {
        Objects.requireNonNull(executionUnit02Dir, "executionUnit02Dir");
        Path inventory = executionUnit02Dir.resolve("canonical_atom_inventory.csv");
        if (!Files.isRegularFile(inventory)) {
            throw new IOException("canonical atom inventory not found: " + inventory);
        }
        CsvTable table = CsvTable.read(inventory);
        List<CanonicalAtomId> atoms = new ArrayList<>();
        for (List<String> row : table.rows()) {
            Optional<String> chemicalId = table.cell(row, "chemical_id");
            if (chemicalId.isEmpty() || !chemicalId.get().equals("TSL_RSH")) {
                continue;
            }
            int index = table.cell(row, "canonical_atom_index")
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .orElseThrow(() -> new IOException(
                            "TSL_RSH row without canonical_atom_index in " + inventory));
            String label = firstPresent(table, row, "mol2_atom_name", "atom_name", "label")
                    .orElseThrow(() -> new IOException(
                            "TSL_RSH row " + index + " without an atom name in " + inventory));
            String element = table.cell(row, "element")
                    .orElseThrow(() -> new IOException(
                            "TSL_RSH row " + index + " without element in " + inventory));
            atoms.add(new CanonicalAtomId(index, label, element));
        }
        if (atoms.isEmpty()) {
            throw new IOException("no TSL_RSH rows found in " + inventory);
        }
        MoleculeIdentity molecule = new MoleculeIdentity(
                MOLECULE_ID, DISPLAY_NAME, hillFormula(atoms));
        return new CanonicalAtomMap(molecule, atoms);
    }

    private static Optional<String> firstPresent(
            CsvTable table, List<String> row, String... columns) {
        for (String column : columns) {
            Optional<String> value = table.cell(row, column);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    /** Hill-ordered formula from parsed element symbols (C, then H, then alphabetical). */
    static String hillFormula(List<CanonicalAtomId> atoms) {
        Map<String, Integer> counts = new TreeMap<>();
        for (CanonicalAtomId atom : atoms) {
            counts.merge(atom.elementSymbol(), 1, Integer::sum);
        }
        StringBuilder formula = new StringBuilder();
        appendElement(formula, counts, "C");
        appendElement(formula, counts, "H");
        for (String element : java.util.List.copyOf(counts.keySet())) {
            appendElement(formula, counts, element);
        }
        return formula.length() > 0 ? formula.toString() : "unknown";
    }

    private static void appendElement(
            StringBuilder formula, Map<String, Integer> counts, String element) {
        Integer count = counts.remove(element);
        if (count == null) {
            return;
        }
        formula.append(element);
        if (count > 1) {
            formula.append(count);
        }
    }
}
