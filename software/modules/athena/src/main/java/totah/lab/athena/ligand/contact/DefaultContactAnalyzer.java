package totah.lab.athena.ligand.contact;

import totah.lab.gaia.geometry.Point3D;
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

/**
 * Distance-based {@link ContactAnalyzer}: for every receptor residue
 * the closest heavy-atom pair between the ligand and that residue is
 * found; residues within {@code contactCutoffAngstroms} are reported
 * as {@link ContactType#DIRECT}, residues beyond it but within
 * {@code shellCutoffAngstroms} as {@link ContactType#SHELL}. Contacts
 * are sorted by distance.
 */
public final class DefaultContactAnalyzer implements ContactAnalyzer {

    public static final double DEFAULT_CONTACT_CUTOFF_ANGSTROMS = 4.5;

    public static final double DEFAULT_SHELL_CUTOFF_ANGSTROMS = 8.0;

    private final double contactCutoffAngstroms;
    private final double shellCutoffAngstroms;

    public DefaultContactAnalyzer() {
        this(
                DEFAULT_CONTACT_CUTOFF_ANGSTROMS,
                DEFAULT_SHELL_CUTOFF_ANGSTROMS
        );
    }

    public DefaultContactAnalyzer(
            double contactCutoffAngstroms,
            double shellCutoffAngstroms
    ) {
        if (contactCutoffAngstroms <= 0.0
                || shellCutoffAngstroms < contactCutoffAngstroms) {
            throw new IllegalArgumentException(
                    "Cutoffs must satisfy 0 < contact <= shell"
            );
        }
        this.contactCutoffAngstroms = contactCutoffAngstroms;
        this.shellCutoffAngstroms = shellCutoffAngstroms;
    }

    @Override
    public List<LigandContact> analyze(
            Structure receptor,
            Ligand ligand
    ) {
        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(ligand, "ligand");

        List<Atom> ligandAtoms = ligand.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .toList();

        List<LigandContact> contacts = new ArrayList<>();
        for (Chain chain : receptor.getChains()) {
            for (Residue residue : chain.residues()) {
                LigandContact contact = closest(
                        chain.id(),
                        residue,
                        ligandAtoms
                );
                if (contact != null) {
                    contacts.add(contact);
                }
            }
        }
        contacts.sort(Comparator.comparingDouble(LigandContact::distance));
        return List.copyOf(contacts);
    }

    private LigandContact closest(
            String chainId,
            Residue residue,
            List<Atom> ligandAtoms
    ) {
        Atom bestLigand = null;
        Atom bestReceptor = null;
        double best = Double.MAX_VALUE;
        for (Atom receptorAtom : residue.getAtoms()) {
            if (!receptorAtom.isHeavyAtom()) {
                continue;
            }
            Point3D receptorPosition = receptorAtom.getPosition();
            if (receptorPosition == null) {
                continue;
            }
            for (Atom ligandAtom : ligandAtoms) {
                double distance = receptorPosition.distance(
                        ligandAtom.getPosition());
                if (distance < best) {
                    best = distance;
                    bestLigand = ligandAtom;
                    bestReceptor = receptorAtom;
                }
            }
        }
        if (bestLigand == null || best > shellCutoffAngstroms) {
            return null;
        }
        return new LigandContact(
                bestLigand,
                bestReceptor,
                new ResidueId(chainId, residue.getNumber(),
                        residue.getInsertionCode()),
                best,
                best <= contactCutoffAngstroms
                        ? ContactType.DIRECT
                        : ContactType.SHELL
        );
    }
}
