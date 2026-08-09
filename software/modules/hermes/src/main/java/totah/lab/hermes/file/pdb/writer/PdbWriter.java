package totah.lab.hermes.file.pdb.writer;

import totah.lab.hermes.file.pdb.PdbWriteOptions;
import totah.lab.hermes.file.pdb.PdbWriteResult;
import totah.lab.hermes.file.pdb.internal.PdbAtomFormatter;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Writes a gaia {@link Structure} as a standard fixed-column PDB file:
 * one ATOM record per atom (atom serials renumbered sequentially from
 * 1), a TER record after every chain and a trailing END record under
 * the default options. No charge or AutoDock columns — for PDBQT use
 * {@code totah.lab.hermes.file.pdbqt.writer.PdbqtWriter}.
 */
public final class PdbWriter {

    private final PdbAtomFormatter formatter = new PdbAtomFormatter();

    public PdbWriteResult write(
            Structure structure,
            Path output,
            PdbWriteOptions options) throws IOException {
        return write(structure, output, options, Set.of());
    }

    /**
     * Writes a structure while emitting selected residues as HETATM records.
     */
    public PdbWriteResult write(
            Structure structure,
            Path output,
            PdbWriteOptions options,
            Set<ResidueId> heteroResidues) throws IOException {
        Objects.requireNonNull(output, "output");
        Path normalized = output.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                normalized, StandardCharsets.UTF_8)) {
            PdbWriteResult result = write(
                    structure, writer, options, heteroResidues);
            return new PdbWriteResult(normalized, result.atomCount());
        }
    }

    public PdbWriteResult write(
            Structure structure,
            Writer writer,
            PdbWriteOptions options) throws IOException {
        return write(structure, writer, options, Set.of());
    }

    /**
     * Writes a structure while emitting selected residues as HETATM records.
     */
    public PdbWriteResult write(
            Structure structure,
            Writer writer,
            PdbWriteOptions options,
            Set<ResidueId> heteroResidues) throws IOException {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(writer, "writer");
        heteroResidues = Set.copyOf(Objects.requireNonNull(
                heteroResidues, "heteroResidues"));
        options = options == null ? PdbWriteOptions.defaults() : options;

        int serial = 1;
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId residueId = new ResidueId(
                        chain.id(),
                        residue.getNumber(),
                        residue.getInsertionCode());
                String recordName = heteroResidues.contains(residueId)
                        ? "HETATM"
                        : "ATOM";
                for (Atom atom : residue.getAtoms()) {
                    writer.write(formatter.format(
                            recordName,
                            serial++,
                            atom.getName(),
                            residue.getName(),
                            chain.id(),
                            residue.getNumber(),
                            residue.getInsertionCode(),
                            atom.getPosition(),
                            atom.getOccupancy(),
                            atom.getBFactor(),
                            atom.getElement() == null
                                    ? null
                                    : atom.getElement().symbol()));
                }
            }
            if (options.writeChainTerminators()) {
                writer.write("TER");
                writer.write(System.lineSeparator());
            }
        }
        if (options.writeEndRecord()) {
            writer.write("END");
            writer.write(System.lineSeparator());
        }
        writer.flush();
        return new PdbWriteResult(null, structure.getAtomCount());
    }
}
