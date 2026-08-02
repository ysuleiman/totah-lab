package totah.lab.hermes.file.reader;


import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompProvider;

import java.util.Objects;

final class OnlineFallbackChemCompProvider implements ChemCompProvider {

    private final ChemCompProvider localProvider;
    private final ChemCompProvider onlineProvider;

    OnlineFallbackChemCompProvider(
            ChemCompProvider localProvider,
            ChemCompProvider onlineProvider) {
        this.localProvider = Objects.requireNonNull(localProvider, "localProvider is null");
        this.onlineProvider = Objects.requireNonNull(onlineProvider, "onlineProvider is null");
    }

    @Override
    public ChemComp getChemComp(String componentId) {
        ChemComp local = localProvider.getChemComp(componentId);
        if (local != null && !local.isEmpty()) {
            return local;
        }
        return onlineProvider.getChemComp(componentId);
    }
}
