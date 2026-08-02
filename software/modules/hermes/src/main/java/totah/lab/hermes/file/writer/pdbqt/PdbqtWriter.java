package totah.lab.hermes.file.writer.pdbqt;

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
import java.util.Set;

public final class PdbqtWriter {
    private final PdbqtAtomFormatter formatter = new PdbqtAtomFormatter();

    private static final Set<String> LEGAL_AD4_TYPES = Set.of(
            "C", "A", "N", "NA", "O", "OA", "S", "SA", "P",
            "HD", "H", "F", "Cl", "Br", "I", "Mg", "Mn", "Fe",
            "Zn", "Ca");

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
        if (type == null || type.isBlank() || !LEGAL_AD4_TYPES.contains(type)) {
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
