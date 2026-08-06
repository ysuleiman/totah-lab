package totah.lab.athena.pocket.evidence;

import totah.lab.athena.pocket.compare.residue.ResidueReference;

import java.util.Set;

/**
 * Supplies ligand-contact annotations for one structure and one
 * functional ligand. The pocket comparison pipeline is
 * ligand-agnostic: nothing is hardcoded to SAM — SAM and SAH are
 * simply the first annotated ligands.
 *
 * <p>Contacts are matched to pocket residues by chain id, residue
 * number and insertion code (the residue name of the returned
 * references is not used for matching).</p>
 */
@FunctionalInterface
public interface LigandContactProvider {

    /**
     * The residues of {@code structureKey} annotated as contacting
     * {@code ligand}; an empty set when the structure has no
     * annotation for the ligand.
     */
    Set<ResidueReference> contacts(
            String structureKey,
            FunctionalLigand ligand
    );
}
