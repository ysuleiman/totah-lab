package totah.lab.athena.regression;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.junit.jupiter.api.Test;
import totah.lab.athena.geometry.FreeVolumeOptions;
import totah.lab.athena.geometry.GridVolume;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.pdbqt.PdbqtFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Category 3: local free volume around the DCMB medoids.
 *
 * <p>3a: {@code local_cavity_volume} (analyze_interference.py:120-127) —
 * 0.5 A grid over the ligand bounding box +/- 3.0 A; a voxel counts when
 * its minimum distance to the ligand is <= 3.0 A AND its minimum distance
 * to the protein is >= 2.0 A; volume = count * 0.125 A3. The historical
 * convention does NOT treat the ligand atoms themselves as occupied;
 * {@link GridVolume#localFreeVolume} deliberately does (documented
 * deviation), so both conventions are computed and recorded here.</p>
 *
 * <p>3b: {@code accessible_volume} (reciprocal_mutation_geometry.py:16-26)
 * — 0.5 A grid; a voxel counts when it lies within 6.0 A of the segment
 * between the DCMB-7A rank-1 centroid and the Kabsch-fitted DiffDock 7B
 * rank-2 centroid and is >= 2.0 A from every protein heavy atom. The
 * segment endpoints are derived here by porting the frozen
 * same_site_pose_analysis.py pipeline (CA-sequence Needleman-Wunsch +
 * Kabsch), since they were never frozen as constants.</p>
 */
class FreeVolumeRegressionTest {

    private static final String CATEGORY_3A = "local_cavity_volume";
    private static final String CATEGORY_3B = "reciprocal_accessible_volume";
    private static final double HALF_VOXEL = 0.0625;

    /** Historical fixed_local_cavity_A3 per family (REFERENCE.md 3a). */
    private static final Map<String, Double> EXPECTED_FIXED = Map.ofEntries(
            Map.entry("R1", 292.75), Map.entry("R2", 295.0),
            Map.entry("R3", 290.375), Map.entry("R4", 301.375),
            Map.entry("R5", 284.0), Map.entry("S1", 293.375),
            Map.entry("S2", 281.375), Map.entry("S3", 298.125),
            Map.entry("S4", 302.375), Map.entry("S5", 284.5),
            Map.entry("S6", 293.625));

    private static final Map<String, Double> EXPECTED_ACCESSIBLE = Map.of(
            "WT_METTL7A", 544.875,
            "METTL7A_F43L", 616.25,
            "WT_METTL7B", 703.125,
            "METTL7B_L43F", 713.0);

    private static final String HISTORICAL_CONVENTION =
            "analyze_interference.py:120-127 local_cavity_volume: voxel "
                    + "counts when min ligand distance <= 3.0 A and min "
                    + "protein distance >= 2.0 A; ligand NOT treated as "
                    + "occupied; np.arange(lo, hi + spacing/2, spacing)";

    private static final String GRIDVOLUME_CONVENTION =
            "GridVolume.localFreeVolume defaults (0.5/3.0/2.0); deliberate "
                    + "deviation: ligand (reference) atoms count as occupied "
                    + "(voxel must also be >= 2.0 A from every ligand atom), "
                    + "so values are shifted down vs the historical convention";

    @Test
    void localCavityVolumesFixedAndRelaxed() {
        List<Point3D> protein = originalProtein();
        Path familiesPath = RegressionHarness.requireInput(
                "analysis/dcmb/controlled_campaign/family_results.csv",
                CATEGORY_3A, "family_results");
        List<Map<String, String>> families =
                RegressionHarness.readCsv(familiesPath).stream()
                .filter(row -> "7A_WT_SAM_BOUND".equals(row.get("condition")))
                .toList();
        List<Map<String, String>> compatibility = RegressionHarness.readCsv(
                RegressionHarness.requireInput(
                        "analysis/dcmb/dcmb_tsl_interference/state_compatibility.csv",
                        CATEGORY_3A, "state_compatibility"));

        for (Map<String, String> family : families) {
            String familyId = family.get("enantiomer") + family.get("family");
            List<Point3D> ligand = RegressionHarness.heavyPosePoints(
                    RegressionHarness.model(
                            RegressionHarness.readPdbqt(
                                    RegressionHarness.requireInput(
                                            "analysis/dcmb/controlled_campaign/raw/7A_WT_SAM_BOUND_"
                                                    + family.get("enantiomer")
                                                    + "_s" + family.get(
                                                            "representative_seed")
                                                    + ".pdbqt",
                                            CATEGORY_3A, familyId + "_pose")),
                            Integer.parseInt(
                                    family.get("representative_mode"))));

            compareVolume(familyId + "_fixed", EXPECTED_FIXED.get(familyId),
                    ligand, protein);

            for (int state = 1; state <= 5; state++) {
                List<Point3D> relaxedProtein = relaxedProtein(state);
                int stateNumber = state;
                Map<String, String> golden = compatibility.stream()
                        .filter(row -> row.get("family_id").equals(familyId)
                                && row.get("tsl_state").equals(
                                        Integer.toString(stateNumber)))
                        .findFirst().orElseThrow();
                compareVolume(familyId + "_relaxed_TSL" + state,
                        RegressionHarness.parseDouble(
                                golden.get("relaxed_local_cavity_A3")),
                        ligand, relaxedProtein);
            }
        }
    }

    private void compareVolume(
            String metric, double historical, List<Point3D> ligand,
            List<Point3D> protein) {

        double historicalConvention = localCavityHistorical(ligand, protein);
        RegressionHarness.record(CATEGORY_3A,
                metric + "_historical_convention_A3", historical,
                historicalConvention, HISTORICAL_CONVENTION, "REPRODUCED");
        assertThat(historicalConvention).as("%s historical convention", metric)
                .isCloseTo(historical, within(HALF_VOXEL));

        double gridVolume = GridVolume.localFreeVolume(
                        ligand, clipToGrid(protein, ligand, 3.0, 2.0),
                        FreeVolumeOptions.defaults())
                .freeVolumeCubicAngstroms();
        RegressionHarness.record(CATEGORY_3A,
                metric + "_gridvolume_ligand_occupied_A3", historical,
                gridVolume, GRIDVOLUME_CONVENTION,
                gridVolume == historicalConvention
                        ? "REPRODUCED" : "DELTA_DOCUMENTED");
        // The Java layer is the new baseline: it must never count voxels
        // the historical convention counted, only fewer (ligand occupancy
        // can only remove free voxels).
        assertThat(gridVolume).as("%s GridVolume", metric)
                .isLessThanOrEqualTo(historicalConvention);
    }

    /** Exact port of analyze_interference.py local_cavity_volume. */
    static double localCavityHistorical(
            List<Point3D> ligand, List<Point3D> protein) {
        double spacing = 0.5;
        double padding = 3.0;
        double clearance = 2.0;
        double[] low = new double[3];
        double[] high = new double[3];
        bounds(ligand, padding, low, high);
        List<Point3D> clipped = clip(protein, low, high, clearance);
        int count = 0;
        int nx = axisCount(low[0], high[0], spacing);
        int ny = axisCount(low[1], high[1], spacing);
        int nz = axisCount(low[2], high[2], spacing);
        for (int ix = 0; ix < nx; ix++) {
            double x = low[0] + ix * spacing;
            for (int iy = 0; iy < ny; iy++) {
                double y = low[1] + iy * spacing;
                for (int iz = 0; iz < nz; iz++) {
                    double z = low[2] + iz * spacing;
                    if (minDistanceSquared(x, y, z, ligand)
                            > padding * padding) {
                        continue;
                    }
                    if (minDistanceSquared(x, y, z, clipped)
                            >= clearance * clearance) {
                        count++;
                    }
                }
            }
        }
        return count * spacing * spacing * spacing;
    }

    @Test
    void reciprocalMutationAccessibleVolumes() {
        Path path7a = RegressionHarness.requireInput(
                "resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb",
                CATEGORY_3B, "receptor_7A");
        Path path7b = RegressionHarness.requireInput(
                "experiments/METTL7B-v6_diffdock/target_protein.pdb",
                CATEGORY_3B, "receptor_7B");

        List<RegressionHarness.PdbAtom> atoms7a =
                RegressionHarness.pdbAtoms(path7a);
        List<RegressionHarness.PdbAtom> atoms7b =
                RegressionHarness.pdbAtoms(path7b);
        List<RegressionHarness.PdbAtom> ca7a = alphaCarbons(atoms7a);
        List<RegressionHarness.PdbAtom> ca7b = alphaCarbons(atoms7b);
        int[][] pairs = alignSequences(
                ca7b.stream().map(RegressionHarness.PdbAtom::residue).toList(),
                ca7a.stream().map(RegressionHarness.PdbAtom::residue).toList());
        double[][] moving = new double[pairs.length][3];
        double[][] fixed = new double[pairs.length][3];
        for (int index = 0; index < pairs.length; index++) {
            moving[index] = toArray(ca7b.get(pairs[index][0]).xyz());
            fixed[index] = toArray(ca7a.get(pairs[index][1]).xyz());
        }
        double[][] rotation = new double[3][3];
        double[] translation = new double[3];
        kabsch(moving, fixed, rotation, translation);

        Path rank1Path = RegressionHarness.requireInput(
                "analysis/dcmb/artifacts/diffdock/DCMB_diffdock_7A_rank1.pdbqt",
                CATEGORY_3B, "pose_7A_rank1");
        PdbqtFile rank1 = RegressionHarness.readPdbqt(rank1Path);
        List<Point3D> pose7a = RegressionHarness.heavyPosePoints(
                rank1.firstModel());
        Path sdfPath = RegressionHarness.requireInput(
                "experiments/METTL7B-v6_diffdock/rank2_confidence-0.3576555848121643.sdf",
                CATEGORY_3B, "pose_7B_rank2");
        List<Point3D> pose7b = sdfHeavyAtoms(sdfPath);
        assertThat(pose7a).as("DCMB heavy atoms in rank1 PDBQT")
                .hasSameSizeAs(pose7b);

        Point3D start = centroid(pose7a);
        Point3D end = transform(centroid(pose7b), rotation, translation);

        record AccessibleCase(String label, String receptorRelative,
                boolean applyTransform) {
        }
        List<AccessibleCase> cases = List.of(
                new AccessibleCase("WT_METTL7A",
                        "resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb",
                        false),
                new AccessibleCase("METTL7A_F43L",
                        "analysis/dcmb/reciprocal_mutation/METTL7A_F43L_fixed_backbone.pdb",
                        false),
                new AccessibleCase("WT_METTL7B",
                        "experiments/METTL7B-v6_diffdock/target_protein.pdb",
                        true),
                new AccessibleCase("METTL7B_L43F",
                        "analysis/dcmb/reciprocal_mutation/METTL7B_L43F_fixed_backbone.pdb",
                        true));
        for (AccessibleCase accessibleCase : cases) {
            List<Point3D> proteinPoints = RegressionHarness.points(
                    RegressionHarness.heavy(RegressionHarness.pdbAtoms(
                            RegressionHarness.requireInput(
                                    accessibleCase.receptorRelative(),
                                    CATEGORY_3B, accessibleCase.label()))));
            if (accessibleCase.applyTransform()) {
                proteinPoints = proteinPoints.stream()
                        .map(point -> transform(point, rotation, translation))
                        .toList();
            }
            double volume = accessibleVolume(proteinPoints, start, end);
            double historical = EXPECTED_ACCESSIBLE.get(accessibleCase.label());
            RegressionHarness.record(CATEGORY_3B,
                    accessibleCase.label() + "_local_accessible_grid_volume_A3",
                    historical, volume,
                    "reciprocal_mutation_geometry.py:16-26: 0.5 A grid, "
                            + "within 6.0 A of the rank1->Kabsch-fitted rank2 "
                            + "centroid segment, >= 2.0 A from protein heavy "
                            + "atoms; corridor convention not expressible via "
                            + "GridVolume (segment region, ligand not "
                            + "occupied), ported verbatim in the test",
                    "REPRODUCED");
            assertThat(volume).as(accessibleCase.label())
                    .isCloseTo(historical, within(HALF_VOXEL));
        }
    }

    /** Port of reciprocal_mutation_geometry.py accessible_volume. */
    private static double accessibleVolume(
            List<Point3D> protein, Point3D start, Point3D end) {
        double spacing = 0.5;
        double radius = 6.0;
        double clearance = 2.0;
        double[] low = new double[3];
        double[] high = new double[3];
        double[] s = toArray(start);
        double[] e = toArray(end);
        for (int axis = 0; axis < 3; axis++) {
            low[axis] = Math.min(s[axis], e[axis]) - radius;
            high[axis] = Math.max(s[axis], e[axis]) + radius;
        }
        List<Point3D> clipped = clip(protein, low, high, clearance);
        int count = 0;
        int nx = axisCount(low[0], high[0], spacing);
        int ny = axisCount(low[1], high[1], spacing);
        int nz = axisCount(low[2], high[2], spacing);
        for (int ix = 0; ix < nx; ix++) {
            double x = low[0] + ix * spacing;
            for (int iy = 0; iy < ny; iy++) {
                double y = low[1] + iy * spacing;
                for (int iz = 0; iz < nz; iz++) {
                    double z = low[2] + iz * spacing;
                    if (segmentDistanceSquared(x, y, z, s, e)
                            > radius * radius) {
                        continue;
                    }
                    if (minDistanceSquared(x, y, z, clipped)
                            >= clearance * clearance) {
                        count++;
                    }
                }
            }
        }
        return count * spacing * spacing * spacing;
    }

    /** Port of same_site_pose_analysis.py segment_distance. */
    private static double segmentDistanceSquared(
            double x, double y, double z, double[] start, double[] end) {
        double dx = end[0] - start[0];
        double dy = end[1] - start[1];
        double dz = end[2] - start[2];
        double denominator = dx * dx + dy * dy + dz * dz;
        double scale = denominator > 0
                ? ((x - start[0]) * dx + (y - start[1]) * dy
                        + (z - start[2]) * dz) / denominator
                : 0.0;
        scale = Math.max(0.0, Math.min(1.0, scale));
        double nx = start[0] + scale * dx - x;
        double ny = start[1] + scale * dy - y;
        double nz = start[2] + scale * dz - z;
        return nx * nx + ny * ny + nz * nz;
    }

    /** Port of same_site_pose_analysis.py align_sequences (NW +2/-1/-2). */
    static int[][] alignSequences(
            List<String> movingSequence, List<String> fixedSequence) {
        Map<String, String> aa = Map.ofEntries(
                Map.entry("ALA", "A"), Map.entry("ARG", "R"),
                Map.entry("ASN", "N"), Map.entry("ASP", "D"),
                Map.entry("CYS", "C"), Map.entry("GLN", "Q"),
                Map.entry("GLU", "E"), Map.entry("GLY", "G"),
                Map.entry("HIS", "H"), Map.entry("ILE", "I"),
                Map.entry("LEU", "L"), Map.entry("LYS", "K"),
                Map.entry("MET", "M"), Map.entry("PHE", "F"),
                Map.entry("PRO", "P"), Map.entry("SER", "S"),
                Map.entry("THR", "T"), Map.entry("TRP", "W"),
                Map.entry("TYR", "Y"), Map.entry("VAL", "V"));
        String moving = movingSequence.stream()
                .map(residue -> aa.getOrDefault(residue, "X"))
                .reduce("", String::concat);
        String fixed = fixedSequence.stream()
                .map(residue -> aa.getOrDefault(residue, "X"))
                .reduce("", String::concat);
        int n = moving.length();
        int m = fixed.length();
        int[][] score = new int[n + 1][m + 1];
        int[][] trace = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            score[i][0] = -2 * i;
        }
        for (int j = 0; j <= m; j++) {
            score[0][j] = -2 * j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int diagonal = score[i - 1][j - 1]
                        + (moving.charAt(i - 1) == fixed.charAt(j - 1)
                                ? 2 : -1);
                int up = score[i - 1][j] - 2;
                int left = score[i][j - 1] - 2;
                // numpy argmax order: diagonal, up, left (first max wins)
                int best = diagonal;
                int direction = 0;
                if (up > best) {
                    best = up;
                    direction = 1;
                }
                if (left > best) {
                    best = left;
                    direction = 2;
                }
                trace[i][j] = direction;
                score[i][j] = best;
            }
        }
        List<int[]> pairs = new ArrayList<>();
        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            int direction = (i > 0 && j > 0) ? trace[i][j] : (i > 0 ? 1 : 2);
            if (direction == 0) {
                pairs.add(new int[]{i - 1, j - 1});
                i--;
                j--;
            } else if (direction == 1) {
                i--;
            } else {
                j--;
            }
        }
        return pairs.reversed().toArray(new int[0][]);
    }

    /**
     * Port of same_site_pose_analysis.py kabsch: row-vector convention,
     * {@code fitted = moving @ rotation + translation}.
     */
    static void kabsch(
            double[][] moving, double[][] fixed,
            double[][] rotation, double[] translation) {
        double[] cm = columnMeans(moving);
        double[] cf = columnMeans(fixed);
        double[][] h = new double[3][3];
        for (int index = 0; index < moving.length; index++) {
            for (int a = 0; a < 3; a++) {
                for (int b = 0; b < 3; b++) {
                    h[a][b] += (moving[index][a] - cm[a])
                            * (fixed[index][b] - cf[b]);
                }
            }
        }
        SingularValueDecomposition svd =
                new SingularValueDecomposition(new Array2DRowRealMatrix(h));
        RealMatrix u = svd.getU();
        RealMatrix vt = svd.getVT();
        RealMatrix r = u.multiply(vt);
        if (new LUDecomposition(r).getDeterminant() < 0) {
            // u[:, -1] *= -1; r = u @ vt
            RealMatrix flipped = u.copy();
            for (int row = 0; row < 3; row++) {
                flipped.setEntry(row, 2, -flipped.getEntry(row, 2));
            }
            r = flipped.multiply(vt);
        }
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                rotation[a][b] = r.getEntry(a, b);
            }
        }
        for (int b = 0; b < 3; b++) {
            translation[b] = cf[b];
            for (int a = 0; a < 3; a++) {
                translation[b] -= cm[a] * rotation[a][b];
            }
        }
    }

    static Point3D transform(
            Point3D point, double[][] rotation, double[] translation) {
        double[] p = toArray(point);
        double[] out = new double[3];
        for (int b = 0; b < 3; b++) {
            out[b] = translation[b];
            for (int a = 0; a < 3; a++) {
                out[b] += p[a] * rotation[a][b];
            }
        }
        return new Point3D(out[0], out[1], out[2]);
    }

    private static double[] columnMeans(double[][] points) {
        double[] mean = new double[3];
        for (double[] point : points) {
            for (int axis = 0; axis < 3; axis++) {
                mean[axis] += point[axis];
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            mean[axis] /= points.length;
        }
        return mean;
    }

    private static List<RegressionHarness.PdbAtom> alphaCarbons(
            List<RegressionHarness.PdbAtom> atoms) {
        Map<String, String> standard = Map.ofEntries(
                Map.entry("ALA", "A"), Map.entry("ARG", "R"),
                Map.entry("ASN", "N"), Map.entry("ASP", "D"),
                Map.entry("CYS", "C"), Map.entry("GLN", "Q"),
                Map.entry("GLU", "E"), Map.entry("GLY", "G"),
                Map.entry("HIS", "H"), Map.entry("ILE", "I"),
                Map.entry("LEU", "L"), Map.entry("LYS", "K"),
                Map.entry("MET", "M"), Map.entry("PHE", "F"),
                Map.entry("PRO", "P"), Map.entry("SER", "S"),
                Map.entry("THR", "T"), Map.entry("TRP", "W"),
                Map.entry("TYR", "Y"), Map.entry("VAL", "V"));
        return atoms.stream()
                .filter(atom -> atom.name().equals("CA"))
                .filter(atom -> atom.chain().equals("A"))
                .filter(atom -> standard.containsKey(atom.residue()))
                .toList();
    }

    /** Minimal SDF V2000 coordinate-block parse; heavy atoms only. */
    private static List<Point3D> sdfHeavyAtoms(Path path) {
        List<String> lines;
        try {
            lines = java.nio.file.Files.readAllLines(path);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        int atomCount = Integer.parseInt(lines.get(3).substring(0, 3).trim());
        List<Point3D> atoms = new ArrayList<>();
        for (int index = 4; index < 4 + atomCount; index++) {
            String line = lines.get(index);
            String element = line.substring(31, 34).trim();
            if ("H".equalsIgnoreCase(element)) {
                continue;
            }
            atoms.add(new Point3D(
                    Double.parseDouble(line.substring(0, 10).trim()),
                    Double.parseDouble(line.substring(10, 20).trim()),
                    Double.parseDouble(line.substring(20, 30).trim())));
        }
        return atoms;
    }

    private List<Point3D> originalProtein() {
        Path receptorPath = RegressionHarness.requireInput(
                "analysis/dcmb/sam_state/validated/WT_METTL7A_SAM_BOUND.pdb",
                CATEGORY_3A, "receptor");
        return RegressionHarness.points(RegressionHarness.heavy(
                RegressionHarness.pdbAtoms(receptorPath)).stream()
                .filter(atom -> !List.of("SAM", "TSL", "SAH", "MTS")
                        .contains(atom.residue()))
                .toList());
    }

    private List<Point3D> relaxedProtein(int state) {
        Path path = RegressionHarness.requireInput(
                "analysis/dcmb/tsl_conformational_response/WT_METTL7A_SAM_TSL_relaxed_"
                        + state + ".pdb",
                CATEGORY_3A, "relaxed_" + state);
        return RegressionHarness.points(RegressionHarness.heavy(
                RegressionHarness.pdbAtoms(path)).stream()
                .filter(atom -> !List.of("SAM", "TSL", "SAH", "MTS")
                        .contains(atom.residue()))
                .toList());
    }

    private static List<Point3D> clipToGrid(
            List<Point3D> points, List<Point3D> reference, double padding,
            double margin) {
        double[] low = new double[3];
        double[] high = new double[3];
        bounds(reference, padding, low, high);
        return clip(points, low, high, margin);
    }

    /** Drops points that cannot lie within {@code margin} of any grid voxel. */
    private static List<Point3D> clip(
            List<Point3D> points, double[] low, double[] high, double margin) {
        return points.stream().filter(point ->
                        point.x() >= low[0] - margin
                                && point.x() <= high[0] + margin
                                && point.y() >= low[1] - margin
                                && point.y() <= high[1] + margin
                                && point.z() >= low[2] - margin
                                && point.z() <= high[2] + margin)
                .toList();
    }

    private static void bounds(
            List<Point3D> points, double margin, double[] low, double[] high) {
        low[0] = Double.POSITIVE_INFINITY;
        low[1] = Double.POSITIVE_INFINITY;
        low[2] = Double.POSITIVE_INFINITY;
        high[0] = Double.NEGATIVE_INFINITY;
        high[1] = Double.NEGATIVE_INFINITY;
        high[2] = Double.NEGATIVE_INFINITY;
        for (Point3D point : points) {
            low[0] = Math.min(low[0], point.x());
            low[1] = Math.min(low[1], point.y());
            low[2] = Math.min(low[2], point.z());
            high[0] = Math.max(high[0], point.x());
            high[1] = Math.max(high[1], point.y());
            high[2] = Math.max(high[2], point.z());
        }
        for (int axis = 0; axis < 3; axis++) {
            low[axis] -= margin;
            high[axis] += margin;
        }
    }

    /** numpy arange(lo, hi + spacing / 2, spacing) point count. */
    private static int axisCount(double lo, double hi, double spacing) {
        double limit = hi + spacing / 2.0;
        int count = 0;
        while (lo + count * spacing < limit) {
            count++;
        }
        return count;
    }

    private static double minDistanceSquared(
            double x, double y, double z, List<Point3D> atoms) {
        double best = Double.POSITIVE_INFINITY;
        for (Point3D atom : atoms) {
            double dx = atom.x() - x;
            double dy = atom.y() - y;
            double dz = atom.z() - z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < best) {
                best = distanceSquared;
            }
        }
        return best;
    }

    private static Point3D centroid(List<Point3D> points) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (Point3D point : points) {
            x += point.x();
            y += point.y();
            z += point.z();
        }
        return new Point3D(x / points.size(), y / points.size(),
                z / points.size());
    }

    private static double[] toArray(Point3D point) {
        return new double[]{point.x(), point.y(), point.z()};
    }
}
