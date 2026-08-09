package totah.lab.athena.pocket.component;

import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Ligand-centred evidence for one raw fpocket cavity. */
public record ExperimentalSitePocket(long pocketId, int pocketNumber,
        int fpocketRank, ComponentPocketRelationshipClass relationship,
        double heavyAtomOccupancyFraction, double minimumProteinDistance,
        double minimumSphereSurfaceDistance, double ligandCentroidDistance,
        Point3D centroid, List<PocketSphere> spheres,
        Set<String> residues, Set<String> directContactResidues,
        Set<String> nearShellResidues, Set<String> coveredLigandAtoms,
        Set<String> nearLigandAtoms, Set<String> contactingLigandAtoms,
        Map<String, Point3D> ligandAtomPositions,
        Set<String> chains, Set<String> humanTargets) {
    public ExperimentalSitePocket {
        Objects.requireNonNull(relationship);
        Objects.requireNonNull(centroid);
        spheres = List.copyOf(spheres);
        residues = Set.copyOf(residues);
        directContactResidues = Set.copyOf(directContactResidues);
        nearShellResidues = Set.copyOf(nearShellResidues);
        coveredLigandAtoms = Set.copyOf(coveredLigandAtoms);
        nearLigandAtoms = Set.copyOf(nearLigandAtoms);
        contactingLigandAtoms = Set.copyOf(contactingLigandAtoms);
        ligandAtomPositions = Map.copyOf(ligandAtomPositions);
        chains = Set.copyOf(chains);
        humanTargets = Set.copyOf(humanTargets);
    }

    public boolean strong() {
        return relationship == ComponentPocketRelationshipClass.OCCUPIES_POCKET
                || relationship == ComponentPocketRelationshipClass.CONTACTS_POCKET;
    }
}
