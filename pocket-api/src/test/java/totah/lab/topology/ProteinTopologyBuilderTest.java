package totah.lab.topology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.biojava.nbio.structure.io.PDBFileReader;
import totah.lab.protein.Atom;
import totah.lab.protein.Residue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProteinTopologyBuilder converts BioJava groups into domain residues and
 * stamps each atom with the charge/amber type from the Amber template.
 * The fixture is a synthetic ALA-LYS dipeptide with one atom (XX) that does
 * not exist in the LYS template.
 */
public class ProteinTopologyBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    public void buildsResiduesWithTemplateChargesAndTypes() throws Exception {
        Path pdb = writePeptidePdb();
        org.biojava.nbio.structure.Structure structure =
                new PDBFileReader().getStructure(pdb.toFile());

        ProteinTopologyBuilder builder =
                new ProteinTopologyBuilder(AmberResidueTemplateLibrary.getInstance());
        List<Residue> residues = builder.build(structure);

        assertEquals(2, residues.size(), "expected ALA + LYS");

        Residue ala = residues.get(0);
        assertEquals("ALA", ala.getName(), "first residue name");
        assertEquals(1, ala.getNumber(), "first residue number");
        assertEquals("A", ala.getChain(), "first residue chain");
        assertEquals(List.of("N", "CA", "C", "O", "CB"),
                ala.getAtoms().stream().map(Atom::getName).toList(),
                "ALA atoms must keep PDB order");

        Residue lys = residues.get(1);
        assertEquals("LYS", lys.getName(), "second residue name");
        assertEquals(2, lys.getNumber(), "second residue number");

        // Template charge + amber type are stamped onto matching atoms
        Atom lysN = lys.getAtom("N");
        assertEquals(-0.3479, lysN.getCharge(), 1e-4, "LYS N charge from template");
        assertEquals("N", lysN.getAmberType(), "LYS N amber type from template");
        assertEquals(-0.5894, lys.getAtom("O").getCharge(), 1e-4,
                "LYS O charge from template");

        // Coordinates survive the conversion
        Atom alaCa = ala.getAtom("CA");
        assertEquals(1.460, alaCa.getPosition().x(), 1e-6, "ALA CA x");
        assertEquals(0.000, alaCa.getPosition().y(), 1e-6, "ALA CA y");
        assertEquals(0.000, alaCa.getPosition().z(), 1e-6, "ALA CA z");

        // Elements come from the PDB element column
        assertEquals("N", lysN.getElement().getSymbol(), "LYS N element");
        assertEquals("C", alaCa.getElement().getSymbol(), "ALA CA element");
    }

    @Test
    public void atomsMissingFromTemplateGetZeroChargeAndNullType() throws Exception {
        Path pdb = writePeptidePdb();
        org.biojava.nbio.structure.Structure structure =
                new PDBFileReader().getStructure(pdb.toFile());

        ProteinTopologyBuilder builder =
                new ProteinTopologyBuilder(AmberResidueTemplateLibrary.getInstance());
        Residue lys = builder.build(structure).get(1);

        Atom xx = lys.getAtom("XX");
        assertNotNull(xx, "unknown atom XX should survive the conversion");
        assertEquals(0.0, xx.getCharge(), 1e-12,
                "atoms missing from the template must default to zero charge");
        assertNull(xx.getAmberType(),
                "atoms missing from the template must not get an amber type");
    }

    /** Synthetic ALA(1)-LYS(2) peptide; LYS carries an extra unknown atom XX. */
    private Path writePeptidePdb() throws Exception {
        StringBuilder pdb = new StringBuilder();
        int serial = 1;
        serial = appendResidue(pdb, serial, "ALA", 1, new String[][]{
                {"N", "0.000", "0.000", "0.000", "N"},
                {"CA", "1.460", "0.000", "0.000", "C"},
                {"C", "2.005", "1.419", "0.000", "C"},
                {"O", "1.230", "2.375", "0.000", "O"},
                {"CB", "1.925", "-1.468", "0.000", "C"}});
        serial = appendResidue(pdb, serial, "LYS", 2, new String[][]{
                {"N", "3.319", "1.628", "0.000", "N"},
                {"CA", "4.259", "0.510", "0.000", "C"},
                {"C", "3.523", "-0.822", "0.000", "C"},
                {"O", "4.159", "-1.876", "0.000", "O"},
                {"CB", "5.716", "1.015", "0.000", "C"},
                {"XX", "7.000", "2.500", "0.000", "C"}});
        pdb.append("TER\nEND\n");

        Path pdbFile = tempDir.resolve("dipeptide.pdb");
        Files.writeString(pdbFile, pdb.toString());
        return pdbFile;
    }

    private static int appendResidue(StringBuilder pdb, int serial, String resName,
                                     int resSeq, String[][] atoms) {
        for (String[] atom : atoms) {
            pdb.append(String.format(Locale.US,
                    "ATOM  %5d %-4s %3s %1s%4d    %8s%8s%8s%6.2f%6.2f          %2s%n",
                    serial, atom[0], resName, "A", resSeq,
                    atom[1], atom[2], atom[3], 1.0, 0.0, atom[4]));
            serial++;
        }
        return serial;
    }
}
