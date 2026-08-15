package totah.lab.prometheus.execution.quantum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.execution.EvidenceExecutionException;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.ingest.authoritative.CartesianGeometry;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;

class QuantumExecutionArchitectureTest {

    @Test
    void capabilitySelectionUsesInjectedBackendsNotAnEnumRegistry() throws Exception {
        QuantumBackend energyOnly = backend("energy", Set.of(QuantumObservable.ABSOLUTE_ENERGY));
        QuantumBackend energyForce = backend("energy-force", QuantumExecutionRequest.energyAndForces());
        QuantumExecutionRequest request = request(List.of("energy-force"));

        QuantumBackend selected = new QuantumBackendSelector(List.of(energyOnly, energyForce)).select(request);

        assertThat(selected.backendId()).isEqualTo("energy-force");
    }

    @Test
    void requestAndResultDefensivelyCopyMutableCollections() {
        List<String> preferences = new ArrayList<>(List.of("java-native"));
        QuantumExecutionRequest request = request(preferences);
        preferences.add("later-mutation");

        Map<String, String> checksums = new HashMap<>();
        checksums.put("raw", "a".repeat(64));
        QuantumResult result = result(request, checksums);
        checksums.put("mutated", "b".repeat(64));

        assertThat(request.options().preferredBackendIds()).containsExactly("java-native");
        assertThat(result.artifactChecksums()).containsOnlyKeys("raw");
        assertThatThrownBy(() -> result.artifactChecksums().put("x", "c".repeat(64)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void executionServiceRejectsResultForDifferentScientificIdentity() {
        QuantumExecutionRequest request = request(List.of("bad"));
        QuantumBackend bad = new QuantumBackend() {
            @Override public String backendId() { return "bad"; }
            @Override public QuantumBackendCapabilities capabilities() {
                return QuantumExecutionArchitectureTest.capabilities(QuantumExecutionRequest.energyAndForces());
            }
            @Override public QuantumResult execute(QuantumExecutionRequest ignored) {
                return new QuantumResult("f".repeat(64), backendId(), "1", ConvergenceStatus.CONVERGED,
                        Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), Map.of(), Instant.EPOCH);
            }
        };

        assertThatThrownBy(() -> new QuantumExecutionService(
                new QuantumBackendSelector(List.of(bad))).execute(request))
                .isInstanceOf(EvidenceExecutionException.class)
                .hasMessageContaining("different scientific identity");
    }

    @Test
    void resultChecksForceEqualsNegativeGradient() {
        QuantumExecutionRequest request = request(List.of());
        QuantumResult result = result(request, Map.of());

        assertThat(result.forceIsNegativeGradient(0.0)).isTrue();
    }

    private static QuantumBackend backend(String id, Set<QuantumObservable> observables) {
        return new QuantumBackend() {
            @Override public String backendId() { return id; }
            @Override public QuantumBackendCapabilities capabilities() {
                return QuantumExecutionArchitectureTest.capabilities(observables);
            }
            @Override public QuantumResult execute(QuantumExecutionRequest request) { return result(request, Map.of()); }
        };
    }

    private static QuantumBackendCapabilities capabilities(Set<QuantumObservable> observables) {
        return new QuantumBackendCapabilities(Set.of(QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE),
                Set.of(CalculationType.FORCE_EVALUATION), observables, false);
    }

    private static QuantumExecutionRequest request(List<String> preferences) {
        var atoms = TslFixtures.canonicalMap().atoms().stream()
                .map(atom -> new CartesianGeometry.Atom(
                        atom.elementSymbol(), atom.canonicalIndex(), 0.0, 0.0)).toList();
        var geometry = new CartesianGeometry(atoms, "angstrom");
        CalculationSpecification specification = new CalculationSpecification("quantum-request", "test",
                TslFixtures.TSL, TslFixtures.geometryIdentityA(), 0, 1,
                new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "gas", false, "java-native", "1"),
                List.of(), CalculationType.FORCE_EVALUATION,
                List.of("absolute energy", "Cartesian gradient", "Cartesian force"),
                List.of("converged"), DatasetRole.DEVELOPMENT, CostEstimate.zero());
        return new QuantumExecutionRequest(specification, geometry, "d".repeat(64),
                QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                QuantumExecutionRequest.energyAndForces(),
                new QuantumExecutionOptions(2, 1024, Path.of("target/test-quantum"), preferences, Optional.empty()));
    }

    private static QuantumResult result(QuantumExecutionRequest request, Map<String, String> checksums) {
        var gradient = new QuantumResult.CartesianField(
                List.of(new QuantumResult.Vector3(1.0, -2.0, 3.0)),
                QuantumResult.CartesianUnit.HARTREE_PER_BOHR);
        var force = new QuantumResult.CartesianField(
                List.of(new QuantumResult.Vector3(-1.0, 2.0, -3.0)),
                QuantumResult.CartesianUnit.HARTREE_PER_BOHR);
        return new QuantumResult(request.scientificIdentity(), "java-native", "1",
                ConvergenceStatus.CONVERGED,
                Optional.of(new QuantumResult.Energy(-1.0, QuantumResult.EnergyUnit.HARTREE)),
                Optional.of(gradient), Optional.of(force), checksums, Map.of("threads", "2"), Instant.EPOCH);
    }
}
