package totah.lab.athena.interaction;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * PLIP 3.0.1 precedence/exclusion graph over raw detector output. The
 * detection pipeline order (and the order in which the methods of this
 * class consume earlier results) is:
 *
 * <ol>
 *   <li>salt bridges (no refinement);</li>
 *   <li>hydrogen bonds, refined against salt bridges
 *   ({@link #refineHydrogenBonds}) — salt bridges suppress H-bonds;</li>
 *   <li>pi-stacking (no refinement);</li>
 *   <li>pi-cations, refined against pi-stacks
 *   ({@link #refinePiCations}) — stacking suppresses HIS pi-cations;</li>
 *   <li>hydrophobic contacts, refined against pi-stacks
 *   ({@link #refineHydrophobicContacts}) — stacking suppresses ring
 *   contacts;</li>
 *   <li>halogen bonds (no refinement).</li>
 * </ol>
 *
 * Water bridges and metal complexation are not reimplemented (no
 * perception exists for them). {@link #refineAll} is a convenience that
 * applies the whole graph in this order. Atom matching is by object
 * identity throughout: all atoms must originate from the structures that
 * were perceived.
 */
public final class InteractionRefinements {

    private InteractionRefinements() {
    }

    /**
     * Applies the full precedence graph and returns all refined
     * interactions in PLIP pipeline order: salt bridges, hydrogen bonds,
     * pi-stacks, pi-cations, hydrophobic contacts, halogen bonds.
     *
     * @param ligand the ligand structure, used for the bond graph behind
     *               hydrophobic patch clustering
     */
    public static List<Interaction> refineAll(
            List<Interaction> saltBridges,
            List<Interaction> hydrogenBonds,
            List<Interaction> piStacks,
            List<Interaction> piCations,
            List<Interaction> hydrophobicContacts,
            List<Interaction> halogenBonds,
            Structure ligand) {

        List<Interaction> refined = new ArrayList<>();
        refined.addAll(saltBridges);
        refined.addAll(refineHydrogenBonds(hydrogenBonds, saltBridges));
        refined.addAll(piStacks);
        refined.addAll(refinePiCations(piCations, piStacks));
        refined.addAll(refineHydrophobicContacts(
                hydrophobicContacts, piStacks, ligand));
        refined.addAll(halogenBonds);
        return List.copyOf(refined);
    }

    /**
     * Refines raw hydrogen bonds against detected salt bridges:
     * <ol>
     *   <li>drop every H-bond whose donor heavy atom sits on one side
     *   and whose acceptor atom sits on the opposite side of a detected
     *   salt bridge's atom sets (both bridge directions);</li>
     *   <li>keep one H-bond per donor heavy atom: the candidate with the
     *   largest angle at the hydrogen; first-seen wins ties.</li>
     * </ol>
     * No acceptor-side dedup is applied (PLIP has none).
     */
    public static List<Interaction> refineHydrogenBonds(
            List<Interaction> hydrogenBonds,
            List<Interaction> saltBridges) {

        Objects.requireNonNull(hydrogenBonds, "hydrogenBonds");
        Objects.requireNonNull(saltBridges, "saltBridges");

        List<Interaction> surviving = new ArrayList<>();
        for (Interaction bond : hydrogenBonds) {
            requireType(bond, InteractionType.HYDROGEN_BOND);
            Atom donorHeavy = donorHeavyAtom(bond);
            Atom acceptor = acceptorAtom(bond);
            if (participatesInSaltBridge(donorHeavy, acceptor, saltBridges)) {
                continue;
            }
            surviving.add(bond);
        }

        Map<Atom, Interaction> bestByDonor = new IdentityHashMap<>();
        for (Interaction bond : surviving) {
            Atom donorHeavy = donorHeavyAtom(bond);
            Interaction best = bestByDonor.get(donorHeavy);
            if (best == null
                    || bond.primaryAngleDegrees() > best.primaryAngleDegrees()) {
                bestByDonor.put(donorHeavy, bond);
            }
        }
        List<Interaction> refined = new ArrayList<>();
        Set<Atom> emitted = new HashSet<>();
        for (Interaction bond : surviving) {
            Atom donorHeavy = donorHeavyAtom(bond);
            if (bestByDonor.get(donorHeavy) == bond
                    && emitted.add(donorHeavy)) {
                refined.add(bond);
            }
        }
        return List.copyOf(refined);
    }

    /**
     * Refines raw pi-cations against detected pi-stacks: drops every
     * pi-cation whose protein-side charged group is a HIS residue group
     * (charged-group id starts with {@code "RESIDUE_HIS "}) and whose
     * ring pair is already a detected pi-stack, matched by the protein
     * residue identity and the ligand-side ring id. Stacking wins.
     *
     * <p>Deviation from PLIP: the residue match compares the full
     * {@link totah.lab.gaia.structure.ResidueId} including the chain;
     * PLIP compares residue numbers only.
     */
    public static List<Interaction> refinePiCations(
            List<Interaction> piCations,
            List<Interaction> piStacks) {

        Objects.requireNonNull(piCations, "piCations");
        Objects.requireNonNull(piStacks, "piStacks");
        for (Interaction stack : piStacks) {
            requirePiStack(stack);
        }

        List<Interaction> refined = new ArrayList<>();
        for (Interaction piCation : piCations) {
            requireType(piCation, InteractionType.PI_CATION);
            String chargedGroupId = piCation.proteinGroupId();
            if (chargedGroupId == null
                    || !chargedGroupId.startsWith("RESIDUE_HIS ")) {
                refined.add(piCation);
                continue;
            }
            boolean stacked = false;
            for (Interaction stack : piStacks) {
                if (stack.residue().equals(piCation.residue())
                        && Objects.equals(stack.ligandGroupId(),
                                piCation.ligandGroupId())) {
                    stacked = true;
                    break;
                }
            }
            if (!stacked) {
                refined.add(piCation);
            }
        }
        return List.copyOf(refined);
    }

    /**
     * Refines raw hydrophobic contacts against detected pi-stacks:
     * <ol>
     *   <li>drop every pair where both atoms are members of the two
     *   rings of a detected pi-stack;</li>
     *   <li>per (ligand atom, protein residue) keep the closest contact;
     *   first-seen wins ties. Deviation from PLIP: the residue key
     *   includes the chain id; PLIP keys on the residue number only;</li>
     *   <li>per-protein-atom patch clustering: the contacting ligand
     *   atoms of each protein atom are clustered by ligand bond
     *   connectivity and only the single closest contact per cluster is
     *   kept.</li>
     * </ol>
     *
     * <p>Deviations from PLIP 3.0.1:
     * <ul>
     *   <li>an isolated contacting ligand atom (no bonded neighbor among
     *   the other contacting ligand atoms of the same protein atom)
     *   forms its own singleton cluster and is kept; PLIP's cluster
     *   branch silently drops such contacts. The quirk is not
     *   reproduced;</li>
     *   <li>the per-cluster representative is deterministically the
     *   closest contact; PLIP's representative may not be the globally
     *   closest;</li>
     *   <li>when the ligand bond graph is degraded or absent, clustering
     *   is skipped entirely and the step-2 result is returned; nothing
     *   is clustered on guessed connectivity.</li>
     * </ul>
     */
    public static List<Interaction> refineHydrophobicContacts(
            List<Interaction> hydrophobicContacts,
            List<Interaction> piStacks,
            Structure ligand) {

        Objects.requireNonNull(hydrophobicContacts, "hydrophobicContacts");
        Objects.requireNonNull(piStacks, "piStacks");
        Objects.requireNonNull(ligand, "ligand");

        // Step 1: pi-stacking exclusion.
        List<Interaction> surviving = new ArrayList<>();
        for (Interaction contact : hydrophobicContacts) {
            requireType(contact, InteractionType.HYDROPHOBIC_CONTACT);
            if (isRingPairContact(contact, piStacks)) {
                continue;
            }
            surviving.add(contact);
        }

        // Step 2: per (ligand atom, protein residue) keep closest.
        Map<Atom, Map<ResidueId, Interaction>> closestByAtomAndResidue =
                new IdentityHashMap<>();
        List<Interaction> stepTwo = new ArrayList<>();
        for (Interaction contact : surviving) {
            Map<ResidueId, Interaction> byResidue = closestByAtomAndResidue
                    .computeIfAbsent(contact.ligandAtoms().get(0),
                            atom -> new LinkedHashMap<>());
            Interaction best = byResidue.get(contact.residue());
            if (best == null) {
                byResidue.put(contact.residue(), contact);
                stepTwo.add(contact);
            } else if (contact.distanceAngstroms()
                    < best.distanceAngstroms()) {
                byResidue.put(contact.residue(), contact);
                stepTwo.set(stepTwo.indexOf(best), contact);
            }
        }

        // Step 3: per-protein-atom patch clustering.
        if (!BondNeighbors.usable(ligand)) {
            return List.copyOf(stepTwo);
        }
        Map<Atom, List<Atom>> ligandNeighbors = BondNeighbors.of(ligand);

        Map<Atom, List<Interaction>> byProteinAtom = new IdentityHashMap<>();
        List<Atom> proteinAtomOrder = new ArrayList<>();
        for (Interaction contact : stepTwo) {
            Atom proteinAtom = contact.proteinAtoms().get(0);
            if (!byProteinAtom.containsKey(proteinAtom)) {
                proteinAtomOrder.add(proteinAtom);
            }
            byProteinAtom.computeIfAbsent(proteinAtom,
                    atom -> new ArrayList<>()).add(contact);
        }
        List<Interaction> refined = new ArrayList<>();
        for (Atom proteinAtom : proteinAtomOrder) {
            List<Interaction> group = byProteinAtom.get(proteinAtom);
            if (group.size() == 1) {
                refined.add(group.get(0));
                continue;
            }
            for (List<Interaction> cluster
                    : clusters(group, ligandNeighbors)) {
                Interaction closest = cluster.get(0);
                for (Interaction contact : cluster) {
                    if (contact.distanceAngstroms()
                            < closest.distanceAngstroms()) {
                        closest = contact;
                    }
                }
                refined.add(closest);
            }
        }
        return List.copyOf(refined);
    }

    private static boolean participatesInSaltBridge(
            Atom donorHeavy,
            Atom acceptor,
            List<Interaction> saltBridges) {

        for (Interaction bridge : saltBridges) {
            requireType(bridge, InteractionType.SALT_BRIDGE);
            boolean donorOnProtein = containsIdentity(
                    bridge.proteinAtoms(), donorHeavy);
            boolean donorOnLigand = containsIdentity(
                    bridge.ligandAtoms(), donorHeavy);
            boolean acceptorOnProtein = containsIdentity(
                    bridge.proteinAtoms(), acceptor);
            boolean acceptorOnLigand = containsIdentity(
                    bridge.ligandAtoms(), acceptor);
            if ((donorOnProtein && acceptorOnLigand)
                    || (donorOnLigand && acceptorOnProtein)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRingPairContact(
            Interaction contact,
            List<Interaction> piStacks) {

        Atom proteinAtom = contact.proteinAtoms().get(0);
        Atom ligandAtom = contact.ligandAtoms().get(0);
        for (Interaction stack : piStacks) {
            requirePiStack(stack);
            if (containsIdentity(stack.proteinAtoms(), proteinAtom)
                    && containsIdentity(stack.ligandAtoms(), ligandAtom)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clusters the contacting ligand atoms of one protein atom by bond
     * connectivity (connected components over the ligand bond graph
     * restricted to the contacting atoms). An isolated contacting atom is
     * a singleton cluster and is kept — PLIP silently drops it.
     */
    private static List<List<Interaction>> clusters(
            List<Interaction> group,
            Map<Atom, List<Atom>> ligandNeighbors) {

        Map<Atom, Interaction> contactByLigandAtom = new IdentityHashMap<>();
        for (Interaction contact : group) {
            contactByLigandAtom.put(contact.ligandAtoms().get(0), contact);
        }
        Set<Atom> contacting = contactByLigandAtom.keySet();
        Set<Atom> visited = new HashSet<>();
        List<List<Interaction>> clusters = new ArrayList<>();
        for (Interaction contact : group) {
            Atom seed = contact.ligandAtoms().get(0);
            if (!visited.add(seed)) {
                continue;
            }
            List<Interaction> cluster = new ArrayList<>();
            cluster.add(contact);
            Deque<Atom> frontier = new ArrayDeque<>();
            frontier.add(seed);
            while (!frontier.isEmpty()) {
                Atom current = frontier.poll();
                for (Atom neighbor : ligandNeighbors.getOrDefault(
                        current, List.of())) {
                    if (contacting.contains(neighbor) && visited.add(neighbor)) {
                        frontier.add(neighbor);
                        cluster.add(contactByLigandAtom.get(neighbor));
                    }
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    private static Atom donorHeavyAtom(Interaction hydrogenBond) {
        return hydrogenBond.proteinAtoms().size() == 2
                ? hydrogenBond.proteinAtoms().get(0)
                : hydrogenBond.ligandAtoms().get(0);
    }

    private static Atom acceptorAtom(Interaction hydrogenBond) {
        return hydrogenBond.proteinAtoms().size() == 2
                ? hydrogenBond.ligandAtoms().get(0)
                : hydrogenBond.proteinAtoms().get(0);
    }

    private static boolean containsIdentity(List<Atom> atoms, Atom atom) {
        for (Atom candidate : atoms) {
            if (candidate == atom) {
                return true;
            }
        }
        return false;
    }

    private static void requireType(
            Interaction interaction,
            InteractionType expected) {

        if (interaction.type() != expected) {
            throw new IllegalArgumentException(
                    "expected " + expected + " but got "
                            + interaction.type());
        }
    }

    private static void requirePiStack(Interaction interaction) {
        if (interaction.type() != InteractionType.PI_STACK_PARALLEL
                && interaction.type() != InteractionType.PI_STACK_T_SHAPED) {
            throw new IllegalArgumentException(
                    "expected a pi-stack but got " + interaction.type());
        }
    }
}
