package totah.lab.athena.interaction;

import totah.lab.athena.interaction.perception.AromaticRing;
import totah.lab.athena.interaction.perception.ChargeSign;
import totah.lab.athena.interaction.perception.ChargedGroup;
import totah.lab.athena.interaction.perception.ChargedGroupType;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Plane3D;
import totah.lab.gaia.geometry.Vector3D;
import totah.lab.gaia.structure.Atom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Detects pi-cation interactions between positive charged groups and
 * aromatic rings, in both directions (protein group x ligand ring and
 * ligand group x protein ring). Perceived groups and rings are passed in
 * partitioned by side; nothing is re-perceived.
 *
 * <p>Tests per group-ring pair: ring-centroid to charge-center distance
 * in {@code (minDist, piCationDistMax]} and in-plane offset of the charge
 * center projected into the ring plane {@code <= piCationOffsetMax}. For
 * {@link ChargedGroupType#AMINE} groups the tertamine guard additionally
 * requires the folded (acute) angle between the ring normal and the amine
 * normal (cross product of two neighbor vectors from the central
 * nitrogen) to be {@code <= piCationTertamineAngleMax}; the central
 * nitrogen is taken as the first nitrogen atom of the group and the
 * remaining group atoms as its neighbors, matching the perception
 * layout. The guard is skipped when the group has fewer than three atoms
 * (no well-defined amine normal).
 *
 * <p>Documented deviations from PLIP 3.0.1:
 * <ul>
 *   <li>All charge groups are evaluated against every ring. PLIP's
 *   tertamine branch {@code break}s after the first tertamine group of a
 *   ring, so later groups are never tested against that ring; that is a
 *   bug and is not reproduced.</li>
 *   <li>Bounds are inclusive where PLIP is strict (PLIP accepts exactly
 *   30 degrees for the tertamine guard as well).</li>
 *   <li>Rings perceived via a degraded fallback are skipped (no plane is
 *   guessed from unknown topology).</li>
 *   <li>Our perception merges PLIP's tertamine and quaternary-amine
 *   classes into {@link ChargedGroupType#AMINE}, so the tertamine guard
 *   is applied to every AMINE group; PLIP scopes it to tertamines.</li>
 * </ul>
 */
public final class PiCationDetector {

    /**
     * Detects pi-cation interactions.
     *
     * @param proteinGroups charged groups perceived on the protein
     * @param proteinRings rings perceived on the protein
     * @param ligandGroups charged groups perceived on the ligand
     * @param ligandRings rings perceived on the ligand
     * @param thresholds threshold set applied and stamped onto the results
     * @return one record per qualifying group-ring pair, protein-side
     *         groups first, in input order
     */
    public List<Interaction> detect(
            List<ChargedGroup> proteinGroups,
            List<AromaticRing> proteinRings,
            List<ChargedGroup> ligandGroups,
            List<AromaticRing> ligandRings,
            InteractionThresholds thresholds) {

        Objects.requireNonNull(proteinGroups, "proteinGroups");
        Objects.requireNonNull(proteinRings, "proteinRings");
        Objects.requireNonNull(ligandGroups, "ligandGroups");
        Objects.requireNonNull(ligandRings, "ligandRings");
        Objects.requireNonNull(thresholds, "thresholds");

        List<Interaction> interactions = new ArrayList<>();
        for (ChargedGroup group : proteinGroups) {
            if (group.sign() != ChargeSign.POSITIVE) {
                continue;
            }
            for (AromaticRing ring : ligandRings) {
                evaluate(group, ring, true, thresholds, interactions);
            }
        }
        for (ChargedGroup group : ligandGroups) {
            if (group.sign() != ChargeSign.POSITIVE) {
                continue;
            }
            for (AromaticRing ring : proteinRings) {
                evaluate(group, ring, false, thresholds, interactions);
            }
        }
        return List.copyOf(interactions);
    }

    private static void evaluate(
            ChargedGroup group,
            AromaticRing ring,
            boolean proteinGroup,
            InteractionThresholds thresholds,
            List<Interaction> interactions) {

        Optional<Plane3D> plane = InteractionGeometry.ringPlane(ring);
        if (plane.isEmpty()) {
            return;
        }
        double distance = ring.centroid().distance(group.chargeCenter());
        if (distance <= thresholds.minDist()
                || distance > thresholds.piCationDistMax()) {
            return;
        }
        double offset = plane.get().centroid()
                .distance(plane.get().project(group.chargeCenter()));
        if (offset > thresholds.piCationOffsetMax()) {
            return;
        }
        Double tertamineAngle = null;
        if (group.type() == ChargedGroupType.AMINE) {
            tertamineAngle = tertamineAngle(group, plane.get());
            if (tertamineAngle == null) {
                // Guard not evaluable (too few neighbor atoms): the
                // distance/offset gates alone decide.
            } else if (tertamineAngle
                    > thresholds.piCationTertamineAngleMax()) {
                return;
            }
        }
        interactions.add(new Interaction(
                InteractionType.PI_CATION,
                proteinGroup ? group.owner() : ring.owner(),
                proteinGroup ? group.atoms() : ring.atoms(),
                proteinGroup ? ring.atoms() : group.atoms(),
                distance,
                tertamineAngle,
                null,
                proteinGroup
                        ? InteractionGeometry.chargedGroupId(group)
                        : ring.ringId(),
                proteinGroup
                        ? ring.ringId()
                        : InteractionGeometry.chargedGroupId(group),
                thresholds));
    }

    /**
     * Folded (acute) angle between the ring normal and the amine normal,
     * or {@code null} when the group has fewer than two neighbor atoms.
     */
    private static Double tertamineAngle(ChargedGroup group, Plane3D plane) {
        Atom nitrogen = group.atoms().stream()
                .filter(atom -> atom.getElement() == Element.N)
                .findFirst()
                .orElse(null);
        if (nitrogen == null || group.atoms().size() < 3) {
            return null;
        }
        List<Atom> neighbors = group.atoms().stream()
                .filter(atom -> atom != nitrogen)
                .toList();
        Vector3D first = nitrogen.getPosition()
                .vectorTo(neighbors.get(0).getPosition());
        Vector3D second = nitrogen.getPosition()
                .vectorTo(neighbors.get(1).getPosition());
        Vector3D amineNormal = first.cross(second);
        if (amineNormal.isZero()) {
            return null;
        }
        double angle = Math.toDegrees(
                amineNormal.angle(plane.normal()));
        return Math.min(angle, 180.0 - angle);
    }
}
