package totah.lab.athena.system.rules;

import totah.lab.athena.system.SystemStateView;
import totah.lab.gaia.structure.*;
import java.util.*;

/** Explicit immutable selection; operational query radius is not a scientific threshold override. */
public record RuleRequest(SystemStateView.Binding state, String manifestKey, String manifestSha256,
                          List<AtomReference> atoms, List<ResidueId> first, List<ResidueId> second,
                          double radiusAngstrom, int maximumHops, int maximumNodes, int maximumCandidates) {
    public RuleRequest {
        Objects.requireNonNull(state);Objects.requireNonNull(manifestKey);
        if(!manifestSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("manifest pin");
        atoms=List.copyOf(atoms);first=List.copyOf(first);second=List.copyOf(second);
        if(!Double.isFinite(radiusAngstrom)||radiusAngstrom<=0||maximumHops<0||maximumNodes<1||maximumCandidates<1)throw new IllegalArgumentException("invalid query bounds");
        if(!Collections.disjoint(first,second))throw new IllegalArgumentException("partners overlap");
    }
}
