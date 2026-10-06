package totah.lab.daedalus.system;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.RuleRegistry;
import totah.lab.mnemosyne.EvidenceInterpretation;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

/** Broader role perception must not silently widen the historical interaction domain. */
class HbondDomainBoundaryTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(doubles = {2.8, 20.0})
    void explicitAmineHydrogenDoesNotAuthorizePositiveOrNegativeInteraction(double separation)
            throws Exception {
        var single = MolecularGraph.BondOrder.SINGLE;
        var amine = new MolecularGraph(List.of(
                atom("N", "N", 0, false, 0, 0, 0),
                atom("H1", "H", 0, false, 1, 0, 0),
                atom("H2", "H", 0, false, 0, 1, 0),
                atom("C", "C", 0, false, -1.4, 0, 0)),
                List.of(bond("N", "H1", single), bond("N", "H2", single),
                        bond("N", "C", single)), Map.of());
        var state = system(List.of(amine, carbonyl(separation)), true, false);
        var before = SystemStateView.bytes(state.snapshot());
        var harness = new AthenaScientificRulesAcceptanceTest();
        harness.temp = directory;
        var manifest = manifest(RuleRegistry.scientific(), "HBOND");
        var result = harness.run(state, manifest, "amine-domain-boundary", 1000,
                false, Optional.empty());
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,
                harness.assessment(result).status());
        assertArrayEquals(before, SystemStateView.bytes(state.snapshot()));
    }
}
