package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.interaction.InteractionFixtures.atom;
import static totah.lab.athena.interaction.InteractionFixtures.bond;
import static totah.lab.athena.interaction.InteractionFixtures.chain;
import static totah.lab.athena.interaction.InteractionFixtures.residue;
import static totah.lab.athena.interaction.InteractionFixtures.structure;

class InteractionRefinementsTest {

    private static final InteractionThresholds THRESHOLDS =
            InteractionThresholds.athenaDefaults();
    private static int serial = 0;

    private static Atom carbon(double x, double y, double z) {
        return atom(++serial, "C" + serial, Element.C, x, y, z);
    }

    private static Interaction hydrogenBond(
            ResidueId residue,
            Atom donorHeavy,
            Atom hydrogen,
            Atom acceptor,
            boolean proteinDonor,
            double angle) {

        return new Interaction(
                InteractionType.HYDROGEN_BOND,
                residue,
                proteinDonor ? List.of(donorHeavy, hydrogen)
                        : List.of(acceptor),
                proteinDonor ? List.of(acceptor)
                        : List.of(donorHeavy, hydrogen),
                3.0, angle, null, null, null, THRESHOLDS);
    }

    private static Interaction saltBridge(
            ResidueId residue,
            List<Atom> proteinAtoms,
            List<Atom> ligandAtoms,
            String proteinGroupId,
            String ligandGroupId) {

        return new Interaction(
                InteractionType.SALT_BRIDGE, residue,
                proteinAtoms, ligandAtoms,
                4.0, null, null, proteinGroupId, ligandGroupId,
                THRESHOLDS);
    }

    private static Interaction piStack(
            ResidueId residue,
            List<Atom> proteinRingAtoms,
            List<Atom> ligandRingAtoms,
            String proteinRingId,
            String ligandRingId) {

        return new Interaction(
                InteractionType.PI_STACK_PARALLEL, residue,
                proteinRingAtoms, ligandRingAtoms,
                3.4, 0.0, null, proteinRingId, ligandRingId, THRESHOLDS);
    }

    private static Interaction piCation(
            ResidueId residue,
            Atom chargedAtom,
            Atom ringAtom,
            String proteinGroupId,
            String ligandGroupId) {

        return new Interaction(
                InteractionType.PI_CATION, residue,
                List.of(chargedAtom), List.of(ringAtom),
                4.0, null, null, proteinGroupId, ligandGroupId,
                THRESHOLDS);
    }

    private static Interaction hydrophobic(
            ResidueId residue, Atom proteinAtom, Atom ligandAtom,
            double distance) {

        return new Interaction(
                InteractionType.HYDROPHOBIC_CONTACT, residue,
                List.of(proteinAtom), List.of(ligandAtom),
                distance, null, null, null, null, THRESHOLDS);
    }

    @Test
    void saltBridgeSuppressesHydrogenBondBetweenItsAtomSets() {
        Atom proteinOxygen = carbon(0, 0, 0);
        Atom ligandNitrogen = carbon(4, 0, 0);
        Atom ligandHydrogen = atom(++serial, "H" + serial, Element.H,
                4, 1, 0);
        Atom unrelatedDonor = carbon(0, 8, 0);
        Atom unrelatedHydrogen = atom(++serial, "H" + serial, Element.H,
                0, 9, 0);
        Atom unrelatedAcceptor = carbon(3, 8, 0);

        Interaction bridge = saltBridge(new ResidueId("A", 60, null),
                List.of(proteinOxygen), List.of(ligandNitrogen),
                "RESIDUE_ASP A:60", "AMINE L:501");
        Interaction suppressed = hydrogenBond(new ResidueId("A", 60, null),
                ligandNitrogen, ligandHydrogen, proteinOxygen, false, 150.0);
        Interaction kept = hydrogenBond(new ResidueId("A", 61, null),
                unrelatedDonor, unrelatedHydrogen, unrelatedAcceptor,
                true, 150.0);

        List<Interaction> refined = InteractionRefinements.refineHydrogenBonds(
                List.of(suppressed, kept), List.of(bridge));

        assertThat(refined).containsExactly(kept);
    }

    @Test
    void oneHydrogenBondPerDonorHeavyAtomKeepsLargestAngle() {
        Atom donor = carbon(0, 0, 0);
        Atom hydrogen = atom(++serial, "H" + serial, Element.H, 1, 0, 0);
        Atom first = carbon(3, 0, 0);
        Atom second = carbon(0, 3, 0);
        Atom third = carbon(0, 0, 3);

        Interaction weaker = hydrogenBond(new ResidueId("A", 20, null),
                donor, hydrogen, first, true, 130.0);
        Interaction stronger = hydrogenBond(new ResidueId("A", 20, null),
                donor, hydrogen, second, true, 150.0);
        Interaction tied = hydrogenBond(new ResidueId("A", 20, null),
                donor, hydrogen, third, true, 150.0);

        // Largest angle wins; first-seen wins the tie.
        assertThat(InteractionRefinements.refineHydrogenBonds(
                List.of(weaker, stronger, tied), List.of()))
                .containsExactly(stronger);
    }

    @Test
    void hisPiCationIsSuppressedWhenTheSameRingPairStacks() {
        Atom hisNitrogen = carbon(0, 0, 4);
        Atom ligandRingAtom = carbon(0, 0, 0);
        ResidueId his = new ResidueId("A", 42, null);
        Interaction stack = piStack(his,
                List.of(carbon(1, 0, 0)), List.of(ligandRingAtom),
                "HIS A:42 ring0", "LIG L:501 ring0");
        Interaction hisPiCation = piCation(his, hisNitrogen, ligandRingAtom,
                "RESIDUE_HIS A:42", "LIG L:501 ring0");
        Interaction lysPiCation = piCation(new ResidueId("A", 50, null),
                carbon(0, 5, 4), ligandRingAtom,
                "RESIDUE_LYS A:50", "LIG L:501 ring0");

        List<Interaction> refined = InteractionRefinements.refinePiCations(
                List.of(hisPiCation, lysPiCation), List.of(stack));

        assertThat(refined).containsExactly(lysPiCation);
    }

    @Test
    void hisPiCationWithoutMatchingStackIsKept() {
        Atom hisNitrogen = carbon(0, 0, 4);
        Atom ligandRingAtom = carbon(0, 0, 0);
        ResidueId his = new ResidueId("A", 42, null);
        Interaction piCation = piCation(his, hisNitrogen, ligandRingAtom,
                "RESIDUE_HIS A:42", "LIG L:501 ring0");

        // No stack at all, and a stack on the same residue NUMBER but a
        // different chain (deviation: the key includes the chain).
        Interaction otherChainStack = piStack(new ResidueId("B", 42, null),
                List.of(carbon(9, 9, 9)), List.of(ligandRingAtom),
                "HIS B:42 ring0", "LIG L:501 ring0");

        assertThat(InteractionRefinements.refinePiCations(
                List.of(piCation), List.of())).containsExactly(piCation);
        assertThat(InteractionRefinements.refinePiCations(
                List.of(piCation), List.of(otherChainStack)))
                .containsExactly(piCation);
    }

    @Test
    void hydrophobicContactsInsideStackedRingsAreExcluded() {
        Atom proteinRingAtom = carbon(1, 0, 0);
        Atom ligandRingAtom = carbon(0, 1, 0);
        Atom otherProtein = carbon(8, 8, 8);
        Atom otherLigand = carbon(9, 9, 9);
        ResidueId residue = new ResidueId("A", 43, null);

        Interaction stack = piStack(residue,
                List.of(proteinRingAtom), List.of(ligandRingAtom),
                "PHE A:43 ring0", "LIG L:501 ring0");
        Interaction ringContact = hydrophobic(residue,
                proteinRingAtom, ligandRingAtom, 3.5);
        Interaction keptContact = hydrophobic(residue,
                otherProtein, otherLigand, 3.5);

        // Degraded ligand (no bonds): clustering skipped, only the
        // ring-pair exclusion applies.
        Structure ligand = structure(chain("L",
                residue("LIG", 501, List.of(ligandRingAtom, otherLigand))));

        assertThat(InteractionRefinements.refineHydrophobicContacts(
                List.of(ringContact, keptContact), List.of(stack), ligand))
                .containsExactly(keptContact);
    }

    @Test
    void perLigandAtomResidueDedupKeepsClosestAndIncludesChain() {
        Atom ligandAtom = carbon(0, 0, 0);
        Atom ligandPartner = carbon(1.5, 0, 0);
        Atom proteinA10Far = carbon(3.9, 0, 0);
        Atom proteinA10Close = carbon(3.8, 0, 0);
        Atom proteinA11 = carbon(0, 3.0, 0);
        Atom proteinB10 = carbon(0, 0, 2.0);

        Interaction far = hydrophobic(new ResidueId("A", 10, null),
                proteinA10Far, ligandAtom, 3.9);
        Interaction close = hydrophobic(new ResidueId("A", 10, null),
                proteinA10Close, ligandAtom, 3.8);
        Interaction otherResidue = hydrophobic(new ResidueId("A", 11, null),
                proteinA11, ligandAtom, 3.0);
        // Same residue number, different chain: kept (deviation from
        // PLIP's residue-number-only key).
        Interaction otherChain = hydrophobic(new ResidueId("B", 10, null),
                proteinB10, ligandAtom, 2.0);

        Structure ligand = structure(
                List.of(chain("L", residue("LIG", 501,
                        List.of(ligandAtom, ligandPartner)))),
                List.of(bond("L", 501, ligandAtom.getName(),
                        ligandPartner.getName())));

        List<Interaction> refined = InteractionRefinements
                .refineHydrophobicContacts(
                        List.of(far, close, otherResidue, otherChain),
                        List.of(), ligand);

        assertThat(refined).containsExactlyInAnyOrder(
                close, otherResidue, otherChain);
    }

    @Test
    void patchClusteringKeepsClosestPerClusterAndRetainsIsolatedAtoms() {
        Atom proteinAtom = carbon(0, 0, 0);
        Atom ligandA = carbon(3.9, 0, 0);
        Atom ligandB = carbon(3.0, 0, 0);
        // Isolated: no bonded neighbor among the contacting ligand atoms.
        // PLIP silently drops this contact; Athena keeps it.
        Atom ligandIsolated = carbon(0, 2.5, 0);

        Interaction contactA = hydrophobic(new ResidueId("A", 10, null),
                proteinAtom, ligandA, 3.9);
        Interaction contactB = hydrophobic(new ResidueId("A", 10, null),
                proteinAtom, ligandB, 3.0);
        Interaction contactIsolated = hydrophobic(
                new ResidueId("A", 10, null),
                proteinAtom, ligandIsolated, 2.5);

        Structure ligand = structure(
                List.of(chain("L", residue("LIG", 501,
                        List.of(ligandA, ligandB, ligandIsolated)))),
                List.of(bond("L", 501, ligandA.getName(),
                        ligandB.getName())));

        List<Interaction> refined = InteractionRefinements
                .refineHydrophobicContacts(
                        List.of(contactA, contactB, contactIsolated),
                        List.of(), ligand);

        // Cluster {ligandA, ligandB} keeps the closest (contactB); the
        // isolated contact survives as a singleton cluster.
        assertThat(refined).containsExactlyInAnyOrder(
                contactB, contactIsolated);
    }

    @Test
    void degradedLigandConnectivitySkipsClustering() {
        Atom proteinAtom = carbon(0, 0, 0);
        Atom ligandA = carbon(3.9, 0, 0);
        Atom ligandB = carbon(3.0, 0, 0);
        Interaction contactA = hydrophobic(new ResidueId("A", 10, null),
                proteinAtom, ligandA, 3.9);
        Interaction contactB = hydrophobic(new ResidueId("A", 10, null),
                proteinAtom, ligandB, 3.0);
        Structure ligand = structure(chain("L",
                residue("LIG", 501, List.of(ligandA, ligandB))));

        assertThat(InteractionRefinements.refineHydrophobicContacts(
                List.of(contactA, contactB), List.of(), ligand))
                .containsExactlyInAnyOrder(contactA, contactB);
    }

    @Test
    void refineAllAppliesThePlipPrecedenceOrder() {
        // Salt bridge suppresses an H-bond; a pi-stack suppresses a HIS
        // pi-cation and a ring hydrophobic contact; halogen bonds pass
        // through unrefined.
        Atom bridgeProtein = carbon(0, 0, 0);
        Atom bridgeLigand = carbon(4, 0, 0);
        Atom ligandHydrogen = atom(++serial, "H" + serial, Element.H,
                4, 1, 0);
        Atom proteinRingAtom = carbon(20, 0, 0);
        Atom ligandRingAtom = carbon(20, 0, 3.4);
        Atom hisNitrogen = carbon(21, 0, 4.4);
        Atom hydrophobicProtein = carbon(40, 40, 40);
        Atom hydrophobicLigand = carbon(43, 40, 40);
        Atom ligandPartner = carbon(44.5, 40, 40);
        Atom halogenAcceptor = carbon(60, 60, 60);
        Atom halogen = carbon(63, 60, 60);

        ResidueId bridgeResidue = new ResidueId("A", 60, null);
        ResidueId his = new ResidueId("A", 42, null);

        Interaction bridge = saltBridge(bridgeResidue,
                List.of(bridgeProtein), List.of(bridgeLigand),
                "RESIDUE_ASP A:60", "AMINE L:501");
        Interaction suppressedBond = hydrogenBond(bridgeResidue,
                bridgeLigand, ligandHydrogen, bridgeProtein, false, 170.0);
        Interaction stack = piStack(his,
                List.of(proteinRingAtom), List.of(ligandRingAtom),
                "HIS A:42 ring0", "LIG L:501 ring0");
        Interaction hisPiCation = piCation(his, hisNitrogen, ligandRingAtom,
                "RESIDUE_HIS A:42", "LIG L:501 ring0");
        Interaction ringContact = hydrophobic(his,
                proteinRingAtom, ligandRingAtom, 3.4);
        Interaction keptContact = hydrophobic(new ResidueId("A", 77, null),
                hydrophobicProtein, hydrophobicLigand, 3.0);
        Interaction halogenBond = new Interaction(
                InteractionType.HALOGEN_BOND, new ResidueId("A", 20, null),
                List.of(halogenAcceptor), List.of(halogen),
                3.2, 120.0, 175.0, null, null, THRESHOLDS);

        Structure ligand = structure(
                List.of(chain("L", residue("LIG", 501,
                        List.of(bridgeLigand, ligandHydrogen,
                                ligandRingAtom, hydrophobicLigand,
                                ligandPartner, halogen)))),
                List.of(bond("L", 501, hydrophobicLigand.getName(),
                        ligandPartner.getName())));

        List<Interaction> refined = InteractionRefinements.refineAll(
                List.of(bridge), List.of(suppressedBond), List.of(stack),
                List.of(hisPiCation), List.of(ringContact, keptContact),
                List.of(halogenBond), ligand);

        assertThat(refined).containsExactly(
                bridge, stack, keptContact, halogenBond);
    }
}
