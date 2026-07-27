package totah.lab.pocket;

public class PocketComparison {
    private double centerDistance;
    private double volumeDifference;
    private int sharedResidues;
    private int residueOverlap;
    private double residueJaccard;

    public PocketComparison(double centerDistance, double volumeDifference) {
        this(centerDistance, volumeDifference, 0, 0, 0);
    }

    public PocketComparison(double centerDistance, double volumeDifference,
                            int size, int size1, double jaccard) {
        this.centerDistance = centerDistance;
        this.volumeDifference = volumeDifference;
        this.sharedResidues = size;
        this.residueOverlap = size1;
        this.residueJaccard = jaccard;
    }

    public double getCenterDistance() {
        return centerDistance;
    }
    public void setCenterDistance(double centerDistance) {
        this.centerDistance = centerDistance;
    }
    public double getVolumeDifference() {
        return volumeDifference;
    }
    public void setVolumeDifference(double volumeDifference) {
        this.volumeDifference = volumeDifference;
    }
    public int getSharedResidues() {
        return sharedResidues;
    }
    public void setSharedResidues(int sharedResidues) {
        this.sharedResidues = sharedResidues;
    }
    public int getResidueOverlap() {
        return residueOverlap;
    }
    public void setResidueOverlap(int residueOverlap) {
        this.residueOverlap = residueOverlap;
    }
    public double getResidueJaccard() {
        return residueJaccard;
    }
    public void setResidueJaccard(double residueJaccard) {
        this.residueJaccard = residueJaccard;
    }
    @Override
    public String toString() {
        return "PocketComparison [centerDistance=" + centerDistance + ", volumeDifference=" + volumeDifference;
    }
}
