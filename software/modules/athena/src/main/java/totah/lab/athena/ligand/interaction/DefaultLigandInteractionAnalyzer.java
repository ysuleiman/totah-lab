package totah.lab.athena.ligand.interaction;

import totah.lab.gaia.molecule.Ligand;
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
 * Detects conservative chemical interactions from prepared PDBQT state.
 * Hydrogen bonds use AD4 donor hydrogens/acceptor atom types and explicit
 * D-H-A geometry. Salt bridges require opposite non-neutral summed partial
 * charges, preserving the prepared charges as the source of truth.
 */
public final class DefaultLigandInteractionAnalyzer
        implements LigandInteractionAnalyzer {

    public static final double HYDROGEN_ACCEPTOR_CUTOFF_ANGSTROMS = 2.5;
    public static final double HYDROGEN_BOND_CUTOFF_ANGSTROMS = 3.5;
    public static final double HYDROGEN_BOND_MIN_ANGLE_DEGREES = 120.0;
    public static final double SALT_BRIDGE_CUTOFF_ANGSTROMS = 4.0;
    public static final double IONIC_CHARGE_THRESHOLD = 0.5;

    private static final double DONOR_BOND_CUTOFF_ANGSTROMS = 1.35;
    private static final Set<String> ACCEPTOR_TYPES =
            Set.of("NA", "OA", "SA");

    @Override
    public List<LigandInteraction> analyze(
            Structure receptor,
            Ligand ligand
    ) {
        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(ligand, "ligand");

        List<Atom> ligandAtoms = atoms(ligand.structure());
        List<DonorSite> ligandDonors = donorSites(ligandAtoms);
        List<Atom> ligandAcceptors = acceptors(ligandAtoms);
        double ligandCharge = charge(ligandAtoms);
        List<LigandInteraction> interactions = new ArrayList<>();

        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId residueId = new ResidueId(
                        chain.id(), residue.getNumber(),
                        residue.getInsertionCode());
                List<Atom> receptorAtoms = residue.getAtoms();
                hydrogenBonds(interactions, residueId,
                        donorSites(receptorAtoms), ligandAcceptors, false);
                hydrogenBonds(interactions, residueId,
                        ligandDonors, acceptors(receptorAtoms), true);
                saltBridge(interactions, residueId, receptorAtoms,
                        ligandAtoms, ligandCharge);
            }
        }
        interactions.sort(Comparator
                .comparing((LigandInteraction value) -> value.residue()
                        .chainId())
                .thenComparingInt(value -> value.residue().residueNumber())
                .thenComparing(LigandInteraction::type)
                .thenComparingDouble(LigandInteraction::distance));
        return List.copyOf(interactions);
    }

    private void hydrogenBonds(
            List<LigandInteraction> interactions,
            ResidueId residue,
            List<DonorSite> donors,
            List<Atom> acceptors,
            boolean ligandDonor
    ) {
        for (DonorSite donor : donors) {
            for (Atom acceptor : acceptors) {
                double hydrogenDistance = donor.hydrogen().getPosition()
                        .distance(acceptor.getPosition());
                double heavyDistance = donor.heavyAtom().getPosition()
                        .distance(acceptor.getPosition());
                double angle = angle(
                        donor.heavyAtom(), donor.hydrogen(), acceptor);
                if (hydrogenDistance > HYDROGEN_ACCEPTOR_CUTOFF_ANGSTROMS
                        || heavyDistance > HYDROGEN_BOND_CUTOFF_ANGSTROMS
                        || angle < HYDROGEN_BOND_MIN_ANGLE_DEGREES) {
                    continue;
                }
                interactions.add(new LigandInteraction(
                        InteractionType.HYDROGEN_BOND,
                        residue,
                        ligandDonor ? acceptor : donor.heavyAtom(),
                        ligandDonor ? donor.heavyAtom() : acceptor,
                        heavyDistance,
                        angle,
                        "AD4 donor/acceptor types with explicit D-H-A geometry"
                ));
            }
        }
    }

    private void saltBridge(
            List<LigandInteraction> interactions,
            ResidueId residue,
            List<Atom> receptorAtoms,
            List<Atom> ligandAtoms,
            double ligandCharge
    ) {
        double receptorCharge = charge(receptorAtoms);
        if (Math.abs(receptorCharge) < IONIC_CHARGE_THRESHOLD
                || Math.abs(ligandCharge) < IONIC_CHARGE_THRESHOLD
                || Math.signum(receptorCharge) == Math.signum(ligandCharge)) {
            return;
        }
        AtomPair closest = closestHeavyAtoms(receptorAtoms, ligandAtoms);
        if (closest != null
                && closest.distance() <= SALT_BRIDGE_CUTOFF_ANGSTROMS) {
            interactions.add(new LigandInteraction(
                    InteractionType.SALT_BRIDGE,
                    residue,
                    closest.receptor(),
                    closest.ligand(),
                    closest.distance(),
                    null,
                    "Opposite prepared partial-charge sums"
            ));
        }
    }

    private static List<DonorSite> donorSites(List<Atom> atoms) {
        List<DonorSite> sites = new ArrayList<>();
        for (Atom hydrogen : atoms) {
            if (!"HD".equals(hydrogen.getAutoDockType())) {
                continue;
            }
            atoms.stream()
                    .filter(Atom::isHeavyAtom)
                    .filter(atom -> atom.getPosition().distance(
                            hydrogen.getPosition())
                            <= DONOR_BOND_CUTOFF_ANGSTROMS)
                    .min(Comparator.comparingDouble(atom -> atom.getPosition()
                            .distance(hydrogen.getPosition())))
                    .ifPresent(heavy -> sites.add(
                            new DonorSite(heavy, hydrogen)));
        }
        return List.copyOf(sites);
    }

    private static List<Atom> acceptors(List<Atom> atoms) {
        return atoms.stream()
                .filter(atom -> ACCEPTOR_TYPES.contains(
                        atom.getAutoDockType()))
                .toList();
    }

    private static List<Atom> atoms(Structure structure) {
        return structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .toList();
    }

    private static double charge(List<Atom> atoms) {
        return atoms.stream().mapToDouble(Atom::getCharge).sum();
    }

    private static AtomPair closestHeavyAtoms(
            List<Atom> receptorAtoms,
            List<Atom> ligandAtoms
    ) {
        AtomPair closest = null;
        for (Atom receptor : receptorAtoms) {
            if (!receptor.isHeavyAtom()) continue;
            for (Atom ligand : ligandAtoms) {
                if (!ligand.isHeavyAtom()) continue;
                double distance = receptor.getPosition()
                        .distance(ligand.getPosition());
                if (closest == null || distance < closest.distance()) {
                    closest = new AtomPair(receptor, ligand, distance);
                }
            }
        }
        return closest;
    }

    private static double angle(Atom first, Atom center, Atom third) {
        double ax = first.getPosition().x() - center.getPosition().x();
        double ay = first.getPosition().y() - center.getPosition().y();
        double az = first.getPosition().z() - center.getPosition().z();
        double bx = third.getPosition().x() - center.getPosition().x();
        double by = third.getPosition().y() - center.getPosition().y();
        double bz = third.getPosition().z() - center.getPosition().z();
        double denominator = Math.sqrt(ax * ax + ay * ay + az * az)
                * Math.sqrt(bx * bx + by * by + bz * bz);
        if (denominator == 0.0) return 0.0;
        double cosine = Math.max(-1.0, Math.min(1.0,
                (ax * bx + ay * by + az * bz) / denominator));
        return Math.toDegrees(Math.acos(cosine));
    }

    private record DonorSite(Atom heavyAtom, Atom hydrogen) {
    }

    private record AtomPair(Atom receptor, Atom ligand, double distance) {
    }
}
