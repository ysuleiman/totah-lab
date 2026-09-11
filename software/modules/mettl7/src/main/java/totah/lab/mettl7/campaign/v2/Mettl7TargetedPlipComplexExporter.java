package totah.lab.mettl7.campaign.v2;

import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.PdbWriteOptions;
import totah.lab.hermes.file.pdb.writer.PdbWriter;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Exports one checksum-validated V2 top pose for an external PLIP oracle run. */
public final class Mettl7TargetedPlipComplexExporter {
    private Mettl7TargetedPlipComplexExporter() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 4) throw new IllegalArgumentException(
                "Usage: <receptor.pdbqt> <poses.pdbqt> <frozen.sdf> <complex.pdb>");
        PdbqtReader pdbqt = new PdbqtReader();
        Structure receptor = PdbqtGaiaMapper.toStructure(pdbqt.read(Path.of(args[0])));
        var pose = pdbqt.read(Path.of(args[1])).models().getFirst();
        var frozen = new SdfLigandReader().readModel(Path.of(args[2]));
        Structure ligand = Mettl7FrozenPoseLigand.reconstruct(frozen, pose).structure();
        Residue source = ligand.getChains().getFirst().residues().getFirst();
        Residue namedLigand = new Residue("LIG", 999, source.getAtoms());
        List<Chain> chains = new ArrayList<>(receptor.getChains());
        chains.add(new Chain("Z", List.of(namedLigand)));
        Set<ResidueId> hetero = new LinkedHashSet<>();
        hetero.add(new ResidueId("Z", 999, null));
        receptor.getChains().forEach(chain -> chain.residues().stream()
                .filter(residue -> residue.getName().equalsIgnoreCase("SAM")
                        || residue.getName().equalsIgnoreCase("SAH"))
                .forEach(residue -> hetero.add(new ResidueId(chain.id(), residue.getNumber(),
                        residue.getInsertionCode()))));
        new PdbWriter().write(new Structure(chains), Path.of(args[3]),
                PdbWriteOptions.defaults(), hetero);
    }
}
