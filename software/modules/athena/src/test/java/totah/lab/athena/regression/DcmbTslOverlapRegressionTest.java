package totah.lab.athena.regression;

import org.junit.jupiter.api.Test;
import totah.lab.athena.geometry.EnvelopeOptions;
import totah.lab.athena.geometry.GridVolume;
import totah.lab.gaia.geometry.Point3D;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Category 5: DCMB x TSL overlap (analyze_interference.py). Per
 * family x TSL-state pair (11 x 5 = 55): minimum heavy-atom distance,
 * shared occupied volume (envelope radius 1.7 A), steric core overlap
 * (radius 1.0 A), minimum distance to the TSL sulfur, and swept overlap
 * of the moved-atom paths (swept_metrics, lines 167-198).
 * Golden: dcmb_tsl_pairwise.csv and swept_volume.csv. Shared and swept
 * volumes go through {@link GridVolume#sharedEnvelopeVolume} (ported
 * verbatim from the Python shared_volume).
 */
class DcmbTslOverlapRegressionTest {

    private static final String CATEGORY = "dcmb_tsl_overlap";
    private static final double DISTANCE_TOLERANCE = 0.01;
    private static final double HALF_VOXEL = 0.0625;

    private static final String DISTANCE_CONVENTION =
            "analyze_interference.py:296-310: min over ligand x TSL "
                    + "heavy-atom pairs";
    private static final String SHARED_CONVENTION =
            "analyze_interference.py:87-96 shared_volume radius 1.7 A via "
                    + "GridVolume.sharedEnvelopeVolume (verbatim port)";
    private static final String CORE_CONVENTION =
            "analyze_interference.py:87-96 shared_volume radius 1.0 A via "
                    + "GridVolume.sharedEnvelopeVolume (verbatim port)";
    private static final String SWEPT_CONVENTION =
            "analyze_interference.py:167-198 swept_metrics: moved-atom "
                    + "paths (displacement > 0.001 A) densely sampled every "
                    + "0.2 A, swept overlap = shared_volume(ligand, paths, "
                    + "1.7) via GridVolume.sharedEnvelopeVolume";

    @Test
    void pairwiseAndSweptMetricsReproduce() {
        List<RegressionHarness.PdbAtom> originalProtein =
                originalProteinAtoms();
        List<Map<String, String>> pairwise = RegressionHarness.readCsv(
                RegressionHarness.requireInput(
                        "analysis/dcmb/dcmb_tsl_interference/dcmb_tsl_pairwise.csv",
                        CATEGORY, "pairwise"));
        List<Map<String, String>> swept = RegressionHarness.readCsv(
                RegressionHarness.requireInput(
                        "analysis/dcmb/dcmb_tsl_interference/swept_volume.csv",
                        CATEGORY, "swept"));
        assertThat(pairwise).hasSize(55);
        assertThat(swept).hasSize(55);

        Map<String, List<Point3D>> posesByFamily = posesByFamily();
        Map<Integer, List<RegressionHarness.PdbAtom>> relaxedByState =
                new LinkedHashMap<>();
        for (int state = 1; state <= 5; state++) {
            relaxedByState.put(state, RegressionHarness.heavy(
                    RegressionHarness.pdbAtoms(RegressionHarness.requireInput(
                            "analysis/dcmb/tsl_conformational_response/WT_METTL7A_SAM_TSL_relaxed_"
                                    + state + ".pdb",
                            CATEGORY, "relaxed_" + state))));
        }

        EnvelopeOptions sharedOptions = EnvelopeOptions.defaults();
        EnvelopeOptions coreOptions = new EnvelopeOptions(
                0.5, 1.0,
                "analyze_interference.py shared_volume radius 1.0 "
                        + "(steric core overlap)");

        double maxSweptOverlap = 0.0;
        for (Map<String, String> row : pairwise) {
            String familyId = row.get("family_id");
            int state = Integer.parseInt(row.get("tsl_state"));
            String metric = familyId + "_TSL" + state;
            List<Point3D> ligand = posesByFamily.get(familyId);
            List<RegressionHarness.PdbAtom> stateAtoms =
                    relaxedByState.get(state);
            List<Point3D> tsl = RegressionHarness.points(stateAtoms.stream()
                    .filter(atom -> "TSL".equals(atom.residue())).toList());
            Point3D tslSulfur = stateAtoms.stream()
                    .filter(atom -> "TSL".equals(atom.residue())
                            && "S".equals(atom.element()))
                    .map(RegressionHarness.PdbAtom::xyz).findFirst()
                    .orElseThrow();

            double minimum = RegressionHarness.minDistance(ligand, tsl);
            RegressionHarness.record(CATEGORY,
                    metric + "_minimum_distance_A",
                    row.get("minimum_distance_A"),
                    Double.toString(minimum), DISTANCE_CONVENTION,
                    "REPRODUCED");
            assertThat(minimum).as(metric)
                    .isCloseTo(RegressionHarness.parseDouble(
                            row.get("minimum_distance_A")),
                            within(DISTANCE_TOLERANCE));

            double shared = GridVolume.sharedEnvelopeVolume(
                    ligand, tsl, sharedOptions).overlapVolumeCubicAngstroms();
            RegressionHarness.record(CATEGORY,
                    metric + "_shared_occupied_volume_A3",
                    row.get("shared_occupied_volume_A3"),
                    Double.toString(shared), SHARED_CONVENTION, "REPRODUCED");
            assertThat(shared).as(metric)
                    .isCloseTo(RegressionHarness.parseDouble(
                            row.get("shared_occupied_volume_A3")),
                            within(HALF_VOXEL));

            double core = GridVolume.sharedEnvelopeVolume(
                    ligand, tsl, coreOptions).overlapVolumeCubicAngstroms();
            RegressionHarness.record(CATEGORY,
                    metric + "_steric_core_overlap_A3",
                    row.get("steric_core_overlap_A3"),
                    Double.toString(core), CORE_CONVENTION, "REPRODUCED");
            assertThat(core).as(metric)
                    .isCloseTo(RegressionHarness.parseDouble(
                            row.get("steric_core_overlap_A3")),
                            within(HALF_VOXEL));

            double toSulfur = ligand.stream()
                    .mapToDouble(point -> point.distance(tslSulfur)).min()
                    .orElseThrow();
            RegressionHarness.record(CATEGORY,
                    metric + "_minimum_distance_to_TSL_S_A",
                    row.get("minimum_distance_to_TSL_S_A"),
                    Double.toString(toSulfur),
                    "analyze_interference.py:309-310", "REPRODUCED");
            assertThat(toSulfur).as(metric)
                    .isCloseTo(RegressionHarness.parseDouble(
                            row.get("minimum_distance_to_TSL_S_A")),
                            within(DISTANCE_TOLERANCE));

            Map<String, String> sweptRow = swept.stream()
                    .filter(candidate -> candidate.get("family_id")
                                    .equals(familyId)
                            && candidate.get("tsl_state").equals(
                                    Integer.toString(state)))
                    .findFirst().orElseThrow();
            List<Point3D> sweptPoints = sweptPathSamples(
                    originalProtein, stateAtoms);
            assertThat(sweptPathCount(originalProtein, stateAtoms))
                    .as(metric + " moved atoms")
                    .isEqualTo(Integer.parseInt(sweptRow.get("moved_atoms")));
            double sweptOverlap = sweptPoints.isEmpty() ? 0.0
                    : GridVolume.sharedEnvelopeVolume(ligand, sweptPoints,
                            sharedOptions).overlapVolumeCubicAngstroms();
            RegressionHarness.record(CATEGORY, metric + "_swept_overlap_A3",
                    sweptRow.get("swept_overlap_A3"),
                    Double.toString(sweptOverlap), SWEPT_CONVENTION,
                    "REPRODUCED");
            assertThat(sweptOverlap).as(metric + " swept")
                    .isCloseTo(RegressionHarness.parseDouble(
                            sweptRow.get("swept_overlap_A3")),
                            within(HALF_VOXEL));
            maxSweptOverlap = Math.max(maxSweptOverlap, sweptOverlap);
        }
        RegressionHarness.record(CATEGORY, "max_swept_overlap_A3",
                1.125, maxSweptOverlap,
                "swept_volume.csv maximum (S6 x TSL2)", "REPRODUCED");
        assertThat(maxSweptOverlap).isCloseTo(1.125, within(HALF_VOXEL));
    }

    /** moved_paths: atoms with displacement > 0.001 A, keyed by (chain,num,name). */
    private static List<Point3D[]> movedPaths(
            List<RegressionHarness.PdbAtom> original,
            List<RegressionHarness.PdbAtom> relaxed) {

        Map<String, Point3D> old = new LinkedHashMap<>();
        for (RegressionHarness.PdbAtom atom : original) {
            old.put(atom.chain() + ":" + atom.number() + ":" + atom.name(),
                    atom.xyz());
        }
        List<Point3D[]> paths = new ArrayList<>();
        for (RegressionHarness.PdbAtom atom : relaxed) {
            Point3D start = old.get(atom.chain() + ":" + atom.number()
                    + ":" + atom.name());
            if (start == null) {
                continue;
            }
            if (atom.xyz().distance(start) > 0.001) {
                paths.add(new Point3D[]{start, atom.xyz()});
            }
        }
        return paths;
    }

    private static int sweptPathCount(
            List<RegressionHarness.PdbAtom> original,
            List<RegressionHarness.PdbAtom> relaxed) {
        return movedPaths(original, relaxed).size();
    }

    /** np.linspace(start, end, max(2, ceil(displacement / 0.2) + 1)). */
    private static List<Point3D> sweptPathSamples(
            List<RegressionHarness.PdbAtom> original,
            List<RegressionHarness.PdbAtom> relaxed) {
        List<Point3D> samples = new ArrayList<>();
        for (Point3D[] path : movedPaths(original, relaxed)) {
            double displacement = path[0].distance(path[1]);
            int n = Math.max(2,
                    (int) Math.ceil(displacement / 0.2) + 1);
            for (int index = 0; index < n; index++) {
                double fraction = n == 1 ? 0.0 : (double) index / (n - 1);
                samples.add(new Point3D(
                        path[0].x() + (path[1].x() - path[0].x()) * fraction,
                        path[0].y() + (path[1].y() - path[0].y()) * fraction,
                        path[0].z() + (path[1].z() - path[0].z()) * fraction));
            }
        }
        return samples;
    }

    private List<RegressionHarness.PdbAtom> originalProteinAtoms() {
        Path receptorPath = RegressionHarness.requireInput(
                "analysis/dcmb/sam_state/validated/WT_METTL7A_SAM_BOUND.pdb",
                CATEGORY, "receptor");
        return RegressionHarness.heavy(RegressionHarness.pdbAtoms(receptorPath))
                .stream()
                .filter(atom -> !List.of("SAM", "TSL", "SAH", "MTS")
                        .contains(atom.residue()))
                .toList();
    }

    private Map<String, List<Point3D>> posesByFamily() {
        Path familiesPath = RegressionHarness.requireInput(
                "analysis/dcmb/controlled_campaign/family_results.csv",
                CATEGORY, "family_results");
        Map<String, List<Point3D>> poses = new LinkedHashMap<>();
        for (Map<String, String> family : RegressionHarness.readCsv(
                familiesPath)) {
            if (!"7A_WT_SAM_BOUND".equals(family.get("condition"))) {
                continue;
            }
            String familyId = family.get("enantiomer") + family.get("family");
            poses.put(familyId, RegressionHarness.heavyPosePoints(
                    RegressionHarness.model(
                            RegressionHarness.readPdbqt(
                                    RegressionHarness.requireInput(
                                            "analysis/dcmb/controlled_campaign/raw/7A_WT_SAM_BOUND_"
                                                    + family.get("enantiomer")
                                                    + "_s" + family.get(
                                                            "representative_seed")
                                                    + ".pdbqt",
                                            CATEGORY, familyId + "_pose")),
                            Integer.parseInt(
                                    family.get("representative_mode")))));
        }
        return poses;
    }
}
