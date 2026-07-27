package totah.lab.pocket;


import java.util.List;
import java.util.Map;

public class PocketProfile {
    private double[] center;
    private double volume;
    private int residueCount;
    private int alphaSphereCount;
    private double meanResidueDistance;
    private Residue closestResidue;
    private Residue farthestResidue;
    private List<Residue> coreResidues;
    private Map<Residue, Double> residueDistances;


    private PocketProfile(Builder builder) {
        this.center = builder.center;
        this.volume = builder.volume;
        this.residueCount = builder.residueCount;
        this.alphaSphereCount = builder.alphaSphereCount;
        this.meanResidueDistance = builder.meanResidueDistance;
        this.closestResidue = builder.closestResidue;
        this.farthestResidue = builder.farthestResidue;
        this.coreResidues = builder.coreResidues;
        this.residueDistances = builder.residueDistances;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private double[] center;

        private double volume;

        private int residueCount;

        private int alphaSphereCount;

        private double meanResidueDistance;

        private Residue closestResidue;

        private Residue farthestResidue;

        private List<Residue> coreResidues;

        private Map<Residue, Double> residueDistances;


        public Builder center(double[] center) {
            this.center = center;
            return this;
        }

        public Builder volume(double volume) {
            this.volume = volume;
            return this;
        }

        public Builder residueCount(int residueCount) {
            this.residueCount = residueCount;
            return this;
        }

        public Builder alphaSphereCount(int alphaSphereCount) {
            this.alphaSphereCount = alphaSphereCount;
            return this;
        }

        public Builder meanResidueDistance(double meanResidueDistance) {
            this.meanResidueDistance = meanResidueDistance;
            return this;
        }

        public Builder closestResidue(Residue closestResidue) {
            this.closestResidue = closestResidue;
            return this;
        }

        public Builder farthestResidue(Residue farthestResidue) {
            this.farthestResidue = farthestResidue;
            return this;
        }

        public Builder coreResidues(List<Residue> coreResidues) {
            this.coreResidues = coreResidues;
            return this;
        }

        public Builder residueDistances(
                Map<Residue, Double> residueDistances) {

            this.residueDistances = residueDistances;
            return this;
        }

        public PocketProfile build() {
            return new PocketProfile(this);
        }
    }
}