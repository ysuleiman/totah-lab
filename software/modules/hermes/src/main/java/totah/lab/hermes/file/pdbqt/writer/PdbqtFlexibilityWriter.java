package totah.lab.hermes.file.pdbqt.writer;

import totah.lab.hermes.file.pdbqt.*;
import totah.lab.hermes.file.pdbqt.internal.PdbqtAtomFormatter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import totah.lab.hermes.file.pdbqt.validation.PdbqtSerializerValidator;
import totah.lab.hermes.file.pdbqt.validation.PdbqtValidationException;

public final class PdbqtFlexibilityWriter {
    private final PdbqtSerializerValidator validator = new PdbqtSerializerValidator();
    private final PdbqtAtomFormatter formatter = new PdbqtAtomFormatter();
    public PdbqtWriteResult write(
            PdbqtFlexibleReceptor input,
            Path rigidOutput,
            Path flexibleOutput) throws IOException {
        var validationReport=validator.validate(input);
        if(validationReport.hasErrors())throw new PdbqtValidationException(validationReport);
        Path rigid = normalize(rigidOutput);
        Path flexible = normalize(flexibleOutput);
        createParent(rigid); createParent(flexible);

        int flexibleAtoms = input.flexibleResidues().stream()
                .flatMap(residue -> residue.fragments().stream())
                .mapToInt(fragment -> fragment.atoms().size()).sum();
        int torsions = input.flexibleResidues().stream()
                .mapToInt(residue -> residue.rotatableBonds().size()).sum();

        // Stage both outputs in sibling temp files and move them into place only
        // after both serialize successfully, so a failure never leaves a partial pair.
        Path rigidTemp = null;
        Path flexibleTemp = null;
        try {
            rigidTemp = temporarySibling(rigid);
            flexibleTemp = temporarySibling(flexible);
            writeRigidAtoms(input, rigidTemp);
            writeFlexibleResidues(input, flexibleTemp);
            moveIntoPlace(rigidTemp, rigid); rigidTemp = null;
            moveIntoPlace(flexibleTemp, flexible); flexibleTemp = null;
        } finally {
            deleteQuietly(rigidTemp);
            deleteQuietly(flexibleTemp);
        }
        return new PdbqtWriteResult(rigid, flexible, input.rigidAtoms().size(), flexibleAtoms,
                input.flexibleResidues().size(), torsions);
    }

    private void writeRigidAtoms(PdbqtFlexibleReceptor input, Path rigid) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(rigid, StandardCharsets.UTF_8)) {
            int serial = 1;
            for (PdbqtRigidAtom rigidAtom : input.rigidAtoms()) {
                writer.write(format(rigidAtom.atom(), serial++));
            }
        }
    }

    private void writeFlexibleResidues(PdbqtFlexibleReceptor input, Path flexible) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(flexible, StandardCharsets.UTF_8)) {
            // Serials continue across residues so BRANCH references stay unique
            // file-wide, matching Meeko's adapt_pdbqt_for_autodock4_flexres.
            int nextSerial = 1;
            for (PdbqtFlexibleResidue residue : input.flexibleResidues()) {
                nextSerial = writeResidue(writer, residue, nextSerial);
            }
        }
    }

    private Path temporarySibling(Path target) throws IOException {
        return Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
    }

    private void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private int writeResidue(BufferedWriter writer, PdbqtFlexibleResidue residue, int firstSerial) throws IOException {
        Map<String, PdbqtFragment> fragments = new LinkedHashMap<>();
        for (PdbqtFragment fragment : residue.fragments()) {
            if (fragments.put(fragment.fragmentId(), fragment) != null) {
                throw new IllegalArgumentException("Duplicate fragment ID: " + fragment.fragmentId());
            }
        }
        PdbqtFragment root = residue.fragments().stream()
                .filter(fragment -> fragment.atoms().stream()
                        .anyMatch(atom -> atom.canonicalAtomIndex() == residue.anchorAtomIndex()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Anchor fragment is missing."));
        Map<String, List<PdbqtRotatableBond>> children = new HashMap<>();
        for (PdbqtRotatableBond bond : residue.rotatableBonds()) {
            if (!fragments.containsKey(bond.parentFragmentId()) || !fragments.containsKey(bond.childFragmentId())) {
                throw new IllegalArgumentException("Rotatable bond references an unknown fragment.");
            }
            children.computeIfAbsent(bond.parentFragmentId(), ignored -> new ArrayList<>()).add(bond);
        }

        writer.write("BEGIN_RES " + residue.residueName() + " " + residue.chainId() + " "
                + residue.residueNumber() + insertion(residue.insertionCode())); writer.newLine();
        Map<Integer, Integer> serialByIndex = assignSerials(residue, root, children, fragments, firstSerial);
        Set<String> visited = new HashSet<>();
        writer.write("ROOT"); writer.newLine();
        writeAtoms(writer, root.atoms(), serialByIndex);
        writer.write("ENDROOT"); writer.newLine();
        visited.add(root.fragmentId());
        writeChildren(writer, root.fragmentId(), children, fragments, serialByIndex, visited);
        if (visited.size() != fragments.size()) throw new IllegalArgumentException("Fragment graph is disconnected or cyclic.");
        writer.write("END_RES " + residue.residueName() + " " + residue.chainId() + " "
                + residue.residueNumber() + insertion(residue.insertionCode())); writer.newLine();
        return firstSerial + serialByIndex.size();
    }

    private Map<Integer, Integer> assignSerials(PdbqtFlexibleResidue residue,
            PdbqtFragment root, Map<String, List<PdbqtRotatableBond>> children,
            Map<String, PdbqtFragment> fragments, int firstSerial) {
        List<PdbqtAtomReference> order = new ArrayList<>();
        order.addAll(root.atoms());
        collectAtoms(root.fragmentId(), children, fragments, order, new HashSet<>());
        Map<Integer, Integer> result = new HashMap<>();
        for (int index = 0; index < order.size(); index++) {
            if (result.put(order.get(index).canonicalAtomIndex(), firstSerial + index) != null)
                throw new IllegalArgumentException("Flexible atom is duplicated.");
        }
        return result;
    }

    private void collectAtoms(String parent, Map<String,List<PdbqtRotatableBond>> children,
            Map<String,PdbqtFragment> fragments, List<PdbqtAtomReference> order, Set<String> visited) {
        if (!visited.add(parent)) throw new IllegalArgumentException("Fragment graph is cyclic.");
        for (PdbqtRotatableBond bond : children.getOrDefault(parent, List.of())) {
            PdbqtFragment child = fragments.get(bond.childFragmentId());
            order.addAll(child.atoms()); collectAtoms(child.fragmentId(), children, fragments, order, visited);
        }
    }

    private void writeChildren(BufferedWriter writer, String parent,
            Map<String,List<PdbqtRotatableBond>> children, Map<String,PdbqtFragment> fragments,
            Map<Integer,Integer> serials, Set<String> visited) throws IOException {
        for (PdbqtRotatableBond bond : children.getOrDefault(parent, List.of())) {
            if (!visited.add(bond.childFragmentId())) throw new IllegalArgumentException("Fragment graph is cyclic.");
            int parentSerial = requireSerial(serials, bond.parentAtomIndex());
            int childSerial = requireSerial(serials, bond.childAtomIndex());
            writer.write("BRANCH " + parentSerial + " " + childSerial); writer.newLine();
            PdbqtFragment child = fragments.get(bond.childFragmentId());
            writeAtoms(writer, child.atoms(), serials);
            writeChildren(writer, child.fragmentId(), children, fragments, serials, visited);
            writer.write("ENDBRANCH " + parentSerial + " " + childSerial); writer.newLine();
        }
    }

    private void writeAtoms(BufferedWriter writer, List<PdbqtAtomReference> atoms,
            Map<Integer,Integer> serials) throws IOException {
        for (PdbqtAtomReference atom : atoms) writer.write(format(atom,
                requireSerial(serials,atom.canonicalAtomIndex())));
    }

    private String format(PdbqtAtomReference atom,int serial){return formatter.format(
            serial,atom.atomName(),atom.residueName(),atom.chainId(),atom.residueNumber(),
            atom.insertionCode(),atom.coordinates(),atom.occupancy(),atom.bFactor(),
            atom.charge(),atom.ad4Type());}

    private void validatePartition(PdbqtFlexibleReceptor input) {
        Set<Integer> indices = new HashSet<>();
        for (PdbqtRigidAtom atom : input.rigidAtoms()) add(indices, atom.atom().canonicalAtomIndex());
        for (PdbqtFlexibleResidue residue : input.flexibleResidues())
            for (PdbqtFragment fragment : residue.fragments())
                for (PdbqtAtomReference atom : fragment.atoms()) add(indices, atom.canonicalAtomIndex());
        if (indices.size() != input.preparedAtomCount())
            throw new IllegalArgumentException("Rigid/flexible atoms do not exactly partition the prepared structure.");
        for (int i = 0; i < input.preparedAtomCount(); i++) if (!indices.contains(i))
            throw new IllegalArgumentException("Prepared atom index is missing: " + i);
    }
    private void add(Set<Integer> indices, int index) {
        if (!indices.add(index)) throw new IllegalArgumentException("Atom appears in both or multiple outputs: " + index);
    }
    private int requireSerial(Map<Integer,Integer> serials, int index) {
        Integer serial = serials.get(index); if (serial == null) throw new IllegalArgumentException("Unknown flexible atom index: " + index); return serial;
    }
    private String insertion(Character code) { return code == null ? " " : code.toString(); }
    private Path normalize(Path path) { return path.toAbsolutePath().normalize(); }
    private void createParent(Path path) throws IOException { if (path.getParent() != null) Files.createDirectories(path.getParent()); }
}
