package totah.lab.daedalus.ligandprep;

import org.junit.jupiter.api.Test;
import totah.lab.daedalus.ligandprep.PdbqtLigandReader.PdbqtLigand;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdbqtLigandReaderTest {

    @Test
    void readsAtomsChargesTypesAndTorsdof() throws Exception {
        PdbqtLigand ligand = PdbqtLigandReader.parse(List.of(
                "REMARK SMILES CC",
                "ROOT",
                atomLine(1, "C1", 0.0, 0.0, 0.0, 0.05, "C"),
                atomLine(2, "O1", 1.4, 0.0, 0.0, -0.3, "OA"),
                "ENDROOT",
                "BRANCH   1   3",
                atomLine(3, "H1", 0.0, 1.0, 0.0, 0.05, "HD"),
                "ENDBRANCH   1   3",
                "TORSDOF 2"
        ));

        assertEquals(3, ligand.atoms().size());
        assertEquals(2, ligand.torsdof());
        assertEquals(2, ligand.heavyAtoms().size());
        assertEquals("O", ligand.atoms().get(1).element());
        assertEquals("H", ligand.atoms().get(2).element());
        assertEquals(-0.3, ligand.atoms().get(1).charge(), 1e-9);
        assertEquals(1.4, ligand.atoms().get(1).position().x(), 1e-9);
        assertEquals(-0.2, ligand.totalCharge(), 1e-9);
    }

    @Test
    void mapsAd4TypesToElements() {
        assertEquals("C", PdbqtLigandReader.PdbqtAtom.elementOf("A"));
        assertEquals("N", PdbqtLigandReader.PdbqtAtom.elementOf("NA"));
        assertEquals("O", PdbqtLigandReader.PdbqtAtom.elementOf("OA"));
        assertEquals("S", PdbqtLigandReader.PdbqtAtom.elementOf("SA"));
        assertEquals("H", PdbqtLigandReader.PdbqtAtom.elementOf("HD"));
        assertEquals("Cl", PdbqtLigandReader.PdbqtAtom.elementOf("Cl"));
    }

    @Test
    void rejectsFilesWithoutAtoms() {
        assertThrows(java.io.IOException.class, () ->
                PdbqtLigandReader.parse(List.of("ROOT", "ENDROOT")));
    }

    static String atomLine(
            int serial,
            String name,
            double x,
            double y,
            double z,
            double charge,
            String ad4Type
    ) {
        return String.format(
                java.util.Locale.ROOT,
                "ATOM  %5d %-4s %3s %1s%4d    "
                        + "%8.3f%8.3f%8.3f%6.2f%6.2f    %6.3f %-2s",
                serial, name, "LIG", "L", 1,
                x, y, z, 1.0, 0.0, charge, ad4Type);
    }
}
