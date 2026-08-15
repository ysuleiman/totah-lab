package totah.lab.prometheus.ingest.authoritative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.recovery.RecoveryClassification;

class AuthoritativeOptimizationReaderTest {

    private final AuthoritativeOptimizationReader reader = new AuthoritativeOptimizationReader();

    @Test
    void reconstructsUnit05hPointFromCalculationArtifacts() throws Exception {
        Path point = repositoryRoot().resolve(
                "analysis/mettl7-phase2/execution-unit-05H/points/phi060_psi-060");
        AuthoritativeOptimizationRecord record = reader.read(point);

        assertEquals("phi060_psi-060", record.pointId().value().orElseThrow());
        assertEquals(0, record.charge().value().orElseThrow());
        assertEquals(1, record.multiplicity().value().orElseThrow());
        assertEquals(-1477.9406288811, record.finalEnergyHartree().value().orElseThrow(), 1.0e-12);
        assertEquals(18, record.cycles().value().orElseThrow());
        assertEquals(2, record.constraints().value().orElseThrow().size());
        assertTrue(record.scfConverged().value().orElseThrow());
        assertEquals(RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT,
                record.finalEnergyHartree().classification());
        assertEquals(2, record.finalEnergyHartree().provenance().size());
    }

    @Test
    void reconstructsAllFive05hAndEight05lCalculations() throws Exception {
        Path root = repositoryRoot().resolve("analysis/mettl7-phase2");
        assertEquals(5, reader.readPointSet(root.resolve("execution-unit-05H/points")).size());
        assertEquals(8, reader.readPointSet(root.resolve("execution-unit-05L/points")).size());
    }

    @Test
    void reproducesUnit05lHistoricalRelativeEnergiesFromRawAbsoluteEnergies() throws Exception {
        Path root = repositoryRoot();
        Path points = root.resolve("analysis/mettl7-phase2/execution-unit-05L/points");
        List<AuthoritativeOptimizationRecord> records = List.of(
                reader.read(points.resolve("phi060_psi060_A_m10")),
                reader.read(points.resolve("phi300_psi060_A_p10")));
        Map<Integer, Double> rawParents = Map.of(
                60, reader.read(root.resolve(
                        "analysis/mettl7-phase2/execution-unit-05H/points/phi060_psi+060"))
                        .finalEnergyHartree().value().orElseThrow(),
                300, reader.read(root.resolve(
                        "analysis/mettl7-phase2/execution-unit-05H/points/phi300_psi+060"))
                        .finalEnergyHartree().value().orElseThrow());

        List<HistoricalValueComparison> comparisons = reader.compareHistoricalRelativeEnergies(records, rawParents,
                root.resolve("analysis/mettl7-phase2/execution-unit-05L/SPARSE_TWO_ANGLE_QM_RESULTS.csv"),
                1.0e-6);

        assertEquals(2, comparisons.size());
        assertTrue(comparisons.stream().allMatch(HistoricalValueComparison::matchesTolerance));
    }

    private static Path repositoryRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return working.endsWith(Path.of("software/modules/prometheus"))
                ? working.resolve("../../..").normalize()
                : working;
    }
}
