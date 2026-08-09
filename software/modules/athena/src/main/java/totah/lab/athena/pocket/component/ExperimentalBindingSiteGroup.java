package totah.lab.athena.pocket.component;

import java.util.List;
import java.util.Set;

/** One canonical ligand-centred site derived from one or more raw cavities. */
public record ExperimentalBindingSiteGroup(int groupNumber,
        boolean weaklyLocalized, List<Long> contributingPocketIds,
        Set<String> directContactResidues, Set<String> nearShellResidues,
        Set<String> chains, Set<String> humanTargets,
        Set<String> coveredLigandAtoms, Set<String> contactingLigandAtoms) {
    public ExperimentalBindingSiteGroup {
        contributingPocketIds = List.copyOf(contributingPocketIds);
        directContactResidues = Set.copyOf(directContactResidues);
        nearShellResidues = Set.copyOf(nearShellResidues);
        chains = Set.copyOf(chains);
        humanTargets = Set.copyOf(humanTargets);
        coveredLigandAtoms = Set.copyOf(coveredLigandAtoms);
        contactingLigandAtoms = Set.copyOf(contactingLigandAtoms);
    }
}
