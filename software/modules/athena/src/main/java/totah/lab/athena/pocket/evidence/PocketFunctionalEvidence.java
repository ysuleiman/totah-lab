package totah.lab.athena.pocket.evidence;

import java.util.Objects;
import java.util.Optional;

/**
 * Functional evidence of a pocket comparison: ligand-contact
 * conservation (empty when no ligand annotation evidence exists for
 * the structure pair — absence is reported, never fabricated) and
 * the configured key-residue summary.
 *
 * @param ligandContacts ligand-contact conservation evidence, empty
 *                       when no contact annotation was available
 * @param keyResidues    key-residue summary (all counts {@code 0}
 *                       when no key residues are configured)
 */
public record PocketFunctionalEvidence(
        Optional<LigandContactEvidence> ligandContacts,
        KeyResidueEvidence keyResidues
) {

    public PocketFunctionalEvidence {
        Objects.requireNonNull(ligandContacts, "ligandContacts");
        Objects.requireNonNull(keyResidues, "keyResidues");
    }
}
