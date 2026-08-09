package totah.lab.athena.ligand.pose;

import totah.lab.gaia.structure.ResidueId;

import java.util.HashSet;
import java.util.Set;

public final class PoseComparator {

    public PoseComparison compare(
            PoseAnalysis first,
            PoseAnalysis second
    ) {
        Set<ResidueId> firstResidues = contactResidues(first);
        Set<ResidueId> secondResidues = contactResidues(second);

        Set<ResidueId> intersection = new HashSet<>(firstResidues);
        intersection.retainAll(secondResidues);

        Set<ResidueId> union = new HashSet<>(firstResidues);
        union.addAll(secondResidues);

        double jaccard = union.isEmpty()
                ? 1.0
                : (double) intersection.size() / union.size();

        double centroidDistanceDelta =
                second.pocketPose().pocketCentroidDistance()
                        - first.pocketPose().pocketCentroidDistance();

        return new PoseComparison(
                centroidDistanceDelta,
                intersection.size(),
                firstResidues.size(),
                secondResidues.size(),
                jaccard
        );
    }

    private static Set<ResidueId> contactResidues(
            PoseAnalysis analysis
    ) {
        Set<ResidueId> residues = new HashSet<>();

        analysis.contacts().forEach(
                contact -> residues.add(contact.residue())
        );

        return residues;
    }
}
