package totah.lab.athena.regression;

import org.junit.jupiter.api.Test;
import totah.lab.athena.tmt.NearAttackAssessment;
import totah.lab.athena.tmt.NearAttackAssessor;
import totah.lab.athena.tmt.NearAttackClassification;
import totah.lab.athena.tmt.NearAttackCriteria;
import totah.lab.athena.tmt.NearAttackGeometry;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtModel;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Category 6: netarsudil 196-207 contacts, C202/C203 distances, and the
 * four-row in-pose NAC table. Golden:
 * research/mettl7-netarsudil-sam-mechanism/vina-matched/analysis/
 * c202_c203_family_geometry.csv + representative_local_wall_classification.csv,
 * local-architecture/analysis/corrected_7a_vs_accepted_7b_residues.csv,
 * and research/mettl7-reaction-competence-v1/
 * netarsudil_acceptor_state_analysis.csv (REFERENCE.md section 6).
 */
class NetarsudilContactRegressionTest {

    private static final String CATEGORY = "netarsudil_nac_and_c202_c203";
    private static final double DISTANCE_TOLERANCE = 0.01;
    private static final double NAC_DISTANCE_TOLERANCE = 0.001;
    private static final double NAC_ANGLE_TOLERANCE = 0.005;

    /** In-pose near-attack gate (run_analysis.py:113): 2.8-3.2 A, >= 150 deg. */
    private static final NearAttackCriteria IN_POSE_CRITERIA =
            new NearAttackCriteria(
                    2.8, 3.2, 150.0, 180.0, 2.0, 0,
                    "research/mettl7-reaction-competence-v1/protocol.json: "
                            + "near-attack iff 2.8 <= d(N...CE) <= 3.2 A and "
                            + "angle(N-CE-SD) >= 150 deg; clash count pinned "
                            + "0 (not re-evaluated)");

    private static final String NAC_CONVENTION =
            "run_analysis.py:108-113: ligand N to SAM CE distance and "
                    + "N-CE-SD angle via tmt.NearAttackGeometry; SAM CE/SD "
                    + "from analysis/dcmb/sam_state/validated PDB; "
                    + "historical values rounded to 4/3 decimals";

    @Test
    void inPoseNearAttackTableReproduces() {
        recordNacRow("7B_ACCEPTED_7B_FAMILY5", "N5", 16.8565, 98.454,
                poseAtom("netarsudil/7B_neutral_seed483271.pdbqt",
                        5, "N5"),
                sam("netarsudil/WT_METTL7B_SAM_BOUND.pdb"));
        recordNacRow("7B_ACCEPTED_7B_FAMILY5", "N6", 17.0993, 119.451,
                poseAtom("netarsudil/7B_neutral_seed483271.pdbqt",
                        5, "N6"),
                sam("netarsudil/WT_METTL7B_SAM_BOUND.pdb"));
        recordNacRow("7A_MATCHED_7A_LOWEST_STRAIN_CONTROL", "N5",
                9.282, 56.181,
                poseAtom("netarsudil/corrected_7A_lowest_strain_mode15.pdbqt",
                        1, "N5"),
                sam("shared/WT_METTL7A_SAM_BOUND.pdb"));
        recordNacRow("7A_MATCHED_7A_LOWEST_STRAIN_CONTROL", "N6",
                15.1994, 97.58,
                poseAtom("netarsudil/corrected_7A_lowest_strain_mode15.pdbqt",
                        1, "N6"),
                sam("shared/WT_METTL7A_SAM_BOUND.pdb"));
    }

    private void recordNacRow(
            String poseFamily, String atomName, double historicalDistance,
            double historicalAngle, Point3D acceptor, Point3D[] samCeSd) {

        NearAttackGeometry geometry = NearAttackGeometry.from(
                acceptor, samCeSd[0], samCeSd[1], 0);
        String metric = poseFamily + "_" + atomName;
        RegressionHarness.record(CATEGORY, metric + "_distance_A",
                historicalDistance,
                geometry.substrateSulfurToMethylCarbonAngstrom(),
                NAC_CONVENTION, "REPRODUCED");
        RegressionHarness.record(CATEGORY, metric + "_angle_deg",
                historicalAngle,
                geometry.substrateSulfurMethylCarbonSamSulfurAngleDegrees(),
                NAC_CONVENTION, "REPRODUCED");
        assertThat(geometry.substrateSulfurToMethylCarbonAngstrom())
                .as(metric).isCloseTo(historicalDistance,
                        within(NAC_DISTANCE_TOLERANCE));
        assertThat(geometry
                .substrateSulfurMethylCarbonSamSulfurAngleDegrees())
                .as(metric).isCloseTo(historicalAngle,
                        within(NAC_ANGLE_TOLERANCE));

        NearAttackAssessment assessment = new NearAttackAssessor().assess(
                geometry, IN_POSE_CRITERIA, false, false);
        RegressionHarness.record(CATEGORY, metric + "_in_pose_near_attack",
                "FALSE",
                Boolean.toString(assessment.geometryWithinCandidateRange())
                        .toUpperCase(),
                "in-pose near-attack gate 2.8-3.2 A and >= 150 deg",
                "REPRODUCED");
        assertThat(assessment.classification()).as(metric)
                .isEqualTo(NearAttackClassification.CLEARLY_NONPRODUCTIVE);
    }

    @Test
    void c202C203FamilyGeometryReproduces() {
        Path receptorPath = RegressionHarness.requireInput(
                "netarsudil/METTL7B_SAM_receptor.pdbqt",
                CATEGORY, "receptor_7B");
        List<PdbqtAtom> receptor = RegressionHarness.readPdbqt(receptorPath)
                .models().stream().flatMap(model -> model.atoms().stream())
                .toList();
        List<Map<String, String>> golden = RegressionHarness.readCsv(
                RegressionHarness.requireInput(
                        "netarsudil/c202_c203_family_geometry.csv",
                        CATEGORY, "golden"));
        assertThat(golden).hasSize(5);

        for (Map<String, String> row : golden) {
            String metric = "7B_neutral_seed" + row.get("seed") + "_mode"
                    + row.get("mode");
            List<PdbqtAtom> pose = RegressionHarness
                    .heavyNetarsudilConvention(RegressionHarness.model(
                            RegressionHarness.readPdbqt(
                                    RegressionHarness.requireInput(
                                            "netarsudil/7B_neutral_seed"
                                                    + row.get("seed") + ".pdbqt",
                                            CATEGORY, metric + "_pose")),
                            Integer.parseInt(row.get("mode"))).atoms());
            for (int number : new int[]{202, 203}) {
                List<PdbqtAtom> residue =
                        RegressionHarness.heavyNetarsudilConvention(
                                receptor.stream()
                                        .filter(atom -> atom.chainId() != null
                                                && atom.chainId().equals("A")
                                                && atom.residueNumber() != null
                                                && atom.residueNumber()
                                                        == number)
                                        .toList());
                double minimum = RegressionHarness.minDistance(
                        pose.stream().map(PdbqtAtom::position).toList(),
                        residue.stream().map(PdbqtAtom::position).toList());
                double historical = RegressionHarness.parseDouble(
                        row.get("c" + number + "_min_a"));
                RegressionHarness.record(CATEGORY,
                        metric + "_C" + number + "_min_A", historical, minimum,
                        "analyze_cys202_cys203_geometry.py pair_min over "
                                + "heavy atoms (type not H/HD, name not H*)",
                        "REPRODUCED");
                assertThat(minimum).as(metric + " C" + number)
                        .isCloseTo(historical, within(DISTANCE_TOLERANCE));
            }
        }
    }

    @Test
    void representativeWallContactsReproduce() {
        List<PdbqtAtom> receptor = RegressionHarness.readPdbqt(
                RegressionHarness.requireInput(
                        "netarsudil/METTL7B_SAM_receptor.pdbqt",
                        CATEGORY, "receptor_7B"))
                .models().stream().flatMap(model -> model.atoms().stream())
                .toList();
        List<Point3D> pose = RegressionHarness.heavyNetarsudilConvention(
                RegressionHarness.model(RegressionHarness.readPdbqt(
                        RegressionHarness.requireInput(
                                "netarsudil/7B_neutral_seed483271.pdbqt",
                                CATEGORY, "pose_483271_5")), 5).atoms())
                .stream().map(PdbqtAtom::position).toList();

        List<Map<String, String>> golden = RegressionHarness.readCsv(
                RegressionHarness.requireInput(
                        "netarsudil/representative_local_wall_classification.csv",
                        CATEGORY, "golden_wall"));
        assertThat(golden).hasSize(54);
        for (Map<String, String> row : golden) {
            int number = Integer.parseInt(row.get("residue_number"));
            List<Point3D> residue = RegressionHarness
                    .heavyNetarsudilConvention(receptor.stream()
                            .filter(atom -> atom.chainId() != null
                                    && atom.chainId().equals(
                                            row.get("chain"))
                                    && atom.residueNumber() != null
                                    && atom.residueNumber() == number)
                            .toList())
                    .stream().map(PdbqtAtom::position).toList();
            double minimum = RegressionHarness.minDistance(pose, residue);
            String metric = "7B_representative_" + row.get("residue_name")
                    + number + "_min_A";
            RegressionHarness.record(CATEGORY, metric,
                    row.get("min_distance_a"), Double.toString(minimum),
                    "representative_local_wall_classification.csv: min "
                            + "heavy-atom distance, pose 483271 mode 5, "
                            + "contact convention <= 4.0 A",
                    "REPRODUCED");
            assertThat(minimum).as(metric)
                    .isCloseTo(RegressionHarness.parseDouble(
                            row.get("min_distance_a")),
                            within(DISTANCE_TOLERANCE));
        }
    }

    @Test
    void corrected7aContactsReproduce() {
        List<PdbqtAtom> receptor = RegressionHarness.readPdbqt(
                RegressionHarness.requireInput(
                        "netarsudil/METTL7A_SAM_receptor.pdbqt",
                        CATEGORY, "receptor_7A_corrected"))
                .models().stream().flatMap(model -> model.atoms().stream())
                .toList();
        List<Point3D> pose = RegressionHarness.heavyNetarsudilConvention(
                RegressionHarness.readPdbqt(RegressionHarness.requireInput(
                        "netarsudil/corrected_7A_lowest_strain_mode15.pdbqt",
                        CATEGORY, "pose_7A_corrected")).firstModel().atoms())
                .stream().map(PdbqtAtom::position).toList();

        List<Map<String, String>> golden = RegressionHarness.readCsv(
                RegressionHarness.requireInput(
                        "netarsudil/corrected_7a_vs_accepted_7b_residues.csv",
                        CATEGORY, "golden_corrected_7a")).stream()
                .filter(row -> "7A_CORRECTED_LOWEST_STRAIN".equals(
                        row.get("reference")))
                .toList();
        assertThat(golden).hasSize(21);
        for (Map<String, String> row : golden) {
            int number = Integer.parseInt(row.get("residue_number"));
            List<Point3D> residue = RegressionHarness
                    .heavyNetarsudilConvention(receptor.stream()
                            .filter(atom -> "SAM".equals(atom.residueName())
                                    == false)
                            .filter(atom -> atom.residueNumber() != null
                                    && atom.residueNumber() == number)
                            .toList())
                    .stream().map(PdbqtAtom::position).toList();
            double minimum = RegressionHarness.minDistance(pose, residue);
            String metric = "7A_corrected_" + row.get("identity") + number
                    + "_min_A";
            RegressionHarness.record(CATEGORY, metric,
                    row.get("ligand_min_distance_a"),
                    Double.toString(minimum),
                    "corrected_7a_vs_accepted_7b_residues.csv: min "
                            + "heavy-atom distance (type not H/HD), direct "
                            + "contact <= 4.0 A",
                    "REPRODUCED");
            assertThat(minimum).as(metric)
                    .isCloseTo(RegressionHarness.parseDouble(
                            row.get("ligand_min_distance_a")),
                            within(DISTANCE_TOLERANCE));
        }
    }

    private Point3D poseAtom(String repoRelative, int mode, String atomName) {
        PdbqtModel model = RegressionHarness.model(
                RegressionHarness.readPdbqt(
                        RegressionHarness.requireInput(repoRelative, CATEGORY,
                                atomName + "_pose")),
                mode);
        return model.atoms().stream()
                .filter(atom -> atom.atomName().equals(atomName))
                .map(PdbqtAtom::position).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "no atom " + atomName + " in " + repoRelative));
    }

    /** [CE, SD] of the SAM residue of a validated PDB. */
    private Point3D[] sam(String repoRelative) {
        List<RegressionHarness.PdbAtom> atoms = RegressionHarness.pdbAtoms(
                RegressionHarness.requireInput(repoRelative, CATEGORY, "sam"));
        Map<String, Point3D> byName = new LinkedHashMap<>();
        for (RegressionHarness.PdbAtom atom : atoms) {
            if ("SAM".equals(atom.residue())) {
                byName.putIfAbsent(atom.name(), atom.xyz());
            }
        }
        return new Point3D[]{byName.get("CE"), byName.get("SD")};
    }
}
