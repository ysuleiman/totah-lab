package totah.lab.hephaestus.topology;

public record Edge(int indexA, int indexB, double length) {
    public Edge {
        if (indexA < 0 || indexB < 0 || indexA == indexB) {
            throw new IllegalArgumentException("Topology edge indices are invalid.");
        }
        if (!Double.isFinite(length) || length <= 0.0) {
            throw new IllegalArgumentException("Topology edge length must be positive.");
        }
        if (indexA > indexB) {
            int swap = indexA;
            indexA = indexB;
            indexB = swap;
        }
    }
}
