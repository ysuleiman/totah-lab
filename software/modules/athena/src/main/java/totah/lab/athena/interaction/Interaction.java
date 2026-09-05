package totah.lab.athena.interaction;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/**
 * One detected protein-ligand interaction, stamped with the thresholds
 * that produced it. Instances are immutable.
 *
 * <p>Per-type semantics of {@code distanceAngstroms}:
 * <ul>
 *   <li>{@code HYDROGEN_BOND} — heavy-atom D...A distance.</li>
 *   <li>{@code SALT_BRIDGE} — center-of-charge to center-of-charge.</li>
 *   <li>{@code HYDROPHOBIC_CONTACT} — closest atom pair distance.</li>
 *   <li>{@code PI_STACK_*} — ring centroid to ring centroid.</li>
 *   <li>{@code PI_CATION} — ring centroid to charge center.</li>
 *   <li>{@code HALOGEN_BOND} — acceptor...halogen (O...X) distance.</li>
 * </ul>
 *
 * <p>Atom-list conventions (relied upon by
 * {@link InteractionRefinements}):
 * <ul>
 *   <li>{@code HYDROGEN_BOND} — the donor side holds
 *   {@code [donor heavy atom, donor hydrogen]}, the acceptor side holds
 *   {@code [acceptor atom]}; either side may be the protein side.</li>
 *   <li>{@code SALT_BRIDGE} — each side holds the atoms of its charged
 *   group.</li>
 *   <li>{@code HYDROPHOBIC_CONTACT} — exactly one atom per side.</li>
 *   <li>{@code PI_STACK_*} — each side holds the atoms of its ring.</li>
 *   <li>{@code PI_CATION} — the ring side holds the ring atoms, the
 *   charged side holds the charged-group atoms.</li>
 *   <li>{@code HALOGEN_BOND} — {@code proteinAtoms} is the acceptor atom;
 *   {@code ligandAtoms} is {@code [halogen, donor carbon]}.</li>
 * </ul>
 *
 * <p>Angle fields are nullable (house style, matching
 * {@code LigandInteraction}): they are {@code null} for types that define
 * no angle. For pi-stacking, {@code primaryAngleDegrees} is the folded
 * (acute) ring-normal angle; for pi-cation it is the folded
 * ring-normal/amine-normal angle when the tertamine guard was evaluated,
 * else {@code null}; for halogen bonds the primary angle is the acceptor
 * angle and the secondary angle is the donor angle.
 *
 * <p>Group identifiers: for ring participants the
 * {@link totah.lab.athena.interaction.perception.AromaticRing#ringId()};
 * for charged groups a synthesized identifier of the form
 * {@code "<ChargedGroupType> <chainId>:<residueNumber>"} (e.g.
 * {@code "RESIDUE_HIS A:42"}). {@code null} when the participant is not a
 * ring or charged group. Atom identity is by object identity: all atoms
 * originate from the structures that were perceived, and refinements
 * match them with identity semantics.
 */
public record Interaction(
        InteractionType type,
        ResidueId residue,
        List<Atom> proteinAtoms,
        List<Atom> ligandAtoms,
        double distanceAngstroms,
        Double primaryAngleDegrees,
        Double secondaryAngleDegrees,
        String proteinGroupId,
        String ligandGroupId,
        InteractionThresholds thresholds) {

    public Interaction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(residue, "residue");
        proteinAtoms = List.copyOf(
                Objects.requireNonNull(proteinAtoms, "proteinAtoms"));
        ligandAtoms = List.copyOf(
                Objects.requireNonNull(ligandAtoms, "ligandAtoms"));
        if (proteinAtoms.isEmpty() || ligandAtoms.isEmpty()) {
            throw new IllegalArgumentException(
                    "both sides of an interaction require at least one atom");
        }
        if (!Double.isFinite(distanceAngstroms) || distanceAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "distanceAngstroms must be finite and non-negative");
        }
        requireAngle(primaryAngleDegrees, "primaryAngleDegrees");
        requireAngle(secondaryAngleDegrees, "secondaryAngleDegrees");
        Objects.requireNonNull(thresholds, "thresholds");
    }

    /** Returns the provenance label of the thresholds that produced this record. */
    public String thresholdsProvenance() {
        return thresholds.provenance();
    }

    private static void requireAngle(Double angle, String name) {
        if (angle != null && (!Double.isFinite(angle)
                || angle < 0.0 || angle > 180.0)) {
            throw new IllegalArgumentException(
                    name + " must be between 0 and 180 degrees");
        }
    }
}
