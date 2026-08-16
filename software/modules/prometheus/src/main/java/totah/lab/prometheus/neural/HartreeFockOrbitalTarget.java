package totah.lab.prometheus.neural;

import java.util.Objects;

import totah.lab.prometheus.variational.QuantumCoordinates;

/** Occupied unrestricted-HF orbital matrices used by reference FermiNet pretraining. */
@FunctionalInterface
public interface HartreeFockOrbitalTarget {
    OrbitalMatrices evaluate(QuantumCoordinates coordinates);

    record OrbitalMatrices(double[][] alpha,double[][] beta) {
        public OrbitalMatrices {
            alpha=copy(alpha,"alpha");beta=copy(beta,"beta");
        }
        @Override public double[][] alpha(){return copy(alpha,"alpha");}
        @Override public double[][] beta(){return copy(beta,"beta");}
        private static double[][] copy(double[][] values,String name){
            Objects.requireNonNull(values,name);double[][] result=new double[values.length][];
            for(int i=0;i<values.length;i++){
                Objects.requireNonNull(values[i],name+" row");result[i]=values[i].clone();
                for(double value:result[i])if(!Double.isFinite(value))throw new IllegalArgumentException(name+" contains non-finite values");
            }
            return result;
        }
    }
}
