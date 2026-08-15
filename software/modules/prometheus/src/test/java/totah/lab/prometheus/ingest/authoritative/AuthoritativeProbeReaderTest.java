package totah.lab.prometheus.ingest.authoritative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.recovery.RecoveryClassification;

class AuthoritativeProbeReaderTest {

    private final AuthoritativeProbeReader reader = new AuthoritativeProbeReader();

    @Test
    void reconstructsCounterpoiseEnergyAndProtocolFromThreeScfLogs() throws Exception {
        Path root = repositoryRoot();
        Path aws = root.resolve("analysis/mettl7-phase2/execution-unit-05O/vdw-probe-validation-aws");
        Path pointRoot = aws.resolve("point-1");
        AuthoritativeProbeRecord record = reader.read(
                pointRoot.resolve("01_MIN01_S_ACCEPTOR_2.2"),
                pointRoot.resolve("software_environment.json"),
                root.resolve("analysis/mettl7-phase2/execution-unit-05O/"
                        + "final-19-point-force-field-diagnostic/ALL_19_PROBE_GEOMETRY_AUDIT.csv"));

        assertEquals("01_MIN01_S_ACCEPTOR_2.2", record.pointId().value().orElseThrow());
        assertEquals(0.4332268549907766, record.interactionEnergyKcalMol().value().orElseThrow(), 1.0e-8);
        assertEquals(-1555.6778473538784, record.dimerElectronicEnergyHartree().value().orElseThrow(), 5.0e-12);
        assertEquals(1, record.multiplicity().value().orElseThrow());
        assertEquals("2.14.0", record.softwareVersions().value().orElseThrow().get("pyscf"));
        assertEquals(RecoveryClassification.DERIVABLE, record.interactionEnergyKcalMol().classification());
        assertEquals(5, record.interactionEnergyKcalMol().provenance().size());
        assertTrue(record.geometryValidForValidation());
    }

    @Test
    void preservesGeometryInvalidProbeAsExcludedRawEvidence() throws Exception {
        Path root = repositoryRoot();
        Path aws = root.resolve("analysis/mettl7-phase2/execution-unit-05O/vdw-probe-validation-aws");
        Path pointRoot = aws.resolve("point-13");
        AuthoritativeProbeRecord record = reader.read(
                pointRoot.resolve("13_MIN01_O3_ACCEPTOR_1.7"),
                pointRoot.resolve("software_environment.json"),
                root.resolve("analysis/mettl7-phase2/execution-unit-05O/"
                        + "final-19-point-force-field-diagnostic/ALL_19_PROBE_GEOMETRY_AUDIT.csv"));

        assertFalse(record.geometryValidForValidation());
        assertEquals("PROBE_DESIGN_SCAFFOLD_COLLISION",
                record.geometryClassification().value().orElseThrow());
        assertEquals("EXCLUDE_PROBE_DESIGN_FAILURE",
                record.validationEligibility().value().orElseThrow());
    }

    @Test
    void independentlyMatchesHistoricalMasterInteractionEnergy() throws Exception {
        Path root = repositoryRoot();
        Path aws = root.resolve("analysis/mettl7-phase2/execution-unit-05O/vdw-probe-validation-aws");
        Path pointRoot = aws.resolve("point-1");
        AuthoritativeProbeRecord record = reader.read(
                pointRoot.resolve("01_MIN01_S_ACCEPTOR_2.2"),
                pointRoot.resolve("software_environment.json"),
                root.resolve("analysis/mettl7-phase2/execution-unit-05O/"
                        + "final-19-point-force-field-diagnostic/ALL_19_PROBE_GEOMETRY_AUDIT.csv"));

        List<HistoricalValueComparison> comparison = reader.compareHistoricalInteractionEnergies(List.of(record),
                root.resolve("analysis/mettl7-phase2/execution-unit-05O/"
                        + "final-19-point-force-field-diagnostic/MASTER_19_POINT_TABLE_GEOMETRY_AUDITED.csv"),
                1.0e-8);
        assertTrue(comparison.getFirst().matchesTolerance());
    }

    @Test
    void reconstructsAllNineteenProbePointsAndPreservesSixAuditExclusions() throws Exception {
        Path root = repositoryRoot();
        List<AuthoritativeProbeRecord> records = reader.readShardedDataset(
                root.resolve("analysis/mettl7-phase2/execution-unit-05O/vdw-probe-validation-aws"),
                root.resolve("analysis/mettl7-phase2/execution-unit-05O/"
                        + "final-19-point-force-field-diagnostic/ALL_19_PROBE_GEOMETRY_AUDIT.csv"));

        assertEquals(19, records.size());
        assertEquals(6, records.stream().filter(record -> !record.geometryValidForValidation()).count());
        assertTrue(records.stream().allMatch(record -> record.scfConverged().value().orElseThrow()));
        AuthoritativeProbeRecord point19 = records.stream()
                .filter(record -> record.pointId().value().orElseThrow().equals("19_MIN04_SH_DONOR_2.1"))
                .findFirst().orElseThrow();
        assertTrue(point19.rawArtifactDiscrepancies().stream()
                .anyMatch(discrepancy -> discrepancy.field().equals("electronic_dimer_hartree")));
        assertTrue(point19.rawArtifactDiscrepancies().stream()
                .allMatch(discrepancy -> discrepancy.absoluteDifference() <= 1.0e-10));
    }

    private static Path repositoryRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return working.endsWith(Path.of("software/modules/prometheus"))
                ? working.resolve("../../..").normalize()
                : working;
    }
}
