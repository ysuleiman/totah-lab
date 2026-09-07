package totah.lab.hermes.file.pdbqt;

import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import org.junit.jupiter.api.Test;
import totah.lab.hermes.file.pdbqt.meeko.MeekoResult;
import totah.lab.hermes.file.pdbqt.meeko.MeekoResultParser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class PdbqtReaderTest {

    @Test
    void emitsEveryModelAcrossBoundaryVariants() throws Exception {
        for (int count : new int[]{1, 8, 9}) {
            assertEquals(count, new PdbqtReader().read(new StringReader(models(count, true))).models().size());
            assertEquals(count, new PdbqtReader().read(new StringReader(models(count, false))).models().size());
        }
    }

    @Test
    void preservesDistinctModelsWithDuplicateScoresAndIdenticalAtoms() throws Exception {
        PdbqtFile file = new PdbqtReader().read(new StringReader(models(9, true)));
        assertEquals(9, file.models().size());
        assertEquals(9, file.models().stream().map(PdbqtModel::modelNumber).distinct().count());
    }

    @Test
    void commitsFinalImplicitModelAtEofWithOrWithoutTrailingNewline() throws Exception {
        String atom = atomLine(1);
        assertEquals(1, new PdbqtReader().read(new StringReader(atom)).models().size());
        assertEquals(1, new PdbqtReader().read(new StringReader(atom + "\n")).models().size());
    }

    private static String models(int count, boolean trailingNewline) {
        StringBuilder out = new StringBuilder();
        for (int model = 1; model <= count; model++) {
            out.append("MODEL ").append(model).append('\n')
                    .append("REMARK VINA RESULT: -6.000 0.000 0.000\n")
                    .append(atomLine(model)).append('\n')
                    .append("ENDMDL");
            if (model < count || trailingNewline) out.append('\n');
        }
        return out.toString();
    }

    private static String atomLine(int serial) {
        return String.format("ATOM  %5d  C   LIG L   1       0.000   0.000   0.000  0.00  0.00    +0.000 C", serial);
    }

    @Test
    void readVinaOutput() throws Exception {
        Path path = Paths.get(
                getClass()
                        .getResource("/vina/dcmb-diffdock-vina_out.pdbqt")
                        .toURI()
        );
        PdbqtFile file = new PdbqtReader().read(path);
        assertFalse(file.models().isEmpty());
        MeekoResultParser parser = new MeekoResultParser();
        for (PdbqtModel model : file.models()) {
            assertFalse(model.atoms().isEmpty());

            MeekoResult result = parser
                    .parse(model.remarks())
                    .orElseThrow();
            assertFalse(result.smiles().isBlank());
            assertFalse(result.smilesIndices().isEmpty());
            assertFalse(result.hydrogenParents().isEmpty());
        }
    }
}
