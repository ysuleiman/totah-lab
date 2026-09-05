package totah.lab.athena.interaction;

import java.util.Objects;

/**
 * Immutable set of every geometric cutoff used by the interaction
 * detectors. Detectors take one instance and stamp it onto every produced
 * {@link Interaction}, so a result always carries the exact thresholds
 * that produced it.
 *
 * <p>Bound convention (applies to all detectors): distances are accepted
 * when {@code minDist < d <= max} — the lower bound is exclusive, the
 * upper bound inclusive. Angle tests are inclusive at both ends. This
 * deviates from PLIP 3.0.1, where all bounds are strict; exact PLIP
 * strictness parity is not claimed (see the detector javadocs).
 *
 * <p>Two curated instances are provided:
 * <ul>
 *   <li>{@link #athenaDefaults()} — the house standard: hydrogen-bond
 *   fields keep the legacy Athena convention of
 *   {@code DefaultLigandInteractionAnalyzer} (2.5/3.5/120/1.35); every
 *   other field is the PLIP 3.0.1 literature value.</li>
 *   <li>{@link #plipReference()} — the PLIP 3.0.1 reference configuration
 *   for validation against PLIP behaviour: H-bond heavy-atom distance
 *   4.1, minimum donor angle 100, and the A...H distance ungated
 *   ({@code hydrogenAcceptorCutoff = }{@link Double#POSITIVE_INFINITY},
 *   because PLIP computes but never gates that distance).</li>
 * </ul>
 *
 * @param minDist global exclusive lower distance bound (PLIP MIN_DIST)
 * @param hydrophobicDistMax hydrophobic closest-pair maximum
 * @param saltBridgeDistMax center-of-charge maximum for salt bridges
 * @param piStackDistMax ring centroid-centroid maximum for pi stacking
 * @param piStackParallelAngleDev max folded normal angle for a parallel
 *                                stack (degrees)
 * @param piStackTShapeAngleDev max deviation from 90 degrees of the
 *                              folded normal angle for a T-shaped stack
 * @param piStackOffsetMax max in-plane center offset for pi stacking
 * @param piCationDistMax ring-centroid to charge-center maximum
 * @param piCationOffsetMax max in-plane offset of the charge center
 *                          projected into the ring plane
 * @param piCationTertamineAngleMax max folded angle between ring normal
 *                                  and amine normal for amine-type
 *                                  charged groups (degrees)
 * @param halogenDistMax acceptor...halogen maximum
 * @param halogenAcceptorAngle nominal acceptor angle (Y-O...X, degrees)
 * @param halogenDonorAngle nominal donor angle (O...X-C, degrees)
 * @param halogenAngleDev symmetric deviation applied to both halogen
 *                        angles (degrees)
 * @param hydrogenAcceptorCutoff H...A maximum for hydrogen bonds;
 *                               {@link Double#POSITIVE_INFINITY} means
 *                               ungated (PLIP reference behaviour)
 * @param donorAcceptorCutoff heavy-atom D...A maximum for hydrogen bonds
 * @param minDonorAngleDegrees minimum D-H...A angle at the hydrogen
 * @param donorBondCutoff maximum H-to-heavy-atom distance when pairing
 *                        AD4 donor hydrogens to their donor heavy atom
 * @param provenance non-blank provenance label of this threshold set
 */
public record InteractionThresholds(
        double minDist,
        double hydrophobicDistMax,
        double saltBridgeDistMax,
        double piStackDistMax,
        double piStackParallelAngleDev,
        double piStackTShapeAngleDev,
        double piStackOffsetMax,
        double piCationDistMax,
        double piCationOffsetMax,
        double piCationTertamineAngleMax,
        double halogenDistMax,
        double halogenAcceptorAngle,
        double halogenDonorAngle,
        double halogenAngleDev,
        double hydrogenAcceptorCutoff,
        double donorAcceptorCutoff,
        double minDonorAngleDegrees,
        double donorBondCutoff,
        String provenance) {

    /** Provenance label of {@link #athenaDefaults()}. */
    public static final String ATHENA_DEFAULTS_PROVENANCE =
            "athena-defaults-1.0 (PLIP-3.0.1 literature values for non-HB"
                    + " types; HB = athena legacy)";

    /** Provenance label of {@link #plipReference()}. */
    public static final String PLIP_REFERENCE_PROVENANCE =
            "plip-3.0.1-reference";

    public InteractionThresholds {
        requirePositive(minDist, "minDist");
        requirePositive(hydrophobicDistMax, "hydrophobicDistMax");
        requirePositive(saltBridgeDistMax, "saltBridgeDistMax");
        requirePositive(piStackDistMax, "piStackDistMax");
        requireAngle(piStackParallelAngleDev, "piStackParallelAngleDev");
        requireAngle(piStackTShapeAngleDev, "piStackTShapeAngleDev");
        requirePositive(piStackOffsetMax, "piStackOffsetMax");
        requirePositive(piCationDistMax, "piCationDistMax");
        requirePositive(piCationOffsetMax, "piCationOffsetMax");
        requireAngle(piCationTertamineAngleMax, "piCationTertamineAngleMax");
        requirePositive(halogenDistMax, "halogenDistMax");
        requireAngle(halogenAcceptorAngle, "halogenAcceptorAngle");
        requireAngle(halogenDonorAngle, "halogenDonorAngle");
        requireAngle(halogenAngleDev, "halogenAngleDev");
        if (Double.isNaN(hydrogenAcceptorCutoff)
                || hydrogenAcceptorCutoff <= 0.0) {
            // POSITIVE_INFINITY is allowed: it encodes the ungated PLIP
            // A...H distance.
            throw new IllegalArgumentException(
                    "hydrogenAcceptorCutoff must be positive");
        }
        requirePositive(donorAcceptorCutoff, "donorAcceptorCutoff");
        requireAngle(minDonorAngleDegrees, "minDonorAngleDegrees");
        requirePositive(donorBondCutoff, "donorBondCutoff");
        Objects.requireNonNull(provenance, "provenance");
        provenance = provenance.trim();
        if (provenance.isEmpty()) {
            throw new IllegalArgumentException("provenance must not be blank");
        }
    }

    /**
     * Returns the house-standard thresholds: PLIP 3.0.1 literature values
     * for every interaction type except hydrogen bonds, which keep the
     * legacy Athena convention (2.5/3.5/120/1.35).
     */
    public static InteractionThresholds athenaDefaults() {
        return new InteractionThresholds(
                0.5,    // minDist
                4.0,    // hydrophobicDistMax
                5.5,    // saltBridgeDistMax
                5.5,    // piStackDistMax
                30.0,   // piStackParallelAngleDev
                30.0,   // piStackTShapeAngleDev
                2.0,    // piStackOffsetMax
                6.0,    // piCationDistMax
                2.0,    // piCationOffsetMax
                30.0,   // piCationTertamineAngleMax
                4.0,    // halogenDistMax
                120.0,  // halogenAcceptorAngle
                165.0,  // halogenDonorAngle
                30.0,   // halogenAngleDev
                2.5,    // hydrogenAcceptorCutoff (athena legacy)
                3.5,    // donorAcceptorCutoff (athena legacy)
                120.0,  // minDonorAngleDegrees (athena legacy)
                1.35,   // donorBondCutoff (athena legacy)
                ATHENA_DEFAULTS_PROVENANCE);
    }

    /**
     * Returns the PLIP 3.0.1 reference thresholds for validation against
     * PLIP behaviour. Hydrogen-bond fields use the PLIP values: heavy-atom
     * D...A maximum 4.1, minimum donor angle 100, A...H distance ungated
     * ({@link Double#POSITIVE_INFINITY}). The donor-bond cutoff stays at
     * 1.35 because PLIP perceives donor bonds from the bond graph and has
     * no equivalent distance cutoff; the value is retained for our
     * AD4-typed donor-hydrogen pairing.
     */
    public static InteractionThresholds plipReference() {
        return new InteractionThresholds(
                0.5,    // minDist (PLIP MIN_DIST)
                4.0,    // hydrophobicDistMax
                5.5,    // saltBridgeDistMax
                5.5,    // piStackDistMax
                30.0,   // piStackParallelAngleDev
                30.0,   // piStackTShapeAngleDev
                2.0,    // piStackOffsetMax
                6.0,    // piCationDistMax
                2.0,    // piCationOffsetMax
                30.0,   // piCationTertamineAngleMax
                4.0,    // halogenDistMax
                120.0,  // halogenAcceptorAngle
                165.0,  // halogenDonorAngle
                30.0,   // halogenAngleDev
                Double.POSITIVE_INFINITY, // A...H ungated in PLIP
                4.1,    // donorAcceptorCutoff (PLIP HBOND_DIST_MAX)
                100.0,  // minDonorAngleDegrees (PLIP HBOND_DON_ANGLE_MIN)
                1.35,   // donorBondCutoff (no PLIP equivalent; see javadoc)
                PLIP_REFERENCE_PROVENANCE);
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and positive");
        }
    }

    private static void requireAngle(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 180.0) {
            throw new IllegalArgumentException(
                    name + " must be between 0 and 180 degrees");
        }
    }
}
