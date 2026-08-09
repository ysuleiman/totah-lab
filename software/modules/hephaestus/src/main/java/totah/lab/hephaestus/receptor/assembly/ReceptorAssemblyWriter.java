package totah.lab.hephaestus.receptor.assembly;

import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.PdbWriteOptions;
import totah.lab.hermes.file.pdb.PdbWriteResult;
import totah.lab.hermes.file.pdb.writer.PdbWriter;
import totah.lab.hermes.file.pdbqt.PdbqtWriteOptions;
import totah.lab.hermes.file.pdbqt.PdbqtWriteResult;
import totah.lab.hermes.file.pdbqt.writer.PdbqtWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Writes combined receptor assemblies using the canonical Hermes formats. */
public final class ReceptorAssemblyWriter {

    private final ReceptorAssemblyStructureBuilder structureBuilder;
    private final PdbWriter pdbWriter;
    private final PdbqtWriter pdbqtWriter;

    public ReceptorAssemblyWriter() {
        this(
                new ReceptorAssemblyStructureBuilder(),
                new PdbWriter(),
                new PdbqtWriter());
    }

    ReceptorAssemblyWriter(
            ReceptorAssemblyStructureBuilder structureBuilder,
            PdbWriter pdbWriter,
            PdbqtWriter pdbqtWriter) {

        this.structureBuilder = Objects.requireNonNull(
                structureBuilder, "structureBuilder");
        this.pdbWriter = Objects.requireNonNull(pdbWriter, "pdbWriter");
        this.pdbqtWriter = Objects.requireNonNull(
                pdbqtWriter, "pdbqtWriter");
    }

    /** Writes a PDB view with fixed cofactors represented as HETATM. */
    public PdbWriteResult writePdb(
            ReceptorAssembly assembly,
            Path output) throws IOException {

        Objects.requireNonNull(assembly, "assembly");
        Structure structure = structureBuilder.build(assembly);
        return pdbWriter.write(
                structure,
                output,
                PdbWriteOptions.defaults(),
                cofactorResidues(assembly));
    }

    /**
     * Writes the rigid PDBQT receptor used for subsequent ligand docking.
     */
    public PdbqtWriteResult writeRigidPdbqt(
            ReceptorAssembly assembly,
            Path output) throws IOException {

        Objects.requireNonNull(assembly, "assembly");
        return pdbqtWriter.write(
                structureBuilder.build(assembly),
                output,
                PdbqtWriteOptions.defaults());
    }

    private static Set<ResidueId> cofactorResidues(
            ReceptorAssembly assembly) {

        Set<ResidueId> residues = new LinkedHashSet<>();
        for (FixedCofactor cofactor : assembly.fixedCofactors()) {
            Structure structure = cofactor.pose().preparedPose()
                    .ligand().structure();
            for (Chain chain : structure.getChains()) {
                for (Residue residue : chain.residues()) {
                    residues.add(new ResidueId(
                            chain.id(),
                            residue.getNumber(),
                            residue.getInsertionCode()));
                }
            }
        }
        return Set.copyOf(residues);
    }
}
