package totah.lab.athena.regression;

import org.junit.jupiter.api.Test;
import totah.lab.athena.tmt.NearAttackAssessment;
import totah.lab.athena.tmt.NearAttackAssessor;
import totah.lab.athena.tmt.NearAttackCriteria;
import totah.lab.athena.tmt.NearAttackGeometry;
import totah.lab.gaia.geometry.Point3D;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Category 4: TSL productive-state NAC geometry recomputed from the
 * frozen stage3 state PDBs ({code analysis/mettl7-closure/stage3/
 * <system>/<system>_SAM_TSL_<n>.pdb}) via {@link NearAttackGeometry}.
 * Golden per-state values: {@code stage3/all_states.csv} (36 PASS rows).
 * The 14400-placement RDKit search itself is not recomputable in Java and
 * its counts are treated as golden constants (REFERENCE.md section 4).
 */
class TslNacRegressionTest {

    private static final String CATEGORY = "tsl_nac_geometry";
    private static final double DISTANCE_TOLERANCE = 0.01;
    private static final double ANGLE_TOLERANCE = 0.1;

    private static final String CONVENTION =
            "run_tsl_matrix.py / reconstruct_tsl.py:34-38: S(TSL)...CE(SAM) "
                    + "distance and S(TSL)-CE-SD attack angle; recomputed "
                    + "via tmt.NearAttackGeometry.from(TSL sulfur, SAM CE, "
                    + "SAM SD) from the frozen state PDB (3-decimal PDB "
                    + "coordinate rounding)";

    /**
     * Historical near-attack gates (protocol: 2.8-3.2 A, angle >= 150 deg).
     * The upper distance bound carries a 0.01 A allowance because the
     * frozen state PDBs round coordinates to 3 decimals: historical
     * placements sit exactly at 2.8/3.0/3.2 A and recompute up to
     * 3.20013 A from the rounded files.
     */
    private static final NearAttackCriteria HISTORICAL_CRITERIA =
            new NearAttackCriteria(
                    2.8 - 0.01, 3.2 + 0.01, 150.0, 180.0,
                    2.0, 0,
                    "analysis/mettl7-closure/stage3 run_tsl_matrix.py / "
                            + "protocol.json: attack distance 2.8-3.2 A, "
                            + "angle >= 150 deg; distance bounds widened by "
                            + "0.01 A for 3-decimal PDB coordinate rounding; "
                            + "clash count pinned 0 (search gates not "
                            + "re-evaluated)");

    @Test
    void allThirtySixAcceptedStatesReproduce() {
        Path statesCsv = RegressionHarness.requireInput(
                "tsl-nac/all_states.csv",
                CATEGORY, "all_states");
        List<Map<String, String>> passRows =
                RegressionHarness.readCsv(statesCsv).stream()
                .filter(row -> "PASS".equals(row.get("status")))
                .toList();
        assertThat(passRows).as("36 accepted states").hasSize(36);

        NearAttackAssessor assessor = new NearAttackAssessor();
        for (Map<String, String> row : passRows) {
            String system = row.get("system");
            String state = row.get("state");
            Path statePdb = RegressionHarness.requireInput(
                    "tsl-nac/states/" + system + "_SAM_TSL_" + state + ".pdb",
                    CATEGORY, system + "_state" + state);
            List<RegressionHarness.PdbAtom> atoms =
                    RegressionHarness.pdbAtoms(statePdb);
            Point3D tslSulfur = atoms.stream()
                    .filter(atom -> "TSL".equals(atom.residue())
                            && "S".equals(atom.element()))
                    .map(RegressionHarness.PdbAtom::xyz)
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "no TSL sulfur in " + statePdb));
            Point3D samCe = samAtom(atoms, "CE", statePdb);
            Point3D samSd = samAtom(atoms, "SD", statePdb);

            NearAttackGeometry geometry = NearAttackGeometry.from(
                    tslSulfur, samCe, samSd, 0);
            double historicalDistance = RegressionHarness.parseDouble(
                    row.get("catalytic_distance_A"));
            double historicalAngle = RegressionHarness.parseDouble(
                    row.get("attack_angle_deg"));
            String metric = system + "_state" + state;
            RegressionHarness.record(CATEGORY,
                    metric + "_S_to_CE_distance_A", historicalDistance,
                    geometry.substrateSulfurToMethylCarbonAngstrom(),
                    CONVENTION, "REPRODUCED");
            RegressionHarness.record(CATEGORY,
                    metric + "_attack_angle_deg", historicalAngle,
                    geometry.substrateSulfurMethylCarbonSamSulfurAngleDegrees(),
                    CONVENTION, "REPRODUCED");
            assertThat(geometry.substrateSulfurToMethylCarbonAngstrom())
                    .as("%s distance", metric)
                    .isCloseTo(historicalDistance, within(DISTANCE_TOLERANCE));
            assertThat(geometry
                    .substrateSulfurMethylCarbonSamSulfurAngleDegrees())
                    .as("%s angle", metric)
                    .isCloseTo(historicalAngle, within(ANGLE_TOLERANCE));

            NearAttackAssessment assessment = assessor.assess(
                    geometry, HISTORICAL_CRITERIA, false, false);
            assertThat(assessment.geometryWithinCandidateRange()).as(metric)
                    .isTrue();
        }
    }

    @Test
    void samDonorBondConstantReproduces() {
        Path path = RegressionHarness.requireInput(
                "shared/WT_METTL7A_SAM_BOUND.pdb",
                CATEGORY, "sam_validated_7A");
        List<RegressionHarness.PdbAtom> atoms =
                RegressionHarness.pdbAtoms(path);
        double distance = samAtom(atoms, "CE", path)
                .distance(samAtom(atoms, "SD", path));
        RegressionHarness.record(CATEGORY, "SAM_SD_to_CE_A",
                1.7258971580021796, distance,
                "catalytic_corridor.csv constant (all 55 rows); "
                        + "validated WT 7A SAM-bound PDB",
                "REPRODUCED");
        assertThat(distance).isCloseTo(1.7258971580021796, within(0.001));
    }

    private static Point3D samAtom(
            List<RegressionHarness.PdbAtom> atoms, String name, Path path) {
        return atoms.stream()
                .filter(atom -> "SAM".equals(atom.residue())
                        && name.equals(atom.name()))
                .map(RegressionHarness.PdbAtom::xyz)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "no SAM " + name + " in " + path));
    }
}
