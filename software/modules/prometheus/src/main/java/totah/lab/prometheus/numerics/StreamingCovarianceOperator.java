package totah.lab.prometheus.numerics;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Applies a covariance from bounded, replayable centered weighted observations. */
public final class StreamingCovarianceOperator implements LinearOperator {
    private final int dimension;private final CenteredObservationSource source;private final double regularization;
    private final AtomicLong applications=new AtomicLong(),observations=new AtomicLong(),passes=new AtomicLong();
    public StreamingCovarianceOperator(int dimension,CenteredObservationSource source,double regularization){if(dimension<1||regularization<=0)throw new IllegalArgumentException("invalid covariance operator");this.dimension=dimension;this.source=Objects.requireNonNull(source);this.regularization=regularization;}
    @Override public int dimension(){return dimension;}
    @Override public double[] apply(double[] vector){if(vector.length!=dimension)throw new IllegalArgumentException("vector dimension mismatch");double[] result=new double[dimension];source.forEach((weight,centered)->{if(centered.length!=dimension)throw new IllegalArgumentException("observation dimension mismatch");double projection=PreconditionedConjugateGradientSolver.dot(centered,vector);for(int i=0;i<dimension;i++)result[i]+=weight*centered[i]*projection;observations.incrementAndGet();});for(int i=0;i<dimension;i++)result[i]+=regularization*vector[i];applications.incrementAndGet();passes.incrementAndGet();return result;}
    public Counters counters(){return new Counters(applications.get(),passes.get(),observations.get());}
    @FunctionalInterface public interface CenteredObservationSource{void forEach(ObservationConsumer consumer);}
    @FunctionalInterface public interface ObservationConsumer{void accept(double normalizedWeight,double[] centeredObservation);}
    public record Counters(long operatorApplications,long streamedPasses,long observations){ }
}
