package totah.lab.athena.interaction;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Detects halogen bonds from ligand halogen donors to protein acceptors,
 * following the PLIP 3.0.1 halocarbon rule. Unidirectional: ligand donor
 * to protein acceptor.
 *
 * <p>Donors are ligand halogen atoms ({@link Element#isHalogen()},
 * including fluorine per the PLIP halocarbon rule) bonded to exactly one
 * atom, which must be a carbon. Acceptors are protein O/N/S atoms with
 * exactly one bonded non-hydrogen neighbor (hydrogens are not counted)
 * whose element is in {C, N, P, S}; this admits backbone C=O and
 * Ser/Thr/Tyr OH and excludes ether/ester oxygens.
 *
 * <p>Tests: acceptor...halogen distance in {@code (minDist,
 * halogenDistMax]}; acceptor angle (Y-O...X) within
 * {@code halogenAcceptorAngle +/- halogenAngleDev}; donor angle
 * (O...X-C) within {@code halogenDonorAngle +/- halogenAngleDev} folded
 * into {@code [halogenDonorAngle - dev, 180]} because the upper end of
 * the PLIP default (165 + 30) exceeds 180 degrees. All bounds inclusive;
 * PLIP is strict on the distance and acceptor angle.
 *
 * <p>This detector requires usable bond-graph connectivity
 * ({@code EXPLICIT}/{@code INFERRED}) in both structures; no AD4-typed
 * distance fallback is attempted. When connectivity is degraded the
 * result is empty — halogen donors/acceptors are never guessed without a
 * bond graph.
 */
public final class HalogenBondDetector {

    private static final Set<Element> ACCEPTOR_ELEMENTS =
            Set.of(Element.O, Element.N, Element.S);
    private static final Set<Element> ACCEPTOR_NEIGHBOR_ELEMENTS =
            Set.of(Element.C, Element.N, Element.P, Element.S);

    /**
     * Detects halogen bonds.
     *
     * @param protein protein structure (acceptor side)
     * @param ligand ligand structure (donor side)
     * @param thresholds threshold set applied and stamped onto the results
     * @return one record per qualifying acceptor-donor pair, in structure
     *         traversal order; empty when either structure lacks usable
     *         connectivity
     */
    public List<Interaction> detect(
            Structure protein,
            Structure ligand,
            InteractionThresholds thresholds) {

        Objects.requireNonNull(protein, "protein");
        Objects.requireNonNull(ligand, "ligand");
        Objects.requireNonNull(thresholds, "thresholds");

        if (!BondNeighbors.usable(protein) || !BondNeighbors.usable(ligand)) {
            return List.of();
        }
        Map<Atom, List<Atom>> proteinNeighbors = BondNeighbors.of(protein);
        Map<Atom, List<Atom>> ligandNeighbors = BondNeighbors.of(ligand);
        AtomResidueIndex proteinIndex = AtomResidueIndex.of(protein);

        List<Interaction> bonds = new ArrayList<>();
        for (Map.Entry<Atom, List<Atom>> donor
                : ligandNeighbors.entrySet()) {
            Atom halogen = donor.getKey();
            if (halogen.getElement() == null
                    || !halogen.getElement().isHalogen()
                    || donor.getValue().size() != 1
                    || donor.getValue().get(0).getElement() != Element.C) {
                continue;
            }
            Atom donorCarbon = donor.getValue().get(0);
            for (Map.Entry<Atom, List<Atom>> acceptor
                    : proteinNeighbors.entrySet()) {
                Atom acceptorAtom = acceptor.getKey();
                List<Atom> nonHydrogenNeighbors = acceptor.getValue().stream()
                        .filter(atom -> atom.getElement() != Element.H)
                        .toList();
                if (!ACCEPTOR_ELEMENTS.contains(acceptorAtom.getElement())
                        || nonHydrogenNeighbors.size() != 1
                        || !ACCEPTOR_NEIGHBOR_ELEMENTS.contains(
                                nonHydrogenNeighbors.get(0).getElement())) {
                    continue;
                }
                evaluate(proteinIndex, acceptorAtom,
                        nonHydrogenNeighbors.get(0), halogen, donorCarbon,
                        thresholds, bonds);
            }
        }
        return List.copyOf(bonds);
    }

    private static void evaluate(
            AtomResidueIndex proteinIndex,
            Atom acceptor,
            Atom acceptorNeighbor,
            Atom halogen,
            Atom donorCarbon,
            InteractionThresholds thresholds,
            List<Interaction> bonds) {

        double distance = acceptor.getPosition()
                .distance(halogen.getPosition());
        if (distance <= thresholds.minDist()
                || distance > thresholds.halogenDistMax()) {
            return;
        }
        double acceptorAngle = angleDegrees(
                acceptorNeighbor, acceptor, halogen);
        if (acceptorAngle
                < thresholds.halogenAcceptorAngle() - thresholds.halogenAngleDev()
                || acceptorAngle
                > thresholds.halogenAcceptorAngle() + thresholds.halogenAngleDev()) {
            return;
        }
        double donorAngle = angleDegrees(acceptor, halogen, donorCarbon);
        // The upper bound folds to 180 degrees (the acos range): the PLIP
        // default halogenDonorAngle + halogenAngleDev (165 + 30) exceeds
        // 180 and is treated as <= 180.
        if (donorAngle
                < thresholds.halogenDonorAngle() - thresholds.halogenAngleDev()) {
            return;
        }
        ResidueId residue = proteinIndex.residueOf(acceptor)
                .orElseThrow(() -> new IllegalStateException(
                        "acceptor atom not indexed: " + acceptor.getName()));
        bonds.add(new Interaction(
                InteractionType.HALOGEN_BOND,
                residue,
                List.of(acceptor),
                List.of(halogen, donorCarbon),
                distance,
                acceptorAngle,
                donorAngle,
                null,
                null,
                thresholds));
    }

    /** Angle at {@code vertex} between the rays to {@code a} and {@code b}. */
    private static double angleDegrees(Atom a, Atom vertex, Atom b) {
        return Math.toDegrees(vertex.getPosition()
                .vectorTo(a.getPosition())
                .angle(vertex.getPosition().vectorTo(b.getPosition())));
    }
}
