package totah.lab.athena.regression;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Category 1: DCMB F43 + F199 minimum heavy-atom distances from the
 * frozen WT-7A family medoids to the original (fixed) receptor.
 * Golden: tmp/athena-v2-regression-reference/REFERENCE.md section 1,
 * produced by analysis/dcmb/dcmb_tsl_interference/analyze_interference.py
 * lines 290-294 (min over all heavy-atom pairs; hydrogens excluded at
 * parse; SAM/TSL/SAH/MTS excluded from protein atoms).
 */
class DcmbContactRegressionTest {

    private static final String CATEGORY = "dcmb_f43_f199_min_distance";
    private static final double TOLERANCE = 0.01;

    /** Historical minimum distances verbatim from REFERENCE.md (22 rows). */
    private static final Map<String, double[]> EXPECTED = Map.ofEntries(
            Map.entry("R1", new double[]{3.3911182521404357, 3.356203062986505}),
            Map.entry("R2", new double[]{3.2821730606413793, 3.3070828535130476}),
            Map.entry("R3", new double[]{3.4421032814254713, 3.5154523179812864}),
            Map.entry("R4", new double[]{3.303485129374734, 3.304553373755673}),
            Map.entry("R5", new double[]{3.4793571245274606, 3.005262384551472}),
            Map.entry("S1", new double[]{3.345602636297383, 3.3400899688481447}),
            Map.entry("S2", new double[]{3.423347776665409, 3.0064740145226607}),
            Map.entry("S3", new double[]{3.3068596885867416, 3.387273092031406}),
            Map.entry("S4", new double[]{3.1530596251894765, 3.278704164757779}),
            Map.entry("S5", new double[]{3.448870249806449, 2.9893400609499077}),
            Map.entry("S6", new double[]{3.042610392409781, 3.098925136236756}));

    private static final String CONVENTION =
            "analyze_interference.py:290-294: min heavy-atom pair distance; "
                    + "hydrogens excluded at parse; protein excludes "
                    + "SAM/TSL/SAH/MTS; pose = representative_mode MODEL of "
                    + "raw docking PDBQT";

    @Test
    void f43AndF199MinimumDistancesReproduce() {
        Path receptorPath = RegressionHarness.requireInput(
                "analysis/dcmb/sam_state/validated/WT_METTL7A_SAM_BOUND.pdb",
                CATEGORY, "receptor");
        List<RegressionHarness.PdbAtom> protein = RegressionHarness.heavy(
                RegressionHarness.pdbAtoms(receptorPath)).stream()
                .filter(atom -> !List.of("SAM", "TSL", "SAH", "MTS")
                        .contains(atom.residue()))
                .toList();
        List<Point3D> f43 = RegressionHarness.points(protein.stream()
                .filter(atom -> atom.number() == 43).toList());
        List<Point3D> f199 = RegressionHarness.points(protein.stream()
                .filter(atom -> atom.number() == 199).toList());
        assertThat(f43).isNotEmpty();
        assertThat(f199).isNotEmpty();

        Path familiesPath = RegressionHarness.requireInput(
                "analysis/dcmb/controlled_campaign/family_results.csv",
                CATEGORY, "family_results");
        List<Map<String, String>> families =
                RegressionHarness.readCsv(familiesPath).stream()
                .filter(row -> "7A_WT_SAM_BOUND".equals(row.get("condition")))
                .toList();
        assertThat(families).hasSize(11);

        int contacts43 = 0;
        int contacts199 = 0;
        for (Map<String, String> family : families) {
            String familyId = family.get("enantiomer") + family.get("family");
            double[] expected = EXPECTED.get(familyId);
            assertThat(expected).as("expected values for %s", familyId)
                    .isNotNull();
            Path posePath = RegressionHarness.requireInput(
                    "analysis/dcmb/controlled_campaign/raw/7A_WT_SAM_BOUND_"
                            + family.get("enantiomer") + "_s"
                            + family.get("representative_seed") + ".pdbqt",
                    CATEGORY, familyId + "_pose");
            List<Point3D> ligand = RegressionHarness.heavyPosePoints(
                    RegressionHarness.model(
                            RegressionHarness.readPdbqt(posePath),
                            Integer.parseInt(
                                    family.get("representative_mode"))));
            assertThat(ligand).isNotEmpty();

            double distance43 = RegressionHarness.minDistance(ligand, f43);
            double distance199 = RegressionHarness.minDistance(ligand, f199);
            RegressionHarness.record(CATEGORY,
                    familyId + "_F43_min_distance_A", expected[0], distance43,
                    CONVENTION, "REPRODUCED");
            RegressionHarness.record(CATEGORY,
                    familyId + "_F199_min_distance_A", expected[1], distance199,
                    CONVENTION, "REPRODUCED");
            assertThat(distance43).as("%s F43", familyId)
                    .isCloseTo(expected[0], within(TOLERANCE));
            assertThat(distance199).as("%s F199", familyId)
                    .isCloseTo(expected[1], within(TOLERANCE));
            if (distance43 <= 4.5) {
                contacts43++;
            }
            if (distance199 <= 4.5) {
                contacts199++;
            }
        }
        RegressionHarness.record(CATEGORY, "families_contacting_F43_of_11",
                11, contacts43, "contact = min distance <= 4.5 A",
                "REPRODUCED");
        RegressionHarness.record(CATEGORY, "families_contacting_F199_of_11",
                11, contacts199, "contact = min distance <= 4.5 A",
                "REPRODUCED");
        assertThat(contacts43).isEqualTo(11);
        assertThat(contacts199).isEqualTo(11);
    }
}
