package totah.lab.pipeline.cleanup;

import org.biojava.nbio.structure.chem.ChemCompGroupFactory;
import org.biojava.nbio.structure.chem.ReducedChemCompProvider;

public final class BioJavaChemCompConfiguration {

    private BioJavaChemCompConfiguration() {
    }

    public static void configureReducedProvider() {
        ChemCompGroupFactory.setChemCompProvider(
                new ReducedChemCompProvider());
        ChemCompGroupFactory.clearCache();
    }
}
