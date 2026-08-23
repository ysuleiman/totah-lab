package totah.lab.prometheus.potential.delta.training;

import java.util.List;
import java.util.Map;

/** Immutable, target-independent authorization boundary for delta fitting. */
public record BasisPreflightResult(List<ColumnAssessment> columns,Map<Integer,Integer> duplicateMap,boolean observableRankValid,boolean derivativeConsistencyPass,boolean invariancePass,boolean dimensionCeilingPass) {
    public enum Classification { STRUCTURALLY_NULL, STRUCTURALLY_DUPLICATE, NONZERO_BUT_COLLINEAR, OBSERVABLE }
    public record ColumnAssessment(int index,Classification classification,double maximumEnergyMagnitude,double maximumDerivativeMagnitude) {}
    public BasisPreflightResult { columns=List.copyOf(columns);duplicateMap=Map.copyOf(duplicateMap); }
    public boolean pass(){return columns.stream().noneMatch(c->c.classification()==Classification.STRUCTURALLY_NULL||c.classification()==Classification.STRUCTURALLY_DUPLICATE)&&duplicateMap.isEmpty()&&observableRankValid&&derivativeConsistencyPass&&invariancePass&&dimensionCeilingPass;}
}
