package totah.lab.prometheus.potential.delta.environment;

import java.util.Arrays;

/** Immutable chemical types and all-pairs graph distances for a molecular topology. */
public final class LocalEnvironment {
    private final SpeciesChannel[] types;
    private final int[][] graphDistances;
    public LocalEnvironment(SpeciesChannel[] types, int[][] graphDistances) {
        if (types == null || graphDistances == null || types.length == 0 || graphDistances.length != types.length) throw new IllegalArgumentException("topology dimensions differ");
        this.types = types.clone(); this.graphDistances = new int[types.length][types.length];
        for (int i=0; i<types.length; i++) {
            if (types[i] == null || graphDistances[i] == null || graphDistances[i].length != types.length) throw new IllegalArgumentException("invalid topology row");
            this.graphDistances[i] = graphDistances[i].clone();
        }
    }
    public int atomCount() { return types.length; }
    public SpeciesChannel type(int atom) { return types[atom]; }
    public int graphDistance(int first, int second) { return graphDistances[first][second]; }
    public SpeciesChannel[] types() { return types.clone(); }
    public int[][] graphDistances() { return Arrays.stream(graphDistances).map(int[]::clone).toArray(int[][]::new); }
}
