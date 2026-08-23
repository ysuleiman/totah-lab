package totah.lab.prometheus.potential.delta.environment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Builds topology distances from immutable zero-based bonds. */
public final class LocalEnvironmentBuilder {
    private SpeciesChannel[] types;
    private final List<int[]> bonds = new ArrayList<>();
    public LocalEnvironmentBuilder types(SpeciesChannel... types) { this.types = types == null ? null : types.clone(); return this; }
    public LocalEnvironmentBuilder bond(int first, int second) { bonds.add(new int[]{first,second}); return this; }
    public LocalEnvironment build() {
        if (types == null || types.length == 0) throw new IllegalStateException("types required");
        List<List<Integer>> adjacent = new ArrayList<>(); for (int i=0;i<types.length;i++) adjacent.add(new ArrayList<>());
        for (int[] bond:bonds) { if (bond[0]<0||bond[1]<0||bond[0]>=types.length||bond[1]>=types.length||bond[0]==bond[1]) throw new IllegalStateException("invalid bond"); adjacent.get(bond[0]).add(bond[1]); adjacent.get(bond[1]).add(bond[0]); }
        int[][] distance = new int[types.length][types.length];
        for (int source=0; source<types.length; source++) {
            java.util.Arrays.fill(distance[source], Integer.MAX_VALUE); distance[source][source]=0;
            ArrayDeque<Integer> queue=new ArrayDeque<>(); queue.add(source);
            while(!queue.isEmpty()){int at=queue.remove(); for(int next:adjacent.get(at)) if(distance[source][next]==Integer.MAX_VALUE){distance[source][next]=distance[source][at]+1;queue.add(next);}}
        }
        return new LocalEnvironment(types,distance);
    }
}
