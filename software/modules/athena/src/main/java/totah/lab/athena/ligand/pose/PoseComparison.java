package totah.lab.athena.ligand.pose;

public record PoseComparison(
        double pocketCentroidDistanceDelta,
        int sharedContactResidues,
        int firstContactResidues,
        int secondContactResidues,
        double contactResidueJaccard
) {
}
