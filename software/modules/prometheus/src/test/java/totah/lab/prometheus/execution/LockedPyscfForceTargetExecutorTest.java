package totah.lab.prometheus.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;

class LockedPyscfForceTargetExecutorTest {

    @TempDir Path temporary;

    @Test
    void rejectsAllPythonExecutionEvenWhenAStaleResultExists() throws Exception {
        CalculationSpecification spec = spec("PySCF");
        Path resultDir = Files.createDirectories(temporary.resolve(spec.specificationId()));
        Files.writeString(resultDir.resolve("result.json"), """
                {"specification_checksum":"stale","scientific_identity":"stale","scf_converged":true}
                """);

        LockedPyscfForceTargetExecutor executor = executor(spec);

        assertThatThrownBy(() -> executor.execute(spec))
                .isInstanceOf(EvidenceExecutionException.class)
                .hasMessageContaining("external Python execution is disabled");
    }

    @Test
    void rejectsPythonExecutionAndNonPyscfRouting() throws Exception {
        CalculationSpecification spec = spec("PySCF");
        String identity = new EvidenceIdentity(spec.molecule(), "atom-map", spec.geometry(), spec.formalCharge(),
                spec.multiplicity(), spec.calculationType(), spec.protocol(), spec.constraints(),
                spec.requiredOutputs()).evidenceHash();
        Path resultDir = Files.createDirectories(temporary.resolve(spec.specificationId()));
        Files.writeString(resultDir.resolve("result.json"), """
                {"specification_checksum":"%s","scientific_identity":"%s","scf_converged":false}
                """.formatted(spec.checksum(), identity));

        assertThatThrownBy(() -> executor(spec).execute(spec))
                .isInstanceOf(EvidenceExecutionException.class)
                .hasMessageContaining("external Python execution is disabled");
        assertThat(executor(spec).supports(spec)).isFalse();
        assertThat(executor(spec("ORCA")).supports(spec("ORCA"))).isFalse();
    }

    private LockedPyscfForceTargetExecutor executor(CalculationSpecification spec) {
        return new LockedPyscfForceTargetExecutor(Path.of("python"), Path.of("runner.py"), temporary,
                Map.of(spec.geometry().sha256(), Path.of("geometry.xyz")), Set.of(spec.checksum()), 1, "atom-map");
    }

    private static CalculationSpecification spec(String software) {
        return new CalculationSpecification("force-test", "force executor fixture", TslFixtures.TSL,
                TslFixtures.geometryIdentityA(), 0, 1,
                new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "none", false, software, "1.0"),
                List.of(), CalculationType.FORCE_EVALUATION, List.of("energy", "gradient", "force"),
                List.of("scf_converged"), DatasetRole.DEVELOPMENT, CostEstimate.zero());
    }
}
