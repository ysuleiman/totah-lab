package totah.lab.athena.interaction;

import totah.lab.athena.interaction.perception.ChargeSign;
import totah.lab.athena.interaction.perception.ChargedGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detects salt bridges as positive x negative charged-group pairs across
 * the protein-ligand boundary, following PLIP 3.0.1: the test is on
 * center-of-charge distance only, never per-atom. Perceived groups are
 * passed in partitioned by side; nothing is re-perceived.
 *
 * <p>A pair is kept when
 * {@code minDist < d(chargeCenter, chargeCenter) <= saltBridgeDistMax}
 * (lower bound exclusive, upper inclusive; PLIP is strict on both ends).
 * Dedup is inherent: one record per (group, group) pair.
 *
 * <p>This detector supersedes the legacy
 * {@code DefaultLigandInteractionAnalyzer} salt bridge (4.0 A closest
 * heavy-atom pair over summed partial charges) in the new layer.
 */
public final class SaltBridgeDetector {

    /**
     * Detects salt bridges.
     *
     * @param proteinGroups charged groups perceived on the protein
     * @param ligandGroups charged groups perceived on the ligand
     * @param thresholds threshold set applied and stamped onto the results
     * @return one record per qualifying opposite-sign group pair, in
     *         input order (both directions produce separate records)
     */
    public List<Interaction> detect(
            List<ChargedGroup> proteinGroups,
            List<ChargedGroup> ligandGroups,
            InteractionThresholds thresholds) {

        Objects.requireNonNull(proteinGroups, "proteinGroups");
        Objects.requireNonNull(ligandGroups, "ligandGroups");
        Objects.requireNonNull(thresholds, "thresholds");

        List<Interaction> bridges = new ArrayList<>();
        for (ChargedGroup proteinGroup : proteinGroups) {
            for (ChargedGroup ligandGroup : ligandGroups) {
                if (proteinGroup.sign() == ligandGroup.sign()) {
                    continue;
                }
                double distance = proteinGroup.chargeCenter()
                        .distance(ligandGroup.chargeCenter());
                if (distance <= thresholds.minDist()
                        || distance > thresholds.saltBridgeDistMax()) {
                    continue;
                }
                bridges.add(new Interaction(
                        InteractionType.SALT_BRIDGE,
                        proteinGroup.owner(),
                        proteinGroup.atoms(),
                        ligandGroup.atoms(),
                        distance,
                        null,
                        null,
                        InteractionGeometry.chargedGroupId(proteinGroup),
                        InteractionGeometry.chargedGroupId(ligandGroup),
                        thresholds));
            }
        }
        return List.copyOf(bridges);
    }
}
