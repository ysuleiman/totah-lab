package totah.lab.athena.interaction;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Detects hydrogen bonds with explicit D-H...A geometry over
 * AutoDock4-typed prepared state: acceptors are atoms typed NA/OA/SA;
 * donor sites are HD-typed hydrogens paired with the nearest heavy atom
 * within {@code donorBondCutoff}. Tests per donor-acceptor pair:
 * H...A {@code <= hydrogenAcceptorCutoff} (ungated when the cutoff is
 * {@link Double#POSITIVE_INFINITY}, the PLIP reference behaviour),
 * heavy-atom D...A in {@code (minDist, donorAcceptorCutoff]}, and the
 * angle at the hydrogen {@code >= minDonorAngleDegrees}.
 *
 * <p>This class deliberately duplicates the rules of the legacy
 * {@code DefaultLigandInteractionAnalyzer} instead of adapting it: the
 * legacy analyzer's cutoffs are compile-time constants, so an adapter
 * cannot honor injected {@link InteractionThresholds} (the PLIP
 * reference set widens the heavy-atom cutoff to 4.1, lowers the angle
 * gate to 100 degrees, and ungates A...H — all unreachable through the
 * adapter). With {@link InteractionThresholds#athenaDefaults()} the two
 * implementations agree, except that this detector additionally applies
 * the global {@code minDist} lower bound to the D...A distance, which
 * the legacy analyzer does not gate. The legacy salt bridge is not
 * duplicated here; {@link SaltBridgeDetector} supersedes it.
 *
 * <p>Results are raw: apply
 * {@link InteractionRefinements#refineHydrogenBonds} after salt-bridge
 * detection.
 */
public final class HydrogenBondDetector {

    private static final Set<String> ACCEPTOR_TYPES =
            Set.of("NA", "OA", "SA");
    private static final String DONOR_HYDROGEN_TYPE = "HD";

    /**
     * Detects raw hydrogen bonds in both directions.
     *
     * @param receptor protein structure
     * @param ligand ligand structure
     * @param thresholds threshold set applied and stamped onto the results
     * @return raw hydrogen bonds in deterministic traversal order
     */
    public List<Interaction> detect(
            Structure receptor,
            Structure ligand,
            InteractionThresholds thresholds) {

        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(ligand, "ligand");
        Objects.requireNonNull(thresholds, "thresholds");

        List<Atom> ligandAtoms = atoms(ligand);
        List<DonorSite> ligandDonors = donorSites(ligandAtoms, thresholds);
        List<Atom> ligandAcceptors = acceptors(ligandAtoms);

        List<Interaction> bonds = new ArrayList<>();
        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId residueId = new ResidueId(
                        chain.id(), residue.getNumber(),
                        residue.getInsertionCode());
                List<Atom> receptorAtoms = residue.getAtoms();
                hydrogenBonds(bonds, residueId,
                        donorSites(receptorAtoms, thresholds),
                        ligandAcceptors, true, thresholds);
                hydrogenBonds(bonds, residueId,
                        ligandDonors, acceptors(receptorAtoms),
                        false, thresholds);
            }
        }
        return List.copyOf(bonds);
    }

    private static void hydrogenBonds(
            List<Interaction> bonds,
            ResidueId residue,
            List<DonorSite> donors,
            List<Atom> acceptors,
            boolean proteinDonor,
            InteractionThresholds thresholds) {

        for (DonorSite donor : donors) {
            for (Atom acceptor : acceptors) {
                double hydrogenDistance = donor.hydrogen().getPosition()
                        .distance(acceptor.getPosition());
                double heavyDistance = donor.heavyAtom().getPosition()
                        .distance(acceptor.getPosition());
                double angle = angleDegrees(
                        donor.heavyAtom(), donor.hydrogen(), acceptor);
                if (hydrogenDistance > thresholds.hydrogenAcceptorCutoff()
                        || heavyDistance <= thresholds.minDist()
                        || heavyDistance > thresholds.donorAcceptorCutoff()
                        || angle < thresholds.minDonorAngleDegrees()) {
                    continue;
                }
                bonds.add(new Interaction(
                        InteractionType.HYDROGEN_BOND,
                        residue,
                        proteinDonor
                                ? List.of(donor.heavyAtom(), donor.hydrogen())
                                : List.of(acceptor),
                        proteinDonor
                                ? List.of(acceptor)
                                : List.of(donor.heavyAtom(), donor.hydrogen()),
                        heavyDistance,
                        angle,
                        null,
                        null,
                        null,
                        thresholds));
            }
        }
    }

    private static List<DonorSite> donorSites(
            List<Atom> atoms,
            InteractionThresholds thresholds) {

        List<DonorSite> sites = new ArrayList<>();
        for (Atom hydrogen : atoms) {
            if (!DONOR_HYDROGEN_TYPE.equals(hydrogen.getAutoDockType())) {
                continue;
            }
            atoms.stream()
                    .filter(Atom::isHeavyAtom)
                    .filter(atom -> atom.getPosition().distance(
                            hydrogen.getPosition())
                            <= thresholds.donorBondCutoff())
                    .min(Comparator.comparingDouble(atom -> atom.getPosition()
                            .distance(hydrogen.getPosition())))
                    .ifPresent(heavy -> sites.add(
                            new DonorSite(heavy, hydrogen)));
        }
        return List.copyOf(sites);
    }

    private static List<Atom> acceptors(List<Atom> atoms) {
        return atoms.stream()
                .filter(atom -> atom.getAutoDockType() != null
                        && ACCEPTOR_TYPES.contains(atom.getAutoDockType()))
                .toList();
    }

    private static List<Atom> atoms(Structure structure) {
        return structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .toList();
    }

    /** Angle at {@code vertex} between the rays to {@code a} and {@code b}. */
    private static double angleDegrees(Atom a, Atom vertex, Atom b) {
        double denominator = vertex.getPosition().vectorTo(a.getPosition())
                .magnitude() * vertex.getPosition().vectorTo(b.getPosition())
                .magnitude();
        if (denominator == 0.0) {
            return 0.0;
        }
        return Math.toDegrees(vertex.getPosition()
                .vectorTo(a.getPosition())
                .angle(vertex.getPosition().vectorTo(b.getPosition())));
    }

    private record DonorSite(Atom heavyAtom, Atom hydrogen) {
    }
}
