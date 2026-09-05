package totah.lab.athena.regression;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Category 7: BRICS stage 12D shared-site anchor geometry. The retained
 * Region-1 placement set is EMPTY historically (0 placements passed the
 * gates; RDKit BRICS is not recomputable in Java), so the regression
 * recomputes only the six frozen anchor values from the embedded
 * pose_coordinates: SAM minimum distance and 7A/7B minimum surface
 * clearance (run_stage12d.py:209, surface_clear at lines 114-117 with
 * the VDW table at line 16; 7B atoms use the frozen
 * stage0/superpocket_transfer.json rigid transform).
 */
class BricsAnchorRegressionTest {

    private static final String CATEGORY = "stage12d_brics";
    /** Historical values are rounded to 3 decimals. */
    private static final double TOLERANCE = 0.001;

    private static final Map<String, Double> VDW = Map.of(
            "C", 1.70, "N", 1.55, "O", 1.52, "S", 1.80, "P", 1.80,
            "F", 1.47, "CL", 1.75, "BR", 1.85, "I", 1.98);

    /**
     * Fragment heavy-atom elements in library-mol atom order, taken from
     * the frozen fragment-library-conformers.sdf C1 conformer blocks
     * (SDF atom order == the RDKit mol order used by run_stage12d.py;
     * SMILES order differs, e.g. F005 is N-first and F010 is C-first).
     */
    private static final Map<String, List<String>> FRAGMENT_ELEMENTS = Map.of(
            "F002", List.of("C", "C", "C"),              // C1CC1
            "F004", List.of("N", "C", "C"),              // CNC (N-first)
            "F005", List.of("N", "C", "C", "C", "C"),    // C1CCNC1 (N-first)
            "F010", List.of("C", "F", "F", "F"),         // FC(F)F (C-first)
            "F011", List.of("C", "C", "C"),              // CCC
            "F012", List.of("C", "C", "O"));             // CCO

    private static final String CONVENTION =
            "run_stage12d.py: SAM_min = min heavy-atom distance to "
                    + "7B SAM; surface clearance = min over atoms of "
                    + "(distance - vdw(fragment) - vdw(protein)), VDW table "
                    + "line 16; 7B atoms transformed by stage0/"
                    + "superpocket_transfer.json (xyz @ R + t); pose "
                    + "coordinates embedded rounded to 3 decimals";

    @Test
    void retainedPlacementSetIsEmpty() {
        Path retained = RegressionHarness.requireInput(
                "analysis/mettl7-closure/stage12d_region1_fragments/retained-region1-placements.csv",
                CATEGORY, "retained_placements");
        List<Map<String, String>> rows = RegressionHarness.readCsv(retained);
        RegressionHarness.record(CATEGORY, "retained_region1_placements",
                0, rows.size(),
                "run_stage12d.py: no fragment passed every frozen gate; "
                        + "RDKit BRICS cleavage + ETKDG conformers not "
                        + "recomputable in Java",
                "REPRODUCED");
        assertThat(rows).isEmpty();
    }

    @Test
    void sharedSiteAnchorGeometryReproduces() {
        double[][] rotation = new double[3][3];
        double[] translation = new double[3];
        parseTransform(rotation, translation);

        List<RegressionHarness.PdbAtom> protein7a = atoms(
                "analysis/mettl7-closure/stage2/prepared/7A_WT_SAM_BOUND.pdb",
                rotation, translation, false, "7A").stream()
                .filter(atom -> !"SAM".equals(atom.residue())).toList();
        List<RegressionHarness.PdbAtom> atoms7b = atoms(
                "analysis/mettl7-closure/stage2/prepared/7B_WT_SAM_BOUND.pdb",
                rotation, translation, true, "7B");
        List<RegressionHarness.PdbAtom> protein7b = atoms7b.stream()
                .filter(atom -> !"SAM".equals(atom.residue())).toList();
        List<Point3D> sam7b = RegressionHarness.points(atoms7b.stream()
                .filter(atom -> "SAM".equals(atom.residue())).toList());

        List<Map<String, String>> anchors = RegressionHarness.readCsv(
                RegressionHarness.requireInput(
                        "analysis/mettl7-closure/stage12d_region1_fragments/shared-site-anchor-fragments.csv",
                        CATEGORY, "anchors"));
        assertThat(anchors).hasSize(6);

        for (Map<String, String> anchor : anchors) {
            String anchorId = anchor.get("anchor_id");
            List<Point3D> pose = parsePose(anchor.get("pose_coordinates"));
            List<String> elements = FRAGMENT_ELEMENTS.get(
                    anchor.get("fragment_id"));
            assertThat(elements).as(anchorId + " elements").isNotNull();
            assertThat(pose).as(anchorId + " pose atoms")
                    .hasSameSizeAs(elements);

            double samMin = RegressionHarness.minDistance(pose, sam7b);
            RegressionHarness.record(CATEGORY,
                    anchorId + "_" + anchor.get("fragment_id")
                            + "_SAM_min_distance_A",
                    anchor.get("SAM_min_distance_A"),
                    Double.toString(samMin), CONVENTION, "REPRODUCED");
            assertThat(samMin).as(anchorId + " SAM")
                    .isCloseTo(RegressionHarness.parseDouble(
                            anchor.get("SAM_min_distance_A")),
                            within(TOLERANCE));

            double clearance7a = surfaceClearance(pose, elements, protein7a);
            double clearance7b = surfaceClearance(pose, elements, protein7b);
            RegressionHarness.record(CATEGORY,
                    anchorId + "_" + anchor.get("fragment_id")
                            + "_7A_min_surface_clearance_A",
                    anchor.get("7A_min_surface_clearance_A"),
                    Double.toString(clearance7a), CONVENTION, "REPRODUCED");
            RegressionHarness.record(CATEGORY,
                    anchorId + "_" + anchor.get("fragment_id")
                            + "_7B_min_surface_clearance_A",
                    anchor.get("7B_min_surface_clearance_A"),
                    Double.toString(clearance7b), CONVENTION, "REPRODUCED");
            assertThat(clearance7a).as(anchorId + " 7A")
                    .isCloseTo(RegressionHarness.parseDouble(
                            anchor.get("7A_min_surface_clearance_A")),
                            within(TOLERANCE));
            assertThat(clearance7b).as(anchorId + " 7B")
                    .isCloseTo(RegressionHarness.parseDouble(
                            anchor.get("7B_min_surface_clearance_A")),
                            within(TOLERANCE));
        }
    }

    /** surface_clear: per fragment atom min over protein (d - lr - pr); overall min. */
    private static double surfaceClearance(
            List<Point3D> pose, List<String> elements,
            List<RegressionHarness.PdbAtom> protein) {
        double best = Double.POSITIVE_INFINITY;
        for (int index = 0; index < pose.size(); index++) {
            double ligandRadius = VDW.getOrDefault(elements.get(index), 1.7);
            for (RegressionHarness.PdbAtom atom : protein) {
                double proteinRadius =
                        VDW.getOrDefault(atom.element(), 1.7);
                best = Math.min(best,
                        pose.get(index).distance(atom.xyz())
                                - ligandRadius - proteinRadius);
            }
        }
        return best;
    }

    private static List<Point3D> parsePose(String coordinates) {
        List<Point3D> points = new ArrayList<>();
        for (String token : coordinates.split(";")) {
            String[] xyz = token.split(":")[1].split("/");
            points.add(new Point3D(Double.parseDouble(xyz[0]),
                    Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2])));
        }
        return points;
    }

    private List<RegressionHarness.PdbAtom> atoms(
            String repoRelative, double[][] rotation, double[] translation,
            boolean applyTransform, String metric) {
        List<RegressionHarness.PdbAtom> atoms = RegressionHarness.heavy(
                RegressionHarness.pdbAtoms(RegressionHarness.requireInput(
                        repoRelative, CATEGORY, metric)));
        if (!applyTransform) {
            return atoms;
        }
        return atoms.stream()
                .map(atom -> new RegressionHarness.PdbAtom(
                        atom.name(), atom.residue(), atom.chain(),
                        atom.number(), atom.element(),
                        FreeVolumeRegressionTest.transform(
                                atom.xyz(), rotation, translation)))
                .toList();
    }

    private void parseTransform(double[][] rotation, double[] translation) {
        Path path = RegressionHarness.requireInput(
                "analysis/mettl7-closure/stage0/superpocket_transfer.json",
                CATEGORY, "transfer");
        String json;
        try {
            json = Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        Matcher rotationMatcher = Pattern.compile(
                "\"rotation\"\\s*:\\s*\\[\\s*\\[([^]]+)\\]\\s*,\\s*\\[([^]]+)\\]\\s*,\\s*\\[([^]]+)\\]")
                .matcher(json);
        if (!rotationMatcher.find()) {
            throw new IllegalStateException("rotation not found in " + path);
        }
        for (int row = 0; row < 3; row++) {
            String[] values = rotationMatcher.group(row + 1).split(",");
            for (int column = 0; column < 3; column++) {
                rotation[row][column] =
                        Double.parseDouble(values[column].trim());
            }
        }
        Matcher translationMatcher = Pattern.compile(
                "\"translation\"\\s*:\\s*\\[([^]]+)]").matcher(json);
        if (!translationMatcher.find()) {
            throw new IllegalStateException("translation not found in " + path);
        }
        String[] values = translationMatcher.group(1).split(",");
        for (int index = 0; index < 3; index++) {
            translation[index] = Double.parseDouble(values[index].trim());
        }
    }
}
