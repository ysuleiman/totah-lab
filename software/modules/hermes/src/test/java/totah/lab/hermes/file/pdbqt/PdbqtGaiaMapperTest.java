package totah.lab.hermes.file.pdbqt;

import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import org.junit.jupiter.api.Test;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Structure;

import java.io.StringReader;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdbqtGaiaMapperTest {

    @Test
    void mapsLigandModelToGaiaLigand() throws Exception {
        PdbqtModel model = new PdbqtReader().read(new StringReader(
                String.join("\n", List.of(
                        "ROOT",
                        atom(1, "C1", "UNL", "L", 1, 0, 0, 0, 0.05, "C"),
                        atom(2, "CL1", "UNL", "L", 1, 1.7, 0, 0, -0.1, "Cl"),
                        atom(3, "H1", "UNL", "L", 1, 0, 1.0, 0, 0.05, "HD"),
                        "ENDROOT",
                        "TORSDOF 1"
                )))).firstModel();

        Ligand ligand = PdbqtGaiaMapper.toLigand(model, "DCMB");

        List<totah.lab.gaia.structure.Atom> atoms =
                ligand.structure().getChains().getFirst()
                        .residues().getFirst().getAtoms();
        assertEquals("DCMB", ligand.id());
        assertEquals(3, atoms.size());
        assertEquals(2, ligand.structure().getChains().getFirst()
                .residues().getFirst().getHeavyAtomCount());
        assertEquals("Cl", atoms.get(1).getElement().symbol());
        assertEquals(1.7, atoms.get(1).getPosition().x(), 1e-9);
        assertEquals(-0.1, atoms.get(1).getCharge(), 1e-9);
        assertEquals("Cl", atoms.get(1).getAutoDockType());
    }

    @Test
    void mapsReceptorToStructureGroupedByResidue() throws Exception {
        PdbqtFile file = new PdbqtReader().read(new StringReader(
                String.join("\n", List.of(
                        atom(1, "N", "MET", "A", 1, 25, 14, -16, 0.0, "NA"),
                        atom(2, "CA", "MET", "A", 1, 24, 13.5, -16.3, 0.1, "C"),
                        atom(3, "CA", "ALA", "A", 2, 20, 12, -15, 0.0, "C"),
                        atom(4, "CA", "GLY", "B", 3, 10, 10, -10, 0.0, "C")
                ))));

        Structure structure = PdbqtGaiaMapper.toStructure(file);

        assertEquals(2, structure.getChains().size());
        assertEquals(2, structure.getChains().getFirst().residues().size());
        assertEquals("MET", structure.getChains().getFirst()
                .residues().getFirst().getName());
        assertEquals(2, structure.getChains().getFirst()
                .residues().getFirst().getAtomCount());
        assertTrue(structure.findResidue(
                new totah.lab.gaia.structure.ResidueId("A", 2, null))
                .isPresent());
    }

    private static String atom(
            int serial,
            String name,
            String residueName,
            String chain,
            int residueNumber,
            double x,
            double y,
            double z,
            double charge,
            String type
    ) {
        return String.format(
                Locale.ROOT,
                "ATOM  %5d %-4s %-3s %1s%4d    %8.3f%8.3f%8.3f"
                        + "  1.00  0.00    %+6.3f %-2s",
                serial, name, residueName, chain, residueNumber,
                x, y, z, charge, type
        );
    }
}
