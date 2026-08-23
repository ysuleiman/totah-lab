package totah.lab.prometheus.potential.delta.basis;

import java.util.List;
import totah.lab.prometheus.potential.QuantumCoordinates;

public final class CompositeManyBodyBasis implements ManyBodyBasis {
    private final List<ManyBodyBasis> parts; private final int dimension;
    public CompositeManyBodyBasis(List<ManyBodyBasis> parts){this.parts=List.copyOf(parts);this.dimension=this.parts.stream().mapToInt(ManyBodyBasis::dimension).sum();}
    @Override public int dimension(){return dimension;}
    @Override public BasisEvaluation evaluate(QuantumCoordinates coordinates){double[] values=new double[dimension];double[][][] gradients=new double[dimension][coordinates.atomCount()][3];int offset=0;for(ManyBodyBasis part:parts){BasisEvaluation e=part.evaluate(coordinates);for(int f=0;f<e.size();f++){values[offset+f]=e.value(f);for(int a=0;a<coordinates.atomCount();a++)for(int q=0;q<3;q++)gradients[offset+f][a][q]=e.gradient(f,a,q);}offset+=e.size();}return new BasisEvaluation(values,gradients);}
}
