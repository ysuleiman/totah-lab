package totah.lab.prometheus.potential.delta.model;

import java.util.Objects;
import totah.lab.prometheus.potential.PotentialEvaluation;
import totah.lab.prometheus.potential.QuantumCoordinates;
import totah.lab.prometheus.potential.delta.DeltaPotential;
import totah.lab.prometheus.potential.delta.basis.BasisEvaluation;
import totah.lab.prometheus.potential.delta.basis.ManyBodyBasis;

/** Linear invariant energy model whose forces are its exact negative gradient. */
public final class LinearDeltaModel implements DeltaPotential {
    private final ManyBodyBasis basis;private final DeltaModelParameters parameters;private final DeltaModelIdentity identity;
    public LinearDeltaModel(ManyBodyBasis basis,DeltaModelParameters parameters,DeltaModelIdentity identity){this.basis=Objects.requireNonNull(basis);this.parameters=Objects.requireNonNull(parameters);this.identity=Objects.requireNonNull(identity);if(basis.dimension()!=parameters.size())throw new IllegalArgumentException("basis and coefficient dimensions differ");}
    public DeltaModelIdentity identity(){return identity;}
    @Override public PotentialEvaluation evaluate(QuantumCoordinates coordinates){BasisEvaluation evaluated=basis.evaluate(coordinates);double energy=0;double[][] forces=new double[coordinates.atomCount()][3];for(int f=0;f<evaluated.size();f++){double c=parameters.coefficient(f);energy+=c*evaluated.value(f);for(int atom=0;atom<coordinates.atomCount();atom++)for(int axis=0;axis<3;axis++)forces[atom][axis]-=c*evaluated.gradient(f,atom,axis);}return new PotentialEvaluation(energy,forces);}
}
