package totah.lab.hermes.file.pdbqt.writer;

import totah.lab.hermes.file.pdbqt.*;
import totah.lab.hermes.file.pdbqt.internal.PdbqtAtomFormatter;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class PdbqtWriter {
    private final PdbqtAtomFormatter formatter = new PdbqtAtomFormatter();

    public PdbqtWriteResult write(
            PdbqtFile file,
            Path output,
            PdbqtWriteOptions options) throws IOException {
        Objects.requireNonNull(output, "output");
        Path normalized = output.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                normalized, StandardCharsets.UTF_8)) {
            PdbqtWriteResult result = write(file, writer, options);
            return new PdbqtWriteResult(normalized, null,
                    result.rigidAtomCount(), 0, 0, result.torsionCount());
        }
    }

    public PdbqtWriteResult write(
            PdbqtFile file,
            Writer writer,
            PdbqtWriteOptions options) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(writer, "writer");
        options = options == null ? PdbqtWriteOptions.defaults() : options;
        boolean multipleModels = file.models().size() > 1;
        int atomCount = 0;
        int torsionCount = 0;
        for (PdbqtModel model : file.models()) {
            if (multipleModels) {
                writer.write("MODEL " + model.modelNumber() + System.lineSeparator());
            }
            for (String remark : model.remarks()) {
                writer.write(remark);
                writer.write(System.lineSeparator());
            }
            PdbqtTorsionTree tree = model.torsionTree();
            if (tree != null && (!tree.rootAtoms().isEmpty()
                    || !tree.branches().isEmpty() || tree.torsdof() != null)) {
                writer.write("ROOT" + System.lineSeparator());
                for (PdbqtAtom atom : tree.rootAtoms()) {
                    writeAtom(atom, writer);
                    atomCount++;
                }
                writer.write("ENDROOT" + System.lineSeparator());
                for (PdbqtBranch branch : tree.branches()) {
                    atomCount += writeBranch(branch, writer);
                }
                if (tree.torsdof() != null) {
                    writer.write("TORSDOF " + tree.torsdof() + System.lineSeparator());
                    torsionCount += tree.torsdof();
                }
            } else {
                String previousChain = null;
                for (PdbqtAtom atom : model.atoms()) {
                    if (options.writeChainTerminators() && previousChain != null
                            && !Objects.equals(previousChain, atom.chainId())) {
                        writer.write("TER" + System.lineSeparator());
                    }
                    writeAtom(atom, writer);
                    previousChain = atom.chainId();
                    atomCount++;
                }
            }
            if (multipleModels) {
                writer.write("ENDMDL" + System.lineSeparator());
            }
        }
        if (options.writeEndRecord()) {
            writer.write("END" + System.lineSeparator());
        }
        writer.flush();
        return new PdbqtWriteResult(null, null, atomCount, 0, 0, torsionCount);
    }

    private int writeBranch(PdbqtBranch branch, Writer writer) throws IOException {
        writer.write("BRANCH " + branch.parentAtom() + " " + branch.childAtom()
                + System.lineSeparator());
        int count = 0;
        for (PdbqtAtom atom : branch.atoms()) {
            writeAtom(atom, writer);
            count++;
        }
        for (PdbqtBranch child : branch.children()) {
            count += writeBranch(child, writer);
        }
        writer.write("ENDBRANCH " + branch.parentAtom() + " " + branch.childAtom()
                + System.lineSeparator());
        return count;
    }

    private void writeAtom(PdbqtAtom atom, Writer writer) throws IOException {
        if (!Double.isFinite(atom.partialCharge())) {
            throw new IllegalArgumentException(
                    "Non-finite charge on PDBQT atom " + atom.serial());
        }
        if (!PdbqtTypes.isSupported(atom.autodockType())) {
            throw new IllegalArgumentException(
                    "Missing or illegal AutoDock4 type on PDBQT atom " + atom.serial());
        }
        writer.write(formatter.format(atom));
    }

    public PdbqtWriteResult write(
            Structure structure,
            Path output,
            PdbqtWriteOptions options) throws IOException {
        Objects.requireNonNull(output, "output");
        Path normalized = output.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                normalized, StandardCharsets.UTF_8)) {
            PdbqtWriteResult result = write(structure, writer, options);
            return new PdbqtWriteResult(
                    normalized,
                    null,
                    result.rigidAtomCount(),
                    0,
                    0,
                    0);
        }
    }

    public PdbqtWriteResult write(
            Structure structure,
            Writer writer,
            PdbqtWriteOptions options) throws IOException {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(writer, "writer");
        options = options == null ? PdbqtWriteOptions.defaults() : options;

        int serial = 1;
        int chainIndex = 0;
        for (Chain chain : structure.getChains()) {
            if (chainIndex > 0 && options.writeChainTerminators()) {
                writer.write("TER");
                writer.write(System.lineSeparator());
            }
            for (Residue residue : chain.residues()) {
                for (Atom atom : residue.getAtoms()) {
                    validate(chain.id(), residue, atom);
                    writer.write(formatter.format(serial++,atom.getName(),residue.getName(),chain.id(),
                            residue.getNumber(),residue.getInsertionCode(),atom.getPosition(),
                            atom.getOccupancy(),atom.getBFactor(),atom.getCharge(),atom.getAutoDockType()));
                }
            }
            chainIndex++;
        }
        if (options.writeEndRecord()) {
            writer.write("END");
            writer.write(System.lineSeparator());
        }
        writer.flush();
        return new PdbqtWriteResult(
                null,
                null,
                structure.getAtomCount(),
                0,
                0,
                0);
    }

    private void validate(String chainId, Residue residue, Atom atom) {
        if (!Double.isFinite(atom.getCharge())) {
            throw new IllegalArgumentException(
                    "Non-finite charge on " + atom.getName() + " in "
                            + label(chainId, residue));
        }
        String type = atom.getAutoDockType();
        if (!PdbqtTypes.isSupported(type)) {
            throw new IllegalArgumentException(
                    "Missing or illegal AutoDock4 type on "
                            + atom.getName() + " in "
                            + label(chainId, residue));
        }
    }

    private String label(String chainId, Residue residue) {
        return residue.getName() + " " + chainId + ":"
                + residue.getNumber()
                + (residue.getInsertionCode() == null
                ? ""
                : residue.getInsertionCode());
    }
}
