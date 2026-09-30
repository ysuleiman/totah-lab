package totah.lab.prometheus.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.store.GeneratedEvidenceCandidate;
import totah.lab.prometheus.store.GeneratedEvidenceRole;

/**
 * Fail-closed falsification of every locked validation gate in
 * {@link PyscfForceTargetEvidenceMapper}. Each rejection test mutates exactly one
 * field of a valid {@code result.json} payload (the same schema the locked executor
 * writes: {@code scientific_identity}, {@code scf_converged}, force-target arrays)
 * and asserts the rejection message identifies THAT gate — a rejection firing for
 * the wrong reason is a defect.
 */
class PyscfForceTargetEvidenceMapperTest {

    @TempDir Path temporary;

    private final EvidenceIdentity identity = new EvidenceIdentity(
            TslFixtures.TSL, "atom-map", TslFixtures.geometryIdentityA(), 0, 1,
            CalculationType.FORCE_EVALUATION,
            new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "none", false, "PySCF", "1.0"),
            List.of(), List.of("energy", "gradient", "force"));

    private final PyscfForceTargetEvidenceMapper mapper = new PyscfForceTargetEvidenceMapper(identity);

    // FAILCLOSED-PYSCF-01: happy path — all gates pass, one PRIMARY candidate.
    @Test
    void validArtifactYieldsOnePrimaryCandidateWithFullIdentityAndProvenance() throws Exception {
        Path base = writeResult(validResultJson());
        RawCalculationResult raw = raw();

        List<GeneratedEvidenceCandidate> candidates = mapper.validateAndMap(raw, base);

        assertThat(candidates).hasSize(1);
        GeneratedEvidenceCandidate candidate = candidates.get(0);
        assertThat(candidate.role()).isEqualTo(GeneratedEvidenceRole.PRIMARY);
        assertThat(candidate.artifactBase()).isEqualTo(base);
        assertThat(candidate.artifacts()).isEqualTo(raw.artifacts());

        QuantumEvidence evidence = candidate.evidence();
        assertThat(evidence.identity()).isEqualTo(identity);
        assertThat(evidence.convergence()).isEqualTo(ConvergenceStatus.CONVERGED);
        assertThat(evidence.acceptance()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
        assertThat(evidence.energyHartree()).hasValue(-40.5);
        assertThat(evidence.gradientHartreePerBohr()).hasValue(List.of(
                0.3, 0.4, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0));
        assertThat(evidence.hessianHartreePerBohr2()).isEmpty();
        assertThat(evidence.dipoleDebye()).isEmpty();
        assertThat(evidence.interactionEnergyKcalMol()).isEmpty();
        assertThat(evidence.convergenceNote()).contains("validation gates passed");

        Path resultPath = base.resolve("result.json");
        assertThat(evidence.provenance().sourcePath()).isEqualTo(resultPath.toString());
        assertThat(evidence.provenance().sha256()).isEqualTo(ArtifactChecksums.sha256(resultPath));
    }

    // FAILCLOSED-PYSCF-02: SCF convergence gate.
    @Test
    void rejectsWhenScfNotConverged() throws Exception {
        assertRejected(
                validResultJson().replace("\"scf_converged\": true", "\"scf_converged\": false"),
                "SCF not converged");
    }

    // FAILCLOSED-PYSCF-03: scientific-identity hash gate.
    @Test
    void rejectsWhenScientificIdentityMismatches() throws Exception {
        assertRejected(
                validResultJson().replace(identity.evidenceHash(), "0".repeat(64)),
                "scientific identity mismatch");
    }

    // FAILCLOSED-PYSCF-04: geometry hash gate.
    @Test
    void rejectsWhenGeometryIdentityMismatches() throws Exception {
        assertRejected(
                validResultJson().replace(identity.geometry().sha256(), "f".repeat(64)),
                "geometry identity mismatch");
    }

    // FAILCLOSED-PYSCF-05: units gate (force declared in kcal/mol/angstrom).
    @Test
    void rejectsWhenForceUnitsAreNotHartreePerBohr() throws Exception {
        assertRejected(
                validResultJson().replace(
                        "\"force\": \"hartree/bohr\"", "\"force\": \"kcal/mol/angstrom\""),
                "force target units mismatch");
    }

    // FAILCLOSED-PYSCF-06: N (atom count) gate — force array has 4 atoms, geometry has 5.
    @Test
    void rejectsWhenForceAtomCountDiffersFromGeometry() throws Exception {
        assertRejected(
                validResultJson().replace(
                        "    [-0.0, -0.0, -0.0],\n    [-0.0, -0.0, -0.0],",
                        "    [-0.0, -0.0, -0.0],"),
                "gradient/force atom count mismatch");
    }

    // FAILCLOSED-PYSCF-07: N x 3 shape gate — one force vector has 2 components.
    @Test
    void rejectsWhenForceVectorsAreNotThreeComponents() throws Exception {
        assertRejected(
                validResultJson().replace("[-0.3, -0.4, -0.0]", "[-0.3, -0.4]"),
                "gradient/force must be N x 3");
    }

    // FAILCLOSED-PYSCF-08: force = -gradient gate — one force component is perturbed.
    @Test
    void rejectsWhenForceIsNotTheNegativeGradient() throws Exception {
        assertRejected(
                validResultJson().replace("[-0.3, -0.4, -0.0]", "[-0.3, -0.4001, -0.0]"),
                "force sign or finiteness validation failed");
    }

    // FAILCLOSED-PYSCF-09: finiteness gate — a gradient component overflows to +Infinity.
    @Test
    void rejectsWhenGradientComponentIsNotFinite() throws Exception {
        assertRejected(
                validResultJson().replace("[0.3, 0.4, 0.0]", "[1e309, 0.4, 0.0]"),
                "force sign or finiteness validation failed");
    }

    // FAILCLOSED-PYSCF-10: energy finiteness gate.
    @Test
    void rejectsWhenEnergyIsNotFinite() throws Exception {
        assertRejected(
                validResultJson().replace("\"energy_hartree\": -40.5", "\"energy_hartree\": 1e309"),
                "non-finite energy");
    }

    // FAILCLOSED-PYSCF-11: declared-norm consistency gate (declared 0.6, computed 0.5).
    @Test
    void rejectsWhenDeclaredGradientNormDisagrees() throws Exception {
        assertRejected(
                validResultJson().replace(
                        "\"gradient_norm_hartree_per_bohr\": 0.5",
                        "\"gradient_norm_hartree_per_bohr\": 0.6"),
                "gradient norm mismatch");
    }

    // FAILCLOSED-PYSCF-12: missing result.json must fail closed, not map to empty evidence.
    @Test
    void missingResultFileFailsClosedWithIoException() {
        Path base = temporary.resolve("calc-without-result");
        assertThatThrownBy(() -> mapper.validateAndMap(raw(), base))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("result.json");
    }

    // FAILCLOSED-PYSCF-13: truncated JSON must fail closed, never a silent partial parse.
    @Test
    void truncatedResultJsonFailsClosedWithJsonProcessingException() throws Exception {
        Path base = writeResult(validResultJson().substring(0, 60));
        assertThatThrownBy(() -> mapper.validateAndMap(raw(), base))
                .isInstanceOf(JsonProcessingException.class);
    }

    // FAILCLOSED-PYSCF-14: absent fields must default to rejection, starting at the SCF gate.
    @Test
    void emptyJsonObjectFailsClosedAtTheFirstGate() throws Exception {
        assertRejected("{}", "SCF not converged");
    }

    private void assertRejected(String json, String expectedGateMessage) throws Exception {
        Path base = writeResult(json);
        assertThatThrownBy(() -> mapper.validateAndMap(raw(), base))
                .isInstanceOf(IOException.class)
                .hasMessage(expectedGateMessage);
    }

    private Path writeResult(String json) throws IOException {
        Path base = Files.createDirectories(temporary.resolve("calc"));
        Files.writeString(base.resolve("result.json"), json);
        return base;
    }

    private RawCalculationResult raw() {
        return new RawCalculationResult(spec(), List.of(
                new RawArtifact("result.json", "unused-checksum", "result_json")),
                ConvergenceStatus.CONVERGED, "executor finished");
    }

    private static CalculationSpecification spec() {
        return new CalculationSpecification("force-test", "force mapper fixture", TslFixtures.TSL,
                TslFixtures.geometryIdentityA(), 0, 1,
                new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "none", false, "PySCF", "1.0"),
                List.of(), CalculationType.FORCE_EVALUATION, List.of("energy", "gradient", "force"),
                List.of("scf_converged"), DatasetRole.DEVELOPMENT, CostEstimate.zero());
    }

    /**
     * Valid payload: TSL geometry A (5 atoms), one non-zero gradient [0.3, 0.4, 0.0]
     * (norm exactly 0.5), forces equal to the negative gradient.
     */
    private String validResultJson() {
        return """
                {
                  "specification_checksum": "spec-checksum",
                  "scientific_identity": "%s",
                  "geometry_identity": "%s",
                  "scf_converged": true,
                  "units": {"energy": "hartree", "gradient": "hartree/bohr", "force": "hartree/bohr"},
                  "energy_hartree": -40.5,
                  "gradient_hartree_per_bohr": [
                    [0.3, 0.4, 0.0],
                    [0.0, 0.0, 0.0],
                    [0.0, 0.0, 0.0],
                    [0.0, 0.0, 0.0],
                    [0.0, 0.0, 0.0]
                  ],
                  "force_hartree_per_bohr": [
                    [-0.3, -0.4, -0.0],
                    [-0.0, -0.0, -0.0],
                    [-0.0, -0.0, -0.0],
                    [-0.0, -0.0, -0.0],
                    [-0.0, -0.0, -0.0]
                  ],
                  "gradient_norm_hartree_per_bohr": 0.5
                }
                """.formatted(identity.evidenceHash(), identity.geometry().sha256());
    }
}
