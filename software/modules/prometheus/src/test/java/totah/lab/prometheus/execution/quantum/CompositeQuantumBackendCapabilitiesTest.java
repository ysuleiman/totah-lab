package totah.lab.prometheus.execution.quantum;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.ingest.authoritative.CartesianGeometry;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;

class CompositeQuantumBackendCapabilitiesTest {
    @Test
    void compositeHessianRequiresElectronicDispersionAndTotalCapabilities() {
        QuantumExecutionRequest request = request();
        QuantumBackendCapabilities backend = new QuantumBackendCapabilities(
                Set.of(QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE),
                Set.of(CalculationType.HESSIAN), Set.of(QuantumObservable.HESSIAN), false);
        var electronicOnly = new CompositeQuantumBackendCapabilities(backend, Set.of(
                new QuantumComponentCapability(
                        QuantumObservable.HESSIAN, QuantumObservableComponent.ELECTRONIC)));
        var complete = new CompositeQuantumBackendCapabilities(backend, Set.of(
                new QuantumComponentCapability(QuantumObservable.HESSIAN, QuantumObservableComponent.ELECTRONIC),
                new QuantumComponentCapability(QuantumObservable.HESSIAN, QuantumObservableComponent.DISPERSION),
                new QuantumComponentCapability(QuantumObservable.HESSIAN, QuantumObservableComponent.TOTAL)));

        assertThat(electronicOnly.satisfies(request)).isFalse();
        assertThat(complete.satisfies(request)).isTrue();
    }

    private static QuantumExecutionRequest request() {
        CartesianGeometry geometry = new CartesianGeometry(TslFixtures.canonicalMap().atoms().stream()
                .map(atom -> new CartesianGeometry.Atom(
                        atom.elementSymbol(), atom.canonicalIndex(), 0.0, 0.0))
                .toList(), "angstrom");
        CalculationSpecification specification = new CalculationSpecification(
                "component-capability", "component capability test", TslFixtures.TSL,
                TslFixtures.geometryIdentityA(), 0, 1,
                new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "gas", true, "pyscf", "2.14.0"),
                List.of(), CalculationType.HESSIAN, List.of("hessian"), List.of("complete"),
                DatasetRole.DEVELOPMENT, CostEstimate.zero());
        return new QuantumExecutionRequest(specification, geometry, "a".repeat(64),
                QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                Set.of(QuantumObservable.HESSIAN),
                QuantumExecutionOptions.local(Path.of("target/component-capability"), 1, 512));
    }
}
