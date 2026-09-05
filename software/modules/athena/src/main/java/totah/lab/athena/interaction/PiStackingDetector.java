package totah.lab.athena.interaction;

import totah.lab.athena.interaction.perception.AromaticRing;
import totah.lab.gaia.geometry.Plane3D;
import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Detects pi-stacking between protein and ligand aromatic rings,
 * following the PLIP 3.0.1 geometry over {@link Plane3D} fits of the
 * perceived ring atoms. Rings are perceived by the caller (protein and
 * ligand structures perceived separately) and passed in partitioned.
 *
 * <p>Tests per ring pair: centroid-centroid distance in
 * {@code (minDist, piStackDistMax]}; the folded (acute) normal angle
 * {@code a} from {@link Plane3D#angleToDegrees}; and the center offset,
 * the minimum over both mutual center-into-plane projections (the other
 * centroid projected into this ring's plane, measured in-plane from this
 * centroid). {@code PARALLEL} when {@code a <= piStackParallelAngleDev}
 * and offset {@code <= piStackOffsetMax}; {@code T_SHAPED} when
 * {@code |90 - a| <= piStackTShapeAngleDev} and offset
 * {@code <= piStackOffsetMax}. All bounds inclusive.
 *
 * <p>Documented deviations from PLIP 3.0.1:
 * <ul>
 *   <li>Bounds are inclusive; PLIP is strict ({@code 0 < a < 30},
 *   {@code 60 < a < 120}, offset {@code < 2.0}).</li>
 *   <li>A numerically perfect parallel stack ({@code a == 0}) is a valid
 *   parallel stack here. PLIP's strict {@code 0 < a} test combined with
 *   its {@code vecangle} returning exactly 0.0 for identical vectors
 *   rejects that case; the rejection is treated as a quirk and not
 *   reproduced.</li>
 *   <li>Rings perceived via a degraded fallback (unknown topology) are
 *   skipped rather than guessed; PLIP has no degraded mode.</li>
 * </ul>
 */
public final class PiStackingDetector {

    /**
     * Detects pi-stacking interactions.
     *
     * @param proteinRings rings perceived on the protein structure
     * @param ligandRings rings perceived on the ligand structure
     * @param thresholds threshold set applied and stamped onto the results
     * @return one record per qualifying ring pair, in input order
     */
    public List<Interaction> detect(
            List<AromaticRing> proteinRings,
            List<AromaticRing> ligandRings,
            InteractionThresholds thresholds) {

        Objects.requireNonNull(proteinRings, "proteinRings");
        Objects.requireNonNull(ligandRings, "ligandRings");
        Objects.requireNonNull(thresholds, "thresholds");

        Map<String, Optional<Plane3D>> planes = new HashMap<>();
        List<Interaction> stacks = new ArrayList<>();
        for (AromaticRing proteinRing : proteinRings) {
            Optional<Plane3D> proteinPlane = planes.computeIfAbsent(
                    proteinRing.ringId(),
                    id -> InteractionGeometry.ringPlane(proteinRing));
            if (proteinPlane.isEmpty()) {
                continue;
            }
            for (AromaticRing ligandRing : ligandRings) {
                Optional<Plane3D> ligandPlane = planes.computeIfAbsent(
                        ligandRing.ringId(),
                        id -> InteractionGeometry.ringPlane(ligandRing));
                if (ligandPlane.isEmpty()) {
                    continue;
                }
                InteractionType type = classify(
                        proteinRing, proteinPlane.get(),
                        ligandRing, ligandPlane.get(), thresholds);
                if (type == null) {
                    continue;
                }
                double distance = proteinRing.centroid()
                        .distance(ligandRing.centroid());
                double angle = proteinPlane.get()
                        .angleToDegrees(ligandPlane.get());
                stacks.add(new Interaction(
                        type,
                        proteinRing.owner(),
                        proteinRing.atoms(),
                        ligandRing.atoms(),
                        distance,
                        angle,
                        null,
                        proteinRing.ringId(),
                        ligandRing.ringId(),
                        thresholds));
            }
        }
        return List.copyOf(stacks);
    }

    private static InteractionType classify(
            AromaticRing proteinRing,
            Plane3D proteinPlane,
            AromaticRing ligandRing,
            Plane3D ligandPlane,
            InteractionThresholds thresholds) {

        double distance = proteinRing.centroid()
                .distance(ligandRing.centroid());
        if (distance <= thresholds.minDist()
                || distance > thresholds.piStackDistMax()) {
            return null;
        }
        double offset = Math.min(
                inPlaneOffset(proteinPlane, ligandRing.centroid()),
                inPlaneOffset(ligandPlane, proteinRing.centroid()));
        if (offset > thresholds.piStackOffsetMax()) {
            return null;
        }
        double angle = proteinPlane.angleToDegrees(ligandPlane);
        if (angle <= thresholds.piStackParallelAngleDev()) {
            return InteractionType.PI_STACK_PARALLEL;
        }
        if (Math.abs(90.0 - angle) <= thresholds.piStackTShapeAngleDev()) {
            return InteractionType.PI_STACK_T_SHAPED;
        }
        return null;
    }

    /** In-plane distance of {@code center} projected into {@code plane}. */
    private static double inPlaneOffset(Plane3D plane, Point3D center) {
        return plane.centroid().distance(plane.project(center));
    }
}
