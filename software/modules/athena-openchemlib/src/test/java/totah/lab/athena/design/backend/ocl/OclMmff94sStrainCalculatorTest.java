package totah.lab.athena.design.backend.ocl;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.ConformerMinimizer;
import totah.lab.athena.design.backend.BackendEvidence;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OclMmff94sStrainCalculatorTest {
    @Test void minimizesReferenceWithFrozenProtocol() throws Exception {
        MolecularGraph graph = ethane(1.54);
        var result = new OclMmff94sStrainCalculator().calculate(graph, graph);
        assertThat(result.definitionId()).isEqualTo(OclMmff94sStrainCalculator.DEFINITION_ID);
        assertThat(result.backendVersion()).isEqualTo("2026.7.2");
        assertThat(result.referenceMaximumIterations()).isEqualTo(2000);
        assertThat(result.referenceConverged()).isTrue();
        assertThat(result.strainKcalMol()).isFinite().isGreaterThanOrEqualTo(0);
    }

    @Test void rejectsNonConvergedReferenceInsteadOfPublishingStrain() {
        ConformerMinimizer unfinished = (graph, configuration) -> new ConformerMinimizer.Result(
                graph, false, -12.0, new BackendEvidence("fixture", "1", "minimize",
                Map.of(), List.of(), List.of("iteration limit")));
        assertThatThrownBy(() -> new OclMmff94sStrainCalculator(unfinished)
                .calculate(ethane(1.54), ethane(1.54)))
                .isInstanceOf(MolecularBackendException.class)
                .hasMessageContaining("strain calculation failed")
                .hasRootCauseMessage("MMFF94S reference minimization did not converge; strain is unavailable");
    }

    @Test void rejectsChangedAtomIdentity() {
        MolecularGraph changed = new MolecularGraph(List.of(atom("a", "C", 0), atom("b", "N", 1.54)),
                List.of(bond()), Map.of());
        assertThatThrownBy(() -> new OclMmff94sStrainCalculator().calculate(ethane(1.54), changed))
                .isInstanceOf(MolecularBackendException.class).hasMessageContaining("atom identity");
    }

    private static MolecularGraph ethane(double distance) {
        return new MolecularGraph(List.of(atom("a", "C", 0), atom("b", "C", distance)),
                List.of(bond()), Map.of());
    }
    private static MolecularGraph.Atom atom(String id, String element, double x) {
        return new MolecularGraph.Atom(id, element, null, 0, element.equals("C") ? 3 : 0,
                false, "NONE", new MolecularGraph.Coordinates(x, 0, 0), Map.of());
    }
    private static MolecularGraph.Bond bond() {
        return new MolecularGraph.Bond("ab", "a", "b", MolecularGraph.BondOrder.SINGLE,
                false, "NONE", Map.of());
    }
}
