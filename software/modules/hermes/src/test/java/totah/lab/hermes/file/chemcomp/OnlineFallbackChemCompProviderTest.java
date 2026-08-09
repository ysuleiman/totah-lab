package totah.lab.hermes.file.chemcomp;
import org.biojava.nbio.structure.chem.ChemComp;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class OnlineFallbackChemCompProviderTest {

    @Test
    void usesReducedEntryWithoutCallingOnlineProvider() {
        ChemComp localEntry = chemComp("ALA");
        AtomicInteger onlineCalls = new AtomicInteger();
        OnlineFallbackChemCompProvider provider =
                new OnlineFallbackChemCompProvider(
                        componentId -> localEntry,
                        componentId -> {
                            onlineCalls.incrementAndGet();
                            return chemComp(componentId);
                        });

        assertSame(localEntry, provider.getChemComp("ALA"));
        assertEquals(0, onlineCalls.get());
    }

    @Test
    void callsOnlineProviderWhenReducedEntryIsEmpty() {
        ChemComp onlineEntry = chemComp("QWE");
        AtomicInteger onlineCalls = new AtomicInteger();
        OnlineFallbackChemCompProvider provider =
                new OnlineFallbackChemCompProvider(
                        componentId -> ChemComp.getEmptyChemComp(),
                        componentId -> {
                            onlineCalls.incrementAndGet();
                            return onlineEntry;
                        });

        assertSame(onlineEntry, provider.getChemComp("QWE"));
        assertEquals(1, onlineCalls.get());
    }

    private ChemComp chemComp(String componentId) {
        ChemComp chemComp = new ChemComp();
        chemComp.setId(componentId);
        chemComp.setThreeLetterCode(componentId);
        return chemComp;
    }
}
