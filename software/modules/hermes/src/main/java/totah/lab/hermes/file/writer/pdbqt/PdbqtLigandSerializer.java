package totah.lab.hermes.file.writer.pdbqt;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Serializes an already prepared ligand; it performs no chemistry decisions. */
public final class PdbqtLigandSerializer {
    private final PdbqtAtomFormatter formatter = new PdbqtAtomFormatter();

    public void write(PdbqtLigandInput input, Path output) throws IOException {
        Objects.requireNonNull(output, "output");
        try (BufferedWriter writer = Files.newBufferedWriter(
                output, StandardCharsets.US_ASCII)) {
            write(input, writer);
        }
    }

    public void write(PdbqtLigandInput input, Writer writer) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(writer, "writer");
        Validated value = validate(input);
        Map<Integer, Integer> serials = serials(value.orderedFragments());
        PdbqtLigandFragmentInput root = value.fragments().get(input.rootFragmentId());
        writer.write("ROOT" + System.lineSeparator());
        writeAtoms(root, value.atoms(), serials, writer);
        writer.write("ENDROOT" + System.lineSeparator());
        writeChildren(root.id(), value, serials, writer);
        writer.write("TORSDOF " + input.torsionalDegreesOfFreedom() + System.lineSeparator());
        writer.flush();
    }

    private void writeChildren(String parentId, Validated value,
                               Map<Integer, Integer> serials, Writer writer) throws IOException {
        for (PdbqtLigandFragmentInput child : value.children().getOrDefault(parentId, List.of())) {
            int parentSerial = serials.get(child.parentAtomIndex());
            int childSerial = serials.get(child.childAtomIndex());
            writer.write("BRANCH " + parentSerial + " " + childSerial + System.lineSeparator());
            writeAtoms(child, value.atoms(), serials, writer);
            writeChildren(child.id(), value, serials, writer);
            writer.write("ENDBRANCH " + parentSerial + " " + childSerial
                    + System.lineSeparator());
        }
    }

    private void writeAtoms(PdbqtLigandFragmentInput fragment,
                            Map<Integer, PdbqtAtomInput> atoms,
                            Map<Integer, Integer> serials, Writer writer) throws IOException {
        for (int index : fragment.canonicalAtomIndices()) {
            PdbqtAtomInput atom = atoms.get(index);
            writer.write(formatter.format(serials.get(index), atom.atomName(), atom.residueName(),
                    atom.chainId(), atom.residueNumber(), atom.insertionCode(), atom.coordinates(),
                    atom.occupancy(), atom.bFactor(), atom.charge(), atom.ad4Type()));
        }
    }

    private Map<Integer, Integer> serials(List<PdbqtLigandFragmentInput> fragments) {
        Map<Integer, Integer> result = new HashMap<>();
        int serial = 1;
        for (PdbqtLigandFragmentInput fragment : fragments) {
            for (int index : fragment.canonicalAtomIndices()) result.put(index, serial++);
        }
        return result;
    }

    private Validated validate(PdbqtLigandInput input) {
        Map<Integer, PdbqtAtomInput> atoms = new LinkedHashMap<>();
        input.atoms().forEach(atom -> {
            if (atoms.putIfAbsent(atom.canonicalAtomIndex(), atom) != null)
                throw new IllegalArgumentException("Duplicate ligand atom index.");
        });
        Map<String, PdbqtLigandFragmentInput> fragments = new LinkedHashMap<>();
        Map<String, List<PdbqtLigandFragmentInput>> mutableChildren = new LinkedHashMap<>();
        Set<Integer> covered = new HashSet<>();
        for (PdbqtLigandFragmentInput fragment : input.fragments()) {
            if (fragments.putIfAbsent(fragment.id(), fragment) != null)
                throw new IllegalArgumentException("Duplicate ligand fragment id.");
            for (int index : fragment.canonicalAtomIndices()) {
                if (!atoms.containsKey(index) || !covered.add(index))
                    throw new IllegalArgumentException("Missing or duplicate fragment atom.");
            }
            if (fragment.parentFragmentId() != null) {
                mutableChildren.computeIfAbsent(fragment.parentFragmentId(), ignored -> new ArrayList<>())
                        .add(fragment);
            }
        }
        if (!fragments.containsKey(input.rootFragmentId()) || covered.size() != atoms.size())
            throw new IllegalArgumentException("Ligand fragment coverage is incomplete.");
        PdbqtLigandFragmentInput root = fragments.get(input.rootFragmentId());
        if (root.parentFragmentId() != null)
            throw new IllegalArgumentException("Root fragment has a parent.");
        for (PdbqtLigandFragmentInput fragment : input.fragments()) {
            if (fragment == root) continue;
            if (!fragments.containsKey(fragment.parentFragmentId())
                    || fragment.parentAtomIndex() == null || fragment.childAtomIndex() == null
                    || !fragments.get(fragment.parentFragmentId()).canonicalAtomIndices()
                    .contains(fragment.parentAtomIndex())
                    || !fragment.canonicalAtomIndices().contains(fragment.childAtomIndex())) {
                throw new IllegalArgumentException("Invalid ligand branch endpoints.");
            }
        }
        List<PdbqtLigandFragmentInput> ordered = new ArrayList<>();
        collect(root, mutableChildren, new HashSet<>(), ordered);
        if (ordered.size() != fragments.size()) throw new IllegalArgumentException("Fragment tree is cyclic or disconnected.");
        return new Validated(atoms, fragments,
                mutableChildren.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))), List.copyOf(ordered));
    }

    private void collect(PdbqtLigandFragmentInput fragment,
                         Map<String, List<PdbqtLigandFragmentInput>> children,
                         Set<String> visited, List<PdbqtLigandFragmentInput> ordered) {
        if (!visited.add(fragment.id())) throw new IllegalArgumentException("Fragment tree is cyclic.");
        ordered.add(fragment);
        for (var child : children.getOrDefault(fragment.id(), List.of())) collect(child, children, visited, ordered);
    }

    private record Validated(Map<Integer, PdbqtAtomInput> atoms,
                             Map<String, PdbqtLigandFragmentInput> fragments,
                             Map<String, List<PdbqtLigandFragmentInput>> children,
                             List<PdbqtLigandFragmentInput> orderedFragments) {}
}
