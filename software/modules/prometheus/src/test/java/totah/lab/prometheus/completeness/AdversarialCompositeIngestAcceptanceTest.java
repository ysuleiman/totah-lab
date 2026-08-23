package totah.lab.prometheus.completeness;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.execution.quantum.QuantumBackendCapabilities;
import totah.lab.prometheus.execution.quantum.QuantumExecutionOptions;
import totah.lab.prometheus.execution.quantum.QuantumExecutionRequest;
import totah.lab.prometheus.execution.quantum.QuantumObservable;
import totah.lab.prometheus.execution.quantum.QuantumSolverMode;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.ingest.authoritative.CartesianGeometry;
import totah.lab.prometheus.ingest.authoritative.PyscfGeometricArtifactReader;
import totah.lab.prometheus.ingest.authoritative.PyscfHessianResult;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;
import totah.lab.prometheus.recovery.RecoveryClassification;

/**
 * Independent adversarial ingest-side audit (ADV-ING-*) of the composite-observable
 * completeness defect class behind the TSL PBE-D3(BJ) Hessian incident.
 *
 * <p>General invariant under audit: claimed scientific observable == all required
 * executed components == all persisted components. The persistence gate (see
 * {@code AdversarialCompositeCompletenessAcceptanceTest}) can only verify consistency of
 * what it is handed; the ingest reader is where an executed-components lie (a PBE-only
 * analytic Hessian persisted under a composite PBE-D3(BJ) method label) must be caught
 * or explicitly relabeled before it can propagate into evidence.
 *
 * <p>Fixtures are synthetic PySCF/geomeTRIC artifact directories built in @TempDir; no
 * frozen archive is consulted.
 */
class AdversarialCompositeIngestAcceptanceTest {

    @TempDir
    Path directory;

    private final PyscfGeometricArtifactReader reader = new PyscfGeometricArtifactReader();

    /**
     * ADV-ING-I1. A composite-labeled Hessian run (method line claims PBE-D3(BJ), Hessian
     * block present) must NOT be emitted as a composite PBE-D3(BJ) Hessian record: the
     * simple-dftd3 1.5.0 wrapper has no Hessian hook, so the only scientifically honest
     * label is the derived PBE-only one, with dispersion relabeled "none". If the working
     * tree still emits the composite label this test is RED and is a live mislabeling
     * defect feeding the persistence gate.
     */
    @Test
    void compositeHessianLogIsRelabeledToPbeOnlyWithDispersionNone() throws Exception {
        writeHessianDirectory("PBE-D3(BJ)/def2-SVP density-fitted gas phase analytic Hessian");

        PyscfHessianResult result = reader.readHessian(directory);

        assertThat(result.method().value()).hasValueSatisfying(method -> assertThat(method)
                .contains("PBE")
                .contains("TRUSTED_PBE_ONLY_HESSIAN")
                .doesNotContain("D3"));
        assertThat(result.method().classification())
                .as("the PBE-only label is derived, never raw producer text")
                .isEqualTo(RecoveryClassification.DERIVABLE);
        assertThat(result.protocol().dispersion().value())
                .as("dispersion must be relabeled to none for a component the backend never executed")
                .contains("none");
        assertThat(result.protocol().functional().value()).contains("PBE");
        assertThat(result.protocol().densityFitted().value()).contains(true);
        assertThat(result.normalModeConvention()).contains("TRUSTED_PBE_ONLY_HESSIAN");
        assertThat(result.artifactChecksumsVerified()).isTrue();
        assertThat(result.cartesianDimension()).isEqualTo(6);
    }

    /**
     * ADV-ING-I2 no-regression: a pure PBE Hessian log keeps its raw PBE-only identity;
     * the composite relabeling path must not fire and must not invent a derived marker.
     */
    @Test
    void purePbeHessianLogKeepsPbeOnlyIdentity() throws Exception {
        writeHessianDirectory("PBE/def2-SVP gas phase analytic Hessian");

        PyscfHessianResult result = reader.readHessian(directory);

        assertThat(result.method().value()).hasValueSatisfying(method -> assertThat(method)
                .contains("PBE")
                .doesNotContain("D3")
                .doesNotContain("TRUSTED_PBE_ONLY_HESSIAN"));
        assertThat(result.method().classification())
                .as("a pure PBE method is raw producer text, not a derived relabel")
                .isEqualTo(RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT);
        assertThat(result.protocol().dispersion().value()).contains("none");
        assertThat(result.normalModeConvention()).doesNotContain("TRUSTED_PBE_ONLY_HESSIAN");
    }

    /**
     * ADV-ING-I3 capabilities seam. A backend whose declared observables lack HESSIAN must
     * NOT satisfy a request requiring HESSIAN (fail-closed capability claim), and the
     * capability declaration is per-backend only: two backends built from the same seam
     * answer the same request differently.
     *
     * <p>Architectural note (the root of the defect class): the capability model has no
     * composite-method component-capability concept. QuantumObservable.HESSIAN is a
     * monolith; there is no way to declare "electronic Hessian: yes, dispersion Hessian:
     * no" for a PBE-D3(BJ) method, so a composite Hessian request can be satisfied by a
     * backend that executes only the electronic component. The component split surfaces
     * only later, as self-declared identity text at the persistence gate.
     */
    @Test
    void backendWithoutHessianObservableCannotSatisfyHessianRequest() {
        QuantumExecutionRequest hessianRequest = hessianRequest();
        QuantumBackendCapabilities gradientOnly = capabilities(
                QuantumExecutionRequest.energyAndForces());
        QuantumBackendCapabilities hessianCapable = capabilities(
                Set.of(QuantumObservable.ABSOLUTE_ENERGY, QuantumObservable.CARTESIAN_GRADIENT,
                        QuantumObservable.CARTESIAN_FORCE, QuantumObservable.HESSIAN));

        assertThat(gradientOnly.satisfies(hessianRequest))
                .as("a backend without HESSIAN must fail closed against a Hessian request")
                .isFalse();
        assertThat(hessianCapable.satisfies(hessianRequest)).isTrue();

        assertThat(Arrays.stream(QuantumObservable.values()).map(Enum::name))
                .as("no per-component composite capability exists in the observable model")
                .noneMatch(name -> name.contains("COMPONENT") || name.contains("DISPERSION"));
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * Writes a synthetic 2-atom PySCF Hessian directory: input.json, result.json with
     * correct artifact digests, a 6x6 Cartesian Hessian, and 2 projected frequencies.
     */
    private void writeHessianDirectory(String method) throws IOException {
        Files.writeString(directory.resolve("input.json"), """
                {"minimum_id":"ADV01","method":"%s","charge":0,"multiplicity":1,
                 "frequency_projection":"PySCF harmonic_analysis exclude_trans=True exclude_rot=True",
                 "software":{"pyscf":"2.14.0","simple-dftd3":"1.5.0","numpy":"2.5.2"}}
                """.formatted(method));
        Path hessian = directory.resolve("cartesian_hessian_flat_hartree_per_bohr2.txt");
        Path frequencies = directory.resolve("frequencies_cm-1.txt");
        Path log = directory.resolve("raw_combined.log");
        Files.writeString(hessian, """
                0.512 0.045 -0.032 0.021 0.017 -0.028
                0.045 0.487 0.039 -0.024 0.031 0.014
                -0.032 0.039 0.623 0.027 -0.019 0.035
                0.021 -0.024 0.027 0.398 0.022 -0.026
                0.017 0.031 -0.019 0.022 0.441 0.029
                -0.028 0.014 0.035 -0.026 0.029 0.556
                """);
        Files.writeString(frequencies, "23.50\n2713.42\n");
        Files.writeString(log, "converged SCF energy = -76.02\n");
        Files.writeString(directory.resolve("result.json"), """
                {"status":"HESSIAN_COMPLETE","energy_hartree":-76.02,
                 "scf_converged":true,"frequency_count":2,
                 "artifact_sha256":{
                   "cartesian_hessian_flat_hartree_per_bohr2.txt":"%s",
                   "frequencies_cm-1.txt":"%s",
                   "raw_combined.log":"%s"}}
                """.formatted(sha256(hessian), sha256(frequencies), sha256(log)));
    }

    private static QuantumExecutionRequest hessianRequest() {
        List<CartesianGeometry.Atom> atoms = TslFixtures.canonicalMap().atoms().stream()
                .map(atom -> new CartesianGeometry.Atom(
                        atom.elementSymbol(), atom.canonicalIndex(), 0.0, 0.0))
                .toList();
        CartesianGeometry geometry = new CartesianGeometry(atoms, "angstrom");
        CalculationSpecification specification = new CalculationSpecification(
                "adv-ing-hessian-request", "composite Hessian capability audit",
                TslFixtures.TSL, TslFixtures.geometryIdentityA(), 0, 1,
                new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "gas", false, "java-native", "1"),
                List.of(), CalculationType.FORCE_EVALUATION,
                List.of("absolute energy", "Cartesian Hessian"),
                List.of("converged"), DatasetRole.DEVELOPMENT, CostEstimate.zero());
        return new QuantumExecutionRequest(specification, geometry, "e".repeat(64),
                QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                Set.of(QuantumObservable.ABSOLUTE_ENERGY, QuantumObservable.HESSIAN),
                QuantumExecutionOptions.local(Path.of("target/adv-ing-quantum"), 1, 512));
    }

    private static QuantumBackendCapabilities capabilities(Set<QuantumObservable> observables) {
        return new QuantumBackendCapabilities(
                Set.of(QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE),
                Set.of(CalculationType.FORCE_EVALUATION), observables, false);
    }

    /** Independent SHA-256 of a file. */
    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[4096];
                for (int count; (count = input.read(buffer)) >= 0;) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
