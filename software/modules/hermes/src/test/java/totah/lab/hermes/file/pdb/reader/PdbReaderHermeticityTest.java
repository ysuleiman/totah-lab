package totah.lab.hermes.file.pdb.reader;

import org.biojava.nbio.structure.chem.ChemCompGroupFactory;
import org.biojava.nbio.structure.chem.ReducedChemCompProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PdbReaderHermeticityTest {
    @Test
    void defaultReaderInitializesBioJavaWithOfflineProvider() {
        new PdbReader();
        assertInstanceOf(ReducedChemCompProvider.class,
                ChemCompGroupFactory.getChemCompProvider());
    }
}
