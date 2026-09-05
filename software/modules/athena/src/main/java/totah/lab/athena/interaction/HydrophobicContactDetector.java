package totah.lab.athena.interaction;

import totah.lab.athena.interaction.perception.HydrophobicAtoms;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detects hydrophobic contacts as protein-hydrophobic-atom x
 * ligand-hydrophobic-atom pairs, following the PLIP 3.0.1 candidate
 * product. Perception is not repeated here: the perceived
 * {@link HydrophobicAtoms} sets are passed in by the caller (protein and
 * ligand perceived separately on their own structures).
 *
 * <p>A pair is kept when {@code minDist < d <= hydrophobicDistMax}
 * (lower bound exclusive, upper inclusive — see
 * {@link InteractionThresholds}). PLIP uses strict bounds on both ends;
 * the difference only matters for pairs exactly at 4.0 A. Raw contacts
 * are unrefined; apply
 * {@link InteractionRefinements#refineHydrophobicContacts} after
 * pi-stacking detection.
 *
 * <p>The protein structure is required to resolve the owning residue of
 * each contacting protein atom by object identity; hydrophobic atom
 * instances must originate from that structure.
 */
public final class HydrophobicContactDetector {

    /**
     * Detects raw hydrophobic contacts.
     *
     * @param protein protein structure the perceived protein atoms belong to
     * @param proteinHydrophobic perceived protein hydrophobic atoms
     * @param ligandHydrophobic perceived ligand hydrophobic atoms
     * @param thresholds threshold set applied and stamped onto the results
     * @return raw contacts in deterministic traversal order
     * @throws IllegalArgumentException if a perceived protein atom is not
     *                                  an instance held by
     *                                  {@code protein}
     */
    public List<Interaction> detect(
            Structure protein,
            HydrophobicAtoms proteinHydrophobic,
            HydrophobicAtoms ligandHydrophobic,
            InteractionThresholds thresholds) {

        Objects.requireNonNull(protein, "protein");
        Objects.requireNonNull(proteinHydrophobic, "proteinHydrophobic");
        Objects.requireNonNull(ligandHydrophobic, "ligandHydrophobic");
        Objects.requireNonNull(thresholds, "thresholds");

        AtomResidueIndex proteinIndex = AtomResidueIndex.of(protein);
        List<Interaction> contacts = new ArrayList<>();
        for (Atom proteinAtom : proteinHydrophobic.atoms()) {
            ResidueId residue = proteinIndex.residueOf(proteinAtom)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "protein hydrophobic atom is not part of the"
                                    + " protein structure: "
                                    + proteinAtom.getName()));
            for (Atom ligandAtom : ligandHydrophobic.atoms()) {
                double distance = proteinAtom.getPosition()
                        .distance(ligandAtom.getPosition());
                if (distance <= thresholds.minDist()
                        || distance > thresholds.hydrophobicDistMax()) {
                    continue;
                }
                contacts.add(new Interaction(
                        InteractionType.HYDROPHOBIC_CONTACT,
                        residue,
                        List.of(proteinAtom),
                        List.of(ligandAtom),
                        distance,
                        null,
                        null,
                        null,
                        null,
                        thresholds));
            }
        }
        return List.copyOf(contacts);
    }
}
