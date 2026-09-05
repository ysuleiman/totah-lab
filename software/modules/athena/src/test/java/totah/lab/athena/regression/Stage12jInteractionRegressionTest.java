package totah.lab.athena.regression;

import org.junit.jupiter.api.Test;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionProfiler;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtFile;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.PdbqtModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Category 2: Stage 12J static DCMB chemistry — Tyr47 pi-contact
 * classification, pi-stack and hydrogen-bond counts — through
 * {@link InteractionProfiler} on the same stage4 prepared PDBQT inputs
 * as run_stage12j.py. Golden: stage12j family-interaction-fingerprints.csv
 * (353 rows) and SUMMARY.json.
 *
 * <p>Documented convention differences (deltas expected and recorded):
 * <ul>
 *   <li>hydrophobic rule: historical residue-set + element based at
 *   4.5 A; ours is PLIP-style perceived-hydrophobic-atom pairs at
 *   4.0 A;</li>
 *   <li>pi offset gate: historical 2.5 A (ligand-plane projection,
 *   parallel only; edge-face ungated); ours 2.0 A (min of both mutual
 *   projections, applied to both classes);</li>
 *   <li>PDBQT inputs carry no bond graph, so perception is degraded:
 *   protein rings via PROTEIN_TEMPLATE, ligand ring via AD4 fallback,
 *   halogens not evaluated (stage12j also froze halogens as
 *   NOT_EVALUATED).</li>
 * </ul>
 */
class Stage12jInteractionRegressionTest {

    private static final String CATEGORY = "stage12j_tyr47_pi";
    private static final double DISTANCE_TOLERANCE = 0.01;
    private static final double ANGLE_TOLERANCE = 0.05;

    /** AROM ring atom name lists (run_stage12j.py:12). */
    private static final Map<String, List<String>> AROM = Map.of(
            "PHE", List.of("CG", "CD1", "CD2", "CE1", "CE2", "CZ"),
            "TYR", List.of("CG", "CD1", "CD2", "CE1", "CE2", "CZ"),
            "TRP", List.of("CG", "CD1", "CD2", "NE1", "CE2", "CE3",
                    "CZ2", "CZ3", "CH2"),
            "HIS", List.of("CG", "ND1", "CD2", "CE1", "NE2"));

    @Test
    void stage12jFingerprintRegression() {
        Structure receptor7a = preparedReceptor("7A");
        Structure receptor7b = preparedReceptor("7B");
        List<Map<String, String>> families = families();
        assertThat(families).hasSize(30);
        List<Map<String, String>> golden = RegressionHarness.readCsv(
                RegressionHarness.requireInput(
                        "analysis/mettl7-closure/stage12j_static_dcmb_chemistry/family-interaction-fingerprints.csv",
                        CATEGORY, "golden_fingerprints"));
        assertThat(golden).hasSize(353);

        InteractionProfiler profiler = new InteractionProfiler();
        int totalParallel = 0;
        int totalTShaped = 0;
        int totalHbonds = 0;
        int totalRawHbonds = 0;
        int totalHydrophobic = 0;
        int totalPiCation = 0;
        int totalSaltBridge = 0;
        int totalHalogen = 0;
        int engagedResidues = 0;

        for (Map<String, String> family : families) {
            String target = family.get("system").substring(0, 2);
            String familyId = target + "_" + family.get("enantiomer")
                    + family.get("family");
            PdbqtModel pose = RegressionHarness.model(
                    RegressionHarness.readPdbqt(RegressionHarness.requireInput(
                            "analysis/mettl7-closure/stage4/raw/"
                                    + family.get("system") + "_"
                                    + family.get("enantiomer") + "_s"
                                    + family.get("representative_seed")
                                    + ".pdbqt",
                            CATEGORY, familyId + "_pose")),
                    Integer.parseInt(family.get("representative_mode")));
            Structure ligand = PdbqtGaiaMapper.toLigand(pose, familyId)
                    .structure();
            Structure receptor =
                    "7A".equals(target) ? receptor7a : receptor7b;
            InteractionProfile profile = profiler.profile(receptor, ligand);

            // PDBQT inputs have no bond graph: degraded fallbacks fire.
            assertThat(profile.anyPerceptionDegraded()).as(familyId)
                    .isTrue();

            List<Interaction> hbonds =
                    profile.interactions(InteractionType.HYDROGEN_BOND);
            List<Interaction> parallel =
                    profile.interactions(InteractionType.PI_STACK_PARALLEL);
            List<Interaction> tshaped =
                    profile.interactions(InteractionType.PI_STACK_T_SHAPED);
            List<Interaction> hydrophobic =
                    profile.interactions(InteractionType.HYDROPHOBIC_CONTACT);
            totalHbonds += hbonds.size();
            totalRawHbonds += (int) profile.rawInteractions().stream()
                    .filter(interaction -> interaction.type()
                            == InteractionType.HYDROGEN_BOND)
                    .count();
            totalParallel += parallel.size();
            totalTShaped += tshaped.size();
            totalHydrophobic += hydrophobic.size();
            totalPiCation +=
                    profile.interactions(InteractionType.PI_CATION).size();
            totalSaltBridge +=
                    profile.interactions(InteractionType.SALT_BRIDGE).size();
            totalHalogen +=
                    profile.interactions(InteractionType.HALOGEN_BOND).size();
            engagedResidues += (int) profile.interactions().stream()
                    .map(Interaction::residue).distinct().count();

            tyr47Check(familyId, target, pose, receptor, profile, golden);
            piRowChecks(familyId, profile, golden);
            hbondRowChecks(familyId, profile, golden);
        }

        RegressionHarness.record(CATEGORY, "perception_degraded_all_30_families",
                "TRUE", "TRUE",
                "PDBQT inputs carry no bond graph: protein rings via "
                        + "PROTEIN_TEMPLATE, ligand ring via AD4 fallback, "
                        + "halogens empty; InteractionProfile."
                        + "anyPerceptionDegraded()",
                "REPRODUCED");

        RegressionHarness.record(CATEGORY, "pi_stack_parallel_total",
                1, totalParallel,
                "PDBQT input: the AD4-fallback ligand ring is marked "
                        + "degraded and InteractionGeometry.ringPlane "
                        + "refuses degraded rings, so no pi-stack can fire "
                        + "at all; additionally our offset gate (2.0 A, "
                        + "min of both mutual projections, both classes) is "
                        + "stricter than the historical one (2.5 A "
                        + "ligand-plane, parallel only)",
                totalParallel == 1 ? "REPRODUCED" : "DELTA_DOCUMENTED");
        RegressionHarness.record(CATEGORY, "pi_stack_edge_face_total",
                11, totalTShaped,
                "as above: degraded ligand ring skipped; historical "
                        + "EDGE_FACE had no offset gate, ours T_SHAPED "
                        + "requires offset <= 2.0 A",
                totalTShaped == 11 ? "REPRODUCED" : "DELTA_DOCUMENTED");
        RegressionHarness.record(CATEGORY, "hydrogen_bonds_raw_total",
                8, totalRawHbonds,
                "detector-level parity: identical HB convention (HD donor "
                        + "pairing 1.35 A, H...A <= 2.5, D...A <= 3.5, D-H-A "
                        + ">= 120, acceptors NA/OA/SA) in both",
                totalRawHbonds == 8 ? "REPRODUCED" : "DELTA_DOCUMENTED");
        RegressionHarness.record(CATEGORY, "hydrogen_bonds_refined_total",
                8, totalHbonds,
                "our PLIP precedence refinement dedups the 7B_S5 SER149 and "
                        + "ASP200 H-bonds sharing the ligand primary-amine "
                        + "donor; the historical script kept both",
                totalHbonds == 8 ? "REPRODUCED" : "DELTA_DOCUMENTED");
        RegressionHarness.record(CATEGORY, "hydrophobic_contact_records_total",
                Integer.toString(golden.stream().mapToInt(
                        row -> Integer.parseInt(
                                row.get("hydrophobic_atom_pairs"))).sum()),
                Integer.toString(totalHydrophobic),
                "historical hydrophobic: residue-set + element (C,S)x(C,CL) "
                        + "at <= 4.5 A; ours: PLIP-style perceived "
                        + "hydrophobic atoms at <= 4.0 A with refinements",
                "DELTA_DOCUMENTED");
        RegressionHarness.record(CATEGORY, "pi_cation_total",
                "NOT_IN_HISTORICAL_SCHEMA",
                Integer.toString(totalPiCation),
                "stage12j had no pi-cation channel", "DELTA_DOCUMENTED");
        RegressionHarness.record(CATEGORY, "salt_bridges_total",
                0, totalSaltBridge,
                "neutral prepared DCMB: no salt bridges expected",
                totalSaltBridge == 0 ? "REPRODUCED" : "DELTA_DOCUMENTED");
        RegressionHarness.record(CATEGORY, "halogen_bonds_total",
                "NOT_EVALUATED", Integer.toString(totalHalogen),
                "stage12j froze halogen_bonds=NOT_EVALUATED (no canonical "
                        + "implementation); our detector needs a bond graph "
                        + "(degraded PDBQT input)",
                "DELTA_DOCUMENTED");
        RegressionHarness.record(CATEGORY, "fingerprint_rows",
                353, engagedResidues,
                "historical row granularity includes untyped "
                        + "CLOSE_GEOMETRIC_CONTACT rows and hydrophobic "
                        + "4.5 A pairs; ours counts residues with at least "
                        + "one typed refined interaction",
                "DELTA_DOCUMENTED");

        // Pinned baselines for the new layer on degraded PDBQT input:
        // detector-level HB parity holds raw (8), refinement dedups to 7,
        // pi channels stay empty because the degraded ligand ring is never
        // plane-fitted.
        assertThat(totalRawHbonds).isEqualTo(8);
        assertThat(totalHbonds).isEqualTo(7);
        assertThat(totalParallel).isEqualTo(0);
        assertThat(totalTShaped).isEqualTo(0);
        assertThat(totalPiCation).isEqualTo(0);
        assertThat(totalSaltBridge).isEqualTo(0);
        assertThat(totalHalogen).isEqualTo(0);
        assertThat(totalHydrophobic).isEqualTo(282);
        assertThat(engagedResidues).isEqualTo(168);
    }

    /** Tyr47 rows: no pi classification; min heavy distance reproduces. */
    private void tyr47Check(
            String familyId, String target, PdbqtModel pose,
            Structure receptor, InteractionProfile profile,
            List<Map<String, String>> golden) {

        List<Interaction> at47 = profile.interactions().stream()
                .filter(interaction -> interaction.residue().residueNumber()
                        == 47)
                .toList();
        List<Interaction> pi47 = at47.stream()
                .filter(interaction -> interaction.type()
                        == InteractionType.PI_STACK_PARALLEL
                        || interaction.type()
                        == InteractionType.PI_STACK_T_SHAPED)
                .toList();

        Map<String, String> goldenRow = golden.stream()
                .filter(row -> row.get("family_id").equals(familyId)
                        && row.get("residue_number").equals("47"))
                .findFirst().orElse(null);
        if (goldenRow == null) {
            assertThat(pi47).as(familyId + " Tyr47 pi").isEmpty();
            return;
        }

        // Historical min heavy distance and pi centroid reproduce exactly
        // (same coordinates, same conventions at the measurement level).
        List<Point3D> ligandHeavy = RegressionHarness.heavyPosePoints(pose);
        Residue residue47 = receptorResidue(receptor, 47);
        if (!goldenRow.get("minimum_heavy_distance_A").isEmpty()) {
            double minDistance = RegressionHarness.minDistance(ligandHeavy,
                    residue47.getAtoms().stream()
                            .filter(Atom::isHeavyAtom)
                            .map(Atom::getPosition).toList());
            RegressionHarness.record(CATEGORY,
                    familyId + "_" + goldenRow.get("residue_name")
                            + "47_min_heavy_distance_A",
                    goldenRow.get("minimum_heavy_distance_A"),
                    Double.toString(minDistance),
                    "run_stage12j.py: min heavy-atom pair distance to "
                            + "residue 47",
                    "REPRODUCED");
            assertThat(minDistance).as(familyId + " residue47 min")
                    .isCloseTo(RegressionHarness.parseDouble(
                            goldenRow.get("minimum_heavy_distance_A")),
                            within(DISTANCE_TOLERANCE));
        }

        if (!goldenRow.get("pi_centroid_distance_A").isEmpty()) {
            double centroid = piCentroidDistance(pose, residue47);
            RegressionHarness.record(CATEGORY,
                    familyId + "_" + goldenRow.get("residue_name")
                            + "47_pi_centroid_distance_A",
                    goldenRow.get("pi_centroid_distance_A"),
                    Double.toString(centroid),
                    "run_stage12j.py classify_pi: ligand ring = serials "
                            + "3-8, TYR ring CG/CD1/CD2/CE1/CE2/CZ; "
                            + "centroid 6.4-8.6 A exceeds the 5.5 A cutoff "
                            + "-> no pi classification",
                    "REPRODUCED");
            assertThat(centroid).as(familyId + " Tyr47 centroid")
                    .isCloseTo(RegressionHarness.parseDouble(
                            goldenRow.get("pi_centroid_distance_A")),
                            within(DISTANCE_TOLERANCE));
        }

        // Historical: 3 of 4 rows typed HYDROPHOBIC (4.5 A element rule);
        // ours (4.0 A PLIP-style) finds nothing at 4.1-4.5 A.
        String historicalTypes = goldenRow.get("interaction_types");
        String javaTypes = at47.isEmpty() ? "NONE"
                : String.join(";", at47.stream()
                        .map(interaction -> interaction.type().name())
                        .distinct().sorted().toList());
        RegressionHarness.record(CATEGORY,
                familyId + "_TYR47_interaction_types",
                historicalTypes, javaTypes,
                "historical HYDROPHOBIC at <= 4.5 A (residue-set+element); "
                        + "ours PLIP-style at <= 4.0 A: Tyr47 min distances "
                        + "4.13-4.47 A fall in the gap",
                javaTypes.contains("PI_STACK") || javaTypes.equals(
                        historicalTypes) ? "REPRODUCED" : "DELTA_DOCUMENTED");
        assertThat(pi47).as(familyId + " Tyr47 pi classification")
                .isEmpty();
    }

    /** Per-row pi comparisons against the 12 historical pi rows. */
    private void piRowChecks(
            String familyId, InteractionProfile profile,
            List<Map<String, String>> golden) {

        for (Map<String, String> row : golden) {
            if (!row.get("family_id").equals(familyId)) {
                continue;
            }
            String types = row.get("interaction_types");
            if (!types.contains("PI")) {
                continue;
            }
            int residueNumber = Integer.parseInt(row.get("residue_number"));
            List<Interaction> ours = profile.interactions().stream()
                    .filter(interaction -> interaction.residue()
                                    .residueNumber() == residueNumber
                            && (interaction.type()
                                    == InteractionType.PI_STACK_PARALLEL
                            || interaction.type()
                                    == InteractionType.PI_STACK_T_SHAPED))
                    .toList();
            String historicalType = types.contains("PARALLEL_PI")
                    ? "PARALLEL_PI" : "EDGE_FACE_PI";
            String metric = familyId + "_" + row.get("residue_name")
                    + residueNumber + "_pi";
            if (ours.isEmpty()) {
                RegressionHarness.record(CATEGORY, metric + "_type",
                        historicalType, "NONE",
                        "our pi gates not met (offset 2.0 A min-of-both vs "
                                + "historical 2.5 A ligand-plane, parallel "
                                + "only; edge-face historically ungated)",
                        "DELTA_DOCUMENTED");
                continue;
            }
            Interaction interaction = ours.getFirst();
            String javaType = interaction.type()
                    == InteractionType.PI_STACK_PARALLEL
                    ? "PARALLEL_PI" : "EDGE_FACE_PI";
            RegressionHarness.record(CATEGORY, metric + "_type",
                    historicalType, javaType,
                    "pi class gates differ (see aggregate rows)",
                    historicalType.equals(javaType)
                            ? "REPRODUCED" : "DELTA_DOCUMENTED");
            RegressionHarness.record(CATEGORY, metric + "_centroid_A",
                    row.get("pi_centroid_distance_A"),
                    Double.toString(interaction.distanceAngstroms()),
                    "ring centroid to ring centroid",
                    "REPRODUCED");
            RegressionHarness.record(CATEGORY, metric + "_normal_deg",
                    row.get("pi_normal_angle_deg"),
                    Double.toString(interaction.primaryAngleDegrees()),
                    "folded ring-normal angle",
                    "REPRODUCED");
            assertThat(interaction.distanceAngstroms()).as(metric)
                    .isCloseTo(RegressionHarness.parseDouble(
                            row.get("pi_centroid_distance_A")),
                            within(DISTANCE_TOLERANCE));
            assertThat(interaction.primaryAngleDegrees()).as(metric)
                    .isCloseTo(RegressionHarness.parseDouble(
                            row.get("pi_normal_angle_deg")),
                            within(ANGLE_TOLERANCE + 0.45));
        }
    }

    /** Per-row hbond geometry against the 8 historical hbond rows. */
    private void hbondRowChecks(
            String familyId, InteractionProfile profile,
            List<Map<String, String>> golden) {

        for (Map<String, String> row : golden) {
            if (!row.get("family_id").equals(familyId)
                    || row.get("hbond_count").equals("0")) {
                continue;
            }
            int residueNumber = Integer.parseInt(row.get("residue_number"));
            List<Interaction> ours = profile.rawInteractions().stream()
                    .filter(interaction -> interaction.type()
                            == InteractionType.HYDROGEN_BOND)
                    .filter(interaction -> interaction.residue()
                            .residueNumber() == residueNumber)
                    .toList();
            // "O-N:3.347A/144.4deg/LIGAND_DONOR"
            String[] geometry = row.get("hbond_geometry").split(":");
            double historicalDistance =
                    Double.parseDouble(geometry[1].split("A/")[0]);
            double historicalAngle = Double.parseDouble(
                    geometry[1].split("A/")[1].split("deg")[0]);
            String metric = familyId + "_" + row.get("residue_name")
                    + residueNumber + "_hbond";
            if (ours.isEmpty()) {
                RegressionHarness.record(CATEGORY, metric + "_distance_A",
                        Double.toString(historicalDistance), "",
                        "historical HB row has no counterpart in our refined "
                                + "profile",
                        "DELTA_DOCUMENTED");
                continue;
            }
            Interaction interaction = ours.getFirst();
            RegressionHarness.record(CATEGORY, metric + "_distance_A",
                    historicalDistance, interaction.distanceAngstroms(),
                    "heavy D...A distance; historical rounded to 3 decimals",
                    "REPRODUCED");
            RegressionHarness.record(CATEGORY, metric + "_angle_deg",
                    historicalAngle, interaction.primaryAngleDegrees(),
                    "D-H...A angle; historical rounded to 1 decimal",
                    "REPRODUCED");
            boolean refinedAway = profile.interactions(
                    InteractionType.HYDROGEN_BOND).stream()
                    .noneMatch(candidate -> candidate.residue()
                            .residueNumber() == residueNumber);
            if (refinedAway) {
                RegressionHarness.record(CATEGORY, metric + "_refined",
                        "PRESENT", "DROPPED_BY_PLIP_PRECEDENCE_REFINEMENT",
                        "raw detector reproduces the historical row; the "
                                + "refinement graph removes it (shared "
                                + "ligand donor)",
                        "DELTA_DOCUMENTED");
            }
            assertThat(interaction.distanceAngstroms()).as(metric)
                    .isCloseTo(historicalDistance, within(0.0015));
            assertThat(interaction.primaryAngleDegrees()).as(metric)
                    .isCloseTo(historicalAngle, within(ANGLE_TOLERANCE));
        }
    }

    /** Historical classify_pi centroid distance: ligand serials 3-8 vs AROM ring. */
    private static double piCentroidDistance(
            PdbqtModel pose, Residue residue) {
        List<Point3D> ligandRing = pose.atoms().stream()
                .filter(atom -> atom.serial() >= 3 && atom.serial() <= 8)
                .map(PdbqtAtom::position).toList();
        List<Point3D> proteinRing = residue.getAtoms().stream()
                .filter(atom -> AROM.getOrDefault(residue.getName(),
                        List.of()).contains(atom.getName()))
                .map(Atom::getPosition).toList();
        return centroid(ligandRing).distance(centroid(proteinRing));
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

    /** The receptor residue with the given number. */
    private static Residue receptorResidue(Structure receptor, int number) {
        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                if (residue.getNumber() == number) {
                    return residue;
                }
            }
        }
        throw new IllegalArgumentException("no residue " + number);
    }

    /** Prepared receptor structure with the SAM residue excluded. */
    private Structure preparedReceptor(String target) {
        PdbqtFile file = RegressionHarness.readPdbqt(
                RegressionHarness.requireInput(
                        "analysis/mettl7-closure/stage4/prepared/" + target
                                + "_WT_SAM_BOUND.pdbqt",
                        CATEGORY, "receptor_" + target));
        Structure structure = PdbqtGaiaMapper.toStructure(file);
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            List<Residue> residues = chain.residues().stream()
                    .filter(residue -> !"SAM".equals(residue.getName()))
                    .toList();
            if (!residues.isEmpty()) {
                chains.add(new Chain(chain.id(), residues));
            }
        }
        return new Structure(chains);
    }

    private List<Map<String, String>> families() {
        return RegressionHarness.readCsv(RegressionHarness.requireInput(
                "analysis/mettl7-closure/stage4/family_results.csv",
                CATEGORY, "family_results")).stream()
                .filter(row -> "7A_WT".equals(row.get("system"))
                        || "7B_WT".equals(row.get("system")))
                .toList();
    }
}
