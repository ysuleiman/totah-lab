package totah.lab.hermes.file.pdbqt;

import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import org.junit.jupiter.api.Test;
import totah.lab.hermes.file.pdbqt.meeko.MeekoResult;
import totah.lab.hermes.file.pdbqt.meeko.MeekoResultParser;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class PdbqtReaderTest {

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
